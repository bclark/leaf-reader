package net.digitalbc.leafreader.data.library

import androidx.room.Room
import kotlinx.coroutines.runBlocking
import net.digitalbc.leafreader.data.db.AppDatabase
import net.digitalbc.leafreader.data.db.ComicDao
import net.digitalbc.leafreader.data.db.RemoteComicLinkDao
import net.digitalbc.leafreader.data.jellyfin.JellyfinAuth
import net.digitalbc.leafreader.data.jellyfin.JellyfinClient
import net.digitalbc.leafreader.data.jellyfin.JellyfinCredentialStore
import net.digitalbc.leafreader.data.jellyfin.JellyfinItem
import net.digitalbc.leafreader.data.settings.AppSettings
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class LibraryRepositoryJellyfinTest {

    private lateinit var server: MockWebServer
    private lateinit var db: AppDatabase
    private lateinit var comicDao: ComicDao
    private lateinit var linkDao: RemoteComicLinkDao
    private lateinit var repository: LibraryRepository
    private lateinit var settings: AppSettings
    private lateinit var credStore: JellyfinCredentialStore

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()

        val context = RuntimeEnvironment.getApplication()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        comicDao = db.comicDao()
        linkDao = db.remoteComicLinkDao()
        settings = AppSettings(context)
        credStore = JellyfinCredentialStore(context)
        credStore.clear()

        val client = JellyfinClient(deviceId = "test-device", clientVersion = "0.3.7")

        repository = LibraryRepository(
            context = context,
            dao = comicDao,
            settings = settings,
            readingListDao = db.readingListDao(),
            db = db,
            remoteComicLinkDao = linkDao,
            jellyfinClient = client,
            jellyfinCredentialStore = credStore,
        )
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
        credStore.clear()
    }

    private fun baseUrl(): String = server.url("/").toString().trimEnd('/')

    private fun createValidCbzBytes(): ByteArray {
        val png1x1 = byteArrayOf(
            0x89.toByte(), 0x50.toByte(), 0x4e.toByte(), 0x47.toByte(), 0x0d.toByte(), 0x0a.toByte(), 0x1a.toByte(), 0x0a.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0d.toByte(), 0x49.toByte(), 0x48.toByte(), 0x44.toByte(), 0x52.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x01.toByte(),
            0x08.toByte(), 0x06.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x1f.toByte(), 0x15.toByte(), 0xc4.toByte(),
            0x89.toByte(), 0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x0a.toByte(), 0x49.toByte(), 0x44.toByte(), 0x41.toByte(),
            0x54.toByte(), 0x78.toByte(), 0x9c.toByte(), 0x63.toByte(), 0x00.toByte(), 0x01.toByte(), 0x00.toByte(), 0x00.toByte(),
            0x05.toByte(), 0x00.toByte(), 0x01.toByte(), 0x0d.toByte(), 0x0a.toByte(), 0x2d.toByte(), 0xb4.toByte(), 0x00.toByte(),
            0x00.toByte(), 0x00.toByte(), 0x00.toByte(), 0x49.toByte(), 0x45.toByte(), 0x4e.toByte(), 0x44.toByte(), 0xae.toByte(),
            0x42.toByte(), 0x60.toByte(), 0x82.toByte()
        )
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("001.png"))
            zos.write(png1x1)
            zos.closeEntry()
        }
        return baos.toByteArray()
    }

    @Test
    fun connectAndDisconnectJellyfin() = runBlocking {
        val mockJson = """
            {
                "AccessToken": "test-tok-999",
                "ServerId": "srv-99",
                "User": { "Id": "u-1", "Name": "Bryan" },
                "ServerInfo": { "Name": "My Server" }
            }
        """.trimIndent()
        server.enqueue(MockResponse().setResponseCode(200).setBody(mockJson))

        val result = repository.connectJellyfin(
            serverUrl = baseUrl(),
            allowInsecureHttp = true,
            username = "Bryan",
            password = "pwd",
        )
        assertTrue(result.isSuccess)
        val auth = result.getOrNull()
        assertNotNull(auth)
        assertEquals("test-tok-999", auth?.accessToken)
        assertEquals("u-1", auth?.userId)
        assertTrue(settings.hasJellyfinConfig)
        assertEquals(baseUrl(), settings.jellyfinServerUrl)

        repository.disconnectJellyfin()
        assertFalse(settings.hasJellyfinConfig)
        assertNull(repository.getJellyfinAuth())
    }

    @Test
    fun importJellyfinComicSuccessAndLinkCreation() = runBlocking {
        val cbzBytes = createValidCbzBytes()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/octet-stream")
                .setHeader("Content-Length", cbzBytes.size)
                .setBody(okio.Buffer().write(cbzBytes))
        )

        val auth = JellyfinAuth(
            accessToken = "tok-1",
            userId = "u-1",
            serverId = "srv-1",
            username = "Bryan",
        )
        credStore.saveAuth(auth)
        settings.jellyfinServerUrl = baseUrl()
        settings.jellyfinAllowInsecureHttp = true
        settings.jellyfinUserId = "u-1"

        val item = JellyfinItem(
            id = "item-cbz-1",
            name = "Spider-Man #01.cbz",
            isFolder = false,
            container = "cbz",
            parentId = "folder-heroes",
            imageTag = "tag-cover",
        )

        val importResult = repository.importJellyfinComic(item)
        assertTrue("Import must succeed: ${importResult.exceptionOrNull()?.message}", importResult.isSuccess)
        val comicId = importResult.getOrThrow()

        val comic = comicDao.getById(comicId)
        assertNotNull(comic)
        assertEquals("Spider-Man #01", comic?.title)
        assertEquals("jellyfin", comic?.source)
        assertEquals(1, comic?.pageCount)
        assertTrue(File(comic!!.filePath).exists())

        // Verify RemoteComicLink was created
        val link = linkDao.findLink("jellyfin", "srv-1", "u-1", "item-cbz-1")
        assertNotNull(link)
        assertEquals(comicId, link?.comicId)
        assertEquals("folder-heroes", link?.parentId)
        assertEquals("tag-cover", link?.remoteVersionTag)

        // Attempting to import again should return existing ID without network request
        val repeatResult = repository.importJellyfinComic(item)
        assertTrue(repeatResult.isSuccess)
        assertEquals(comicId, repeatResult.getOrNull())
    }

    @Test
    fun importRejectsEpub() = runBlocking {
        val auth = JellyfinAuth("tok", "u", "srv", "Bryan")
        credStore.saveAuth(auth)
        settings.jellyfinServerUrl = baseUrl()
        settings.jellyfinAllowInsecureHttp = true
        settings.jellyfinUserId = "u"

        val epubItem = JellyfinItem(
            id = "epub-1",
            name = "Book.epub",
            isFolder = false,
            container = "epub",
        )
        val result = repository.importJellyfinComic(epubItem)
        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull()?.message?.contains("not supported") == true)
    }

    @Test
    fun importRejectsCorruptFileAndCleansUp() = runBlocking {
        val corruptBytes = "Not a valid zip or comic file".toByteArray()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/octet-stream")
                .setHeader("Content-Length", corruptBytes.size)
                .setBody(okio.Buffer().write(corruptBytes))
        )

        val auth = JellyfinAuth("tok", "u", "srv", "Bryan")
        credStore.saveAuth(auth)
        settings.jellyfinServerUrl = baseUrl()
        settings.jellyfinAllowInsecureHttp = true
        settings.jellyfinUserId = "u"

        val item = JellyfinItem(
            id = "corrupt-1",
            name = "Corrupt.cbz",
            isFolder = false,
            container = "cbz",
        )

        val result = repository.importJellyfinComic(item)
        assertFalse("Import of corrupt archive must fail", result.isSuccess)

        // Ensure no link or comic was inserted
        assertNull(linkDao.findLink("jellyfin", "srv", "u", "corrupt-1"))
        assertEquals(0, comicDao.getAll().size)
    }
}
