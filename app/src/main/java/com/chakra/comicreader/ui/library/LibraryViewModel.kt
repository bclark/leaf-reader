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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    private var importJob: Job? = null

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _importProgress = MutableStateFlow(0f)
    val importProgress: StateFlow<Float> = _importProgress.asStateFlow()

    private val _importStatus = MutableStateFlow<String?>(null)
    val importStatus: StateFlow<String?> = _importStatus.asStateFlow()

    private val _importDetail = MutableStateFlow<String?>(null)
    val importDetail: StateFlow<String?> = _importDetail.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _smbPath = MutableStateFlow("")
    val smbPath: StateFlow<String> = _smbPath.asStateFlow()

    private val _smbEntries = MutableStateFlow<List<SmbEntry>>(emptyList())
    val smbEntries: StateFlow<List<SmbEntry>> = _smbEntries.asStateFlow()

    private val _smbLoading = MutableStateFlow(false)
    val smbLoading: StateFlow<Boolean> = _smbLoading.asStateFlow()

    val hasSmbConfig: Boolean get() = settings.hasSmbConfig

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        _importing.value = false
        _importProgress.value = 0f
        _importStatus.value = null
        _importDetail.value = null
        _message.value = "Import cancelled."
    }

    fun importComic(uri: Uri) = importComics(listOf(uri))

    fun importComics(uris: List<Uri>) {
        if (uris.isEmpty()) return
        importJob?.cancel()
        importJob = viewModelScope.launch(Dispatchers.IO) {
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
        importJob = viewModelScope.launch(Dispatchers.IO) {
            _importing.value = true
            _importProgress.value = 0f
            _importDetail.value = "Connecting to drive…"
            var importedCount = 0
            var failedCount = 0
            var wasCancelled = false
            val total = entries.size

            try {
                for ((index, entry) in entries.withIndex()) {
                    if (!isActive) {
                        wasCancelled = true
                        break
                    }
                    val baseStatus = if (total > 1) "Importing ${index + 1} of $total: ${entry.name}" else "Importing ${entry.name}"
                    _importStatus.value = baseStatus

                    val result = repository.importSmbComic(
                        entry = entry,
                        onProgress = { transfer ->
                            val overall = (index + transfer.fraction) / total
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
