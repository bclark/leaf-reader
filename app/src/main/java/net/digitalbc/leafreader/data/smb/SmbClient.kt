package net.digitalbc.leafreader.data.smb

import net.digitalbc.leafreader.data.settings.AppSettings
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.protocol.commons.EnumWithValue
import com.hierynomus.mssmb2.SMB2Dialect
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.share.DiskShare
import kotlinx.coroutines.CancellationException
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.EnumSet
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object SmbClient {
    fun configuration(settings: AppSettings): SmbConnectionConfig =
        SmbConnectionConfig.fromInputs(settings.smbHost, settings.smbShare, settings.smbUsername)

    fun testConnection(settings: AppSettings) = testConnection(configuration(settings), settings.smbPassword)

    fun testConnection(config: SmbConnectionConfig, password: String) {
        withShare(config, password, { false }) { share -> share.list(wirePath(config.basePath)) }
    }

    fun listDirectory(settings: AppSettings, relativePath: String, isCancelled: () -> Boolean = { false }): List<SmbEntry> =
        listDirectory(configuration(settings), settings.smbPassword, relativePath, isCancelled)

    fun listDirectory(config: SmbConnectionConfig, password: String, relativePath: String,
                      isCancelled: () -> Boolean = { false }): List<SmbEntry> {
        val directory = SmbPaths.normalize(relativePath)
        val fullPath = wirePath(SmbPaths.join(config.basePath, directory))
        return withShare(config, password, isCancelled) { share ->
            val entries = share.list(fullPath).mapNotNull { info ->
                val name = info.fileName
                if (!SmbPaths.isChildName(name) || name.startsWith('.')) return@mapNotNull null
                // Do not follow reparse points that could escape the configured base directory.
                if (EnumWithValue.EnumUtils.isSet(info.fileAttributes, FileAttributes.FILE_ATTRIBUTE_REPARSE_POINT)) return@mapNotNull null
                SmbEntry(name, SmbPaths.child(directory, name),
                    EnumWithValue.EnumUtils.isSet(info.fileAttributes, FileAttributes.FILE_ATTRIBUTE_DIRECTORY),
                    info.endOfFile.coerceAtLeast(0L))
            }
            SmbListing.prepare(directory, entries)
        }
    }

    fun downloadToFile(
        settings: AppSettings,
        entry: SmbEntry,
        dest: File,
        onProgress: (SmbTransferProgress) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ) = downloadToFile(configuration(settings), settings.smbPassword, entry, dest, onProgress, isCancelled)

    fun downloadToFile(
        config: SmbConnectionConfig,
        password: String,
        entry: SmbEntry,
        dest: File,
        onProgress: (SmbTransferProgress) -> Unit = {},
        isCancelled: () -> Boolean = { false },
    ) {
        require(!entry.isDirectory && SmbListing.isImportable(entry.name)) { "Not an importable comic" }
        val path = SmbPaths.normalize(entry.path)
        require(path.substringAfterLast('/') == entry.name) { "Invalid SMB entry path" }
        val fullPath = wirePath(SmbPaths.join(config.basePath, path))
        try {
            withShare(config, password, isCancelled) { share ->
                share.openFile(fullPath, EnumSet.of(AccessMask.GENERIC_READ), null,
                    EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ), SMB2CreateDisposition.FILE_OPEN, null).use { remote ->
                    // Stat again at open time: a server file may have changed since the listing.
                    val total = remote.fileInformation.standardInformation.endOfFile
                    remote.inputStream.use { input ->
                        BufferedOutputStream(FileOutputStream(dest), 1024 * 1024).use { output ->
                            copySmbStream(input, output, total, onProgress, isCancelled)
                        }
                    }
                }
            }
        } catch (error: Exception) {
            dest.delete()
            throw error
        }
    }

    private fun <T> withShare(config: SmbConnectionConfig, password: String,
                             isCancelled: () -> Boolean, block: (DiskShare) -> T): T {
        checkSmbCancellation(isCancelled)
        val auth = if (config.username.isBlank()) AuthenticationContext.anonymous()
                   else AuthenticationContext(config.username, password.toCharArray(), config.domain)
        val smbConfig = SmbConfig.builder()
            .withDialects(SMB2Dialect.SMB_3_1_1, SMB2Dialect.SMB_3_0_2, SMB2Dialect.SMB_3_0,
                SMB2Dialect.SMB_2_1, SMB2Dialect.SMB_2_0_2)
            .withReadBufferSize(1024 * 1024).withBufferSize(1024 * 1024)
            .withTimeout(60, TimeUnit.SECONDS).withReadTimeout(60, TimeUnit.SECONDS)
            .withMultiProtocolNegotiate(true).withSigningRequired(false).withDfsEnabled(false).build()

        try {
            SMBClient(smbConfig).use { client ->
                client.connect(config.host, config.port).use { connection ->
                    // Closing this operation's connection releases a blocked read on cancellation.
                    // Initial socket connection still has the finite SMBJ timeout.
                    val monitor = Executors.newSingleThreadScheduledExecutor { work ->
                        Thread(work, "leaf-smb-cancel").apply { isDaemon = true }
                    }
                    val poll = monitor.scheduleWithFixedDelay({
                        if (isCancelled()) runCatching { connection.close(true) }
                    }, 0, 100, TimeUnit.MILLISECONDS)
                    try {
                        checkSmbCancellation(isCancelled)
                        connection.authenticate(auth).use { session ->
                            (session.connectShare(config.share) as DiskShare).use { share ->
                                checkSmbCancellation(isCancelled)
                                val result = block(share)
                                checkSmbCancellation(isCancelled)
                                return result
                            }
                        }
                    } finally {
                        poll.cancel(true)
                        monitor.shutdownNow()
                    }
                }
            }
        } catch (error: Exception) {
            checkSmbCancellation(isCancelled)
            throw error
        }
    }

    private fun wirePath(path: String): String = path.replace('/', '\\')
}

internal fun checkSmbCancellation(isCancelled: () -> Boolean) {
    if (isCancelled()) throw CancellationException("SMB operation cancelled")
}
