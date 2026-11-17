package net.digitalbc.leafreader.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.first
import net.digitalbc.leafreader.ComicReaderApp
import net.digitalbc.leafreader.data.settings.FirstLaunchState
import net.digitalbc.leafreader.ui.library.LibraryViewModel
import net.digitalbc.leafreader.ui.library.comicFileMimeTypes
import net.digitalbc.leafreader.ui.library.rememberComicFilePicker
import net.digitalbc.leafreader.ui.menu.AppMenuScreen

@Composable
internal fun FirstLaunchHost(app: ComicReaderApp, library: @Composable () -> Unit) {
    val imports: LibraryViewModel = viewModel(factory = LibraryViewModel.factory(app))
    val model: FirstLaunchViewModel = viewModel(factory = viewModelFactory {
        initializer {
            FirstLaunchViewModel(createSavedStateHandle(), app.firstLaunchState,
                readableIds = {
                    app.libraryRepository.comics.first().mapNotNull { row ->
                        app.libraryRepository.getReadableComic(row.id)?.id
                    }.toSet()
                },
                connected = {
                    val auth = app.libraryRepository.getJellyfinAuth()
                    app.settings.hasJellyfinConfig && auth != null &&
                        auth.userId == app.settings.jellyfinUserId && auth.serverId == app.settings.jellyfinServerId
                },
                startImport = imports::importComics, outcomes = imports.fileImportState,
                serverConnector = app.libraryRepository::connectJellyfin)
        }
    })
    val state by model.state.collectAsState()
    val picker = rememberComicFilePicker(model::filesSelected)
    LaunchedEffect(state.presentation) {
        if (model.claimPicker()) {
            try { picker.launch(comicFileMimeTypes) }
            catch (_: android.content.ActivityNotFoundException) { model.pickerUnavailable() }
        }
    }
    when {
        state.destination == null -> androidx.compose.foundation.layout.Box(
            androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center,
        ) { androidx.compose.material3.CircularProgressIndicator() }
        state.destination == FirstLaunchState.COMPLETE -> library()
        state.showSetup -> {
            val setupId = state.setupId
            val connecting by model.connecting.collectAsState()
            val failed by model.connectionFailed.collectAsState()
            BackHandler { model.closeSetup(setupId) }
            AppMenuScreen(app, openJellyfinSetup = true,
                onboardingConnector = model::connect, onboardingConnecting = connecting, onboardingFailure = failed,
                onJellyfinConnected = { model.closeSetup(setupId) },
                onBack = { model.closeSetup(setupId) })
        }
        else -> {
            BackHandler(enabled = state.destination == FirstLaunchState.OPTIONAL_JELLYFIN && !state.busy) {
                model.back()
            }
            val progress by imports.importProgress.collectAsState()
            FirstLaunchScreen(state, progress ?: 0f, onChooseFile = model::chooseFiles,
                onJellyfin = model::optionalJellyfin, onConnect = model::openSetup,
                onSkip = model::skip, onBack = model::back, onRetrySave = model::retrySave)
        }
    }
}
