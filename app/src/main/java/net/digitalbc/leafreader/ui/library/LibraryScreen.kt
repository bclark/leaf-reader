package net.digitalbc.leafreader.ui.library

import android.graphics.BitmapFactory
import android.util.LruCache
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
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
import net.digitalbc.leafreader.ui.theme.Cream
import net.digitalbc.leafreader.ui.theme.CreamMuted
import net.digitalbc.leafreader.ui.theme.Crimson
import net.digitalbc.leafreader.ui.theme.DeepInk
import net.digitalbc.leafreader.ui.theme.DeepInkSurface
import net.digitalbc.leafreader.ui.theme.Ink
import net.digitalbc.leafreader.ui.theme.InkSoft
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenComic: (Long) -> Unit,
    onOpenSeries: (String, String?) -> Unit,
    onOpenReadingList: (Long) -> Unit = {},
    onOpenMenu: () -> Unit,
    onOpenDrive: () -> Unit,
) {
    val comics by viewModel.comics.collectAsStateWithLifecycle()
    val libraryItems by viewModel.libraryItems.collectAsStateWithLifecycle()
    val currentFilter by viewModel.libraryFilter.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val importStatus by viewModel.importStatus.collectAsStateWithLifecycle()
    val importDetail by viewModel.importDetail.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val defaultRtl by viewModel.defaultRightToLeft.collectAsStateWithLifecycle()
    val libraryColumns by viewModel.libraryColumns.collectAsStateWithLifecycle()
    val isEditMode by viewModel.isEditMode.collectAsStateWithLifecycle()
    val selectedComicIds by viewModel.selectedComicIds.collectAsStateWithLifecycle()

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

    var isSearchOpen by rememberSaveable { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var isSearchFocused by remember { mutableStateOf(false) }

    BackHandler(enabled = isEditMode) {
        viewModel.setEditMode(false)
    }

    BackHandler(enabled = isSearchOpen && !isEditMode) {
        isSearchOpen = false
        viewModel.clearSearch()
        focusManager.clearFocus()
    }

    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) {
            focusRequester.requestFocus()
        }
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
    val actualColumns = libraryColumns.coerceIn(2, availableColumns.last())
    val gridState = rememberLazyGridState()

    val rtlRotation by animateFloatAsState(
        targetValue = if (defaultRtl) 180f else 0f,
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "directionFlip",
    )

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
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
                    LeafWordmark()
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isSearchOpen) Ochre else PaperWhite.copy(alpha = 0.14f))
                                .clickable {
                                    isSearchOpen = !isSearchOpen
                                    if (!isSearchOpen) {
                                        viewModel.clearSearch()
                                        focusManager.clearFocus()
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = "Search",
                                tint = if (isSearchOpen) Ink else PaperWhite,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(PaperWhite.copy(alpha = 0.14f))
                                .clickable(onClick = onOpenMenu),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Menu",
                                tint = PaperWhite,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isSearchOpen,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 4.dp)
                            .comicShadow(offset = 3.dp, color = Color(0xB3000000))
                            .clip(RoundedCornerShape(6.dp))
                            .background(DeepInkSurface)
                            .border(
                                width = 2.dp,
                                color = if (isSearchFocused) Ochre else DeepInk,
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
                                tint = if (isSearchFocused) Ochre else CreamMuted,
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
                                    color = PaperWhite,
                                ),
                                cursorBrush = SolidColor(Ochre),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                                decorationBox = { innerTextField ->
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            "SEARCH COMICS BY TITLE…",
                                            fontFamily = Archivo,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            color = CreamMuted.copy(alpha = 0.55f),
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
                                        .background(PaperWhite.copy(alpha = 0.12f))
                                        .clickable { viewModel.clearSearch() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = PaperWhite,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            Box(Modifier.matchParentSize().halftone(LeafSecondary, alpha = 0.05f))

            LazyVerticalGrid(
                columns = GridCells.Fixed(actualColumns),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(horizontalSpacing),
                verticalArrangement = Arrangement.spacedBy(verticalSpacing),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (isEditMode) {
                            OchreBadge("${selectedComicIds.size} SELECTED")
                        } else if (searchQuery.isNotBlank()) {
                            OchreBadge("FOUND ${comics.size} ${if (comics.size == 1) "COMIC" else "COMICS"}")
                        } else {
                            OchreBadge("YOUR LIBRARY")
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!isEditMode && comics.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(InkSoft)
                                        .border(1.dp, Ink, RoundedCornerShape(4.dp))
                                        .clickable { viewModel.setEditMode(true) }
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "EDIT",
                                        fontFamily = Anton,
                                        fontSize = 11.sp,
                                        color = Cream,
                                    )
                                }
                            } else if (isEditMode) {
                                val allSelected = comics.isNotEmpty() && selectedComicIds.size == comics.size
                                Box(
                                    modifier = Modifier
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (allSelected) Ochre else InkSoft)
                                        .border(1.dp, Ink, RoundedCornerShape(4.dp))
                                        .clickable {
                                            if (allSelected) viewModel.clearComicSelection()
                                            else viewModel.selectAllComics(comics.map { it.id })
                                        }
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        if (allSelected) "DESELECT ALL" else "SELECT ALL",
                                        fontFamily = Anton,
                                        fontSize = 11.sp,
                                        color = if (allSelected) Ink else Cream,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Ochre)
                                        .clickable { viewModel.setEditMode(false) }
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "DONE",
                                        fontFamily = Anton,
                                        fontSize = 11.sp,
                                        color = Ink,
                                    )
                                }
                            }

                            if (!isEditMode) {
                                Box(
                                    modifier = Modifier
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Ochre)
                                        .border(1.dp, Ink, RoundedCornerShape(4.dp))
                                        .clickable {
                                            val idx = availableColumns.indexOf(actualColumns)
                                            val next = availableColumns[(if (idx >= 0) idx + 1 else 0) % availableColumns.size]
                                            viewModel.setLibraryColumns(next)
                                        }
                                        .padding(horizontal = 9.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        ColumnDensityIcon(columns = actualColumns)
                                        Text(
                                            "$actualColumns",
                                            fontFamily = Anton,
                                            fontSize = 11.sp,
                                            color = Ink,
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .height(30.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Ochre)
                                        .border(1.dp, Ink, RoundedCornerShape(4.dp))
                                        .clickable { viewModel.toggleDefaultDirection() }
                                        .padding(horizontal = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.graphicsLayer { rotationY = rtlRotation },
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                                            contentDescription = null,
                                            tint = Ink,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                            contentDescription = if (defaultRtl) "Reading Direction: RTL" else "Reading Direction: LTR",
                                            tint = Ink,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (!isEditMode) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LibraryFilter.entries.forEach { filter ->
                                val isSelected = currentFilter == filter
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (isSelected) Ochre else InkSoft)
                                        .border(1.dp, if (isSelected) Ochre else Ink, RoundedCornerShape(4.dp))
                                        .clickable { viewModel.setLibraryFilter(filter) }
                                        .padding(horizontal = 14.dp, vertical = 7.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = filter.label,
                                        fontFamily = Anton,
                                        fontSize = 12.sp,
                                        color = if (isSelected) Ink else Cream,
                                    )
                                }
                            }
                        }
                    }
                }

                if (libraryItems.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        if (searchQuery.isNotBlank()) {
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
                                    color = Cream,
                                )
                                Text(
                                    "No items found matching \"$searchQuery\"",
                                    fontFamily = Archivo,
                                    fontSize = 13.sp,
                                    color = CreamMuted,
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
                                "No comics yet. Tap LOAD COMIC to add a CBZ, CBR, or PDF.",
                                fontFamily = Archivo,
                                fontSize = 13.sp,
                                color = CreamMuted,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                }

                items(libraryItems, key = { item ->
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
                                        comicOptionsTarget = item.comic
                                    }
                                },
                            )
                        }
                        is LibraryItem.SeriesStack -> {
                            ComicStackCard(
                                stack = item,
                                columns = actualColumns,
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

                if (searchQuery.isBlank() && !isEditMode) {
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

            if (!isEditMode && !importing) {
                val isListsFilter = currentFilter == LibraryFilter.READING_LISTS
                ExtendedFloatingActionButton(
                    onClick = {
                        if (!importing) {
                            if (isListsFilter) showCreateListDialog = true
                            else showSourceDialog = true
                        }
                    },
                    containerColor = LeafAccent,
                    contentColor = DeepInk,
                    shape = RoundedCornerShape(4.dp),
                    elevation = FloatingActionButtonDefaults.elevation(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .navigationBarsPadding()
                        .padding(20.dp),
                    icon = {
                        Icon(Icons.Default.Add, contentDescription = if (isListsFilter) "Create list" else "Add comic", tint = DeepInk)
                    },
                    text = {
                        Text(
                            text = if (isListsFilter) "CREATE LIST" else "ADD COMIC",
                            fontFamily = Anton,
                            fontSize = 14.sp,
                            color = DeepInk,
                            letterSpacing = 0.6.sp,
                        )
                    },
                )
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
                                .background(DeepInkSurface)
                                .border(2.dp, Ochre, RoundedCornerShape(6.dp))
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "${selectedComicIds.size} ${if (selectedComicIds.size == 1) "COMIC" else "COMICS"} SELECTED",
                                        fontFamily = Anton,
                                        fontSize = 15.sp,
                                        color = PaperWhite,
                                    )
                                    Text(
                                        text = "Group into series, add to list, or delete",
                                        fontFamily = Archivo,
                                        fontSize = 11.sp,
                                        color = PaperWhiteMuted,
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                            color = PaperWhite,
                                        )
                                    }
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

    if (showSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title = { Text("Load comic", fontFamily = Anton, color = PaperWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(LeafPrimary)
                            .clickable {
                                showSourceDialog = false
                                picker.launch(arrayOf("*/*"))
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
                            .background(DeepInkSurface)
                            .border(1.5.dp, LeafSecondary, RoundedCornerShape(4.dp))
                            .clickable {
                                showSourceDialog = false
                                folderPicker.launch(null)
                            }
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text("FROM DEVICE (FOLDER)", fontFamily = Anton, fontSize = 14.sp, color = PaperWhite)
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
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSourceDialog = false }) { Text("CANCEL", color = PaperWhiteMuted) }
            },
            containerColor = DeepInkSurface,
        )
    }

    if (showGroupDialog) {
        AlertDialog(
            onDismissRequest = { showGroupDialog = false },
            title = { Text("Set Series Name", fontFamily = Anton, color = PaperWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Comics with the same Series Name automatically group into a Series Stack in your library.",
                        fontFamily = Archivo,
                        fontSize = 12.sp,
                        color = PaperWhiteMuted,
                    )
                    OutlinedTextField(
                        value = seriesInput,
                        onValueChange = { seriesInput = it },
                        label = { Text("Series Name (e.g. Deadpool)", color = CreamMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Ochre,
                            unfocusedBorderColor = DeepInk,
                            focusedTextColor = PaperWhite,
                            unfocusedTextColor = PaperWhite,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = volumeInput,
                        onValueChange = { volumeInput = it },
                        label = { Text("Volume (Optional, e.g. Vol. 2)", color = CreamMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Ochre,
                            unfocusedBorderColor = DeepInk,
                            focusedTextColor = PaperWhite,
                            unfocusedTextColor = PaperWhite,
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
                    Text("SAVE & GROUP", color = Ochre, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showGroupDialog = false }) { Text("CANCEL", color = PaperWhiteMuted) }
            },
            containerColor = DeepInkSurface,
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
                    color = PaperWhite,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (isCreatingListInDialog) {
                        Text(
                            text = "Enter a name for your new reading list.",
                            fontFamily = Archivo,
                            fontSize = 12.sp,
                            color = PaperWhiteMuted,
                        )
                        OutlinedTextField(
                            value = newListNameInDialog,
                            onValueChange = { newListNameInDialog = it },
                            label = { Text("List Name (e.g. Favorites)", color = CreamMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Ochre,
                                unfocusedBorderColor = DeepInk,
                                focusedTextColor = PaperWhite,
                                unfocusedTextColor = PaperWhite,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            text = "Choose a reading list to add ${selectedComicIds.size} selected ${if (selectedComicIds.size == 1) "comic" else "comics"}.",
                            fontFamily = Archivo,
                            fontSize = 12.sp,
                            color = PaperWhiteMuted,
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
                                color = CreamMuted,
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
                                            .background(DeepInk)
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
                                            color = PaperWhite,
                                        )
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Select",
                                            tint = Ochre,
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
                        Text("CREATE & ADD", color = Ochre, fontWeight = FontWeight.Bold)
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
                    Text("CANCEL", color = PaperWhiteMuted)
                }
            },
            containerColor = DeepInkSurface,
        )
    }

    if (showCreateListDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateListDialog = false
                createListNameInput = ""
            },
            title = {
                Text("Create Reading List", fontFamily = Anton, color = PaperWhite)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter a name for your new reading list.",
                        fontFamily = Archivo,
                        fontSize = 12.sp,
                        color = PaperWhiteMuted,
                    )
                    OutlinedTextField(
                        value = createListNameInput,
                        onValueChange = { createListNameInput = it },
                        label = { Text("List Name (e.g. Favorites)", color = CreamMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Ochre,
                            unfocusedBorderColor = DeepInk,
                            focusedTextColor = PaperWhite,
                            unfocusedTextColor = PaperWhite,
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
                        Text("CREATE & OPEN", color = Ochre, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showCreateListDialog = false
                    createListNameInput = ""
                }) {
                    Text("CANCEL", color = PaperWhiteMuted)
                }
            },
            containerColor = DeepInkSurface,
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
                    Text(text = list.name.uppercase(), fontFamily = Anton, color = PaperWhite, modifier = Modifier.weight(1f))
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
                                .background(InkSoft)
                                .border(1.dp, Ink, RoundedCornerShape(3.dp))
                                .clickable {
                                    renameListInput = list.name
                                    showRenameListDialog = true
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text("RENAME", fontFamily = Anton, fontSize = 10.5.sp, color = Cream)
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
                            Text("DELETE", fontFamily = Anton, fontSize = 10.5.sp, color = PaperWhite)
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
                            color = LeafAccent,
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
                            color = PaperWhiteMuted,
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
                                        .background(DeepInk)
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
                                            .background(InkSoft),
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
                                        color = PaperWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(PaperWhite.copy(alpha = 0.12f))
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
                TextButton(onClick = { activeReadingList = null }) { Text("CLOSE", color = PaperWhiteMuted) }
            },
            containerColor = DeepInkSurface,
        )
    }

    if (showRenameListDialog && activeReadingList != null) {
        val list = activeReadingList!!
        AlertDialog(
            onDismissRequest = { showRenameListDialog = false },
            title = { Text("Rename Reading List", fontFamily = Anton, color = PaperWhite) },
            text = {
                OutlinedTextField(
                    value = renameListInput,
                    onValueChange = { renameListInput = it },
                    label = { Text("List Name", color = CreamMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Ochre,
                        unfocusedBorderColor = DeepInk,
                        focusedTextColor = PaperWhite,
                        unfocusedTextColor = PaperWhite,
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
                    Text("SAVE", color = Ochre, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameListDialog = false }) { Text("CANCEL", color = PaperWhiteMuted) }
            },
            containerColor = DeepInkSurface,
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
            title = { Text("Add Comics to ${list.name}", fontFamily = Anton, color = PaperWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Select downloaded comics from your library to add to this list.",
                        fontFamily = Archivo,
                        fontSize = 12.sp,
                        color = PaperWhiteMuted,
                    )

                    if (availableComicsForList.isEmpty()) {
                        Text(
                            "All downloaded comics in your library are already in this list.",
                            fontFamily = Archivo,
                            fontSize = 12.sp,
                            color = CreamMuted,
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
                                        .background(if (isSelected) DeepInkSurface else DeepInk)
                                        .border(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) Ochre else LeafSecondary.copy(alpha = 0.4f),
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
                                            .background(InkSoft),
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
                                        color = PaperWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(if (isSelected) Ochre else DeepInk)
                                            .border(1.5.dp, if (isSelected) Ochre else PaperWhiteMuted.copy(alpha = 0.45f), RoundedCornerShape(3.dp)),
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
                        Text("ADD (${selectedComicsForList.size})", color = Ochre, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddComicsToListDialog = false
                    selectedComicsForList = emptySet()
                }) {
                    Text("CANCEL", color = PaperWhiteMuted)
                }
            },
            containerColor = DeepInkSurface,
        )
    }

    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text("Delete selected comics?", fontFamily = Anton, color = PaperWhite) },
            text = { Text("This removes ${selectedComicIds.size} imported comic copies. Original files are untouched.", color = PaperWhiteMuted) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSelectedComics()
                    showBatchDeleteDialog = false
                }) {
                    Text("DELETE", color = ComicRose, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) { Text("CANCEL", color = PaperWhiteMuted) }
            },
            containerColor = DeepInkSurface,
        )
    }

    comicOptionsTarget?.let { comic ->
        val isRead = comic.pageCount > 0 && comic.lastPage >= comic.pageCount - 1
        AlertDialog(
            onDismissRequest = { comicOptionsTarget = null },
            title = {
                Text(
                    text = comic.title.uppercase(),
                    fontFamily = Anton,
                    color = PaperWhite,
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
                        color = LeafAccent,
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
                            .background(DeepInk)
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
                        Icon(Icons.Default.Edit, contentDescription = null, tint = Ochre, modifier = Modifier.size(18.dp))
                        Text("SET SERIES NAME", fontFamily = Anton, fontSize = 13.sp, color = PaperWhite)
                    }

                    // Option 3: MARK AS READ / UNREAD
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(DeepInk)
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
                            tint = Ochre,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            if (isRead) "MARK AS UNREAD" else "MARK AS READ",
                            fontFamily = Anton,
                            fontSize = 13.sp,
                            color = PaperWhite,
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
                    Text("CLOSE", color = PaperWhiteMuted)
                }
            },
            containerColor = DeepInkSurface,
        )
    }

    pendingDelete?.let { comic ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Remove comic?", fontFamily = Anton, color = PaperWhite) },
            text = { Text("This removes the imported copy. The original file is untouched.", color = PaperWhiteMuted) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteComic(comic.id); pendingDelete = null }) {
                    Text("DELETE", color = ComicRose, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("CANCEL", color = PaperWhiteMuted) } },
            containerColor = DeepInkSurface,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ComicCard(
    comic: ComicEntity,
    columns: Int = 2,
    isEditMode: Boolean = false,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val pct = if (comic.pageCount > 0) ((comic.lastPage + 1f) / comic.pageCount).coerceIn(0f, 1f) else 0f
    val started = comic.lastPage > 0
    val borderWidth = if (columns >= 4) 2.dp else 3.dp
    val shadowOffset = if (columns >= 4) 3.dp else 5.dp
    val progressHeight = if (columns >= 4) 5.dp else 7.dp

    val fileSize = remember(comic.filePath) {
        runCatching { File(comic.filePath).length() }.getOrDefault(0L)
    }
    val sizeText = formatFileSize(fileSize)

    Column(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .comicShadow(offset = shadowOffset, color = Color(0xB3000000))
                .clip(RoundedCornerShape(4.dp))
                .background(InkSoft)
                .border(
                    width = if (isSelected) 3.dp else borderWidth,
                    color = if (isSelected) Ochre else Ink,
                    shape = RoundedCornerShape(4.dp),
                ),
        ) {
            val cover = rememberCover(comic.coverPath)
            if (cover != null) {
                Image(cover, comic.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                GeneratedCover(comic.title, columns = columns)
            }

            if (isEditMode) {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .size(24.dp)
                        .align(Alignment.TopEnd)
                        .clip(CircleShape)
                        .background(if (isSelected) Ochre else DeepInk.copy(alpha = 0.8f))
                        .border(1.5.dp, if (isSelected) Ink else PaperWhite, CircleShape),
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

            if (!isEditMode) {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(progressHeight).background(Ink)) {
                    Box(Modifier.fillMaxWidth(pct).fillMaxHeight().background(Ochre))
                }
            }
        }
        Text(
            comic.title.uppercase(),
            fontFamily = Archivo,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (columns >= 5) 10.sp else if (columns >= 4) 11.sp else 12.sp,
            color = Cream,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = if (columns >= 4) 6.dp else 9.dp),
        )
        Text(
            if (started) "${(pct * 100).toInt()}% · pg ${comic.lastPage + 1}/${comic.pageCount}${if (sizeText.isNotEmpty()) " · $sizeText" else ""}"
            else "${comic.pageCount} pages${if (sizeText.isNotEmpty()) " · $sizeText" else ""}",
            fontFamily = Archivo,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (columns >= 4) 8.5.sp else 9.5.sp,
            color = CreamMuted,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
internal fun GeneratedCover(title: String, columns: Int = 2) {
    val fontSize = if (columns >= 5) 13.sp else if (columns >= 4) 15.sp else if (columns == 3) 18.sp else 22.sp
    val padding = if (columns >= 4) 8.dp else 12.dp
    Box(Modifier.fillMaxSize().background(InkSoft).halftone(Crimson, alpha = 0.18f)) {
        Text(
            title.uppercase(),
            fontFamily = Anton,
            fontSize = fontSize,
            color = Cream,
            modifier = Modifier.align(Alignment.BottomStart).padding(padding),
        )
    }
}

@Composable
fun SmbDriveScreen(
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
) {
    val entries by viewModel.smbEntries.collectAsStateWithLifecycle()
    val loading by viewModel.smbLoading.collectAsStateWithLifecycle()
    val path by viewModel.smbPath.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val importStatus by viewModel.importStatus.collectAsStateWithLifecycle()
    val importDetail by viewModel.importDetail.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedPaths by remember(path, entries) { mutableStateOf(emptySet<String>()) }
    val fileEntries = remember(entries) { entries.filter { !it.isDirectory } }
    val selectedCount = selectedPaths.size
    val totalFiles = fileEntries.size
    val allSelected = totalFiles > 0 && selectedCount == totalFiles
    val totalSelectedBytes = remember(selectedPaths, fileEntries) {
        fileEntries.filter { it.path in selectedPaths }.sumOf { it.sizeBytes }
    }

    BackHandler(enabled = !importing && selectedCount > 0) {
        selectedPaths = emptySet()
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
                                selectedPaths = emptySet()
                            } else if (path.isNotEmpty()) {
                                viewModel.loadSmbDirectory(path.substringBeforeLast('\\', ""))
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
                            color = if (selectedCount > 0) LeafAccent else PaperWhiteMuted,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (selectedCount > 0 && !allSelected) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(DeepInkSurface)
                                        .border(1.dp, DeepInk, RoundedCornerShape(4.dp))
                                        .clickable { selectedPaths = emptySet() }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "DESELECT ALL",
                                        fontFamily = Anton,
                                        fontSize = 10.5.sp,
                                        color = PaperWhiteMuted,
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (allSelected) Ochre else DeepInkSurface)
                                    .border(1.dp, if (allSelected) Ochre else DeepInk, RoundedCornerShape(4.dp))
                                    .clickable {
                                        selectedPaths = if (allSelected) emptySet() else fileEntries.map { it.path }.toSet()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (allSelected) "DESELECT ALL" else "SELECT ALL",
                                    fontFamily = Anton,
                                    fontSize = 10.5.sp,
                                    color = if (allSelected) DeepInk else PaperWhite,
                                )
                            }
                        }
                    }
                }

                Box(Modifier.fillMaxWidth().weight(1f)) {
                    if (loading) {
                        CircularProgressIndicator(
                            color = Ochre,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    } else if (entries.isEmpty()) {
                        Text(
                            "No folders or comics found here.",
                            fontFamily = Archivo,
                            fontSize = 13.sp,
                            color = PaperWhiteMuted,
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
                                            selectedPaths = if (isSelected) selectedPaths - entry.path else selectedPaths + entry.path
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

                    if (selectedCount > 0 && !importing) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .comicShadow(offset = 3.dp, color = Color(0xB3000000))
                                .clip(RoundedCornerShape(6.dp))
                                .background(DeepInkSurface)
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
                                        color = PaperWhite,
                                    )
                                    Text(
                                        text = formatFileSize(totalSelectedBytes),
                                        fontFamily = Archivo,
                                        fontSize = 11.5.sp,
                                        color = LeafAccent,
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(LeafAccent)
                                        .clickable {
                                            val toImport = fileEntries.filter { it.path in selectedPaths }
                                            viewModel.importSmbComics(toImport) {
                                                selectedPaths = emptySet()
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
    val bgColor = if (isSelected) DeepInkSurface else DeepInk
    val borderColor = if (isSelected) LeafAccent else LeafSecondary.copy(alpha = 0.4f)

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
            tint = if (isDirectory) LeafAccent else if (isSelected) LeafAccent else PaperWhite,
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
                color = PaperWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!isDirectory && sizeBytes > 0L) {
                Text(
                    formatFileSize(sizeBytes),
                    fontFamily = Archivo,
                    fontSize = 11.sp,
                    color = PaperWhiteMuted,
                )
            }
        }
        if (isDirectory) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = PaperWhiteMuted.copy(alpha = 0.4f),
                modifier = Modifier
                    .size(14.dp)
                    .graphicsLayer(rotationZ = 180f),
            )
        } else {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (isSelected) LeafAccent else DeepInk)
                    .border(
                        width = 1.5.dp,
                        color = if (isSelected) LeafAccent else PaperWhiteMuted.copy(alpha = 0.45f),
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

private val coverBitmapCache = LruCache<String, ImageBitmap>(200)

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
                val threshold = if (cardHeightPx > 0f) cardHeightPx * 0.35f else 180f
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

@Composable
private fun ComicStackCard(
    stack: LibraryItem.SeriesStack,
    columns: Int = 2,
    onClick: () -> Unit,
) {
    val pct = stack.progressFraction
    val borderWidth = if (columns >= 4) 2.dp else 3.dp
    val shadowOffset = if (columns >= 4) 3.dp else 5.dp
    val progressHeight = if (columns >= 4) 5.dp else 7.dp

    val initialIndex = remember(stack.issues, stack.topCoverComic) {
        val idx = stack.issues.indexOfFirst { it.id == stack.topCoverComic.id }
        if (idx >= 0) idx else 0
    }
    var topIndex by rememberSaveable(stack.seriesName, stack.volume) { mutableIntStateOf(initialIndex) }
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

    val coroutineScope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    var cardHeightPx by remember { mutableFloatStateOf(0f) }
    val progress = if (cardHeightPx > 0f) (offsetY.value / cardHeightPx).coerceIn(0f, 1f) else 0f

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
                        topIndex = if (issueCount > 0) (topIndex + 1) % issueCount else topIndex + 1
                    },
                    onClick = onClick,
                )
        ) {
            // Neat Stack Layer 3 (for 3+ issues) - highest top offset
            if (stack.totalIssues >= 3) {
                val cover3 = rememberCover(layer3Comic?.coverPath)
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = lerp((-8).dp.toPx(), (-4).dp.toPx(), progress)
                            scaleX = lerp(0.93f, 0.96f, progress)
                        }
                        .clip(RoundedCornerShape(4.dp))
                        .background(InkSoft)
                ) {
                    if (cover3 != null) {
                        Image(cover3, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
            }

            // Neat Stack Layer 2 (for 2+ issues) - middle top offset
            if (stack.totalIssues >= 2) {
                val cover2 = rememberCover(layer2Comic?.coverPath)
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            translationY = lerp((-4).dp.toPx(), 0f, progress)
                            scaleX = lerp(0.96f, 1f, progress)
                        }
                        .comicShadow(offset = (shadowOffset.value * progress).dp, color = Color(0xB3000000))
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (progress > 0.5f) InkSoft else DeepInkSurface)
                        .then(
                            if (progress > 0.5f) Modifier.border(borderWidth, Ink, RoundedCornerShape(4.dp))
                            else Modifier
                        )
                ) {
                    if (cover2 != null) {
                        Image(cover2, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }

                    val layer2IssueNum = if (issueCount > 0) ((topIndex + 1) % issueCount + issueCount) % issueCount + 1 else 2
                    val layer2BadgeText = layer2Comic?.issueNumber?.let { num ->
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
                            .background(Ink.copy(alpha = 0.85f))
                            .border(1.dp, Ochre, RoundedCornerShape(3.dp))
                            .padding(horizontal = if (columns >= 4) 4.dp else 5.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = layer2BadgeText,
                            fontFamily = Anton,
                            fontSize = if (columns >= 4) 7.sp else 8.sp,
                            color = Cream,
                        )
                    }

                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(progressHeight).background(Ink)) {
                        Box(Modifier.fillMaxWidth(pct).fillMaxHeight().background(Ochre))
                    }
                }
            }

            // Front Layer (Main cover)
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = offsetY.value
                        alpha = if (progress > 0.75f) (1f - (progress - 0.75f) / 0.25f).coerceIn(0f, 1f) else 1f
                    }
                    .comicShadow(offset = shadowOffset, color = Color(0xB3000000))
                    .clip(RoundedCornerShape(4.dp))
                    .background(InkSoft)
                    .border(width = borderWidth, color = Ink, shape = RoundedCornerShape(4.dp)),
            ) {
                val cover1 = rememberCover(currentTopComic.coverPath)
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
                        .background(Ink.copy(alpha = 0.85f))
                        .border(1.dp, Ochre, RoundedCornerShape(3.dp))
                        .padding(horizontal = if (columns >= 4) 4.dp else 5.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center,
                ) {
                    val currentIssueNum = if (issueCount > 0) (topIndex % issueCount + issueCount) % issueCount + 1 else 1
                    val frontBadgeText = if (stack.totalIssues > 1) {
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
                        color = Cream,
                    )
                }

                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(progressHeight).background(Ink)) {
                    Box(Modifier.fillMaxWidth(pct).fillMaxHeight().background(Ochre))
                }
            }
        }
        Text(
            stack.seriesName.uppercase(),
            fontFamily = Archivo,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (columns >= 5) 10.sp else if (columns >= 4) 11.sp else 12.sp,
            color = Cream,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = if (columns >= 4) 6.dp else 9.dp),
        )
        Text(
            "${stack.totalIssues} issues ${if (stack.unreadIssues > 0) "· ${stack.unreadIssues} unread" else "· read"}",
            fontFamily = Archivo,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (columns >= 4) 8.5.sp else 9.5.sp,
            color = CreamMuted,
            modifier = Modifier.padding(top = 2.dp),
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
                        .clip(RoundedCornerShape(4.dp))
                        .background(InkSoft)
                ) {
                    if (backCover != null) {
                        Image(
                            backCover,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            alpha = 0.85f,
                        )
                    }
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
                        .comicShadow(offset = (shadowOffset.value * progress).dp, color = Color(0xB3000000))
                        .clip(RoundedCornerShape(4.dp))
                        .background(DeepInkSurface)
                ) {
                    if (midCover != null) {
                        Image(
                            midCover,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            alpha = lerp(0.85f, 1f, progress),
                        )
                    }

                    val layer2ItemNum = if (totalCount > 0) ((topIndex + 1) % totalCount + totalCount) % totalCount + 1 else 2
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(if (columns >= 4) 4.dp else 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Ink.copy(alpha = 0.85f))
                            .border(1.dp, Ochre, RoundedCornerShape(3.dp))
                            .padding(horizontal = if (columns >= 4) 4.dp else 5.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "ITEM $layer2ItemNum OF ${item.itemCount}",
                            fontFamily = Anton,
                            fontSize = if (columns >= 4) 7.sp else 8.sp,
                            color = Cream,
                        )
                    }
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
                    .comicShadow(offset = shadowOffset, color = Color(0xB3000000))
                    .clip(RoundedCornerShape(4.dp))
                    .background(DeepInkSurface),
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
                        .background(Ink.copy(alpha = 0.85f))
                        .border(1.dp, Ochre, RoundedCornerShape(3.dp))
                        .padding(horizontal = if (columns >= 4) 4.dp else 5.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val currentItemNum = if (totalCount > 0) (topIndex % totalCount + totalCount) % totalCount + 1 else 1
                    Text(
                        text = if (item.itemCount > 1) "ITEM $currentItemNum OF ${item.itemCount}" else "${item.itemCount} ${if (item.itemCount == 1) "ITEM" else "ITEMS"}",
                        fontFamily = Anton,
                        fontSize = if (columns >= 4) 7.sp else 8.sp,
                        color = Cream,
                    )
                }
            }
        }

        Text(
            item.readingList.name.uppercase(),
            fontFamily = Archivo,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (columns >= 5) 10.sp else if (columns >= 4) 11.sp else 12.sp,
            color = Cream,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = if (columns >= 4) 6.dp else 9.dp),
        )
        Text(
            "${item.itemCount} ${if (item.itemCount == 1) "item" else "items"}",
            fontFamily = Archivo,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (columns >= 4) 8.5.sp else 9.5.sp,
            color = CreamMuted,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun AddTile(
    importing: Boolean,
    progress: Float,
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
    val pct = (progress * 100).toInt().coerceIn(0, 100)

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(0.7f)
            .clip(RoundedCornerShape(4.dp))
            .background(DeepInkSurface)
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
                    if (status != null) "$pct% · $status".uppercase()
                    else if (progress > 0f) "LOADING $pct%"
                    else "LOADING…"
                } else if (isListsFilter) "CREATE LIST" else "LOAD COMIC",
                fontFamily = Anton,
                fontSize = fontSize,
                color = PaperWhite,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (importing) {
                LinearProgressIndicator(
                    progress = { if (progress > 0f) progress else 0.05f },
                    color = LeafAccent,
                    trackColor = DeepInk,
                    modifier = Modifier.fillMaxWidth().height(if (columns >= 4) 4.dp else 6.dp).clip(RoundedCornerShape(3.dp)),
                )
                if (detail != null) {
                    Text(
                        detail,
                        fontFamily = Archivo,
                        fontSize = if (columns >= 4) 8.sp else 9.5.sp,
                        color = PaperWhiteMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(DeepInk)
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
private fun ImportProgressBanner(
    importing: Boolean,
    progress: Float,
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
        val pct = (progress * 100).toInt().coerceIn(0, 100)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .comicShadow(offset = 4.dp, color = Color(0xB3000000))
                .clip(RoundedCornerShape(8.dp))
                .background(DeepInkSurface)
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
                            color = LeafAccent,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = status?.uppercase() ?: "DOWNLOADING COMIC…",
                            fontFamily = Anton,
                            fontSize = 13.sp,
                            color = PaperWhite,
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
                            text = "$pct%",
                            fontFamily = Anton,
                            fontSize = 13.sp,
                            color = LeafAccent,
                            letterSpacing = 0.5.sp,
                        )
                    }
                }

                LinearProgressIndicator(
                    progress = { if (progress > 0f) progress else 0.05f },
                    color = LeafAccent,
                    trackColor = DeepInk,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(3.5.dp)),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = detail ?: if (progress > 0f) "$pct% completed" else "Connecting…",
                        fontFamily = Archivo,
                        fontSize = 11.5.sp,
                        color = PaperWhiteMuted,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(DeepInk)
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
