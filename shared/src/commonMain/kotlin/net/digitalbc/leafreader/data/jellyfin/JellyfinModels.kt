package net.digitalbc.leafreader.data.jellyfin

/**
 * Server connection configuration.
 *
 * Enforces URL normalization, local-network checks, and security requirements.
 * Tokens and passwords must never be stored here; they belong to native Keystore storage.
 */
class JellyfinServerConfig(
    baseUrl: String,
    val allowInsecureHttp: Boolean = false,
) {
    val baseUrl: String = normalizeUrl(baseUrl)
    val host: String
    val isHttps: Boolean

    init {
        require(this.baseUrl.startsWith("http://") || this.baseUrl.startsWith("https://")) {
            "URL must use http or https scheme"
        }
        isHttps = this.baseUrl.startsWith("https://")
        val hostPart = extractHost(this.baseUrl)
        require(hostPart.isNotBlank()) { "Invalid host in Jellyfin URL" }
        host = hostPart

        if (!isHttps) {
            val isLocal = isLocalNetworkHost(hostPart)
            require(isLocal) {
                "Plain HTTP is only permitted on local networks (LAN). Remote servers must use HTTPS."
            }
            require(allowInsecureHttp) {
                "Unencrypted HTTP on local network requires explicit confirmation."
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is JellyfinServerConfig) return false
        return baseUrl == other.baseUrl && allowInsecureHttp == other.allowInsecureHttp
    }

    override fun hashCode(): Int {
        var result = baseUrl.hashCode()
        result = 31 * result + allowInsecureHttp.hashCode()
        return result
    }

    override fun toString(): String =
        "JellyfinServerConfig(baseUrl='$baseUrl', allowInsecureHttp=$allowInsecureHttp, host='$host', isHttps=$isHttps)"

    companion object {
        private val LOCAL_IP_PATTERN = Regex(
            "^(" +
                "127\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}|" +
                "10\\.[0-9]{1,3}\\.[0-9]{1,3}\\.[0-9]{1,3}|" +
                "192\\.168\\.[0-9]{1,3}\\.[0-9]{1,3}|" +
                "172\\.(1[6-9]|2[0-9]|3[0-1])\\.[0-9]{1,3}\\.[0-9]{1,3}" +
            ")$"
        )

        fun isLocalNetworkHost(host: String): Boolean {
            val h = host.trim().lowercase().removePrefix("[").removeSuffix("]")
            if (h == "localhost" || h == "::1" || h.endsWith(".local") || h.endsWith(".lan") || h.endsWith(".home.arpa")) {
                return true
            }
            return LOCAL_IP_PATTERN.matches(h)
        }

        fun normalizeUrl(input: String): String {
            var url = input.trim()
            if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                // If input starts with a local hostname or IP, default to http:// only if specified, otherwise https://
                url = if (isLocalNetworkHost(extractHost("http://$url"))) "http://$url" else "https://$url"
            }
            val scheme = if (url.startsWith("https://", ignoreCase = true)) "https://" else "http://"
            val afterScheme = url.substring(scheme.length)
            val parts = afterScheme.split('/', limit = 2)
            val hostAndPort = parts[0].trim().lowercase()
            val path = if (parts.size > 1) {
                val p = parts[1].trim().trimEnd('/')
                if (p.isNotEmpty()) "/$p" else ""
            } else ""
            return "$scheme$hostAndPort$path"
        }

        fun extractHost(url: String): String {
            val afterScheme = url.substringAfter("://")
            val hostAndPort = afterScheme.substringBefore('/')
            return if (hostAndPort.startsWith("[")) {
                hostAndPort.substringAfter('[').substringBefore(']')
            } else {
                hostAndPort.substringBefore(':')
            }
        }
    }
}

/**
 * Authentication tokens and identifiers received from Jellyfin login.
 * Never log or serialize these to database, exports, or backups.
 */
data class JellyfinAuth(
    val accessToken: String,
    val userId: String,
    val serverId: String,
    val username: String,
    val serverName: String? = null,
) {
    init {
        require(accessToken.isNotBlank()) { "Access token cannot be blank" }
        require(userId.isNotBlank()) { "User ID cannot be blank" }
        require(serverId.isNotBlank()) { "Server ID cannot be blank" }
    }
}

/** Top-level library or view (e.g. Comics, Manga, Books). */
data class JellyfinView(
    val id: String,
    val name: String,
    val collectionType: String? = null,
)

/** Represents a book or folder inside Jellyfin library. */
data class JellyfinItem(
    val id: String,
    val name: String,
    val isFolder: Boolean,
    val container: String? = null,
    val sizeBytes: Long = 0L,
    val imageTag: String? = null,
    val path: String? = null,
    val parentId: String? = null,
    val seriesName: String? = null,
    val productionYear: Int? = null,
    val playbackPositionTicks: Long = 0L,
    val played: Boolean = false,
) {
    /**
     * Supported formats for Leaf reading: CBZ, CBR, PDF, ZIP, RAR.
     * EPUB is explicitly NOT supported by the offline reader and must not be imported.
     */
    val isImportable: Boolean
        get() {
            if (isFolder) return false
            val ext = container?.lowercase()
                ?: path?.substringAfterLast('.', "")?.lowercase()
                ?: name.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "cbz", "cbr", "pdf", "zip", "rar" -> true
                else -> false
            }
        }

    val fileExtension: String
        get() {
            val ext = container?.lowercase()
                ?: path?.substringAfterLast('.', "")?.lowercase()
                ?: name.substringAfterLast('.', "").lowercase()
            return ext.ifBlank { "cbz" }
        }
}

/** Download progress tracking. */
data class JellyfinTransferProgress(
    val bytesTransferred: Long,
    val totalBytes: Long,
    val bytesPerSec: Long,
) {
    val isIndeterminate: Boolean get() = totalBytes <= 0L
    val fraction: Float
        get() = if (isIndeterminate) 0f
        else (bytesTransferred.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
}

enum class JellyfinFailureKind {
    INVALID_CONFIGURATION,
    AUTHENTICATION,
    ACCESS_DENIED,
    NOT_FOUND,
    SERVER_ERROR,
    UNAVAILABLE,
    TIMEOUT,
    UNSUPPORTED,
    IO,
    PARSE_ERROR,
    UNKNOWN,
}

/** Tick conversions and calculations for Jellyfin comic/book playback. */
object JellyfinProgress {
    const val TICKS_PER_PAGE: Long = 10_000L

    fun pageToTicks(pageIndex: Int): Long =
        (pageIndex.coerceAtLeast(0).toLong()) * TICKS_PER_PAGE

    fun ticksToPage(ticks: Long, maxPages: Int? = null): Int {
        val page = (ticks / TICKS_PER_PAGE).toInt()
        return if (maxPages != null && maxPages > 0) page.coerceIn(0, maxPages - 1) else page.coerceAtLeast(0)
    }
}

object JellyfinEndpoints {
    fun authenticateByName(baseUrl: String): String =
        "${baseUrl.trimEnd('/')}/Users/AuthenticateByName"

    fun userViews(baseUrl: String, userId: String): String =
        "${baseUrl.trimEnd('/')}/Users/$userId/Views"

    fun userItems(
        baseUrl: String,
        userId: String,
        parentId: String? = null,
        startIndex: Int = 0,
        limit: Int = 100,
    ): String {
        val base = "${baseUrl.trimEnd('/')}/Users/$userId/Items"
        val params = mutableListOf(
            "StartIndex=$startIndex",
            "Limit=$limit",
            "Fields=PrimaryImageAspectRatio,Overview,Path,MediaSources",
            "IncludeItemTypes=Book,Folder",
            "Recursive=false",
            "SortBy=SortName",
            "SortOrder=Ascending",
        )
        if (!parentId.isNullOrBlank()) {
            params.add("ParentId=$parentId")
        }
        return "$base?${params.joinToString("&")}"
    }

    fun itemDownload(baseUrl: String, itemId: String): String =
        "${baseUrl.trimEnd('/')}/Items/$itemId/Download"

    fun sessionProgress(baseUrl: String): String =
        "${baseUrl.trimEnd('/')}/Sessions/Playing/Progress"

    fun sessionStopped(baseUrl: String): String =
        "${baseUrl.trimEnd('/')}/Sessions/Playing/Stopped"

    fun markPlayed(baseUrl: String, userId: String, itemId: String): String =
        "${baseUrl.trimEnd('/')}/Users/$userId/PlayedItems/$itemId"

    fun markUnplayed(baseUrl: String, userId: String, itemId: String): String =
        "${baseUrl.trimEnd('/')}/Users/$userId/PlayedItems/$itemId"

    fun primaryImage(
        baseUrl: String,
        itemId: String,
        maxWidth: Int? = null,
        tag: String? = null,
    ): String {
        val base = "${baseUrl.trimEnd('/')}/Items/$itemId/Images/Primary"
        val params = mutableListOf<String>()
        if (maxWidth != null && maxWidth > 0) params.add("maxWidth=$maxWidth")
        if (!tag.isNullOrBlank()) params.add("tag=$tag")
        return if (params.isEmpty()) base else "$base?${params.joinToString("&")}"
    }

    fun authHeader(
        clientName: String = "Leaf",
        deviceName: String = "Android",
        deviceId: String,
        version: String = "0.3.7",
        token: String? = null,
    ): String {
        val parts = mutableListOf(
            "Client=\"$clientName\"",
            "Device=\"$deviceName\"",
            "DeviceId=\"$deviceId\"",
            "Version=\"$version\"",
        )
        if (!token.isNullOrBlank()) {
            parts.add("Token=\"$token\"")
        }
        return "MediaBrowser ${parts.joinToString(", ")}"
    }
}
