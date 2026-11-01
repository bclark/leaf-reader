package com.chakra.comicreader.ui.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.chakra.comicreader.ComicReaderApp
import com.chakra.comicreader.ui.library.LibraryScreen
import com.chakra.comicreader.ui.library.LibraryViewModel
import com.chakra.comicreader.ui.library.SeriesDetailScreen
import com.chakra.comicreader.ui.library.SmbDriveScreen
import com.chakra.comicreader.ui.menu.MenuScreen
import com.chakra.comicreader.ui.reader.ReaderScreen
import com.chakra.comicreader.ui.reader.ReaderViewModel

private object Routes {
    const val LIBRARY = "library"
    const val DRIVE = "drive"
    const val READER = "reader/{comicId}"
    const val MENU = "menu"
    const val SERIES = "series/{seriesName}?volume={volume}"
    fun reader(comicId: Long) = "reader/$comicId"
    fun series(seriesName: String, volume: String?) = "series/${Uri.encode(seriesName)}${if (!volume.isNullOrBlank()) "?volume=${Uri.encode(volume)}" else ""}"
}

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val app = LocalContext.current.applicationContext as ComicReaderApp
    val libraryVm: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(app))

    NavHost(navController = navController, startDestination = Routes.LIBRARY) {
        composable(Routes.LIBRARY) {
            LibraryScreen(
                viewModel = libraryVm,
                onOpenComic = { id -> navController.navigate(Routes.reader(id)) },
                onOpenSeries = { name, vol -> navController.navigate(Routes.series(name, vol)) },
                onOpenMenu = { navController.navigate(Routes.MENU) },
                onOpenDrive = {
                    libraryVm.loadSmbDirectory("")
                    navController.navigate(Routes.DRIVE)
                },
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
        composable(Routes.MENU) {
            val amoled by app.amoledTheme.collectAsState()
            val pageSlide by app.pageSlideEnabled.collectAsState()
            MenuScreen(
                amoledTheme = amoled,
                onSetAmoledTheme = app::setAmoledTheme,
                pageSlideEnabled = pageSlide,
                onSetPageSlideEnabled = app::setPageSlideEnabled,
                settings = app.settings,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.READER,
            arguments = listOf(navArgument("comicId") { type = NavType.LongType }),
        ) { entry ->
            val comicId = entry.arguments?.getLong("comicId") ?: return@composable
            val pageSlide by app.pageSlideEnabled.collectAsState()
            val vm: ReaderViewModel = viewModel(
                key = "reader-$comicId",
                factory = ReaderViewModel.factory(app, comicId),
            )
            ReaderScreen(
                viewModel = vm,
                pageSlideEnabled = pageSlide,
                onTogglePageSlideEnabled = { app.setPageSlideEnabled(!pageSlide) },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
