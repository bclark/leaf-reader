package net.digitalbc.leafreader.data.jellyfin

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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
    private val okHttpClient: OkHttpClient = customHttpClient ?: OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .addInterceptor(SafeRedirectInterceptor())
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
                val serverId = json.optString("ServerId", "").ifBlank {
                    json.optJSONObject("SessionInfo")?.optString("ServerId", "") ?: "jellyfin-server"
                }

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
                    val obj = itemsArray.getJSONObject(i)
                    val type = obj.optString("Type", "")
                    val isFolder = obj.optBoolean("IsFolder", false) || type.equals("Folder", ignoreCase = true)
                    val imageTags = obj.optJSONObject("ImageTags")
                    val primaryTag = imageTags?.optString("Primary", null)

                    val containerVal = if (obj.has("Container")) obj.getString("Container").ifBlank { null } else null
                    val pathVal = if (obj.has("Path")) obj.getString("Path").ifBlank { null } else null
                    val parentVal = if (obj.has("ParentId")) obj.getString("ParentId").ifBlank { null } else parentId
                    val seriesVal = if (obj.has("SeriesName")) obj.getString("SeriesName").ifBlank { null } else null

                    val userData = obj.optJSONObject("UserData")
                    val playbackTicks = userData?.optLong("PlaybackPositionTicks", 0L) ?: 0L
                    val played = userData?.optBoolean("Played", false) ?: false

                    items.add(
                        JellyfinItem(
                            id = obj.getString("Id"),
                            name = obj.optString("Name", "Untitled"),
                            isFolder = isFolder,
                            container = containerVal,
                            sizeBytes = obj.optLong("Size", 0L),
                            imageTag = primaryTag,
                            path = pathVal,
                            parentId = parentVal,
                            seriesName = seriesVal,
                            productionYear = if (obj.has("ProductionYear")) obj.getInt("ProductionYear") else null,
                            playbackPositionTicks = playbackTicks,
                            played = played,
                        )
                    )
                }
                JellyfinItemsResult(items = items, totalRecordCount = total, startIndex = startIndex)
            } catch (e: Exception) {
                throw JellyfinException(JellyfinFailureKind.PARSE_ERROR, "Failed to parse library items", e)
            }
        }
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
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bytes = response.body?.bytes() ?: return@withContext null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to load cover for item $itemId: ${e.message}")
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

    private inline fun <T> executeRequest(request: Request, block: (Response) -> T): T {
        return try {
            okHttpClient.newCall(request).execute().use(block)
        } catch (t: Throwable) {
            if (t is JellyfinException) throw t
            throw mapNetworkException(t)
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
        val kind = when (t) {
            is SocketTimeoutException -> JellyfinFailureKind.TIMEOUT
            is UnknownHostException -> JellyfinFailureKind.UNAVAILABLE
            is IOException -> JellyfinFailureKind.IO
            else -> JellyfinFailureKind.UNKNOWN
        }
        return JellyfinException(kind, t.message ?: "Network operation failed", t)
    }

    private class SafeRedirectInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            var request = chain.request()
            var response = chain.proceed(request)
            var redirectCount = 0

            while (response.isRedirect) {
                if (redirectCount >= MAX_REDIRECTS) {
                    response.close()
                    throw JellyfinException(JellyfinFailureKind.INVALID_CONFIGURATION, "Too many redirects")
                }
                redirectCount++

                // Never follow redirects on POST requests (prevent credential or payload leaking)
                if (request.method.equals("POST", ignoreCase = true)) {
                    response.close()
                    throw JellyfinException(
                        JellyfinFailureKind.AUTHENTICATION,
                        "Redirect on POST request rejected for credential security (HTTP ${response.code})"
                    )
                }

                val location = response.header("Location")
                if (location.isNullOrBlank()) {
                    return response
                }

                val targetUrl = request.url.resolve(location)
                if (targetUrl == null) {
                    response.close()
                    throw JellyfinException(JellyfinFailureKind.INVALID_CONFIGURATION, "Invalid redirect Location: $location")
                }

                // Block HTTPS -> HTTP downgrade
                if (request.url.isHttps && !targetUrl.isHttps) {
                    response.close()
                    throw JellyfinException(
                        JellyfinFailureKind.INVALID_CONFIGURATION,
                        "Insecure HTTPS-to-HTTP redirect downgrade rejected"
                    )
                }

                val isSameOrigin = request.url.scheme.equals(targetUrl.scheme, ignoreCase = true) &&
                        request.url.host.equals(targetUrl.host, ignoreCase = true) &&
                        request.url.port == targetUrl.port

                val nextRequestBuilder = request.newBuilder().url(targetUrl)

                // If cross-origin, strip sensitive headers
                if (!isSameOrigin) {
                    nextRequestBuilder
                        .removeHeader("Authorization")
                        .removeHeader("X-Emby-Authorization")
                        .removeHeader("X-MediaBrowser-Token")
                }

                // If the redirect target is unencrypted HTTP, ensure it is restricted to local network
                if (!targetUrl.isHttps && !JellyfinServerConfig.isLocalNetworkHost(targetUrl.host)) {
                    response.close()
                    throw JellyfinException(
                        JellyfinFailureKind.INVALID_CONFIGURATION,
                        "Redirect to unencrypted HTTP on remote host rejected: ${targetUrl.host}"
                    )
                }

                response.close()
                request = nextRequestBuilder.build()
                response = chain.proceed(request)
            }

            return response
        }

        companion object {
            private const val MAX_REDIRECTS = 3
        }
    }

    companion object {
        private const val TAG = "JellyfinClient"
    }
}
