package net.digitalbc.leafreader.ui.library

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import net.digitalbc.leafreader.data.jellyfin.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.digitalbc.leafreader.data.db.ComicEntity
import net.digitalbc.leafreader.data.db.ReadingListEntity
import net.digitalbc.leafreader.data.smb.SmbEntry
import net.digitalbc.leafreader.ui.brand.LeafWordmark
import net.digitalbc.leafreader.ui.brand.LeafShape
import net.digitalbc.leafreader.ui.brand.OchreBadge
import net.digitalbc.leafreader.ui.brand.comicShadow
import net.digitalbc.leafreader.ui.brand.halftone
import net.digitalbc.leafreader.ui.theme.Anton
import net.digitalbc.leafreader.ui.theme.Archivo
import net.digitalbc.leafreader.ui.theme.ComicRose
import net.digitalbc.leafreader.ui.theme.Crimson
import net.digitalbc.leafreader.ui.theme.DeepInk
import net.digitalbc.leafreader.ui.theme.Ink
import net.digitalbc.leafreader.ui.theme.LeafAccent
import net.digitalbc.leafreader.ui.theme.LeafPrimary
import net.digitalbc.leafreader.ui.theme.LeafSecondary
import net.digitalbc.leafreader.ui.theme.Ochre
import net.digitalbc.leafreader.ui.theme.PaperWhite
import net.digitalbc.leafreader.ui.theme.PaperWhiteMuted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

private fun formatFileSize(bytes: Long): String = when {
    bytes <= 0L -> ""
    bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f GB", bytes / (1024f * 1024f * 1024f))
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024f)
    else -> "$bytes B"
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenComic: (Long) -> Unit,
    onOpenSeries: (String, String?) -> Unit,
    onOpenReadingList: (Long) -> Unit = {},
    onOpenMenu: () -> Unit,
    onOpenDrive: () -> Unit,
    onOpenJellyfin: () -> Unit = {},
    onConnectJellyfin: () -> Unit = onOpenMenu,
    catalogue: JellyfinCatalogueViewModel? = null,
) {
    val comics by viewModel.comics.collectAsStateWithLifecycle()
    val preparedLocalRows by viewModel.libraryRows.collectAsStateWithLifecycle()
    val libraryItems = preparedLocalRows.items
    val unreadOnly by viewModel.unreadOnly.collectAsStateWithLifecycle()
    val progressConflicts by viewModel.progressConflicts.collectAsStateWithLifecycle()
    val coverSelections by viewModel.coverSelections.collectAsStateWithLifecycle()
    val currentFilter by viewModel.libraryFilter.collectAsStateWithLifecycle()
    var isSearchOpen by rememberSaveable { mutableStateOf(false) }
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val importStatus by viewModel.importStatus.collectAsStateWithLifecycle()
    val importDetail by viewModel.importDetail.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val libraryColumns by viewModel.libraryColumns.collectAsStateWithLifecycle()
    val isEditMode by viewModel.isEditMode.collectAsStateWithLifecycle()
    val selectedComicIds by viewModel.selectedComicIds.collectAsStateWithLifecycle()

    val catalogueState = catalogue?.state?.collectAsStateWithLifecycle()?.value
        ?.takeIf { it.accountScope == catalogue.positionScope }
    val readableDownloads by viewModel.readableComics.collectAsStateWithLifecycle()
    val jellyfinDetail by viewModel.jellyfinDetail.collectAsStateWithLifecycle()
    val jellyfinBrowser by viewModel.jellyfinBrowser.collectAsStateWithLifecycle()
    val requestedDownloads by viewModel.downloadedOnly.collectAsStateWithLifecycle()
    val launchFallback = catalogueState?.downloadedFallback == true
    var remoteSeries by rememberSaveable { mutableStateOf<String?>(null) }
    var collectionRootQuery by rememberSaveable { mutableStateOf("") }
    fun closeRemoteCollection() {
        catalogue?.closeCollection()
        viewModel.setSearchQuery(collectionRootQuery)
    }
    val downloadedOnly = !isSearchOpen && (currentFilter == LibraryFilter.OFFLINE ||
        (requestedDownloads && (remoteSeries != null || catalogueState?.collection != null)))
    LaunchedEffect(launchFallback, catalogueState?.accountScope) {
        if (launchFallback) viewModel.setLibraryFilter(LibraryFilter.OFFLINE)
    }
    var remoteListSelection by remember(catalogueState?.accountScope) { mutableStateOf<RemoteListSelection?>(null) }
    var remoteSelecting by remember(catalogueState?.accountScope) { mutableStateOf(false) }
    var remoteSelected by remember(catalogueState?.accountScope) { mutableStateOf<Map<String, RemoteListSelection>>(emptyMap()) }
    var pendingRemoteRemoval by remember(catalogueState?.accountScope) { mutableStateOf<List<RemoteListSelection>?>(null) }
    val removingRemote by viewModel.removingDownload.collectAsStateWithLifecycle()
    val copyFailure = androidx.compose.ui.res.stringResource(net.digitalbc.leafreader.R.string.compact_copy_failed)
    fun toggleRemote(entry: JellyfinCatalogueEntry) {
        val identity = viewModel.captureRemoteIdentity(entry.item) ?: return
        remoteSelected = if (entry.item.id in remoteSelected) remoteSelected - entry.item.id
            else remoteSelected + (entry.item.id to RemoteListSelection(entry, identity))
    }
    fun removeRemote(items: List<RemoteListSelection>) {
        if (viewModel.confirmDownloadRemoval) pendingRemoteRemoval = items.toList()
        else { viewModel.removeRemoteSelection(items, copyFailure); remoteSelecting = false; remoteSelected = emptyMap() }
    }
    val remoteMode = viewModel.hasJellyfinConfig && catalogue != null &&
        (currentFilter != LibraryFilter.OFFLINE) && !isEditMode
    val downloadedCopies by viewModel.downloadedCopies.collectAsStateWithLifecycle()
    val representedLocalIds = if (remoteMode && (currentFilter == LibraryFilter.SERIES || isSearchOpen))
        catalogueState?.entries.orEmpty().mapNotNull { downloadedCopies[it.item.id]?.id }.toSet() else emptySet()
    val readableIds = (if (downloadedOnly) readableDownloads.map { it.id } else comics.map { it.id }).toSet() - representedLocalIds
    val displayedLocalItems: List<LibraryItem> = when {
        remoteMode && currentFilter != LibraryFilter.READING_LISTS && !downloadedOnly && !isSearchOpen -> emptyList()
        downloadedOnly || (remoteMode && isSearchOpen) -> libraryItems.mapNotNull { item ->
            when (item) {
                is LibraryItem.SingleComic -> item.takeIf { it.comic.id in readableIds }
                is LibraryItem.SeriesStack -> item.issues.filter { it.id in readableIds }.takeIf { it.isNotEmpty() }?.let {
                    item.copy(issues = it, topCoverComic = it.first(), totalIssues = it.size,
                        unreadIssues = it.count { c -> c.isUnread })
                }
                is LibraryItem.ReadingListItem -> item
            }
        }
        else -> libraryItems
    }
    val remotePlan = planRemoteCopies(remoteSelected.values, downloadedCopies)
    val contextSelection = remoteListSelection
    val contextPlan = planRemoteCopies(listOfNotNull(contextSelection), downloadedCopies)
    RemoteListActions(contextSelection, catalogueState?.library?.id, viewModel,
        onDismiss = { remoteListSelection = null },
        canDownload = !importing && contextPlan.downloads.isNotEmpty(),
        canRemove = !importing && !removingRemote && contextPlan.removals.isNotEmpty(),
        onDownload = { contextSelection?.let { viewModel.downloadRemoteSelection(listOf(it), copyFailure) } },
        onRemove = { contextSelection?.let { removeRemote(listOf(it)) } },
        onSelect = { contextSelection?.let { remoteSelecting = true; remoteSelected = mapOf(it.identity.itemId to it) } })
    pendingRemoteRemoval?.let { selected ->
        val plan = planRemoteCopies(selected, downloadedCopies)
        androidx.compose.material3.AlertDialog(onDismissRequest = { pendingRemoteRemoval = null },
            title = { Text(androidx.compose.ui.res.stringResource(net.digitalbc.leafreader.R.string.compact_remove_title)) },
            text = { Text(androidx.compose.ui.res.pluralStringResource(net.digitalbc.leafreader.R.plurals.compact_remove_body,
                plan.removals.size, plan.removals.size, selected.size - plan.removals.size)) },
            confirmButton = {
                androidx.compose.material3.TextButton(enabled = plan.removals.isNotEmpty() && !removingRemote && !importing,
                    onClick = {
                        viewModel.removeRemoteSelection(selected, copyFailure)
                        pendingRemoteRemoval = null; remoteSelecting = false; remoteSelected = emptyMap()
                    }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(androidx.compose.ui.res.stringResource(net.digitalbc.leafreader.R.string.compact_remove))
                }
            }, dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pendingRemoteRemoval = null }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(androidx.compose.ui.res.stringResource(net.digitalbc.leafreader.R.string.compact_cancel))
                }
            })
    }

    LaunchedEffect(catalogue, viewModel.jellyfinBrowsePositionKey(null), currentFilter,
        catalogueState?.library?.id, catalogueState?.launchFailure, catalogueState?.browsingSaved, catalogueState?.collection?.id, searchQuery, downloadedOnly, unreadOnly) {
        if (viewModel.hasJellyfinConfig && catalogue != null) {
            catalogue.ensure()
            viewModel.refreshDownloadedJellyfinItems()
            if (catalogueState?.library != null) {
                val target = if (downloadedOnly && currentFilter !in listOf(LibraryFilter.SERIES, LibraryFilter.READING_LISTS)) JellyfinCatalogueView.DOWNLOADED else when (currentFilter) {
                    LibraryFilter.ALL -> JellyfinCatalogueView.ALL
                    LibraryFilter.SERIES -> JellyfinCatalogueView.SERIES
                    LibraryFilter.READING_LISTS -> JellyfinCatalogueView.LISTS
                    LibraryFilter.OFFLINE -> JellyfinCatalogueView.DOWNLOADED
                }
                if (searchQuery.isNotBlank() && catalogue.state.value.search != searchQuery.trim()) delay(300)
                catalogue.browse(target, searchQuery, unreadOnly)
            }
        }
    }
    val catalogueLifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(catalogueLifecycle, catalogue) {
        viewModel.refreshLibraryPreferences()
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshLibraryPreferences()
                if (viewModel.hasJellyfinConfig) catalogue?.onForeground()
            }
        }
        catalogueLifecycle.lifecycle.addObserver(observer)
        onDispose { catalogueLifecycle.lifecycle.removeObserver(observer) }
    }
    BackHandler(enabled = jellyfinDetail == null && !isSearchOpen && (remoteSeries != null || catalogueState?.collection != null)) {
        if (remoteSeries != null) remoteSeries = null else closeRemoteCollection()
    }

    val snackbarHostState = remember { SnackbarHostState() }
    var pendingDelete by remember { mutableStateOf<ComicEntity?>(null) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showSourceDialog by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var seriesInput by remember(showGroupDialog) { mutableStateOf("") }
    var volumeInput by remember(showGroupDialog) { mutableStateOf("") }
    var showAddToListDialog by remember { mutableStateOf(false) }
    var isCreatingListInDialog by remember { mutableStateOf(false) }
    var newListNameInDialog by remember { mutableStateOf("") }
    var activeReadingList by remember { mutableStateOf<ReadingListEntity?>(null) }
    var showRenameListDialog by remember { mutableStateOf(false) }
    var renameListInput by remember(showRenameListDialog) { mutableStateOf("") }
    var showCreateListDialog by remember { mutableStateOf(false) }
    var createListNameInput by remember(showCreateListDialog) { mutableStateOf("") }
    var showAddComicsToListDialog by remember { mutableStateOf(false) }
    var selectedComicsForList by remember(showAddComicsToListDialog) { mutableStateOf(emptySet<Long>()) }
    var comicOptionsTarget by remember { mutableStateOf<ComicEntity?>(null) }

    var searchReturnFilter by rememberSaveable { mutableStateOf<String?>(null) }
    var searchReturnQuery by rememberSaveable { mutableStateOf("") }
    var gridScrolling by remember { mutableStateOf(false) }
    fun closeSearch() {
        isSearchOpen = false
        searchReturnFilter?.let { viewModel.setLibraryFilter(LibraryFilter.valueOf(it)) }
        viewModel.setSearchQuery(searchReturnQuery)
        searchReturnFilter = null
        catalogue?.endGlobalSearch()
    }
    fun openSearch() {
        searchReturnFilter = currentFilter.name
        searchReturnQuery = searchQuery
        catalogue?.beginGlobalSearch()
        viewModel.setLibraryFilter(LibraryFilter.ALL)
        viewModel.clearSearch()
        isSearchOpen = true
    }
    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) {
            catalogue?.beginGlobalSearch()
            viewModel.setLibraryFilter(LibraryFilter.ALL)
        }
    }
    val unreadFocus = remember { FocusRequester() }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var menuExpanded by remember { mutableStateOf(false) }
    val menuFocus = remember { FocusRequester() }
    var continuingRemote by rememberSaveable { mutableStateOf<String?>(null) }
    var continueDownloadStarted by rememberSaveable { mutableStateOf(false) }
    var missingContinueIds by remember(catalogueState?.accountScope) { mutableStateOf(emptySet<String>()) }
    var missingContinueCopies by remember { mutableStateOf(emptySet<Long>()) }
    var checkingContinue by remember { mutableStateOf<Long?>(null) }
    var continueLocalJob by remember { mutableStateOf<Job?>(null) }
    val continueScope = rememberCoroutineScope()
    val unreadableContinueMessage = androidx.compose.ui.res.stringResource(net.digitalbc.leafreader.R.string.library_continue_unreadable)
    val emptyLibrary = comics.isEmpty() && !viewModel.hasJellyfinConfig &&
        searchQuery.isBlank() && !isSearchOpen && currentFilter !in listOf(LibraryFilter.READING_LISTS, LibraryFilter.OFFLINE) && !isEditMode
    val continueRemoteReachable = currentFilter != LibraryFilter.OFFLINE && catalogueState?.canContinueRemote == true
    val continueEntries = catalogueState?.indexedEntries.orEmpty()
    val projectedContinue by androidx.compose.runtime.key(catalogueState?.accountScope, catalogueState?.library?.id) {
        produceState<List<ContinueReading>>(emptyList(), readableDownloads, downloadedCopies,
            continueEntries, continueRemoteReachable, missingContinueIds, missingContinueCopies) {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                continueReading(readableDownloads.filterNot { it.id in missingContinueCopies }, continueEntries,
                    downloadedCopies, continueRemoteReachable, missingContinueIds)
            }
        }
    }
    // Apply safety removals immediately while the background projection catches up.
    val continueCandidates = projectedContinue.filter { candidate ->
        candidate.local?.let { it.id !in missingContinueCopies } ?: (continueRemoteReachable &&
            candidate.remote?.item?.id !in missingContinueIds)
    }
    val continueFailureMessage = androidx.compose.ui.res.stringResource(net.digitalbc.leafreader.R.string.library_continue_failed)
    val currentContinueIntent by rememberUpdatedState(continuingRemote)
    DisposableEffect(viewModel) {
        onDispose {
            // Keep the requested copy, but never replay a read intent after leaving Library.
            if (currentContinueIntent != null) viewModel.closeJellyfinDetail()
        }
    }
    LaunchedEffect(jellyfinDetail, continuingRemote, importing, isEditMode, isSearchOpen) {
        if (continuingRemote != null) {
            if (isEditMode || isSearchOpen) {
                continuingRemote = null
                viewModel.closeJellyfinDetail()
                return@LaunchedEffect
            }
            val detail = jellyfinDetail
            val ready = viewModel.consumeJellyfinRead()
            when {
                ready != null -> { continuingRemote = null; viewModel.closeJellyfinDetail(); onOpenComic(ready) }
                detail?.failure != null -> {
                    if (detail.failure == net.digitalbc.leafreader.data.jellyfin.JellyfinFailureKind.NOT_FOUND)
                        missingContinueIds = missingContinueIds + continuingRemote!!
                    continuingRemote = null
                    viewModel.closeJellyfinDetail()
                    viewModel.showMessage(continueFailureMessage)
                }
                detail?.details != null && !continueDownloadStarted -> {
                    continueDownloadStarted = true
                    viewModel.downloadJellyfinDetail(openWhenReady = true)
                }
                detail == null -> continuingRemote = null
                detail.localCopyFailure && !detail.checkingLocal -> {
                    continuingRemote = null; viewModel.closeJellyfinDetail()
                }
            }
        }
    }
    var isSearchFocused by remember { mutableStateOf(false) }

    BackHandler(enabled = remoteSelecting) { remoteSelecting = false; remoteSelected = emptyMap() }
    BackHandler(enabled = isEditMode) {
        viewModel.setEditMode(false)
    }

    BackHandler(enabled = isSearchOpen && !isEditMode) {
        closeSearch()
        focusManager.clearFocus()
    }

    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp.dp
    val availableColumns = remember(screenWidthDp) {
        when {
            screenWidthDp >= 600.dp -> listOf(2, 3, 4, 6)
            screenWidthDp >= 380.dp -> listOf(2, 3, 4)
            else -> listOf(2, 3)
        }
    }
    // Keep the saved grid preference; larger text limits the rendered density for readable cards.
    val maximumColumns = if (configuration.fontScale > 1.3f)
        minOf(availableColumns.last(), maxOf(1, (configuration.screenWidthDp / (160f * configuration.fontScale)).toInt()))
        else availableColumns.last()
    val actualColumns = libraryColumns.coerceIn(if (configuration.fontScale > 1.3f) 1 else 2, maximumColumns)
    val gridPositions = rememberSaveableStateHolder()

    val picker = rememberComicFilePicker { uris ->
        if (!uris.isNullOrEmpty()) viewModel.importComics(uris)
    }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { treeUri ->
        if (treeUri != null) viewModel.importFolder(treeUri)
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val horizontalSpacing = if (actualColumns >= 4) 12.dp else 16.dp
    val verticalSpacing = if (actualColumns >= 4) 14.dp else 18.dp

    if (jellyfinDetail != null && continuingRemote == null) {
        JellyfinBrowserScreen(viewModel, onBack = { viewModel.closeJellyfinDetail() },
            onOpenComic = onOpenComic, onOpenLocal = { viewModel.closeJellyfinDetail() },
            onOpenSettings = onOpenMenu)
        return
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(
            color = LeafPrimary,
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 3.dp,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 10.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (configuration.fontScale > 1.3f) {
                        Image(androidx.compose.ui.res.painterResource(net.digitalbc.leafreader.R.drawable.ic_leaf_logo),
                            contentDescription = "Leaf", modifier = Modifier.size(36.dp))
                    } else LeafWordmark()
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (!isEditMode && !remoteSelecting) {
                            UnreadOnlyControl(unreadOnly, { viewModel.unreadOnly.value = it },
                                Modifier.focusRequester(unreadFocus), onToolbar = true)
                        }
                        Box(
                            Modifier
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = if (isSearchOpen) "Close search" else "Open search",
                                    enabled = !remoteSelecting && !isEditMode,
                                ) {
                                    if (isSearchOpen) { closeSearch(); focusManager.clearFocus() }
                                    else openSearch()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSearchOpen) Ochre else PaperWhite.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = if (isSearchOpen) Ink else PaperWhite.copy(alpha = if (remoteSelecting || isEditMode) 0.38f else 1f),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Box(
                            Modifier
                                .focusRequester(menuFocus)
                                .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                .clickable(
                                    role = Role.Button,
                                    onClickLabel = "Open menu",
                                    onClick = { menuExpanded = true },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(PaperWhite.copy(alpha = 0.14f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Menu,
                                    contentDescription = "Menu",
                                    tint = PaperWhite,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                            androidx.compose.material3.DropdownMenu(expanded = menuExpanded, modifier = Modifier.width(250.dp),
                                shape = RoundedCornerShape(14.dp), containerColor = MaterialTheme.colorScheme.surfaceVariant, onDismissRequest = {
                                menuExpanded = false; menuFocus.requestFocus()
                            }) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(androidx.compose.ui.res.stringResource(net.digitalbc.leafreader.R.string.library_select_comics)) },
                                    leadingIcon = { Icon(Icons.Default.CheckCircle, null) },
                                    enabled = if (remoteMode) catalogueState?.entries?.isNotEmpty() == true else comics.isNotEmpty(),
                                    onClick = {
                                        menuExpanded = false
                                        if (remoteMode) remoteSelecting = true else viewModel.setEditMode(true)
                                    })
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(androidx.compose.ui.res.stringResource(net.digitalbc.leafreader.R.string.library_add_comics)) },
                                    leadingIcon = { Icon(Icons.Default.Add, null) },
                                    enabled = !importing,
                                    onClick = { menuExpanded = false; showSourceDialog = true })
                                androidx.compose.material3.HorizontalDivider()
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(androidx.compose.ui.res.stringResource(net.digitalbc.leafreader.R.string.library_settings_more)) },
                                    leadingIcon = { Icon(Icons.Default.Settings, null) },
                                    onClick = { menuExpanded = false; onOpenMenu() })
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isSearchOpen,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    // Detail navigation can retain open search without composing its field.
                    LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 4.dp)
                            .comicShadow(offset = 3.dp, color = Color(0xB3000000))
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(
                                width = 2.dp,
                                color = if (isSearchFocused) MaterialTheme.colorScheme.tertiary else DeepInk,
                                shape = RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = if (isSearchFocused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester)
                                    .onFocusChanged { isSearchFocused = it.isFocused },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontFamily = Archivo,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.tertiary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            "SEARCH COMICS BY TITLE…",
                                            fontFamily = Archivo,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                        )
                                    }
                                    innerTextField()
                                },
                            )
                            if (searchQuery.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                                        .clickable { viewModel.clearSearch() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!isEditMode && !isSearchOpen && !remoteSelecting) {
            LibraryTabs(currentFilter) { filter ->
                remoteSeries = null
                if (filter != LibraryFilter.OFFLINE) catalogue?.browseSavedCatalogue()
                viewModel.setLibraryFilter(filter)
            }
            if (unreadOnly && !emptyLibrary) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalArrangement = Arrangement.End) {
                    UnreadOnlyChip {
                        viewModel.unreadOnly.value = false
                        unreadFocus.requestFocus()
                    }
                }
            }
        }

        androidx.compose.material3.pulltorefresh.PullToRefreshBox(
            isRefreshing = catalogueState?.refreshing == true,
            onRefresh = { if (viewModel.hasJellyfinConfig) catalogue?.refresh() },
            modifier = Modifier.fillMaxWidth().weight(1f),
        ) {
            Box(Modifier.matchParentSize().halftone(LeafSecondary, alpha = 0.05f))

            val gridKey = listOf(viewModel.jellyfinBrowsePositionKey(null),
                if (remoteMode) catalogueState?.library?.id.orEmpty() else "", currentFilter.name,
                searchQuery, downloadedOnly.toString(), unreadOnly.toString(), remoteSeries.orEmpty(), catalogueState?.collection?.id.orEmpty())
                .joinToString(":") { "${it.length}:$it" }
            val targetView = when (currentFilter) {
                LibraryFilter.ALL -> JellyfinCatalogueView.ALL
                LibraryFilter.SERIES -> JellyfinCatalogueView.SERIES
                LibraryFilter.READING_LISTS -> JellyfinCatalogueView.LISTS
                LibraryFilter.OFFLINE -> JellyfinCatalogueView.DOWNLOADED
            }
            val localRowsReady = preparedLocalRows.filter == currentFilter &&
                preparedLocalRows.query == searchQuery && preparedLocalRows.unreadOnly == unreadOnly
            val remoteRowsReady = catalogueState != null &&
                (catalogueState.loaded || catalogueState.error != null || catalogueState.launchFailure != null) &&
                (catalogueState.library == null || (catalogueState.view == targetView &&
                    catalogueState.search == searchQuery.trim() && catalogueState.unreadOnly == unreadOnly))
            val rowsReady = if (remoteMode) remoteRowsReady &&
                ((currentFilter != LibraryFilter.READING_LISTS && !downloadedOnly && !isSearchOpen) || localRowsReady)
                else localRowsReady
            if (emptyLibrary) {
                LibraryEmpty(onAdd = { showSourceDialog = true }, onConnect = onConnectJellyfin, importing = importing)
            } else if (rowsReady || (!isSearchOpen && !isEditMode && continueCandidates.isNotEmpty())) {
            gridPositions.SaveableStateProvider(gridKey) {
            // One constraint read for the screen, never one subcomposition per cover.
            BoxWithConstraints(Modifier.fillMaxSize()) {
            val gridWidth = this.maxWidth
            val remoteCoverWidthPx = with(androidx.compose.ui.platform.LocalDensity.current) {
                net.digitalbc.leafreader.data.library.CoverImageDecoder.widthBucket(
                    ((gridWidth - 36.dp - horizontalSpacing * (actualColumns - 1)) / actualColumns).roundToPx())
            }
            val gridState = rememberLazyGridState()
            LaunchedEffect(remoteSelecting) {
                if (remoteSelecting) gridState.scrollToItem(0)
            }
            LaunchedEffect(gridState) {
                androidx.compose.runtime.snapshotFlow { gridState.isScrollInProgress }
                    .collect { gridScrolling = it }
            }
            DisposableEffect(gridState) { onDispose { gridScrolling = false } }
            LaunchedEffect(gridState, catalogueState?.library?.id, catalogueState?.collection?.id,
                catalogueState?.search, catalogueState?.entries?.size, catalogueState?.loading, catalogueState?.error) {
                if (remoteMode && !downloadedOnly && catalogueState?.hasMore == true &&
                    !catalogueState.loading && catalogueState.error == null) {
                    androidx.compose.runtime.snapshotFlow {
                        val info = gridState.layoutInfo
                        (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >= info.totalItemsCount - 8
                    }.distinctUntilChanged().collect { nearEnd -> if (nearEnd) catalogue?.load(more = true) }
                }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(actualColumns),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = if (gridWidth >= 600.dp) 32.dp else 18.dp, end = if (gridWidth >= 600.dp) 32.dp else 18.dp, top = 12.dp,
                    bottom = if (isEditMode || importing) 120.dp else 96.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
                verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            ) {
                if (remoteSelecting) {
                    item(key = "remote-selection", span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            Text(androidx.compose.ui.res.pluralStringResource(net.digitalbc.leafreader.R.plurals.compact_selected,
                                remoteSelected.size, remoteSelected.size), style = MaterialTheme.typography.titleMedium)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                androidx.compose.material3.TextButton(enabled = remotePlan.downloads.isNotEmpty() && !importing && !removingRemote,
                                    onClick = {
                                        viewModel.downloadRemoteSelection(remoteSelected.values.toList(), copyFailure)
                                        remoteSelected = emptyMap(); remoteSelecting = false
                                    }, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text(androidx.compose.ui.res.pluralStringResource(net.digitalbc.leafreader.R.plurals.compact_download_count,
                                        remotePlan.downloads.size, remotePlan.downloads.size))
                                }
                                androidx.compose.material3.TextButton(enabled = remotePlan.removals.isNotEmpty() && !importing && !removingRemote,
                                    onClick = { removeRemote(remoteSelected.values.toList()) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text(androidx.compose.ui.res.pluralStringResource(net.digitalbc.leafreader.R.plurals.compact_remove_count,
                                        remotePlan.removals.size, remotePlan.removals.size))
                                }
                                androidx.compose.material3.TextButton(onClick = { remoteSelecting = false; remoteSelected = emptyMap() },
                                    modifier = Modifier.heightIn(min = 48.dp)) {
                                    Text(androidx.compose.ui.res.stringResource(net.digitalbc.leafreader.R.string.compact_done))
                                }
                            }
                        }
                    }
                }
                if (isEditMode) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OchreBadge("${selectedComicIds.size} SELECTED")
                            val allSelected = preparedLocalRows.selectableComicIds.isNotEmpty() &&
                                preparedLocalRows.selectableComicIds.all { it in selectedComicIds }
                            androidx.compose.material3.TextButton(onClick = {
                                if (allSelected) viewModel.clearComicSelection()
                                else viewModel.selectAllComics(preparedLocalRows.selectableComicIds.toList())
                            }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text(androidx.compose.ui.res.stringResource(if (allSelected) net.digitalbc.leafreader.R.string.library_deselect_all else net.digitalbc.leafreader.R.string.library_select_all)) }
                            androidx.compose.material3.TextButton(onClick = { viewModel.setEditMode(false) }, colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text(androidx.compose.ui.res.stringResource(net.digitalbc.leafreader.R.string.library_done)) }
                        }
                    }
                }

                if (progressConflicts.isNotEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        androidx.compose.material3.TextButton(onClick = {
                            viewModel.reviewProgressConflicts()
                        }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text(androidx.compose.ui.res.stringResource(net.digitalbc.leafreader.R.string.sync_review_positions))
                        }
                    }
                }

                catalogueState?.launchFailure?.let { failure ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        JellyfinLaunchNotice(failure = failure, retrying = catalogueState.loading || catalogueState.indexing,
                            showingDownloads = downloadedOnly,
                            onRetry = { catalogue?.retryConnection() }, onSettings = onOpenMenu)
                    }
                }
                if (!isEditMode && !remoteSelecting && !isSearchOpen && continueCandidates.isNotEmpty()) {
                    item(key = "continue-reading", span = { GridItemSpan(maxLineSpan) }) {
                        LibraryContinue(continueCandidates, continuingRemote?.let { "remote:$it" } ?: checkingContinue?.let { "local:$it" }, importProgress,
                            onContinue = { candidate ->
                                if (!importing && continuingRemote == null && checkingContinue == null) {
                                    candidate.local?.let { comic ->
                                        checkingContinue = comic.id
                                        continueLocalJob = continueScope.launch {
                                            try {
                                                if (viewModel.readableContinueComic(comic.id) != null) onOpenComic(comic.id)
                                                else {
                                                    missingContinueCopies = missingContinueCopies + comic.id
                                                    viewModel.showMessage(unreadableContinueMessage)
                                                }
                                            } finally { checkingContinue = null }
                                        }
                                    }
                                    candidate.remote?.let {
                                        continuingRemote = it.item.id
                                        continueDownloadStarted = false
                                        viewModel.openJellyfinDetail(it.item)
                                    }
                                }
                            }, onCancel = {
                                continueLocalJob?.cancel(); checkingContinue = null
                                if (continuingRemote != null) {
                                    viewModel.cancelImport(); viewModel.closeJellyfinDetail(); continuingRemote = null
                                }
                            }, loadCover = { item, width -> viewModel.loadJellyfinCover(item, width) })
                    }
                }
                val showDownloadedFilter = !isSearchOpen && (remoteSeries != null || catalogueState?.collection != null)
                val showSearchContext = remoteMode && searchQuery.isNotBlank()
                if (showDownloadedFilter || showSearchContext) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (showDownloadedFilter) androidx.compose.material3.FilterChip(
                                selected = downloadedOnly,
                                onClick = { viewModel.downloadedOnly.value = !requestedDownloads },
                                label = { Text("Downloaded") }, modifier = Modifier.heightIn(min = 48.dp))
                            if (showSearchContext) Text(
                                catalogueState?.library?.name?.let { "Search · $it" } ?: "Jellyfin",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp))
                        }
                    }
                }
                if (rowsReady && remoteMode && catalogueState != null) {
                    mainCatalogueGrid(
                        state = if (downloadedOnly && (catalogueState.view != JellyfinCatalogueView.LISTS || catalogueState.collection != null)) catalogueState.copy(
                            entries = catalogueState.entries.filter { it.type.equals("BoxSet", true) || it.item.id in jellyfinBrowser.downloadedItemIds },
                            seriesGroups = catalogueState.downloadedSeriesGroups,
                            hasMore = false, loading = false) else catalogueState,
                        columns = actualColumns,
                        accountScope = catalogue?.positionScope.orEmpty(),
                        onLongClick = { entry ->
                            if (remoteSelecting) toggleRemote(entry)
                            else viewModel.captureRemoteIdentity(entry.item)?.let { remoteListSelection = RemoteListSelection(entry, it) }
                        },
                        selectedSeries = remoteSeries.takeUnless { isSearchOpen },
                        downloadedIds = jellyfinBrowser.downloadedItemIds,
                        onSeries = { remoteSeries = it },
                        onBack = { if (remoteSeries != null) remoteSeries = null else closeRemoteCollection() },
                        onItem = { viewModel.openJellyfinDetail(it) },
                        onCollection = {
                            collectionRootQuery = searchQuery
                            viewModel.setSearchQuery("")
                            catalogue?.openCollection(it)
                        },
                        onLibrary = { catalogue?.chooseLibrary(it) },
                        onRetry = { catalogue?.retryView() },
                        onMore = { catalogue?.load(more = true) },
                        loadCover = { viewModel.loadJellyfinCover(it, remoteCoverWidthPx) },
                        coverWidthPx = remoteCoverWidthPx,
                        coverSelections = coverSelections,
                        onCoverSelected = viewModel::rememberCoverSelection,
                        selecting = remoteSelecting, selectedIds = remoteSelected.keys,
                        onToggleSelection = ::toggleRemote,
                        showEmpty = displayedLocalItems.isEmpty(),
                    )
                }

                if (rowsReady && displayedLocalItems.isEmpty() && !remoteMode) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        if (unreadOnly) {
                            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(androidx.compose.ui.res.stringResource(net.digitalbc.leafreader.R.string.library_no_unread))
                                androidx.compose.material3.TextButton(onClick = { viewModel.unreadOnly.value = false }) {
                                    Text(androidx.compose.ui.res.stringResource(net.digitalbc.leafreader.R.string.library_clear_unread))
                                }
                            }
                        } else if (searchQuery.isNotBlank()) {
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    "NO MATCHES",
                                    fontFamily = Anton,
                                    fontSize = 22.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    "No items found matching \"$searchQuery\"",
                                    fontFamily = Archivo,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Box(
                                    Modifier
                                        .padding(top = 6.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Ochre)
                                        .clickable { viewModel.clearSearch() }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                ) {
                                    Text(
                                        "CLEAR SEARCH",
                                        fontFamily = Anton,
                                        fontSize = 12.sp,
                                        color = Ink,
                                    )
                                }
                            }
                        } else {
                            Text(
                                if (downloadedOnly) androidx.compose.ui.res.stringResource(net.digitalbc.leafreader.R.string.library_offline_empty)
                                else "No comics yet. Tap LOAD COMIC to add a CBZ, CBR, or PDF.",
                                fontFamily = Archivo,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                }

                if (remoteMode && currentFilter == LibraryFilter.READING_LISTS) {
                    item(span = { GridItemSpan(maxLineSpan) }) { Text("Leaf lists") }
                }
                items(if (rowsReady) displayedLocalItems else emptyList(), key = { item ->
                    when (item) {
                        is LibraryItem.SingleComic -> "comic-${item.comic.id}"
                        is LibraryItem.SeriesStack -> "series-${item.seriesName}-${item.volume.orEmpty()}"
                        is LibraryItem.ReadingListItem -> "list-${item.readingList.id}"
                    }
                }) { item ->
                    when (item) {
                        is LibraryItem.SingleComic -> {
                            ComicCard(
                                comic = item.comic,
                                columns = actualColumns,
                                isEditMode = isEditMode,
                                isSelected = item.comic.id in selectedComicIds,
                                onClick = {
                                    if (isEditMode) {
                                        viewModel.toggleComicSelection(item.comic.id)
                                    } else {
                                        onOpenComic(item.comic.id)
                                    }
                                },
                                onLongClick = {
                                    if (isEditMode) {
                                        viewModel.toggleComicSelection(item.comic.id)
                                    } else {
                                        viewModel.setEditMode(true)
                                        viewModel.toggleComicSelection(item.comic.id)
                                    }
                                },
                            )
                        }
                        is LibraryItem.SeriesStack -> {
                            val coverKey = "local:${item.seriesName.length}:${item.seriesName}:${item.volume.orEmpty()}"
                            ComicStackCard(
                                stack = item,
                                columns = actualColumns,
                                selectedCoverId = coverSelections[coverKey],
                                onCoverSelected = { viewModel.rememberCoverSelection(coverKey, it) },
                                onClick = {
                                    onOpenSeries(item.seriesName, item.volume)
                                },
                            )
                        }
                        is LibraryItem.ReadingListItem -> {
                            ReadingListCard(
                                item = item,
                                columns = actualColumns,
                                onClick = {
                                    onOpenReadingList(item.readingList.id)
                                },
                            )
                        }
                    }
                }

                val isListsFilter = currentFilter == LibraryFilter.READING_LISTS

                if (rowsReady && isListsFilter && searchQuery.isBlank() && !isEditMode && !unreadOnly) {
                    item {
                        AddTile(
                            importing = importing,
                            progress = importProgress,
                            status = importStatus,
                            detail = importDetail,
                            columns = actualColumns,
                            isListsFilter = isListsFilter,
                            onClick = {
                                if (!importing) {
                                    if (isListsFilter) showCreateListDialog = true
                                    else showSourceDialog = true
                                }
                            },
                            onCancel = { viewModel.cancelImport() },
                        )
                    }
                }
            }
            }
            }

            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    androidx.compose.material3.CircularProgressIndicator()
                }
            }


            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ImportProgressBanner(
                        importing = importing,
                        progress = importProgress,
                        status = importStatus,
                        detail = importDetail,
                        onCancel = { viewModel.cancelImport() },
                    )

                    AnimatedVisibility(
                        visible = isEditMode && selectedComicIds.isNotEmpty(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .comicShadow(offset = 3.dp, color = Color(0xB3000000))
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(2.dp, Ochre, RoundedCornerShape(6.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (configuration.fontScale <= 1.3f) Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "${selectedComicIds.size} ${if (selectedComicIds.size == 1) "COMIC" else "COMICS"} SELECTED",
                                        fontFamily = Anton,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = "Group into series, add to list, or delete",
                                        fontFamily = Archivo,
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Ochre)
                                            .clickable { showGroupDialog = true }
                                            .padding(horizontal = 9.dp, vertical = 9.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "SET SERIES",
                                            fontFamily = Anton,
                                            fontSize = 11.sp,
                                            color = Ink,
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(LeafAccent)
                                            .clickable { showAddToListDialog = true }
                                            .padding(horizontal = 9.dp, vertical = 9.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "ADD TO LIST",
                                            fontFamily = Anton,
                                            fontSize = 11.sp,
                                            color = DeepInk,
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(ComicRose)
                                            .clickable { showBatchDeleteDialog = true }
                                            .padding(horizontal = 9.dp, vertical = 9.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = "DELETE",
                                            fontFamily = Anton,
                                            fontSize = 11.sp,
                                            color = Color.White,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!isEditMode && !remoteSelecting && !emptyLibrary && !gridScrolling && !importing && continuingRemote == null && checkingContinue == null) {
                androidx.compose.material3.FloatingActionButton(
                    onClick = {
                        if (currentFilter == LibraryFilter.READING_LISTS && !isSearchOpen) showCreateListDialog = true
                        else showSourceDialog = true
                    }, containerColor = Ochre, contentColor = Ink,
                    modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp),
                ) {
                    Icon(Icons.Default.Add, androidx.compose.ui.res.stringResource(
                        if (currentFilter == LibraryFilter.READING_LISTS && !isSearchOpen)
                            net.digitalbc.leafreader.R.string.library_create_reading_list else net.digitalbc.leafreader.R.string.library_add_comics))
                }
            }

            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
            )
        }

    }

    if (showSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = { Text("Load comic", fontFamily = Anton, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(LeafPrimary)
                            .clickable {
                                showSourceDialog = false
                                picker.launch(comicFileMimeTypes)
                            }
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text("FROM DEVICE (FILES)", fontFamily = Anton, fontSize = 14.sp, color = PaperWhite)
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.5.dp, LeafSecondary, RoundedCornerShape(4.dp))
                            .clickable {
                                showSourceDialog = false
                                folderPicker.launch(null)
                            }
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text("FROM DEVICE (FOLDER)", fontFamily = Anton, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(LeafAccent)
                            .clickable {
                                showSourceDialog = false
                                if (viewModel.hasSmbConfig) {
                                    onOpenDrive()
                                } else {
                                    viewModel.showMessage("Configure your SMB drive in Menu first.")
                                    onOpenMenu()
                                }
                            }
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text("FROM SMB DRIVE", fontFamily = Anton, fontSize = 14.sp, color = DeepInk)
                    }

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(LeafAccent)
                            .clickable {
                                showSourceDialog = false
                                if (viewModel.hasJellyfinConfig) {
                                    viewModel.downloadedOnly.value = false
                                    remoteSeries = null
                                    viewModel.setLibraryFilter(LibraryFilter.ALL)
                                    onOpenJellyfin()
                                } else {
                                    onConnectJellyfin()
                                }
                            }
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text("FROM JELLYFIN", fontFamily = Anton, fontSize = 14.sp, color = DeepInk)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSourceDialog = false }) { Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }

    if (showGroupDialog) {
        AlertDialog(
            onDismissRequest = { showGroupDialog = false },
            title = { Text("Set Series Name", fontFamily = Anton, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Comics with the same Series Name automatically group into a Series Stack in your library.",
                        fontFamily = Archivo,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = seriesInput,
                        onValueChange = { seriesInput = it },
                        label = { Text("Series Name (e.g. Deadpool)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.tertiary,
                            unfocusedBorderColor = DeepInk,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = volumeInput,
                        onValueChange = { volumeInput = it },
                        label = { Text("Volume (Optional, e.g. Vol. 2)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.tertiary,
                            unfocusedBorderColor = DeepInk,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateComicsSeries(selectedComicIds.toList(), seriesInput, volumeInput)
                    showGroupDialog = false
                }) {
                    Text("SAVE & GROUP", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGroupDialog = false }) { Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }

    if (showAddToListDialog) {
        val readingLists by viewModel.readingLists.collectAsStateWithLifecycle()

        AlertDialog(
            onDismissRequest = {
                showAddToListDialog = false
                isCreatingListInDialog = false
                newListNameInDialog = ""
            },
            title = {
                Text(
                    text = if (isCreatingListInDialog) "Create New Reading List" else "Add to Reading List",
                    fontFamily = Anton,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isCreatingListInDialog) {
                        Text(
                            text = "Enter a name for your new reading list.",
                            fontFamily = Archivo,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = newListNameInDialog,
                            onValueChange = { newListNameInDialog = it },
                            label = { Text("List Name (e.g. Favorites)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.tertiary,
                                unfocusedBorderColor = DeepInk,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            text = "Choose a reading list to add ${selectedComicIds.size} selected ${if (selectedComicIds.size == 1) "comic" else "comics"}.",
                            fontFamily = Archivo,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(LeafAccent)
                                .clickable { isCreatingListInDialog = true }
                                .padding(vertical = 10.dp, horizontal = 14.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = DeepInk, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("+ CREATE NEW LIST", fontFamily = Anton, fontSize = 13.sp, color = DeepInk)
                        }

                        if (readingLists.isEmpty()) {
                            Text(
                                text = "No reading lists created yet. Tap above to create one!",
                                fontFamily = Archivo,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.heightIn(max = 240.dp),
                            ) {
                                items(readingLists, key = { it.id }) { list ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.surface)
                                            .border(1.dp, Ochre, RoundedCornerShape(4.dp))
                                            .clickable {
                                                viewModel.addComicsToReadingList(list.id, selectedComicIds.toList())
                                                showAddToListDialog = false
                                            }
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = list.name.uppercase(),
                                            fontFamily = Anton,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Select",
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (isCreatingListInDialog) {
                    TextButton(onClick = {
                        if (newListNameInDialog.isNotBlank()) {
                            viewModel.createReadingList(newListNameInDialog) { newListId ->
                                viewModel.addComicsToReadingList(newListId, selectedComicIds.toList())
                            }
                            showAddToListDialog = false
                            isCreatingListInDialog = false
                            newListNameInDialog = ""
                        }
                    }) {
                        Text("CREATE & ADD", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (isCreatingListInDialog) {
                        isCreatingListInDialog = false
                    } else {
                        showAddToListDialog = false
                    }
                }) {
                    Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }

    if (showCreateListDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateListDialog = false
                createListNameInput = ""
            },
            title = {
                Text("Create Reading List", fontFamily = Anton, color = MaterialTheme.colorScheme.onSurface)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter a name for your new reading list.",
                        fontFamily = Archivo,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = createListNameInput,
                        onValueChange = { createListNameInput = it },
                        label = { Text("List Name (e.g. Favorites)", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.tertiary,
                            unfocusedBorderColor = DeepInk,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        if (createListNameInput.isNotBlank()) {
                            val listName = createListNameInput.trim()
                            viewModel.createReadingList(listName) { newListId ->
                                onOpenReadingList(newListId)
                            }
                            showCreateListDialog = false
                            createListNameInput = ""
                        }
                    }) {
                        Text("CREATE & OPEN", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateListDialog = false
                    createListNameInput = ""
                }) {
                    Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }

    activeReadingList?.let { list ->
        val comicsInList by viewModel.observeComicsForList(list.id).collectAsStateWithLifecycle(emptyList())

        AlertDialog(
            onDismissRequest = { activeReadingList = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = list.name.uppercase(), fontFamily = Anton, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(LeafAccent)
                                .clickable { showAddComicsToListDialog = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = DeepInk, modifier = Modifier.size(12.dp))
                                Text("ADD COMICS", fontFamily = Anton, fontSize = 10.5.sp, color = DeepInk)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, Ink, RoundedCornerShape(3.dp))
                                .clickable {
                                    renameListInput = list.name
                                    showRenameListDialog = true
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text("RENAME", fontFamily = Anton, fontSize = 10.5.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(ComicRose)
                                .clickable {
                                    viewModel.deleteReadingList(list.id)
                                    activeReadingList = null
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text("DELETE", fontFamily = Anton, fontSize = 10.5.sp, color = Color.White)
                        }
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${comicsInList.size} ${if (comicsInList.size == 1) "COMIC" else "COMICS"} IN LIST",
                            fontFamily = Archivo,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(LeafAccent)
                            .clickable { showAddComicsToListDialog = true }
                            .padding(vertical = 10.dp, horizontal = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = DeepInk, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("+ ADD COMICS TO LIST", fontFamily = Anton, fontSize = 13.sp, color = DeepInk)
                    }

                    if (comicsInList.isEmpty()) {
                        Text(
                            text = "This reading list is empty. Tap '+ ADD COMICS TO LIST' above or select comics in Edit mode.",
                            fontFamily = Archivo,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 320.dp),
                        ) {
                            items(comicsInList, key = { it.id }) { comic ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.surface)
                                        .border(1.dp, LeafSecondary.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                        .clickable {
                                            activeReadingList = null
                                            onOpenComic(comic.id)
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp, 50.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                    ) {
                                        val cover = rememberCover(comic.coverPath)
                                        if (cover != null) {
                                            Image(cover, comic.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        }
                                    }
                                    Text(
                                        text = comic.title.uppercase(),
                                        fontFamily = Archivo,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                                            .clickable {
                                                viewModel.removeComicFromReadingList(list.id, comic.id)
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove from list",
                                            tint = ComicRose,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeReadingList = null }) { Text("CLOSE", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }

    if (showRenameListDialog && activeReadingList != null) {
        val list = activeReadingList!!
        AlertDialog(
            onDismissRequest = { showRenameListDialog = false },
            title = { Text("Rename Reading List", fontFamily = Anton, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                OutlinedTextField(
                    value = renameListInput,
                    onValueChange = { renameListInput = it },
                    label = { Text("List Name", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.tertiary,
                        unfocusedBorderColor = DeepInk,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameListInput.isNotBlank()) {
                        viewModel.renameReadingList(list.id, renameListInput)
                        activeReadingList = list.copy(name = renameListInput.trim())
                        showRenameListDialog = false
                    }
                }) {
                    Text("SAVE", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameListDialog = false }) { Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }

    if (showAddComicsToListDialog && activeReadingList != null) {
        val list = activeReadingList!!
        val comicsInList by viewModel.observeComicsForList(list.id).collectAsStateWithLifecycle(emptyList())
        val availableComicsForList = remember(comics, comicsInList) {
            val listComicIds = comicsInList.map { it.id }.toSet()
            comics.filter { it.id !in listComicIds }
        }

        AlertDialog(
            onDismissRequest = {
                showAddComicsToListDialog = false
                selectedComicsForList = emptySet()
            },
            title = { Text("Add Comics to ${list.name}", fontFamily = Anton, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Select downloaded comics from your library to add to this list.",
                        fontFamily = Archivo,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (availableComicsForList.isEmpty()) {
                        Text(
                            "All downloaded comics in your library are already in this list.",
                            fontFamily = Archivo,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.heightIn(max = 280.dp),
                        ) {
                            items(availableComicsForList, key = { it.id }) { comic ->
                                val isSelected = comic.id in selectedComicsForList
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
                                        .border(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) MaterialTheme.colorScheme.tertiary else LeafSecondary.copy(alpha = 0.4f),
                                            shape = RoundedCornerShape(4.dp),
                                        )
                                        .clickable {
                                            selectedComicsForList = if (isSelected) selectedComicsForList - comic.id else selectedComicsForList + comic.id
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp, 50.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                    ) {
                                        val cover = rememberCover(comic.coverPath)
                                        if (cover != null) {
                                            Image(cover, comic.title, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        }
                                    }
                                    Text(
                                        text = comic.title.uppercase(),
                                        fontFamily = Archivo,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.5.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(if (isSelected) Ochre else MaterialTheme.colorScheme.surface)
                                            .border(1.5.dp, if (isSelected) Ochre else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(3.dp)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (isSelected) {
                                            Icon(Icons.Default.Check, contentDescription = "Selected", tint = Ink, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (selectedComicsForList.isNotEmpty()) {
                    TextButton(onClick = {
                        viewModel.addComicsToReadingList(list.id, selectedComicsForList.toList())
                        showAddComicsToListDialog = false
                        selectedComicsForList = emptySet()
                    }) {
                        Text("ADD (${selectedComicsForList.size})", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddComicsToListDialog = false
                    selectedComicsForList = emptySet()
                }) {
                    Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }

    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text("Delete selected comics?", fontFamily = Anton, color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("This removes ${selectedComicIds.size} imported comic copies. Original files are untouched.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSelectedComics()
                    showBatchDeleteDialog = false
                }) {
                    Text("DELETE", color = ComicRose, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) { Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }

    comicOptionsTarget?.let { comic ->
        val isRead = comic.isCompleted
        AlertDialog(
            onDismissRequest = { comicOptionsTarget = null },
            title = {
                Text(
                    text = comic.title.uppercase(),
                    fontFamily = Anton,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "COMIC ACTIONS",
                        fontFamily = Archivo,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                    )

                    // Option 1: ADD TO READING LIST
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(LeafAccent)
                            .clickable {
                                val target = comicOptionsTarget
                                comicOptionsTarget = null
                                if (target != null) {
                                    viewModel.setSelectedComicIds(setOf(target.id))
                                    showAddToListDialog = true
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = DeepInk, modifier = Modifier.size(18.dp))
                        Text("ADD TO READING LIST", fontFamily = Anton, fontSize = 13.sp, color = DeepInk)
                    }

                    // Option 2: SET SERIES
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, Ochre, RoundedCornerShape(4.dp))
                            .clickable {
                                val target = comicOptionsTarget
                                comicOptionsTarget = null
                                if (target != null) {
                                    seriesInput = target.series.orEmpty()
                                    volumeInput = target.volume.orEmpty()
                                    viewModel.setSelectedComicIds(setOf(target.id))
                                    showGroupDialog = true
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                        Text("SET SERIES NAME", fontFamily = Anton, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    }

                    // Option 3: MARK AS READ / UNREAD
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, LeafSecondary.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .clickable {
                                val target = comicOptionsTarget
                                comicOptionsTarget = null
                                if (target != null) {
                                    viewModel.markComicRead(target.id, !isRead)
                                }
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            if (isRead) Icons.Default.Close else Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            if (isRead) "MARK AS UNREAD" else "MARK AS READ",
                            fontFamily = Anton,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    // Option 4: DELETE COMIC
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(ComicRose.copy(alpha = 0.15f))
                            .border(1.dp, ComicRose, RoundedCornerShape(4.dp))
                            .clickable {
                                val target = comicOptionsTarget
                                comicOptionsTarget = null
                                pendingDelete = target
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = ComicRose, modifier = Modifier.size(18.dp))
                        Text("DELETE COMIC", fontFamily = Anton, fontSize = 13.sp, color = ComicRose)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { comicOptionsTarget = null }) {
                    Text("CLOSE", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }

    pendingDelete?.let { comic ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove comic?", fontFamily = Anton, color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("This removes the imported copy. The original file is untouched.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteComic(comic.id); pendingDelete = null }) {
                    Text("DELETE", color = ComicRose, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant) } },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ComicCard(
    comic: ComicEntity? = null,
    columns: Int = 2,
    isEditMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    remote: JellyfinCatalogueEntry? = null,
    remoteTitle: String? = null,
    remoteSubtitle: String? = null,
    loadRemoteCover: (suspend (JellyfinItem) -> Bitmap?)? = null,
    remoteCoverWidthPx: Int = 400,
) {
    require((comic == null) != (remote == null))
    val title = comic?.title ?: remoteTitle ?: remote!!.item.name
    val pct = if (comic != null && comic.pageCount > 0) ((comic.lastPage + 1f) / comic.pageCount).coerceIn(0f, 1f) else 0f
    val started = (comic?.lastPage ?: 0) > 0
    val borderWidth = if (columns >= 4) 2.dp else 3.dp
    val shadowOffset = if (columns >= 4) 3.dp else 5.dp
    val progressHeight = if (columns >= 4) 5.dp else 7.dp

    val fileSize by rememberFileSize(comic?.filePath)
    val sizeText = formatFileSize(fileSize)

    Column(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .comicShadow(offset = shadowOffset, color = Color(0xB3000000))
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = if (isSelected) 3.dp else borderWidth,
                    color = if (isSelected) MaterialTheme.colorScheme.tertiary else Color.Transparent,
                    shape = RoundedCornerShape(4.dp),
                ),
        ) {
            val cover = rememberCover(comic?.coverPath)
            val remoteBitmap by produceState<Bitmap?>(null, remote?.item?.id, remote?.item?.imageTag, LocalRemoteCoverOffline.current, remoteCoverWidthPx) {
                val loaded = remote?.item?.let { loadRemoteCover?.invoke(it) }
                if (loaded != null || value == null) value = loaded
            }
            if (remoteBitmap != null) {
                Image(remoteBitmap!!.asImageBitmap(), title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else if (cover != null) {
                Image(cover, title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                GeneratedCover(title, columns = columns)
            }

            if (isEditMode) {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(24.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(if (isSelected) Ochre else MaterialTheme.colorScheme.surface.copy(alpha = 0.8f))
                        .border(1.5.dp, if (isSelected) Ink else MaterialTheme.colorScheme.onSurface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isSelected) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Ink,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            if (!isEditMode && (comic != null || remote?.played == true)) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(progressHeight).background(MaterialTheme.colorScheme.surface)) {
                    Box(Modifier.fillMaxWidth(if (remote?.played == true) 1f else pct).fillMaxHeight().background(Ochre))
                }
            }
        }
        Text(
            title.uppercase(),
            fontFamily = Archivo,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (columns >= 5) 10.sp else if (columns >= 4) 11.sp else 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = if (columns >= 4) 6.dp else 9.dp),
        )
        Text(
            remoteSubtitle ?: if (comic != null && started) "${(pct * 100).toInt()}% · pg ${comic.lastPage + 1}/${comic.pageCount}${if (sizeText.isNotEmpty()) " · $sizeText" else ""}"
            else if (comic != null) "${comic.pageCount} pages${if (sizeText.isNotEmpty()) " · $sizeText" else ""}" else "Jellyfin",
            fontFamily = Archivo,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (columns >= 4) 8.5.sp else 9.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
internal fun GeneratedCover(title: String, columns: Int = 2) {
    val fontSize = if (columns >= 5) 13.sp else if (columns >= 4) 15.sp else if (columns == 3) 18.sp else 22.sp
    val padding = if (columns >= 4) 8.dp else 12.dp
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).halftone(Crimson, alpha = 0.18f)) {
        Text(
            title.uppercase(),
            fontFamily = Anton,
            fontSize = fontSize,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.BottomStart).padding(padding),
        )
    }
}

@Composable
fun SmbDriveScreen(
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
) {
    val browser by viewModel.smbBrowser.collectAsStateWithLifecycle()
    val entries = browser.entries
    val loading = browser.loading
    val path = browser.path
    val selectedPaths = browser.selectedPaths
    DisposableEffect(viewModel) {
        onDispose { viewModel.cancelSmbBrowse() }
    }
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val importStatus by viewModel.importStatus.collectAsStateWithLifecycle()
    val importDetail by viewModel.importDetail.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val fileEntries = remember(entries) { entries.filter { !it.isDirectory } }
    val selectedCount = selectedPaths.size
    val totalFiles = fileEntries.size
    val allSelected = totalFiles > 0 && selectedCount == totalFiles
    val totalSelectedBytes = remember(selectedPaths, fileEntries) {
        fileEntries.filter { it.path in selectedPaths }.sumOf { it.sizeBytes }
    }

    BackHandler(enabled = !importing && selectedCount > 0) {
        viewModel.clearSmbSelection()
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Surface(
            color = LeafPrimary,
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(PaperWhite.copy(alpha = 0.14f))
                        .clickable {
                            if (selectedCount > 0) {
                                viewModel.clearSmbSelection()
                            } else if (path.isNotEmpty()) {
                                viewModel.loadSmbDirectory(net.digitalbc.leafreader.data.smb.SmbPaths.parent(path))
                            } else {
                                onBack()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = PaperWhite,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.size(14.dp))
                Column {
                    OchreBadge("SMB DRIVE")
                    if (path.isNotEmpty()) {
                        Text(
                            path,
                            fontFamily = Archivo,
                            fontSize = 11.sp,
                            color = PaperWhiteMuted,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            Box(Modifier.matchParentSize().halftone(LeafSecondary, alpha = 0.05f))

            Column(
                Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 16.dp),
            ) {
                if (fileEntries.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (selectedCount > 0) "$selectedCount SELECTED (${formatFileSize(totalSelectedBytes)})" else "$totalFiles ${if (totalFiles == 1) "COMIC" else "COMICS"}",
                            fontFamily = Archivo,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = if (selectedCount > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (selectedCount > 0 && !allSelected) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(1.dp, DeepInk, RoundedCornerShape(4.dp))
                                        .clickable { viewModel.clearSmbSelection() }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "DESELECT ALL",
                                        fontFamily = Anton,
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (allSelected) Ochre else MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, if (allSelected) Ochre else DeepInk, RoundedCornerShape(4.dp))
                                    .clickable {
                                        if (allSelected) viewModel.clearSmbSelection() else viewModel.selectAllSmb()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (allSelected) "DESELECT ALL" else "SELECT ALL",
                                    fontFamily = Anton,
                                    fontSize = 10.5.sp,
                                    color = if (allSelected) DeepInk else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }

                Box(Modifier.fillMaxWidth().weight(1f)) {
                    if (loading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else if (entries.isEmpty()) {
                        Text(
                            "No folders or comics found here.",
                            fontFamily = Archivo,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
                        ) {
                            items(entries, key = { it.path }) { entry ->
                                val isSelected = entry.path in selectedPaths
                                SmbEntryRow(
                                    name = entry.name,
                                    isDirectory = entry.isDirectory,
                                    sizeBytes = entry.sizeBytes,
                                    isSelected = isSelected,
                                    onClick = {
                                        if (entry.isDirectory) {
                                            viewModel.loadSmbDirectory(entry.path)
                                        } else {
                                            viewModel.toggleSmbSelection(entry.path)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ImportProgressBanner(
                        importing = importing,
                        progress = importProgress,
                        status = importStatus,
                        detail = importDetail,
                        onCancel = { viewModel.cancelImport() },
                    )

                    if (selectedCount > 0 && !importing && !loading) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .comicShadow(offset = 3.dp, color = Color(0xB3000000))
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(2.dp, LeafAccent, RoundedCornerShape(6.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(
                                        text = "$selectedCount ${if (selectedCount == 1) "COMIC" else "COMICS"} SELECTED",
                                        fontFamily = Anton,
                                        fontSize = 15.sp,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        text = formatFileSize(totalSelectedBytes),
                                        fontFamily = Archivo,
                                        fontSize = 11.5.sp,
                                        color = MaterialTheme.colorScheme.tertiary,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(LeafAccent)
                                        .clickable {
                                            val toImport = fileEntries.filter { it.path in selectedPaths }
                                            viewModel.importSmbComics(toImport) {
                                                viewModel.clearSmbSelection()
                                            }
                                            onBack()
                                        }
                                        .padding(horizontal = 16.dp, vertical = 9.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "IMPORT ($selectedCount)",
                                        fontFamily = Anton,
                                        fontSize = 13.sp,
                                        color = DeepInk,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            SnackbarHost(
                snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
            )
        }
    }
}

@Composable
private fun SmbEntryRow(
    name: String,
    isDirectory: Boolean,
    sizeBytes: Long,
    isSelected: Boolean = false,
    onClick: () -> Unit,
) {
    val bgColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else DeepInk
    val borderColor = if (isSelected) MaterialTheme.colorScheme.tertiary else LeafSecondary.copy(alpha = 0.4f)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(4.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            if (isDirectory) Icons.Default.Folder else Icons.Default.Description,
            contentDescription = null,
            tint = if (isDirectory) MaterialTheme.colorScheme.tertiary else if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(18.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                name,
                fontFamily = Archivo,
                fontWeight = if (isDirectory || isSelected) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!isDirectory && sizeBytes > 0L) {
                Text(
                    formatFileSize(sizeBytes),
                    fontFamily = Archivo,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (isDirectory) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer(rotationZ = 180f),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isSelected) LeafAccent else MaterialTheme.colorScheme.surface)
                    .border(
                        width = 1.5.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(3.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = DeepInk,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

internal val coverBitmapCache = object : LruCache<String, ImageBitmap>(
    minOf(32 * 1024 * 1024, (Runtime.getRuntime().maxMemory() / 16).coerceIn(1, Int.MAX_VALUE.toLong()).toInt())
) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.asAndroidBitmap().allocationByteCount
}

@Composable
internal fun rememberCover(path: String?): ImageBitmap? {
    if (path == null) return null
    var bitmap by remember(path) { mutableStateOf(coverBitmapCache[path]) }
    if (bitmap == null) {
        LaunchedEffect(path) {
            val fromCache = coverBitmapCache[path]
            if (fromCache != null) {
                bitmap = fromCache
                return@LaunchedEffect
            }
            val loaded = withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(path)
                    if (file.exists()) BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap() else null
                }.getOrNull()
            }
            if (loaded != null) {
                coverBitmapCache.put(path, loaded)
                bitmap = loaded
            }
        }
    }
    return bitmap
}

private const val COVER_PEEL_THRESHOLD_FRACTION = 0.35f

private fun lerp(start: Float, stop: Float, fraction: Float): Float =
    start + fraction * (stop - start)

@Composable
private fun Modifier.onePointerCoverPeel(
    enabled: Boolean = true,
    cardHeightPx: Float,
    offsetY: Animatable<Float, AnimationVector1D>,
    coroutineScope: CoroutineScope,
    onThresholdMet: () -> Unit,
    onClick: () -> Unit,
): Modifier {
    val currentOnThresholdMet by rememberUpdatedState(onThresholdMet)
    val currentOnClick by rememberUpdatedState(onClick)
    return pointerInput(enabled, cardHeightPx) {
        if (!enabled) return@pointerInput
        var animJob: Job? = null
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val downId = down.id
            val downX = down.position.x
            val downY = down.position.y
            val slop = viewConfiguration.touchSlop
            val tracker = VelocityTracker()
            tracker.addPosition(down.uptimeMillis, down.position)

            animJob?.cancel()
            animJob = coroutineScope.launch {
                offsetY.stop()
            }

            var isDraggingDown = false
            var isCanceled = false
            var lastChange: PointerInputChange? = null
            var currentDragY = 0f

            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == downId } ?: break
                lastChange = change
                tracker.addPosition(change.uptimeMillis, change.position)

                val totalDx = change.position.x - downX
                val totalDy = change.position.y - downY

                if (!isDraggingDown) {
                    if (abs(totalDy) > slop || abs(totalDx) > slop) {
                        if (totalDy > slop && totalDy > abs(totalDx)) {
                            isDraggingDown = true
                        } else {
                            // Upwards or horizontal drag: leave unconsumed so parent LazyVerticalGrid can scroll
                            isCanceled = true
                            break
                        }
                    }
                }

                if (isDraggingDown) {
                    change.consume()
                    currentDragY = (totalDy - slop).coerceIn(0f, if (cardHeightPx > 0f) cardHeightPx * 1.25f else 1000f)
                    animJob?.cancel()
                    animJob = coroutineScope.launch {
                        offsetY.snapTo(currentDragY)
                    }
                }

                if (!change.pressed) {
                    if (isDraggingDown) {
                        change.consume()
                    }
                    break
                }
            }

            if (!isDraggingDown && !isCanceled) {
                // Tapped without dragging past slop -> Consume UP event and trigger click
                lastChange?.consume()
                currentOnClick()
            } else if (isDraggingDown) {
                val velocityY = tracker.calculateVelocity().y
                val currentOffset = currentDragY
                val threshold = if (cardHeightPx > 0f) cardHeightPx * COVER_PEEL_THRESHOLD_FRACTION else 180f
                val isThresholdMet = currentOffset >= threshold
                val isFlickDown = velocityY > 600f && currentOffset > (if (cardHeightPx > 0f) cardHeightPx * 0.15f else 60f)
                val isCancelingUp = velocityY < -1000f
                val shouldCycle = (isThresholdMet && !isCancelingUp) || isFlickDown

                animJob?.cancel()
                animJob = coroutineScope.launch {
                    if (shouldCycle) {
                        val targetY = if (cardHeightPx > 0f) cardHeightPx * 1.15f else 1000f
                        offsetY.animateTo(
                            targetValue = targetY,
                            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                        )
                        currentOnThresholdMet()
                        offsetY.snapTo(0f)
                    } else {
                        offsetY.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                        )
                    }
                }
            }
        }
    }
}

internal data class StackCover(val id: String, val coverPath: String?, val issueNumber: Float?, val remote: JellyfinItem? = null)
private data class StackPresentation(
    val seriesName: String, val volume: String?, val issues: List<StackCover>,
    val topCoverComic: StackCover, val progressFraction: Float,
    val remote: Boolean = false,
) {
    val totalIssues get() = issues.size
}

@Composable
private fun stackCover(cover: StackCover?, load: (suspend (JellyfinItem) -> Bitmap?)?, widthPx: Int): ImageBitmap? {
    val local = rememberCover(cover?.coverPath)
    val remote by produceState<Bitmap?>(null, cover?.id, cover?.remote?.imageTag, LocalRemoteCoverOffline.current, widthPx) {
        val loaded = cover?.remote?.let { load?.invoke(it) }
        if (loaded != null || value == null) value = loaded
    }
    return local ?: remote?.asImageBitmap()
}

/** Keep artwork state with its comic while that comic changes stack roles. */
@Composable
internal fun stackCoverImages(covers: List<StackCover>, load: (suspend (JellyfinItem) -> Bitmap?)?, widthPx: Int): Map<String, ImageBitmap?> =
    covers.distinctBy { it.id }.associate { cover ->
        cover.id to androidx.compose.runtime.key(cover.id) { stackCover(cover, load, widthPx) }
    }

@Composable
private fun ComicStackCard(stack: LibraryItem.SeriesStack, columns: Int = 2,
    selectedCoverId: String? = null, onCoverSelected: (String) -> Unit = {}, onClick: () -> Unit) {
    val covers = remember(stack.issues) { stack.issues.map { StackCover(it.id.toString(), it.coverPath, it.issueNumber) } }
    CoverStackCard(StackPresentation(stack.seriesName, stack.volume, covers,
        covers.firstOrNull { it.id == stack.topCoverComic.id.toString() } ?: covers.first(),
        stack.progressFraction),
        columns = columns, selectedCoverId = selectedCoverId, onCoverSelected = onCoverSelected, onClick = onClick)
}

@Composable
internal fun RemoteSeriesStackCard(
    group: net.digitalbc.leafreader.data.jellyfin.JellyfinSeriesGroup,
    columns: Int, loadCover: suspend (JellyfinItem) -> Bitmap?, coverWidthPx: Int = 400,
    selectedCoverId: String? = null, onCoverSelected: (String) -> Unit = {}, onClick: () -> Unit,
) {
    val covers = remember(group.entries) { group.entries.map { StackCover(it.item.id, null, it.issueNumber?.toFloat(), it.item) } }
    androidx.compose.runtime.key(group.key) {
        CoverStackCard(StackPresentation(group.name, null, covers, covers.first(), 0f, remote = true),
            loadRemoteCover = loadCover, remoteCoverWidthPx = coverWidthPx, columns = columns,
            selectedCoverId = selectedCoverId, onCoverSelected = onCoverSelected, onClick = onClick)
    }
}

@Composable
private fun CoverStackCard(
    stack: StackPresentation,
    loadRemoteCover: (suspend (JellyfinItem) -> Bitmap?)? = null,
    remoteCoverWidthPx: Int = 400,
    columns: Int = 2,
    selectedCoverId: String? = null,
    onCoverSelected: (String) -> Unit = {},
    onClick: () -> Unit,
) {
    val pct = stack.progressFraction
    val borderWidth = if (columns >= 4) 2.dp else 3.dp
    val shadowOffset = if (columns >= 4) 3.dp else 5.dp
    val progressHeight = if (stack.remote) 0.dp else if (columns >= 4) 5.dp else 7.dp

    val initialIndex = remember(stack.issues, stack.topCoverComic) {
        val idx = stack.issues.indexOfFirst { it.id == stack.topCoverComic.id }
        if (idx >= 0) idx else 0
    }
    var savedTopIndex by rememberSaveable(stack.seriesName, stack.volume) { mutableIntStateOf(initialIndex) }
    val selectedIndex = remember(stack.issues, selectedCoverId) {
        selectedCoverId?.let { id -> stack.issues.indexOfFirst { it.id == id } } ?: -1
    }
    val topIndex = if (selectedIndex >= 0) selectedIndex else savedTopIndex
    val issueCount = stack.issues.size
    val currentTopComic = remember(stack.issues, topIndex) {
        if (issueCount > 0) stack.issues[(topIndex % issueCount + issueCount) % issueCount]
        else stack.topCoverComic
    }
    val layer2Comic = remember(stack.issues, topIndex) {
        if (issueCount >= 2) stack.issues[((topIndex + 1) % issueCount + issueCount) % issueCount]
        else null
    }
    val layer3Comic = remember(stack.issues, topIndex) {
        if (issueCount >= 3) stack.issues[((topIndex + 2) % issueCount + issueCount) % issueCount]
        else null
    }

    val coverImages = stackCoverImages(listOfNotNull(currentTopComic, layer2Comic, layer3Comic), loadRemoteCover, remoteCoverWidthPx)
    val coroutineScope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    var cardHeightPx by remember { mutableFloatStateOf(0f) }
    val progress = { if (cardHeightPx > 0f) (offsetY.value / cardHeightPx).coerceIn(0f, 1f) else 0f }
    val peelThresholdReached by remember { androidx.compose.runtime.derivedStateOf {
        cardHeightPx > 0f && offsetY.value / cardHeightPx >= COVER_PEEL_THRESHOLD_FRACTION
    } }
    val backCoverDimming = animateFloatAsState(
        targetValue = if (peelThresholdReached) 0f else 1f,
        animationSpec = tween(180),
        label = "Back cover dimming",
    )

    Column(
        Modifier.clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .padding(top = if (stack.totalIssues > 1) 8.dp else 0.dp)
                .onSizeChanged { cardHeightPx = it.height.toFloat() }
                .onePointerCoverPeel(
                    enabled = stack.totalIssues > 1,
                    cardHeightPx = cardHeightPx,
                    offsetY = offsetY,
                    coroutineScope = coroutineScope,
                    onThresholdMet = {
                        savedTopIndex = if (issueCount > 0) (topIndex + 1) % issueCount else topIndex + 1
                        stack.issues.getOrNull(savedTopIndex)?.let { onCoverSelected(it.id) }
                    },
                    onClick = onClick,
                )
        ) {
            // Neat Stack Layer 3 (for 3+ issues) - highest top offset
            if (stack.totalIssues >= 3) {
                val cover3 = coverImages[layer3Comic?.id]
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = lerp((-8).dp.toPx(), (-4).dp.toPx(), progress())
                            scaleX = lerp(0.93f, 0.96f, progress())
                        }
                        .shadow(elevation = shadowOffset * 1.6f, shape = RoundedCornerShape(4.dp), clip = false,
                            ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.24f))
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (cover3 != null) {
                        Image(cover3, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                    Box(Modifier.matchParentSize().graphicsLayer { alpha = backCoverDimming.value }.background(Color.Black.copy(alpha = 0.28f)))
                }
            }

            // Neat Stack Layer 2 (for 2+ issues) - middle top offset
            if (stack.totalIssues >= 2) {
                val cover2 = coverImages[layer2Comic?.id]
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = lerp((-4).dp.toPx(), 0f, progress())
                            scaleX = lerp(0.96f, 1f, progress())
                        }
                        .then(
                            if (stack.totalIssues == 2) Modifier.shadow(elevation = shadowOffset * 1.6f, shape = RoundedCornerShape(4.dp), clip = false,
                                ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.24f))
                            else Modifier
                        )
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)

                ) {
                    if (cover2 != null) {
                        Image(cover2, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }

                    val layer2IssueNum = if (issueCount > 0) ((topIndex + 1) % issueCount + issueCount) % issueCount + 1 else 2
                    val layer2BadgeText = if (stack.remote) "$layer2IssueNum / ${stack.totalIssues}" else layer2Comic?.issueNumber?.let { num ->
                        val numStr = if (num % 1.0f == 0.0f) "${num.toInt()}" else "$num"
                        "ISSUE #$numStr OF ${stack.totalIssues}"
                    } ?: "ISSUE $layer2IssueNum OF ${stack.totalIssues}"
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(
                                start = if (columns >= 4) 4.dp else 6.dp,
                                bottom = (if (columns >= 4) 4.dp else 6.dp) + progressHeight,
                            )
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                            .border(1.dp, Ochre, RoundedCornerShape(3.dp))
                            .padding(horizontal = if (columns >= 4) 4.dp else 5.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = layer2BadgeText,
                            fontFamily = Anton,
                            fontSize = if (columns >= 4) 7.sp else 8.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(progressHeight).background(MaterialTheme.colorScheme.surface)) {
                        Box(Modifier.fillMaxWidth(pct).fillMaxHeight().background(Ochre))
                    }
                    Box(Modifier.matchParentSize().graphicsLayer { alpha = backCoverDimming.value }.background(Color.Black.copy(alpha = 0.16f)))
                }
            }

            // Front Layer (Main cover)
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = offsetY.value
                        val fraction = progress()
                        alpha = if (fraction > 0.75f) (1f - (fraction - 0.75f) / 0.25f).coerceIn(0f, 1f) else 1f
                    }
                    .then(
                        if (stack.totalIssues <= 1) Modifier.shadow(elevation = shadowOffset * 1.6f, shape = RoundedCornerShape(4.dp), clip = false,
                            ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.24f))
                        else Modifier
                    )
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(width = borderWidth, color = Color.Transparent, shape = RoundedCornerShape(4.dp)),
            ) {
                val cover1 = coverImages[currentTopComic.id]
                if (cover1 != null) {
                    Image(cover1, stack.seriesName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    GeneratedCover(stack.seriesName, columns = columns)
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(
                            start = if (columns >= 4) 4.dp else 6.dp,
                            bottom = (if (columns >= 4) 4.dp else 6.dp) + progressHeight,
                        )
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                        .border(1.dp, Ochre, RoundedCornerShape(3.dp))
                        .padding(horizontal = if (columns >= 4) 4.dp else 5.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center,
                ) {
                    val currentIssueNum = if (issueCount > 0) (topIndex % issueCount + issueCount) % issueCount + 1 else 1
                    val frontBadgeText = if (stack.remote) "$currentIssueNum / ${stack.totalIssues}" else if (stack.totalIssues > 1) {
                        currentTopComic.issueNumber?.let { num ->
                            val numStr = if (num % 1.0f == 0.0f) "${num.toInt()}" else "$num"
                            "ISSUE #$numStr OF ${stack.totalIssues}"
                        } ?: "ISSUE $currentIssueNum OF ${stack.totalIssues}"
                    } else if (stack.volume != null) {
                        "${stack.seriesName.uppercase()} · ${stack.volume.uppercase()}"
                    } else {
                        "1 ISSUE"
                    }
                    Text(
                        text = frontBadgeText,
                        fontFamily = Anton,
                        fontSize = if (columns >= 4) 7.sp else 8.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(progressHeight).background(MaterialTheme.colorScheme.surface)) {
                    Box(Modifier.fillMaxWidth(pct).fillMaxHeight().background(Ochre))
                }
            }
        }
        Text(
            stack.seriesName.uppercase(),
            fontFamily = Archivo,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (columns >= 5) 10.sp else if (columns >= 4) 11.sp else 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = if (columns >= 4) 6.dp else 9.dp),
        )
    }
}

@Composable
private fun ReadingListCard(
    item: LibraryItem.ReadingListItem,
    columns: Int = 2,
    onClick: () -> Unit,
) {
    val shadowOffset = if (columns >= 4) 3.dp else 5.dp

    var topIndex by rememberSaveable(item.readingList.id) { mutableIntStateOf(0) }
    val totalCount = item.previewComics.size
    val currentTopComic = remember(item.previewComics, topIndex) {
        if (totalCount > 0) item.previewComics[(topIndex % totalCount + totalCount) % totalCount]
        else null
    }
    val layer2Comic = remember(item.previewComics, topIndex) {
        if (totalCount >= 2) item.previewComics[((topIndex + 1) % totalCount + totalCount) % totalCount]
        else null
    }
    val layer3Comic = remember(item.previewComics, topIndex) {
        if (totalCount >= 3) item.previewComics[((topIndex + 2) % totalCount + totalCount) % totalCount]
        else null
    }

    val coroutineScope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    var cardHeightPx by remember { mutableFloatStateOf(0f) }
    val progress = if (cardHeightPx > 0f) (offsetY.value / cardHeightPx).coerceIn(0f, 1f) else 0f
    val backCoverDimming by animateFloatAsState(
        targetValue = if (progress >= COVER_PEEL_THRESHOLD_FRACTION) 0f else 1f,
        animationSpec = tween(180),
        label = "Back cover dimming",
    )

    Column(
        Modifier.clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .padding(
                    horizontal = if (item.itemCount > 1) 6.dp else 0.dp,
                    vertical = if (item.itemCount > 1) 4.dp else 0.dp,
                )
                .onSizeChanged { cardHeightPx = it.height.toFloat() }
                .onePointerCoverPeel(
                    enabled = item.itemCount > 1,
                    cardHeightPx = cardHeightPx,
                    offsetY = offsetY,
                    coroutineScope = coroutineScope,
                    onThresholdMet = {
                        topIndex = if (totalCount > 0) (topIndex + 1) % totalCount else topIndex + 1
                    },
                    onClick = onClick,
                )
        ) {
            // Messy Layer 3 (Rotated Left) - for 3+ items
            if (item.itemCount >= 3) {
                val backCover = rememberCover(layer3Comic?.coverPath)
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = lerp(-6f, 5f, progress)
                            translationX = lerp((-6).dp.toPx(), 7.dp.toPx(), progress)
                            translationY = lerp((-4).dp.toPx(), (-3).dp.toPx(), progress)
                        }
                        .shadow(elevation = shadowOffset * 1.6f, shape = RoundedCornerShape(4.dp), clip = false,
                            ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.24f))
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (backCover != null) {
                        Image(
                            backCover,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.28f * backCoverDimming)))
                }
            }

            // Messy Layer 2 (Rotated Right) - for 2+ items
            if (item.itemCount >= 2) {
                val midCover = rememberCover(layer2Comic?.coverPath)
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = lerp(5f, 0f, progress)
                            translationX = lerp(7.dp.toPx(), 0f, progress)
                            translationY = lerp((-3).dp.toPx(), 0f, progress)
                        }
                        .then(
                            if (item.itemCount == 2) Modifier.shadow(elevation = shadowOffset * 1.6f, shape = RoundedCornerShape(4.dp), clip = false,
                                ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.24f))
                            else Modifier
                        )
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (midCover != null) {
                        Image(
                            midCover,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                    }

                    val layer2ItemNum = if (totalCount > 0) ((topIndex + 1) % totalCount + totalCount) % totalCount + 1 else 2
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(if (columns >= 4) 4.dp else 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                            .border(1.dp, Ochre, RoundedCornerShape(3.dp))
                            .padding(horizontal = if (columns >= 4) 4.dp else 5.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "ITEM $layer2ItemNum OF ${item.itemCount}",
                            fontFamily = Anton,
                            fontSize = if (columns >= 4) 7.sp else 8.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.16f * backCoverDimming)))
                }
            }

            // Front Main List Card
            val topCover = rememberCover(currentTopComic?.coverPath)
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = offsetY.value
                        alpha = if (progress > 0.75f) (1f - (progress - 0.75f) / 0.25f).coerceIn(0f, 1f) else 1f
                    }
                    .then(
                        if (item.itemCount <= 1) Modifier.shadow(elevation = shadowOffset * 1.6f, shape = RoundedCornerShape(4.dp), clip = false,
                            ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.24f))
                        else Modifier
                    )
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                if (topCover != null) {
                    Image(
                        topCover,
                        contentDescription = item.readingList.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    GeneratedCover(item.readingList.name, columns = columns)
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(if (columns >= 4) 4.dp else 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                        .border(1.dp, Ochre, RoundedCornerShape(3.dp))
                        .padding(horizontal = if (columns >= 4) 4.dp else 5.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val currentItemNum = if (totalCount > 0) (topIndex % totalCount + totalCount) % totalCount + 1 else 1
                    Text(
                        text = if (item.itemCount > 1) "ITEM $currentItemNum OF ${item.itemCount}" else "${item.itemCount} ${if (item.itemCount == 1) "ITEM" else "ITEMS"}",
                        fontFamily = Anton,
                        fontSize = if (columns >= 4) 7.sp else 8.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Text(
            item.readingList.name.uppercase(),
            fontFamily = Archivo,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (columns >= 5) 10.sp else if (columns >= 4) 11.sp else 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = if (columns >= 4) 6.dp else 9.dp),
        )
        Text(
            "Leaf list · ${item.itemCount} ${if (item.itemCount == 1) "item" else "items"}",
            fontFamily = Archivo,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (columns >= 4) 8.5.sp else 9.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun AddTile(
    importing: Boolean,
    progress: Float?,
    status: String? = null,
    detail: String? = null,
    columns: Int = 2,
    isListsFilter: Boolean = false,
    onClick: () -> Unit,
    onCancel: () -> Unit = {},
) {
    val starburstSize = if (columns >= 5) 42.dp else if (columns >= 4) 50.dp else 64.dp
    val iconSize = if (columns >= 5) 18.dp else if (columns >= 4) 22.dp else 26.dp
    val fontSize = if (columns >= 5) 11.sp else if (columns >= 4) 12.5.sp else 14.sp
    val spacing = if (columns >= 4) 6.dp else 10.dp
    val contentPadding = if (columns >= 4) 8.dp else 16.dp
    val strokeWidth = if (columns >= 4) 2.dp else 3.dp
    val pct = ((progress ?: 0f) * 100).toInt().coerceIn(0, 100)

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .drawBehind {
                val p = PathEffect.dashPathEffect(floatArrayOf(16f, 12f), 0f)
                drawRoundRect(
                    color = LeafAccent,
                    style = Stroke(width = strokeWidth.toPx(), pathEffect = p),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
            .clickable(onClick = onClick)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing),
        ) {
            Box(
                modifier = Modifier
                    .size(starburstSize)
                    .clip(LeafShape)
                    .background(LeafAccent),
                contentAlignment = Alignment.Center,
            ) {
                if (importing) CircularProgressIndicator(modifier = Modifier.size(iconSize), color = DeepInk, strokeWidth = 3.dp)
                else Icon(Icons.Default.Add, contentDescription = "Load comic", tint = DeepInk, modifier = Modifier.size(iconSize))
            }
            Text(
                if (importing) {
                    if (status != null) (if (progress == null) status else "$pct% · $status").uppercase()
                    else if ((progress ?: 0f) > 0f) "LOADING $pct%"
                    else "LOADING…"
                } else if (isListsFilter) "CREATE LIST" else "LOAD COMIC",
                fontFamily = Anton,
                fontSize = fontSize,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (importing) {
                ImportProgressBar(progress,
                    Modifier.fillMaxWidth().height(if (columns >= 4) 4.dp else 6.dp).clip(RoundedCornerShape(3.dp)))
                if (detail != null) {
                    Text(
                        detail,
                        fontFamily = Archivo,
                        fontSize = if (columns >= 4) 8.sp else 9.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, ComicRose, RoundedCornerShape(3.dp))
                        .clickable { onCancel() }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "CANCEL",
                        fontFamily = Anton,
                        fontSize = if (columns >= 4) 9.sp else 10.5.sp,
                        color = ComicRose,
                        letterSpacing = 0.5.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportProgressBar(progress: Float?, modifier: Modifier) {
    if (progress == null) {
        LinearProgressIndicator(color = MaterialTheme.colorScheme.tertiary, trackColor = DeepInk, modifier = modifier)
    } else {
        LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) },
            color = MaterialTheme.colorScheme.tertiary, trackColor = DeepInk, modifier = modifier)
    }
}

@Composable
private fun ImportProgressBanner(
    importing: Boolean,
    progress: Float?,
    status: String?,
    detail: String?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = importing,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        val pct = ((progress ?: 0f) * 100).toInt().coerceIn(0, 100)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .comicShadow(offset = 4.dp, color = Color(0xB3000000))
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(2.dp, LeafAccent, RoundedCornerShape(8.dp))
                .padding(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.tertiary,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = status?.uppercase() ?: "DOWNLOADING COMIC…",
                            fontFamily = Anton,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(LeafPrimary)
                            .border(1.dp, LeafAccent, RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = if (progress == null) "IMPORTING" else "$pct%",
                            fontFamily = Anton,
                            fontSize = 13.sp,
                            color = LeafAccent,
                            letterSpacing = 0.5.sp,
                        )
                    }
                }

                ImportProgressBar(progress,
                    Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(3.5.dp)))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = detail ?: if ((progress ?: 0f) > 0f) "$pct% completed" else "Connecting…",
                        fontFamily = Archivo,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, ComicRose, RoundedCornerShape(4.dp))
                            .clickable { onCancel() }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Cancel import",
                                tint = ComicRose,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = "CANCEL",
                                fontFamily = Anton,
                                fontSize = 11.sp,
                                color = ComicRose,
                                letterSpacing = 0.5.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnDensityIcon(columns: Int, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(13.dp)) {
        val gap = 2.dp.toPx()
        val totalGaps = (columns - 1) * gap
        val colWidth = (size.width - totalGaps) / columns
        val corner = 1.dp.toPx()

        for (i in 0 until columns) {
            val left = i * (colWidth + gap)
            drawRoundRect(
                color = Ink,
                topLeft = Offset(left, 0f),
                size = Size(colWidth, size.height),
                cornerRadius = CornerRadius(corner, corner),
            )
        }
    }
}
