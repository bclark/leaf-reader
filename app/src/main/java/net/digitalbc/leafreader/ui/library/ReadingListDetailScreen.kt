package net.digitalbc.leafreader.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.digitalbc.leafreader.data.db.ComicEntity
import net.digitalbc.leafreader.ui.brand.OchreBadge
import net.digitalbc.leafreader.ui.brand.comicShadow
import net.digitalbc.leafreader.ui.brand.halftone
import net.digitalbc.leafreader.ui.theme.Anton
import net.digitalbc.leafreader.ui.theme.Archivo
import net.digitalbc.leafreader.ui.theme.ComicRose
import net.digitalbc.leafreader.ui.theme.Cream
import net.digitalbc.leafreader.ui.theme.CreamMuted
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReadingListDetailScreen(
    listId: Long,
    viewModel: LibraryViewModel,
    onOpenComic: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val readingList by viewModel.observeReadingList(listId).collectAsStateWithLifecycle(initialValue = null)
    val listComics by viewModel.observeComicsForList(listId).collectAsStateWithLifecycle(initialValue = emptyList())
    val allComics by viewModel.comics.collectAsStateWithLifecycle()
    val libraryColumns by viewModel.libraryColumns.collectAsStateWithLifecycle()

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

    val allCompleted = listComics.isNotEmpty() && listComics.all { it.pageCount > 0 && it.lastPage >= it.pageCount - 1 }
    val nextUnread = listComics.firstOrNull { it.pageCount > 0 && it.lastPage < it.pageCount - 1 }
        ?: listComics.firstOrNull()

    val continueButtonText = when {
        allCompleted && listComics.isNotEmpty() -> "READ FROM START: ${listComics.first().title.uppercase()}"
        nextUnread != null && nextUnread.lastPage > 0 -> "CONTINUE: ${nextUnread.title.uppercase()}"
        nextUnread != null -> "START: ${nextUnread.title.uppercase()}"
        else -> null
    }

    var showAddComicsDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }
    var addSearchQuery by remember { mutableStateOf("") }
    var selectedToAdd by remember { mutableStateOf(emptySet<Long>()) }

    var selectedComicForOptions by remember { mutableStateOf<ComicEntity?>(null) }
    var comicToRemove by remember { mutableStateOf<ComicEntity?>(null) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // App Bar
        Surface(
            color = LeafPrimary,
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = readingList?.name?.uppercase() ?: "READING LIST",
                        fontFamily = Anton,
                        fontSize = 18.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "CUSTOM STORYLINE / READING LIST",
                        fontFamily = Archivo,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = CreamMuted,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .clickable {
                                renameInput = readingList?.name.orEmpty()
                                showRenameDialog = true
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Rename list",
                            tint = Color.White,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ComicRose.copy(alpha = 0.85f))
                            .clickable { showDeleteDialog = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete list",
                            tint = Color.White,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            Box(Modifier.matchParentSize().halftone(LeafSecondary, alpha = 0.05f))

            LazyVerticalGrid(
                columns = GridCells.Fixed(actualColumns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(if (actualColumns >= 4) 12.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(if (actualColumns >= 4) 14.dp else 18.dp),
            ) {
                // Hero Card Header
                item(span = { GridItemSpan(actualColumns) }) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DeepInkSurface),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .comicShadow(offset = 4.dp, color = Color(0xB3000000)),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OchreBadge("${listComics.size} ${if (listComics.size == 1) "COMIC" else "COMICS"} IN LIST")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(LeafAccent)
                                            .clickable {
                                                selectedToAdd = emptySet()
                                                addSearchQuery = ""
                                                showAddComicsDialog = true
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(Icons.Default.Add, contentDescription = null, tint = DeepInk, modifier = Modifier.size(14.dp))
                                            Text("ADD COMICS", fontFamily = Anton, fontSize = 11.sp, color = DeepInk)
                                        }
                                    }
                                    if (listComics.isNotEmpty()) {
                                        TextButton(onClick = { viewModel.markReadingListRead(listId, !allCompleted) }) {
                                            Text(if (allCompleted) "MARK UNREAD" else "MARK READ", fontFamily = Anton, fontSize = 11.sp, color = Ochre)
                                        }
                                    }
                                }
                            }

                            if (continueButtonText != null && nextUnread != null) {
                                val targetComic = if (allCompleted && listComics.isNotEmpty()) listComics.first() else nextUnread
                                Button(
                                    onClick = { onOpenComic(targetComic.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Ochre),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Ink)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = continueButtonText,
                                        fontFamily = Anton,
                                        fontSize = 13.sp,
                                        color = Ink,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }

                // Empty State or Comics Grid
                if (listComics.isEmpty()) {
                    item(span = { GridItemSpan(actualColumns) }) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "THIS LIST IS EMPTY",
                                    fontFamily = Anton,
                                    fontSize = 16.sp,
                                    color = PaperWhiteMuted,
                                )
                                Text(
                                    text = "Tap ADD COMICS above to curate your reading order.",
                                    fontFamily = Archivo,
                                    fontSize = 12.sp,
                                    color = CreamMuted,
                                )
                            }
                        }
                    }
                } else {
                    itemsIndexed(listComics, key = { _, comic -> comic.id }) { index, comic ->
                        val cover = rememberCover(comic.coverPath)
                        val started = comic.lastPage > 0
                        val progressFraction = if (comic.pageCount > 0) comic.lastPage.toFloat() / comic.pageCount else 0f

                        Column(
                            Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onOpenComic(comic.id) },
                                    onLongClick = { selectedComicForOptions = comic },
                                )
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.7f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(InkSoft)
                                    .border(if (actualColumns >= 4) 2.dp else 3.dp, Ink, RoundedCornerShape(4.dp))
                                    .comicShadow(offset = if (actualColumns >= 4) 3.dp else 5.dp, color = Color(0xB3000000))
                            ) {
                                if (cover != null) {
                                    Image(
                                        cover,
                                        contentDescription = comic.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                }

                                // Sequence Number Badge (#1, #2, etc.)
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(Ink.copy(alpha = 0.88f))
                                        .border(1.dp, Ochre, RoundedCornerShape(3.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "#${index + 1}",
                                        fontFamily = Anton,
                                        fontSize = if (actualColumns >= 4) 9.sp else 10.5.sp,
                                        color = Cream,
                                    )
                                }

                                // Top-Right Quick Remove Button (X)
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(6.dp)
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(DeepInk.copy(alpha = 0.85f))
                                        .clickable { comicToRemove = comic },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Remove from reading list",
                                        tint = ComicRose,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }

                                // Bottom Reorder Affordances
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(bottom = if (actualColumns >= 4) 10.dp else 12.dp, end = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    if (index > 0) {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(DeepInk.copy(alpha = 0.85f))
                                                .clickable { viewModel.moveReadingListItem(listId, comic.id, -1) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                Icons.Default.ArrowUpward,
                                                contentDescription = "Move earlier in reading order",
                                                tint = Cream,
                                                modifier = Modifier.size(12.dp),
                                            )
                                        }
                                    }
                                    if (index < listComics.size - 1) {
                                        Box(
                                            modifier = Modifier
                                                .size(22.dp)
                                                .clip(CircleShape)
                                                .background(DeepInk.copy(alpha = 0.85f))
                                                .clickable { viewModel.moveReadingListItem(listId, comic.id, 1) },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                Icons.Default.ArrowDownward,
                                                contentDescription = "Move later in reading order",
                                                tint = Cream,
                                                modifier = Modifier.size(12.dp),
                                            )
                                        }
                                    }
                                }

                                // Bottom Progress Bar
                                Box(
                                    Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .height(if (actualColumns >= 4) 5.dp else 7.dp)
                                        .background(Ink)
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxHeight()
                                            .fillMaxWidth(if (started) progressFraction else 0f)
                                            .background(Ochre)
                                    )
                                }
                            }

                            Text(
                                text = comic.title,
                                fontFamily = Archivo,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = if (actualColumns >= 4) 10.sp else 12.sp,
                                color = PaperWhite,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = if (actualColumns >= 4) 6.dp else 9.dp),
                            )

                            Text(
                                text = if (started) {
                                    "${(progressFraction * 100).toInt()}% · pg ${comic.lastPage + 1}/${comic.pageCount}"
                                } else {
                                    "${comic.pageCount} pages"
                                },
                                fontFamily = Archivo,
                                fontSize = if (actualColumns >= 4) 8.5.sp else 9.5.sp,
                                color = if (started) Ochre else CreamMuted,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    // Long-Press Options Dialog for an Issue
    selectedComicForOptions?.let { comic ->
        val comicIndex = listComics.indexOfFirst { it.id == comic.id }
        val isRead = comic.pageCount > 0 && comic.lastPage >= comic.pageCount - 1
        AlertDialog(
            onDismissRequest = { selectedComicForOptions = null },
            title = {
                Text(
                    text = comic.title.uppercase(),
                    fontFamily = Anton,
                    fontSize = 15.sp,
                    color = PaperWhite,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // 1. Read / Continue
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(DeepInk)
                            .clickable {
                                selectedComicForOptions = null
                                onOpenComic(comic.id)
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Ochre, modifier = Modifier.size(18.dp))
                        Text(
                            text = if (comic.lastPage > 0) "CONTINUE READING (PG ${comic.lastPage + 1})" else "READ COMIC",
                            fontFamily = Anton,
                            fontSize = 12.sp,
                            color = PaperWhite,
                        )
                    }

                    // 2. Mark as Read / Unread
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(DeepInk)
                            .clickable {
                                viewModel.markComicRead(comic.id, !isRead)
                                selectedComicForOptions = null
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = LeafAccent, modifier = Modifier.size(18.dp))
                        Text(
                            text = if (isRead) "MARK AS UNREAD" else "MARK AS READ",
                            fontFamily = Anton,
                            fontSize = 12.sp,
                            color = PaperWhite,
                        )
                    }

                    // 3. Move Up
                    if (comicIndex > 0) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(DeepInk)
                                .clickable {
                                    viewModel.moveReadingListItem(listId, comic.id, -1)
                                    selectedComicForOptions = null
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = null, tint = Cream, modifier = Modifier.size(18.dp))
                            Text(
                                text = "MOVE UP IN READING ORDER",
                                fontFamily = Anton,
                                fontSize = 12.sp,
                                color = PaperWhite,
                            )
                        }
                    }

                    // 4. Move Down
                    if (comicIndex >= 0 && comicIndex < listComics.size - 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(DeepInk)
                                .clickable {
                                    viewModel.moveReadingListItem(listId, comic.id, 1)
                                    selectedComicForOptions = null
                                }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = null, tint = Cream, modifier = Modifier.size(18.dp))
                            Text(
                                text = "MOVE DOWN IN READING ORDER",
                                fontFamily = Anton,
                                fontSize = 12.sp,
                                color = PaperWhite,
                            )
                        }
                    }

                    // 5. Remove from Reading List
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(ComicRose.copy(alpha = 0.2f))
                            .border(1.dp, ComicRose.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .clickable {
                                viewModel.removeComicFromReadingList(listId, comic.id)
                                selectedComicForOptions = null
                            }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = ComicRose, modifier = Modifier.size(18.dp))
                        Text(
                            text = "REMOVE FROM LIST",
                            fontFamily = Anton,
                            fontSize = 12.sp,
                            color = ComicRose,
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedComicForOptions = null }) {
                    Text("CANCEL", fontFamily = Anton, color = PaperWhiteMuted)
                }
            },
            containerColor = DeepInkSurface,
        )
    }

    // Confirmation Dialog for Quick Remove (X)
    comicToRemove?.let { comic ->
        AlertDialog(
            onDismissRequest = { comicToRemove = null },
            title = {
                Text("REMOVE FROM READING LIST?", fontFamily = Anton, color = PaperWhite)
            },
            text = {
                Text(
                    "Remove \"${comic.title}\" from this reading list? The comic remains in your library.",
                    fontFamily = Archivo,
                    fontSize = 13.sp,
                    color = CreamMuted,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeComicFromReadingList(listId, comic.id)
                        comicToRemove = null
                    }
                ) {
                    Text("REMOVE", fontFamily = Anton, color = ComicRose)
                }
            },
            dismissButton = {
                TextButton(onClick = { comicToRemove = null }) {
                    Text("CANCEL", fontFamily = Anton, color = PaperWhiteMuted)
                }
            },
            containerColor = DeepInkSurface,
        )
    }

    // Rename Reading List Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = {
                Text("RENAME READING LIST", fontFamily = Anton, color = PaperWhite)
            },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("List Name", color = CreamMuted) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = PaperWhite,
                        unfocusedTextColor = PaperWhite,
                        focusedContainerColor = DeepInk,
                        unfocusedContainerColor = DeepInk,
                        focusedIndicatorColor = Ochre,
                        unfocusedIndicatorColor = PaperWhiteMuted,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameInput.isNotBlank()) {
                            viewModel.renameReadingList(listId, renameInput.trim())
                            showRenameDialog = false
                        }
                    }
                ) {
                    Text("SAVE", fontFamily = Anton, color = Ochre)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("CANCEL", fontFamily = Anton, color = PaperWhiteMuted)
                }
            },
            containerColor = DeepInkSurface,
        )
    }

    // Delete Reading List Dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text("DELETE READING LIST?", fontFamily = Anton, color = PaperWhite)
            },
            text = {
                Text(
                    "Are you sure you want to delete this reading list? Comics inside the list will NOT be deleted from your library.",
                    fontFamily = Archivo,
                    fontSize = 13.sp,
                    color = CreamMuted,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteReadingList(listId)
                        showDeleteDialog = false
                        onBack()
                    }
                ) {
                    Text("DELETE", fontFamily = Anton, color = ComicRose)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("CANCEL", fontFamily = Anton, color = PaperWhiteMuted)
                }
            },
            containerColor = DeepInkSurface,
        )
    }

    // Add Comics to Reading List Multi-Select Dialog
    if (showAddComicsDialog) {
        val existingIds = remember(listComics) { listComics.map { it.id }.toSet() }
        val availableComics = remember(allComics, existingIds, addSearchQuery) {
            allComics.filter {
                !existingIds.contains(it.id) &&
                (addSearchQuery.isBlank() || it.title.contains(addSearchQuery.trim(), ignoreCase = true) ||
                 (it.series?.contains(addSearchQuery.trim(), ignoreCase = true) == true))
            }.sortedWith(
                compareBy<ComicEntity> { it.series ?: it.title }
                    .thenBy { it.issueNumber ?: Float.MAX_VALUE }
                    .thenBy { it.title }
            )
        }

        AlertDialog(
            onDismissRequest = { showAddComicsDialog = false },
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("ADD COMICS TO LIST", fontFamily = Anton, color = PaperWhite)
                    OutlinedTextField(
                        value = addSearchQuery,
                        onValueChange = { addSearchQuery = it },
                        placeholder = { Text("Search library...", color = CreamMuted, fontSize = 12.sp) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedTextColor = PaperWhite,
                            unfocusedTextColor = PaperWhite,
                            focusedContainerColor = DeepInk,
                            unfocusedContainerColor = DeepInk,
                            focusedIndicatorColor = Ochre,
                            unfocusedIndicatorColor = PaperWhiteMuted,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            text = {
                if (availableComics.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (addSearchQuery.isBlank()) "All comics are already in this list." else "No matching comics found.",
                            fontFamily = Archivo,
                            fontSize = 12.sp,
                            color = CreamMuted,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(availableComics, key = { it.id }) { comic ->
                            val isSelected = selectedToAdd.contains(comic.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSelected) LeafAccent.copy(alpha = 0.2f) else DeepInk)
                                    .border(1.dp, if (isSelected) LeafAccent else Color.Transparent, RoundedCornerShape(4.dp))
                                    .clickable {
                                        selectedToAdd = if (isSelected) selectedToAdd - comic.id else selectedToAdd + comic.id
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (isSelected) LeafAccent else InkSoft)
                                        .border(1.dp, if (isSelected) LeafAccent else Ink, RoundedCornerShape(3.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (isSelected) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = DeepInk, modifier = Modifier.size(14.dp))
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = comic.title,
                                        fontFamily = Archivo,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = PaperWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (!comic.series.isNullOrBlank()) {
                                        Text(
                                            text = "${comic.series}${if (comic.issueNumber != null) " #${comic.issueNumber}" else ""}",
                                            fontFamily = Archivo,
                                            fontSize = 10.sp,
                                            color = Ochre,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (selectedToAdd.isNotEmpty()) {
                            viewModel.addComicsToReadingList(listId, selectedToAdd.toList())
                            showAddComicsDialog = false
                        }
                    },
                    enabled = selectedToAdd.isNotEmpty(),
                ) {
                    Text(
                        "ADD (${selectedToAdd.size})",
                        fontFamily = Anton,
                        color = if (selectedToAdd.isNotEmpty()) LeafAccent else PaperWhiteMuted,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddComicsDialog = false }) {
                    Text("CANCEL", fontFamily = Anton, color = PaperWhiteMuted)
                }
            },
            containerColor = DeepInkSurface,
        )
    }
}
