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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import net.digitalbc.leafreader.ui.brand.BookStatusBadgeIcon
import net.digitalbc.leafreader.ui.brand.comicShadow
import net.digitalbc.leafreader.ui.brand.halftone
import net.digitalbc.leafreader.ui.theme.Anton
import net.digitalbc.leafreader.ui.theme.Archivo
import net.digitalbc.leafreader.ui.theme.ComicRose
import net.digitalbc.leafreader.ui.theme.CreamMuted
import net.digitalbc.leafreader.ui.theme.DeepInk
import net.digitalbc.leafreader.ui.theme.Ink
import net.digitalbc.leafreader.ui.theme.LeafAccent
import net.digitalbc.leafreader.ui.theme.LeafPrimary
import net.digitalbc.leafreader.ui.theme.LeafSecondary
import net.digitalbc.leafreader.ui.theme.Ochre

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeriesDetailScreen(
    seriesName: String,
    volume: String?,
    viewModel: LibraryViewModel,
    onOpenComic: (Long) -> Unit,
    onBack: () -> Unit,
) {
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

    val seriesComics = remember(allComics, seriesName, volume) {
        allComics.filter {
            it.series.equals(seriesName, ignoreCase = true) &&
            (volume.isNullOrBlank() || it.volume.equals(volume, ignoreCase = true))
        }.sortedWith(
            compareBy<ComicEntity> { it.issueNumber ?: Float.MAX_VALUE }
                .thenBy { it.title }
        )
    }

    val allCompleted = seriesComics.isNotEmpty() && seriesComics.all { it.pageCount > 0 && it.lastPage >= it.pageCount - 1 }
    val nextUnread = seriesComics.firstOrNull { it.pageCount > 0 && it.lastPage < it.pageCount - 1 }
        ?: seriesComics.firstOrNull()

    val continueButtonText = when {
        allCompleted && seriesComics.isNotEmpty() -> "READ FROM START: ${seriesComics.first().title.uppercase()}"
        nextUnread != null && nextUnread.lastPage > 0 -> "CONTINUE: ${nextUnread.title.uppercase()}"
        nextUnread != null -> "START: ${nextUnread.title.uppercase()}"
        else -> null
    }

    var showAddComicsDialog by remember { mutableStateOf(false) }
    var selectedToAdd by remember { mutableStateOf(emptySet<Long>()) }

    var selectedComicForOptions by remember { mutableStateOf<ComicEntity?>(null) }
    var comicToRemove by remember { mutableStateOf<ComicEntity?>(null) }

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
                        text = seriesName.uppercase(),
                        fontFamily = Anton,
                        fontSize = 18.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!volume.isNullOrBlank()) {
                        Text(
                            text = volume.uppercase(),
                            fontFamily = Archivo,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = CreamMuted,
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
                contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
                horizontalArrangement = Arrangement.spacedBy(if (actualColumns >= 4) 12.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(if (actualColumns >= 4) 14.dp else 18.dp),
            ) {
                // Hero Card Header (Compact, Uncluttered Status Row)
                item(span = { GridItemSpan(actualColumns) }) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .comicShadow(offset = 3.dp, color = Color(0xB3000000)),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Left Column: Unified Read Count Badge + Slim Progress Bar
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                val completedCount = seriesComics.count { it.pageCount > 0 && it.lastPage >= it.pageCount - 1 }
                                val statusText = if (seriesComics.isEmpty()) {
                                    "0 ISSUES"
                                } else {
                                    "$completedCount OF ${seriesComics.size} READ"
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(
                                            1.dp,
                                            if (allCompleted) LeafAccent.copy(alpha = 0.65f) else Ochre.copy(alpha = 0.6f),
                                            RoundedCornerShape(4.dp),
                                        )
                                        .padding(horizontal = 8.dp, vertical = 3.dp),
                                ) {
                                    Text(
                                        text = statusText,
                                        fontFamily = Anton,
                                        fontSize = 11.sp,
                                        color = if (allCompleted) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.tertiary,
                                        letterSpacing = 0.4.sp,
                                        maxLines = 1,
                                    )
                                }

                                if (seriesComics.isNotEmpty()) {
                                    val completedCount = seriesComics.count { it.pageCount > 0 && it.lastPage >= it.pageCount - 1 }
                                    val progressFraction = completedCount.toFloat() / seriesComics.size
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(1.5.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(progressFraction)
                                                .background(if (allCompleted) LeafAccent else Ochre),
                                        )
                                    }
                                }
                            }

                            // Right Row: Mark Read/Unread Book Icon + Compact Start/Continue Pill
                            if (seriesComics.isNotEmpty()) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // Mark Read/Unread Book-Status Icon Button
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .border(
                                                1.dp,
                                                if (allCompleted) ComicRose.copy(alpha = 0.6f) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                                RoundedCornerShape(6.dp),
                                            )
                                            .clickable { viewModel.markSeriesRead(seriesName, volume, !allCompleted) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        BookStatusBadgeIcon(
                                            isAllCompleted = allCompleted,
                                            bookTint = if (allCompleted) ComicRose else MaterialTheme.colorScheme.onSurface,
                                            badgeColor = if (allCompleted) ComicRose else MaterialTheme.colorScheme.tertiary,
                                            badgeGlyphTint = Ink,
                                        )
                                    }

                                    // Compact Start / Continue Action Pill
                                    val targetComic = if (allCompleted) seriesComics.first() else (nextUnread ?: seriesComics.first())
                                    val shortActionLabel = when {
                                        allCompleted -> "RESTART"
                                        targetComic.lastPage > 0 -> "CONTINUE"
                                        else -> "START"
                                    }
                                    Box(
                                        modifier = Modifier
                                            .height(36.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Ochre)
                                            .border(1.5.dp, Ink, RoundedCornerShape(6.dp))
                                            .clickable { onOpenComic(targetComic.id) }
                                            .padding(horizontal = 11.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = continueButtonText ?: shortActionLabel,
                                                tint = Ink,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Text(
                                                text = shortActionLabel,
                                                fontFamily = Anton,
                                                fontSize = 11.5.sp,
                                                color = Ink,
                                                letterSpacing = 0.5.sp,
                                                maxLines = 1,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                items(seriesComics, key = { it.id }) { comic ->
                    SeriesComicItem(
                        comic = comic,
                        columns = actualColumns,
                        onClick = { onOpenComic(comic.id) },
                        onLongClick = { selectedComicForOptions = comic },
                        onRemove = { comicToRemove = comic },
                    )
                }
            }

            ExtendedFloatingActionButton(
                onClick = { showAddComicsDialog = true },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = DeepInk,
                shape = RoundedCornerShape(4.dp),
                elevation = FloatingActionButtonDefaults.elevation(6.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(20.dp),
                icon = {
                    Icon(Icons.Default.Add, contentDescription = "Add comics", tint = DeepInk)
                },
                text = {
                    Text(
                        text = "ADD COMICS",
                        fontFamily = Anton,
                        fontSize = 14.sp,
                        color = DeepInk,
                        letterSpacing = 0.6.sp,
                    )
                },
            )
        }
    }

    if (showAddComicsDialog) {
        val availableComics = remember(allComics, seriesComics) {
            val seriesComicIds = seriesComics.map { it.id }.toSet()
            allComics.filter { it.id !in seriesComicIds }
        }

        AlertDialog(
            onDismissRequest = {
                showAddComicsDialog = false
                selectedToAdd = emptySet()
            },
            title = { Text("Add Comics to $seriesName", fontFamily = Anton, color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Select downloaded comics in your library to add to this series.",
                        fontFamily = Archivo,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (availableComics.isEmpty()) {
                        Text(
                            "All downloaded comics in your library are already in this series.",
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
                            items(availableComics, key = { it.id }) { comic ->
                                val isSelected = comic.id in selectedToAdd
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
                                            selectedToAdd = if (isSelected) selectedToAdd - comic.id else selectedToAdd + comic.id
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
                if (selectedToAdd.isNotEmpty()) {
                    TextButton(onClick = {
                        viewModel.updateComicsSeries(selectedToAdd.toList(), seriesName, volume)
                        showAddComicsDialog = false
                        selectedToAdd = emptySet()
                    }) {
                        Text("ADD (${selectedToAdd.size})", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddComicsDialog = false
                    selectedToAdd = emptySet()
                }) {
                    Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }

    selectedComicForOptions?.let { comic ->
        val isRead = comic.pageCount > 0 && comic.lastPage >= comic.pageCount - 1
        AlertDialog(
            onDismissRequest = { selectedComicForOptions = null },
            title = {
                Text(comic.title.uppercase(), fontFamily = Anton, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, LeafSecondary.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .clickable {
                                val id = comic.id
                                selectedComicForOptions = null
                                onOpenComic(id)
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                        Text(if (comic.lastPage > 0) "CONTINUE READING" else "READ COMIC", fontFamily = Anton, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, LeafSecondary.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                            .clickable {
                                val id = comic.id
                                selectedComicForOptions = null
                                viewModel.markComicRead(id, !isRead)
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

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, ComicRose.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .clickable {
                                val target = comic
                                selectedComicForOptions = null
                                comicToRemove = target
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = ComicRose, modifier = Modifier.size(18.dp))
                        Text("REMOVE FROM SERIES", fontFamily = Anton, fontSize = 13.sp, color = ComicRose)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { selectedComicForOptions = null }) {
                    Text("CLOSE", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }

    comicToRemove?.let { comic ->
        AlertDialog(
            onDismissRequest = { comicToRemove = null },
            title = { Text("Remove from series?", fontFamily = Anton, color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("Remove \"${comic.title}\" from $seriesName? The comic will remain in your library as a standalone comic.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateComicsSeries(listOf(comic.id), null, null)
                    comicToRemove = null
                }) {
                    Text("REMOVE", color = ComicRose, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { comicToRemove = null }) { Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SeriesComicItem(
    comic: ComicEntity,
    columns: Int = 2,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val pct = if (comic.pageCount > 0) ((comic.lastPage + 1f) / comic.pageCount).coerceIn(0f, 1f) else 0f
    val started = comic.lastPage > 0
    val borderWidth = if (columns >= 4) 2.dp else 3.dp
    val shadowOffset = if (columns >= 4) 3.dp else 5.dp
    val progressHeight = if (columns >= 4) 5.dp else 7.dp

    Column(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.7f)
                .comicShadow(offset = shadowOffset, color = Color(0xB3000000))
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(borderWidth, Color.Transparent, RoundedCornerShape(4.dp)),
        ) {
            val cover = rememberCover(comic.coverPath)
            if (cover != null) {
                Image(cover, comic.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                GeneratedCover(comic.title, columns = columns)
            }

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Close, contentDescription = "Remove from series", tint = ComicRose, modifier = Modifier.size(13.dp))
            }

            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(progressHeight).background(MaterialTheme.colorScheme.surface)) {
                Box(Modifier.fillMaxWidth(pct).fillMaxHeight().background(Ochre))
            }
        }
        Text(
            comic.title.uppercase(),
            fontFamily = Archivo,
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (columns >= 5) 10.sp else if (columns >= 4) 11.sp else 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = if (columns >= 4) 6.dp else 9.dp),
        )
        Text(
            if (started) "${(pct * 100).toInt()}% · pg ${comic.lastPage + 1}/${comic.pageCount}"
            else "${comic.pageCount} pages",
            fontFamily = Archivo,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (columns >= 4) 8.5.sp else 9.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
