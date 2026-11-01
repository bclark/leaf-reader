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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

    val nextUnread = seriesComics.firstOrNull { it.pageCount > 0 && it.lastPage < it.pageCount - 1 }
        ?: seriesComics.firstOrNull()

    var showAddComicsDialog by remember { mutableStateOf(false) }
    var selectedToAdd by remember { mutableStateOf(emptySet<Long>()) }

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
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(if (actualColumns >= 4) 12.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(if (actualColumns >= 4) 14.dp else 18.dp),
            ) {
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
                                OchreBadge("${seriesComics.size} ISSUES TOTAL")
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(LeafAccent)
                                            .clickable { showAddComicsDialog = true }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Icon(Icons.Default.Add, contentDescription = null, tint = DeepInk, modifier = Modifier.size(14.dp))
                                            Text("ADD COMICS", fontFamily = Anton, fontSize = 11.sp, color = DeepInk)
                                        }
                                    }
                                    TextButton(onClick = { viewModel.markSeriesRead(seriesName, volume, true) }) {
                                        Text("MARK READ", fontFamily = Anton, fontSize = 11.sp, color = Ochre)
                                    }
                                }
                            }
                            if (nextUnread != null) {
                                Button(
                                    onClick = { onOpenComic(nextUnread.id) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Ochre),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Ink)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "CONTINUE: ${nextUnread.title.uppercase()}",
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

                items(seriesComics, key = { it.id }) { comic ->
                    SeriesComicItem(
                        comic = comic,
                        columns = actualColumns,
                        onClick = { onOpenComic(comic.id) },
                        onLongClick = { comicToRemove = comic },
                        onRemove = { comicToRemove = comic },
                    )
                }
            }
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
            title = { Text("Add Comics to $seriesName", fontFamily = Anton, color = PaperWhite) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Select downloaded comics in your library to add to this series.",
                        fontFamily = Archivo,
                        fontSize = 12.sp,
                        color = PaperWhiteMuted,
                    )

                    if (availableComics.isEmpty()) {
                        Text(
                            "All downloaded comics in your library are already in this series.",
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
                            items(availableComics, key = { it.id }) { comic ->
                                val isSelected = comic.id in selectedToAdd
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
                if (selectedToAdd.isNotEmpty()) {
                    TextButton(onClick = {
                        viewModel.updateComicsSeries(selectedToAdd.toList(), seriesName, volume)
                        showAddComicsDialog = false
                        selectedToAdd = emptySet()
                    }) {
                        Text("ADD (${selectedToAdd.size})", color = Ochre, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddComicsDialog = false
                    selectedToAdd = emptySet()
                }) {
                    Text("CANCEL", color = PaperWhiteMuted)
                }
            },
            containerColor = DeepInkSurface,
        )
    }

    comicToRemove?.let { comic ->
        AlertDialog(
            onDismissRequest = { comicToRemove = null },
            title = { Text("Remove from series?", fontFamily = Anton, color = PaperWhite) },
            text = { Text("Remove \"${comic.title}\" from $seriesName? The comic will remain in your library as a standalone comic.", color = PaperWhiteMuted) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateComicsSeries(listOf(comic.id), null, null)
                    comicToRemove = null
                }) {
                    Text("REMOVE", color = ComicRose, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { comicToRemove = null }) { Text("CANCEL", color = PaperWhiteMuted) }
            },
            containerColor = DeepInkSurface,
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
                .background(InkSoft)
                .border(borderWidth, Ink, RoundedCornerShape(4.dp)),
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
                    .background(DeepInk.copy(alpha = 0.85f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Close, contentDescription = "Remove from series", tint = ComicRose, modifier = Modifier.size(13.dp))
            }

            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(progressHeight).background(Ink)) {
                Box(Modifier.fillMaxWidth(pct).fillMaxHeight().background(Ochre))
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
            if (started) "${(pct * 100).toInt()}% · pg ${comic.lastPage + 1}/${comic.pageCount}"
            else "${comic.pageCount} pages",
            fontFamily = Archivo,
            fontWeight = FontWeight.SemiBold,
            fontSize = if (columns >= 4) 8.5.sp else 9.5.sp,
            color = CreamMuted,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
