package net.digitalbc.leafreader.ui.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import net.digitalbc.leafreader.ComicReaderApp
import net.digitalbc.leafreader.ui.library.JellyfinCatalogueViewModel
import net.digitalbc.leafreader.ui.library.LibraryScreen
import net.digitalbc.leafreader.ui.library.LibraryViewModel
import net.digitalbc.leafreader.ui.library.ReadingListDetailScreen
import net.digitalbc.leafreader.ui.library.SeriesDetailScreen
import net.digitalbc.leafreader.ui.library.SmbDriveScreen
import net.digitalbc.leafreader.ui.menu.MenuScreen
import net.digitalbc.leafreader.ui.reader.ReaderScreen
import net.digitalbc.leafreader.ui.reader.ReaderViewModel

private object Routes {
    const val LIBRARY = "library"
    const val DRIVE = "drive"
    const val JELLYFIN = "jellyfin"
    const val READER = "reader/{comicId}"
    const val MENU = "menu"
    const val SERIES = "series/{seriesName}?volume={volume}"
    const val READING_LIST = "reading_list/{listId}"
    const val CREATOR = "creator/{name}?id={id}&scope={scope}"
    fun reader(comicId: Long) = "reader/$comicId"
    fun series(seriesName: String, volume: String?) = "series/${Uri.encode(seriesName)}${if (!volume.isNullOrBlank()) "?volume=${Uri.encode(volume)}" else ""}"
    fun readingList(listId: Long) = "reading_list/$listId"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val app = LocalContext.current.applicationContext as ComicReaderApp
    val libraryVm: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(app))

    val catalogueVm: JellyfinCatalogueViewModel = viewModel(factory = JellyfinCatalogueViewModel.factory(app))
    val catalogueState by catalogueVm.state.collectAsState()
    val offlineDownloads = catalogueState.accountScope == catalogueVm.positionScope &&
        catalogueState.downloadedFallback

    androidx.compose.runtime.CompositionLocalProvider(
        net.digitalbc.leafreader.ui.library.LocalCreatorNavigation provides { person ->
            navController.navigate("creator/${Uri.encode(person.name)}?id=${Uri.encode(person.id.orEmpty())}&scope=${Uri.encode(catalogueVm.positionScope)}")
        },
    ) {
    if (currentEntry?.destination?.route != Routes.READER)
        net.digitalbc.leafreader.ui.library.ProgressConflictHost(libraryVm)
    NavHost(navController = navController, startDestination = Routes.LIBRARY) {
        composable(Routes.CREATOR, arguments = listOf(
            navArgument("name") { type = NavType.StringType },
            navArgument("id") { type = NavType.StringType; defaultValue = "" },
            navArgument("scope") { type = NavType.StringType; defaultValue = "" },
        )) { entry ->
            val person = net.digitalbc.leafreader.data.jellyfin.JellyfinPerson(
                entry.arguments?.getString("name").orEmpty(),
                id = entry.arguments?.getString("id")?.takeIf { it.isNotBlank() })
            val creatorCatalogue: JellyfinCatalogueViewModel = viewModel(
                factory = JellyfinCatalogueViewModel.factory(app, person))
            val creatorLibrary: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(app))
            net.digitalbc.leafreader.ui.library.CreatorScreen(
                person, entry.arguments?.getString("scope").orEmpty(),
                creatorCatalogue, creatorLibrary,
                onBack = { navController.popBackStack() },
                onOpenComic = { navController.navigate(Routes.reader(it)) })
        }
        composable(Routes.LIBRARY) {
            LibraryScreen(
                viewModel = libraryVm,
                catalogue = catalogueVm,
                onOpenComic = { id -> navController.navigate(Routes.reader(id)) },
                onOpenSeries = { name, vol -> navController.navigate(Routes.series(name, vol)) },
                onOpenReadingList = { listId -> navController.navigate(Routes.readingList(listId)) },
                onOpenMenu = { navController.navigate(Routes.MENU) },
                onOpenDrive = {
                    libraryVm.loadSmbDirectory("")
                    navController.navigate(Routes.DRIVE)
                },
                onConnectJellyfin = { navController.navigate("menu?section=jellyfin") },
                onOpenJellyfin = {
                    libraryVm.setLibraryFilter(net.digitalbc.leafreader.ui.library.LibraryFilter.ALL)
                    catalogueVm.ensureRetry()
                },
            )
        }
        composable(
            route = Routes.READING_LIST,
            arguments = listOf(
                navArgument("listId") { type = NavType.LongType },
            ),
        ) { entry ->
            val listId = entry.arguments?.getLong("listId") ?: return@composable
            ReadingListDetailScreen(
                listId = listId,
                offlineDownloads = offlineDownloads,
                viewModel = libraryVm,
                onOpenComic = { id -> navController.navigate(Routes.reader(id)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.SERIES,
            arguments = listOf(
                navArgument("seriesName") { type = NavType.StringType },
                navArgument("volume") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            val seriesName = Uri.decode(entry.arguments?.getString("seriesName") ?: "")
            val volume = entry.arguments?.getString("volume")?.let { Uri.decode(it) }
            SeriesDetailScreen(
                seriesName = seriesName,
                offlineDownloads = offlineDownloads,
                volume = volume,
                viewModel = libraryVm,
                onOpenComic = { id -> navController.navigate(Routes.reader(id)) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.DRIVE) {
            SmbDriveScreen(
                viewModel = libraryVm,
                onBack = { navController.popBackStack() },
            )
        }
        // Restore older tasks into the main library; this is no longer a separate catalogue destination.
        composable(Routes.JELLYFIN) {
            LaunchedEffect(Unit) {
                navController.navigate(Routes.LIBRARY) {
                    popUpTo(navController.graph.id)
                    launchSingleTop = true
                }
            }
        }
        composable(Routes.MENU + "?section={section}", arguments = listOf(
            navArgument("section") { type = NavType.StringType; defaultValue = "" },
        )) { menuEntry ->
            val connectedOnEntry = rememberSaveable { libraryVm.hasJellyfinConfig }
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
                openJellyfinSetup = menuEntry.arguments?.getString("section") == "jellyfin",
                catalogueRefreshAt = { catalogueState.refreshTimeFor(catalogueVm.positionScope) },
                onBack = {
                    if (connectedOnEntry != libraryVm.hasJellyfinConfig) {
                        if (!libraryVm.hasJellyfinConfig) libraryVm.ensureJellyfinLanding()
                        navController.navigate(Routes.LIBRARY) {
                            popUpTo(navController.graph.id)
                            launchSingleTop = true
                        }
                    } else navController.popBackStack()
                },
            )
        }
        composable(
            route = Routes.READER,
            arguments = listOf(navArgument("comicId") { type = NavType.LongType }),
        ) { entry ->
            val comicId = entry.arguments?.getLong("comicId") ?: return@composable
            val pageSlide by app.pageSlideEnabled.collectAsState()
            val fitMode by app.pageFitMode.collectAsState()
            val portraitSpread by app.portraitSpread.collectAsState()
            val landscapeSpread by app.landscapeSpread.collectAsState()
            val vm: ReaderViewModel = viewModel(
                key = "reader-$comicId",
                factory = ReaderViewModel.factory(app, comicId),
            )
            ReaderScreen(
                viewModel = vm,
                pageSlideEnabled = pageSlide,
                onTogglePageSlideEnabled = { app.setPageSlideEnabled(!pageSlide) },
                fitMode = fitMode,
                onSetFitMode = app::setPageFitMode,
                portraitSpread = portraitSpread,
                landscapeSpread = landscapeSpread,
                onTogglePortraitSpread = { app.setPortraitSpread(!portraitSpread) },
                onToggleLandscapeSpread = { app.setLandscapeSpread(!landscapeSpread) },
                onBack = { navController.popBackStack() },
            )
        }
    }
    }
}
