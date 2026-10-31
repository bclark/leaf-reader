package com.chakra.comicreader.data.smb

import com.chakra.comicreader.data.archive.NaturalOrderComparator
import com.chakra.comicreader.data.settings.AppSettings
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.protocol.commons.EnumWithValue
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import java.io.File
import java.util.EnumSet

data class SmbEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L,
)

object SmbClient {

    private val COMIC_EXTENSIONS = setOf("cbz", "cbr", "pdf", "zip", "rar")

    fun testConnection(settings: AppSettings) {
        require(settings.smbHost.isNotBlank()) { "Host / IP is required" }
        require(settings.smbShare.isNotBlank()) { "Share is required" }
        withShare(settings) { share, basePath ->
            share.list(basePath)
        }
    }

    fun listDirectory(settings: AppSettings, relativePath: String): List<SmbEntry> =
        withShare(settings) { share, basePath ->
            val fullPath = joinPath(basePath, relativePath)
            share.list(fullPath).mapNotNull { info ->
                val name = info.fileName
                if (name == "." || name == ".." || name.startsWith(".")) return@mapNotNull null
                val isDir = EnumWithValue.EnumUtils.isSet(
                    info.fileAttributes,
                    FileAttributes.FILE_ATTRIBUTE_DIRECTORY,
                )
                val ext = name.substringAfterLast('.', "").lowercase()
                if (!isDir && ext !in COMIC_EXTENSIONS) return@mapNotNull null
                val childRelPath = joinPath(relativePath, name)
                SmbEntry(
                    name = name,
                    path = childRelPath,
                    isDirectory = isDir,
                    sizeBytes = info.endOfFile,
                )
            }.sortedWith { a, b ->
                if (a.isDirectory != b.isDirectory) {
                    if (a.isDirectory) -1 else 1
                } else {
                    NaturalOrderComparator.compare(a.name, b.name)
                }
            }
        }

    fun downloadToFile(
        settings: AppSettings,
        entry: SmbEntry,
        dest: File,
        isActive: () -> Boolean = { true },
        onProgress: (Float) -> Unit = {},
    ) {
        withShare(settings) { share, basePath ->
            val fullPath = joinPath(basePath, entry.path)
            share.openFile(
                fullPath,
                EnumSet.of(AccessMask.GENERIC_READ),
                null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ),
                SMB2CreateDisposition.FILE_OPEN,
                null,
            ).use { remoteFile ->
                val total = entry.sizeBytes.takeIf { it > 0L }
                    ?: runCatching { remoteFile.fileInformation.standardInformation.endOfFile }.getOrDefault(0L)
                remoteFile.inputStream.use { input ->
                    dest.outputStream().use { output ->
                        val buf = ByteArray(64 * 1024)
                        var copied = 0L
                        var read: Int
                        while (input.read(buf).also { read = it } >= 0) {
                            if (!isActive()) {
                                throw java.util.concurrent.CancellationException("Download cancelled")
                            }
                            output.write(buf, 0, read)
                            copied += read
                            if (total > 0L) {
                                onProgress((copied.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
        }
    }

    private fun <T> withShare(settings: AppSettings, block: (DiskShare, String) -> T): T {
        val rawHost = settings.smbHost.removePrefix("smb://").trim().trim('/')
        val host = rawHost.substringBefore(':')
        val port = rawHost.substringAfter(':', "").toIntOrNull()
        val rawShare = settings.smbShare.replace('\\', '/').trim('/')
        val shareName = rawShare.substringBefore('/')
        val basePath = rawShare.substringAfter('/', "")

        val auth = if (settings.smbUsername.isBlank()) {
            AuthenticationContext.anonymous()
        } else {
            val userRaw = settings.smbUsername
            val domain = if ('\\' in userRaw) userRaw.substringBefore('\\') else ""
            val user = userRaw.substringAfter('\\')
            AuthenticationContext(user, settings.smbPassword.toCharArray(), domain)
        }

        SMBClient().use { client ->
            val connection = if (port != null) client.connect(host, port) else client.connect(host)
            connection.use { conn ->
                conn.authenticate(auth).use { session ->
                    (session.connectShare(shareName) as DiskShare).use { share ->
                        return block(share, basePath)
                    }
                }
            }
        }
    }

    private fun joinPath(parent: String, child: String): String {
        val p = parent.trim('/', '\\')
        val c = child.trim('/', '\\')
        return when {
            p.isEmpty() -> c
            c.isEmpty() -> p
            else -> "$p\\$c"
        }
    }
}
