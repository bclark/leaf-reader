package net.digitalbc.leafreader.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import net.digitalbc.leafreader.ComicReaderApp
import net.digitalbc.leafreader.data.db.ComicEntity
import net.digitalbc.leafreader.data.db.ReadingListEntity
import net.digitalbc.leafreader.data.library.LibraryRepository
import net.digitalbc.leafreader.data.settings.AppSettings
import net.digitalbc.leafreader.data.smb.SmbEntry
import net.digitalbc.leafreader.data.smb.SmbBrowserState
import net.digitalbc.leafreader.data.smb.SmbBrowserLogic
import net.digitalbc.leafreader.data.smb.SmbClient
import net.digitalbc.leafreader.data.smb.SmbErrors
import net.digitalbc.leafreader.data.smb.SmbImportPhase
import net.digitalbc.leafreader.data.smb.SmbImportProgress
import net.digitalbc.leafreader.data.jellyfin.JellyfinBreadcrumb
import net.digitalbc.leafreader.data.jellyfin.JellyfinBrowserLogic
import net.digitalbc.leafreader.data.jellyfin.JellyfinBrowserState
import net.digitalbc.leafreader.data.jellyfin.JellyfinException
import net.digitalbc.leafreader.data.jellyfin.JellyfinFailureKind
import net.digitalbc.leafreader.data.jellyfin.JellyfinItem
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class LibraryFilter(val label: String) {
    ALL("ALL"),
    SERIES("SERIES"),
    READING_LISTS("LISTS"),
    UNREAD("UNREAD"),
}

sealed interface LibraryItem {
    data class SingleComic(val comic: ComicEntity) : LibraryItem

    data class SeriesStack(
        val seriesName: String,
        val volume: String?,
        val issues: List<ComicEntity>,
        val topCoverComic: ComicEntity,
        val totalIssues: Int,
        val unreadIssues: Int,
        val progressFraction: Float,
    ) : LibraryItem

    data class ReadingListItem(
        val readingList: ReadingListEntity,
        val previewComics: List<ComicEntity>,
        val itemCount: Int,
    ) : LibraryItem
}

class LibraryViewModel(
    private val repository: LibraryRepository,
    private val settings: AppSettings,
    private val listSmbDirectory: suspend (String) -> Result<List<SmbEntry>> = repository::listSmbDirectory,
) : ViewModel() {

    init {
        viewModelScope.launch {
            repository.backfillMetadataIfNeeded()
        }
        viewModelScope.launch {
            repository.observeDownloadedJellyfinItemIds().collect { downloaded ->
                _jellyfinBrowser.update { JellyfinBrowserLogic.updateDownloadedIds(it, downloaded) }
            }
        }
    }

    private var importJob: Job? = null

    /** Search query for filtering comics in the library. */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _libraryFilter = MutableStateFlow(LibraryFilter.ALL)
    val libraryFilter: StateFlow<LibraryFilter> = _libraryFilter.asStateFlow()

    fun setLibraryFilter(filter: LibraryFilter) {
        _libraryFilter.value = filter
    }

    val comics: StateFlow<List<ComicEntity>> = combine(repository.comics, _searchQuery) { list, query ->
        if (query.isBlank()) {
            list
        } else {
            val q = query.trim()
            list.filter { it.title.contains(q, ignoreCase = true) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val readingLists: StateFlow<List<ReadingListEntity>> =
        repository.readingLists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val libraryItems: StateFlow<List<LibraryItem>> = combine(
        repository.comics,
        repository.readingLists,
        repository.allReadingListItems,
        _searchQuery,
        _libraryFilter,
    ) { allComics, lists, allListItems, query, filter ->
        val filteredComics = if (query.isBlank()) {
            allComics
        } else {
            val q = query.trim()
            allComics.filter {
                it.title.contains(q, ignoreCase = true) ||
                (it.series?.contains(q, ignoreCase = true) == true)
            }
        }

        val readingListCards = if (filter == LibraryFilter.ALL || filter == LibraryFilter.READING_LISTS) {
            val filteredLists = if (query.isBlank()) {
                lists
            } else {
                lists.filter { it.name.contains(query.trim(), ignoreCase = true) }
            }
            val comicMap = allComics.associateBy { it.id }
            filteredLists.map { list ->
                val listItems = allListItems.filter { it.listId == list.id }.sortedBy { it.sortOrder }
                val listComics = listItems.mapNotNull { comicMap[it.comicId] }
                LibraryItem.ReadingListItem(
                    readingList = list,
                    previewComics = listComics.take(4),
                    itemCount = listComics.size,
                )
            }
        } else emptyList()

        when (filter) {
            LibraryFilter.ALL -> {
                val seriesGroups = filteredComics
                    .filter { !it.series.isNullOrBlank() }
                    .groupBy { "${it.series}__${it.volume.orEmpty()}".lowercase() }

                val handledComicIds = mutableSetOf<Long>()
                val items = mutableListOf<LibraryItem>()

                for (comic in filteredComics) {
                    if (comic.id in handledComicIds) continue

                    val seriesKey = if (!comic.series.isNullOrBlank()) {
                        "${comic.series}__${comic.volume.orEmpty()}".lowercase()
                    } else null

                    val group = if (seriesKey != null) seriesGroups[seriesKey] else null
                    if (group != null && group.size >= 2) {
                        val sortedIssues = group.sortedWith(
                            compareBy<ComicEntity> { it.issueNumber ?: Float.MAX_VALUE }
                                .thenBy { it.title }
                        )
                        val nextIssue = sortedIssues.firstOrNull { it.pageCount > 0 && it.lastPage < it.pageCount - 1 }
                            ?: sortedIssues.first()
                        val completedCount = sortedIssues.count { it.pageCount > 0 && it.lastPage >= it.pageCount - 1 }
                        val unreadCount = sortedIssues.count { it.lastPage == 0 }

                        items.add(
                            LibraryItem.SeriesStack(
                                seriesName = group.first().series ?: "Series",
                                volume = group.first().volume,
                                issues = sortedIssues,
                                topCoverComic = nextIssue,
                                totalIssues = sortedIssues.size,
                                unreadIssues = unreadCount,
                                progressFraction = if (sortedIssues.isNotEmpty()) completedCount.toFloat() / sortedIssues.size else 0f,
                            )
                        )
                        for (c in group) handledComicIds.add(c.id)
                    } else {
                        items.add(LibraryItem.SingleComic(comic))
                        handledComicIds.add(comic.id)
                    }
                }
                items + readingListCards
            }
            LibraryFilter.SERIES -> {
                val seriesGroups = filteredComics
                    .filter { !it.series.isNullOrBlank() }
                    .groupBy { "${it.series}__${it.volume.orEmpty()}".lowercase() }

                seriesGroups.values.map { group ->
                    val sortedIssues = group.sortedWith(
                        compareBy<ComicEntity> { it.issueNumber ?: Float.MAX_VALUE }
                            .thenBy { it.title }
                    )
                    val nextIssue = sortedIssues.firstOrNull { it.pageCount > 0 && it.lastPage < it.pageCount - 1 }
                        ?: sortedIssues.first()
                    val completedCount = sortedIssues.count { it.pageCount > 0 && it.lastPage >= it.pageCount - 1 }
                    val unreadCount = sortedIssues.count { it.lastPage == 0 }

                    LibraryItem.SeriesStack(
                        seriesName = group.first().series ?: "Series",
                        volume = group.first().volume,
                        issues = sortedIssues,
                        topCoverComic = nextIssue,
                        totalIssues = sortedIssues.size,
                        unreadIssues = unreadCount,
                        progressFraction = if (sortedIssues.isNotEmpty()) completedCount.toFloat() / sortedIssues.size else 0f,
                    )
                }.sortedBy { it.seriesName.lowercase() }
            }
            LibraryFilter.READING_LISTS -> readingListCards
            LibraryFilter.UNREAD -> {
                val unreadComics = filteredComics.filter { it.lastPage == 0 }
                unreadComics.map { LibraryItem.SingleComic(it) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Global default reading direction applied to newly imported comics. */
    private val _defaultRightToLeft = MutableStateFlow(settings.defaultRightToLeft)
    val defaultRightToLeft: StateFlow<Boolean> = _defaultRightToLeft.asStateFlow()

    /** Number of columns displayed in the library grid. */
    private val _libraryColumns = MutableStateFlow(settings.libraryColumns)
    val libraryColumns: StateFlow<Int> = _libraryColumns.asStateFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _importProgress = MutableStateFlow<Float?>(0f)
    val importProgress: StateFlow<Float?> = _importProgress.asStateFlow()

    private val _importStatus = MutableStateFlow<String?>(null)
    val importStatus: StateFlow<String?> = _importStatus.asStateFlow()

    private val _importDetail = MutableStateFlow<String?>(null)
    val importDetail: StateFlow<String?> = _importDetail.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _smbBrowser = MutableStateFlow(SmbBrowserState.empty())
    val smbBrowser: StateFlow<SmbBrowserState> = _smbBrowser.asStateFlow()
    private var smbListingJob: Job? = null
    private var smbConnectionKey: Triple<String, String, String>? = null

    fun toggleSmbSelection(path: String) { _smbBrowser.update { SmbBrowserLogic.toggle(it, path) } }
    fun selectAllSmb() { _smbBrowser.update(SmbBrowserLogic::selectAll) }
    fun clearSmbSelection() { _smbBrowser.update(SmbBrowserLogic::clearSelection) }
    fun cancelSmbBrowse() {
        smbListingJob?.cancel()
        _smbBrowser.update(SmbBrowserLogic::cancel)
    }

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    private val _selectedComicIds = MutableStateFlow(emptySet<Long>())
    val selectedComicIds: StateFlow<Set<Long>> = _selectedComicIds.asStateFlow()

    val hasSmbConfig: Boolean get() = settings.hasSmbConfig

    private val _jellyfinBrowser = MutableStateFlow(JellyfinBrowserState.empty())
    val jellyfinBrowser: StateFlow<JellyfinBrowserState> = _jellyfinBrowser.asStateFlow()
    private var jellyfinListingJob: Job? = null

    val hasJellyfinConfig: Boolean get() = settings.hasJellyfinConfig

    fun toggleJellyfinSelection(itemId: String) {
        _jellyfinBrowser.update { JellyfinBrowserLogic.toggleSelection(it, itemId) }
    }

    fun selectAllJellyfin() {
        _jellyfinBrowser.update(JellyfinBrowserLogic::selectAllImportable)
    }

    fun clearJellyfinSelection() {
        _jellyfinBrowser.update(JellyfinBrowserLogic::clearSelection)
    }

    fun cancelJellyfinBrowse() {
        jellyfinListingJob?.cancel()
        _jellyfinBrowser.update { it.copy(loading = false) }
    }

    fun loadJellyfinRoot() {
        jellyfinListingJob?.cancel()
        val request = JellyfinBrowserLogic.begin(_jellyfinBrowser.value)
        _jellyfinBrowser.value = request
        jellyfinListingJob = viewModelScope.launch {
            val result = repository.listJellyfinViews()
            result.onSuccess { views ->
                _jellyfinBrowser.update { JellyfinBrowserLogic.completeViews(it, request.generation, views) }
            }.onFailure { error ->
                if (_jellyfinBrowser.value.generation == request.generation) {
                    val kind = (error as? JellyfinException)?.kind ?: JellyfinFailureKind.UNKNOWN
                    _jellyfinBrowser.update { JellyfinBrowserLogic.fail(it, request.generation, kind) }
                    _message.value = error.message ?: "Failed to load Jellyfin libraries"
                }
            }
        }
    }

    fun loadJellyfinFolder(parentId: String, title: String) {
        jellyfinListingJob?.cancel()
        val request = JellyfinBrowserLogic.begin(_jellyfinBrowser.value)
        _jellyfinBrowser.value = request

        val currentBreadcrumbs = _jellyfinBrowser.value.breadcrumbs
        val existingIndex = currentBreadcrumbs.indexOfFirst { it.id == parentId }
        val updatedBreadcrumbs = if (existingIndex >= 0) {
            currentBreadcrumbs.take(existingIndex + 1)
        } else {
            currentBreadcrumbs + JellyfinBreadcrumb(parentId, title)
        }

        jellyfinListingJob = viewModelScope.launch {
            val result = repository.listJellyfinItems(parentId)
            result.onSuccess { res ->
                _jellyfinBrowser.update {
                    JellyfinBrowserLogic.completeItems(
                        state = it,
                        generation = request.generation,
                        parentId = parentId,
                        title = title,
                        items = res.items,
                        breadcrumbs = updatedBreadcrumbs,
                    )
                }
            }.onFailure { error ->
                if (_jellyfinBrowser.value.generation == request.generation) {
                    val kind = (error as? JellyfinException)?.kind ?: JellyfinFailureKind.UNKNOWN
                    _jellyfinBrowser.update { JellyfinBrowserLogic.fail(it, request.generation, kind) }
                    _message.value = error.message ?: "Failed to load folder"
                }
            }
        }
    }

    fun navigateJellyfinUp() {
        val breadcrumbs = _jellyfinBrowser.value.breadcrumbs
        if (breadcrumbs.size <= 1) {
            loadJellyfinRoot()
        } else {
            val parent = breadcrumbs[breadcrumbs.size - 2]
            val parentId = parent.id
            if (parentId != null) {
                loadJellyfinFolder(parentId, parent.name)
            } else {
                loadJellyfinRoot()
            }
        }
    }

    fun importJellyfinItems(items: List<JellyfinItem>, onFinished: () -> Unit = {}) {
        if (items.isEmpty()) return
        val selectedItems = items.toList()
        val previousImport = importJob
        previousImport?.cancel()
        importJob = viewModelScope.launch(Dispatchers.IO) {
            previousImport?.join()
            _importing.value = true
            _importProgress.value = 0f
            _importDetail.value = "Connecting to Jellyfin…"
            var importedCount = 0
            val total = selectedItems.size
            try {
                for ((index, item) in selectedItems.withIndex()) {
                    if (!isActive) break
                    _importProgress.value = (index.toFloat() / total).coerceIn(0f, 1f)
                    _importDetail.value = "Downloading ${item.name} (${index + 1}/$total)"

                    val result = repository.importJellyfinComic(
                        item = item,
                        onProgress = { progress ->
                            val fileProgress = progress.fraction
                            val overall = ((index + fileProgress) / total).coerceIn(0f, 1f)
                            _importProgress.value = overall
                        },
                        isCancelled = { !isActive },
                    )
                    if (result.isSuccess) {
                        importedCount++
                    } else {
                        val err = result.exceptionOrNull()
                        if (err !is CancellationException) {
                            _message.value = err?.message ?: "Failed to import ${item.name}"
                        }
                    }
                }
            } finally {
                _importing.value = false
                _importProgress.value = null
                _importDetail.value = null
                if (importedCount > 0) {
                    _message.value = "Imported $importedCount ${if (importedCount == 1) "comic" else "comics"} from Jellyfin"
                }
                onFinished()
            }
        }
    }

    fun cancelImport() {
        importJob?.cancel()
        // Keep the job reference until its cleanup completes so the next batch can join it.
        _message.value = "Cancelling import…"
    }

    fun importComic(uri: Uri) = importComics(listOf(uri))

    fun importComics(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val previousImport = importJob
        previousImport?.cancel()
        importJob = viewModelScope.launch(Dispatchers.IO) {
            previousImport?.join()
            _importing.value = true
            _importProgress.value = 0f
            _importDetail.value = null
            var importedCount = 0
            var failedCount = 0
            var wasCancelled = false
            val total = uris.size

            try {
                for ((index, uri) in uris.withIndex()) {
                    if (!isActive) {
                        wasCancelled = true
                        break
                    }
                    _importStatus.value = if (total > 1) "Importing ${index + 1} of $total…" else "Importing comic…"
                    val result = repository.importComic(
                        uri = uri,
                        onProgress = { fileProgress ->
                            val overall = (index + fileProgress) / total
                            _importProgress.value = overall
                            val pct = (overall * 100).toInt().coerceIn(0, 100)
                            _importDetail.value = "$pct% completed"
                        },
                        isCancelled = { !isActive },
                    )
                    if (result.isSuccess) {
                        importedCount++
                    } else {
                        if (!isActive) {
                            wasCancelled = true
                            break
                        }
                        failedCount++
                    }
                }
            } catch (e: CancellationException) {
                wasCancelled = true
            } finally {
                _importing.value = false
                _importProgress.value = 0f
                _importStatus.value = null
                _importDetail.value = null
                if (wasCancelled) {
                    _message.value = "Import cancelled."
                } else {
                    if (failedCount == 0) {
                        _message.value = if (total == 1) "Imported 1 comic." else "Imported $importedCount comics."
                    } else {
                        _message.value = "Imported $importedCount of $total comics ($failedCount failed)."
                    }
                }
            }
        }
    }

    fun importFolder(treeUri: Uri) {
        val uris = repository.getUrisFromFolder(treeUri)
        if (uris.isNotEmpty()) {
            importComics(uris)
        } else {
            _message.value = "No comic files found in that folder."
        }
    }

    fun loadSmbDirectory(path: String = "") {
        val connectionKey = Triple(settings.smbHost, settings.smbShare, settings.smbUsername)
        if (smbConnectionKey != connectionKey) {
            smbListingJob?.cancel()
            _smbBrowser.update(SmbBrowserLogic::reset)
            smbConnectionKey = connectionKey
        }
        val request = try { SmbBrowserLogic.begin(_smbBrowser.value, path) }
            catch (error: IllegalArgumentException) {
                _message.value = SmbErrors.message(error)
                return
            }
        smbListingJob?.cancel()
        _smbBrowser.value = request
        smbListingJob = viewModelScope.launch {
            try {
                val result = listSmbDirectory(request.pendingPath!!)
                result.onSuccess { entries ->
                    _smbBrowser.update { SmbBrowserLogic.complete(it, request.generation, entries) }
                }.onFailure { error ->
                    if (_smbBrowser.value.generation == request.generation) {
                        _smbBrowser.update { SmbBrowserLogic.fail(it, request.generation, SmbErrors.kind(error)) }
                        _message.value = SmbErrors.message(error)
                    }
                }
            } catch (error: CancellationException) {
                _smbBrowser.update {
                    if (it.generation == request.generation) SmbBrowserLogic.cancel(it) else it
                }
                throw error
            }
        }
    }

    fun importSmbComic(entry: SmbEntry, onFinished: () -> Unit = {}) =
        importSmbComics(listOf(entry), onFinished)

    fun importSmbComics(entries: List<SmbEntry>, onFinished: () -> Unit = {}) {
        if (entries.isEmpty()) return
        val config = try { SmbClient.configuration(settings) } catch (error: IllegalArgumentException) {
            _message.value = SmbErrors.message(error)
            return
        }
        val password = settings.smbPassword
        val selectedEntries = entries.toList()
        val previousImport = importJob
        previousImport?.cancel()
        importJob = viewModelScope.launch(Dispatchers.IO) {
            previousImport?.join()
            _importing.value = true
            _importProgress.value = 0f
            _importDetail.value = "Connecting to drive…"
            _importProgress.value = null
            var importedCount = 0
            var failedCount = 0
            var wasCancelled = false
            val total = selectedEntries.size

            try {
                for ((index, entry) in selectedEntries.withIndex()) {
                    if (!isActive) {
                        wasCancelled = true
                        break
                    }
                    val baseStatus = if (total > 1) "Importing ${index + 1} of $total: ${entry.name}" else "Importing ${entry.name}"
                    _importStatus.value = baseStatus

                    val result = repository.importSmbComic(
                        entry = entry,
                        config = config,
                        password = password,
                        onProgress = { transfer ->
                            val overall = SmbImportProgress.fraction(index, total, transfer, SmbImportPhase.DOWNLOADING)
                            _importProgress.value = overall
                            val speedText = formatSpeed(transfer.bytesPerSec)
                            val bytesText = if (transfer.totalBytes > 0L) {
                                "${formatFileSize(transfer.bytesTransferred)} / ${formatFileSize(transfer.totalBytes)}"
                            } else if (transfer.bytesTransferred > 0L) {
                                "${formatFileSize(transfer.bytesTransferred)} downloaded"
                            } else ""
                            _importDetail.value = when {
                                bytesText.isNotEmpty() && speedText.isNotEmpty() -> "$bytesText · $speedText"
                                bytesText.isNotEmpty() -> bytesText
                                speedText.isNotEmpty() -> speedText
                                else -> null
                            }
                        },
                        isCancelled = { !isActive },
                    )
                    if (result.isSuccess) {
                        importedCount++
                    } else {
                        if (!isActive) {
                            wasCancelled = true
                            break
                        }
                        failedCount++
                    }
                }
            } catch (e: CancellationException) {
                wasCancelled = true
            } finally {
                _importing.value = false
                _importProgress.value = 0f
                _importStatus.value = null
                _importDetail.value = null
                if (wasCancelled) {
                    _message.value = "Import cancelled."
                } else {
                    if (failedCount == 0) {
                        _message.value = if (total == 1) "Imported 1 comic." else "Imported $importedCount comics."
                    } else {
                        _message.value = "Imported $importedCount of $total comics ($failedCount failed)."
                    }
                    onFinished()
                }
            }
        }
    }

    fun showMessage(text: String) {
        _message.value = text
    }

    fun deleteComic(id: Long) {
        viewModelScope.launch { repository.deleteComic(id) }
    }

    fun setEditMode(enabled: Boolean) {
        _isEditMode.value = enabled
        if (!enabled) {
            _selectedComicIds.value = emptySet()
        }
    }

    fun toggleComicSelection(id: Long) {
        val current = _selectedComicIds.value
        _selectedComicIds.value = if (id in current) current - id else current + id
    }

    fun selectAllComics(ids: List<Long>) {
        _selectedComicIds.value = ids.toSet()
    }

    fun setSelectedComicIds(ids: Set<Long>) {
        _selectedComicIds.value = ids
    }

    fun clearComicSelection() {
        _selectedComicIds.value = emptySet()
    }

    fun deleteSelectedComics() {
        val ids = _selectedComicIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { id ->
                repository.deleteComic(id)
            }
            _selectedComicIds.value = emptySet()
            _isEditMode.value = false
            _message.value = "Deleted ${ids.size} comics."
        }
    }

    fun updateComicsSeries(ids: List<Long>, series: String?, volume: String?) {
        val s = series?.trim()?.ifBlank { null }
        val v = volume?.trim()?.ifBlank { null }
        viewModelScope.launch {
            for (id in ids) {
                val comic = repository.getComic(id) ?: continue
                repository.updateComicMetadata(id, s, v, comic.issueNumber)
            }
            _selectedComicIds.value = emptySet()
            _isEditMode.value = false
            _message.value = if (s != null) "Grouped ${ids.size} comics under \"$s\"" else "Cleared series for ${ids.size} comics"
        }
    }

    fun createReadingList(name: String, onCreated: (Long) -> Unit = {}) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = repository.createReadingList(name.trim())
            if (id > 0) {
                _message.value = "Created reading list \"${name.trim()}\""
                onCreated(id)
            }
        }
    }

    fun addComicsToReadingList(listId: Long, comicIds: List<Long>) {
        viewModelScope.launch {
            for (id in comicIds) {
                repository.addComicToReadingList(listId, id)
            }
            _selectedComicIds.value = emptySet()
            _isEditMode.value = false
            _message.value = "Added ${comicIds.size} ${if (comicIds.size == 1) "comic" else "comics"} to reading list"
        }
    }

    fun observeReadingList(listId: Long): Flow<ReadingListEntity?> =
        repository.observeReadingList(listId)

    fun observeComicsForList(listId: Long): Flow<List<ComicEntity>> =
        repository.observeComicsForList(listId)

    fun renameReadingList(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.renameReadingList(id, name.trim())
            _message.value = "Renamed list to \"${name.trim()}\""
        }
    }

    fun deleteReadingList(id: Long) {
        viewModelScope.launch {
            repository.deleteReadingList(id)
            _message.value = "Deleted reading list"
        }
    }

    fun removeComicFromReadingList(listId: Long, comicId: Long) {
        viewModelScope.launch {
            repository.removeComicFromReadingList(listId, comicId)
        }
    }

    fun reorderReadingList(listId: Long, comicIdsInOrder: List<Long>) {
        viewModelScope.launch {
            repository.reorderReadingList(listId, comicIdsInOrder)
        }
    }

    fun moveReadingListItem(listId: Long, comicId: Long, direction: Int) {
        viewModelScope.launch {
            repository.moveReadingListItem(listId, comicId, direction)
        }
    }

    fun markReadingListRead(listId: Long, read: Boolean) {
        viewModelScope.launch {
            val comics = repository.observeComicsForList(listId).firstOrNull() ?: return@launch
            for (comic in comics) {
                val newPage = if (read) (comic.pageCount - 1).coerceAtLeast(0) else 0
                repository.saveProgress(comic.id, newPage, 0)
            }
            _message.value = if (read) "Marked all comics in list as read" else "Marked all comics in list as unread"
        }
    }

    fun markComicRead(comicId: Long, read: Boolean) {
        viewModelScope.launch {
            val comic = repository.getComic(comicId) ?: return@launch
            val newPage = if (read) (comic.pageCount - 1).coerceAtLeast(0) else 0
            repository.saveProgress(comic.id, newPage, 0)
            _message.value = if (read) "Marked \"${comic.title}\" as read" else "Marked \"${comic.title}\" as unread"
        }
    }

    fun markSeriesRead(seriesName: String, volume: String?, read: Boolean) {
        viewModelScope.launch {
            val allComics = repository.comics.firstOrNull() ?: emptyList()
            val matching = allComics.filter {
                it.series.equals(seriesName, ignoreCase = true) &&
                (volume.isNullOrBlank() || it.volume.equals(volume, ignoreCase = true))
            }
            for (comic in matching) {
                val newPage = if (read) (comic.pageCount - 1).coerceAtLeast(0) else 0
                repository.saveProgress(comic.id, newPage, 0)
            }
            _message.value = if (read) "Marked $seriesName as completed" else "Marked $seriesName as unread"
        }
    }

    /** Flips the global default reading direction for comics imported from now on. */
    fun toggleDefaultDirection() {
        settings.defaultRightToLeft = !settings.defaultRightToLeft
        _defaultRightToLeft.value = settings.defaultRightToLeft
    }

    /** Sets the number of columns in the library grid and persists it. */
    fun setLibraryColumns(columns: Int) {
        settings.libraryColumns = columns
        _libraryColumns.value = columns
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    fun consumeMessage() {
        _message.value = null
    }

    private fun formatSpeed(bytesPerSec: Long): String = when {
        bytesPerSec <= 0L -> ""
        bytesPerSec >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB/s", bytesPerSec / (1024f * 1024f))
        bytesPerSec >= 1024 -> String.format(java.util.Locale.US, "%.0f KB/s", bytesPerSec / 1024f)
        else -> "$bytesPerSec B/s"
    }

    private fun formatFileSize(bytes: Long): String = when {
        bytes <= 0L -> ""
        bytes >= 1024L * 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f GB", bytes / (1024f * 1024f * 1024f))
        bytes >= 1024L * 1024L -> String.format(java.util.Locale.US, "%.1f MB", bytes / (1024f * 1024f))
        bytes >= 1024L -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024f)
        else -> "$bytes B"
    }

    companion object {
        fun factory(app: ComicReaderApp): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LibraryViewModel(app.libraryRepository, app.settings) as T
            }
    }
}
