package net.digitalbc.leafreader.data.library

import androidx.room.Room
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.digitalbc.leafreader.data.db.AppDatabase
import net.digitalbc.leafreader.data.db.ComicDao
import net.digitalbc.leafreader.data.db.JellyfinSyncOutboxDao
import net.digitalbc.leafreader.data.db.JellyfinSyncOutboxEntity
import net.digitalbc.leafreader.data.db.RemoteComicLinkDao
import net.digitalbc.leafreader.data.jellyfin.JellyfinAuth
import net.digitalbc.leafreader.data.jellyfin.JellyfinClient
import net.digitalbc.leafreader.data.jellyfin.JellyfinCredentialStore
import net.digitalbc.leafreader.data.jellyfin.JellyfinItem
import net.digitalbc.leafreader.data.jellyfin.JellyfinServerConfig
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
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class LibraryRepositoryJellyfinTest {

    private lateinit var server: MockWebServer
    private lateinit var db: AppDatabase
    private lateinit var comicDao: ComicDao
    private lateinit var linkDao: RemoteComicLinkDao
    private lateinit var outboxDao: JellyfinSyncOutboxDao
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
        outboxDao = db.jellyfinSyncOutboxDao()
        settings = AppSettings(context)
        val testKey = javax.crypto.spec.SecretKeySpec("LeafTestKey12345678901234567890!".toByteArray(), "AES")
        credStore = JellyfinCredentialStore(context, keyProvider = { testKey })
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

    private fun createValidCbzBytes(pageCount: Int = 1): ByteArray {
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
            for (i in 1..pageCount) {
                zos.putNextEntry(ZipEntry(String.format("page_%03d.png", i)))
                zos.write(png1x1)
                zos.closeEntry()
            }
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

    @Test
    fun importRejectsTruncatedZipArchiveAndCleansUp() = runBlocking {
        // ZIP magic bytes "PK\x03\x04" followed by incomplete bytes
        val truncatedZip = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x0A, 0x00, 0x00, 0x00)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/octet-stream")
                .setHeader("Content-Length", truncatedZip.size)
                .setBody(okio.Buffer().write(truncatedZip))
        )

        val auth = JellyfinAuth("tok", "u", "srv", "Bryan")
        credStore.saveAuth(auth)
        settings.jellyfinServerUrl = baseUrl()
        settings.jellyfinAllowInsecureHttp = true
        settings.jellyfinUserId = "u"

        val item = JellyfinItem(
            id = "truncated-1",
            name = "Truncated.cbz",
            isFolder = false,
            container = "cbz",
        )

        val result = repository.importJellyfinComic(item)
        assertFalse("Import of truncated archive must fail", result.isSuccess)
        assertNull(linkDao.findLink("jellyfin", "srv", "u", "truncated-1"))
        assertEquals(0, comicDao.getAll().size)
    }

    @Test
    fun importJellyfinComicSeedsInitialProgressFromRemote() = runBlocking {
        val cbzBytes = createValidCbzBytes(pageCount = 5)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/octet-stream")
                .setHeader("Content-Length", cbzBytes.size)
                .setBody(okio.Buffer().write(cbzBytes))
        )

        val auth = JellyfinAuth("tok", "u", "srv", "Bryan")
        credStore.saveAuth(auth)
        settings.jellyfinServerUrl = baseUrl()
        settings.jellyfinAllowInsecureHttp = true
        settings.jellyfinUserId = "u"

        val item = JellyfinItem(
            id = "seed-item-1",
            name = "SeedComic.cbz",
            isFolder = false,
            container = "cbz",
            playbackPositionTicks = 30_000L, // page 3
            played = false,
        )

        val result = repository.importJellyfinComic(item)
        assertTrue(result.isSuccess)
        val comicId = result.getOrThrow()
        val comic = comicDao.getById(comicId)
        assertNotNull(comic)
        assertEquals(3, comic?.lastPage)
    }

    @Test
    fun saveProgressSyncsJellyfinProgressForLinkedComic() = runBlocking {
        val cbzBytes = createValidCbzBytes(pageCount = 10)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/octet-stream")
                .setHeader("Content-Length", cbzBytes.size)
                .setBody(okio.Buffer().write(cbzBytes))
        )

        val auth = JellyfinAuth("tok", "u", "srv", "Bryan")
        credStore.saveAuth(auth)
        settings.jellyfinServerUrl = baseUrl()
        settings.jellyfinAllowInsecureHttp = true
        settings.jellyfinUserId = "u"

        val item = JellyfinItem(
            id = "sync-item-1",
            name = "SyncComic.cbz",
            isFolder = false,
            container = "cbz",
        )

        val result = repository.importJellyfinComic(item)
        assertTrue(result.isSuccess)
        val comicId = result.getOrThrow()

        // Enqueue progress response
        server.enqueue(MockResponse().setResponseCode(204))

        // Save progress with debounceMs = 0L to execute immediately
        repository.syncJellyfinProgressDirect(comicId, page = 4)

        // Consume the download request first
        server.takeRequest()

        // Now take the progress sync request
        val recorded = server.takeRequest()
        assertEquals("/Sessions/Playing/Progress", recorded.path)
        assertEquals("POST", recorded.method)
        val bodyStr = recorded.body.readUtf8()
        val json = org.json.JSONObject(bodyStr)
        assertEquals("sync-item-1", json.getString("ItemId"))
        assertEquals(40000L, json.getLong("PositionTicks"))
    }

    @Test
    fun saveProgressMarksPlayedWhenReachingLastPage() = runBlocking {
        val cbzBytes = createValidCbzBytes(pageCount = 5)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/octet-stream")
                .setHeader("Content-Length", cbzBytes.size)
                .setBody(okio.Buffer().write(cbzBytes))
        )

        val auth = JellyfinAuth("tok", "u", "srv", "Bryan")
        credStore.saveAuth(auth)
        settings.jellyfinServerUrl = baseUrl()
        settings.jellyfinAllowInsecureHttp = true
        settings.jellyfinUserId = "u"

        val item = JellyfinItem(
            id = "last-page-item",
            name = "LastPage.cbz",
            isFolder = false,
            container = "cbz",
        )

        val result = repository.importJellyfinComic(item)
        assertTrue(result.isSuccess)
        val comicId = result.getOrThrow()

        // Enqueue progress response and mark played response
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        // Page 4 is the last page (5 pages total, 0-indexed)
        repository.syncJellyfinProgressDirect(comicId, page = 4)

        // Download request
        server.takeRequest()

        // 1. Progress request
        val progressReq = server.takeRequest()
        assertEquals("/Sessions/Playing/Progress", progressReq.path)

        // 2. Mark played request
        val playedReq = server.takeRequest()
        assertEquals("/Users/u/PlayedItems/last-page-item", playedReq.path)
        assertEquals("POST", playedReq.method)
    }

    @Test
    fun saveProgressNeverFailsEvenIfJellyfinOffline() = runBlocking {
        val cbzBytes = createValidCbzBytes(pageCount = 5)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/octet-stream")
                .setHeader("Content-Length", cbzBytes.size)
                .setBody(okio.Buffer().write(cbzBytes))
        )

        val auth = JellyfinAuth("tok", "u", "srv", "Bryan")
        credStore.saveAuth(auth)
        settings.jellyfinServerUrl = baseUrl()
        settings.jellyfinAllowInsecureHttp = true
        settings.jellyfinUserId = "u"

        val item = JellyfinItem(
            id = "offline-item",
            name = "Offline.cbz",
            isFolder = false,
            container = "cbz",
        )

        val result = repository.importJellyfinComic(item)
        assertTrue(result.isSuccess)
        val comicId = result.getOrThrow()

        // Consume the download request
        server.takeRequest()

        // Server fails with 500 Server Error
        server.enqueue(MockResponse().setResponseCode(500).setBody("Server down"))

        // Calling saveProgress must update local DB and never throw
        val rowsUpdated = repository.saveProgress(comicId, page = 2, slot = 0)
        assertEquals(1, rowsUpdated)

        val comic = comicDao.getById(comicId)
        assertEquals(2, comic?.lastPage)
    }

    @Test
    fun outboxPersistsAndDeletesOnSuccessfulSync() = runBlocking {
        val cbzBytes = createValidCbzBytes(pageCount = 5)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/octet-stream")
                .setHeader("Content-Length", cbzBytes.size)
                .setBody(okio.Buffer().write(cbzBytes))
        )

        val auth = JellyfinAuth("tok", "u", "srv", "Bryan")
        credStore.saveAuth(auth)
        settings.jellyfinServerUrl = baseUrl()
        settings.jellyfinAllowInsecureHttp = true
        settings.jellyfinUserId = "u"

        val item = JellyfinItem(id = "outbox-item-1", name = "OutboxTest.cbz", isFolder = false, container = "cbz")
        val comicId = repository.importJellyfinComic(item).getOrThrow()
        server.takeRequest(5, TimeUnit.SECONDS) // consume download

        server.enqueue(MockResponse().setResponseCode(204))
        repository.syncJellyfinProgressDirect(comicId, page = 2)

        val req = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(req)
        assertEquals("/Sessions/Playing/Progress", req?.path)

        // After successful sync, outbox entry should be deleted
        assertNull(outboxDao.getPendingForComic(comicId))
    }

    @Test
    fun outboxRetainsEntryOnServerErrorAndFlushesOnReconnect() = runBlocking {
        val cbzBytes = createValidCbzBytes(pageCount = 5)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/octet-stream")
                .setHeader("Content-Length", cbzBytes.size)
                .setBody(okio.Buffer().write(cbzBytes))
        )

        val auth = JellyfinAuth("tok", "u", "srv", "Bryan")
        credStore.saveAuth(auth)
        settings.jellyfinServerUrl = baseUrl()
        settings.jellyfinAllowInsecureHttp = true
        settings.jellyfinUserId = "u"

        val item = JellyfinItem(id = "retry-item-1", name = "RetryTest.cbz", isFolder = false, container = "cbz")
        val comicId = repository.importJellyfinComic(item).getOrThrow()
        server.takeRequest(5, TimeUnit.SECONDS) // consume download

        // Server fails with 500
        server.enqueue(MockResponse().setResponseCode(500).setBody("Server Error"))
        repository.syncJellyfinProgressDirect(comicId, page = 3)
        server.takeRequest(5, TimeUnit.SECONDS) // consume failed progress request

        // Outbox must retain the pending entry
        val pending = outboxDao.getPendingForComic(comicId)
        assertNotNull(pending)
        assertEquals(3, pending?.pageIndex)
        assertEquals("retry-item-1", pending?.itemId)

        // Server recovers
        server.enqueue(MockResponse().setResponseCode(204))
        repository.flushJellyfinOutbox()

        val flushedReq = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(flushedReq)
        assertEquals("/Sessions/Playing/Progress", flushedReq?.path)

        // Outbox entry should now be deleted
        assertNull(outboxDao.getPendingForComic(comicId))
    }

    @Test
    fun sessionCancellationAbortsDelayedSyncOnDisconnect() = runBlocking {
        val cbzBytes = createValidCbzBytes(pageCount = 5)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/octet-stream")
                .setHeader("Content-Length", cbzBytes.size)
                .setBody(okio.Buffer().write(cbzBytes))
        )

        val auth = JellyfinAuth("tok", "u", "srv", "Bryan")
        credStore.saveAuth(auth)
        settings.jellyfinServerUrl = baseUrl()
        settings.jellyfinAllowInsecureHttp = true
        settings.jellyfinUserId = "u"

        val item = JellyfinItem(id = "cancel-item-1", name = "CancelTest.cbz", isFolder = false, container = "cbz")
        val comicId = repository.importJellyfinComic(item).getOrThrow()
        server.takeRequest(5, TimeUnit.SECONDS) // consume download

        // Trigger sync with 600ms debounce
        repository.triggerJellyfinProgressSync(comicId, page = 2, debounceMs = 600L)

        // Disconnect immediately
        repository.disconnectJellyfin()

        // Wait for debounce period to pass
        kotlinx.coroutines.delay(800L)

        // Verify no progress request was sent
        val request = server.takeRequest(200, TimeUnit.MILLISECONDS)
        assertNull("Cancelled session must not send HTTP request", request)
    }

    @Test
    fun navigatingToPageZeroDoesNotUnmarkPlayed() = runBlocking {
        val cbzBytes = createValidCbzBytes(pageCount = 5)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/octet-stream")
                .setHeader("Content-Length", cbzBytes.size)
                .setBody(okio.Buffer().write(cbzBytes))
        )

        val auth = JellyfinAuth("tok", "u", "srv", "Bryan")
        credStore.saveAuth(auth)
        settings.jellyfinServerUrl = baseUrl()
        settings.jellyfinAllowInsecureHttp = true
        settings.jellyfinUserId = "u"

        val item = JellyfinItem(id = "page0-item-1", name = "Page0Test.cbz", isFolder = false, container = "cbz")
        val comicId = repository.importJellyfinComic(item).getOrThrow()
        server.takeRequest(5, TimeUnit.SECONDS) // consume download

        server.enqueue(MockResponse().setResponseCode(204))

        // Read page 0 without explicit unread flag
        repository.syncJellyfinProgressDirect(comicId, page = 0)

        val progressReq = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(progressReq)
        assertEquals("/Sessions/Playing/Progress", progressReq?.path)

        // No DELETE request should be sent to PlayedItems
        val deleteReq = server.takeRequest(200, TimeUnit.MILLISECONDS)
        assertNull("Page 0 navigation must not unmark played status", deleteReq)
    }

    @Test
    fun explicitMarkUnreadSendsDeleteToPlayedItems() = runBlocking {
        val cbzBytes = createValidCbzBytes(pageCount = 5)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/octet-stream")
                .setHeader("Content-Length", cbzBytes.size)
                .setBody(okio.Buffer().write(cbzBytes))
        )

        val auth = JellyfinAuth("tok", "u", "srv", "Bryan")
        credStore.saveAuth(auth)
        settings.jellyfinServerUrl = baseUrl()
        settings.jellyfinAllowInsecureHttp = true
        settings.jellyfinUserId = "u"

        val item = JellyfinItem(id = "unread-item-1", name = "UnreadTest.cbz", isFolder = false, container = "cbz")
        val comicId = repository.importJellyfinComic(item).getOrThrow()
        server.takeRequest(5, TimeUnit.SECONDS) // consume download

        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        // Explicit mark unread
        repository.syncJellyfinProgressDirect(comicId, page = 0, explicitPlayed = false)

        val progressReq = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(progressReq)

        val deleteReq = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(deleteReq)
        assertEquals("/Users/u/PlayedItems/unread-item-1", deleteReq?.path)
        assertEquals("DELETE", deleteReq?.method)
    }

    @Test
    fun remoteProgressReconciliationUpdatesLocalWhenRemoteIsAheadAndOutboxEmpty() = runBlocking {
        val cbzBytes = createValidCbzBytes(pageCount = 10)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/octet-stream")
                .setHeader("Content-Length", cbzBytes.size)
                .setBody(okio.Buffer().write(cbzBytes))
        )

        val auth = JellyfinAuth("tok", "u", "srv", "Bryan")
        credStore.saveAuth(auth)
        settings.jellyfinServerUrl = baseUrl()
        settings.jellyfinAllowInsecureHttp = true
        settings.jellyfinUserId = "u"

        val item = JellyfinItem(id = "reconcile-item-1", name = "ReconcileTest.cbz", isFolder = false, container = "cbz")
        val comicId = repository.importJellyfinComic(item).getOrThrow()
        server.takeRequest(5, TimeUnit.SECONDS) // consume download

        // Remote has progress at page 6 (60,000 ticks)
        val remoteItem = JellyfinItem(
            id = "reconcile-item-1",
            name = "ReconcileTest.cbz",
            isFolder = false,
            container = "cbz",
            playbackPositionTicks = 60_000L,
            played = false,
        )

        repository.reconcileItemProgressFromRemote(remoteItem)

        val updated = comicDao.getById(comicId)
        assertEquals(6, updated?.lastPage)
    }

    @Test
    fun remoteProgressReconciliationPreservesLocalWhenOutboxPending() = runBlocking {
        val cbzBytes = createValidCbzBytes(pageCount = 10)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/octet-stream")
                .setHeader("Content-Length", cbzBytes.size)
                .setBody(okio.Buffer().write(cbzBytes))
        )

        val auth = JellyfinAuth("tok", "u", "srv", "Bryan")
        credStore.saveAuth(auth)
        settings.jellyfinServerUrl = baseUrl()
        settings.jellyfinAllowInsecureHttp = true
        settings.jellyfinUserId = "u"

        val item = JellyfinItem(id = "pending-outbox-item", name = "PendingOutbox.cbz", isFolder = false, container = "cbz")
        val comicId = repository.importJellyfinComic(item).getOrThrow()
        server.takeRequest(5, TimeUnit.SECONDS) // consume download

        // Set local page to 2
        comicDao.updateProgress(comicId, 2, 0, System.currentTimeMillis())

        // Insert pending outbox entry for page 2
        outboxDao.upsert(
            JellyfinSyncOutboxEntity(
                comicId = comicId,
                serverId = "srv",
                userId = "u",
                itemId = "pending-outbox-item",
                pageIndex = 2,
                markPlayed = null,
                updatedAt = System.currentTimeMillis(),
            )
        )

        // Remote is ahead at page 8
        val remoteItem = JellyfinItem(
            id = "pending-outbox-item",
            name = "PendingOutbox.cbz",
            isFolder = false,
            container = "cbz",
            playbackPositionTicks = 80_000L,
            played = false,
        )

        repository.reconcileItemProgressFromRemote(remoteItem)

        // Local progress should NOT be overwritten because an outbox entry is still pending
        val comic = comicDao.getById(comicId)
        assertEquals(2, comic?.lastPage)
    }

    @Test
    fun saveProgressPersistsOutboxAtomicallyBeforeNetworkDispatch() = runBlocking {
        val cbzBytes = createValidCbzBytes(pageCount = 10)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/octet-stream")
                .setHeader("Content-Length", cbzBytes.size)
                .setBody(okio.Buffer().write(cbzBytes))
        )

        val auth = JellyfinAuth("tok", "u", "srv", "Bryan")
        credStore.saveAuth(auth)
        settings.jellyfinServerUrl = baseUrl()
        settings.jellyfinAllowInsecureHttp = true
        settings.jellyfinUserId = "u"

        val item = JellyfinItem(id = "atomic-item-1", name = "AtomicTest.cbz", isFolder = false, container = "cbz")
        val comicId = repository.importJellyfinComic(item).getOrThrow()
        server.takeRequest(5, TimeUnit.SECONDS) // consume download

        // Call saveProgress with page 4
        repository.saveProgress(comicId, page = 4, slot = 0)

        // The outbox entry must be persisted synchronously in the database transaction immediately
        val pending = outboxDao.getPendingForComic(comicId)
        assertNotNull("Outbox entry must be persisted synchronously in Room transaction", pending)
        assertEquals(4, pending?.pageIndex)
        assertEquals("srv", pending?.serverId)
        assertEquals("u", pending?.userId)
        assertEquals("atomic-item-1", pending?.itemId)
    }

    @Test
    fun routinePageTurnPreservesExplicitMarkUnreadIntent() = runBlocking {
        val cbzBytes = createValidCbzBytes(pageCount = 10)
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/octet-stream")
                .setHeader("Content-Length", cbzBytes.size)
                .setBody(okio.Buffer().write(cbzBytes))
        )

        val auth = JellyfinAuth("tok", "u", "srv", "Bryan")
        credStore.saveAuth(auth)
        settings.jellyfinServerUrl = baseUrl()
        settings.jellyfinAllowInsecureHttp = true
        settings.jellyfinUserId = "u"

        val item = JellyfinItem(id = "intent-item-1", name = "IntentTest.cbz", isFolder = false, container = "cbz")
        val comicId = repository.importJellyfinComic(item).getOrThrow()
        server.takeRequest(5, TimeUnit.SECONDS) // consume download

        // 1. Explicitly mark as unread (explicitPlayed = false)
        repository.saveProgress(comicId, page = 0, slot = 0, explicitPlayed = false)
        val initialOutbox = outboxDao.getPendingForComic(comicId)
        assertNotNull(initialOutbox)
        assertEquals(false, initialOutbox?.markPlayed)

        // 2. Turn to page 1 during normal reading without explicit flag (explicitPlayed = null)
        repository.saveProgress(comicId, page = 1, slot = 0, explicitPlayed = null)
        val updatedOutbox = outboxDao.getPendingForComic(comicId)
        assertNotNull(updatedOutbox)
        assertEquals(1, updatedOutbox?.pageIndex)
        // Must preserve markPlayed = false instead of reverting to null!
        assertEquals("Routine page turn must preserve pending explicit markPlayed intent", false, updatedOutbox?.markPlayed)

        // 3. Flush to server and verify both progress and PlayedItems DELETE are sent
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(200).setBody("{}"))

        repository.flushJellyfinOutbox()

        val progressReq = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(progressReq)
        assertEquals("/Sessions/Playing/Progress", progressReq?.path)

        val deleteReq = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(deleteReq)
        assertEquals("/Users/u/PlayedItems/intent-item-1", deleteReq?.path)
        assertEquals("DELETE", deleteReq?.method)
    }

    @Test
    fun disconnectJellyfinCancelsInFlightBulkFlush() = runBlocking {
        val auth = JellyfinAuth("tok", "u", "srv", "Bryan")
        credStore.saveAuth(auth)
        settings.jellyfinServerUrl = baseUrl()
        settings.jellyfinAllowInsecureHttp = true
        settings.jellyfinUserId = "u"

        // Insert two comics and pending outbox entries
        val comic1 = comicDao.insert(
            net.digitalbc.leafreader.data.db.ComicEntity(
                title = "Bulk Comic 1",
                filePath = "/c1.cbz",
                coverPath = null,
                format = "CBZ",
                pageCount = 10,
                dateAdded = 1000L,
                lastOpened = 1000L,
            )
        )
        val comic2 = comicDao.insert(
            net.digitalbc.leafreader.data.db.ComicEntity(
                title = "Bulk Comic 2",
                filePath = "/c2.cbz",
                coverPath = null,
                format = "CBZ",
                pageCount = 10,
                dateAdded = 1000L,
                lastOpened = 1000L,
            )
        )

        outboxDao.upsert(
            JellyfinSyncOutboxEntity(
                comicId = comic1,
                serverId = "srv",
                userId = "u",
                itemId = "bulk-item-1",
                pageIndex = 5,
            )
        )
        outboxDao.upsert(
            JellyfinSyncOutboxEntity(
                comicId = comic2,
                serverId = "srv",
                userId = "u",
                itemId = "bulk-item-2",
                pageIndex = 10,
            )
        )

        // First request is held / delayed
        server.enqueue(
            MockResponse()
                .setResponseCode(204)
                .setHeadersDelay(1000, TimeUnit.MILLISECONDS)
        )

        // Launch bulk flush in repository's jellyfinSyncScope
        val config = JellyfinServerConfig(baseUrl(), true)
        val flushJob = repository.jellyfinSyncScope.launch {
            repository.flushJellyfinOutbox(config, auth)
        }

        // Wait briefly for first call to begin, then disconnect
        kotlinx.coroutines.delay(200L)
        repository.disconnectJellyfin()

        flushJob.join()

        // Consume first request
        val req1 = server.takeRequest(5, TimeUnit.SECONDS)
        assertNotNull(req1)

        // Second request must NOT have been sent because session was disconnected and cancelled
        val req2 = server.takeRequest(300, TimeUnit.MILLISECONDS)
        assertNull("Second request in bulk flush must be cancelled on disconnect", req2)
    }
}
