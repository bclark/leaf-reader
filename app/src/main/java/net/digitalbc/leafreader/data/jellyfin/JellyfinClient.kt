package net.digitalbc.leafreader.data.jellyfin

import android.graphics.Bitmap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

data class JellyfinItemsResult(
    val items: List<JellyfinItem>,
    val totalRecordCount: Int,
    val startIndex: Int,
)

/**
 * Native Jellyfin HTTP client for Leaf.
 *
 * Enforces:
 * - Authorization headers scoped to target server origin (stripped on cross-origin redirects).
 * - Bounded-memory streaming into .part files with speed and progress callbacks.
 * - Explicit rejection of text/html responses (captive portal / proxy login pages).
 * - Strict cancellation checking and atomic temporary file cleanup.
 */
class JellyfinClient(
    private val deviceId: String,
    private val clientVersion: String = "0.3.7",
    private val customHttpClient: OkHttpClient? = null,
) {
    private val okHttpClient = (customHttpClient?.newBuilder() ?: OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS))
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    // Cleartext stays direct and validates the exact DNS results used for connection.
    // HTTPS keeps its normal resolver/proxy behavior.
    private val localHttpClient = okHttpClient.newBuilder()
        .proxy(Proxy.NO_PROXY)
        .dns(LocalNetworkDns(okHttpClient.dns))
        .build()

    suspend fun authenticate(
        config: JellyfinServerConfig,
        username: String,
        password: String,
    ): JellyfinAuth = withContext(Dispatchers.IO) {
        val url = JellyfinEndpoints.authenticateByName(config.baseUrl)
        val bodyJson = JSONObject().apply {
            put("Username", username)
            put("Pw", password)
        }.toString()

        val request = Request.Builder()
            .url(url)
            .header("Authorization", JellyfinEndpoints.authHeader(deviceId = deviceId, version = clientVersion))
            .header("X-Emby-Authorization", JellyfinEndpoints.authHeader(deviceId = deviceId, version = clientVersion))
            .post(bodyJson.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        executeRequest(request) { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                mapHttpError(response.code, "Authentication failed")
            }
            try {
                val json = JSONObject(responseBody)
                val token = json.getString("AccessToken")
                val userObj = json.getJSONObject("User")
                val userId = userObj.getString("Id")
                val userName = userObj.optString("Name", username)
                val serverId = sequenceOf(json, json.optJSONObject("SessionInfo"))
                    .mapNotNull { (it?.opt("ServerId") as? String)?.trim()?.takeIf(String::isNotEmpty) }
                    .firstOrNull()
                require(serverId != null && serverId != "jellyfin-server") { "Server identity is missing" }

                JellyfinAuth(
                    accessToken = token,
                    userId = userId,
                    serverId = serverId,
                    username = userName,
                    serverName = json.optJSONObject("ServerInfo")?.optString("Name")?.ifBlank { null },
                )
            } catch (e: Exception) {
                throw JellyfinException(JellyfinFailureKind.PARSE_ERROR, "Failed to parse authentication response", e)
            }
        }
    }

    suspend fun getViews(
        config: JellyfinServerConfig,
        auth: JellyfinAuth,
    ): List<JellyfinView> = withContext(Dispatchers.IO) {
        val url = JellyfinEndpoints.userViews(config.baseUrl, auth.userId)
        val request = authorizedRequestBuilder(url, auth).get().build()

        executeRequest(request) { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                mapHttpError(response.code, "Failed to load views")
            }
            try {
                val json = JSONObject(body)
                val itemsArray = json.optJSONArray("Items") ?: return@executeRequest emptyList()
                val views = mutableListOf<JellyfinView>()
                for (i in 0 until itemsArray.length()) {
                    val item = itemsArray.getJSONObject(i)
                    views.add(
                        JellyfinView(
                            id = item.getString("Id"),
                            name = item.optString("Name", "Library"),
                            collectionType = item.optString("CollectionType", null),
                        )
                    )
                }
                views
            } catch (e: Exception) {
                throw JellyfinException(JellyfinFailureKind.PARSE_ERROR, "Failed to parse library views", e)
            }
        }
    }

    suspend fun getItems(
        config: JellyfinServerConfig,
        auth: JellyfinAuth,
        parentId: String?,
        startIndex: Int = 0,
        limit: Int = 100,
        onProgress: ((String, JellyfinProgressSnapshot) -> Unit)? = null,
    ): JellyfinItemsResult = withContext(Dispatchers.IO) {
        val url = JellyfinEndpoints.userItems(
            baseUrl = config.baseUrl,
            userId = auth.userId,
            parentId = parentId,
            startIndex = startIndex,
            limit = limit,
        )
        val request = authorizedRequestBuilder(url, auth).get().build()

        executeRequest(request) { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                mapHttpError(response.code, "Failed to load items")
            }
            try {
                val json = JSONObject(body)
                val total = json.optInt("TotalRecordCount", 0)
                val itemsArray = json.optJSONArray("Items") ?: return@executeRequest JellyfinItemsResult(emptyList(), total, startIndex)
                val items = mutableListOf<JellyfinItem>()
                for (i in 0 until itemsArray.length()) {
                    val value = itemsArray.getJSONObject(i)
                    val item = parseItem(value, parentId)
                    items.add(item)
                    onProgress?.invoke(item.id, parseProgress(value))
                }
                JellyfinItemsResult(items = items, totalRecordCount = total, startIndex = startIndex)
            } catch (e: Exception) {
                throw JellyfinException(JellyfinFailureKind.PARSE_ERROR, "Failed to parse library items", e)
            }
        }
    }

    /** Paginated metadata only. Unknown reading state must not become unread. */
    suspend fun getCatalogue(
        config: JellyfinServerConfig, auth: JellyfinAuth, query: JellyfinCatalogueQuery,
    ): JellyfinCataloguePage = withContext(Dispatchers.IO) {
        val request = authorizedRequestBuilder(query.url(config.baseUrl, auth.userId), auth).get().build()
        val page = executeRequest(request) { response ->
            if (!response.isSuccessful) mapHttpError(response.code, "Failed to load catalogue")
            try {
                val json = JSONObject(response.body?.string().orEmpty())
                val array = json.getJSONArray("Items")
                val entries = buildList {
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val data = obj.optJSONObject("UserData")
                        add(JellyfinCatalogueEntry(parseItem(obj),
                            obj.optString("Type", ""),
                            if (data == null || data.isNull("Played")) null else data.getBoolean("Played"),
                            seriesId = obj.textOrNull("SeriesId")?.takeUnless { it.replace("-", "").all { c -> c == '0' } },
                            volumeNumber = if (obj.isNull("ParentIndexNumber")) null else obj.getInt("ParentIndexNumber"),
                            issueNumber = if (obj.isNull("IndexNumber")) null else obj.getDouble("IndexNumber"),
                            people = parsePeople(obj),
                            lastPlayedDate = data?.textOrNull("LastPlayedDate"),
                            playedPercentage = if (data == null || data.isNull("PlayedPercentage")) null else data.optDouble("PlayedPercentage").takeIf { it.isFinite() && it in 0.0..100.0 }))
                    }
                }
                val total = json.getInt("TotalRecordCount")
                val start = json.optInt("StartIndex", query.startIndex)
                require(start == query.startIndex && total >= 0 && entries.size <= query.limit)
                val next = start + entries.size
                require(entries.isNotEmpty() || next >= total) { "Server returned an empty page before the end" }
                JellyfinCataloguePage(entries, next, total, next < total)
            } catch (error: Exception) {
                throw JellyfinException(JellyfinFailureKind.PARSE_ERROR, "Invalid catalogue page", error)
            }
        }
        val visible = JellyfinCatalogueLogic.visible(page.entries,
            if (query.collectionId != null) JellyfinCatalogueView.ALL else query.view)
        val scoped = if (query.collectionId == null) visible else visible.filter { it.item.isImportable }.filter { entry ->
            val ancestors = authorizedRequestBuilder(
                JellyfinCatalogueQuery.ancestorsUrl(config.baseUrl, auth.userId, entry.item.id), auth,
            ).get().build()
            executeRequest(ancestors) { response ->
                if (!response.isSuccessful) mapHttpError(response.code, "Cannot verify collection membership")
                try {
                    val array = org.json.JSONArray(response.body?.string().orEmpty())
                    (0 until array.length()).any {
                        array.getJSONObject(it).getString("Id").equals(query.libraryId, true)
                    }
                } catch (error: Exception) {
                    throw JellyfinException(JellyfinFailureKind.PARSE_ERROR, "Invalid collection ancestry", error)
                }
            }
        }
        page.copy(entries = scoped)
    }

    /** Folder fallback is admitted only after authenticated selected-library ancestry validation. */
    suspend fun getCatalogueFolder(
        config: JellyfinServerConfig, auth: JellyfinAuth, libraryId: String, folderId: String,
    ): JellyfinFolderIdentity? = withContext(Dispatchers.IO) {
        if (folderId == libraryId) return@withContext null
        val folderRequest = authorizedRequestBuilder(
            JellyfinEndpoints.itemDetails(config.baseUrl, auth.userId, folderId), auth).get().build()
        val folder = executeRequest(folderRequest) { response ->
            if (response.code in listOf(403, 404)) return@executeRequest null
            if (!response.isSuccessful) mapHttpError(response.code, "Cannot verify catalogue folder")
            val obj = JSONObject(response.body?.string().orEmpty())
            if (!obj.optString("Id").equals(folderId, true) || !obj.optBoolean("IsFolder", false)) null
            else obj.textOrNull("Name")
        } ?: return@withContext null
        val ancestorsRequest = authorizedRequestBuilder(
            JellyfinCatalogueQuery.ancestorsUrl(config.baseUrl, auth.userId, folderId), auth).get().build()
        val belongs = executeRequest(ancestorsRequest) { response ->
            if (response.code in listOf(403, 404)) return@executeRequest false
            if (!response.isSuccessful) mapHttpError(response.code, "Cannot verify catalogue folder scope")
            val array = org.json.JSONArray(response.body?.string().orEmpty())
            (0 until array.length()).any { array.getJSONObject(it).optString("Id").equals(libraryId, true) }
        }
        if (belongs) JellyfinFolderIdentity(folderId, folder, libraryId) else null
    }

    /** Fetches metadata only; never downloads an archive or reports reading progress. */
    /** Missing progress remains unknown, so synchronization cannot infer a reset from defaults. */
    suspend fun getProgress(
        config: JellyfinServerConfig,
        auth: JellyfinAuth,
        itemId: String,
    ): JellyfinProgressSnapshot = withContext(Dispatchers.IO) {
        val request = authorizedRequestBuilder(
            JellyfinEndpoints.itemDetails(config.baseUrl, auth.userId, itemId), auth,
        ).get().build()
        executeRequest(request) { response ->
            if (!response.isSuccessful) mapHttpError(response.code, "Could not read the server position")
            try {
                val item = JSONObject(response.body?.string().orEmpty())
                require(item.getString("Id").equals(itemId, ignoreCase = true)) { "Unexpected progress item" }
                parseProgress(item)
            } catch (error: Exception) {
                throw JellyfinException(JellyfinFailureKind.PARSE_ERROR, "Invalid server reading position", error)
            }
        }
    }

    private fun parseProgress(item: JSONObject): JellyfinProgressSnapshot {
        val data = item.optJSONObject("UserData")
        val ticks = when (val value = data?.opt("PlaybackPositionTicks")) {
            is Long -> value
            is Int -> value.toLong()
            else -> null
        }?.takeIf { it >= 0 }
        val percentage = (data?.opt("PlayedPercentage") as? Number)?.toDouble()
            ?.takeIf { it.isFinite() && it in 0.0..100.0 }
        return JellyfinProgressSnapshot(ticks, data?.opt("Played") as? Boolean, percentage, data?.textOrNull("LastPlayedDate"))
    }

    suspend fun getItemDetails(
        config: JellyfinServerConfig,
        auth: JellyfinAuth,
        itemId: String,
    ): JellyfinItemDetails = withContext(Dispatchers.IO) {
        val request = authorizedRequestBuilder(
            JellyfinEndpoints.itemDetails(config.baseUrl, auth.userId, itemId), auth,
        ).get().build()
        executeRequest(request) { response ->
            if (!response.isSuccessful) mapHttpError(response.code, "Failed to load comic details")
            try {
                val obj = JSONObject(response.body?.string().orEmpty())
                val item = parseItem(obj)
                require(item.id.equals(itemId, ignoreCase = true)) { "Unexpected detail item" }
                val genres = obj.optJSONArray("Genres")
                val userData = obj.optJSONObject("UserData")
                JellyfinItemDetails(
                    identity = JellyfinItemIdentity(auth.serverId, auth.userId, item.id),
                    item = item,
                    overview = obj.textOrNull("Overview"),
                    people = parsePeople(obj),
                    genres = buildList {
                        for (i in 0 until (genres?.length() ?: 0)) {
                            if (genres?.isNull(i) == false) {
                                genres.optString(i).trim().takeIf { it.isNotEmpty() }?.let { add(it) }
                            }
                        }
                    },
                    indexNumber = if (obj.isNull("IndexNumber")) null else obj.getInt("IndexNumber"),
                    parentIndexNumber = if (obj.isNull("ParentIndexNumber")) null else obj.getInt("ParentIndexNumber"),
                    etag = obj.textOrNull("Etag"),
                    playedPercentage = userData?.optDouble("PlayedPercentage")
                        ?.takeIf { it.isFinite() && it in 0.0..100.0 },
                    lastPlayedDate = userData?.textOrNull("LastPlayedDate"),
                )
            } catch (e: Exception) {
                throw JellyfinException(JellyfinFailureKind.PARSE_ERROR, "Failed to parse comic details", e)
            }
        }
    }

    private fun parsePeople(obj: JSONObject): List<JellyfinPerson> = buildList {
        val people = obj.optJSONArray("People") ?: return@buildList
        for (i in 0 until people.length()) {
            val person = people.optJSONObject(i) ?: continue
            val name = person.textOrNull("Name") ?: continue
            add(JellyfinPerson(name, person.textOrNull("Type"), person.textOrNull("Role"), person.textOrNull("Id")))
        }
    }

    private fun JSONObject.textOrNull(key: String): String? =
        if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() }

    private fun parseItem(obj: JSONObject, parentId: String? = null): JellyfinItem {
        val source = obj.optJSONArray("MediaSources")?.optJSONObject(0)
        val userData = obj.optJSONObject("UserData")
        return JellyfinItem(
            id = obj.getString("Id"),
            name = obj.textOrNull("Name") ?: "Untitled",
            isFolder = obj.optBoolean("IsFolder", false) ||
                obj.optString("Type").equals("Folder", ignoreCase = true),
            container = obj.textOrNull("Container") ?: source?.textOrNull("Container"),
            sizeBytes = obj.optLong("Size", 0L).takeIf { it > 0L }
                ?: (source?.optLong("Size", 0L) ?: 0L).coerceAtLeast(0L),
            imageTag = obj.optJSONObject("ImageTags")?.textOrNull("Primary"),
            path = obj.textOrNull("Path") ?: source?.textOrNull("Path"),
            parentId = obj.textOrNull("ParentId") ?: parentId,
            seriesName = obj.textOrNull("SeriesName"),
            productionYear = if (obj.isNull("ProductionYear")) null else obj.getInt("ProductionYear"),
            playbackPositionTicks = userData?.optLong("PlaybackPositionTicks", 0L) ?: 0L,
            played = userData?.optBoolean("Played", false) ?: false,
        )
    }

    suspend fun downloadToFile(
        config: JellyfinServerConfig,
        auth: JellyfinAuth,
        itemId: String,
        partFile: File,
        onProgress: (JellyfinTransferProgress) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ): Unit = withContext(Dispatchers.IO) {
        if (isCancelled()) throw CancellationException("Download cancelled before start")

        val url = JellyfinEndpoints.itemDownload(config.baseUrl, itemId)
        val request = authorizedRequestBuilder(url, auth).get().build()

        val call = okHttpClient.newCall(request)
        // Also interrupt a blocked headers/body read, not just the next copy-loop iteration.
        val cancellation = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { call.cancel() }
        }
        try {
            val response = call.execute()
            response.use { res ->
                if (!res.isSuccessful) {
                    mapHttpError(res.code, "Failed to download item: HTTP ${res.code}")
                }

                val body = res.body ?: throw JellyfinException(
                    JellyfinFailureKind.IO,
                    "Empty response body received from server",
                )

                val contentType = body.contentType()?.toString()?.lowercase().orEmpty()
                if (contentType.contains("text/html")) {
                    throw JellyfinException(
                        JellyfinFailureKind.PARSE_ERROR,
                        "Server returned HTML instead of comic binary (possible login redirect or proxy error)",
                    )
                }

                val totalBytes = body.contentLength()
                partFile.parentFile?.mkdirs()
                if (partFile.exists()) partFile.delete()

                val source = body.byteStream()
                FileOutputStream(partFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var transferred = 0L
                    var lastUpdate = System.currentTimeMillis()
                    var lastTransferred = 0L

                    onProgress(JellyfinTransferProgress(0L, totalBytes, 0L))

                    while (true) {
                        if (isCancelled()) {
                            call.cancel()
                            throw CancellationException("Jellyfin download cancelled")
                        }
                        val read = source.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        transferred += read

                        val now = System.currentTimeMillis()
                        if (now - lastUpdate >= 100 || transferred == totalBytes) {
                            val elapsedSec = (now - lastUpdate) / 1000.0
                            val speed = if (elapsedSec > 0) ((transferred - lastTransferred) / elapsedSec).toLong() else 0L
                            onProgress(JellyfinTransferProgress(transferred, totalBytes, speed))
                            lastUpdate = now
                            lastTransferred = transferred
                        }
                    }
                    output.flush()
                }

                if (totalBytes > 0L && partFile.length() != totalBytes) {
                    throw JellyfinException(
                        JellyfinFailureKind.IO,
                        "Download incomplete: expected $totalBytes bytes but got ${partFile.length()}",
                    )
                }

                onProgress(JellyfinTransferProgress(bytesTransferred = partFile.length(), totalBytes = partFile.length(), bytesPerSec = 0L))
            }
        } catch (t: Throwable) {
            if (partFile.exists()) partFile.delete()
            if (t is CancellationException) throw t
            if (t is JellyfinException) throw t
            throw mapNetworkException(t)
        } finally {
            cancellation.cancel()
        }
    }

    suspend fun loadCoverBitmap(
        config: JellyfinServerConfig,
        auth: JellyfinAuth,
        itemId: String,
        imageTag: String? = null,
        maxWidth: Int = 400,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val url = JellyfinEndpoints.primaryImage(
            baseUrl = config.baseUrl,
            itemId = itemId,
            maxWidth = maxWidth,
            tag = imageTag,
        )
        val request = authorizedRequestBuilder(url, auth).get().build()

        try {
            net.digitalbc.leafreader.data.library.CoverPerformanceTrace.event("network")
            executeRequest(request) { response ->
                if (!response.isSuccessful) return@executeRequest null
                val bytes = response.body?.bytes() ?: return@executeRequest null
                net.digitalbc.leafreader.data.library.CoverPerformanceTrace.decode {
                    net.digitalbc.leafreader.data.library.CoverImageDecoder.decode(bytes, maxWidth)
                }.also { net.digitalbc.leafreader.data.library.CoverPerformanceTrace.decoded(maxWidth, it) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            net.digitalbc.leafreader.data.library.CoverPerformanceTrace.event("failed")
            null
        }
    }

    suspend fun reportProgress(
        config: JellyfinServerConfig,
        auth: JellyfinAuth,
        itemId: String,
        pageIndex: Int,
    ): Unit = withContext(Dispatchers.IO) {
        val url = JellyfinEndpoints.sessionProgress(config.baseUrl)
        val jsonPayload = JSONObject().apply {
            put("ItemId", itemId)
            put("PositionTicks", JellyfinProgress.pageToTicks(pageIndex))
            put("CanSeek", true)
            put("IsPaused", false)
            put("PlayMethod", "DirectPlay")
        }.toString()

        val request = authorizedRequestBuilder(url, auth)
            .post(jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        executeRequest(request) { response ->
            if (!response.isSuccessful) {
                mapHttpError(response.code, "Failed to report progress to Jellyfin")
            }
        }
    }

    suspend fun markPlayedStatus(
        config: JellyfinServerConfig,
        auth: JellyfinAuth,
        itemId: String,
        played: Boolean,
    ): Unit = withContext(Dispatchers.IO) {
        val url = if (played) {
            JellyfinEndpoints.markPlayed(config.baseUrl, auth.userId, itemId)
        } else {
            JellyfinEndpoints.markUnplayed(config.baseUrl, auth.userId, itemId)
        }

        val requestBuilder = authorizedRequestBuilder(url, auth)
        val request = if (played) {
            requestBuilder.post(ByteArray(0).toRequestBody(null)).build()
        } else {
            requestBuilder.delete().build()
        }

        executeRequest(request) { response ->
            if (!response.isSuccessful) {
                mapHttpError(response.code, "Failed to update played status on Jellyfin")
            }
        }
    }

    private fun authorizedRequestBuilder(url: String, auth: JellyfinAuth): Request.Builder {
        val header = JellyfinEndpoints.authHeader(
            deviceId = deviceId,
            version = clientVersion,
            token = auth.accessToken,
        )
        return Request.Builder()
            .url(url)
            .header("Authorization", header)
            .header("X-Emby-Authorization", header)
    }

    private sealed interface Hop<out T> {
        data class Done<T>(val value: T) : Hop<T>
        data class Redirect(val request: Request) : Hop<Nothing>
    }

    private suspend fun <T> executeRequest(request: Request, block: (Response) -> T): T {
        var next = request
        var redirectCount = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val attempted = next
            when (val hop = executeHop(attempted) { response ->
                val redirect = if (response.isRedirect) redirectedRequest(attempted, response, redirectCount) else null
                if (redirect != null) Hop.Redirect(redirect) else Hop.Done(block(response))
            }) {
                is Hop.Done -> return hop.value
                is Hop.Redirect -> {
                    next = hop.request
                    redirectCount++
                }
            }
        }
    }

    private suspend fun <T> executeHop(request: Request, block: (Response) -> T): T {
        val transport = if (request.url.isHttps) okHttpClient else {
            if (!JellyfinServerConfig.isLocalNetworkHost(request.url.host)) {
                throw JellyfinException(JellyfinFailureKind.INVALID_CONFIGURATION,
                    "Unencrypted HTTP requires a local Jellyfin destination")
            }
            localHttpClient
        }
        return suspendCancellableCoroutine { continuation ->
            val call = transport.newCall(request)
            continuation.invokeOnCancellation {
                call.cancel()
            }
            call.enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use(block)
                        continuation.resume(result)
                    } catch (t: Throwable) {
                        val mapped = if (t is JellyfinException) t else mapNetworkException(t)
                        continuation.resumeWithException(mapped)
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isCancelled) return
                    val mapped = if (e is JellyfinException) e else mapNetworkException(e)
                    continuation.resumeWithException(mapped)
                }
            })
        }
    }

    private fun mapHttpError(code: Int, defaultMessage: String): Nothing {
        val kind = when (code) {
            401 -> JellyfinFailureKind.AUTHENTICATION
            403 -> JellyfinFailureKind.ACCESS_DENIED
            404 -> JellyfinFailureKind.NOT_FOUND
            in 500..599 -> JellyfinFailureKind.SERVER_ERROR
            else -> JellyfinFailureKind.IO
        }
        throw JellyfinException(kind, "$defaultMessage (HTTP $code)")
    }

    private fun mapNetworkException(t: Throwable): JellyfinException {
        if (t is JellyfinException) return t
        val kind = when (t) {
            is SocketTimeoutException -> JellyfinFailureKind.TIMEOUT
            is UnknownHostException -> JellyfinFailureKind.UNAVAILABLE
            is IOException -> JellyfinFailureKind.IO
            else -> JellyfinFailureKind.UNKNOWN
        }
        return JellyfinException(kind, t.message ?: "Network operation failed", t)
    }

    private fun redirectedRequest(request: Request, response: Response, redirectCount: Int): Request? {
        if (redirectCount >= 3) {
            throw JellyfinException(JellyfinFailureKind.INVALID_CONFIGURATION, "Too many redirects")
        }
        if (request.method.equals("POST", ignoreCase = true)) {
            throw JellyfinException(JellyfinFailureKind.AUTHENTICATION,
                "Redirect on POST request rejected for credential security (HTTP "+response.code+")")
        }
        val location = response.header("Location")?.takeIf { it.isNotBlank() } ?: return null
        val targetUrl = request.url.resolve(location)
            ?: throw JellyfinException(JellyfinFailureKind.INVALID_CONFIGURATION, "Invalid redirect Location")
        if (request.url.isHttps && !targetUrl.isHttps) {
            throw JellyfinException(JellyfinFailureKind.INVALID_CONFIGURATION,
                "Insecure HTTPS-to-HTTP redirect downgrade rejected")
        }
        if (!targetUrl.isHttps && !JellyfinServerConfig.isLocalNetworkHost(targetUrl.host)) {
            throw JellyfinException(JellyfinFailureKind.INVALID_CONFIGURATION,
                "Redirect to unencrypted HTTP on remote host rejected")
        }
        val sameOrigin = request.url.scheme == targetUrl.scheme &&
            request.url.host == targetUrl.host && request.url.port == targetUrl.port
        return request.newBuilder().url(targetUrl).apply {
            if (!sameOrigin) {
                removeHeader("Authorization")
                removeHeader("X-Emby-Authorization")
                removeHeader("X-MediaBrowser-Token")
            }
        }.build()
    }
}
