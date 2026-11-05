package net.digitalbc.leafreader

import android.app.Application
import net.digitalbc.leafreader.data.db.AppDatabase
import net.digitalbc.leafreader.data.library.LibraryRepository
import net.digitalbc.leafreader.data.settings.AppSettings
import net.digitalbc.leafreader.data.settings.ThemeMode
import net.digitalbc.leafreader.data.settings.LauncherIcon
import net.digitalbc.leafreader.data.settings.LauncherIcons
import net.digitalbc.leafreader.detection.MlPanelDetector
import net.digitalbc.leafreader.detection.NoopPanelSource
import net.digitalbc.leafreader.detection.PanelSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Application entry point. Holds process-wide singletons (the Room database, the
 * [LibraryRepository], and the panel detector).
 */
class ComicReaderApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.get(this) }
    val settings: AppSettings by lazy { AppSettings(this) }
    val libraryRepository: LibraryRepository by lazy {
        LibraryRepository(this, database.comicDao(), settings, database.readingListDao(), database)
    }

    companion object {
        /**
         * Temporary experiment flag: disables ML panel detection and guided panel-by-panel reading,
         * keeping Leaf in whole-page mode with standard page navigation and zoom/pan.
         * Set to false to restore panel-by-panel guided reading.
         */
        const val EXPERIMENT_DISABLE_PANEL_READING = true
    }

    /** On-device ML panel detector; falls back to whole-page-only if the model can't load or experiment is active. */
    val panelSource: PanelSource by lazy {
        if (EXPERIMENT_DISABLE_PANEL_READING) {
            NoopPanelSource
        } else {
            MlPanelDetector.tryCreate(this) ?: NoopPanelSource
        }
    }

    private val _themeMode by lazy { MutableStateFlow(settings.themeMode) }
    val themeMode: StateFlow<ThemeMode> by lazy { _themeMode.asStateFlow() }

    fun setThemeMode(mode: ThemeMode) {
        settings.themeMode = mode
        _themeMode.value = mode
    }

    private val _launcherIcon by lazy { MutableStateFlow(settings.launcherIcon) }
    val launcherIcon: StateFlow<LauncherIcon> by lazy { _launcherIcon.asStateFlow() }

    fun setLauncherIcon(icon: LauncherIcon) {
        LauncherIcons.apply(this, icon)
        settings.launcherIcon = icon
        _launcherIcon.value = icon
    }

    /** Process-wide reactive theme flag so the whole UI re-themes the moment it's toggled. */
    private val _amoledTheme by lazy { MutableStateFlow(settings.amoledTheme) }
    val amoledTheme: StateFlow<Boolean> by lazy { _amoledTheme.asStateFlow() }

    fun setAmoledTheme(enabled: Boolean) {
        // Resolve legacy AMOLED migration before changing the old preference.
        settings.themeMode = themeMode.value
        settings.amoledTheme = enabled
        _amoledTheme.value = enabled
    }

    /** Process-wide reactive flag for side-by-side horizontal page sliding vs classic swipe-to-turn. */
    private val _pageSlideEnabled by lazy { MutableStateFlow(settings.pageSlideEnabled) }
    val pageSlideEnabled: StateFlow<Boolean> by lazy { _pageSlideEnabled.asStateFlow() }

    fun setPageSlideEnabled(enabled: Boolean) {
        settings.pageSlideEnabled = enabled
        _pageSlideEnabled.value = enabled
    }

    /** Process-wide reactive fit mode for whole-page viewing (fit to page vs fit to width). */
    private val _pageFitMode by lazy { MutableStateFlow(settings.pageFitMode) }
    val pageFitMode: StateFlow<net.digitalbc.leafreader.ui.reader.PageFitMode> by lazy { _pageFitMode.asStateFlow() }

    fun setPageFitMode(mode: net.digitalbc.leafreader.ui.reader.PageFitMode) {
        settings.pageFitMode = mode
        _pageFitMode.value = mode
    }

    /** Layout preferences update the active reader immediately and persist across launches. */
    private val _portraitSpread by lazy { MutableStateFlow(settings.portraitSpread) }
    val portraitSpread: StateFlow<Boolean> by lazy { _portraitSpread.asStateFlow() }

    fun setPortraitSpread(enabled: Boolean) {
        settings.portraitSpread = enabled
        _portraitSpread.value = enabled
    }

    /** Process-wide reactive flag for optional two-page spread in landscape orientation. */
    private val _landscapeSpread by lazy { MutableStateFlow(settings.landscapeSpread) }
    val landscapeSpread: StateFlow<Boolean> by lazy { _landscapeSpread.asStateFlow() }

    fun setLandscapeSpread(enabled: Boolean) {
        settings.landscapeSpread = enabled
        _landscapeSpread.value = enabled
    }
}
