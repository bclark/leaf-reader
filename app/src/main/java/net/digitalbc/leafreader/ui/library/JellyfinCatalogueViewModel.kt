package net.digitalbc.leafreader.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import net.digitalbc.leafreader.ComicReaderApp
import net.digitalbc.leafreader.data.db.ComicEntity
import net.digitalbc.leafreader.data.jellyfin.*
import net.digitalbc.leafreader.data.library.LibraryRepository
import net.digitalbc.leafreader.data.settings.AppSettings

data class CatalogueState(
    val libraries: List<JellyfinView> = emptyList(),
    val library: JellyfinView? = null,
    val view: JellyfinCatalogueView = JellyfinCatalogueView.ALL,
    val collection: JellyfinItem? = null,
    val search: String = "",
    val unreadOnly: Boolean = false,
    val entries: List<JellyfinCatalogueEntry> = emptyList(),
    val nextIndex: Int = 0,
    val total: Int = 0,
    val hasMore: Boolean = false,
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val error: String? = null,
    val offline: Boolean = false,
    val accountScope: String = "",
    val launchFailure: JellyfinFailureKind? = null,
    val browsingSaved: Boolean = false,
    val indexing: Boolean = false,
    val indexComplete: Boolean = false,
    val indexedRecords: Int = 0,
    val indexTotal: Int = 0,
    val updatedAt: Long = 0,
    val indexError: String? = null,
    val partialSearch: Boolean = false,
    val refreshing: Boolean = false,
    val creator: JellyfinPerson? = null,
    val seriesGroups: List<JellyfinSeriesGroup> = emptyList(),
    val downloadedSeriesGroups: List<JellyfinSeriesGroup> = emptyList(),
    internal val indexedEntries: List<JellyfinCatalogueEntry> = emptyList(),
) {
    val downloadedFallback get() = launchFailure != null && !browsingSaved
    val usesIndex get() = creator == null && collection == null && search.isBlank() &&
        view in listOf(JellyfinCatalogueView.ALL, JellyfinCatalogueView.SERIES, JellyfinCatalogueView.UNREAD)
    val canBrowseSaved get() = loaded && launchFailure !in listOf(
        JellyfinFailureKind.AUTHENTICATION, JellyfinFailureKind.ACCESS_DENIED)
}

@OptIn(ExperimentalCoroutinesApi::class)
class JellyfinCatalogueViewModel(
    private val repository: LibraryRepository,
    private val settings: AppSettings,
    private val views: suspend () -> Result<List<JellyfinView>> = repository::listJellyfinViewsForLaunch,
    private val fetch: suspend (JellyfinCatalogueQuery) -> Result<JellyfinCataloguePage> = repository::listJellyfinCatalogue,
    private val cached: suspend (JellyfinCatalogueQuery) -> JellyfinCataloguePage? = repository::cachedJellyfinCatalogue,
    private val restore: suspend () -> JellyfinSavedCatalogue? = repository::restoreJellyfinCatalogue,
    private val readIndex: suspend (String) -> JellyfinCatalogueIndex? = repository::readJellyfinIndex,
    private val saveIndex: suspend (String, JellyfinCatalogueIndex, List<String>) -> Unit = repository::writeJellyfinIndex,
    private val resolveFolder: suspend (String, String) -> JellyfinFolderIdentity? = repository::resolveJellyfinFolder,
    private val elapsedNanos: () -> Long = System::nanoTime,
    private val creator: JellyfinPerson? = null,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {
    private val _state = MutableStateFlow(CatalogueState(creator = creator))
    private val projector = CatalogueProjector()
    private val downloadedIds = _state.map { it.accountScope }.distinctUntilChanged().flatMapLatest {
        repository.observeDownloadedJellyfinCopies().map { it.keys }
            .onStart { emit(emptySet()) }
    }.flowOn(ioDispatcher)
    val state: StateFlow<CatalogueState> = combine(_state, downloadedIds) { raw, ids -> raw to ids }
        .mapLatest { (raw, ids) -> withContext(defaultDispatcher) { projector.prepare(raw, ids) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CatalogueState(creator = creator))
    private var scope: List<String>? = null
    private var job: Job? = null
    private var indexJob: Job? = null
    private var generation = 0L
    private var indexGeneration = 0L
    private var index: JellyfinCatalogueIndex? = null
    private var lastCheck = 0L
    private data class PageKey(val view: JellyfinCatalogueView, val collection: String?, val search: String, val unread: Boolean)
    private val pages = mutableMapOf<PageKey, CatalogueState>()

    val downloads: StateFlow<List<ComicEntity>> = repository.readableComics

    private fun currentScope() = listOf(settings.jellyfinServerUrl, settings.jellyfinServerId, settings.jellyfinUserId)
    val positionScope: String get() = currentScope().joinToString(":") { "${it.length}:$it" }
    private fun key(s: CatalogueState) = PageKey(s.view, s.collection?.id, s.search, s.unreadOnly)
    private fun denied(kind: JellyfinFailureKind?) =
        kind == JellyfinFailureKind.AUTHENTICATION || kind == JellyfinFailureKind.ACCESS_DENIED

    private suspend fun <T> launchRequest(request: suspend () -> Result<T>): Result<T> =
        withTimeoutOrNull(LAUNCH_TIMEOUT_MILLIS) { request() }
            ?: Result.failure(JellyfinException(JellyfinFailureKind.TIMEOUT, "Jellyfin launch timed out"))

    private fun failureKind(error: Throwable): JellyfinFailureKind = when (error) {
        is JellyfinException -> error.kind
        is java.net.SocketTimeoutException -> JellyfinFailureKind.TIMEOUT
        is java.io.IOException -> JellyfinFailureKind.UNAVAILABLE
        else -> JellyfinFailureKind.UNKNOWN
    }

    // Main only attaches immutable snapshots and scalar coverage; projection runs on Default.
    private fun project(base: CatalogueState): CatalogueState {
        val saved = index
        val coverage = base.copy(indexComplete = saved?.complete == true,
            indexedRecords = saved?.scanned ?: 0, indexTotal = saved?.total ?: 0,
            updatedAt = saved?.updatedAt ?: 0, indexedEntries = saved?.entries.orEmpty())
        if (denied(coverage.launchFailure)) return coverage.copy(entries = emptyList())
        return if (coverage.usesIndex) coverage.copy(entries = saved?.entries.orEmpty(),
            loaded = saved != null, hasMore = false, nextIndex = saved?.scanned ?: 0, total = saved?.total ?: 0)
        else coverage
    }

    fun ensureRetry() = ensure(force = true)

    fun ensure(force: Boolean = false) {
        if (!force && scope == currentScope() &&
            (_state.value.loaded || _state.value.loading || _state.value.launchFailure != null)) return
        val sameScope = scope == currentScope()
        job?.cancel(); indexJob?.cancel(); ++indexGeneration
        if (!sameScope) { index = null; pages.clear() }
        scope = currentScope()
        val captured = scope!!
        val request = ++generation
        lastCheck = elapsedNanos()
        val before = if (sameScope) _state.value else CatalogueState(accountScope = positionScope, creator = creator)
        _state.value = before.copy(loading = true, indexing = false, accountScope = positionScope)
        job = viewModelScope.launch {
            if (index == null) {
                val saved = restore()
                if (!valid(request, captured)) return@launch
                if (saved != null) {
                    index = saved.index
                    val books = JellyfinLibraryEntry.comicLibraries(saved.libraries).ifEmpty { saved.libraries }
                    _state.value = project(_state.value.copy(libraries = books, library = saved.library,
                        offline = true, loaded = saved.index != null))
                }
            }
            val result = launchRequest(views)
            if (!valid(request, captured)) return@launch
            result.onSuccess { libraries ->
                val books = JellyfinLibraryEntry.comicLibraries(libraries).ifEmpty { libraries }
                val selected = books.firstOrNull { it.id == _state.value.library?.id }
                    ?: JellyfinLibraryEntry.automaticLibrary(libraries)
                _state.value = project(_state.value.copy(libraries = books, library = selected,
                    loading = false, error = null, loaded = true))
                if (selected != null) {
                    if (index == null) index = readIndex(selected.id)
                    if (!valid(request, captured)) return@launch
                    _state.value = project(_state.value)
                    startIndex(startup = true)
                    if (!_state.value.usesIndex && _state.value.view != JellyfinCatalogueView.DOWNLOADED) load()
                } else _state.value = _state.value.copy(launchFailure = null, offline = false, loaded = true)
            }.onFailure { error -> connectionFailed(error, startup = true) }
        }
    }

    private fun valid(request: Long, captured: List<String>) =
        request == generation && captured == currentScope()

    private fun connectionFailed(error: Throwable, startup: Boolean) {
        val kind = failureKind(error)
        val before = _state.value
        _state.value = before.copy(loading = false, indexing = false, refreshing = false, offline = true,
            launchFailure = if (startup || denied(kind)) kind else before.launchFailure,
            browsingSaved = before.browsingSaved && !denied(kind),
            entries = if (denied(kind)) emptyList() else before.entries,
            indexError = "Could not refresh the catalogue. Saved metadata and downloads are preserved.",
            error = if (before.usesIndex) null else "Could not load this view. Saved metadata is preserved.")
        if (before.creator != null && !denied(kind)) load()
    }

    fun chooseLibrary(library: JellyfinView) {
        job?.cancel(); indexJob?.cancel(); ++generation; ++indexGeneration; pages.clear(); index = null
        val captured = currentScope()
        val request = generation
        _state.value = CatalogueState(libraries = _state.value.libraries, library = library,
            accountScope = positionScope, loading = true, creator = creator)
        job = viewModelScope.launch {
            index = readIndex(library.id)
            if (!valid(request, captured)) return@launch
            _state.value = project(_state.value.copy(loading = false))
            startIndex(startup = true)
            if (!_state.value.usesIndex && _state.value.view != JellyfinCatalogueView.DOWNLOADED) load()
        }
    }

    /** One metadata pass; every page is persisted, but removals require a complete fresh pass. */
    private fun startIndex(startup: Boolean = false, manual: Boolean = false) {
        val library = _state.value.library ?: return
        if (_state.value.indexing) return
        indexJob?.cancel()
        val request = ++indexGeneration
        val captured = currentScope()
        val old = index
        val wasOffline = _state.value.offline
        val update = JellyfinIndexUpdate(old)
        lastCheck = elapsedNanos()
        _state.value = _state.value.copy(indexing = true, refreshing = manual, indexError = null,
            loading = if (_state.value.usesIndex) old == null else _state.value.loading, error = null)
        indexJob = viewModelScope.launch {
            val folders = mutableMapOf<String, JellyfinFolderIdentity?>()
            var next = 0
            var first = true
            try {
                do {
                    val query = JellyfinCatalogueQuery(library.id, startIndex = next)
                    val page = (if (first && startup) launchRequest { fetch(query) } else fetch(query)).getOrThrow()
                    if (!isActive || request != indexGeneration || captured != currentScope()) return@launch
                    if (page.fromCache) {
                        if (index == null) index = JellyfinCatalogueIndex(page.entries, false, page.nextIndex,
                            page.total, 0, 0)
                        _state.value = project(_state.value)
                        throw JellyfinException(JellyfinFailureKind.UNAVAILABLE, "Only saved metadata is available")
                    }
                    // Publish lightweight covers immediately; folder validation follows on this IO request path.
                    val previous = index
                    val raw = withContext(defaultDispatcher) {
                        val known = previous?.entries.orEmpty().associateBy { it.item.id }
                        (old?.entries.orEmpty().associateBy { it.item.id } + known +
                            page.entries.map { entry ->
                                val folder = known[entry.item.id]?.folder?.takeIf {
                                    it.id == entry.item.parentId && it.libraryId == library.id
                                }
                                entry.copy(folder = entry.folder ?: folder)
                            }.associateBy { it.item.id }).values.toList()
                    }
                    if (!isActive || request != indexGeneration || captured != currentScope()) return@launch
                    index = JellyfinCatalogueIndex(raw, false, page.nextIndex, page.total,
                        System.currentTimeMillis(), old?.lastCompletedAt ?: 0)
                    _state.value = project(_state.value.copy(loading = if (_state.value.usesIndex) false else _state.value.loading,
                        loaded = true, offline = false, launchFailure = null, browsingSaved = false))
                    val enriched = page.entries.map { entry ->
                        val parent = entry.item.parentId?.takeIf { it != library.id }
                        if (parent == null || !entry.seriesId.isNullOrBlank())
                            entry
                        else {
                            if (!folders.containsKey(parent)) folders[parent] = resolveFolder(library.id, parent)
                            entry.copy(folder = folders[parent])
                        }
                    }
                    if (!isActive || request != indexGeneration || captured != currentScope()) return@launch
                    val snapshot = withContext(defaultDispatcher) {
                        update.append(page.copy(entries = enriched), System.currentTimeMillis())
                    }
                    if (!isActive || request != indexGeneration || captured != currentScope()) return@launch
                    saveIndex(library.id, snapshot, captured)
                    if (!isActive || request != indexGeneration || captured != currentScope()) return@launch
                    index = snapshot
                    _state.value = project(_state.value.copy(loading = if (_state.value.usesIndex) false else _state.value.loading,
                        loaded = true, offline = false, launchFailure = null, browsingSaved = false))
                    next = page.nextIndex
                    first = false
                } while (page.hasMore)
                _state.value = project(_state.value.copy(indexing = false, refreshing = false, indexError = null))
                if (wasOffline && !_state.value.usesIndex &&
                    _state.value.view != JellyfinCatalogueView.DOWNLOADED) load()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (request == indexGeneration && captured == currentScope())
                    connectionFailed(error, startup && first)
            }
        }
    }

    fun refresh(quiet: Boolean = false) {
        if (scope != currentScope() || _state.value.library == null) { ensureRetry(); return }
        if (_state.value.indexing) return
        startIndex(startup = _state.value.launchFailure != null, manual = !quiet)
        if (!_state.value.usesIndex && _state.value.view != JellyfinCatalogueView.DOWNLOADED) load()
    }

    /** Both refresh and page failures have a real network retry, even while browsing saved metadata. */
    fun retryView() {
        val current = _state.value
        if (current.library == null || current.offline || current.launchFailure != null || current.indexError != null)
            refresh()
        else load(more = current.hasMore, retry = true)
    }

    fun retryConnection() {
        if (_state.value.indexing || _state.value.loading) return
        refresh()
    }

    /** Foreground check, throttled on transitions; no timer, service or server scan. */
    fun onForeground() {
        if (scope != currentScope()) ensure()
        else if (elapsedNanos() - lastCheck >= FOREGROUND_CHECK_NANOS) refresh(quiet = true)
    }

    fun browseSavedCatalogue() {
        if (_state.value.canBrowseSaved && index != null)
            _state.value = project(_state.value.copy(browsingSaved = true))
    }

    private fun restorePage(base: CatalogueState): CatalogueState =
        (pages[key(base)] ?: base).copy(launchFailure = base.launchFailure,
            offline = base.offline, browsingSaved = base.browsingSaved, indexing = base.indexing,
            refreshing = base.refreshing, indexError = base.indexError, accountScope = base.accountScope,
            libraries = base.libraries, library = base.library)

    /** Apply the independent filter and scope together so only the final query is requested. */
    fun browse(view: JellyfinCatalogueView, search: String, unreadOnly: Boolean) {
        val before = _state.value
        val query = search.trim()
        if (before.view == view && before.search == query && before.unreadOnly == unreadOnly) return
        pages[key(before)] = before.copy(loading = false)
        job?.cancel()
        ++generation
        val base = before.copy(view = view, collection = if (view == before.view) before.collection else null,
            search = query, unreadOnly = unreadOnly, entries = emptyList(), nextIndex = 0,
            total = 0, hasMore = false, loaded = false, loading = false, error = null)
        _state.value = project(restorePage(base))
        if (!_state.value.usesIndex && view != JellyfinCatalogueView.DOWNLOADED && !_state.value.loaded) load()
    }

    fun select(view: JellyfinCatalogueView) {
        if (view == _state.value.view && _state.value.collection == null) return
        pages[key(_state.value)] = _state.value.copy(loading = false)
        job?.cancel(); ++generation
        val base = _state.value.copy(view = view, collection = null, search = "", entries = emptyList(),
            nextIndex = 0, total = 0, hasMore = false, loading = false, error = null)
        _state.value = project(restorePage(base))
        if (!_state.value.usesIndex && view != JellyfinCatalogueView.DOWNLOADED) load()
    }

    fun search(query: String) {
        if (query.trim() == _state.value.search) return
        job?.cancel(); ++generation
        _state.value = _state.value.copy(search = query.trim(), entries = emptyList(),
            nextIndex = 0, total = 0, hasMore = false, loading = false, error = null)
        if (_state.value.usesIndex) _state.value = project(_state.value) else load()
    }

    fun openCollection(item: JellyfinItem) {
        pages[key(_state.value)] = _state.value
        job?.cancel(); ++generation
        _state.value = _state.value.copy(collection = item, search = "", entries = emptyList(),
            nextIndex = 0, total = 0, loaded = false, hasMore = false, loading = false, error = null)
        load()
    }

    fun closeCollection() {
        job?.cancel(); ++generation
        val base = _state.value.copy(collection = null, search = "", loading = false, error = null)
        _state.value = project(restorePage(base))
        if (!_state.value.usesIndex && !_state.value.loaded) load()
    }

    fun load(more: Boolean = false, startup: Boolean = false, retry: Boolean = false) {
        val before = _state.value
        val library = before.library ?: return
        if (before.usesIndex) { if (!before.indexing) startIndex(startup); return }
        if (denied(before.launchFailure) || before.loading || (more && (!before.hasMore || (before.error != null && !retry))) ||
            before.view == JellyfinCatalogueView.DOWNLOADED) return
        job?.cancel()
        val request = ++generation
        val captured = currentScope()
        _state.value = before.copy(loading = true, error = null)
        job = viewModelScope.launch {
            if (before.offline && before.collection == null && before.view != JellyfinCatalogueView.LISTS &&
                !denied(before.launchFailure)) {
                val q = before.search.lowercase()
                val source = index?.entries.orEmpty()
                val entries = withContext(defaultDispatcher) { source.filter {
                    ((before.view != JellyfinCatalogueView.UNREAD && !before.unreadOnly) || it.played == false) &&
                        (before.creator == null || it.people.any(before.creator::matches)) &&
                        (it.item.name.contains(q, true) || it.item.seriesName?.contains(q, true) == true ||
                            it.folder?.name?.contains(q, true) == true)
                } }
                if (valid(request, captured)) _state.value = before.copy(entries = entries, loading = false,
                    loaded = true, hasMore = false, error = null)
                return@launch
            }
            if (before.offline && before.collection != null && !denied(before.launchFailure)) {
                val members = linkedMapOf<String, JellyfinCatalogueEntry>()
                var query = JellyfinCatalogueQuery(library.id, before.view, before.collection.id, limit = 30)
                var saved = cached(query)
                if (saved == null && before.unreadOnly) {
                    query = query.copy(view = JellyfinCatalogueView.UNREAD)
                    saved = cached(query)
                }
                var complete = false
                while (saved != null) {
                    saved.entries.forEach { members[it.item.id] = it }
                    complete = !saved.hasMore
                    if (complete || saved.nextIndex <= query.startIndex) break
                    query = query.copy(startIndex = saved.nextIndex)
                    saved = cached(query)
                }
                if (!isActive || !valid(request, captured)) return@launch
                val entries = withContext(defaultDispatcher) { members.values.filter {
                    it.item.name.contains(before.search, true) || it.item.seriesName?.contains(before.search, true) == true
                } }
                if (!isActive || !valid(request, captured)) return@launch
                _state.value = before.copy(entries = entries, loading = false, loaded = true, hasMore = false, partialSearch = !complete,
                    error = if (members.isEmpty() && !complete) "No saved collection membership is available. Connect to search this collection." else null)
                return@launch
            }
            val queryView = if (before.unreadOnly && (before.view != JellyfinCatalogueView.LISTS || before.collection != null))
                JellyfinCatalogueView.UNREAD else before.view
            val query = JellyfinCatalogueQuery(library.id, queryView, before.collection?.id,
                before.search, if (more) before.nextIndex else 0, limit = 30, creator = before.creator)
            val result = if (before.offline) cached(query)?.let { Result.success(it) }
                ?: Result.failure(java.io.IOException("No saved page for this scope"))
            else if (startup) launchRequest { fetch(query) } else fetch(query)
            if (!isActive || !valid(request, captured)) return@launch
            result.onSuccess { page ->
                val entries = withContext(defaultDispatcher) {
                    ((if (more) before.entries else emptyList()) + page.entries).distinctBy { it.item.id }
                }
                if (!isActive || !valid(request, captured)) return@onSuccess
                val current = _state.value
                if (denied(current.launchFailure)) return@onSuccess
                _state.value = project(current.copy(entries = entries, nextIndex = page.nextIndex, total = page.total,
                    hasMore = page.hasMore, loaded = true, loading = false, partialSearch = false, error = null,
                    offline = page.fromCache || current.offline))
            }.onFailure {
                if (denied(failureKind(it))) connectionFailed(it, startup = false)
                else _state.value = _state.value.copy(loading = false,
                    error = "Could not load this view. Saved metadata and downloads are preserved.")
            }
        }
    }

    companion object {
        internal const val LAUNCH_TIMEOUT_MILLIS = 10_000L
        private const val FOREGROUND_CHECK_NANOS = 60_000_000_000L
        fun factory(app: ComicReaderApp, creator: JellyfinPerson? = null) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                JellyfinCatalogueViewModel(app.libraryRepository, app.settings, creator = creator) as T
        }
    }
}
