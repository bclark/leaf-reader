package net.digitalbc.leafreader.ui.library

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import net.digitalbc.leafreader.data.jellyfin.JellyfinAuth
import net.digitalbc.leafreader.data.jellyfin.JellyfinItem
import net.digitalbc.leafreader.data.jellyfin.JellyfinItemDetails
import net.digitalbc.leafreader.data.jellyfin.JellyfinItemIdentity
import net.digitalbc.leafreader.data.jellyfin.JellyfinException
import net.digitalbc.leafreader.data.jellyfin.JellyfinFailureKind
import net.digitalbc.leafreader.data.jellyfin.JellyfinView
import net.digitalbc.leafreader.data.jellyfin.JellyfinItemsResult
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import net.digitalbc.leafreader.data.db.ComicDao
import net.digitalbc.leafreader.data.db.ComicEntity
import net.digitalbc.leafreader.data.db.ReadingListDao
import net.digitalbc.leafreader.data.db.ReadingListEntity
import net.digitalbc.leafreader.data.db.ReadingListItemEntity
import net.digitalbc.leafreader.data.library.LibraryRepository
import net.digitalbc.leafreader.data.settings.AppSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class LibraryViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private val stores = mutableListOf<ViewModelStore>()

    private lateinit var fakeComicDao: FakeComicDao
    private lateinit var fakeReadingListDao: FakeReadingListDao
    private lateinit var repository: LibraryRepository
    private lateinit var settings: AppSettings
    private lateinit var viewModel: LibraryViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        val context = RuntimeEnvironment.getApplication()
        fakeComicDao = FakeComicDao()
        fakeReadingListDao = FakeReadingListDao().apply {
            comicsLookup = { fakeComicDao.getAllDirect() }
        }
        settings = AppSettings(context)
        repository = LibraryRepository(
            context = context,
            dao = fakeComicDao,
            settings = settings,
            readingListDao = fakeReadingListDao,
        )
        viewModel = LibraryViewModel(repository, settings, defaultDispatcher = testDispatcher).also {
            stores += ViewModelStore().apply { put("library", it) }
        }
    }

    @After
    fun cleanup() {
        val jobs = stores.flatMap { store ->
            store.keys().mapNotNull { store[it]?.viewModelScope?.coroutineContext?.get(Job) }
        }
        stores.forEach { it.clear() }
        runBlocking {
            jobs.forEach { it.join() }
            repository.observationScope.coroutineContext[Job]?.let { it.cancel(); it.join() }
            repository.jellyfinSyncScope.coroutineContext[Job]?.let { it.cancel(); it.join() }
        }
        Dispatchers.resetMain()
    }

    private fun landingViewModel(
        views: suspend () -> Result<List<JellyfinView>>,
        items: suspend (String) -> Result<JellyfinItemsResult>,
    ) = LibraryViewModel(
        repository, settings, defaultDispatcher = testDispatcher, loadJellyfinViews = views, loadJellyfinLandingItems = items,
    ).also { stores += ViewModelStore().apply { put("landing", it) } }

    private fun configureJellyfin() {
        settings.jellyfinServerUrl = "https://example.test"
        settings.jellyfinServerId = "server"
        settings.jellyfinUserId = "user"
    }

    @Test fun globalSearchLeavesCollectionAndRestoresItsQueryWithoutRetargetingAnAccount() = runBlocking {
        configureJellyfin()
        val vm = JellyfinCatalogueViewModel(repository, settings, defaultDispatcher = testDispatcher, ioDispatcher = testDispatcher,
            restore = { null }, readIndex = { null }, saveIndex = { _, _, _ -> },
            views = { Result.success(listOf(JellyfinView("books", "Comics", "books"))) },
            fetch = { Result.success(net.digitalbc.leafreader.data.jellyfin.JellyfinCataloguePage(emptyList(), 0, 0, false)) })
        stores += ViewModelStore().apply { put("global-search", vm) }
        vm.ensure()
        vm.browse(net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueView.LISTS, "", true)
        val collection = JellyfinItem("collection", "A collection", true)
        vm.openCollection(collection)
        vm.search("prior")
        vm.beginGlobalSearch()
        vm.browse(net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueView.ALL, "global", true)
        assertNull(vm.state.value.collection)
        assertEquals("global", vm.state.value.search)
        vm.browse(net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueView.ALL, "global", false)
        vm.endGlobalSearch()
        assertEquals(collection, vm.state.value.collection)
        assertEquals("prior", vm.state.value.search)
        assertFalse(vm.state.value.unreadOnly)
        vm.beginGlobalSearch()
        settings.jellyfinUserId = "other"
        vm.endGlobalSearch()
        assertNull(vm.state.value.collection)
    }

    @Test fun staleAccountSelectionCannotDownloadOrRemoveAnyCopy() {
        val auth = JellyfinAuth(accessToken = "synthetic", userId = "current", serverId = "server", username = "fixture")
        val vm = LibraryViewModel(repository, settings, jellyfinAuth = { auth }, defaultDispatcher = testDispatcher)
        stores += ViewModelStore().apply { put("copy-actions", vm) }
        val selected = RemoteListSelection(
            net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueEntry(JellyfinItem("comic", "Comic", false, container = "cbz"), "Book", false),
            JellyfinItemIdentity("server", "previous", "comic"))
        vm.downloadRemoteSelection(listOf(selected), "Account changed")
        vm.removeRemoteSelection(listOf(selected), "Account changed")
        assertEquals("Account changed", vm.message.value)
        assertFalse(vm.removingDownload.value)
        assertFalse(vm.importing.value)
    }

    private fun fileImportViewModel(
        dispatcher: kotlinx.coroutines.CoroutineDispatcher = testDispatcher,
        importer: suspend (android.net.Uri, (Float) -> Unit, () -> Boolean) -> Result<Long>,
    ) = LibraryViewModel(
        repository, settings, defaultDispatcher = testDispatcher,
        importFile = importer, importDispatcher = dispatcher,
    ).also { stores += ViewModelStore().apply { put("file-import", it) } }

    private suspend fun fileResult(vm: LibraryViewModel, id: Long): FileImportState =
        withTimeout(3000) { vm.fileImportState.first { it?.operationId == id && it.finished }!! }

    private fun fileUri(id: Int) = android.net.Uri.parse("content://fixture/$id")

    @Test
    fun fileImportReportsOnlyConfirmedIdsAndKeepsPartialFailureMessage() = runBlocking {
        val vm = fileImportViewModel { uri, _, _ ->
            if (uri == fileUri(2)) Result.failure(java.io.IOException("Invalid archive"))
            else Result.success(uri.lastPathSegment!!.toLong())
        }
        val id = vm.importComics(listOf(fileUri(1), fileUri(2), fileUri(3)))!!
        val result = fileResult(vm, id)
        assertEquals(3, result.requestedCount)
        assertEquals(listOf(1L, 3L), result.importedComicIds)
        assertEquals(1, result.failedCount)
        assertFalse(result.cancelled)
        assertFalse(vm.importing.value)
        assertEquals("Imported 2 of 3 comics (1 failed).", vm.message.value)
        vm.consumeFileImportResult(id)
        assertNull(vm.fileImportState.value)
    }

    @Test
    fun failedFileImportCannotReportSuccessOrReuseAnEarlierResult() = runBlocking {
        val vm = fileImportViewModel { uri, _, _ ->
            if (uri == fileUri(1)) Result.success(1L) else Result.failure(java.io.IOException("Invalid archive"))
        }
        val first = vm.importComics(listOf(fileUri(1)))!!
        fileResult(vm, first)
        val second = vm.importComics(listOf(fileUri(2)))!!
        val result = fileResult(vm, second)
        assertTrue(second > first)
        assertTrue(result.importedComicIds.isEmpty())
        assertEquals(1, result.failedCount)
        vm.consumeFileImportResult(first)
        assertEquals(result, vm.fileImportState.value)
    }

    @Test
    fun emptySelectionDoesNotCancelRunningBatchAndSelectionsAreSnapshotted() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val seen = mutableListOf<android.net.Uri>()
        val vm = fileImportViewModel { uri, _, _ ->
            seen += uri
            if (uri == fileUri(1)) release.await()
            Result.success(uri.lastPathSegment!!.toLong())
        }
        val selection = mutableListOf(fileUri(1), fileUri(2))
        val id = vm.importComics(selection)!!
        selection.clear()
        assertNull(vm.importComics(emptyList()))
        vm.consumeFileImportResult(id)
        assertEquals(id, vm.fileImportState.value?.operationId)
        assertFalse(vm.fileImportState.value!!.finished)
        release.complete(Unit)
        assertEquals(listOf(1L, 2L), fileResult(vm, id).importedComicIds)
        assertEquals(listOf(fileUri(1), fileUri(2)), seen)
    }

    @Test
    fun cancellingPartialImportReportsRetainedSuccessWithoutInventingFailedFiles() = runBlocking {
        val secondStarted = CompletableDeferred<Unit>()
        val vm = fileImportViewModel { uri, _, _ ->
            if (uri == fileUri(2)) {
                secondStarted.complete(Unit)
                CompletableDeferred<Unit>().await()
            }
            Result.success(uri.lastPathSegment!!.toLong())
        }
        val id = vm.importComics(listOf(fileUri(1), fileUri(2), fileUri(3)))!!
        secondStarted.await()
        vm.cancelImport()
        val result = fileResult(vm, id)
        assertTrue(result.cancelled)
        assertEquals(listOf(1L), result.importedComicIds)
        assertEquals(0, result.failedCount)
        assertFalse(vm.importing.value)
    }

    @Test
    fun replacementWaitsForCleanupAndOldCompletionCannotCompleteOrConsumeNewBatch() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val seen = mutableListOf<Int>()
        val vm = fileImportViewModel { uri, _, _ ->
            val value = uri.lastPathSegment!!.toInt()
            seen += value
            if (value == 1) withContext(NonCancellable) { release.await() }
            Result.success(value.toLong())
        }
        try {
            val oldId = vm.importComics(listOf(fileUri(1)))!!
            val newId = vm.importComics(listOf(fileUri(2)))!!
            assertEquals(listOf(1), seen)
            vm.consumeFileImportResult(oldId)
            assertEquals(newId, vm.fileImportState.value?.operationId)
            assertFalse(vm.fileImportState.value!!.finished)
            release.complete(Unit)
            val result = fileResult(vm, newId)
            assertEquals(listOf(1, 2), seen)
            assertEquals(listOf(2L), result.importedComicIds)
            assertFalse(result.cancelled)
            vm.consumeFileImportResult(oldId)
            assertEquals(result, vm.fileImportState.value)
        } finally {
            release.complete(Unit)
        }
    }

    @Test
    fun cancellationWhileWaitingForOldCleanupStillFinishesItsOwnResult() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val vm = fileImportViewModel { uri, _, _ ->
            assertEquals(fileUri(1), uri)
            withContext(NonCancellable) { release.await() }
            Result.success(1L)
        }
        try {
            vm.importComics(listOf(fileUri(1)))
            val id = vm.importComics(listOf(fileUri(2)))!!
            vm.cancelImport()
            val result = fileResult(vm, id)
            assertTrue(result.cancelled)
            assertTrue(result.importedComicIds.isEmpty())
            release.complete(Unit)
            assertEquals(result, vm.fileImportState.value)
        } finally {
            release.complete(Unit)
        }
    }

    @Test
    fun cancellationBeforeDispatchDoesNotLeaveTheBatchPending() = runBlocking {
        val dispatcher = StandardTestDispatcher()
        val vm = fileImportViewModel(dispatcher) { _, _, _ -> error("Cancelled batch must not import") }
        val id = vm.importComics(listOf(fileUri(1)))!!
        vm.cancelImport()
        dispatcher.scheduler.runCurrent()
        assertTrue(fileResult(vm, id).cancelled)
        assertTrue(vm.fileImportState.value!!.importedComicIds.isEmpty())
    }

    @Test
    fun repositoryCancellationResultStopsTheBatchWithoutReportingAFileFailure() = runBlocking {
        var calls = 0
        val vm = fileImportViewModel { _, _, _ ->
            calls++
            Result.failure(kotlinx.coroutines.CancellationException("Cancelled"))
        }
        val id = vm.importComics(listOf(fileUri(1), fileUri(2)))!!
        val result = fileResult(vm, id)
        assertEquals(1, calls)
        assertTrue(result.cancelled)
        assertEquals(0, result.failedCount)
        assertTrue(result.importedComicIds.isEmpty())
        assertEquals("Import cancelled.", vm.message.value)
    }

    @Test
    fun creatorLookupQueriesUnloadedPagesAndUsesExactCachedRelationshipsOffline() = runBlocking {
        configureJellyfin()
        val person = net.digitalbc.leafreader.data.jellyfin.JellyfinPerson("Same name", id = "person-one")
        val known = net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueEntry(
            JellyfinItem("cached", "Saved book", false, container = "cbz"), "Book", false, people = listOf(person))
        val other = known.copy(item = known.item.copy(id = "other"), people = listOf(person.copy(id = "person-two")))
        val queries = mutableListOf<net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueQuery>()
        val online = JellyfinCatalogueViewModel(repository, settings, defaultDispatcher = testDispatcher, ioDispatcher = testDispatcher, creator = person,
            restore = { null }, readIndex = { null }, saveIndex = { _, _, _ -> },
            views = { Result.success(listOf(JellyfinView("books", "Comics", "books"))) },
            fetch = { query ->
                queries += query
                Result.success(if (query.creator == null)
                    net.digitalbc.leafreader.data.jellyfin.JellyfinCataloguePage(emptyList(), 0, 0, false)
                else if (query.startIndex == 0)
                    net.digitalbc.leafreader.data.jellyfin.JellyfinCataloguePage((0 until 30).map {
                        known.copy(item = known.item.copy(id = "result-$it")) }, 30, 31, true)
                else net.digitalbc.leafreader.data.jellyfin.JellyfinCataloguePage(
                    listOf(known.copy(item = known.item.copy(id = "unloaded"))), 31, 31, false))
            })
        stores += ViewModelStore().apply { put("online-creator", online) }
        online.ensure()
        withTimeout(3000) { online.state.first { it.entries.size == 30 } }
        online.load(more = true)
        withTimeout(3000) { online.state.first { it.entries.size == 31 } }
        assertEquals(31, online.state.value.entries.size)
        assertTrue(online.state.value.entries.any { it.item.id == "unloaded" })
        assertTrue(queries.filter { it.creator != null }.all { it.search.isEmpty() && it.libraryId == "books" })
        val saved = net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueIndex(listOf(known, other), true, 2, 2, 1, 1)
        val offline = JellyfinCatalogueViewModel(repository, settings, defaultDispatcher = testDispatcher, ioDispatcher = testDispatcher, creator = person,
            restore = { net.digitalbc.leafreader.data.jellyfin.JellyfinSavedCatalogue(
                listOf(JellyfinView("books", "Comics", "books")), JellyfinView("books", "Comics", "books"), saved) },
            readIndex = { null }, saveIndex = { _, _, _ -> },
            views = { Result.failure(java.io.IOException("Offline")) },
            fetch = { error("Offline creator lookup must use indexed credits") })
        stores += ViewModelStore().apply { put("offline-creator", offline) }
        offline.ensure()
        assertEquals(listOf("cached"), offline.state.value.entries.map { it.item.id })
        assertTrue(offline.state.value.offline)
        assertEquals(person, offline.state.value.creator)
    }

    @Test
    fun unreachableLaunchKeepsEmptyAndPopulatedLocalLibrariesAndConnection() = runBlocking {
        configureJellyfin()
        for (populated in listOf(false, true)) {
            if (populated) fakeComicDao.insert(createComic(1, "Retained comic"))
            val before = fakeComicDao.getAllDirect()
            val vm = JellyfinCatalogueViewModel(repository, settings, defaultDispatcher = testDispatcher, ioDispatcher = testDispatcher,
            restore = { null }, readIndex = { null }, saveIndex = { _, _, _ -> },
                views = { Result.failure(java.net.ConnectException("unreachable")) },
                fetch = { error("No catalogue request after failed discovery") })
            stores += ViewModelStore().apply { put("offline-$populated", vm) }
            vm.ensure()
            assertEquals(JellyfinFailureKind.UNAVAILABLE, vm.state.value.launchFailure)
            assertFalse(vm.state.value.loading)
            assertEquals(before, fakeComicDao.getAllDirect())
            assertEquals("https://example.test", settings.jellyfinServerUrl)
            assertEquals("user", settings.jellyfinUserId)
            assertFalse(viewModel.downloadedOnly.value) // Fallback is temporary, not a saved preference.
        }
    }

    @Test
    fun seriesSelectionFromSavedLibraryDoesNotCancelServerDiscovery() = runBlocking {
        configureJellyfin()
        val discovered = CompletableDeferred<Unit>()
        val books = JellyfinView("books", "Comics", "books")
        val entry = net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueEntry(
            JellyfinItem("one", "A book", false, container = "cbz"), "Book", false)
        val vm = JellyfinCatalogueViewModel(repository, settings,
            defaultDispatcher = testDispatcher, ioDispatcher = testDispatcher,
            restore = { net.digitalbc.leafreader.data.jellyfin.JellyfinSavedCatalogue(listOf(books), books, null) },
            readIndex = { null }, saveIndex = { _, _, _ -> },
            views = { discovered.await(); Result.success(listOf(books)) },
            fetch = { Result.success(net.digitalbc.leafreader.data.jellyfin.JellyfinCataloguePage(
                listOf(entry), 1, 1, false)) })
        stores += ViewModelStore().apply { put("saved-library-series", vm) }
        vm.ensure()
        assertEquals(books, vm.state.value.library)
        vm.browse(net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueView.SERIES, "", false)
        discovered.complete(Unit)
        withTimeout(3000) { vm.state.first { it.indexComplete } }
        assertEquals(listOf("one"), vm.state.value.entries.map { it.item.id })
        assertFalse(vm.state.value.offline)
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun seriesSelectionDuringColdCacheReadStillStartsCatalogueIndex() = runBlocking {
        configureJellyfin()
        val cacheRead = CompletableDeferred<Unit>()
        val releaseCache = CompletableDeferred<Unit>()
        val entry = net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueEntry(
            JellyfinItem("one", "A book", false, container = "cbz"), "Book", false)
        val vm = JellyfinCatalogueViewModel(repository, settings,
            defaultDispatcher = testDispatcher, ioDispatcher = testDispatcher,
            restore = { null }, readIndex = {
                cacheRead.complete(Unit)
                releaseCache.await()
                null
            }, saveIndex = { _, _, _ -> },
            views = { Result.success(listOf(JellyfinView("books", "Comics", "books"))) },
            fetch = { Result.success(net.digitalbc.leafreader.data.jellyfin.JellyfinCataloguePage(
                listOf(entry), 1, 1, false)) })
        stores += ViewModelStore().apply { put("cold-cache-series", vm) }
        vm.ensure()
        withTimeout(3000) { cacheRead.await() }
        // LibraryScreen applies its default Series query as soon as a library is exposed.
        if (vm.state.value.library != null)
            vm.browse(net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueView.SERIES, "", false)
        releaseCache.complete(Unit)
        withTimeout(3000) { vm.state.first { it.indexComplete } }
        vm.browse(net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueView.SERIES, "", false)
        assertEquals(listOf("one"), vm.state.value.entries.map { it.item.id })
        assertFalse(vm.state.value.loading)
    }

    @Test
    fun launchTimeoutCancelsDiscoveryAndAllowsExplicitRetry() {
        configureJellyfin()
        var cancelled = false
        var reachable = false
        val vm = JellyfinCatalogueViewModel(repository, settings, defaultDispatcher = testDispatcher, ioDispatcher = testDispatcher,
            restore = { null }, readIndex = { null }, saveIndex = { _, _, _ -> },
            views = {
                if (!reachable) {
                    try { kotlinx.coroutines.awaitCancellation() }
                    finally { cancelled = true }
                }
                Result.success(emptyList())
            })
        stores += ViewModelStore().apply { put("launch-timeout", vm) }
        vm.ensure()
        assertTrue(vm.state.value.loading)
        testDispatcher.scheduler.advanceTimeBy(JellyfinCatalogueViewModel.LAUNCH_TIMEOUT_MILLIS + 1)
        testDispatcher.scheduler.runCurrent()
        assertTrue(cancelled)
        assertFalse(vm.state.value.loading)
        assertEquals(JellyfinFailureKind.TIMEOUT, vm.state.value.launchFailure)
        reachable = true
        vm.retryConnection()
        assertNull(vm.state.value.launchFailure)
        assertTrue(vm.state.value.loaded)
    }

    @Test
    fun backgroundIndexAndCreatorRequestKeepIndependentLoadingAndCoverage() = runBlocking {
        configureJellyfin()
        val indexGate = CompletableDeferred<net.digitalbc.leafreader.data.jellyfin.JellyfinCataloguePage>()
        val creatorGate = CompletableDeferred<net.digitalbc.leafreader.data.jellyfin.JellyfinCataloguePage>()
        val vm = JellyfinCatalogueViewModel(repository, settings, defaultDispatcher = testDispatcher, ioDispatcher = testDispatcher,
            creator = net.digitalbc.leafreader.data.jellyfin.JellyfinPerson("Creator", id = "person"),
            restore = { null }, readIndex = { null }, saveIndex = { _, _, _ -> },
            views = { Result.success(listOf(JellyfinView("books", "Comics", "books"))) },
            fetch = { Result.success(if (it.creator == null) indexGate.await() else creatorGate.await()) })
        stores += ViewModelStore().apply { put("independent-index-query", vm) }
        vm.ensure()
        assertTrue(vm.state.value.loading)
        assertTrue(vm.state.value.indexing)
        indexGate.complete(net.digitalbc.leafreader.data.jellyfin.JellyfinCataloguePage(emptyList(), 0, 0, false))
        withTimeout(3000) { vm.state.first { it.indexComplete } }
        assertTrue(vm.state.value.loading)
        assertFalse(vm.state.value.indexing)
        val entry = net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueEntry(
            JellyfinItem("one", "A matching book", false), "Book", null)
        creatorGate.complete(net.digitalbc.leafreader.data.jellyfin.JellyfinCataloguePage(listOf(entry), 1, 1, false))
        withTimeout(3000) { vm.state.first { it.entries.size == 1 } }
        assertTrue(vm.state.value.indexComplete)
        assertFalse(vm.state.value.loading)
        assertFalse(vm.state.value.indexing)
    }

    @Test
    fun deniedRefreshCannotRevealOldQueryPagesAfterChangingViews() {
        configureJellyfin()
        val entry = net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueEntry(
            JellyfinItem("saved", "Private metadata", false, container = "cbz"), "Book", false)
        var denied = false
        val vm = JellyfinCatalogueViewModel(repository, settings, defaultDispatcher = testDispatcher, ioDispatcher = testDispatcher,
            restore = { null }, readIndex = { null }, saveIndex = { _, _, _ -> },
            views = { Result.success(listOf(JellyfinView("books", "Comics", "books"))) },
            fetch = {
                if (denied) Result.failure(JellyfinException(JellyfinFailureKind.ACCESS_DENIED, "Denied"))
                else Result.success(net.digitalbc.leafreader.data.jellyfin.JellyfinCataloguePage(
                    listOf(entry), 1, 1, false))
            })
        stores += ViewModelStore().apply { put("denied-index", vm) }
        vm.ensure()
        vm.select(net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueView.SERIES)
        vm.select(net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueView.ALL)
        denied = true
        vm.refresh()
        assertEquals(JellyfinFailureKind.ACCESS_DENIED, vm.state.value.launchFailure)
        vm.select(net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueView.SERIES)
        assertTrue(vm.state.value.entries.isEmpty())
    }

    @Test
    fun launchAuthenticationAndPermissionFailuresStayDistinctFromUnreachable() {
        configureJellyfin()
        for (kind in listOf(JellyfinFailureKind.AUTHENTICATION, JellyfinFailureKind.ACCESS_DENIED)) {
            val vm = JellyfinCatalogueViewModel(repository, settings, defaultDispatcher = testDispatcher, ioDispatcher = testDispatcher,
            restore = { null }, readIndex = { null }, saveIndex = { _, _, _ -> },
                views = { Result.failure(JellyfinException(kind, "Denied")) })
            stores += ViewModelStore().apply { put("launch-$kind", vm) }
            vm.ensure()
            assertEquals(kind, vm.state.value.launchFailure)
            assertFalse(vm.state.value.loading)
            assertTrue(settings.hasJellyfinConfig)
        }
    }

    @Test
    fun cachedFirstPageFallsBackAndRetryRetainsContextUntilNetworkSuccess() = runBlocking {
        configureJellyfin()
        val gate = CompletableDeferred<Unit>()
        var retrying = false
        val entry = net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueEntry(
            JellyfinItem("one", "One", false, container = "cbz"), "Book", false)
        val vm = JellyfinCatalogueViewModel(repository, settings, defaultDispatcher = testDispatcher, ioDispatcher = testDispatcher,
            restore = { null }, readIndex = { null }, saveIndex = { _, _, _ -> },
            views = { Result.success(listOf(JellyfinView("books", "Comics", "books"))) },
            fetch = {
                if (retrying) gate.await()
                Result.success(net.digitalbc.leafreader.data.jellyfin.JellyfinCataloguePage(
                    listOf(entry), 1, 1, false, fromCache = !retrying))
            })
        stores += ViewModelStore().apply { put("launch-cache", vm) }
        vm.ensure()
        assertEquals(JellyfinFailureKind.UNAVAILABLE, vm.state.value.launchFailure)
        assertEquals(listOf(entry), vm.state.value.entries)
        val context = vm.state.value
        retrying = true
        vm.retryConnection()
        assertTrue(vm.state.value.indexing)
        assertEquals(context.launchFailure, vm.state.value.launchFailure)
        assertEquals(context.entries, vm.state.value.entries)
        assertEquals(context.library, vm.state.value.library)
        gate.complete(Unit)
        assertNull(vm.state.value.launchFailure)
        assertFalse(vm.state.value.offline)
        assertEquals(context.entries, vm.state.value.entries)
        assertEquals(context.search, vm.state.value.search)
    }

    @Test
    fun firstCatalogueTimeoutAlsoFallsBackAndCancelsPendingFetch() {
        configureJellyfin()
        var cancelled = false
        val vm = JellyfinCatalogueViewModel(repository, settings, defaultDispatcher = testDispatcher, ioDispatcher = testDispatcher,
            restore = { null }, readIndex = { null }, saveIndex = { _, _, _ -> },
            views = { Result.success(listOf(JellyfinView("books", "Comics", "books"))) },
            fetch = {
                try { kotlinx.coroutines.awaitCancellation() }
                finally { cancelled = true }
            })
        stores += ViewModelStore().apply { put("catalogue-timeout", vm) }
        vm.ensure()
        testDispatcher.scheduler.advanceTimeBy(JellyfinCatalogueViewModel.LAUNCH_TIMEOUT_MILLIS + 1)
        testDispatcher.scheduler.runCurrent()
        assertTrue(cancelled)
        assertEquals(JellyfinFailureKind.TIMEOUT, vm.state.value.launchFailure)
        assertFalse(vm.state.value.loading)
        assertEquals("books", vm.state.value.library?.id)
    }

    @Test
    fun seriesNameSearchKeepsMembersAvailableForDrillDown() = runBlocking {
        val member = createComic(1, "Issue 1").copy(series = "Forest")
        fakeComicDao.setComics(listOf(member, createComic(2, "Other")))
        viewModel.setSearchQuery("forest")
        val matching = withTimeout(3000) { viewModel.comics.first { it.isNotEmpty() } }
        assertEquals(listOf(member), matching)
    }

    @Test
    fun coverSelectionsSurviveBrowseAndPreferencesAndClearOnlyRemoteOnAccountChange() {
        configureJellyfin()
        var auth = JellyfinAuth("token", "user", "server", "Reader")
        val vm = detailViewModel(auth = { auth }) { Result.failure(IllegalStateException("unused")) }
        vm.rememberCoverSelection("remote:scoped-run", "second")
        vm.rememberCoverSelection("local:3:Run:", "local-second")
        vm.setLibraryFilter(LibraryFilter.SERIES)
        vm.refreshLibraryPreferences()
        vm.refreshDownloadedJellyfinItems()
        assertEquals("second", vm.coverSelections.value["remote:scoped-run"])
        assertEquals("local-second", vm.coverSelections.value["local:3:Run:"])
        auth = auth.copy(userId = "other")
        vm.refreshDownloadedJellyfinItems()
        assertFalse(vm.coverSelections.value.containsKey("remote:scoped-run"))
        assertEquals("local-second", vm.coverSelections.value["local:3:Run:"])
    }

    @Test
    fun preparedRowsPublishTheSameBrowseContextAsTheirItems() = runBlocking {
        fakeComicDao.setComics(listOf(
            createComic(1, "Forest 1").copy(series = "Forest"),
            createComic(2, "Forest 2", lastPage = 5).copy(series = "Forest")))
        viewModel.setSearchQuery("Forest")
        viewModel.setLibraryFilter(LibraryFilter.SERIES)
        viewModel.unreadOnly.value = true
        val prepared = withTimeout(3000) { viewModel.libraryRows.first {
            it.filter == LibraryFilter.SERIES && it.query == "Forest" && it.unreadOnly
        } }
        assertEquals(listOf(1L), (prepared.items.single() as LibraryItem.SeriesStack).issues.map { it.id })
        assertEquals(setOf(1L), prepared.selectableComicIds)
    }

    @Test
    fun returningToSearchRestoresLoadedPagesWithoutRestartingAtPageOne() {
        configureJellyfin()
        var requests = 0
        val vm = JellyfinCatalogueViewModel(repository, settings, defaultDispatcher = testDispatcher,
            ioDispatcher = testDispatcher, restore = { null }, readIndex = { null }, saveIndex = { _, _, _ -> },
            views = { Result.success(listOf(JellyfinView("books", "Comics", "books"))) },
            fetch = { query ->
                requests++
                val entries = if (query.search.isBlank()) emptyList() else listOf(
                    net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueEntry(
                        JellyfinItem("result-${query.startIndex}", "Run", false, container = "cbz"), "Book", false))
                Result.success(net.digitalbc.leafreader.data.jellyfin.JellyfinCataloguePage(entries,
                    if (entries.isEmpty()) 0 else query.startIndex + 1,
                    if (entries.isEmpty()) 0 else 3, entries.isNotEmpty() && query.startIndex < 2))
            })
        stores += ViewModelStore().apply { put("search-pages", vm) }
        vm.ensure()
        vm.browse(net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueView.ALL, "run", false)
        vm.load(more = true)
        assertEquals(2, vm.state.value.entries.size)
        val before = requests
        vm.browse(net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueView.DOWNLOADED, "", false)
        vm.browse(net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueView.ALL, "run", false)
        assertEquals(listOf("result-0", "result-1"), vm.state.value.entries.map { it.item.id })
        assertEquals(2, vm.state.value.nextIndex)
        assertEquals(before, requests)
    }

    @Test
    fun menuPreferencesRefreshWithoutChangingBrowseOrUnreadState() {
        viewModel.setLibraryFilter(LibraryFilter.OFFLINE)
        viewModel.unreadOnly.value = true
        viewModel.setSearchQuery("forest")
        settings.libraryColumns = 4
        settings.defaultRightToLeft = true
        viewModel.refreshLibraryPreferences()
        assertEquals(4, viewModel.libraryColumns.value)
        assertTrue(viewModel.defaultRightToLeft.value)
        assertEquals(LibraryFilter.OFFLINE, viewModel.libraryFilter.value)
        assertTrue(viewModel.unreadOnly.value)
        assertEquals("forest", viewModel.searchQuery.value)
    }

    @Test
    fun independentUnreadQueriesServerScopeAndPreservesCollectionsAndOfflineChoice() {
        configureJellyfin()
        val queries = mutableListOf<net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueQuery>()
        val vm = JellyfinCatalogueViewModel(repository, settings, defaultDispatcher = testDispatcher,
            ioDispatcher = testDispatcher, restore = { null }, readIndex = { null }, saveIndex = { _, _, _ -> },
            views = { Result.success(listOf(JellyfinView("books", "Comics", "books"))) },
            fetch = { query ->
                queries += query
                Result.success(net.digitalbc.leafreader.data.jellyfin.JellyfinCataloguePage(emptyList(), 0, 0, false))
            })
        stores += ViewModelStore().apply { put("independent-unread", vm) }
        vm.ensure()
        vm.browse(net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueView.SERIES, "forest", true)
        assertEquals(net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueView.UNREAD, queries.last().view)
        assertEquals("forest", queries.last().search)
        assertTrue(vm.state.value.unreadOnly)
        vm.browse(net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueView.LISTS, "", true)
        assertEquals(net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueView.LISTS, queries.last().view)
        vm.openCollection(JellyfinItem("collection", "Ordered", true))
        assertEquals("collection", queries.last().collectionId)
        assertEquals(net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueView.UNREAD, queries.last().view)
        vm.closeCollection()
        assertTrue(vm.state.value.unreadOnly)
        viewModel.setLibraryFilter(LibraryFilter.OFFLINE)
        viewModel.unreadOnly.value = true
        vm.refresh()
        assertEquals(LibraryFilter.OFFLINE, viewModel.libraryFilter.value)
        assertTrue(viewModel.unreadOnly.value)
    }

    @Test
    fun offlineCollectionUsesSavedUnreadPagesWithoutMixingPaginationScopes() {
        configureJellyfin()
        val library = JellyfinView("books", "Comics", "books")
        val first = net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueEntry(
            JellyfinItem("first", "First issue", false, container = "cbz"), "Book", false)
        val second = first.copy(item = first.item.copy(id = "second", name = "Second issue"))
        val read = first.copy(item = first.item.copy(id = "read"), played = true)
        val unknown = first.copy(item = first.item.copy(id = "unknown"), played = null)
        for (fullMembershipCached in listOf(false, true)) {
            val cachedQueries = mutableListOf<net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueQuery>()
            val vm = JellyfinCatalogueViewModel(repository, settings,
                defaultDispatcher = testDispatcher, ioDispatcher = testDispatcher,
                views = { Result.failure(java.io.IOException("Offline")) },
                fetch = { error("Offline collection must use saved pages") },
                restore = { net.digitalbc.leafreader.data.jellyfin.JellyfinSavedCatalogue(
                    listOf(library), library,
                    net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueIndex(emptyList(), true, 0, 0, 1, 1)) },
                cached = { query ->
                    cachedQueries += query
                    if (query.collectionId != "collection" || query.limit != 30 || query.search.isNotEmpty()) null
                    else when {
                        query.view == net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueView.LISTS &&
                            fullMembershipCached && query.startIndex == 0 ->
                            net.digitalbc.leafreader.data.jellyfin.JellyfinCataloguePage(
                                listOf(first, read, unknown), 30, 60, true, fromCache = true)
                        query.view == net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueView.UNREAD ->
                            if (query.startIndex == 0)
                                net.digitalbc.leafreader.data.jellyfin.JellyfinCataloguePage(
                                    listOf(first), 30, 31, true, fromCache = true)
                            else net.digitalbc.leafreader.data.jellyfin.JellyfinCataloguePage(
                                listOf(second), 31, 31, false, fromCache = true)
                        else -> null
                    }
                })
            stores += ViewModelStore().apply { put("offline-collection-$fullMembershipCached", vm) }
            vm.ensure()
            vm.browse(net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueView.LISTS, "", true)
            vm.openCollection(JellyfinItem("collection", "Ordered", true))
            assertEquals(if (fullMembershipCached) listOf("first") else listOf("first", "second"),
                vm.state.value.entries.map { it.item.id })
            assertEquals(fullMembershipCached, vm.state.value.partialSearch)
            assertNull(vm.state.value.error)
            if (fullMembershipCached) assertFalse(cachedQueries.any {
                it.collectionId == "collection" &&
                    it.view == net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueView.UNREAD
            })
            vm.search("Second")
            assertEquals(if (fullMembershipCached) emptyList<String>() else listOf("second"),
                vm.state.value.entries.map { it.item.id })
            vm.browse(net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueView.LISTS, "", false)
            assertEquals(if (fullMembershipCached) listOf("first", "read", "unknown") else emptyList<String>(),
                vm.state.value.entries.map { it.item.id })
            assertEquals(!fullMembershipCached, vm.state.value.error != null)
        }
    }

    @Test
    fun catalogueRetainsEachViewPaginationAndRejectsAnOldAccountResponse() = runBlocking {
        configureJellyfin()
        val requests = mutableListOf<net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueQuery>()
        val gate = CompletableDeferred<Unit>()
        var held = false
        val vm = JellyfinCatalogueViewModel(repository, settings, defaultDispatcher = testDispatcher, ioDispatcher = testDispatcher,
            restore = { null }, readIndex = { null }, saveIndex = { _, _, _ -> },
            views = { Result.success(listOf(JellyfinView("books", "Comics", "books"))) },
            fetch = { query ->
                requests += query
                if (held) withContext(NonCancellable) { gate.await() }
                val id = "${query.view}-${query.startIndex}"
                Result.success(net.digitalbc.leafreader.data.jellyfin.JellyfinCataloguePage(
                    listOf(net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueEntry(
                        JellyfinItem(id, id, false, container = "cbz"), "Book", false)),
                    query.startIndex + 1, 2, query.startIndex == 0))
            })
        stores += ViewModelStore().apply { put("catalogue", vm) }
        vm.ensure()
        assertEquals(2, vm.state.value.entries.size)
        vm.load(more = true)
        assertEquals(2, vm.state.value.entries.size)
        vm.select(net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueView.UNREAD)
        vm.select(net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueView.ALL)
        assertEquals(2, vm.state.value.entries.size)
        assertEquals(4, requests.size)
        held = true
        vm.search("held")
        settings.jellyfinUserId = "new-user"
        vm.ensure()
        gate.complete(Unit)
        assertTrue(vm.state.value.entries.none { it.item.id == "ALL-0" && vm.state.value.search == "held" })
        assertEquals("", vm.state.value.search)
    }

    @Test
    fun completeSavedIndexRendersBeforeNetworkAndSupportsOfflineSearch() = runBlocking {
        configureJellyfin()
        val gate = CompletableDeferred<Unit>()
        val entries = (1..3).map { number ->
            net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueEntry(
                JellyfinItem("issue-$number", "Issue $number", false, container = "cbz",
                    parentId = "run", seriesName = null), "Book", null,
                folder = net.digitalbc.leafreader.data.jellyfin.JellyfinFolderIdentity("run", "Series run", "books"))
        }
        val saved = net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueIndex(entries, true, 3, 3, 1, 1)
        var requests = 0
        var reachable = false
        val library = JellyfinView("books", "Comics", "books")
        val libraries = listOf(library, JellyfinView("movies", "Movies", "movies"))
        val vm = JellyfinCatalogueViewModel(repository, settings, defaultDispatcher = testDispatcher, ioDispatcher = testDispatcher,
            restore = { net.digitalbc.leafreader.data.jellyfin.JellyfinSavedCatalogue(libraries, library, saved) },
            readIndex = { saved }, saveIndex = { _, _, _ -> },
            resolveFolder = { _, _ -> entries.first().folder },
            views = { if (reachable) Result.success(libraries)
                else { gate.await(); Result.failure(java.io.IOException("offline")) } },
            fetch = { query ->
                requests++
                val matches = entries.filter { query.search.isEmpty() || it.item.name.contains(query.search) }
                Result.success(net.digitalbc.leafreader.data.jellyfin.JellyfinCataloguePage(
                    matches, matches.size, matches.size, false))
            })
        stores += ViewModelStore().apply { put("series-cache", vm) }
        vm.ensure()
        assertEquals(entries, vm.state.value.entries) // Visible before discovery completes.
        assertEquals(listOf(library), vm.state.value.libraries) // Cached discovery has the same Books scope.
        gate.complete(Unit)
        assertTrue(vm.state.value.downloadedFallback)
        vm.select(net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueView.SERIES)
        val group = net.digitalbc.leafreader.data.jellyfin.JellyfinSeries.groups("s", "u", "books", vm.state.value.entries).single()
        vm.browseSavedCatalogue()
        assertFalse(vm.state.value.downloadedFallback)
        vm.search("Issue 2")
        assertEquals(listOf("issue-2"), vm.state.value.entries.map { it.item.id })
        assertEquals(group.key, net.digitalbc.leafreader.data.jellyfin.JellyfinSeries.groups(
            "s", "u", "books", vm.state.value.entries).single().key)
        assertTrue(vm.state.value.indexComplete)
        assertEquals(0, requests)
        assertTrue(fakeComicDao.getAllDirect().isEmpty())
        reachable = true
        vm.retryView() // The visible Retry refresh must leave the cache-only path.
        withTimeout(3000) { vm.state.first { !it.offline && !it.indexing } }
        assertTrue(requests >= 2) // Fresh inventory and the active search.
        assertEquals("Issue 2", vm.state.value.search)
        assertEquals(listOf("issue-2"), vm.state.value.entries.map { it.item.id })
        assertNull(vm.state.value.launchFailure)
        assertNull(vm.state.value.indexError)
    }

    @Test
    fun catalogueRetryAndDownloadedDoNotHideLocalListsOrRequireARequest() {
        configureJellyfin()
        var requests = 0
        val vm = JellyfinCatalogueViewModel(repository, settings, defaultDispatcher = testDispatcher, ioDispatcher = testDispatcher,
            restore = { null }, readIndex = { null }, saveIndex = { _, _, _ -> },
            views = { Result.success(listOf(JellyfinView("books", "Comics", "books"))) },
            fetch = { requests++; Result.failure(java.io.IOException("offline")) })
        stores += ViewModelStore().apply { put("catalogue", vm) }
        vm.ensure()
        assertTrue(vm.state.value.indexError != null)
        vm.select(net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueView.DOWNLOADED)
        assertEquals(1, requests)
        vm.select(net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueView.ALL)
        assertEquals(1, requests)
        vm.load()
        assertEquals(2, requests)
    }

    @Test
    fun partialIndexFailurePreservesMembersAndCompleteRefreshReconcilesOnlyMetadata() {
        configureJellyfin()
        fun entry(id: String) = net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueEntry(
            JellyfinItem(id, id, false, container = "cbz"), "Book", false)
        val old = net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueIndex(listOf(entry("old")), true, 1, 1, 1, 1)
        val library = JellyfinView("books", "Comics", "books")
        val persisted = mutableListOf<net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueIndex>()
        var fail = true
        val vm = JellyfinCatalogueViewModel(repository, settings, defaultDispatcher = testDispatcher, ioDispatcher = testDispatcher,
            restore = { net.digitalbc.leafreader.data.jellyfin.JellyfinSavedCatalogue(listOf(library), library, old) },
            readIndex = { old }, saveIndex = { _, snapshot, _ -> persisted += snapshot },
            views = { Result.success(listOf(library)) },
            fetch = { query ->
                if (fail && query.startIndex > 0) Result.failure(java.io.IOException("interrupted"))
                else Result.success(net.digitalbc.leafreader.data.jellyfin.JellyfinCataloguePage(
                    listOf(entry("new")), 1, if (fail) 2 else 1, fail))
            })
        stores += ViewModelStore().apply { put("inventory-removal", vm) }
        vm.ensure()
        assertEquals(setOf("old", "new"), vm.state.value.entries.map { it.item.id }.toSet())
        assertFalse(vm.state.value.indexComplete)
        assertFalse(persisted.last().complete)
        assertTrue(vm.state.value.indexError != null)
        fail = false
        vm.refresh()
        assertEquals(listOf("new"), vm.state.value.entries.map { it.item.id })
        assertTrue(vm.state.value.indexComplete)
        assertTrue(persisted.last().complete)
    }

    @Test
    fun onlineSearchFindsUnindexedItemsAndNextPageFailureRequiresExplicitRetry() = runBlocking {
        configureJellyfin()
        val gate = CompletableDeferred<Unit>()
        var searchCalls = 0
        var failNext = true
        val requests = mutableListOf<net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueQuery>()
        fun entry(id: String) = net.digitalbc.leafreader.data.jellyfin.JellyfinCatalogueEntry(
            JellyfinItem(id, id, false, container = "cbz"), "Book", null)
        val vm = JellyfinCatalogueViewModel(repository, settings, defaultDispatcher = testDispatcher, ioDispatcher = testDispatcher,
            restore = { null }, readIndex = { null }, saveIndex = { _, _, _ -> },
            views = { Result.success(listOf(JellyfinView("books", "Comics", "books"))) },
            fetch = { query ->
                requests += query
                if (query.search.isBlank()) {
                    if (query.startIndex > 0) gate.await()
                    Result.success(net.digitalbc.leafreader.data.jellyfin.JellyfinCataloguePage(
                        listOf(entry("index-${query.startIndex}")), query.startIndex + 1, 2, query.startIndex == 0))
                } else {
                    searchCalls++
                    if (query.startIndex > 0 && failNext) Result.failure(java.io.IOException("page failed"))
                    else Result.success(net.digitalbc.leafreader.data.jellyfin.JellyfinCataloguePage(
                        listOf(entry("far-${query.startIndex}")), query.startIndex + 1, 2, query.startIndex == 0))
                }
            })
        stores += ViewModelStore().apply { put("search-pages", vm) }
        vm.ensure()
        vm.search("far")
        assertEquals(listOf("far-0"), vm.state.value.entries.map { it.item.id })
        assertEquals("books", requests.last().libraryId)
        assertEquals("far", requests.last().search)
        vm.load(more = true)
        assertTrue(vm.state.value.error != null)
        vm.load(more = true)
        assertEquals(2, searchCalls) // No retry loop from near-end observation.
        failNext = false
        vm.retryView()
        assertEquals(listOf("far-0", "far-1"), vm.state.value.entries.map { it.item.id })
        assertFalse(vm.state.value.hasMore)
        gate.complete(Unit)
        Unit
    }

    @Test
    fun foregroundRefreshIsQuietThrottledAndDoesNotResetSearch() {
        configureJellyfin()
        var clock = 1L
        var passes = 0
        val vm = JellyfinCatalogueViewModel(repository, settings, defaultDispatcher = testDispatcher, ioDispatcher = testDispatcher,
            restore = { null }, readIndex = { null }, saveIndex = { _, _, _ -> },
            elapsedNanos = { clock },
            views = { Result.success(listOf(JellyfinView("books", "Comics", "books"))) },
            fetch = {
                passes++
                Result.success(net.digitalbc.leafreader.data.jellyfin.JellyfinCataloguePage(emptyList(), 0, 0, false))
            })
        stores += ViewModelStore().apply { put("foreground", vm) }
        vm.ensure()
        vm.onForeground()
        assertEquals(1, passes)
        clock += 61_000_000_000L
        vm.onForeground()
        assertEquals(2, passes)
        assertFalse(vm.state.value.refreshing)
        vm.onForeground()
        assertEquals(2, passes)
    }

    @Test
    fun folderPositionKeysSeparateParentsServersAndAccounts() {
        configureJellyfin()
        val first = viewModel.jellyfinBrowsePositionKey("folder-a")
        val parent = viewModel.jellyfinBrowsePositionKey(null)
        assertTrue(first != parent)
        assertTrue(first != viewModel.jellyfinBrowsePositionKey("folder-b"))
        settings.jellyfinUserId = "other"
        assertTrue(first != viewModel.jellyfinBrowsePositionKey("folder-a"))
        settings.jellyfinUserId = "user"
        settings.jellyfinServerUrl = "https://other.test"
        assertTrue(first != viewModel.jellyfinBrowsePositionKey("folder-a"))
    }

    @Test
    fun localOnlyEntryMakesNoJellyfinRequests() {
        settings.clearJellyfinConfig()
        val vm = landingViewModel(
            views = { error("Local-only launch must not request libraries") },
            items = { error("Local-only launch must not request comics") },
        )
        vm.ensureJellyfinLanding()
        assertFalse(vm.hasJellyfinConfig)
        assertFalse(vm.jellyfinBrowser.value.loading)
        assertNull(vm.jellyfinBrowser.value.currentParentId)
    }

    @Test
    fun oneBooksLibraryOpensCatalogueAndReentryPreservesIt() {
        configureJellyfin()
        var viewRequests = 0
        var itemRequests = 0
        val comic = JellyfinItem("comic", "Remote comic", false, container = "cbz")
        val vm = landingViewModel(
            views = {
                viewRequests++
                Result.success(listOf(JellyfinView("movies", "Movies", "movies"),
                    JellyfinView("books", "Comics", "books")))
            },
            items = { id ->
                assertEquals("books", id)
                itemRequests++
                Result.success(JellyfinItemsResult(listOf(comic), 1, 0))
            },
        )
        vm.ensureJellyfinLanding()
        assertEquals("books", vm.jellyfinBrowser.value.currentParentId)
        assertEquals(listOf(comic), vm.jellyfinBrowser.value.items)
        vm.toggleJellyfinSelection("comic")
        val before = vm.jellyfinBrowser.value
        vm.ensureJellyfinLanding()
        assertEquals(before, vm.jellyfinBrowser.value)
        assertEquals(1, viewRequests)
        assertEquals(1, itemRequests)
    }

    @Test
    fun multipleBookLibrariesShowChoiceWithoutFetchingEither() {
        configureJellyfin()
        val books = listOf(JellyfinView("western", "Comics", "books"), JellyfinView("manga", "Manga", "books"))
        val vm = landingViewModel(
            views = { Result.success(books + JellyfinView("music", "Music", "music")) },
            items = { error("Ambiguous libraries must not auto-open") },
        )
        vm.ensureJellyfinLanding()
        assertTrue(vm.jellyfinBrowser.value.isRoot)
        assertEquals(books, vm.jellyfinBrowser.value.views)
        assertFalse(vm.jellyfinBrowser.value.loading)
    }

    @Test
    fun failedLandingDoesNotLoopAndSupportsExplicitRetry() {
        configureJellyfin()
        var requests = 0
        val vm = landingViewModel(
            views = {
                if (++requests == 1) Result.failure(JellyfinException(JellyfinFailureKind.UNAVAILABLE, "Offline"))
                else Result.success(emptyList())
            },
            items = { error("No book library") },
        )
        vm.ensureJellyfinLanding()
        assertEquals(JellyfinFailureKind.UNAVAILABLE, vm.jellyfinBrowser.value.failure)
        vm.ensureJellyfinLanding()
        assertEquals(1, requests)
        vm.ensureJellyfinLanding(force = true)
        assertEquals(2, requests)
        assertNull(vm.jellyfinBrowser.value.failure)
    }

    @Test
    fun cancelledLandingRestartsOnReturnAndAccountChangeClearsOldItems() {
        configureJellyfin()
        val gate = CompletableDeferred<Unit>()
        var requests = 0
        val vm = landingViewModel(
            views = {
                if (++requests == 1) gate.await()
                Result.success(listOf(JellyfinView("books", settings.jellyfinUserId, "books")))
            },
            items = { Result.success(JellyfinItemsResult(listOf(JellyfinItem(settings.jellyfinUserId, "Comic", false)), 1, 0)) },
        )
        vm.ensureJellyfinLanding()
        vm.cancelJellyfinBrowse()
        vm.ensureJellyfinLanding()
        assertEquals("user", vm.jellyfinBrowser.value.items.single().id)
        settings.jellyfinUserId = "other"
        vm.ensureJellyfinLanding()
        assertEquals("other", vm.jellyfinBrowser.value.items.single().id)
        assertEquals(3, requests)
        assertEquals(emptySet<String>(), vm.jellyfinBrowser.value.selectedItemIds)
    }

    @Test
    fun blockedCoverCredentialsLeaveTheUiDispatcherResponsive() = runBlocking {
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val main = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
            Thread(task, "leaf-cover-ui")
        }.asCoroutineDispatcher()
        val authThread = java.util.concurrent.atomic.AtomicReference<String>()
        val blockCoverRead = java.util.concurrent.atomic.AtomicBoolean(false)
        val vm = LibraryViewModel(repository, settings, defaultDispatcher = testDispatcher, jellyfinAuth = {
            if (blockCoverRead.get()) {
                authThread.set(Thread.currentThread().name)
                entered.countDown()
                check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
            }
            null
        }).also { stores += ViewModelStore().apply { put("cover-io", it) } }
        blockCoverRead.set(true)
        val load = launch(main) { assertNull(vm.loadJellyfinCover(JellyfinItem("one", "Cover", false), 384)) }
        try {
            assertTrue(withContext(Dispatchers.IO) { entered.await(3, java.util.concurrent.TimeUnit.SECONDS) })
            assertTrue(withTimeout(1000) { withContext(main) { true } })
            assertTrue(authThread.get() != "leaf-cover-ui")
        } finally {
            release.countDown()
            load.join()
            main.close()
        }
    }

    private fun detailViewModel(
        auth: () -> JellyfinAuth? = { JellyfinAuth("token", "user", "server", "Reader") },
        local: suspend (JellyfinItemIdentity) -> Result<ComicEntity?> = { Result.success(null) },
        fetch: suspend (JellyfinItemIdentity) -> Result<JellyfinItemDetails>,
    ): LibraryViewModel = LibraryViewModel(
        repository, settings, defaultDispatcher = testDispatcher, loadJellyfinDetails = fetch, jellyfinAuth = auth,
        loadLocalJellyfinComic = local,
    ).also { stores += ViewModelStore().apply { put("detail", it) } }

    @Test
    fun openingAndClosingDetailPreservesBrowserAndLocalFilters() {
        val item = JellyfinItem("one", "Comic", false)
        var requests = 0
        val vm = detailViewModel { identity ->
            requests++
            Result.success(JellyfinItemDetails(identity, item, overview = "Synopsis"))
        }
        vm.setSearchQuery("forest")
        vm.setLibraryFilter(LibraryFilter.ALL)
        vm.unreadOnly.value = true
        val before = vm.jellyfinBrowser.value
        vm.openJellyfinDetail(item)
        assertEquals("Synopsis", vm.jellyfinDetail.value?.details?.overview)
        assertFalse(vm.jellyfinDetail.value!!.loading)
        vm.closeJellyfinDetail()
        assertNull(vm.jellyfinDetail.value)
        assertEquals(before, vm.jellyfinBrowser.value)
        assertEquals("forest", vm.searchQuery.value)
        assertEquals(LibraryFilter.ALL, vm.libraryFilter.value)
        assertTrue(vm.unreadOnly.value)
        assertEquals(1, requests)
    }

    @Test
    fun newerDetailWinsEvenIfCancelledLoaderReturnsLate() = runBlocking {
        val first = JellyfinItem("first", "First", false)
        val second = JellyfinItem("second", "Second", false)
        val gate = CompletableDeferred<Unit>()
        val oldFinished = CompletableDeferred<Unit>()
        val vm = detailViewModel { identity ->
            if (identity.itemId == first.id) {
                withContext(NonCancellable) {
                    gate.await()
                    oldFinished.complete(Unit)
                }
                Result.success(JellyfinItemDetails(identity, first))
            } else Result.success(JellyfinItemDetails(identity, second))
        }
        vm.openJellyfinDetail(first)
        assertTrue(vm.jellyfinDetail.value!!.loading)
        vm.openJellyfinDetail(second)
        gate.complete(Unit)
        oldFinished.await()
        assertEquals(second, vm.jellyfinDetail.value?.details?.item)
        vm.closeJellyfinDetail()
        assertNull(vm.jellyfinDetail.value)
    }

    @Test
    fun retryReplacesFailureWithDetails() {
        val item = JellyfinItem("item", "Comic", false)
        var requests = 0
        val vm = detailViewModel { identity ->
            if (++requests == 1) Result.failure(
                JellyfinException(JellyfinFailureKind.UNAVAILABLE, "Fixture offline"))
            else Result.success(JellyfinItemDetails(identity, item))
        }
        vm.openJellyfinDetail(item)
        assertEquals(JellyfinFailureKind.UNAVAILABLE, vm.jellyfinDetail.value?.failure)
        vm.retryJellyfinDetail()
        assertNull(vm.jellyfinDetail.value?.failure)
        assertEquals(item, vm.jellyfinDetail.value?.details?.item)
        assertEquals(2, requests)
    }

    @Test
    fun noAuthenticationAndFoldersDoNotRequestDetails() {
        var requests = 0
        val vm = detailViewModel(auth = { null }) {
            requests++
            error("Should not request details")
        }
        vm.openJellyfinDetail(JellyfinItem("folder", "Folder", true))
        assertNull(vm.jellyfinDetail.value)
        vm.openJellyfinDetail(JellyfinItem("comic", "Comic", false))
        assertEquals(JellyfinFailureKind.AUTHENTICATION, vm.jellyfinDetail.value?.failure)
        assertEquals(0, requests)
    }

    @Test
    fun changedAccountCannotPublishInFlightDetails() = runBlocking {
        var auth = JellyfinAuth("token", "user", "server", "Reader")
        val item = JellyfinItem("same-id", "Comic", false)
        val gate = CompletableDeferred<Unit>()
        val finished = CompletableDeferred<Unit>()
        var requests = 0
        val vm = detailViewModel(auth = { auth }) { identity ->
            requests++
            gate.await()
            finished.complete(Unit)
            Result.success(JellyfinItemDetails(identity, item))
        }
        vm.openJellyfinDetail(item)
        auth = auth.copy(userId = "other")
        gate.complete(Unit)
        finished.await()
        assertNull(vm.jellyfinDetail.value?.details)
        assertEquals(JellyfinFailureKind.AUTHENTICATION, vm.jellyfinDetail.value?.failure)
        vm.retryJellyfinDetail()
        assertEquals(1, requests)
        assertNull(vm.jellyfinDetail.value?.details)
    }

    @Test
    fun retainedCopyCanOpenWhileMetadataFailsAndDisconnected() {
        val item = JellyfinItem("item", "Comic", false)
        val saved = createComic(42, "Comic", lastPage = 4).copy(lastSlot = 2)
        var auth: JellyfinAuth? = JellyfinAuth("token", "user", "server", "Reader")
        var checks = 0
        val vm = detailViewModel(auth = { auth }, local = {
            checks++
            Result.success(saved)
        }) { Result.failure(JellyfinException(JellyfinFailureKind.UNAVAILABLE, "Offline")) }
        vm.openJellyfinDetail(item)
        assertEquals(saved, vm.jellyfinDetail.value?.localComic)
        assertEquals(JellyfinFailureKind.UNAVAILABLE, vm.jellyfinDetail.value?.failure)
        auth = null
        vm.refreshJellyfinLocalCopy(openWhenReady = true)
        assertEquals(42L, vm.consumeJellyfinRead())
        assertNull(vm.consumeJellyfinRead())
        assertEquals(2, checks)
        assertEquals(4, vm.jellyfinDetail.value?.localComic?.lastPage)
        assertEquals(2, vm.jellyfinDetail.value?.localComic?.lastSlot)
    }

    @Test
    fun retainedDetailReflectsCommittedPositionAndCompletionChanges() {
        val item = JellyfinItem("item", "Comic", false)
        val saved = createComic(42, "Comic", lastPage = 4, series = "Fixture")
        fakeComicDao.setComics(listOf(saved))
        val vm = detailViewModel(local = { Result.success(saved) }) { identity ->
            Result.success(JellyfinItemDetails(identity, item))
        }
        vm.openJellyfinDetail(item)
        val changed = saved.copy(lastPage = 1, completionOverride = true)
        fakeComicDao.setComics(listOf(changed))
        assertEquals(changed, vm.jellyfinDetail.value!!.localComic)
        assertTrue(vm.jellyfinDetail.value!!.localComic!!.isCompleted)
    }

    @Test
    fun repeatedReadAndResumeDoNotReplacePendingNavigation() = runBlocking {
        val item = JellyfinItem("item", "Comic", false)
        val gate = CompletableDeferred<Unit>()
        var checks = 0
        val vm = detailViewModel(local = {
            if (++checks > 1) gate.await()
            Result.success(createComic(42, "Comic"))
        }) { identity -> Result.success(JellyfinItemDetails(identity, item)) }
        vm.openJellyfinDetail(item)
        vm.refreshJellyfinLocalCopy(openWhenReady = true)
        vm.refreshJellyfinLocalCopy()
        vm.refreshJellyfinLocalCopy(openWhenReady = true)
        assertEquals(2, checks)
        gate.complete(Unit)
        assertEquals(42L, vm.consumeJellyfinRead())
        assertNull(vm.consumeJellyfinRead())
    }

    @Test
    fun readRevalidatesCopyAndDoesNotNavigateWhenFileWasLost() {
        val item = JellyfinItem("item", "Comic", false)
        var available = true
        val vm = detailViewModel(local = {
            if (available) Result.success(createComic(42, "Comic"))
            else Result.failure(java.io.IOException("Fixture file removed"))
        }) { identity -> Result.success(JellyfinItemDetails(identity, item)) }
        vm.openJellyfinDetail(item)
        assertEquals(42L, vm.jellyfinDetail.value?.localComic?.id)
        available = false
        vm.refreshJellyfinLocalCopy(openWhenReady = true)
        assertNull(vm.consumeJellyfinRead())
        assertNull(vm.jellyfinDetail.value?.localComic)
        assertTrue(vm.jellyfinDetail.value!!.localCopyFailure)
        available = true
        vm.refreshJellyfinLocalCopy()
        assertFalse(vm.jellyfinDetail.value!!.localCopyFailure)
        assertEquals(42L, vm.jellyfinDetail.value?.localComic?.id)
        assertNull(vm.consumeJellyfinRead())
    }

    @Test
    fun changedAccountCannotOpenAnotherAccountsRetainedComic() {
        val item = JellyfinItem("item", "Comic", false)
        var auth = JellyfinAuth("token", "user", "server", "Reader")
        val vm = detailViewModel(auth = { auth },
            local = { Result.success(createComic(42, "Comic")) }
        ) { identity -> Result.success(JellyfinItemDetails(identity, item)) }
        vm.openJellyfinDetail(item)
        auth = auth.copy(userId = "other")
        vm.refreshJellyfinLocalCopy(openWhenReady = true)
        assertNull(vm.consumeJellyfinRead())
        assertNull(vm.jellyfinDetail.value?.localComic)
        assertTrue(vm.jellyfinDetail.value!!.localCopyFailure)
    }

    private fun createComic(
        id: Long,
        title: String,
        series: String? = null,
        volume: String? = null,
        issueNumber: Float? = null,
        pageCount: Int = 20,
        lastPage: Int = 0,
    ) = ComicEntity(
        id = id,
        title = title,
        filePath = "/test/$title.cbz",
        coverPath = null,
        format = "CBZ",
        pageCount = pageCount,
        lastPage = lastPage,
        dateAdded = 1000L * id,
        lastOpened = 1000L * id,
        series = series,
        volume = volume,
        issueNumber = issueNumber,
    )

    @Test
    fun groupingMultipleIssuesIntoSeriesStackInAllFilter() = runBlocking {
        viewModel.setLibraryFilter(LibraryFilter.ALL)
        val c1 = createComic(1, "Ultimate Spider-Man 01", series = "Ultimate Spider-Man", issueNumber = 1f)
        val c2 = createComic(2, "Ultimate Spider-Man 02", series = "Ultimate Spider-Man", issueNumber = 2f)
        fakeComicDao.setComics(listOf(c1, c2))

        val items = withTimeout(3000) {
            viewModel.libraryItems.first { it.isNotEmpty() }
        }

        assertEquals(1, items.size)
        val stack = items[0] as LibraryItem.SeriesStack
        assertEquals("Ultimate Spider-Man", stack.seriesName)
        assertEquals(2, stack.totalIssues)
        assertEquals(1L, stack.issues[0].id)
        assertEquals(2L, stack.issues[1].id)
    }

    @Test
    fun singleIssueRemainsSingleComicInAllFilter() = runBlocking {
        viewModel.setLibraryFilter(LibraryFilter.ALL)
        val c1 = createComic(1, "Batman Year One 01", series = "Batman", issueNumber = 1f)
        val c2 = createComic(2, "Watchmen Complete", series = null)
        fakeComicDao.setComics(listOf(c1, c2))

        val items = withTimeout(3000) {
            viewModel.libraryItems.first { it.isNotEmpty() }
        }

        assertEquals(2, items.size)
        assertTrue(items[0] is LibraryItem.SingleComic)
        assertTrue(items[1] is LibraryItem.SingleComic)
    }

    @Test
    fun defaultLibraryShowsSeriesStacksSorted() = runBlocking {
        val c1 = createComic(1, "X-Men 01", series = "X-Men", issueNumber = 1f)
        val c2 = createComic(2, "Avengers 01", series = "Avengers", issueNumber = 1f)
        val c3 = createComic(3, "Avengers 02", series = "Avengers", issueNumber = 2f)
        fakeComicDao.setComics(listOf(c1, c2, c3))

        val items = withTimeout(3000) {
            viewModel.libraryItems.first { it.size == 2 }
        }

        assertEquals(2, items.size)
        val s1 = items[0] as LibraryItem.SeriesStack
        val s2 = items[1] as LibraryItem.SeriesStack
        assertEquals("Avengers", s1.seriesName)
        assertEquals("X-Men", s2.seriesName)
    }

    @Test
    fun unreadFilterOnlyReturnsComicsWithLastPageZero() = runBlocking {
        viewModel.setLibraryFilter(LibraryFilter.ALL)
        val unread = createComic(1, "Issue 1", series = "Unread standalone", pageCount = 20, lastPage = 0)
        val read = createComic(2, "Issue 2", series = "Read standalone", pageCount = 20, lastPage = 19)
        fakeComicDao.setComics(listOf(unread, read))
        repository.backfillMetadataIfNeeded()

        viewModel.unreadOnly.value = true

        val items = withTimeout(3000) {
            viewModel.libraryItems.first { it.isNotEmpty() }
        }

        assertEquals(1, items.size)
        val single = items[0] as LibraryItem.SingleComic
        assertEquals(1L, single.comic.id)
    }

    @Test
    fun allFilterIncludesListsAlongsideSeriesAndStandaloneComics() = runBlocking {
        viewModel.setLibraryFilter(LibraryFilter.ALL)
        fakeComicDao.setComics(listOf(
            createComic(1, "Saga 01", series = "Saga", issueNumber = 1f),
            createComic(2, "Saga 02", series = "Saga", issueNumber = 2f),
            createComic(3, "Standalone"),
        ))
        fakeReadingListDao.setListsAndItems(
            listOf(ReadingListEntity(10, "Favorites"), ReadingListEntity(11, "To explore")),
            listOf(
                ReadingListItemEntity(10, 3, sortOrder = 1),
                ReadingListItemEntity(10, 1, sortOrder = 0),
            ),
        )

        val items = withTimeout(3000) { viewModel.libraryItems.first { it.size == 4 } }
        assertEquals(1, items.filterIsInstance<LibraryItem.SeriesStack>().size)
        assertEquals(3L, items.filterIsInstance<LibraryItem.SingleComic>().single().comic.id)
        val lists = items.filterIsInstance<LibraryItem.ReadingListItem>()
        assertEquals(listOf(10L, 11L), lists.map { it.readingList.id })
        assertEquals(listOf(1L, 3L), lists[0].previewComics.map { it.id })
        assertEquals(2, lists[0].itemCount)
        assertEquals(0, lists[1].itemCount)
    }

    @Test
    fun blockedBackgroundProjectionPublishesOnlyTheNewestRows() = runBlocking {
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val release = java.util.concurrent.CountDownLatch(1)
        val occupied = java.util.concurrent.CountDownLatch(1)
        executor.submit { occupied.countDown(); release.await() }
        assertTrue(occupied.await(3, java.util.concurrent.TimeUnit.SECONDS))
        val dispatcher = executor.asCoroutineDispatcher()
        val vm = LibraryViewModel(repository, settings, defaultDispatcher = dispatcher)
        vm.setLibraryFilter(LibraryFilter.ALL)
        val store = ViewModelStore().apply { put("background-projection", vm) }
        val history = mutableListOf<List<LibraryItem>>()
        val observer = launch(start = kotlinx.coroutines.CoroutineStart.UNDISPATCHED) {
            vm.libraryItems.collect { history += it }
        }
        try {
            fakeComicDao.setComics(listOf(createComic(1, "Old")))
            fakeComicDao.setComics(listOf(createComic(2, "Newest")))
            assertTrue(vm.libraryItems.value.isEmpty()) // Main stays usable while CPU dispatcher is occupied.
            release.countDown()
            val rows = withTimeout(3000) { vm.libraryItems.first { it.isNotEmpty() } }
            assertEquals(2L, (rows.single() as LibraryItem.SingleComic).comic.id)
            assertTrue(history.flatten().filterIsInstance<LibraryItem.SingleComic>().none { it.comic.id == 1L })
        } finally {
            release.countDown()
            observer.cancel()
            val job = vm.viewModelScope.coroutineContext[Job]
            store.clear()
            job?.join()
            dispatcher.close()
        }
    }

    @Test
    fun allFilterFindsListsByNameEvenWithoutAnyComics() = runBlocking {
        viewModel.setLibraryFilter(LibraryFilter.ALL)
        fakeReadingListDao.setListsAndItems(
            listOf(ReadingListEntity(10, "Favorites"), ReadingListEntity(11, "To explore")),
            emptyList(),
        )
        val all = withTimeout(3000) { viewModel.libraryItems.first { it.size == 2 } }
        assertTrue(all.all { it is LibraryItem.ReadingListItem })

        viewModel.setSearchQuery("  FAVOR  ")
        val matches = withTimeout(3000) {
            viewModel.libraryItems.first { it.size == 1 && it[0] is LibraryItem.ReadingListItem }
        }
        assertEquals(10L, (matches.single() as LibraryItem.ReadingListItem).readingList.id)
        viewModel.setLibraryFilter(LibraryFilter.READING_LISTS)
        assertEquals(matches, withTimeout(3000) { viewModel.libraryItems.first { it == matches } })
    }

    @Test
    fun readingListsFilterReturnsReadingListItems() = runBlocking {
        val c1 = createComic(1, "Comic 1")
        val c2 = createComic(2, "Comic 2")
        fakeComicDao.setComics(listOf(c1, c2))

        val list = ReadingListEntity(id = 10, name = "Favorites", createdAt = 1000L, updatedAt = 1000L)
        val item1 = ReadingListItemEntity(listId = 10, comicId = 1, sortOrder = 0)
        val item2 = ReadingListItemEntity(listId = 10, comicId = 2, sortOrder = 1)
        fakeReadingListDao.setListsAndItems(listOf(list), listOf(item1, item2))

        viewModel.setLibraryFilter(LibraryFilter.READING_LISTS)

        val items = withTimeout(3000) {
            viewModel.libraryItems.first { it.isNotEmpty() }
        }

        assertEquals(1, items.size)
        val listItem = items[0] as LibraryItem.ReadingListItem
        assertEquals("Favorites", listItem.readingList.name)
        assertEquals(2, listItem.itemCount)
        assertEquals(2, listItem.previewComics.size)
    }

    @Test
    fun searchQueryFiltersBothTitleAndSeries() = runBlocking {
        viewModel.setLibraryFilter(LibraryFilter.ALL)
        val c1 = createComic(1, "Astonishing Tales 01", series = "X-Men", issueNumber = 1f)
        val c2 = createComic(2, "Amazing Fantasy 15", series = "Spider-Man", issueNumber = 15f)
        fakeComicDao.setComics(listOf(c1, c2))

        viewModel.setSearchQuery("tales")
        var items = withTimeout(3000) {
            viewModel.libraryItems.first { it.isNotEmpty() }
        }
        assertEquals(1, items.size)
        assertEquals(1L, (items[0] as LibraryItem.SingleComic).comic.id)

        viewModel.setSearchQuery("spider-man")
        items = withTimeout(3000) {
            viewModel.libraryItems.first { it.isNotEmpty() }
        }
        assertEquals(1, items.size)
        assertEquals(2L, (items[0] as LibraryItem.SingleComic).comic.id)
    }

    @Test
    fun topCoverComicResolvesToFirstUnreadOrInProgress() = runBlocking {
        val c1 = createComic(1, "Issue 01", series = "Saga", issueNumber = 1f, pageCount = 20, lastPage = 19) // Completed
        val c2 = createComic(2, "Issue 02", series = "Saga", issueNumber = 2f, pageCount = 20, lastPage = 5)  // In-progress
        val c3 = createComic(3, "Issue 03", series = "Saga", issueNumber = 3f, pageCount = 20, lastPage = 0)  // Unread
        fakeComicDao.setComics(listOf(c1, c2, c3))

        val items = withTimeout(3000) {
            viewModel.libraryItems.first { it.isNotEmpty() }
        }

        val stack = items[0] as LibraryItem.SeriesStack
        assertEquals(2L, stack.topCoverComic.id)
    }

    @Test
    fun topCoverComicFallsBackToFirstIssueWhenAllCompleted() = runBlocking {
        val c1 = createComic(1, "Issue 01", series = "Saga", issueNumber = 1f, pageCount = 20, lastPage = 19)
        val c2 = createComic(2, "Issue 02", series = "Saga", issueNumber = 2f, pageCount = 20, lastPage = 19)
        fakeComicDao.setComics(listOf(c1, c2))

        val items = withTimeout(3000) {
            viewModel.libraryItems.first { it.isNotEmpty() }
        }

        val stack = items[0] as LibraryItem.SeriesStack
        assertEquals(1L, stack.topCoverComic.id)
        assertEquals(1.0f, stack.progressFraction, 0.001f)
    }

    @Test
    fun seriesReadScopeIncludesSearchHiddenComicsButRespectsVolume() = runBlocking {
        fakeComicDao.setComics(listOf(
            createComic(1, "Only visible title", series = "Invincible", volume = "1", pageCount = 25),
            createComic(2, "Hidden title", series = "Invincible", volume = "1", pageCount = 25),
            createComic(3, "Other volume", series = "Invincible", volume = "2", pageCount = 25),
            createComic(4, "Other series", series = "Saga", volume = "1", pageCount = 25),
        ))
        viewModel.setSearchQuery("Only visible")
        assertEquals(listOf(1L), withTimeout(3000) { viewModel.comics.first { it.isNotEmpty() } }.map { it.id })
        val affected = viewModel.seriesReadComics.first().filter {
            it.series.equals("Invincible", ignoreCase = true) && it.volume.equals("1", ignoreCase = true)
        }
        assertEquals(listOf(1L, 2L), affected.map { it.id })
        viewModel.markSeriesRead("Invincible", "1", true)
        withTimeout(3000) {
            while (fakeComicDao.getAll().filter { it.id in listOf(1L, 2L) }.any { it.lastPage != 24 }) {
                kotlinx.coroutines.delay(10)
            }
        }
        assertEquals(listOf(24, 24, 0, 0), fakeComicDao.getAll().sortedBy { it.id }.map { it.lastPage })
    }

    @Test
    fun markSeriesReadAndUnreadOperations() = runBlocking {
        val c1 = createComic(1, "Invincible 01", series = "Invincible", issueNumber = 1f, pageCount = 25, lastPage = 0)
        val c2 = createComic(2, "Invincible 02", series = "Invincible", issueNumber = 2f, pageCount = 25, lastPage = 0)
        fakeComicDao.setComics(listOf(c1, c2))

        viewModel.markSeriesRead("Invincible", null, true)
        withTimeout(3000) {
            while (!fakeComicDao.getAll().all { it.lastPage == 24 }) {
                kotlinx.coroutines.delay(10)
            }
        }
        var all = fakeComicDao.getAll()
        assertTrue(all.all { it.lastPage == 24 })

        viewModel.markSeriesRead("Invincible", null, false)
        withTimeout(3000) {
            while (!fakeComicDao.getAll().all { it.lastPage == 0 }) {
                kotlinx.coroutines.delay(10)
            }
        }
        all = fakeComicDao.getAll()
        assertTrue(all.all { it.lastPage == 0 })
    }

    @Test
    fun lateMetadataBackfillPreservesAnExplicitSeriesEdit() = runBlocking {
        val captured = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val dao = FakeComicDao().apply {
            setComics(listOf(createComic(7, "Old series 01", series = null)))
            missingRead = captured
            missingRelease = release
        }
        val localRepository = LibraryRepository(RuntimeEnvironment.getApplication(), dao, settings)
        val backfill = launch(Dispatchers.IO) {
            localRepository.backfillMetadataIfNeeded()
        }
        withTimeout(3000) { captured.await() }
        localRepository.updateComicMetadata(7, "Owner-selected run", "Vol 2", 3f)
        release.complete(Unit)
        withTimeout(3000) { backfill.join() }
        assertEquals("Owner-selected run", dao.getById(7)?.series)
        assertEquals("Vol 2", dao.getById(7)?.volume)
        assertEquals(3f, dao.getById(7)?.issueNumber)
    }

    @Test
    fun updateComicsSeriesUpdatesMetadata() = runBlocking {
        val c1 = createComic(1, "Comic 01", series = null)
        val c2 = createComic(2, "Comic 02", series = null)
        fakeComicDao.setComics(listOf(c1, c2))

        viewModel.updateComicsSeries(listOf(1L, 2L), "New Mutants", "Vol 1")
        withTimeout(3000) {
            while (fakeComicDao.getAll().any { it.series != "New Mutants" || it.volume != "Vol 1" }) {
                kotlinx.coroutines.delay(10)
            }
        }
        val all = fakeComicDao.getAll()
        assertEquals("New Mutants", all.find { it.id == 1L }?.series)
        assertEquals("Vol 1", all.find { it.id == 1L }?.volume)
        assertEquals("New Mutants", all.find { it.id == 2L }?.series)
        assertEquals("Vol 1", all.find { it.id == 2L }?.volume)
    }

    @Test
    fun readingListCreationAndItemInsertion() = runBlocking {
        val c1 = createComic(1, "Infinity Gauntlet 01")
        val c2 = createComic(2, "Silver Surfer 34")
        fakeComicDao.setComics(listOf(c1, c2))

        var createdId = 0L
        viewModel.createReadingList("Cosmic Marvel") { id ->
            createdId = id
        }

        withTimeout(3000) {
            while (fakeReadingListDao.getAllLists().isEmpty()) {
                kotlinx.coroutines.delay(10)
            }
        }
        val list = fakeReadingListDao.getAllLists().first()
        assertEquals("Cosmic Marvel", list.name)

        viewModel.addComicsToReadingList(list.id, listOf(1L, 2L))
        withTimeout(3000) {
            while (fakeReadingListDao.getAllItems().size < 2) {
                kotlinx.coroutines.delay(10)
            }
        }
        val items = fakeReadingListDao.getAllItems().filter { it.listId == list.id }
        assertEquals(2, items.size)
    }

    @Test
    fun readingListReorderingUpdatesSortOrder() = runBlocking {
        val c1 = createComic(1, "Issue 1")
        val c2 = createComic(2, "Issue 2")
        val c3 = createComic(3, "Issue 3")
        fakeComicDao.setComics(listOf(c1, c2, c3))

        val list = ReadingListEntity(1L, "Storyline")
        fakeReadingListDao.setListsAndItems(
            listOf(list),
            listOf(
                ReadingListItemEntity(1L, 1L, sortOrder = 0),
                ReadingListItemEntity(1L, 2L, sortOrder = 1),
                ReadingListItemEntity(1L, 3L, sortOrder = 2),
            )
        )

        viewModel.moveReadingListItem(1L, 3L, -1)
        withTimeout(3000) {
            while (fakeReadingListDao.getAllItems().find { it.comicId == 3L }?.sortOrder != 1) {
                kotlinx.coroutines.delay(10)
            }
        }
        val reordered = fakeReadingListDao.getAllItems().filter { it.listId == 1L }.sortedBy { it.sortOrder }
        assertEquals(listOf(1L, 3L, 2L), reordered.map { it.comicId })

        viewModel.reorderReadingList(1L, listOf(2L, 1L, 3L))
        withTimeout(3000) {
            while (fakeReadingListDao.getAllItems().find { it.comicId == 2L }?.sortOrder != 0) {
                kotlinx.coroutines.delay(10)
            }
        }
        val finalOrder = fakeReadingListDao.getAllItems().filter { it.listId == 1L }.sortedBy { it.sortOrder }
        assertEquals(listOf(2L, 1L, 3L), finalOrder.map { it.comicId })
    }

    @Test
    fun readingListMarkAllReadAndUnread() = runBlocking {
        val c1 = createComic(1, "Event 01", pageCount = 30, lastPage = 0)
        val c2 = createComic(2, "Event 02", pageCount = 20, lastPage = 0)
        fakeComicDao.setComics(listOf(c1, c2))

        val list = ReadingListEntity(1L, "Crossover")
        fakeReadingListDao.setListsAndItems(
            listOf(list),
            listOf(
                ReadingListItemEntity(1L, 1L, sortOrder = 0),
                ReadingListItemEntity(1L, 2L, sortOrder = 1),
            )
        )

        viewModel.markReadingListRead(1L, true)
        withTimeout(3000) {
            while (!fakeComicDao.getAll().all { it.lastPage > 0 && it.completionOverride == true }) {
                kotlinx.coroutines.delay(10)
            }
        }
        var all = fakeComicDao.getAll()
        assertEquals(29, all.find { it.id == 1L }?.lastPage)
        assertEquals(19, all.find { it.id == 2L }?.lastPage)

        viewModel.markReadingListRead(1L, false)
        withTimeout(3000) {
            while (!fakeComicDao.getAll().all { it.lastPage == 0 && it.completionOverride == false }) {
                kotlinx.coroutines.delay(10)
            }
        }
        all = fakeComicDao.getAll()
        assertEquals(0, all.find { it.id == 1L }?.lastPage)
        assertEquals(0, all.find { it.id == 2L }?.lastPage)
    }

    @Test
    fun readingListRenameAndDelete() = runBlocking {
        val list = ReadingListEntity(1L, "Old Title")
        fakeReadingListDao.setListsAndItems(
            listOf(list),
            listOf(ReadingListItemEntity(1L, 10L, 0))
        )

        viewModel.renameReadingList(1L, "New Title")
        withTimeout(3000) {
            while (fakeReadingListDao.getById(1L)?.name != "New Title") {
                kotlinx.coroutines.delay(10)
            }
        }
        assertEquals("New Title", fakeReadingListDao.getById(1L)?.name)

        viewModel.deleteReadingList(1L)
        withTimeout(3000) {
            while (fakeReadingListDao.getAllLists().isNotEmpty()) {
                kotlinx.coroutines.delay(10)
            }
        }
        assertTrue(fakeReadingListDao.getAllLists().isEmpty())
        assertTrue(fakeReadingListDao.getAllItems().isEmpty())
    }

    @Test
    fun smbLateListingCannotReplaceTheNewerFolder() {
        val pending = mutableMapOf<String, kotlin.coroutines.Continuation<Result<List<net.digitalbc.leafreader.data.smb.SmbEntry>>>>()
        val vm = LibraryViewModel(repository, settings, defaultDispatcher = testDispatcher) { path ->
            kotlin.coroutines.suspendCoroutine { pending[path] = it }
        }.also { stores += ViewModelStore().apply { put("smb", it) } }
        vm.loadSmbDirectory("first")
        vm.loadSmbDirectory("second")
        val entry = net.digitalbc.leafreader.data.smb.SmbEntry("book.cbz", "second/book.cbz", false)
        pending.getValue("second").resumeWith(Result.success(Result.success(listOf(entry))))
        pending.getValue("first").resumeWith(Result.success(Result.success(emptyList())))
        assertEquals("second", vm.smbBrowser.value.path)
        assertEquals(listOf(entry), vm.smbBrowser.value.entries)
        assertTrue(!vm.smbBrowser.value.loading)
    }

    @Test
    fun smbFailedNavigationRetainsTheUsableFolderAndSelection() {
        val entry = net.digitalbc.leafreader.data.smb.SmbEntry("book.cbz", "book.cbz", false)
        val vm = LibraryViewModel(repository, settings, defaultDispatcher = testDispatcher) { path ->
            if (path.isEmpty()) Result.success(listOf(entry))
            else Result.failure(java.io.IOException("private server details"))
        }.also { stores += ViewModelStore().apply { put("smb", it) } }
        vm.loadSmbDirectory()
        vm.toggleSmbSelection("book.cbz")
        vm.loadSmbDirectory("denied")
        assertEquals("", vm.smbBrowser.value.path)
        assertEquals(listOf(entry), vm.smbBrowser.value.entries)
        assertEquals(setOf("book.cbz"), vm.smbBrowser.value.selectedPaths)
        assertTrue(vm.message.value?.contains("private") != true)
        vm.cancelSmbBrowse()
        assertTrue(!vm.smbBrowser.value.loading)
    }

    @Test
    fun changingSmbServerClearsThePreviousServersListingEvenWhenTheNewRequestFails() {
        val entry = net.digitalbc.leafreader.data.smb.SmbEntry("book.cbz", "book.cbz", false)
        settings.smbHost = "server-a"
        val vm = LibraryViewModel(repository, settings, defaultDispatcher = testDispatcher) {
            if (settings.smbHost == "server-a") Result.success(listOf(entry))
            else Result.failure(java.io.IOException())
        }.also { stores += ViewModelStore().apply { put("smb", it) } }
        vm.loadSmbDirectory()
        vm.toggleSmbSelection("book.cbz")
        settings.smbHost = "server-b"
        vm.loadSmbDirectory()
        assertTrue(vm.smbBrowser.value.entries.isEmpty())
        assertTrue(vm.smbBrowser.value.selectedPaths.isEmpty())
    }

    private class FakeComicDao : ComicDao {
        private val comicsState = MutableStateFlow<List<ComicEntity>>(emptyList())

        fun getAllDirect(): List<ComicEntity> = comicsState.value

        fun setComics(list: List<ComicEntity>) {
            comicsState.value = list
        }

        override fun observeAll(): Flow<List<ComicEntity>> = comicsState

        override suspend fun getAll(): List<ComicEntity> = comicsState.value

        override suspend fun getById(id: Long): ComicEntity? = comicsState.value.find { it.id == id }

        override suspend fun insert(comic: ComicEntity): Long {
            comicsState.update { it + comic }
            return comic.id
        }

        override suspend fun updateProgress(id: Long, page: Int, slot: Int, openedAt: Long): Int {
            comicsState.update { comics ->
                comics.map {
                    if (it.id == id) it.copy(lastPage = page, lastSlot = slot, lastOpened = openedAt) else it
                }
            }
            return 1
        }

        override suspend fun updateLocalFiles(id: Long, path: String, cover: String?, format: String, pageCount: Int): Int {
            if (getById(id) == null) return 0
            comicsState.update { comics ->
                comics.map { if (it.id == id) it.copy(filePath = path, coverPath = cover, format = format, pageCount = pageCount) else it }
            }
            return 1
        }

        override suspend fun updateCompletion(id: Long, completed: Boolean?): Int {
            comicsState.update { comics ->
                comics.map {
                    if (it.id == id) it.copy(completionOverride = completed) else it
                }
            }
            return 1
        }

        override suspend fun updatePageCount(id: Long, pageCount: Int): Int {
            comicsState.update { comics ->
                comics.map {
                    if (it.id == id) it.copy(pageCount = pageCount) else it
                }
            }
            return 1
        }

        override suspend fun updateReadingDirection(id: Long, rtl: Boolean): Int {
            comicsState.update { comics ->
                comics.map {
                    if (it.id == id) it.copy(rightToLeft = rtl) else it
                }
            }
            return 1
        }

        override suspend fun updateMetadata(id: Long, series: String?, volume: String?, issueNumber: Float?) {
            comicsState.update { comics ->
                comics.map {
                    if (it.id == id) it.copy(series = series, volume = volume, issueNumber = issueNumber) else it
                }
            }
        }

        var missingRead: CompletableDeferred<Unit>? = null
        var missingRelease: CompletableDeferred<Unit>? = null
        override suspend fun getComicsWithoutSeries(): List<ComicEntity> {
            val missing = comicsState.value.filter { it.series == null }
            missingRead?.complete(Unit)
            missingRelease?.await()
            return missing
        }

        override fun observeBySeries(series: String, volume: String?): Flow<List<ComicEntity>> =
            comicsState.map { list ->
                list.filter {
                    it.series.equals(series, ignoreCase = true) &&
                    (volume.isNullOrBlank() || it.volume.equals(volume, ignoreCase = true))
                }
            }

        override suspend fun delete(id: Long): Int {
            comicsState.update { comics -> comics.filter { it.id != id } }
            return 1
        }
    }

    private class FakeReadingListDao : ReadingListDao {
        private val listsState = MutableStateFlow<List<ReadingListEntity>>(emptyList())
        private val itemsState = MutableStateFlow<List<ReadingListItemEntity>>(emptyList())
        var comicsLookup: () -> List<ComicEntity> = { emptyList() }

        fun setListsAndItems(lists: List<ReadingListEntity>, items: List<ReadingListItemEntity>) {
            listsState.value = lists
            itemsState.value = items
        }

        override fun observeById(id: Long): Flow<ReadingListEntity?> =
            listsState.map { lists -> lists.find { it.id == id } }

        override fun observeAll(): Flow<List<ReadingListEntity>> = listsState

        override suspend fun getAllLists(): List<ReadingListEntity> = listsState.value

        override fun observeAllItems(): Flow<List<ReadingListItemEntity>> = itemsState

        override suspend fun getAllItems(): List<ReadingListItemEntity> = itemsState.value

        override suspend fun getById(id: Long): ReadingListEntity? =
            listsState.value.find { it.id == id }

        override suspend fun insertList(list: ReadingListEntity): Long {
            val id = if (list.id == 0L) (listsState.value.maxOfOrNull { it.id } ?: 0L) + 1 else list.id
            listsState.value = listsState.value + list.copy(id = id)
            return id
        }

        override suspend fun updateName(id: Long, name: String, updatedAt: Long): Int {
            listsState.value = listsState.value.map {
                if (it.id == id) it.copy(name = name, updatedAt = updatedAt) else it
            }
            return 1
        }

        override suspend fun deleteList(id: Long): Int {
            listsState.value = listsState.value.filter { it.id != id }
            return 1
        }

        override fun observeComicsForList(listId: Long): Flow<List<ComicEntity>> =
            itemsState.map { items ->
                val comicMap = comicsLookup().associateBy { it.id }
                items.filter { it.listId == listId }
                    .sortedBy { it.sortOrder }
                    .mapNotNull { comicMap[it.comicId] }
            }

        override fun observeItemCount(listId: Long): Flow<Int> =
            itemsState.map { items -> items.count { it.listId == listId } }

        override suspend fun insertItem(item: ReadingListItemEntity) {
            itemsState.value = itemsState.value.filterNot { it.listId == item.listId && it.comicId == item.comicId } + item
        }

        override suspend fun updateItemOrder(listId: Long, comicId: Long, sortOrder: Int): Int {
            itemsState.value = itemsState.value.map {
                if (it.listId == listId && it.comicId == comicId) it.copy(sortOrder = sortOrder) else it
            }
            return 1
        }

        override suspend fun removeItem(listId: Long, comicId: Long): Int {
            itemsState.value = itemsState.value.filterNot { it.listId == listId && it.comicId == comicId }
            return 1
        }

        override suspend fun clearItems(listId: Long): Int {
            itemsState.value = itemsState.value.filterNot { it.listId == listId }
            return 1
        }
    }
}
