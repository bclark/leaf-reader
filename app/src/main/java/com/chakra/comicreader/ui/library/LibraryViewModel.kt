package com.chakra.comicreader.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chakra.comicreader.ComicReaderApp
import com.chakra.comicreader.data.db.ComicEntity
import com.chakra.comicreader.data.library.LibraryRepository
import com.chakra.comicreader.data.settings.AppSettings
import com.chakra.comicreader.data.smb.SmbEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class LibraryViewModel(
    private val repository: LibraryRepository,
    private val settings: AppSettings,
) : ViewModel() {

    private var importJob: Job? = null

    /** Search query for filtering comics in the library. */
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val comics: StateFlow<List<ComicEntity>> = combine(repository.comics, _searchQuery) { list, query ->
        if (query.isBlank()) {
            list
        } else {
            val q = query.trim()
            list.filter { it.title.contains(q, ignoreCase = true) }
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

    private val _importProgress = MutableStateFlow(0f)
    val importProgress: StateFlow<Float> = _importProgress.asStateFlow()

    private val _importStatus = MutableStateFlow<String?>(null)
    val importStatus: StateFlow<String?> = _importStatus.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _smbPath = MutableStateFlow("")
    val smbPath: StateFlow<String> = _smbPath.asStateFlow()

    private val _smbEntries = MutableStateFlow<List<SmbEntry>>(emptyList())
    val smbEntries: StateFlow<List<SmbEntry>> = _smbEntries.asStateFlow()

    private val _smbLoading = MutableStateFlow(false)
    val smbLoading: StateFlow<Boolean> = _smbLoading.asStateFlow()

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    private val _selectedComicIds = MutableStateFlow(emptySet<Long>())
    val selectedComicIds: StateFlow<Set<Long>> = _selectedComicIds.asStateFlow()

    val hasSmbConfig: Boolean get() = settings.hasSmbConfig

    fun importComic(uri: Uri) = importComics(listOf(uri))

    fun importComics(uris: List<Uri>) {
        if (uris.isEmpty()) return
        importJob?.cancel()
        importJob = viewModelScope.launch {
            val scope = this
            _importing.value = true
            _importProgress.value = 0f
            var importedCount = 0
            var failedCount = 0
            val total = uris.size
            try {
                uris.forEachIndexed { index, uri ->
                    currentCoroutineContext().ensureActive()
                    _importStatus.value = if (total > 1) "Importing ${index + 1} of $total…" else "Importing comic…"
                    val result = repository.importComic(uri, isActive = { scope.isActive }) { fileProgress ->
                        _importProgress.value = (index + fileProgress) / total
                    }
                    if (result.isSuccess) {
                        importedCount++
                    } else {
                        val ex = result.exceptionOrNull()
                        if (ex is CancellationException) throw ex
                        failedCount++
                    }
                }
                _importing.value = false
                _importProgress.value = 0f
                _importStatus.value = null
                if (failedCount == 0) {
                    _message.value = if (total == 1) "Imported 1 comic." else "Imported $importedCount comics."
                } else {
                    _message.value = "Imported $importedCount of $total comics ($failedCount failed)."
                }
            } catch (e: CancellationException) {
                _importing.value = false
                _importProgress.value = 0f
                _importStatus.value = null
                _message.value = "Download cancelled."
            }
        }
    }

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        _importing.value = false
        _importProgress.value = 0f
        _importStatus.value = null
        _message.value = "Download cancelled."
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
        viewModelScope.launch {
            _smbPath.value = path
            _smbLoading.value = true
            val result = repository.listSmbDirectory(path)
            _smbLoading.value = false
            result.onSuccess { _smbEntries.value = it }
                .onFailure { _message.value = "Drive error: ${it.message ?: "Connection failed"}" }
        }
    }

    fun importSmbComic(entry: SmbEntry, onFinished: () -> Unit = {}) =
        importSmbComics(listOf(entry), onFinished)

    fun importSmbComics(entries: List<SmbEntry>, onFinished: () -> Unit = {}) {
        if (entries.isEmpty()) return
        importJob?.cancel()
        importJob = viewModelScope.launch {
            val scope = this
            _importing.value = true
            _importProgress.value = 0f
            var importedCount = 0
            var failedCount = 0
            val total = entries.size
            try {
                entries.forEachIndexed { index, entry ->
                    currentCoroutineContext().ensureActive()
                    _importStatus.value = if (total > 1) "Importing ${index + 1} of $total: ${entry.name}" else "Importing ${entry.name}…"
                    val result = repository.importSmbComic(entry, isActive = { scope.isActive }) { fileProgress ->
                        _importProgress.value = (index + fileProgress) / total
                    }
                    if (result.isSuccess) {
                        importedCount++
                    } else {
                        val ex = result.exceptionOrNull()
                        if (ex is CancellationException) throw ex
                        failedCount++
                    }
                }
                _importing.value = false
                _importProgress.value = 0f
                _importStatus.value = null
                if (failedCount == 0) {
                    _message.value = if (total == 1) "Imported 1 comic." else "Imported $importedCount comics."
                } else {
                    _message.value = "Imported $importedCount of $total comics ($failedCount failed)."
                }
                onFinished()
            } catch (e: CancellationException) {
                _importing.value = false
                _importProgress.value = 0f
                _importStatus.value = null
                _message.value = "Download cancelled."
                onFinished()
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

    companion object {
        fun factory(app: ComicReaderApp): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LibraryViewModel(app.libraryRepository, app.settings) as T
            }
    }
}
