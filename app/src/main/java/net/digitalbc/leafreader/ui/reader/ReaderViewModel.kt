package net.digitalbc.leafreader.ui.reader

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import net.digitalbc.leafreader.ComicReaderApp
import net.digitalbc.leafreader.data.archive.ComicArchiveFactory
import net.digitalbc.leafreader.data.library.LibraryRepository
import net.digitalbc.leafreader.data.page.PageLoader
import net.digitalbc.leafreader.detection.Panel
import net.digitalbc.leafreader.detection.PanelSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reading state machine for a single comic.
 *
 * Each page is read as a sequence of "slots": slot 0 is the whole page (zoomed-out intro), slots
 * `1..n` are the detected panels in reading order, and slot `n+1` is the whole page again (the
 * zoomed-out outro). Advancing past the outro turns to the next page (which starts at its intro),
 * so the view is always zoomed out across a page turn — matching the desired transition.
 *
 * Panels are detected lazily: the current page on demand, with the next page prefetched in the
 * background. Bitmaps are cached by [PageLoader]; ordered panels are cached here per page.
 */
class ReaderViewModel(
    private val comicId: Long,
    private val repository: LibraryRepository,
    private val panelSource: PanelSource,
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private var pageLoader: PageLoader? = null
    private val panelCache = HashMap<Int, List<Panel>>()

    private var rightToLeft = false
    private var spreadEnabled = false
    private var navJob: Job? = null

    init {
        viewModelScope.launch { open() }
    }

    private suspend fun open() {
        val comic = repository.getComic(comicId)
        if (comic == null) {
            _state.update { it.copy(loading = false, error = "Comic not found.") }
            return
        }
        rightToLeft = comic.rightToLeft
        val archive = try {
            ComicArchiveFactory.open(File(comic.filePath))
        } catch (t: Throwable) {
            _state.update { it.copy(loading = false, error = "Could not open this comic: ${t.message}") }
            return
        }
        pageLoader = PageLoader(archive)
        _state.update {
            it.copy(
                title = comic.title,
                pageCount = archive.pageCount,
                rightToLeft = rightToLeft,
            )
        }
        goToPage(comic.lastPage.coerceIn(0, (archive.pageCount - 1).coerceAtLeast(0)), comic.lastSlot)
    }

    fun setSpreadEnabled(enabled: Boolean) {
        if (spreadEnabled == enabled) return
        spreadEnabled = enabled
        if (_state.value.pageCount > 0 && _state.value.page != null) {
            goToPage(_state.value.readingPageIndex, targetSlot = 0)
        }
    }

    fun next() {
        val s = _state.value
        if (s.loading || s.error != null) return
        if (ComicReaderApp.EXPERIMENT_DISABLE_PANEL_READING) {
            nextPage()
            return
        }
        val lastSlot = s.panels.size + 1
        when {
            s.slot < lastSlot -> setSlot(s.slot + 1)
            s.pageIndex < s.pageCount - 1 -> goToPage(s.pageIndex + 1, targetSlot = 0)
            // Already at the final outro of the last page: nothing to do.
        }
    }

    fun previous() {
        val s = _state.value
        if (s.loading || s.error != null) return
        if (ComicReaderApp.EXPERIMENT_DISABLE_PANEL_READING) {
            previousPage()
            return
        }
        when {
            s.slot > 0 -> setSlot(s.slot - 1)
            s.pageIndex > 0 -> goToPage(s.pageIndex - 1, targetSlot = ENTER_AT_OUTRO)
        }
    }

    /** Turn to the next whole page (skips remaining panels), landing on its full-page view. */
    fun nextPage() {
        val s = _state.value
        if (s.loading || s.error != null) return
        val step = if (s.isSpread) 2 else 1
        val target = s.pageIndex + step
        if (target < s.pageCount) goToPage(target, targetSlot = 0)
    }

    /** Turn to the previous whole page, landing on its full-page view. */
    fun previousPage() {
        val s = _state.value
        if (s.loading || s.error != null) return
        if (s.pageIndex <= 0) return
        goToPage(s.previousSpread?.first ?: s.pageIndex - 1, targetSlot = 0)
    }

    /** Zoom back out to the whole current page (full-page view). */
    fun showFullPage() {
        val s = _state.value
        if (s.loading || s.error != null) return
        if (s.slot != 0) setSlot(0)
    }

    /** Jump directly to a page (from the scrubber), landing on its full-page view. */
    fun jumpToPage(page: Int) {
        val s = _state.value
        if (s.error != null || s.pageCount == 0) return
        val target = page.coerceIn(0, s.pageCount - 1)
        if (target != s.readingPageIndex) goToPage(target, targetSlot = 0)
    }

    /** Returns the cached bitmap for [page] if already decoded in memory, or null. */
    fun getCachedPageBitmap(page: Int): Bitmap? = pageLoader?.getCachedPage(page)

    /** Decodes (or returns cached) bitmap for [page] for the horizontal pager. */
    suspend fun loadPageBitmap(page: Int): Bitmap? =
        runCatching { pageLoader?.loadPage(page) }.getOrNull()

    /** Syncs ViewModel state when the horizontal pager settles on [page]. */
    fun onPageSettled(page: Int) {
        val s = _state.value
        if (page < 0 || page >= s.pageCount || page == s.pageIndex) return
        goToPage(page, targetSlot = 0)
    }

    fun toggleReadingDirection() {
        rightToLeft = !rightToLeft
        panelCache.clear()
        _state.update { it.copy(rightToLeft = rightToLeft) }
        viewModelScope.launch { repository.setReadingDirection(comicId, rightToLeft) }
        // Re-detect current page with new ordering, keeping the page in view.
        goToPage(_state.value.readingPageIndex, targetSlot = 0)
    }

    private fun setSlot(slot: Int) {
        _state.update { it.copy(slot = slot) }
        persistProgress()
    }

    private fun goToPage(page: Int, targetSlot: Int) {
        val loader = pageLoader ?: return
        navJob?.cancel()
        val isExperiment = ComicReaderApp.EXPERIMENT_DISABLE_PANEL_READING

        navJob = viewModelScope.launch {
            if (_state.value.page == null) {
                _state.update { it.copy(loading = true, error = null) }
            }
            try {
                val spread = resolveSpread(page) ?: return@launch
                val resolvedPage = spread.first
                val bitmap = loader.getCachedPage(resolvedPage) ?: loader.loadPage(resolvedPage)
                val secondBitmap = spread.second?.let { loader.getCachedPage(it) ?: loader.loadPage(it) }
                val previous = resolveSpread(resolvedPage - 1)
                val next = resolveSpread(spread.after)
                val cachedPanels = if (isExperiment) listOf(Panel.FULL_PAGE) else panelCache[resolvedPage]
                val initialPanels = cachedPanels ?: listOf(Panel.FULL_PAGE)
                fun slotFor(panels: List<Panel>) = if (isExperiment) 0 else when (targetSlot) {
                    ENTER_AT_OUTRO -> panels.size + 1
                    else -> targetSlot.coerceIn(0, panels.size + 1)
                }
                _state.update {
                    it.copy(loading = false, detecting = !isExperiment && cachedPanels == null,
                        pageIndex = resolvedPage, readingPageIndex = page, page = bitmap, secondPage = secondBitmap,
                        isSpread = secondBitmap != null, previousSpread = previous, nextSpread = next,
                        panels = initialPanels, slot = slotFor(initialPanels), error = null)
                }
                val panels = panelsFor(resolvedPage, bitmap)
                _state.update { it.copy(detecting = false, panels = panels, slot = slotFor(panels)) }
                persistProgress()
                for (neighbor in listOfNotNull(previous, next)) {
                    prefetch(neighbor.first)
                    neighbor.second?.let(::prefetch)
                }
            } catch (t: Throwable) {
                if (t is CancellationException) throw t
                _state.update {
                    it.copy(loading = false, detecting = false, error = "Failed to load page ${page + 1}: ${t.message}")
                }
            }
        }
    }

    private val widePages = mutableSetOf<Int>()
    private var sizesKnownThrough = 0

    private suspend fun resolveSpread(page: Int): ReaderSpread? {
        val loader = pageLoader ?: return null
        if (page !in 0 until loader.pageCount) return null
        if (!spreadEnabled) return ReaderSpread(page)
        // Read only bounds and retain the prefix so navigation never depends on bitmap-cache residency.
        while (sizesKnownThrough < minOf(page + 1, loader.pageCount - 1)) {
            val index = sizesKnownThrough + 1
            val cached = loader.getCachedPage(index)
            val size = if (cached != null) intArrayOf(cached.width, cached.height) else loader.pageSize(index)
            if (size[0] > size[1]) widePages += index
            sizesKnownThrough = index
        }
        return resolveReaderSpread(page, loader.pageCount, widePages)
    }

    private suspend fun panelsFor(page: Int, bitmap: Bitmap): List<Panel> {
        if (ComicReaderApp.EXPERIMENT_DISABLE_PANEL_READING) {
            return listOf(Panel.FULL_PAGE)
        }
        panelCache[page]?.let { return it }
        val panels = withContext(Dispatchers.Default) { panelSource.detect(bitmap, rightToLeft) }
        panelCache[page] = panels
        return panels
    }

    private fun prefetch(page: Int) {
        val loader = pageLoader ?: return
        if (page < 0 || page >= _state.value.pageCount) return
        if (ComicReaderApp.EXPERIMENT_DISABLE_PANEL_READING) {
            viewModelScope.launch { runCatching { loader.loadPage(page) } }
            return
        }
        if (panelCache.containsKey(page)) return
        viewModelScope.launch {
            runCatching {
                val bmp = loader.loadPage(page)
                panelsFor(page, bmp)
            }
        }
    }

    private fun persistProgress() {
        val s = _state.value
        val slotToSave = if (ComicReaderApp.EXPERIMENT_DISABLE_PANEL_READING) 0 else s.slot
        viewModelScope.launch { repository.saveProgress(comicId, s.readingPageIndex, slotToSave) }
    }

    override fun onCleared() {
        pageLoader?.close()
        pageLoader = null
    }

    companion object {
        /** Sentinel for [goToPage] meaning "enter this page at its outro" (used when paging back). */
        private const val ENTER_AT_OUTRO = -1

        fun factory(app: ComicReaderApp, comicId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ReaderViewModel(comicId, app.libraryRepository, app.panelSource) as T
            }
    }
}

/**
 * @property slot 0 = full-page intro, 1..panels.size = panels, panels.size + 1 = full-page outro.
 */
data class ReaderUiState(
    val title: String = "",
    val loading: Boolean = true,
    val detecting: Boolean = false,
    val pageIndex: Int = 0,
    /** Exact document page retained when a layout temporarily groups it with its partner. */
    val readingPageIndex: Int = 0,
    val pageCount: Int = 0,
    val page: Bitmap? = null,
    val secondPage: Bitmap? = null,
    val previousSpread: ReaderSpread? = null,
    val nextSpread: ReaderSpread? = null,
    val isSpread: Boolean = false,
    val panels: List<Panel> = listOf(Panel.FULL_PAGE),
    val slot: Int = 0,
    val rightToLeft: Boolean = false,
    val error: String? = null,
) {
    /** The page region currently framed by the camera. Intro/outro slots frame the whole page. */
    val currentCamera: Panel
        get() = if (slot in 1..panels.size) panels[slot - 1] else Panel.FULL_PAGE

    val isFullPageView: Boolean get() = isSpread || slot !in 1..panels.size

    /** 1-based panel number for display, or null when showing the whole page. */
    val panelLabel: Int? get() = if (slot in 1..panels.size) slot else null
}
