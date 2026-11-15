package net.digitalbc.leafreader.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.flow.distinctUntilChanged
import net.digitalbc.leafreader.R
import net.digitalbc.leafreader.data.jellyfin.*

/** Navigation stays native; opening another detail has its own destination-scoped state. */
internal val LocalCreatorNavigation = staticCompositionLocalOf<((JellyfinPerson) -> Unit)?> { null }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CreatorScreen(
    person: JellyfinPerson,
    originScope: String,
    catalogue: JellyfinCatalogueViewModel,
    library: LibraryViewModel,
    onBack: () -> Unit,
    onOpenComic: (Long) -> Unit,
) {
    val candidate by catalogue.state.collectAsStateWithLifecycle()
    val state = candidate.takeIf { it.accountScope == catalogue.positionScope }
        ?: CatalogueState(creator = person)
    val sameAccount = originScope == catalogue.positionScope
    val detail by library.jellyfinDetail.collectAsStateWithLifecycle()
    val downloaded by library.jellyfinBrowser.collectAsStateWithLifecycle()
    var downloadedOnly by rememberSaveable { mutableStateOf(false) }
    val grid = rememberLazyGridState()
    var listSelection by remember(originScope) { mutableStateOf<RemoteListSelection?>(null) }
    LaunchedEffect(catalogue, sameAccount) { if (sameAccount) catalogue.ensure() }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle, catalogue, sameAccount) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && sameAccount) catalogue.onForeground()
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(grid, state.entries.size, state.hasMore, state.loading, state.error, sameAccount) {
        snapshotFlow {
            val layout = grid.layoutInfo
            layout.visibleItemsInfo.lastOrNull()?.index?.let { it >= layout.totalItemsCount - 8 } == true
        }.distinctUntilChanged().collect { nearEnd ->
            if (sameAccount && nearEnd && state.hasMore && !state.loading && state.error == null && !state.offline)
                catalogue.load(more = true)
        }
    }
    BackHandler(enabled = detail == null) { onBack() }
    if (sameAccount) RemoteListActions(listSelection, state.library?.id, library) { listSelection = null }
    if (sameAccount && detail != null) {
        JellyfinBrowserScreen(library, onBack = { library.closeJellyfinDetail() },
            onOpenComic = onOpenComic, onOpenLocal = {}, onOpenSettings = onBack)
        return
    }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().navigationBarsPadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.jellyfin_back_to_catalogue))
                }
                Text(stringResource(R.string.creator_comics_by, person.name),
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            if (!sameAccount) {
                Text(stringResource(R.string.creator_account_changed), Modifier.padding(24.dp))
                return@Column
            }
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text(stringResource(R.string.creator_library_scope, state.library?.name.orEmpty()))
                state.launchFailure?.let { failure ->
                    Text(stringResource(when (failure) {
                        JellyfinFailureKind.AUTHENTICATION -> R.string.jellyfin_error_auth_expired_desc
                        JellyfinFailureKind.ACCESS_DENIED -> R.string.jellyfin_error_access_denied_desc
                        else -> R.string.jellyfin_error_server_unreachable_desc
                    }), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = catalogue::retryConnection, enabled = !state.loading && !state.indexing,
                        modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.jellyfin_retry))
                    }
                }
                if (person.id.isNullOrBlank()) Text(stringResource(R.string.creator_name_scope))
                if (state.offline || !state.indexComplete) Text(stringResource(R.string.creator_saved_scope))
                FilterChip(selected = downloadedOnly, onClick = { downloadedOnly = !downloadedOnly },
                    label = { Text(stringResource(R.string.creator_downloaded)) },
                    modifier = Modifier.heightIn(min = 48.dp))
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp), state = grid,
                contentPadding = PaddingValues(20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                mainCatalogueGrid(
                    state = if (downloadedOnly) state.copy(entries = state.entries.filter {
                        it.item.id in downloaded.downloadedItemIds }) else state,
                    columns = 2, selectedSeries = null, downloadedIds = downloaded.downloadedItemIds,
                    onSeries = {}, onBack = onBack, onItem = library::openJellyfinDetail,
                    onCollection = {}, onLibrary = catalogue::chooseLibrary,
                    onRetry = catalogue::retryView,
                    onMore = { catalogue.load(more = true) },
                    loadCover = library::loadJellyfinCover,
                    accountScope = catalogue.positionScope,
                    onLongClick = { entry -> library.captureRemoteIdentity(entry.item)?.let {
                        listSelection = RemoteListSelection(entry, it)
                    } },
                )
            }
        }
    }
}
