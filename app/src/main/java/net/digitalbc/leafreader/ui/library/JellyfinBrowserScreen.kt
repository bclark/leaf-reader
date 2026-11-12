package net.digitalbc.leafreader.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material3.Checkbox
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import net.digitalbc.leafreader.R
import net.digitalbc.leafreader.data.jellyfin.JellyfinBrowserState
import net.digitalbc.leafreader.data.jellyfin.JellyfinFailureKind
import net.digitalbc.leafreader.data.jellyfin.JellyfinItem
import net.digitalbc.leafreader.data.jellyfin.JellyfinProgress
import net.digitalbc.leafreader.data.jellyfin.JellyfinView
import net.digitalbc.leafreader.data.settings.ThemeMode
import net.digitalbc.leafreader.ui.brand.OchreBadge
import net.digitalbc.leafreader.ui.brand.halftone
import net.digitalbc.leafreader.ui.theme.Anton
import net.digitalbc.leafreader.ui.theme.Archivo
import net.digitalbc.leafreader.ui.theme.ComicReaderTheme
import net.digitalbc.leafreader.ui.theme.DeepInk
import net.digitalbc.leafreader.ui.theme.Ink
import net.digitalbc.leafreader.ui.theme.LeafAccent
import net.digitalbc.leafreader.ui.theme.LeafPrimary
import net.digitalbc.leafreader.ui.theme.LeafSecondary
import net.digitalbc.leafreader.ui.theme.Ochre
import net.digitalbc.leafreader.ui.theme.PaperWhite
import net.digitalbc.leafreader.ui.theme.PaperWhiteMuted

@Composable
fun JellyfinBrowserScreen(
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
    onOpenComic: (Long) -> Unit,
    onOpenLocal: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val browser by viewModel.jellyfinBrowser.collectAsStateWithLifecycle()
    val detail by viewModel.jellyfinDetail.collectAsStateWithLifecycle()
    val positions = rememberSaveableStateHolder()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val importDetail by viewModel.importDetail.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(viewModel) {
        onDispose { viewModel.cancelJellyfinBrowse() }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshJellyfinLocalCopy()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(detail?.readyComicId) {
        viewModel.consumeJellyfinRead()?.let(onOpenComic)
    }

    val selectedCount = browser.selectedItemIds.size

    BackHandler(enabled = !importing && (detail != null || selectedCount > 0 || !browser.isRoot)) {
        if (detail != null) {
            viewModel.closeJellyfinDetail()
        } else if (selectedCount > 0) {
            viewModel.clearJellyfinSelection()
        } else if (!browser.isRoot) {
            viewModel.navigateJellyfinUp()
        } else {
            onBack()
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    val cover: @Composable (JellyfinItem, Modifier) -> Unit = { item, modifier ->
        val loader = remember(viewModel, item.id, item.imageTag) {
            suspend { viewModel.loadJellyfinCover(item) }
        }
        JellyfinCover(item, modifier, loader)
    }

    detail?.let { state ->
        val currentItem = state.details?.item ?: state.item
        val heroBitmap by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(
            null, viewModel, currentItem.id, currentItem.imageTag, state.details?.fromCache == true) {
            value = viewModel.loadJellyfinCover(currentItem)
        }
        val creatorNavigation = LocalCreatorNavigation.current
        positions.SaveableStateProvider("detail:${state.identity}") {
        Box(Modifier.fillMaxSize()) {
            JellyfinDetailScreen(
                state = state,
                onRead = {
                    if (state.localComic != null) viewModel.refreshJellyfinLocalCopy(openWhenReady = true)
                    else viewModel.downloadJellyfinDetail(openWhenReady = true)
                },
                onCheckCopy = { viewModel.refreshJellyfinLocalCopy() },
                importing = importing,
                importProgress = importProgress,
                importDetail = importDetail,
                onBack = { viewModel.closeJellyfinDetail() },
                onRetry = { viewModel.retryJellyfinDetail() },
                onDownload = { viewModel.downloadJellyfinDetail() },
                onCancel = { viewModel.cancelImport() },
                cover = { _, modifier -> JellyfinCoverImage(heroBitmap, modifier) },
                heroBitmap = heroBitmap,
                onCreator = creatorNavigation,
            )
            SnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
        }
        return
    }

    positions.SaveableStateProvider(viewModel.jellyfinBrowsePositionKey(browser.currentParentId)) {
    JellyfinBrowserScreenContent(
        browser = browser,
        itemCover = cover,
        importing = importing,
        importProgress = importProgress,
        importDetail = importDetail,
        snackbarHostState = snackbarHostState,
        onBack = {
            if (!browser.isRoot) viewModel.navigateJellyfinUp() else onBack()
        },
        onOpenLocal = onOpenLocal,
        onOpenLibraries = { viewModel.loadJellyfinRoot() },
        onOpenSettings = onOpenSettings,
        onClearSelection = { viewModel.clearJellyfinSelection() },
        onSelectAll = { viewModel.selectAllJellyfin() },
        onToggleSelection = { viewModel.toggleJellyfinSelection(it) },
        onItemClick = { item ->
            if (item.isFolder) {
                viewModel.loadJellyfinFolder(item.id, item.name)
            } else {
                viewModel.openJellyfinDetail(item)
            }
        },
        onViewClick = { view ->
            viewModel.loadJellyfinFolder(view.id, view.name)
        },
        onImportSelected = {
            val selectedItems = browser.items.filter { it.id in browser.selectedItemIds }
            viewModel.importJellyfinItems(selectedItems) {
                viewModel.clearJellyfinSelection()
            }
        },
        onCancelImport = { viewModel.cancelImport() },
        onRetry = {
            if (browser.isRoot) {
                viewModel.ensureJellyfinLanding(force = true)
            } else {
                browser.currentParentId?.let { viewModel.loadJellyfinFolder(it, browser.currentTitle) }
            }
        },
    )
    }
}

@Composable
fun JellyfinBrowserScreenContent(
    browser: JellyfinBrowserState,
    importing: Boolean,
    importProgress: Float?,
    importDetail: String?,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onToggleSelection: (String) -> Unit,
    onItemClick: (JellyfinItem) -> Unit,
    onViewClick: (JellyfinView) -> Unit,
    onImportSelected: () -> Unit,
    onCancelImport: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenLocal: () -> Unit = {},
    onOpenLibraries: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    folderListState: LazyListState = rememberLazyListState(),
    libraryListState: LazyListState = rememberLazyListState(),
    itemCover: @Composable (JellyfinItem, Modifier) -> Unit = { item, coverModifier ->
        JellyfinCover(item, coverModifier)
    },
) {
    val selectedCount = browser.selectedItemIds.size
    val importableItems = remember(browser.items, browser.downloadedItemIds) {
        browser.items.filter { it.isImportable && it.id !in browser.downloadedItemIds }
    }
    val allSelected = importableItems.isNotEmpty() && selectedCount == importableItems.size

    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Top Bar
        Surface(
            color = LeafPrimary,
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Back"
                        ) {
                            if (selectedCount > 0) {
                                onClearSelection()
                            } else {
                                onBack()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(PaperWhite.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PaperWhite,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.size(10.dp))
                Column {
                    OchreBadge("JELLYFIN")
                    Text(
                        browser.currentTitle,
                        fontFamily = Archivo,
                        fontSize = 11.sp,
                        color = PaperWhiteMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(onClick = onOpenLocal,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                Text(stringResource(R.string.library_title), fontFamily = Archivo)
            }
            TextButton(onClick = onOpenLibraries, modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                Text(stringResource(R.string.jellyfin_libraries_action), fontFamily = Archivo)
            }
            TextButton(onClick = onOpenSettings, modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                Text(stringResource(R.string.jellyfin_settings_action), fontFamily = Archivo)
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
                // Header / selection controls (when inside a folder with importable items)
                if (!browser.isRoot && importableItems.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (selectedCount > 0) "$selectedCount SELECTED" else "${importableItems.size} COMICS AVAILABLE",
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
                                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(1.dp, DeepInk, RoundedCornerShape(4.dp))
                                        .clickable(role = Role.Button) { onClearSelection() }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        stringResource(R.string.jellyfin_deselect_all),
                                        fontFamily = Anton,
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (allSelected) Ochre else MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, if (allSelected) Ochre else DeepInk, RoundedCornerShape(4.dp))
                                    .clickable(role = Role.Button) {
                                        if (allSelected) onClearSelection() else onSelectAll()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (allSelected) stringResource(R.string.jellyfin_clear) else stringResource(R.string.jellyfin_select_all),
                                    fontFamily = Anton,
                                    fontSize = 10.5.sp,
                                    color = if (allSelected) Ink else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // Main content state dispatcher
                val failure = browser.failure
                if (browser.loading) {
                    JellyfinLoadingStateView(isRoot = browser.isRoot)
                } else if (failure != null) {
                    JellyfinErrorStateView(
                        failure = failure,
                        onRetry = onRetry,
                    )
                } else if (browser.isRoot) {
                    if (browser.views.isEmpty()) {
                        JellyfinEmptyStateView(isRoot = true)
                    } else {
                        LazyColumn(
                            state = libraryListState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                        ) {
                            items(browser.views, key = { it.id }) { view ->
                                JellyfinViewRow(
                                    view = view,
                                    onClick = { onViewClick(view) },
                                )
                            }
                        }
                    }
                } else {
                    if (browser.items.isEmpty()) {
                        JellyfinEmptyStateView(isRoot = false)
                    } else {
                        LazyColumn(
                            state = folderListState,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp),
                        ) {
                            items(browser.items, key = { it.id }) { item ->
                                val isDownloaded = item.id in browser.downloadedItemIds
                                val isSelected = item.id in browser.selectedItemIds

                                JellyfinItemRow(
                                    item = item,
                                    isDownloaded = isDownloaded,
                                    isSelected = isSelected,
                                    onClick = { onItemClick(item) },
                                    onToggleSelection = { onToggleSelection(item.id) },
                                    cover = itemCover,
                                )
                            }
                        }
                    }
                }

                // Bottom import action bar
                AnimatedVisibility(visible = selectedCount > 0 && !importing) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Ochre)
                            .clickable(role = Role.Button, onClick = onImportSelected)
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "DOWNLOAD $selectedCount ${if (selectedCount == 1) "COMIC" else "COMICS"}",
                            fontFamily = Anton,
                            fontSize = 15.sp,
                            letterSpacing = 0.5.sp,
                            color = Ink,
                        )
                    }
                }

                // In-progress import banner (DS08)
                AnimatedVisibility(visible = importing) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            importDetail ?: stringResource(R.string.importing),
                            fontFamily = Archivo,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (importProgress != null) {
                            LinearProgressIndicator(
                                progress = { importProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = Ochre,
                                trackColor = DeepInk.copy(alpha = 0.2f),
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = Ochre,
                                trackColor = DeepInk.copy(alpha = 0.2f),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .clickable(role = Role.Button, onClick = onCancelImport)
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    stringResource(R.string.cancel).uppercase(),
                                    fontFamily = Anton,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            )
        }
    }
}

/** Loading state view (DS08). */
@Composable
fun JellyfinLoadingStateView(
    isRoot: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator(
                color = Ochre,
                strokeWidth = 3.dp,
                modifier = Modifier.size(36.dp),
            )
            Text(
                text = stringResource(if (isRoot) R.string.jellyfin_loading_libraries else R.string.jellyfin_loading_items),
                fontFamily = Archivo,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Accessible, recoverable failure state view with non-color cues and high contrast (DS01, DS08). */
@Composable
fun JellyfinErrorStateView(
    failure: JellyfinFailureKind,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (icon: ImageVector, titleRes: Int, descRes: Int) = when (failure) {
        JellyfinFailureKind.UNAVAILABLE -> Triple(
            Icons.Default.CloudOff,
            R.string.jellyfin_error_server_unreachable_title,
            R.string.jellyfin_error_server_unreachable_desc,
        )
        JellyfinFailureKind.TIMEOUT -> Triple(
            Icons.Default.Schedule,
            R.string.jellyfin_error_timeout_title,
            R.string.jellyfin_error_timeout_desc,
        )
        JellyfinFailureKind.AUTHENTICATION -> Triple(
            Icons.Default.Lock,
            R.string.jellyfin_error_auth_expired_title,
            R.string.jellyfin_error_auth_expired_desc,
        )
        JellyfinFailureKind.ACCESS_DENIED -> Triple(
            Icons.Default.Lock,
            R.string.jellyfin_error_access_denied_title,
            R.string.jellyfin_error_access_denied_desc,
        )
        JellyfinFailureKind.NOT_FOUND -> Triple(
            Icons.Default.Folder,
            R.string.jellyfin_error_not_found_title,
            R.string.jellyfin_error_not_found_desc,
        )
        JellyfinFailureKind.SERVER_ERROR -> Triple(
            Icons.Default.Warning,
            R.string.jellyfin_error_server_title,
            R.string.jellyfin_error_server_desc,
        )
        else -> Triple(
            Icons.Default.Warning,
            R.string.jellyfin_error_generic_title,
            R.string.jellyfin_error_generic_desc,
        )
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 20.dp, vertical = 24.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(26.dp),
                )
            }

            Text(
                text = stringResource(titleRes),
                fontFamily = Archivo,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )

            Text(
                text = stringResource(descRes),
                fontFamily = Archivo,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.size(4.dp))

            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 120.dp, minHeight = 48.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Ochre)
                    .clickable(role = Role.Button, onClick = onRetry)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.jellyfin_retry),
                    fontFamily = Anton,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp,
                    color = Ink,
                )
            }
        }
    }
}

/** Accessible empty state view (DS08). */
@Composable
fun JellyfinEmptyStateView(
    isRoot: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 28.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isRoot) Icons.Default.Folder else Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = LeafSecondary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = stringResource(if (isRoot) R.string.jellyfin_no_libraries else R.string.jellyfin_no_items),
                fontFamily = Archivo,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(if (isRoot) R.string.jellyfin_no_libraries_detail else R.string.jellyfin_no_items_detail),
                fontFamily = Archivo,
                fontSize = 11.5.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun JellyfinViewRow(
    view: JellyfinView,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = "Library",
            tint = Ochre,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                view.name,
                fontFamily = Archivo,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            view.collectionType?.let { type ->
                Text(
                    type.uppercase(),
                    fontFamily = Archivo,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun JellyfinItemRow(
    item: JellyfinItem,
    isDownloaded: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggleSelection: () -> Unit,
    cover: @Composable (JellyfinItem, Modifier) -> Unit,
    showSelection: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (item.isFolder) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(48.dp))
        } else {
            cover(item, Modifier.size(width = 48.dp, height = 72.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.name, fontFamily = Archivo, fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (!item.isFolder) {
                Text(listOfNotNull(
                    item.fileExtension.uppercase(),
                    item.sizeBytes.takeIf { it > 0L }?.let(::formatFileSize),
                ).joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (item.played || item.playbackPositionTicks > 0L) {
                    Text(stringResource(if (item.played) R.string.jellyfin_read_on_server
                        else R.string.jellyfin_in_progress_on_server),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (isDownloaded) {
                    Text(stringResource(R.string.jellyfin_badge_downloaded),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (showSelection && !item.isFolder && item.isImportable && !isDownloaded) {
            val label = stringResource(R.string.jellyfin_select_download, item.name)
            Checkbox(checked = isSelected, onCheckedChange = { onToggleSelection() },
                modifier = Modifier.size(48.dp).semantics { contentDescription = label })
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(java.util.Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(java.util.Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
}

// =============================================================================
// PREVIEWS & ACCESSIBILITY REFERENCE GALLERY (DS01, DS08, DS12)
// =============================================================================

private fun previewMockViewsState(): JellyfinBrowserState = JellyfinBrowserState(
    currentParentId = null,
    currentTitle = "Jellyfin Libraries",
    breadcrumbs = emptyList(),
    views = listOf(
        JellyfinView("v1", "Comics", "books"),
        JellyfinView("v2", "Manga", "books"),
        JellyfinView("v3", "Graphic Novels", "books"),
    ),
    items = emptyList(),
    downloadedItemIds = emptySet(),
    selectedItemIds = emptySet(),
    loading = false,
    failure = null,
    generation = 1L,
)

private fun previewMockItemsState(): JellyfinBrowserState = JellyfinBrowserState(
    currentParentId = "v1",
    currentTitle = "Comics / Spider-Man",
    breadcrumbs = emptyList(),
    views = emptyList(),
    items = listOf(
        JellyfinItem(id = "c1", name = "Spider-Man #01.cbz", isFolder = false, container = "cbz", sizeBytes = 32_500_000L, played = true),
        JellyfinItem(id = "c2", name = "Spider-Man #02.cbz", isFolder = false, container = "cbz", sizeBytes = 28_400_000L, playbackPositionTicks = 40_000L),
        JellyfinItem(id = "c3", name = "Spider-Man #03.cbz", isFolder = false, container = "cbz", sizeBytes = 35_100_000L),
        JellyfinItem(id = "f1", name = "Spider-Gwen Series", isFolder = true),
    ),
    downloadedItemIds = setOf("c1"),
    selectedItemIds = setOf("c2"),
    loading = false,
    failure = null,
    generation = 2L,
)

@Preview(name = "Light Theme - Libraries", showBackground = true)
@Composable
fun JellyfinBrowserPreviewLight() {
    ComicReaderTheme(mode = ThemeMode.LIGHT) {
        Surface {
            JellyfinBrowserScreenContent(
                browser = previewMockViewsState(),
                importing = false,
                importProgress = null,
                importDetail = null,
                snackbarHostState = remember { SnackbarHostState() },
                onBack = {},
                onClearSelection = {},
                onSelectAll = {},
                onToggleSelection = {},
                onItemClick = {},
                onViewClick = {},
                onImportSelected = {},
                onCancelImport = {},
                onRetry = {},
            )
        }
    }
}

@Preview(name = "Dark Theme - Libraries", showBackground = true)
@Composable
fun JellyfinBrowserPreviewDark() {
    ComicReaderTheme(mode = ThemeMode.DARK) {
        Surface {
            JellyfinBrowserScreenContent(
                browser = previewMockViewsState(),
                importing = false,
                importProgress = null,
                importDetail = null,
                snackbarHostState = remember { SnackbarHostState() },
                onBack = {},
                onClearSelection = {},
                onSelectAll = {},
                onToggleSelection = {},
                onItemClick = {},
                onViewClick = {},
                onImportSelected = {},
                onCancelImport = {},
                onRetry = {},
            )
        }
    }
}

@Preview(name = "AMOLED Theme - Libraries", showBackground = true)
@Composable
fun JellyfinBrowserPreviewAmoled() {
    ComicReaderTheme(mode = ThemeMode.DARK, amoled = true) {
        Surface {
            JellyfinBrowserScreenContent(
                browser = previewMockViewsState(),
                importing = false,
                importProgress = null,
                importDetail = null,
                snackbarHostState = remember { SnackbarHostState() },
                onBack = {},
                onClearSelection = {},
                onSelectAll = {},
                onToggleSelection = {},
                onItemClick = {},
                onViewClick = {},
                onImportSelected = {},
                onCancelImport = {},
                onRetry = {},
            )
        }
    }
}

@Preview(name = "Dark Theme - Items & Selection", showBackground = true)
@Composable
fun JellyfinBrowserPreviewItems() {
    ComicReaderTheme(mode = ThemeMode.DARK) {
        Surface {
            JellyfinBrowserScreenContent(
                browser = previewMockItemsState(),
                importing = false,
                importProgress = null,
                importDetail = null,
                snackbarHostState = remember { SnackbarHostState() },
                onBack = {},
                onClearSelection = {},
                onSelectAll = {},
                onToggleSelection = {},
                onItemClick = {},
                onViewClick = {},
                onImportSelected = {},
                onCancelImport = {},
                onRetry = {},
            )
        }
    }
}

@Preview(name = "Dark Theme - Offline Error State", showBackground = true)
@Composable
fun JellyfinBrowserPreviewOfflineError() {
    ComicReaderTheme(mode = ThemeMode.DARK) {
        Surface {
            JellyfinBrowserScreenContent(
                browser = previewMockViewsState().copy(failure = JellyfinFailureKind.UNAVAILABLE),
                importing = false,
                importProgress = null,
                importDetail = null,
                snackbarHostState = remember { SnackbarHostState() },
                onBack = {},
                onClearSelection = {},
                onSelectAll = {},
                onToggleSelection = {},
                onItemClick = {},
                onViewClick = {},
                onImportSelected = {},
                onCancelImport = {},
                onRetry = {},
            )
        }
    }
}

@Preview(name = "Dark Theme - In-Progress Download", showBackground = true)
@Composable
fun JellyfinBrowserPreviewDownloading() {
    ComicReaderTheme(mode = ThemeMode.DARK) {
        Surface {
            JellyfinBrowserScreenContent(
                browser = previewMockItemsState(),
                importing = true,
                importProgress = 0.54f,
                importDetail = "Downloading Spider-Man #02.cbz (15.3 MB / 28.4 MB)",
                snackbarHostState = remember { SnackbarHostState() },
                onBack = {},
                onClearSelection = {},
                onSelectAll = {},
                onToggleSelection = {},
                onItemClick = {},
                onViewClick = {},
                onImportSelected = {},
                onCancelImport = {},
                onRetry = {},
            )
        }
    }
}

@Preview(name = "Large Font Scale 1.5x", fontScale = 1.5f, showBackground = true)
@Composable
fun JellyfinBrowserPreviewLargeFont() {
    ComicReaderTheme(mode = ThemeMode.DARK) {
        Surface {
            JellyfinBrowserScreenContent(
                browser = previewMockItemsState(),
                importing = false,
                importProgress = null,
                importDetail = null,
                snackbarHostState = remember { SnackbarHostState() },
                onBack = {},
                onClearSelection = {},
                onSelectAll = {},
                onToggleSelection = {},
                onItemClick = {},
                onViewClick = {},
                onImportSelected = {},
                onCancelImport = {},
                onRetry = {},
            )
        }
    }
}
