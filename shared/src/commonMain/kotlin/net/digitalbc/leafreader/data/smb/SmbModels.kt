package net.digitalbc.leafreader.data.smb

/** Connection snapshot without credentials. Passwords belong to native credential storage. */
data class SmbConnectionConfig(
    val host: String,
    val port: Int,
    val share: String,
    val basePath: String,
    val username: String,
    val domain: String,
) {
    init {
        require(host.isNotBlank() && host.none { it.isWhitespace() || it in "/\\@?#[]" }) { "Invalid SMB host" }
        require(port in 1..65535) { "Invalid SMB port" }
        require(SmbPaths.isChildName(share)) { "Invalid SMB share" }
        require(basePath == SmbPaths.normalize(basePath)) { "Invalid SMB base directory" }
    }

    companion object {
        /** Accepts Android's existing host[:port] and share[/base] preference fields. */
        @Throws(IllegalArgumentException::class)
        fun fromInputs(hostInput: String, shareInput: String, usernameInput: String): SmbConnectionConfig {
            val endpoint = hostInput.trim().removePrefix("smb://").trimEnd('/')
            val host: String
            val portText: String?
            if (endpoint.startsWith("[")) {
                val end = endpoint.indexOf(']')
                require(end > 1) { "Invalid bracketed SMB host" }
                host = endpoint.substring(1, end)
                require(host.count { it == ':' } >= 2) { "Invalid IPv6 host" }
                val suffix = endpoint.substring(end + 1)
                require(suffix.isEmpty() || suffix.startsWith(":")) { "Invalid SMB port" }
                portText = if (suffix.isEmpty()) null else suffix.drop(1)
            } else {
                require(endpoint.count { it == ':' } <= 1) { "Use brackets around IPv6 hosts" }
                host = endpoint.substringBefore(':')
                portText = if (':' in endpoint) endpoint.substringAfter(':') else null
            }
            val port = if (portText == null) 445 else {
                require(portText.isNotEmpty() && portText.all { it in '0'..'9' }) { "Invalid SMB port" }
                portText.toIntOrNull() ?: throw IllegalArgumentException("Invalid SMB port")
            }
            val sharePath = SmbPaths.normalize(shareInput.trim().replace('\\', '/').trim('/'))
            require(sharePath.isNotEmpty()) { "SMB share is required" }
            val user = usernameInput.trim()
            return SmbConnectionConfig(host, port, sharePath.substringBefore('/'),
                sharePath.substringAfter('/', ""), user.substringAfter('\\'),
                if ('\\' in user) user.substringBefore('\\') else "")
        }
    }
}

/** Paths use '/' relative to the configured base directory. Zero size means unknown/empty. */
data class SmbEntry(val name: String, val path: String, val isDirectory: Boolean, val sizeBytes: Long = 0L)

/** Native adapters report bytes; derived progress is identical on both platforms. */
data class SmbTransferProgress(val bytesTransferred: Long, val totalBytes: Long, val bytesPerSec: Long) {
    val isIndeterminate: Boolean get() = totalBytes <= 0L
    val fraction: Float get() = if (isIndeterminate) 0f
        else (bytesTransferred.toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)
}

enum class SmbFailureKind { INVALID_CONFIGURATION, AUTHENTICATION, ACCESS_DENIED, UNAVAILABLE, TIMEOUT, UNSUPPORTED, IO, UNKNOWN }
enum class SmbImportPhase { DOWNLOADING, PREPARING, IMPORTING, COMPLETE }

object SmbImportProgress {
    /** Null means indeterminate. Completion is reported only after native library import succeeds. */
    fun fraction(completed: Int, count: Int, transfer: SmbTransferProgress, phase: SmbImportPhase): Float? {
        if (count <= 0) return null
        if (phase == SmbImportPhase.COMPLETE) return (completed.toFloat() / count).coerceIn(0f, 1f)
        if (transfer.isIndeterminate) return null
        // Reserve the final 5% of each file for validation/indexing/conversion.
        val fileFraction = when (phase) {
            SmbImportPhase.DOWNLOADING -> transfer.fraction * 0.95f
            else -> 0.95f
        }
        return ((completed + fileFraction) / count).coerceIn(0f, 1f)
    }
}
