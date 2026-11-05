package net.digitalbc.leafreader.data.smb

import kotlinx.coroutines.CancellationException
import net.digitalbc.leafreader.data.archive.ComicArchiveFactory
import net.digitalbc.leafreader.data.archive.ComicFormat
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Opt-in read-only smoke test. Values are read at runtime, never passed as Gradle arguments.
 * LEAF_SMB_LIVE_TEST=1 ./gradlew :app:testFullDebugUnitTest --tests '*SmbLiveTest'
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class SmbLiveTest {
    @Test fun configuredShareListsDownloadsAndCancelsWithoutPublishingPartialFiles() {
        assumeTrue(System.getenv("LEAF_SMB_LIVE_TEST") == "1")
        val envFile = listOf(File(".env"), File("../.env")).firstOrNull { it.isFile }
            ?: throw AssertionError("Private SMB test configuration is missing")
        // Literal dotenv values only: never source/execute the credentials file.
        val values = envFile.readLines().mapNotNull { line ->
            val text = line.trim().removePrefix("export ")
            if (text.isEmpty() || text.startsWith("#") || '=' !in text) null else {
                val value = text.substringAfter('=').trim()
                val unquoted = if (value.length >= 2 && value.first() == value.last() &&
                    (value.first() == '"' || value.first() == '\'')) value.substring(1, value.length - 1) else value
                text.substringBefore('=').trim() to unquoted
            }
        }.toMap()
        var stage = "configuration"
        val destination = File.createTempFile("leaf-smb-live", ".comic")
        try {
            val config = SmbConnectionConfig.fromInputs(values["SMB_SERVER"].orEmpty(),
                values["SMB_PATH"].orEmpty(), values["SMB_USERNAME"].orEmpty())
            val password = values["SMB_PASSWORD"].orEmpty()
            stage = "connect"
            SmbClient.testConnection(config, password)
            stage = "list"
            val root = SmbClient.listDirectory(config, password, "")
            val provided = values.filterKeys { it.startsWith("FILE") }.values.filter { it.isNotBlank() }
            if (provided.isEmpty()) throw AssertionError("No representative SMB test files configured")
            val candidates = provided.mapNotNull { raw ->
                val unix = raw.replace('\\', '/').trim('/')
                val name = unix.substringAfterLast('/')
                root.firstOrNull { it.name == name && !it.isDirectory } ?: run {
                    var relative = unix.removePrefix(config.share + "/")
                    if (config.basePath.isNotEmpty()) relative = relative.removePrefix(config.basePath + "/")
                    val path = SmbPaths.normalize(relative)
                    SmbClient.listDirectory(config, password, SmbPaths.parent(path))
                        .firstOrNull { it.path == path && !it.isDirectory }
                }
            }
            // Filename-only samples may live below the configured base folder.
            val found = candidates.toMutableList()
            val names = provided.map { it.replace('\\', '/').substringAfterLast('/') }.toSet()
            val folders = java.util.ArrayDeque(root.filter { it.isDirectory }.map { it.path })
            var inspected = 0
            while (found.none { it.sizeBytes > 0L } && folders.isNotEmpty() && inspected < 200) {
                val folder = folders.removeFirst()
                inspected++
                val listing = try { SmbClient.listDirectory(config, password, folder) }
                    catch (error: Exception) {
                        if (SmbErrors.kind(error) == SmbFailureKind.ACCESS_DENIED) continue else throw error
                    }
                found += listing.filter { !it.isDirectory && it.name in names }
                folders.addAll(listing.filter { it.isDirectory && it.path.count { c -> c == '/' } < 8 }.map { it.path })
            }
            val entry = found.filter { it.sizeBytes > 0L }.minByOrNull { it.sizeBytes }
                ?: throw AssertionError("No configured nonempty comic found (root entries=${root.size}, matched=${found.size}, folders checked=$inspected)")
            if (destination.usableSpace < entry.sizeBytes) throw AssertionError("Insufficient local space for smoke test")
            stage = "download"
            var finalBytes = 0L
            SmbClient.downloadToFile(config, password, entry, destination, { finalBytes = it.bytesTransferred })
            if (destination.length() != finalBytes || finalBytes <= 0) throw AssertionError("Downloaded length mismatch")
            if (ComicArchiveFactory.detectFormat(destination) == ComicFormat.UNKNOWN) throw AssertionError("Unsupported downloaded format")
            stage = "cancel"
            var cancelled = false
            try {
                SmbClient.downloadToFile(config, password, entry, destination, { cancelled = true }, { cancelled })
                throw AssertionError("Expected transfer cancellation")
            } catch (_: CancellationException) { }
            if (destination.exists()) throw AssertionError("Cancelled download left a partial file")
        } catch (error: Exception) {
            // No cause or native message: test reports must not expose private server paths/accounts.
            throw AssertionError("SMB live check failed during $stage (${SmbErrors.kind(error)})")
        } finally {
            destination.delete()
        }
    }
}
