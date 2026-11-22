package net.digitalbc.leafreader.data.library

import androidx.room.Room
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.EventListener
import okhttp3.Call
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import net.digitalbc.leafreader.data.db.AppDatabase
import net.digitalbc.leafreader.data.db.ComicDao
import net.digitalbc.leafreader.data.db.JellyfinSyncOutboxDao
import net.digitalbc.leafreader.data.db.JellyfinSyncOutboxEntity
import net.digitalbc.leafreader.data.db.RemoteComicLinkDao
import net.digitalbc.leafreader.data.jellyfin.JellyfinAuth
import net.digitalbc.leafreader.data.jellyfin.JellyfinClient
import net.digitalbc.leafreader.data.jellyfin.JellyfinCredentialStore
import net.digitalbc.leafreader.data.jellyfin.JellyfinItem
import net.digitalbc.leafreader.data.jellyfin.JellyfinItemIdentity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
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
        runBlocking { stopRepository() }
        server.shutdown()
        db.close()
        RuntimeEnvironment.getApplication().deleteDatabase("sync-durability.db")
        credStore.clear()
    }

    private suspend fun stopRepository() {
        val jobs = listOf(repository.jellyfinSyncScope, repository.observationScope)
            .mapNotNull { it.coroutineContext[Job] }
        jobs.forEach { it.cancel() }
        jobs.forEach { it.join() }
    }

    private suspend fun clearModels(store: androidx.lifecycle.ViewModelStore) {
        val jobs = store.keys().mapNotNull { store[it]?.viewModelScope?.coroutineContext?.get(Job) }
        store.clear()
        jobs.forEach { it.join() }
    }

    @Test
    fun cachedThumbnailSurvivesRepositoryRestartButDisconnectRemovesItWithoutCreatingReadableCopy() = runBlocking {
        val auth = JellyfinAuth("private-token-never-cache", "thumbnail-user", "thumbnail-server", "Reader")
        credStore.saveAuth(auth)
        settings.jellyfinServerUrl = baseUrl()
        settings.jellyfinAllowInsecureHttp = true
        settings.jellyfinServerId = auth.serverId
        settings.jellyfinUserId = auth.userId
        val png = ByteArrayOutputStream().also { stream ->
            android.graphics.Bitmap.createBitmap(16, 24, android.graphics.Bitmap.Config.ARGB_8888)
                .compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        }.toByteArray()
        server.enqueue(MockResponse().setBody(okio.Buffer().write(png)).setHeader("Content-Type", "image/png"))
        assertNotNull(repository.loadJellyfinCoverBitmap("remote-art", "tag", 64))
        assertNotNull(repository.peekJellyfinCoverBitmap("remote-art", "tag", 64))
        assertEquals(1, server.requestCount)
        assertTrue(comicDao.getAll().isEmpty())
        assertTrue(repository.observeDownloadedJellyfinCopies().first().isEmpty())
        val directory = File(RuntimeEnvironment.getApplication().cacheDir, "jellyfin-thumbnails")
        assertTrue(directory.listFiles()!!.isNotEmpty())
        assertTrue(directory.listFiles()!!.none { it.readBytes().toString(Charsets.ISO_8859_1).contains(auth.accessToken) })
        stopRepository()
        repository = LibraryRepository(RuntimeEnvironment.getApplication(), comicDao, settings,
            db.readingListDao(), db, jellyfinCredentialStore = credStore)
        assertNotNull(repository.loadJellyfinCoverBitmap("remote-art", "tag", 64))
        assertEquals("Restart must use disk, not another HTTP request", 1, server.requestCount)
        repository.disconnectJellyfin()
        assertTrue(directory.listFiles()!!.isEmpty())
        assertNull(repository.peekJellyfinCoverBitmap("remote-art", "tag", 64))
        assertNull(repository.loadJellyfinCoverBitmap("remote-art", "tag", 64))
        assertTrue(comicDao.getAll().isEmpty())
    }

    @Test
    fun remoteListMembershipNeedsNoDownloadAndKeepsOrderAcrossRemovalAndAccountSwitch() = runBlocking {
        val identity = JellyfinItemIdentity("srv", "u", "remote")
        val item = JellyfinItem("remote", "Private comic", false, container = "cbz")
        val entry = net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueEntry(item, "Book", null)
        val list = repository.createReadingList("Mixed list")
        val local = retainedComic(identity.copy(itemId = "other"))
        repository.addComicToReadingList(list, local.id)
        repository.addRemoteToReadingList(list, entry, identity, "books")
        repository.addRemoteToReadingList(list, entry, identity, "books")
        assertEquals(0, server.requestCount)
        val before = repository.observeListReferences(list).first()
        assertEquals(listOf(0, 1), before.map { it.sortOrder })
        assertEquals(2, before.size)
        assertEquals(0L, before.last().comicId)
        repository.editListReference(list, before.last().referenceKey, -1)
        assertEquals(identity.itemId, repository.observeListReferences(list).first().first().itemId)

        credStore.saveAuth(JellyfinAuth("token", "u", "srv", "Reader"))
        settings.jellyfinServerUrl = baseUrl()
        settings.jellyfinAllowInsecureHttp = true
        server.enqueue(MockResponse().setBody(okio.Buffer().write(createValidCbzBytes())))
        val downloaded = repository.importJellyfinComic(item).getOrThrow()
        val attached = repository.observeListReferences(list).first().first()
        assertEquals(downloaded, attached.comicId)
        assertEquals(before.last().referenceKey, attached.referenceKey)
        repository.addComicToReadingList(list, downloaded)
        assertEquals(2, repository.observeListReferences(list).first().size)
        repository.deleteComic(downloaded)
        val removed = repository.observeListReferences(list).first().first()
        assertEquals(0L, removed.comicId)
        assertEquals(attached.referenceKey, removed.referenceKey)
        assertEquals(0, removed.sortOrder)
        assertNotNull(removed.snapshot)

        Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher())
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val vm = net.digitalbc.leafreader.ui.library.LibraryViewModel(repository, settings)
            store.put("lists", vm)
            assertNotNull(vm.observeListMembers(list).first().first().remote)
            credStore.saveAuth(JellyfinAuth("other-token", "other", "srv", "Other"))
            val unavailable = vm.observeListMembers(list).first().first()
            assertNull(unavailable.remote)
            assertNull(unavailable.comic)
            assertEquals(removed.referenceKey, unavailable.reference.referenceKey)
        } finally { clearModels(store); Dispatchers.resetMain() }
    }

    @Test
    fun invalidSavedCoverAddressRemainsANonFatalPlaceholder() = runBlocking {
        credStore.saveAuth(JellyfinAuth("token", "u", "srv", "Reader"))
        settings.jellyfinServerUrl = "not-a-server-url"
        assertNull(repository.loadJellyfinCoverBitmap("cover", "tag"))
        assertEquals(0, server.requestCount)
    }

    @Test
    fun remoteCoverReentryAndConcurrentCardsUseOneRequestButReconnectInvalidatesIt() = runBlocking {
        fun configure() {
            credStore.saveAuth(JellyfinAuth("token", "u", "srv", "Reader"))
            settings.jellyfinServerUrl = baseUrl()
            settings.jellyfinAllowInsecureHttp = true
        }
        val png = java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(createValidCbzBytes())).use {
            it.nextEntry; it.readBytes()
        }
        fun response() = MockResponse().setBody(okio.Buffer().write(png))
        configure()
        server.enqueue(response())
        val results = (1..8).map { async { repository.loadJellyfinCoverBitmap("cover", "tag") } }
            .map { it.await() }
        assertNotNull(results.first())
        assertTrue(results.all { it === results.first() })
        assertTrue(repository.loadJellyfinCoverBitmap("cover", "tag") === results.first())
        assertEquals(1, server.requestCount)
        repository.disconnectJellyfin()
        configure()
        server.enqueue(response())
        assertNotNull(repository.loadJellyfinCoverBitmap("cover", "tag"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun readerStillRejectsCorruptionAfterAvailabilityWasWarmed() = runBlocking {
        val identity = JellyfinItemIdentity("srv", "u", "warm-integrity")
        val comic = retainedComic(identity)
        withTimeout(3000) { repository.readableComics.first { rows -> rows.any { it.id == comic.id } } }
        val file = File(comic.filePath)
        val length = file.length().toInt()
        file.writeBytes(ByteArray(length))
        assertTrue(repository.getReadableJellyfinComic(identity).isFailure)
    }

    @Test
    fun retainedComicCoverUsesLocalImageWithoutAConnection() = runBlocking {
        val identity = JellyfinItemIdentity("srv", "u", "retained-cover")
        val comic = retainedComic(identity)
        val cover = File(RuntimeEnvironment.getApplication().cacheDir, "retained-cover.png")
        val png = java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(createValidCbzBytes())).use {
            it.nextEntry
            it.readBytes()
        }
        cover.writeBytes(png)
        db.openHelper.writableDatabase.execSQL("UPDATE comics SET coverPath=? WHERE id=?",
            arrayOf(cover.absolutePath, comic.id))
        credStore.saveAuth(JellyfinAuth("token", "u", "srv", "Reader"))
        settings.jellyfinServerUrl = baseUrl()
        settings.jellyfinAllowInsecureHttp = true
        val bitmap = withTimeout(3000) { repository.loadJellyfinCoverBitmap(identity.itemId, null) }
        assertNotNull(bitmap)
        assertEquals(0, server.requestCount)
        assertEquals(comic.lastPage, comicDao.getById(comic.id)?.lastPage)
        cover.delete()
        Unit
    }

    @Test
    fun catalogueIndexRoundTripsScopedFolderMetadataAndNeverDeletesRetainedFilesOrLists() = runBlocking {
        val auth = JellyfinAuth("token", "u", "srv", "Reader")
        credStore.saveAuth(auth)
        settings.jellyfinServerUrl = baseUrl()
        server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"books","Name":"Comics","CollectionType":"books"}]}"""))
        settings.jellyfinAllowInsecureHttp = true
        repository.listJellyfinViews().getOrThrow()
        val comic = retainedComic(JellyfinItemIdentity("srv", "u", "retained"))
        val list = repository.createReadingList("Preserved")
        repository.addComicToReadingList(list, comic.id)
        val entry = net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueEntry(
            JellyfinItem("item", "Vol. 01", false, container = "cbz", parentId = "folder"),
            "Book", null, folder = net.digitalbc.leafreader.data.jellyfin.JellyfinFolderIdentity("folder", "A run", "books"))
        val index = net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueIndex(listOf(entry), true, 1, 1, 10, 10)
        repository.writeJellyfinIndex("books", index, listOf(baseUrl(), "srv", "u"))
        assertEquals(index, repository.readJellyfinIndex("books"))
        val restored = repository.restoreJellyfinCatalogue()!!
        assertEquals("books", restored.library?.id)
        assertEquals(index, restored.index)
        repository.writeJellyfinIndex("books", index.copy(entries = emptyList(), total = 0, scanned = 0),
            listOf(baseUrl(), "srv", "u"))
        assertNotNull(comicDao.getById(comic.id))
        assertTrue(File(comic.filePath).isFile)
        assertEquals(1, repository.observeListReferences(list).first().size)
        credStore.saveAuth(auth.copy(userId = "other"))
        assertNull(repository.readJellyfinIndex("books"))
        assertTrue(repository.cachedJellyfinLibraries().isEmpty())
    }

    @Test
    fun launchDiscoveryDoesNotHideConnectionFailureBehindCachedLibraries() = runBlocking {
        credStore.saveAuth(JellyfinAuth("token", "u", "srv", "Reader"))
        settings.jellyfinServerUrl = baseUrl()
        settings.jellyfinAllowInsecureHttp = true
        server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"books","Name":"Comics","CollectionType":"books"}]}"""))
        assertEquals("books", repository.listJellyfinViews().getOrThrow().single().id)
        server.enqueue(MockResponse().setResponseCode(503))
        val launch = repository.listJellyfinViewsForLaunch()
        assertTrue(launch.isFailure)
        assertEquals(net.digitalbc.leafreader.data.jellyfin.JellyfinFailureKind.SERVER_ERROR,
            (launch.exceptionOrNull() as net.digitalbc.leafreader.data.jellyfin.JellyfinException).kind)
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals("books", repository.listJellyfinViews().getOrThrow().single().id)
        assertTrue(comicDao.getAll().isEmpty())
        assertTrue(outboxDao.getPendingForUser("srv", "u").isEmpty())
        assertNotNull(credStore.loadAuth())
        assertEquals(baseUrl(), settings.jellyfinServerUrl)
    }

    @Test
    fun savedCatalogueWorksOnTransportFailureButNeverOnDeniedAuthentication() = runBlocking {
        credStore.saveAuth(JellyfinAuth("token", "u", "srv", "Reader"))
        settings.jellyfinServerUrl = baseUrl()
        settings.jellyfinAllowInsecureHttp = true
        val query = net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueQuery("books")
        server.enqueue(MockResponse().setBody("""{"Items":[{"Id":"remote","Name":"Comic","Type":"Book","Container":"cbz"}],"TotalRecordCount":1}"""))
        assertFalse(repository.listJellyfinCatalogue(query).getOrThrow().fromCache)
        assertEquals("remote", repository.cachedJellyfinCatalogue(query)?.entries?.single()?.item?.id)
        server.enqueue(MockResponse().setResponseCode(503))
        assertTrue(repository.listJellyfinCatalogue(query).getOrThrow().fromCache)
        server.enqueue(MockResponse().setResponseCode(401))
        assertTrue(repository.listJellyfinCatalogue(query).isFailure)
        credStore.saveAuth(JellyfinAuth("other", "other-user", "srv", "Other"))
        assertNull(repository.cachedJellyfinCatalogue(query))
        assertTrue(comicDao.getAll().isEmpty())
        assertTrue(outboxDao.getPendingForUser("srv", "u").isEmpty())
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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun readOnDemandPublishesOnceOnlyAfterValidatedImport() = runBlocking {
        kotlinx.coroutines.Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher())
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val auth = JellyfinAuth("token", "user", "server", "Reader")
            credStore.saveAuth(auth)
            settings.jellyfinServerUrl = baseUrl()
            settings.jellyfinAllowInsecureHttp = true
            val item = JellyfinItem("read-on-demand", "Fixture.cbz", false, container = "cbz")
            val identity = JellyfinItemIdentity(auth.serverId, auth.userId, item.id)
            val vm = net.digitalbc.leafreader.ui.library.LibraryViewModel(repository, settings,
                loadJellyfinDetails = { Result.success(net.digitalbc.leafreader.data.jellyfin.JellyfinItemDetails(identity, item)) })
            store.put("read", vm)
            vm.openJellyfinDetail(item)
            withTimeout(5000) { vm.jellyfinDetail.first { it?.loading == false && !it.checkingLocal } }
            server.enqueue(MockResponse().setBody(okio.Buffer().write(createValidCbzBytes(3))))
            vm.downloadJellyfinDetail(openWhenReady = true)
            val ready = withTimeout(10000) { vm.jellyfinDetail.first { it?.readyComicId != null } }!!
            val comic = comicDao.getById(ready.readyComicId!!)!!
            assertEquals(3, comic.pageCount)
            assertTrue(File(comic.filePath).isFile)
            assertEquals(comic.id, vm.consumeJellyfinRead())
            assertNull(vm.consumeJellyfinRead())
            assertEquals("/Items/read-on-demand/Download", server.takeRequest().path)
            assertEquals(1, server.requestCount)
        } finally { clearModels(store); kotlinx.coroutines.Dispatchers.resetMain() }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun cancelledReadOnDemandPublishesNoComicOrNavigation() = runBlocking {
        kotlinx.coroutines.Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher())
        val store = androidx.lifecycle.ViewModelStore()
        try {
            val auth = JellyfinAuth("token", "user", "server", "Reader")
            credStore.saveAuth(auth)
            settings.jellyfinServerUrl = baseUrl()
            settings.jellyfinAllowInsecureHttp = true
            val item = JellyfinItem("cancel-read", "Fixture.cbz", false, container = "cbz")
            val identity = JellyfinItemIdentity(auth.serverId, auth.userId, item.id)
            val vm = net.digitalbc.leafreader.ui.library.LibraryViewModel(repository, settings,
                loadJellyfinDetails = { Result.success(net.digitalbc.leafreader.data.jellyfin.JellyfinItemDetails(identity, item)) })
            store.put("read", vm)
            vm.openJellyfinDetail(item)
            withTimeout(5000) { vm.jellyfinDetail.first { it?.loading == false && !it.checkingLocal } }
            server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE))
            vm.downloadJellyfinDetail(openWhenReady = true)
            withTimeout(5000) { vm.importing.first { it } }
            assertNotNull(server.takeRequest(5, TimeUnit.SECONDS))
            vm.cancelImport()
            withTimeout(5000) { vm.importing.first { !it } }
            assertNull(vm.consumeJellyfinRead())
            assertNull(linkDao.findLink("jellyfin", "server", "user", item.id))
        } finally { clearModels(store); kotlinx.coroutines.Dispatchers.resetMain() }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun downloadedMarkersRestartForTheCurrentAccount() = runBlocking {
        Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher())
        val store = androidx.lifecycle.ViewModelStore()
        try {
            credStore.saveAuth(JellyfinAuth("token", "first", "server", "Reader"))
            retainedComic(JellyfinItemIdentity("server", "first", "first-item"))
            retainedComic(JellyfinItemIdentity("server", "second", "second-item"))
            val vm = net.digitalbc.leafreader.ui.library.LibraryViewModel(repository, settings)
            store.put("library", vm)
            withTimeout(5000) { vm.jellyfinBrowser.first { it.downloadedItemIds == setOf("first-item") } }
            credStore.saveAuth(JellyfinAuth("token-2", "second", "server", "Reader"))
            vm.refreshDownloadedJellyfinItems()
            withTimeout(5000) { vm.jellyfinBrowser.first { it.downloadedItemIds == setOf("second-item") } }
            credStore.clear()
            vm.refreshDownloadedJellyfinItems()
            assertTrue(vm.jellyfinBrowser.value.downloadedItemIds.isEmpty())
        } finally { clearModels(store); Dispatchers.resetMain() }
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
    fun emptyLocalLibraryCanBrowseRemoteCoversWithoutImportingComics() = runBlocking {
        credStore.saveAuth(JellyfinAuth("token", "user", "server", "Reader"))
        settings.jellyfinServerUrl = baseUrl()
        settings.jellyfinAllowInsecureHttp = true
        server.enqueue(MockResponse().setBody("""{"Items":[
            {"Id":"remote","Name":"Remote comic","Type":"Book","Container":"cbz","ImageTags":{"Primary":"cover"}}
        ],"TotalRecordCount":1}"""))
        val page = repository.listJellyfinCatalogue(
            net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueQuery("books")).getOrThrow()
        assertEquals("remote", page.entries.single().item.id)
        assertEquals("cover", page.entries.single().item.imageTag)
        assertTrue(repository.comics.first().isEmpty())
        assertNull(linkDao.findLink("jellyfin", "server", "user", "remote"))
        assertTrue(outboxDao.getPendingForUser("server", "user").isEmpty())
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertTrue(request.path!!.startsWith("/Items?"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun detailReadIsScopedAndDoesNotDownloadOrWriteProgress() = runBlocking {
        credStore.saveAuth(JellyfinAuth("tok-1", "u-1", "srv-1", "Bryan"))
        settings.jellyfinServerUrl = baseUrl()
        settings.jellyfinAllowInsecureHttp = true
        server.enqueue(MockResponse().setBody("""{"Id":"item","Name":"Comic","Overview":"Summary"}"""))

        val identity = JellyfinItemIdentity("srv-1", "u-1", "item")
        val details = repository.getJellyfinItemDetails(identity).getOrThrow()

        assertEquals(identity, details.identity)
        assertEquals("Summary", details.overview)
        assertEquals("/Items/item?UserId=u-1", server.takeRequest().path)
        assertEquals(1, server.requestCount)
        assertTrue(repository.getJellyfinItemDetails(identity.copy(userId = "other")).isFailure)
        assertTrue(repository.getJellyfinItemDetails(identity.copy(serverId = "other")).isFailure)
        assertEquals(1, server.requestCount)
        assertTrue(linkDao.getLinksForUser("jellyfin", "srv-1", "u-1").isEmpty())
        assertTrue(outboxDao.getPendingForUser("srv-1", "u-1").isEmpty())
    }

    @Test
    fun disconnectDiscardsInFlightDetails() = runBlocking {
        credStore.saveAuth(JellyfinAuth("tok-1", "u-1", "srv-1", "Bryan"))
        settings.jellyfinServerUrl = baseUrl()
        settings.jellyfinAllowInsecureHttp = true
        val requested = CountDownLatch(1)
        val release = CountDownLatch(1)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                requested.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                return MockResponse().setBody("""{"Id":"item","Name":"Comic"}""")
            }
        }
        val result = async(Dispatchers.IO) {
            runCatching {
                repository.getJellyfinItemDetails(JellyfinItemIdentity("srv-1", "u-1", "item"))
            }
        }
        try {
            assertTrue(requested.await(5, TimeUnit.SECONDS))
            repository.disconnectJellyfin()
        } finally {
            release.countDown()
        }
        assertTrue(withTimeout(5_000) { result.await() }.exceptionOrNull() is CancellationException)
        assertNull(repository.getJellyfinAuth())
    }

    private suspend fun retainedComic(identity: JellyfinItemIdentity): net.digitalbc.leafreader.data.db.ComicEntity {
        val file = File(RuntimeEnvironment.getApplication().cacheDir, "retained-${java.util.UUID.randomUUID()}.cbz")
        file.writeBytes(createValidCbzBytes(3))
        val id = comicDao.insert(net.digitalbc.leafreader.data.db.ComicEntity(
            title = "Retained comic", filePath = file.absolutePath, coverPath = null,
            format = "cbz", pageCount = 3, lastPage = 1, lastSlot = 2,
            dateAdded = 10L, lastOpened = 20L, source = "jellyfin",
        ))
        linkDao.insert(net.digitalbc.leafreader.data.db.RemoteComicLinkEntity(
            comicId = id, provider = "jellyfin", serverId = identity.serverId,
            userId = identity.userId, itemId = identity.itemId, downloadedAt = 10L,
        ))
        return comicDao.getById(id)!!
    }

    @Test
    fun automaticCatalogueFetchDoesNotApplyRemoteProgressOrFlushPendingEdits() = runBlocking {
        val identity = JellyfinItemIdentity("server", "user", "item")
        val original = retainedComic(identity)
        credStore.saveAuth(JellyfinAuth("token", identity.userId, identity.serverId, "Reader"))
        settings.jellyfinServerUrl = baseUrl()
        settings.jellyfinAllowInsecureHttp = true
        outboxDao.upsert(JellyfinSyncOutboxEntity(
            comicId = original.id, serverId = identity.serverId, userId = identity.userId,
            itemId = identity.itemId, pageIndex = 1, markPlayed = false, revision = 5L,
        ))
        val pending = outboxDao.getPendingForComic(original.id)
        server.enqueue(MockResponse().setBody(
            """{"Items":[{"Id":"item","Name":"Comic","UserData":{"PlaybackPositionTicks":20000,"Played":true}}],"TotalRecordCount":1}"""
        ))

        // If this path accidentally launches the existing flush, let it complete rather than
        // racing the assertions against a background job.
        server.enqueue(MockResponse().setResponseCode(204))
        server.enqueue(MockResponse().setResponseCode(204))
        val result = repository.listJellyfinItems("books", syncReadingProgress = false).getOrThrow()
        repository.jellyfinSyncScope.coroutineContext[Job]?.children?.toList()?.forEach { it.join() }

        assertEquals(1, result.items.size)
        assertTrue(result.items.single().played)
        assertEquals("GET", server.takeRequest().method)
        assertEquals(1, server.requestCount)
        assertEquals(original, comicDao.getById(original.id))
        assertEquals(pending, outboxDao.getPendingForComic(original.id))
    }

    @Test
    fun retainedComicOpensWithoutCredentialsOrNetworkAndPreservesProgress() = runBlocking {
        val identity = JellyfinItemIdentity("server", "user", "item")
        val original = retainedComic(identity)
        assertNull(credStore.loadAuth())
        assertEquals(original, repository.getReadableJellyfinComic(identity).getOrThrow())
        assertNull(repository.getReadableJellyfinComic(identity.copy(userId = "other")).getOrThrow())
        assertNull(repository.getReadableJellyfinComic(identity.copy(serverId = "other")).getOrThrow())
        assertEquals(original, comicDao.getById(original.id))
        assertTrue(outboxDao.getPendingForUser("server", "user").isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun unusableRetainedCopiesFailWithoutRemovingFilesRecordsOrProgress() = runBlocking {
        for ((index, content) in listOf(byteArrayOf(), "corrupt zip".toByteArray(), createValidCbzBytes(1)).withIndex()) {
            val identity = JellyfinItemIdentity("server", "user", "item-$index")
            val original = retainedComic(identity)
            val file = File(original.filePath)
            file.writeBytes(content)
            assertTrue(repository.getReadableJellyfinComic(identity).isFailure)
            assertTrue(file.exists())
            assertEquals(original, comicDao.getById(original.id))
            assertNotNull(linkDao.findLink("jellyfin", "server", "user", identity.itemId))
            file.delete()
            assertTrue(repository.getReadableJellyfinComic(identity).isFailure)
            assertEquals(original, comicDao.getById(original.id))
        }
        assertEquals(0, server.requestCount)
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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun directContinueRetrievalReportsMissingAndNeverNavigates() = runBlocking {
        kotlinx.coroutines.Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher())
        val store = androidx.lifecycle.ViewModelStore()
        try {
            credStore.saveAuth(JellyfinAuth("token", "user", "server", "Reader"))
            settings.jellyfinServerUrl = baseUrl()
            settings.jellyfinAllowInsecureHttp = true
            val item = JellyfinItem("missing-continue", "Missing.cbz", false, container = "cbz")
            val identity = JellyfinItemIdentity("server", "user", item.id)
            val vm = net.digitalbc.leafreader.ui.library.LibraryViewModel(repository, settings,
                loadJellyfinDetails = { Result.success(net.digitalbc.leafreader.data.jellyfin.JellyfinItemDetails(identity, item)) })
            store.put("continue-missing", vm)
            vm.openJellyfinDetail(item)
            withTimeout(5_000) { vm.jellyfinDetail.first { it?.loading == false && !it.checkingLocal } }
            server.enqueue(MockResponse().setResponseCode(404))
            vm.downloadJellyfinDetail(openWhenReady = true)
            assertEquals("/Items/missing-continue/Download", takeRequest().path)
            withTimeout(5_000) { vm.jellyfinDetail.first { it?.failure == net.digitalbc.leafreader.data.jellyfin.JellyfinFailureKind.NOT_FOUND } }
            assertNull(vm.consumeJellyfinRead())
            assertTrue(comicDao.getAll().isEmpty())
            assertTrue(ownedImportFiles().none { it.endsWith(".part") })
        } finally { clearModels(store); kotlinx.coroutines.Dispatchers.resetMain() }
    }

    @Test
    fun continueReadRevalidatesMissingCopyWithoutRemovingHistory() = runBlocking {
        val comic = retainedComic(JellyfinItemIdentity("server", "user", "local-continue"))
        assertNotNull(repository.getReadableComic(comic.id))
        File(comic.filePath).delete()
        assertNull(repository.getReadableComic(comic.id))
        assertEquals(comic, comicDao.getById(comic.id))
    }

    @Test
    fun disconnectCancelsHeldArchiveWithoutPublishingOrDeletingOtherCopies() = runBlocking {
        val original = retainedComic(JellyfinItemIdentity("server", "user", "original"))
        val beforeRows = comicDao.getAll()
        val beforeBytes = File(original.filePath).readBytes()
        credStore.saveAuth(JellyfinAuth("token-A", "user", "server", "Reader"))
        settings.jellyfinServerUrl = baseUrl()
        settings.jellyfinAllowInsecureHttp = true
        val item = JellyfinItem("held-archive", "Held.cbz", false, container = "cbz")
        val beforeFiles = ownedImportFiles()
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE))
        val operation = async(Dispatchers.IO) { repository.importJellyfinComic(item) }
        try {
            assertEquals("/Items/held-archive/Download", takeRequest().path)
            repository.disconnectJellyfin()
            withTimeout(3_000) { operation.join() }
            assertTrue("Disconnect must cancel the actual import job", operation.isCancelled)
            assertEquals(beforeRows, comicDao.getAll())
            assertTrue(beforeBytes.contentEquals(File(original.filePath).readBytes()))
            assertEquals(beforeFiles, ownedImportFiles())
            assertNull(linkDao.findLink("jellyfin", "server", "user", item.id))
        } finally { operation.cancel(); operation.join() }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun accountSwitchCancelsHeldBatchWithoutNavigationOrNewAccountRequests() = runBlocking {
        kotlinx.coroutines.Dispatchers.setMain(kotlinx.coroutines.test.UnconfinedTestDispatcher())
        val store = androidx.lifecycle.ViewModelStore()
        try {
            credStore.saveAuth(JellyfinAuth("token-A", "user-A", "server-A", "Reader"))
            settings.jellyfinServerUrl = baseUrl()
            settings.jellyfinAllowInsecureHttp = true
            val item = JellyfinItem("first", "First.cbz", false, container = "cbz")
            val identity = JellyfinItemIdentity("server-A", "user-A", item.id)
            val vm = net.digitalbc.leafreader.ui.library.LibraryViewModel(repository, settings,
                loadJellyfinDetails = { Result.success(net.digitalbc.leafreader.data.jellyfin.JellyfinItemDetails(identity, item)) })
            store.put("held-batch", vm)
            vm.openJellyfinDetail(item)
            withTimeout(5_000) { vm.jellyfinDetail.first { it?.loading == false && !it.checkingLocal } }
            val completed = AtomicBoolean(false)
            server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.NO_RESPONSE))
            server.enqueue(MockResponse().setBody("""{"AccessToken":"token-B","ServerId":"server-A","User":{"Id":"user-B","Name":"Other"}}"""))
            vm.importJellyfinItems(listOf(item, item.copy(id = "second"))) { completed.set(true) }
            assertEquals("/Items/first/Download", takeRequest().path)
            assertTrue(repository.connectJellyfin(baseUrl(), true, "Other", "fixture-only").isSuccess)
            assertEquals("/Users/AuthenticateByName", takeRequest().path)
            withTimeout(3_000) { vm.importing.first { !it } }
            assertFalse("Stale completion cannot navigate or refresh a new account", completed.get())
            assertNull(vm.consumeJellyfinRead())
            assertTrue(comicDao.getAll().isEmpty())
            assertTrue(ownedImportFiles().none { it.endsWith(".part") })
            assertEquals("user-B", repository.getJellyfinAuth()?.userId)
            assertNull("No remaining selection may be requested using B", server.takeRequest(250, TimeUnit.MILLISECONDS))
        } finally { clearModels(store); kotlinx.coroutines.Dispatchers.resetMain() }
    }

    @Test
    fun sessionChangeDuringRelinkRollsBackReplacementAndKeepsHistory() = runBlocking {
        val (item, id) = importedRetentionFixture("session-relink")
        saveOffline(id, 3, false)
        assertTrue(repository.removeJellyfinDownload(JellyfinItemIdentity("srv", "u", item.id)).isSuccess)
        val beforeComic = comicDao.getById(id)
        val beforePending = outboxDao.getPendingForComic(id)
        val beforeFiles = ownedImportFiles()
        val links = object : RemoteComicLinkDao by linkDao {
            override suspend fun insert(link: net.digitalbc.leafreader.data.db.RemoteComicLinkEntity): Long {
                val result = linkDao.insert(link)
                repository.disconnectJellyfin()
                return result
            }
        }
        stopRepository()
        repository = LibraryRepository(RuntimeEnvironment.getApplication(), comicDao, settings, db.readingListDao(), db,
            remoteComicLinkDao = links, jellyfinClient = JellyfinClient("session-relink"), jellyfinCredentialStore = credStore)
        enqueueArchive()
        val result = runCatching { repository.importJellyfinComic(item) }
        assertTrue("Stale uncommitted publication must be cancelled", result.exceptionOrNull() is CancellationException)
        takeRequest()
        assertEquals(listOf(beforeComic), comicDao.getAll())
        assertEquals(beforePending, outboxDao.getPendingForComic(id))
        assertEquals(beforeFiles, ownedImportFiles())
        assertEquals(id, linkDao.findLink("jellyfin", "srv", "u", item.id)?.comicId)
    }

    @Test
    fun aCapturedSelectionCannotDownloadFromAnotherAccountOrServer() = runBlocking {
        val other = MockWebServer()
        other.start()
        try {
            for (differentServer in listOf(false, true)) {
                credStore.saveAuth(JellyfinAuth("token-A", "user-A", "server-A", "Reader"))
                settings.jellyfinServerUrl = baseUrl()
                settings.jellyfinAllowInsecureHttp = true
                val session = checkNotNull(repository.captureJellyfinImportSession())
                credStore.saveAuth(JellyfinAuth("token-B", "user-B",
                    if (differentServer) "server-B" else "server-A", "Other"))
                if (differentServer) settings.jellyfinServerUrl = other.url("/").toString().trimEnd('/')
                val result = runCatching {
                    repository.importJellyfinComic(
                        JellyfinItem("colliding-id", "Old selection.cbz", false, container = "cbz"), session = session)
                }
                assertTrue(result.exceptionOrNull() is CancellationException)
                assertTrue(comicDao.getAll().isEmpty())
            }
            assertEquals(0, server.requestCount)
            assertEquals(0, other.requestCount)
        } finally { other.shutdown() }
    }

    private fun ownedImportFiles(): Set<String> = listOf("comics", "covers").flatMap { directory ->
        File(RuntimeEnvironment.getApplication().filesDir, directory).listFiles()?.map { it.path }.orEmpty()
    }.toSet()

    @Test
    fun importPreservesDottedDisplayNamesAndStripsOnlyTheFileExtension() = runBlocking {
        credStore.saveAuth(JellyfinAuth("tok", "u", "srv", "Bryan"))
        settings.jellyfinServerUrl = baseUrl()
        settings.jellyfinAllowInsecureHttp = true
        val bytes = createValidCbzBytes()
        val cases = listOf(
            "King Spawn - Vol. 01" to "King Spawn - Vol. 01",
            "King Spawn - Vol. 01.cbz" to "King Spawn - Vol. 01",
            "King Spawn - Vol. 01.CBZ" to "King Spawn - Vol. 01",
            "Dr. Strange" to "Dr. Strange",
        )
        for ((index, names) in cases.withIndex()) {
            val (name, expectedTitle) = names
            server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes)))
            val item = JellyfinItem(
                id = "dotted-title-$index", name = name, isFolder = false, container = "cbz",
            )
            val comicId = repository.importJellyfinComic(item).getOrThrow()
            assertEquals(name, expectedTitle, comicDao.getById(comicId)?.title)
            assertEquals(name, linkDao.findByComicId(comicId)?.remoteName)
        }
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
        val progressServer = enableProgressFixture(comicId, ticks = 0L, played = false)

        // Enqueue progress response
        server.enqueue(MockResponse().setResponseCode(204))

        // Save progress with debounceMs = 0L to execute immediately
        repository.syncJellyfinProgressDirect(comicId, page = 4)

        // Consume the download request first
        takeRequest()

        // Now take the progress sync request
        val recorded = takeRequest()
        assertEquals("/UserItems/sync-item-1/UserData?userId=u", recorded.path)
        assertEquals("POST", recorded.method)
        val bodyStr = recorded.body.readUtf8()
        val json = JSONObject(bodyStr)
        assertFalse(json.getBoolean("Played"))
        assertEquals(40000L, json.getLong("PlaybackPositionTicks"))
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
        enableProgressFixture(comicId)

        // Enqueue the complete state-write response
        server.enqueue(MockResponse().setResponseCode(204))

        // Page 4 is the last page (5 pages total, 0-indexed)
        repository.syncJellyfinProgressDirect(comicId, page = 4)

        // Download request
        takeRequest()

        assertProgress(takeRequest(), "last-page-item", 4, token = "tok", played = true)
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
        val progressServer = enableProgressFixture(comicId, ticks = 0L, played = false)
        takeRequest() // consume download

        server.enqueue(MockResponse().setResponseCode(204))
        repository.syncJellyfinProgressDirect(comicId, page = 2)

        val req = takeRequest()
        assertNotNull(req)
        assertEquals("/UserItems/outbox-item-1/UserData?userId=u", req.path)

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
        val progressServer = enableProgressFixture(comicId, ticks = 0L, played = false)
        takeRequest() // consume download

        // Server fails with 500
        server.enqueue(MockResponse().setResponseCode(500).setBody("Server Error"))
        repository.syncJellyfinProgressDirect(comicId, page = 3)
        takeRequest() // consume failed progress request

        // Outbox must retain the pending entry
        val pending = outboxDao.getPendingForComic(comicId)
        assertNotNull(pending)
        assertEquals(3, pending?.pageIndex)
        assertEquals("retry-item-1", pending?.itemId)

        // Server recovers
        server.enqueue(MockResponse().setResponseCode(204))
        repository.flushJellyfinOutbox()

        val flushedReq = takeRequest()
        assertNotNull(flushedReq)
        assertEquals("/UserItems/retry-item-1/UserData?userId=u", flushedReq.path)

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
        val progressServer = enableProgressFixture(comicId, ticks = 20000L, played = true)
        takeRequest() // consume download

        server.enqueue(MockResponse().setResponseCode(204))

        // Read page 0 without explicit unread flag
        repository.syncJellyfinProgressDirect(comicId, page = 0)

        val progressReq = takeRequest()
        assertNotNull(progressReq)
        assertEquals("/UserItems/page0-item-1/UserData?userId=u", progressReq.path)

        assertTrue(JSONObject(progressReq.body.readUtf8()).getBoolean("Played"))
        assertTrue(progressServer.position("page0-item-1").played!!)
        // Drain authoritative readbacks and verify no DELETE was sent.
        while (server.takeRequest(100, TimeUnit.MILLISECONDS) != null) { }
        assertTrue(progressServer.writes.none { it.method == "DELETE" })
        // No DELETE request should be sent to PlayedItems
        val deleteReq = server.takeRequest(200, TimeUnit.MILLISECONDS)
        assertNull("Page 0 navigation must not unmark played status", deleteReq)
    }

    @Test
    fun explicitMarkUnreadWritesBothStateFields() = runBlocking {
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
        val progressServer = enableProgressFixture(comicId, ticks = 0L, played = true)
        takeRequest() // consume download

        server.enqueue(MockResponse().setResponseCode(204))

        // Explicit mark unread
        repository.syncJellyfinProgressDirect(comicId, page = 0, explicitPlayed = false)

        assertProgress(takeRequest(), "unread-item-1", 0, token = "tok", played = false)
        assertFalse(progressServer.position("unread-item-1").played!!)

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
        val progressServer = enableProgressFixture(comicId, ticks = 0L, played = false)
        takeRequest() // consume download

        // Remote has progress at page 6 (60,000 ticks)
        val remoteItem = JellyfinItem(
            id = "reconcile-item-1",
            name = "ReconcileTest.cbz",
            isFolder = false,
            container = "cbz",
            playbackPositionTicks = 60_000L,
            played = false,
        )

        progressServer.position(item.id, 60_000L, false)
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
        val progressServer = enableProgressFixture(comicId, ticks = 0L, played = true)
        takeRequest() // consume download

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

        // Flush the retained explicit unread intent and newer page together.
        server.enqueue(MockResponse().setResponseCode(204))
        repository.flushJellyfinOutbox()
        assertProgress(takeRequest(), "intent-item-1", 1, token = "tok", played = false)
        assertEquals(10000L, progressServer.position("intent-item-1").positionTicks)
        assertFalse(progressServer.position("intent-item-1").played!!)

    }

    @Test
    fun disconnectCancelsBrowseTriggeredFlushAndKeepsPendingIntent() = runBlocking {
        verifySessionCancellation(switchAccount = false, reconnect = false)
    }

    @Test
    fun accountSwitchCancelsReconnectFlushAndIsolatesBothAccounts() = runBlocking {
        verifySessionCancellation(switchAccount = true, reconnect = true)
    }

    @Test
    fun inFlightFlushPreservesNewerPageSave() = runBlocking {
        verifyHeldSave(page = 6, played = null)
    }

    @Test
    fun inFlightFlushPreservesExplicitRead() = runBlocking {
        verifyHeldSave(page = 9, played = true)
    }

    @Test
    fun inFlightFlushPreservesExplicitUnread() = runBlocking {
        verifyHeldSave(page = 0, played = false)
    }

    private suspend fun verifyHeldSave(page: Int, played: Boolean?) {
        val held = HeldProgress()
        configureSync(held)
        val comic = linkedComic("race-item")
        saveOffline(comic, 4)
        val initial = outboxDao.getPendingForComic(comic)!!
        val flush = repository.jellyfinSyncScope.launch { repository.flushJellyfinOutbox() }
        try {
            withTimeout(5000) { held.started.await() }
            saveOffline(comic, page, played)
            val newer = outboxDao.getPendingForComic(comic)!!
            assertEquals(initial.id, newer.id)
            assertEquals(initial.revision + 1, newer.revision)
            held.release.countDown()
            withTimeout(5000) { flush.join() }
            assertEquals(newer, outboxDao.getPendingForComic(comic))

            repository.flushJellyfinOutbox()
            assertNull(outboxDao.getPendingForComic(comic))
            assertProgress(takeRequest(), "race-item", 4)
            assertProgress(takeRequest(), "race-item", page, played = played ?: false)
            assertEquals(page * 10_000L, held.position.get())
            assertEquals(played ?: false, held.played.get())
            assertEquals(2, held.writes.size)
        } finally {
            held.release.countDown()
            flush.cancel()
        }
    }

    @Test
    fun concurrentBulkAndPerComicFlushesSerializeAtHeldRequest() = runBlocking {
        val held = HeldProgress()
        configureSync(held)
        val comic = linkedComic("serialized-item")
        saveOffline(comic, 3)
        val snapshot = outboxDao.getPendingForComic(comic)!!
        val lookups = AtomicInteger()
        val observedDao = object : JellyfinSyncOutboxDao by outboxDao {
            override suspend fun getPendingForComic(comicId: Long): JellyfinSyncOutboxEntity? {
                // Make the per-comic preflight lookup synchronous, so Unconfined runs to the mutex.
                lookups.incrementAndGet()
                return outboxDao.getPendingForComic(comicId)
            }
        }
        stopRepository()
        repository = newRepository(held.client, outbox = observedDao,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))
        val bulk = repository.jellyfinSyncScope.launch { repository.flushJellyfinOutbox() }
        var perComic: Job? = null
        try {
            withTimeout(5000) { held.started.await() }
            val lookupsBefore = lookups.get()
            perComic = repository.triggerJellyfinProgressSync(comic, 3, debounceMs = 0)
            assertTrue(perComic!!.isActive)
            assertEquals("Per-comic flush must wait before its locked re-read", lookupsBefore + 1, lookups.get())
            held.release.countDown()
            withTimeout(5000) { bulk.join(); perComic.join() }
            assertNull(outboxDao.getPendingForComic(comic))
            assertProgress(takeRequest(), "serialized-item", 3)
            assertEquals("Only one send may acknowledge the shared pending row", 1, held.writes.size)
        } finally {
            held.release.countDown()
            bulk.cancel()
            perComic?.cancel()
        }
    }

    private suspend fun verifySessionCancellation(switchAccount: Boolean, reconnect: Boolean) {
        val held = HeldProgress()
        configureSync(held)
        val comic = linkedComic("old-item")
        val secondComic = linkedComic("old-second-item")
        saveOffline(comic, 4, false)
        saveOffline(secondComic, 5, true)
        val oldPending = outboxDao.getPendingForUser("srv", "u")

        try {
            if (reconnect) {
                assertTrue(repository.connectJellyfin(baseUrl(), true, "Bryan", "test-password").isSuccess)
                assertEquals("/Users/AuthenticateByName", takeRequest().path)
            } else {
                assertTrue(repository.listJellyfinItems(parentId = null).isSuccess)
                assertTrue(takeRequest().path!!.contains("/Items"))
            }
            withTimeout(5000) { held.started.await() }

            if (switchAccount) {
                val otherComic = linkedComic("new-item", user = "other")
                outboxDao.upsert(JellyfinSyncOutboxEntity(
                    comicId = otherComic, serverId = "srv", userId = "other",
                    itemId = "new-item", pageIndex = 2, markPlayed = true,
                ))
                held.loginUser = "other"
                assertTrue(repository.connectJellyfin(baseUrl(), true, "Other", "test-password").isSuccess)
                assertProgress(takeRequest(), "old-item", 4)
                assertEquals("/Users/AuthenticateByName", takeRequest().path)
                withTimeout(5000) { held.cancelled.await() }
                // Explicit flush also waits for the reconnect flush through the same mutex.
                repository.flushJellyfinOutbox()
                assertProgress(takeRequest(), "new-item", 2, token = "token-other", played = true, user = "other")
                assertEquals("other", repository.getJellyfinAuth()?.userId)
                assertEquals(0, outboxDao.countPendingForUser("srv", "other"))
            } else {
                repository.disconnectJellyfin()
                withTimeout(5000) { held.cancelled.await() }
                assertProgress(takeRequest(), "old-item", 4)
                assertNull(repository.getJellyfinAuth())
            }
            assertEquals("Cancellation must retain unsent played intent and both rows", oldPending,
                outboxDao.getPendingForUser("srv", "u"))
            assertEquals(if (switchAccount) 2 else 1, held.writes.size)
        } finally {
            held.release.countDown()
        }
    }

    @Test
    fun pendingProgressAndUnreadSurviveDatabaseReopenAndFlush() = runBlocking {
        stopRepository()
        db.close()
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("sync-durability.db")
        fun openDiskDb() = Room.databaseBuilder(context, AppDatabase::class.java, "sync-durability.db")
            .allowMainThreadQueries().build()
        db = openDiskDb()
        refreshDaos()
        val held = HeldProgress(hold = false)
        configureSync(held)
        held.position("durable-item", 0L, true)
        val comic = linkedComic("durable-item")
        saveOffline(comic, 0, false)
        saveOffline(comic, 2)
        val pending = outboxDao.getPendingForComic(comic)
        assertEquals(false, pending?.markPlayed)
        stopRepository()
        db.close()
        db = openDiskDb()
        refreshDaos()
        repository = newRepository(held.client)
        assertEquals(2, comicDao.getById(comic)?.lastPage)
        assertEquals(pending, outboxDao.getPendingForComic(comic))
        repository.flushJellyfinOutbox()
        assertProgress(takeRequest(), "durable-item", 2, played = false)
        assertNull(outboxDao.getPendingForComic(comic))
    }

    @Test
    fun failedStateWriteRetainsIntentUntilConfirmed() = runBlocking {
        val held = HeldProgress(hold = false)
        configureSync(held)
        held.position("retry-item", 0L, true)
        val comic = linkedComic("retry-item")
        saveOffline(comic, 0, false)
        val pending = outboxDao.getPendingForComic(comic)
        held.failNextStateWrite = true
        repository.flushJellyfinOutbox()
        assertEquals(pending, outboxDao.getPendingForComic(comic))
        assertProgress(takeRequest(), "retry-item", 0, played = false)
        assertNull(held.played.get())

        repository.flushJellyfinOutbox()
        assertProgress(takeRequest(), "retry-item", 0, played = false)
        assertEquals(false, held.played.get())
        assertNull(outboxDao.getPendingForComic(comic))
        assertEquals(2, held.writes.size)
    }

    @Test
    fun outboxInsertFailureRollsBackLocalProgressAndSendsNothing() = runBlocking {
        val held = HeldProgress(hold = false)
        configureSync(held)
        val comic = linkedComic("rollback-item")
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_outbox BEFORE INSERT ON jellyfin_sync_outbox BEGIN SELECT RAISE(ABORT, 'injected outbox failure'); END"
        )
        val failure = runCatching { repository.saveProgress(comic, 7, 2, explicitPlayed = true) }.exceptionOrNull()
        assertNotNull("Injected SQLite failure must propagate", failure)
        assertEquals(0, comicDao.getById(comic)?.lastPage)
        assertEquals(0, comicDao.getById(comic)?.lastSlot)
        assertNull(outboxDao.getPendingForComic(comic))
        assertEquals(0, server.requestCount)
    }


    @Test fun aFreshOnePageLocalImportIsUnreadUntilReadingCompletesIt() = runBlocking {
        val file = File(RuntimeEnvironment.getApplication().cacheDir, "fresh-one-page.cbz")
        file.writeBytes(createValidCbzBytes(1))
        val id = repository.importComic(android.net.Uri.fromFile(file)).getOrThrow()
        val comic = comicDao.getById(id)!!
        assertEquals(0, comic.lastPage)
        assertEquals(false, comic.completionOverride)
        assertFalse(comic.isCompleted)
        assertTrue(comic.isUnread)
        assertEquals(0, server.requestCount)
    }

    @Test fun freshImportsKeepServerReadStateSeparateFromExactPositionIncludingOnePage() = runBlocking {
        credStore.saveAuth(JellyfinAuth("token-u", "u", "srv", "Reader"))
        settings.jellyfinServerUrl = baseUrl()
        settings.jellyfinAllowInsecureHttp = true
        for ((index, snapshot) in listOf(Triple(1, 0L, false), Triple(5, 40_000L, false), Triple(5, 0L, true)).withIndex()) {
            val (pages, ticks, played) = snapshot
            val bytes = createValidCbzBytes(pages)
            server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes)))
            val item = JellyfinItem("fresh-$index", "Fresh.cbz", false, container = "cbz", playbackPositionTicks = ticks, played = played)
            val id = repository.importJellyfinComic(item).getOrThrow()
            takeRequest()
            val comic = comicDao.getById(id)!!
            assertEquals((ticks / 10_000).toInt(), comic.lastPage)
            assertEquals(played, comic.completionOverride)
            assertEquals(played, comic.isCompleted)
            assertEquals(ticks == 0L && !played, comic.isUnread)
            assertNull(outboxDao.getPendingForComic(id))
        }
    }

    private suspend fun importedRetentionFixture(itemId: String): Pair<JellyfinItem, Long> {
        credStore.saveAuth(JellyfinAuth("token-u", "u", "srv", "Reader"))
        settings.jellyfinServerUrl = baseUrl()
        settings.jellyfinAllowInsecureHttp = true
        settings.jellyfinUserId = "u"
        val item = JellyfinItem(itemId, "Retained.cbz", false, container = "cbz")
        enqueueArchive()
        val id = repository.importJellyfinComic(item).getOrThrow()
        assertTrue(takeRequest().path!!.contains("/Download"))
        return item to id
    }

    private fun enqueueArchive() {
        val bytes = createValidCbzBytes(5)
        server.enqueue(MockResponse().setBody(okio.Buffer().write(bytes)).setHeader("Content-Length", bytes.size))
    }

    @Test fun manualRemovalAndRedownloadPreserveCanonicalHistoryListsAndPendingRevisionAcrossReopen() = runBlocking {
        stopRepository()
        db.close()
        val context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("sync-durability.db")
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, "sync-durability.db").allowMainThreadQueries().build()
        db = open(); refreshDaos(); repository = newRepository(JellyfinClient("retention"))
        val (item, id) = importedRetentionFixture("retention")
        saveOffline(id, 3, true)
        val list = repository.createReadingList("Retained order")
        repository.addComicToReadingList(list, id)
        val before = comicDao.getById(id)!!
        val members = db.readingListDao().getAllItems()
        val pending = outboxDao.getPendingForComic(id)
        val identity = JellyfinItemIdentity("srv", "u", item.id)
        val requestCount = server.requestCount
        assertTrue(repository.removeJellyfinDownload(identity).isSuccess)
        assertEquals(requestCount, server.requestCount)
        assertFalse(File(before.filePath).exists())
        before.coverPath?.let { assertFalse(File(it).exists()) }
        assertEquals(before.copy(coverPath = null), comicDao.getById(id))
        assertEquals(members, db.readingListDao().getAllItems())
        assertEquals(pending, outboxDao.getPendingForComic(id))
        assertTrue(repository.observeDownloadedJellyfinCopies().first().isEmpty())
        stopRepository()
        db.close(); db = open(); refreshDaos(); repository = newRepository(JellyfinClient("retention-reopen"))
        assertEquals(pending, outboxDao.getPendingForComic(id))
        enqueueArchive()
        // Differing server metadata must not replace the retained unsent local position/read state.
        val restoredId = repository.importJellyfinComic(item.copy(playbackPositionTicks = 10_000, played = false)).getOrThrow()
        takeRequest()
        assertEquals(id, restoredId)
        assertEquals(1, comicDao.getAll().size)
        val restored = comicDao.getById(id)!!
        assertEquals(3, restored.lastPage)
        assertEquals(true, restored.completionOverride)
        assertEquals(before.lastOpened, restored.lastOpened)
        assertEquals(before.dateAdded, restored.dateAdded)
        assertTrue(File(restored.filePath).isFile)
        assertEquals(pending, outboxDao.getPendingForComic(id))
        assertEquals(members, db.readingListDao().getAllItems())
        assertEquals(id, linkDao.findLink("jellyfin", "srv", "u", item.id)!!.comicId)
        assertEquals(id, repository.observeDownloadedJellyfinCopies().first()[item.id]!!.id)
    }

    @Test fun failedRedownloadLeavesRetainedHistoryPendingAndListsUntouched() = runBlocking {
        val (item, id) = importedRetentionFixture("retention-failure")
        saveOffline(id, 2, false)
        val list = repository.createReadingList("Keep")
        repository.addComicToReadingList(list, id)
        assertTrue(repository.removeJellyfinDownload(JellyfinItemIdentity("srv", "u", item.id)).isSuccess)
        val before = comicDao.getById(id)
        val members = db.readingListDao().getAllItems()
        val pending = outboxDao.getPendingForComic(id)
        val context = RuntimeEnvironment.getApplication()
        fun files() = listOf("comics", "covers").flatMap { File(context.filesDir, it).listFiles()?.map { it.path }.orEmpty() }.toSet()
        val originalFiles = files()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_retention_link BEFORE INSERT ON remote_comic_links BEGIN SELECT RAISE(ABORT, 'injected relink failure'); END")
        enqueueArchive()
        assertTrue(repository.importJellyfinComic(item).isFailure)
        takeRequest()
        assertEquals(listOf(before), comicDao.getAll())
        assertEquals(pending, outboxDao.getPendingForComic(id))
        assertEquals(members, db.readingListDao().getAllItems())
        assertEquals(id, linkDao.findLink("jellyfin", "srv", "u", item.id)!!.comicId)
        assertEquals(originalFiles, files())
    }

    @Test fun cancellationAtRelinkCommitKeepsThePublishedCanonicalCopyUsable() = runBlocking {
        val (item, id) = importedRetentionFixture("retention-cancel")
        saveOffline(id, 3, false)
        assertTrue(repository.removeJellyfinDownload(JellyfinItemIdentity("srv", "u", item.id)).isSuccess)
        val pending = outboxDao.getPendingForComic(id)
        val parent = Job()
        val links = object : RemoteComicLinkDao by linkDao {
            override suspend fun insert(link: net.digitalbc.leafreader.data.db.RemoteComicLinkEntity): Long {
                val result = linkDao.insert(link)
                parent.cancel() // Cancellation while publication is committing must not delete committed files.
                return result
            }
        }
        stopRepository()
        repository = LibraryRepository(RuntimeEnvironment.getApplication(), comicDao, settings, db.readingListDao(), db,
            remoteComicLinkDao = links, jellyfinClient = JellyfinClient("retention-cancel"), jellyfinCredentialStore = credStore)
        enqueueArchive()
        val operation = CoroutineScope(parent + Dispatchers.IO).async { repository.importJellyfinComic(item) }
        assertTrue(runCatching { operation.await() }.exceptionOrNull() is CancellationException)
        parent.join()
        takeRequest()
        assertEquals(1, comicDao.getAll().size)
        assertTrue(File(comicDao.getById(id)!!.filePath).isFile)
        assertEquals(id, repository.getReadableJellyfinComic(JellyfinItemIdentity("srv", "u", item.id)).getOrThrow()!!.id)
        assertEquals(3, comicDao.getById(id)!!.lastPage)
        assertEquals(pending, outboxDao.getPendingForComic(id))
    }

    @Test fun removeDownloadRejectsLocalOriginalsAndUnmanagedPathsWithoutRequests() = runBlocking {
        for (source in listOf("local", "smb", "jellyfin")) {
            val identity = JellyfinItemIdentity("srv", "u", "protected-$source")
            val comic = retainedComic(identity)
            db.openHelper.writableDatabase.execSQL("UPDATE comics SET source=? WHERE id=?", arrayOf<Any>(source, comic.id))
            assertTrue(repository.removeJellyfinDownload(identity).isFailure)
            assertTrue(File(comic.filePath).isFile)
            assertNotNull(comicDao.getById(comic.id))
        }
        assertEquals(0, server.requestCount)
    }

    @Test
    fun playedStatusResetStillAcknowledgesTheCompletedPage() = runBlocking {
        verifyPlayedStatusReset(played = true, page = 9)
    }

    @Test
    fun unreadStatusResetPreservesIntentAcrossRoutinePageTurns() = runBlocking {
        verifyPlayedStatusReset(played = false, page = 6)
    }

    @Test
    fun playedStatusResetDoesNotRestoreAnOlderRevision() = runBlocking {
        val held = HeldProgress(hold = false).apply { resetPositionOnPlayed = true; playedOnProgress = false }
        configureSync(held)
        val comic = linkedComic("newer-reset")
        saveOffline(comic, 6, played = false)
        val initial = outboxDao.getPendingForComic(comic)!!
        held.onProgressWrite = { runBlocking { saveOffline(comic, 4) } }

        repository.flushJellyfinOutbox()

        val pending = outboxDao.getPendingForComic(comic)!!
        assertEquals(initial.revision + 1, pending.revision)
        assertEquals(4, pending.pageIndex)
        assertEquals(false, pending.markPlayed)
        assertEquals("The in-flight write must not acknowledge newer local progress", 1, held.writes.size)
        assertEquals(60_000L, held.position("newer-reset").positionTicks)
    }

    private suspend fun verifyPlayedStatusReset(played: Boolean, page: Int) {
        val held = HeldProgress(hold = false).apply { resetPositionOnPlayed = true; playedOnProgress = false }
        configureSync(held)
        val item = "played-reset-$played"
        held.position(item, 0, !played)
        val comic = linkedComic(item)
        saveOffline(comic, 0, played)
        saveOffline(comic, page)
        assertEquals(played, outboxDao.getPendingForComic(comic)!!.markPlayed)

        repository.flushJellyfinOutbox()

        assertNull("Both page and played state must be confirmed before acknowledging", outboxDao.getPendingForComic(comic))
        assertEquals(page * 10_000L, held.position(item).positionTicks)
        assertEquals(played, held.position(item).played)
    }

    private fun refreshDaos() {
        comicDao = db.comicDao()
        linkDao = db.remoteComicLinkDao()
        outboxDao = db.jellyfinSyncOutboxDao()
    }

    private fun newRepository(
        client: JellyfinClient,
        outbox: JellyfinSyncOutboxDao = outboxDao,
        scope: CoroutineScope = CoroutineScope(
            SupervisorJob() + Dispatchers.IO),
    ) = LibraryRepository(
        RuntimeEnvironment.getApplication(), comicDao, settings, db.readingListDao(), db,
        remoteComicLinkDao = linkDao, jellyfinSyncOutboxDao = outbox,
        jellyfinClient = client, jellyfinCredentialStore = credStore, jellyfinSyncScope = scope,
    )

    private suspend fun configureSync(held: HeldProgress) {
        stopRepository()
        repository = newRepository(held.client)
        server.dispatcher = held
        credStore.saveAuth(JellyfinAuth("token-u", "u", "srv", "Bryan"))
        settings.jellyfinServerUrl = baseUrl()
        settings.jellyfinAllowInsecureHttp = true
        settings.jellyfinUserId = "u"
    }

    private suspend fun linkedComic(item: String, user: String = "u"): Long {
        val id = comicDao.insert(net.digitalbc.leafreader.data.db.ComicEntity(
            title = item, filePath = "/$item.cbz", coverPath = null, format = "CBZ",
            pageCount = 10, dateAdded = 1000, lastOpened = 1000, completionOverride = false,
        ))
        linkDao.insert(net.digitalbc.leafreader.data.db.RemoteComicLinkEntity(
            comicId = id, provider = "jellyfin", serverId = "srv", userId = user, itemId = item,
        ))
        val snapshot = (server.dispatcher as SyncProtocolDispatcher).position(item)
        seedBaseline(id, snapshot.positionTicks!!, snapshot.played!!)
        return id
    }

    private suspend fun seedBaseline(comicId: Long, ticks: Long, played: Boolean) {
        val link = linkDao.findByComicId(comicId)!!
        comicDao.updateCompletion(comicId, played)
        db.jellyfinMetadataCacheDao().put(net.digitalbc.leafreader.data.db.JellyfinMetadataCache(
            link.serverId, link.userId, baseUrl(), "progress-baseline:${link.itemId}",
            JSONObject().put("ticks", ticks).put("played", played).toString()))
    }

    private suspend fun enableProgressFixture(comicId: Long, ticks: Long = 0, played: Boolean = false): SyncProtocolDispatcher {
        val link = linkDao.findByComicId(comicId)!!
        return SyncProtocolDispatcher(queuedResponses = true).also {
            it.position(link.itemId, ticks, played)
            seedBaseline(comicId, ticks, played)
            server.dispatcher = it
        }
    }

    private suspend fun saveOffline(comic: Long, page: Int, played: Boolean? = null) {
        // Persist with no configured endpoint so no debounce can race test-driven flushes.
        settings.jellyfinServerUrl = ""
        try {
            repository.saveProgress(comic, page, 0, explicitPlayed = played)
        } finally {
            settings.jellyfinServerUrl = baseUrl()
        }
    }

    private fun takeRequest(): RecordedRequest {
        while (true) {
            val request = requireNotNull(server.takeRequest(5, TimeUnit.SECONDS)) { "Expected a Jellyfin request" }
            if (request.method == "GET" && request.requestUrl!!.pathSegments.let { it.size == 2 && it[0] == "Items" }) {
                assertNotNull("Progress reads must be authenticated", request.getHeader("Authorization"))
            } else return request
        }
    }

    private fun assertProgress(
        request: RecordedRequest, item: String, page: Int, token: String = "token-u",
        played: Boolean? = null, user: String = "u",
    ) {
        assertEquals("POST", request.method)
        assertEquals("/UserItems/$item/UserData?userId=$user", request.path)
        assertTrue(request.getHeader("Authorization")!!.contains(token))
        val payload = JSONObject(request.body.readUtf8())
        val hasReadingDate = page > 0 || payload.getBoolean("Played")
        assertEquals(if (hasReadingDate) 3 else 2, payload.length())
        if (hasReadingDate) assertNotNull(java.time.Instant.parse(payload.getString("LastPlayedDate")))
        assertEquals(page * 10_000L, payload.getLong("PlaybackPositionTicks"))
        assertTrue(payload.get("Played") is Boolean)
        if (played != null) assertEquals(played, payload.getBoolean("Played"))
    }

    private class HeldProgress(private val hold: Boolean = true) : SyncProtocolDispatcher() {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        val position = AtomicLong(-1)
        val played = AtomicReference<Boolean?>(null)
        @Volatile var loginUser = "u"
        private val first = AtomicBoolean(true)
        val client = JellyfinClient("sync-test", customHttpClient = OkHttpClient.Builder()
            .eventListener(object : EventListener() {
                override fun callFailed(call: Call, ioe: IOException) {
                    if (call.request().url.encodedPath.startsWith("/UserItems/") && call.request().url.encodedPath.endsWith("/UserData") && call.isCanceled()) {
                        cancelled.complete(Unit)
                    }
                }
            }).build())

        override fun dispatch(request: RecordedRequest): MockResponse {
            val path = request.requestUrl!!.encodedPath
            if (path == "/Users/AuthenticateByName") {
                return MockResponse().setBody(
                    """{"AccessToken":"token-$loginUser","ServerId":"srv","User":{"Id":"$loginUser","Name":"Test"}}"""
                )
            }
            if (request.method == "GET" && !path.startsWith("/Items/")) {
                return MockResponse().setBody("""{"Items":[],"TotalRecordCount":0,"StartIndex":0}""")
            }
            val response = super.dispatch(request)
            if (response.status.split(' ')[1].toInt() in 200..299) {
                if (path.startsWith("/UserItems/") && path.endsWith("/UserData")) {
                    val json = JSONObject(request.body.clone().readUtf8())
                    position.set(json.getLong("PlaybackPositionTicks"))
                    played.set(json.getBoolean("Played"))
                    if (hold && first.compareAndSet(true, false)) {
                        started.complete(Unit)
                        if (!release.await(10, TimeUnit.SECONDS)) return MockResponse().setResponseCode(500)
                    }
                }
            }
            return response
        }
    }
}
