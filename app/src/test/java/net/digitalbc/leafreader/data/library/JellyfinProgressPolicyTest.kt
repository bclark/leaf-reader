package net.digitalbc.leafreader.data.library

import android.content.Context
import androidx.room.Room
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import net.digitalbc.leafreader.ui.library.LibraryViewModel
import net.digitalbc.leafreader.ui.library.JellyfinCatalogueViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import net.digitalbc.leafreader.data.db.AppDatabase
import net.digitalbc.leafreader.data.db.ComicEntity
import net.digitalbc.leafreader.data.db.JellyfinMetadataCache
import net.digitalbc.leafreader.data.db.RemoteComicLinkEntity
import net.digitalbc.leafreader.data.jellyfin.*
import net.digitalbc.leafreader.data.settings.AppSettings
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import javax.crypto.spec.SecretKeySpec

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class JellyfinProgressPolicyTest {
    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var settings: AppSettings
    private lateinit var credentials: JellyfinCredentialStore
    private lateinit var repository: LibraryRepository
    private lateinit var server: MockWebServer
    private lateinit var remote: SyncProtocolDispatcher
    private val databaseName = "progress-policy.db"
    private val auth = JellyfinAuth("token", "user", "server", "Reader")

    @Before fun setup() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase(databaseName)
        remote = SyncProtocolDispatcher()
        server = MockWebServer().apply { dispatcher = remote; start() }
        settings = AppSettings(context.getSharedPreferences("progress-policy", Context.MODE_PRIVATE))
        settings.jellyfinServerUrl = url()
        settings.jellyfinAllowInsecureHttp = true
        credentials = JellyfinCredentialStore(context) {
            SecretKeySpec("LeafTestKey12345678901234567890!".toByteArray(), "AES")
        }
        credentials.saveAuth(auth)
        openRepository()
    }

    @After fun cleanup() = runBlocking {
        closeRepository()
        db.close()
        server.shutdown()
        context.deleteDatabase(databaseName)
        credentials.clear()
    }

    private fun url() = server.url("/").toString().trimEnd('/')
    private fun openRepository() {
        db = Room.databaseBuilder(context, AppDatabase::class.java, databaseName).allowMainThreadQueries().build()
        repository = LibraryRepository(context, db.comicDao(), settings, db.readingListDao(), db,
            jellyfinCredentialStore = credentials, jellyfinClient = JellyfinClient("policy-test"))
    }
    private suspend fun closeRepository() {
        repository.jellyfinSyncScope.coroutineContext[Job]!!.cancelAndJoin()
        repository.observationScope.coroutineContext[Job]!!.cancelAndJoin()
    }
    private suspend fun comic(item: String, page: Int = 0, completed: Boolean? = false): Long {
        val id = db.comicDao().insert(ComicEntity(title = item, filePath = "/fixture/$item.cbz", coverPath = null,
            format = "CBZ", pageCount = 20, lastPage = page, completionOverride = completed,
            dateAdded = 1, lastOpened = 2))
        db.remoteComicLinkDao().insert(RemoteComicLinkEntity(comicId = id,
            serverId = auth.serverId, userId = auth.userId, itemId = item))
        return id
    }
    private suspend fun baseline(item: String, ticks: Long, played: Boolean) {
        db.jellyfinMetadataCacheDao().put(JellyfinMetadataCache(auth.serverId, auth.userId, url(),
            "progress-baseline:$item", JSONObject().put("ticks", ticks).put("played", played).toString()))
    }
    private suspend fun saveOffline(id: Long, page: Int, played: Boolean? = null) {
        settings.jellyfinServerUrl = ""
        try { repository.saveProgress(id, page, 0, played) }
        finally { settings.jellyfinServerUrl = url() }
    }
    private fun item(id: String) = JellyfinItem(id, id, false, container = "cbz")

    @Test fun missingBaselineHoldsBothPositionsUntilAnExplicitChoice() = runBlocking {
        for (choice in JellyfinProgressChoice.entries) {
            val item = "choice-${choice.name}"
            val id = comic(item)
            saveOffline(id, 4)
            remote.position(item, 20_000, false)
            val writesBefore = remote.writes.size
            repository.flushJellyfinOutbox()
            assertEquals(writesBefore, remote.writes.size)
            val conflict = repository.progressConflicts.value.single()
            assertEquals(4, conflict.localPage)
            assertEquals(20_000L, conflict.remote.positionTicks)
            assertNotNull(db.jellyfinSyncOutboxDao().getPendingForComic(id))
            assertTrue(repository.resolveJellyfinProgress(conflict, choice).isSuccess)
            assertEquals(if (choice == JellyfinProgressChoice.DEVICE) 4 else 2, db.comicDao().getById(id)!!.lastPage)
            assertEquals(if (choice == JellyfinProgressChoice.DEVICE) writesBefore + 1 else writesBefore, remote.writes.size)
            assertNull(db.jellyfinSyncOutboxDao().getPendingForComic(id))
            assertTrue(repository.progressConflicts.value.isEmpty())
        }
    }

    @Test fun unchangedServerUploadsAndConfirmedBaselineSurvivesDatabaseReopen() = runBlocking {
        val id = comic("durable")
        baseline("durable", 0, false)
        saveOffline(id, 4)
        repository.flushJellyfinOutbox()
        assertNull(db.jellyfinSyncOutboxDao().getPendingForComic(id))
        assertEquals(40_000L, remote.position("durable").positionTicks)
        closeRepository()
        db.close()
        openRepository()
        saveOffline(id, 5)
        repository.flushJellyfinOutbox()
        assertTrue(repository.progressConflicts.value.isEmpty())
        assertNull(db.jellyfinSyncOutboxDao().getPendingForComic(id))
        assertEquals(50_000L, remote.position("durable").positionTicks)
        assertEquals(2, remote.writes.size)
    }

    @Test fun remoteResetAndCompletionKeepExactPageAndSeparateReadState() = runBlocking {
        val id = comic("remote", page = 6)
        baseline("remote", 60_000, false)
        remote.position("remote", 10_000, true)
        repository.reconcileItemProgressFromRemote(item("remote"))
        assertEquals(1, db.comicDao().getById(id)!!.lastPage)
        assertTrue(db.comicDao().getById(id)!!.isCompleted)
        remote.position("remote", 0, false)
        repository.reconcileItemProgressFromRemote(item("remote"))
        assertEquals(0, db.comicDao().getById(id)!!.lastPage)
        assertFalse(db.comicDao().getById(id)!!.isCompleted)
        assertTrue(remote.writes.isEmpty())
    }

    @Test fun staleRemoteAndLocalChoicesAreRefreshedWithoutAWrite() = runBlocking {
        val id = comic("stale")
        baseline("stale", 0, false)
        saveOffline(id, 4)
        remote.position("stale", 20_000, false)
        repository.flushJellyfinOutbox()
        val initial = repository.progressConflicts.value.single()
        remote.position("stale", 30_000, true)
        assertTrue(repository.resolveJellyfinProgress(initial, JellyfinProgressChoice.DEVICE).isFailure)
        val updated = repository.progressConflicts.value.single()
        assertEquals(30_000L, updated.remote.positionTicks)
        saveOffline(id, 5, false)
        assertTrue(repository.resolveJellyfinProgress(updated, JellyfinProgressChoice.SERVER).isFailure)
        assertEquals(5, db.comicDao().getById(id)!!.lastPage)
        assertTrue(remote.writes.isEmpty())
        assertTrue(repository.resolveJellyfinProgress(repository.progressConflicts.value.single(),
            JellyfinProgressChoice.SERVER).isSuccess)
        assertEquals(3, db.comicDao().getById(id)!!.lastPage)
        assertTrue(db.comicDao().getById(id)!!.isCompleted)
    }

    @Test fun unknownRemoteStateStaysQueuedWithoutGuessingAReset() = runBlocking {
        val id = comic("unknown")
        baseline("unknown", 0, false)
        saveOffline(id, 4)
        for (unknown in listOf(JellyfinProgressSnapshot(null, false), JellyfinProgressSnapshot(0, null))) {
            remote.position("unknown", unknown.positionTicks, unknown.played)
            repository.flushJellyfinOutbox()
            assertNotNull(db.jellyfinSyncOutboxDao().getPendingForComic(id))
            assertTrue(remote.writes.isEmpty())
            assertEquals(4, db.comicDao().getById(id)!!.lastPage)
        }
        remote.position("unknown", 0, false)
        repository.flushJellyfinOutbox()
        assertNull(db.jellyfinSyncOutboxDao().getPendingForComic(id))
    }

    @Test fun successResponseWithoutReadbackConfirmationKeepsPendingIntent() = runBlocking {
        val id = comic("unconfirmed")
        baseline("unconfirmed", 0, false)
        saveOffline(id, 4)
        remote.acknowledgeWrites = false
        repository.flushJellyfinOutbox()
        assertNotNull(db.jellyfinSyncOutboxDao().getPendingForComic(id))
        remote.acknowledgeWrites = true
        repository.flushJellyfinOutbox()
        assertNull(db.jellyfinSyncOutboxDao().getPendingForComic(id))
        assertEquals(2, remote.writes.size)
    }

    @Test fun partialPlayedFailureRetriesFromConfirmedPageWithoutLosingIntent() = runBlocking {
        val id = comic("partial")
        baseline("partial", 0, false)
        saveOffline(id, 19, true)
        remote.failNextPlayed = true
        repository.flushJellyfinOutbox()
        assertEquals(true, db.jellyfinSyncOutboxDao().getPendingForComic(id)!!.markPlayed)
        assertEquals(JellyfinProgressSnapshot(190_000, false), remote.position("partial"))
        repository.flushJellyfinOutbox()
        assertNull(db.jellyfinSyncOutboxDao().getPendingForComic(id))
        assertEquals(JellyfinProgressSnapshot(190_000, true), remote.position("partial"))
        assertTrue(repository.progressConflicts.value.isEmpty())
    }

    @Test fun aNewerRevisionSurvivesAnOlderConfirmedWrite() = runBlocking {
        val id = comic("revision")
        baseline("revision", 0, false)
        saveOffline(id, 4)
        val first = db.jellyfinSyncOutboxDao().getPendingForComic(id)!!
        remote.onProgressWrite = { runBlocking { saveOffline(id, 5) } }
        repository.flushJellyfinOutbox()
        val newer = db.jellyfinSyncOutboxDao().getPendingForComic(id)!!
        assertTrue(newer.revision > first.revision)
        assertEquals(5, newer.pageIndex)
        repository.flushJellyfinOutbox()
        assertNull(db.jellyfinSyncOutboxDao().getPendingForComic(id))
        assertEquals(50_000L, remote.position("revision").positionTicks)
    }

    @Test fun disconnectDuringAReadPreventsWritesAndPreservesTheQueue() = runBlocking {
        val id = comic("disconnect")
        baseline("disconnect", 0, false)
        saveOffline(id, 4)
        remote.onProgressRead = { runBlocking { repository.disconnectJellyfin() } }
        repository.flushJellyfinOutbox()
        assertTrue(remote.writes.isEmpty())
        assertNotNull(db.jellyfinSyncOutboxDao().getPendingForComic(id))
        assertTrue(repository.progressConflicts.value.isEmpty())
    }
    @Test fun deferKeepsBothPositionsAndExplicitCheckReopensFreshChoices() = runBlocking {
        val id = comic("defer")
        saveOffline(id, 4)
        remote.position("defer", 20_000, false)
        repository.flushJellyfinOutbox()
        val conflict = repository.progressConflicts.value.single()
        val pending = db.jellyfinSyncOutboxDao().getPendingForComic(id)
        repository.deferProgressConflict(conflict.key)
        repository.flushJellyfinOutbox()
        assertTrue(repository.progressConflicts.value.single().deferred)
        assertEquals(pending, db.jellyfinSyncOutboxDao().getPendingForComic(id))
        assertTrue(remote.writes.isEmpty())
        assertTrue(repository.checkJellyfinProgress(conflict.identity).isSuccess)
        assertFalse(repository.progressConflicts.value.single().deferred)
        repository.deferProgressConflict(conflict.key)
        remote.position("defer", 30_000, true)
        repository.flushJellyfinOutbox()
        assertFalse(repository.progressConflicts.value.single().deferred)
        assertEquals(30_000L, repository.progressConflicts.value.single().remote.positionTicks)
        assertEquals(4, db.comicDao().getById(id)!!.lastPage)
        assertTrue(remote.writes.isEmpty())
    }

    @Test fun retainedCopyWithoutOutboxCanChooseDeviceAfterExplicitCheck() = runBlocking {
        val id = comic("retained", page = 4)
        remote.position("retained", 20_000, true)
        assertTrue(repository.checkJellyfinProgress(JellyfinItemIdentity(auth.serverId, auth.userId, "retained")).isSuccess)
        val conflict = repository.progressConflicts.value.single()
        assertNull(db.jellyfinSyncOutboxDao().getPendingForComic(id))
        assertTrue(repository.resolveJellyfinProgress(conflict, JellyfinProgressChoice.DEVICE).isSuccess)
        assertEquals(JellyfinProgressSnapshot(40_000, false), remote.position("retained"))
        assertNull(db.jellyfinSyncOutboxDao().getPendingForComic(id))
        assertTrue(repository.progressConflicts.value.isEmpty())
    }

    @Test fun normalizedUrlSpellingStillAllowsFreshConflictResolution() = runBlocking {
        for ((index, spelling) in listOf(url() + "/", "  " + url() + "/  ").withIndex()) {
            val name = "normalized-$index"
            val id = comic(name)
            saveOffline(id, 4)
            remote.position(name, 20_000, true)
            settings.jellyfinServerUrl = spelling
            repository.flushJellyfinOutbox()
            val conflict = repository.progressConflicts.value.single()
            assertTrue(repository.resolveJellyfinProgress(conflict, JellyfinProgressChoice.SERVER).isSuccess)
            assertEquals(2, db.comicDao().getById(id)!!.lastPage)
            assertTrue(db.comicDao().getById(id)!!.isCompleted)
            assertNull(db.jellyfinSyncOutboxDao().getPendingForComic(id))
            assertTrue(remote.writes.isEmpty())
        }
    }

    @Test fun revokingLocalHttpConsentDuringFreshReadPreventsTheWrite() = runBlocking {
        val id = comic("consent")
        baseline("consent", 0, false)
        saveOffline(id, 4)
        remote.onProgressRead = { settings.jellyfinAllowInsecureHttp = false }
        repository.flushJellyfinOutbox()
        assertTrue(remote.writes.isEmpty())
        assertNotNull(db.jellyfinSyncOutboxDao().getPendingForComic(id))
        assertEquals(4, db.comicDao().getById(id)!!.lastPage)
    }

    @Test fun aChangedPageCountRequiresFreshlyDisplayedChoices() = runBlocking {
        val id = comic("count")
        saveOffline(id, 4)
        remote.position("count", 20_000, false)
        repository.flushJellyfinOutbox()
        val old = repository.progressConflicts.value.single()
        db.comicDao().updatePageCount(id, 30)
        assertTrue(repository.resolveJellyfinProgress(old, JellyfinProgressChoice.SERVER).isFailure)
        val refreshed = repository.progressConflicts.value.single()
        assertEquals(30, refreshed.pageCount)
        assertNotEquals(old.key, refreshed.key)
        assertEquals(4, db.comicDao().getById(id)!!.lastPage)
        assertTrue(remote.writes.isEmpty())
        assertNotNull(db.jellyfinSyncOutboxDao().getPendingForComic(id))
        assertTrue(repository.resolveJellyfinProgress(refreshed, JellyfinProgressChoice.SERVER).isSuccess)
        assertEquals(2, db.comicDao().getById(id)!!.lastPage)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun freshServerObservationsUpdateOpenDetailAndLoadedCatalogueWithoutReloading() = runBlocking {
        val main = UnconfinedTestDispatcher()
        Dispatchers.setMain(main)
        val store = ViewModelStore()
        try {
            settings.jellyfinServerId = auth.serverId
            settings.jellyfinUserId = auth.userId
            val id = comic("captions")
            baseline("captions", 0, false)
            val identity = JellyfinItemIdentity(auth.serverId, auth.userId, "captions")
            val stale = item("captions")
            val details = CompletableDeferred<JellyfinItemDetails>()
            val library = LibraryViewModel(repository, settings, loadJellyfinDetails = { Result.success(details.await()) },
                defaultDispatcher = main)
            store.put("library", library)
            val books = JellyfinView("books", "Comics", "books")
            var fetches = 0
            var heldPage: CompletableDeferred<JellyfinCataloguePage>? = null
            val catalogue = JellyfinCatalogueViewModel(repository, settings,
                views = { Result.success(listOf(books)) }, restore = { null }, readIndex = { null },
                fetch = { fetches++; Result.success(heldPage?.await() ?: JellyfinCataloguePage(
                    listOf(JellyfinCatalogueEntry(stale, "Book", false)), 1, 1, false)) },
                saveIndex = { _, _, _ -> }, defaultDispatcher = main, ioDispatcher = main)
            store.put("catalogue", catalogue)
            catalogue.ensure()
            withTimeout(5000) { catalogue.state.first { it.loaded && !it.indexing && it.entries.isNotEmpty() } }
            val requests = fetches
            library.openJellyfinDetail(stale)
            remote.position("captions", 30_000, true)
            assertTrue(repository.checkJellyfinProgress(identity).isSuccess)
            details.complete(JellyfinItemDetails(identity, stale, overview = "preserved", playedPercentage = 99.0))
            withTimeout(5000) { library.jellyfinDetail.first { it?.details != null } }
            val detail = library.jellyfinDetail.value!!.details!!
            assertEquals(30_000L, detail.item.playbackPositionTicks)
            assertTrue(detail.item.played)
            assertNull(detail.playedPercentage) // unavailable fresh percentage must not retain the old99%
            assertEquals("preserved", detail.overview)
            withTimeout(5000) { catalogue.state.first { it.entries.singleOrNull()?.played == true } }
            assertEquals(30_000L, catalogue.state.value.entries.single().item.playbackPositionTicks)
            assertEquals(requests, fetches)
            assertTrue(db.comicDao().getById(id)!!.completionOverride == true)
            remote.position("captions", 0, false)
            assertTrue(repository.checkJellyfinProgress(identity).isSuccess)
            withTimeout(5000) { catalogue.state.first { it.entries.singleOrNull()?.played == false } }
            assertEquals(0L, library.jellyfinDetail.value!!.details!!.item.playbackPositionTicks)
            assertFalse(library.jellyfinDetail.value!!.details!!.item.played)
            // A metadata pass begun before a sync must not restore its older progress fields.
            val delayed = CompletableDeferred<JellyfinCataloguePage>()
            heldPage = delayed
            catalogue.refresh()
            remote.position("captions", 40_000, true)
            assertTrue(repository.checkJellyfinProgress(identity).isSuccess)
            delayed.complete(JellyfinCataloguePage(listOf(JellyfinCatalogueEntry(stale, "Book", false)), 1, 1, false))
            withTimeout(5000) { catalogue.state.first { !it.indexing } }
            assertTrue(catalogue.state.value.entries.single().played == true)
            assertEquals(40_000L, catalogue.state.value.entries.single().item.playbackPositionTicks)
            // Observations are not a permanent overlay over a subsequent fresh metadata pass.
            heldPage = null
            catalogue.refresh()
            withTimeout(5000) { catalogue.state.first { !it.indexing } }
            assertFalse(catalogue.state.value.entries.single().played == true)
            val otherUrl = MockWebServer().apply { dispatcher = remote; start() }
            try {
                settings.jellyfinServerUrl = otherUrl.url("/").toString().trimEnd('/')
                remote.position("captions", 10_000, false)
                assertTrue(repository.checkJellyfinProgress(identity).isSuccess)
                // Matching server/user/item IDs must not update a detail loaded from another URL.
                assertEquals(40_000L, library.jellyfinDetail.value!!.details!!.item.playbackPositionTicks)
                assertTrue(library.jellyfinDetail.value!!.details!!.item.played)
            } finally {
                settings.jellyfinServerUrl = url()
                otherUrl.shutdown()
            }
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test fun progressObservationsAreNotOptimisticAndRejectChangedAccounts() = runBlocking {
        val id = comic("observed")
        baseline("observed", 0, false)
        saveOffline(id, 4)
        remote.position("observed", 20_000, false)
        val next = async(start = CoroutineStart.UNDISPATCHED) { repository.progressUpdates.first() }
        repository.flushJellyfinOutbox()
        val observed = next.await()
        assertEquals(20_000L, observed.snapshot.positionTicks)
        assertEquals(4, db.comicDao().getById(id)!!.lastPage)
        assertTrue(repository.isCurrentProgressUpdate(observed))
        credentials.saveAuth(auth.copy(userId = "other"))
        assertFalse(repository.isCurrentProgressUpdate(observed))
        assertTrue(remote.writes.isEmpty())
    }

}
