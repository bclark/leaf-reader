package net.digitalbc.leafreader.ui.menu

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import net.digitalbc.leafreader.ComicReaderApp

@Composable
internal fun AppMenuScreen(
    app: ComicReaderApp,
    openJellyfinSetup: Boolean = false,
    catalogueRefreshAt: () -> Long = { 0L },
    onboardingConnector: (suspend (String, Boolean, String, String) -> Result<net.digitalbc.leafreader.data.jellyfin.JellyfinAuth>)? = null,
    onboardingConnecting: Boolean? = null,
    onboardingFailure: Boolean = false,
    onJellyfinConnected: () -> Unit = {},
    onBack: () -> Unit,
) {
    val amoled by app.amoledTheme.collectAsState()
    val themeMode by app.themeMode.collectAsState()
    val launcherIcon by app.launcherIcon.collectAsState()
    val pageSlide by app.pageSlideEnabled.collectAsState()
    val fitMode by app.pageFitMode.collectAsState()
    val portraitSpread by app.portraitSpread.collectAsState()
    val landscapeSpread by app.landscapeSpread.collectAsState()
    MenuScreen(
        themeMode = themeMode,
        onSetThemeMode = app::setThemeMode,
        launcherIcon = launcherIcon,
        onSetLauncherIcon = app::setLauncherIcon,
        amoledTheme = amoled,
        onSetAmoledTheme = app::setAmoledTheme,
        pageSlideEnabled = pageSlide,
        onSetPageSlideEnabled = app::setPageSlideEnabled,
        pageFitMode = fitMode,
        onSetPageFitMode = app::setPageFitMode,
        portraitSpread = portraitSpread,
        landscapeSpread = landscapeSpread,
        onSetPortraitSpread = app::setPortraitSpread,
        onSetLandscapeSpread = app::setLandscapeSpread,
        settings = app.settings,
        repository = app.libraryRepository,
        openJellyfinSetup = openJellyfinSetup,
        catalogueRefreshAt = catalogueRefreshAt,
        onboardingConnector = onboardingConnector,
        onboardingConnecting = onboardingConnecting,
        onboardingFailure = onboardingFailure,
        onJellyfinConnected = onJellyfinConnected,
        onBack = onBack,
    )
}
