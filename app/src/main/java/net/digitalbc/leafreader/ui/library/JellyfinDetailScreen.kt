package net.digitalbc.leafreader.ui.library

import android.graphics.Bitmap
import android.text.Html
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.digitalbc.leafreader.R
import net.digitalbc.leafreader.data.jellyfin.*
import net.digitalbc.leafreader.data.settings.ThemeMode
import net.digitalbc.leafreader.ui.theme.Archivo
import net.digitalbc.leafreader.ui.theme.ComicReaderTheme
import java.text.NumberFormat

/** A cover is decorative beside the title; an unavailable image never blocks details. */
@Composable
internal fun JellyfinCover(
    item: JellyfinItem,
    modifier: Modifier = Modifier,
    load: (suspend () -> Bitmap?)? = null,
) {
    val bitmap by produceState<Bitmap?>(null, item.id, item.imageTag, load) {
        value = null
        value = load?.invoke()
    }
    JellyfinCoverImage(bitmap, modifier)
}

@Composable
internal fun JellyfinCoverImage(bitmap: Bitmap?, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap!!.asImageBitmap(), contentDescription = null,
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        } else {
            Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
internal fun JellyfinDetailScreen(
    state: JellyfinDetailState,
    onRead: () -> Unit,
    importing: Boolean,
    importProgress: Float?,
    importDetail: String?,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onCheckCopy: () -> Unit = {},
    cover: @Composable (JellyfinItem, Modifier) -> Unit,
    heroBitmap: Bitmap? = null,
    onCreator: ((JellyfinPerson) -> Unit)? = null,
    onSync: (() -> Unit)? = null,
    syncing: Boolean = false,
    syncError: String? = null,
    onRemoveDownload: (() -> Unit)? = null,
    removingDownload: Boolean = false,
    confirmRemovalRequired: Boolean = true,
) {
    var confirmRemoval by remember(state.identity) { mutableStateOf(false) }
    if (confirmRemoval && onRemoveDownload != null) {
        AlertDialog(onDismissRequest = { confirmRemoval = false },
            title = { Text(stringResource(R.string.remove_download)) },
            text = { Text(stringResource(R.string.remove_download_explanation)) },
            confirmButton = {
                TextButton(onClick = { confirmRemoval = false; onRemoveDownload() },
                    modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Text(stringResource(R.string.remove_download))
                }
            }, dismissButton = {
                TextButton(onClick = { confirmRemoval = false }, modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Text(stringResource(R.string.cancel))
                }
            })
    }
    val item = state.details?.item ?: state.item
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().background(net.digitalbc.leafreader.ui.theme.LeafPrimary)
                .statusBarsPadding()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.jellyfin_back_to_catalogue),
                    tint = net.digitalbc.leafreader.ui.theme.PaperWhite)
            }
            Text(stringResource(R.string.jellyfin_comic_details),
                style = MaterialTheme.typography.titleLarge, fontFamily = Archivo,
                color = net.digitalbc.leafreader.ui.theme.PaperWhite)
        }
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            contentAlignment = Alignment.TopCenter) {
            val fontScale = LocalDensity.current.fontScale
            val wide = maxWidth >= 600.dp && fontScale < 1.5f
            val sideBySide = wide || (maxWidth >= 360.dp && fontScale <= 1.2f)
            val coverWidth = if (heroBitmap == null) 84.dp else if (wide) 200.dp else 112.dp
            LazyColumn(
                modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                if (state.details?.fromCache == true) item {
                    Text(stringResource(R.string.creator_saved_scope), Modifier.padding(horizontal = 24.dp))
                }
                item(key = "hero") {
                    Box(Modifier.fillMaxWidth()) {
                        heroBitmap?.let { bitmap ->
                            Image(bitmap.asImageBitmap(), null, contentScale = ContentScale.Crop,
                                modifier = Modifier.matchParentSize())
                        }
                        Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(
                            MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                            MaterialTheme.colorScheme.background))).padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            if (sideBySide) {
                                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                                    cover(item, Modifier.width(coverWidth).aspectRatio(2f / 3f))
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        DetailHeading(item, state.details)
                                    }
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                                    cover(item, Modifier.width(coverWidth).aspectRatio(2f / 3f))
                                    DetailHeading(item, state.details)
                                }
                            }
                            state.details?.people?.takeIf { it.isNotEmpty() }?.let { people ->
                                DetailSection(stringResource(R.string.jellyfin_creators), inset = false) {
                                    people.forEach { person ->
                                        val role = creatorRole(person)
                                        val label = stringResource(R.string.creator_find_accessibility, person.name, role)
                                        if (onCreator == null) {
                                            Text(role, style = MaterialTheme.typography.labelLarge)
                                            Text(person.name, style = MaterialTheme.typography.titleMedium)
                                        } else {
                                            TextButton(onClick = { onCreator(person) },
                                                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                                    .semantics { contentDescription = label },
                                                contentPadding = PaddingValues(vertical = 8.dp),
                                                colors = ButtonDefaults.textButtonColors(
                                                    contentColor = MaterialTheme.colorScheme.onSurface)) {
                                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Text(role, style = MaterialTheme.typography.labelLarge,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    Text(person.name, style = MaterialTheme.typography.titleLarge,
                                                        textDecoration = TextDecoration.Underline)
                                                }
                                                Icon(Icons.AutoMirrored.Filled.ArrowBack, null,
                                                    modifier = Modifier.size(24.dp).rotate(180f))
                                            }
                                        }
                                    }
                                }
                            }
                            DetailActions(state, importing, importProgress, importDetail,
                                onRead, onDownload, onCancel, onCheckCopy, onSync, syncing, syncError,
                                onRemoveDownload?.let { remove -> ({ if (confirmRemovalRequired) confirmRemoval = true else remove() }) }, removingDownload)
                        }
                    }
                }
                if (state.loading) {
                    item {
                        Row(Modifier.padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                            Text(stringResource(R.string.jellyfin_loading_details))
                        }
                    }
                } else if (state.failure != null) {
                    item {
                        val description = when (state.failure) {
                            JellyfinFailureKind.AUTHENTICATION -> R.string.jellyfin_error_auth_expired_desc
                            JellyfinFailureKind.ACCESS_DENIED -> R.string.jellyfin_error_access_denied_desc
                            JellyfinFailureKind.NOT_FOUND -> R.string.jellyfin_detail_not_found
                            else -> R.string.jellyfin_detail_load_failed
                        }
                        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(description), color = MaterialTheme.colorScheme.error)
                            OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                                shape = RoundedCornerShape(12.dp)) {
                                Text(stringResource(R.string.jellyfin_retry))
                            }
                        }
                    }
                } else {
                    state.details?.let { details ->
                        details.overview?.let { overview ->
                            item {
                                DetailSection(stringResource(R.string.jellyfin_synopsis)) {
                                    Text(Html.fromHtml(overview, Html.FROM_HTML_MODE_COMPACT).toString().trim(),
                                        style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                        if (details.genres.isNotEmpty()) {
                            item {
                                DetailSection(stringResource(R.string.jellyfin_genres)) {
                                    Text(details.genres.joinToString(" · "))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun DetailActions(
    state: JellyfinDetailState,
    importing: Boolean,
    progress: Float?,
    importDetail: String?,
    onRead: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onCheckCopy: () -> Unit,
    onSync: (() -> Unit)?,
    syncing: Boolean,
    syncError: String?,
    onRemoveDownload: (() -> Unit)?,
    removingDownload: Boolean,
) {
    val comic = state.localComic
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            state.checkingLocal -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.jellyfin_checking_copy))
                }
            }
            state.localCopyFailure -> {
                Text(stringResource(R.string.jellyfin_copy_unavailable),
                    color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onCheckCopy, modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Text(stringResource(R.string.jellyfin_check_copy))
                }
            }
            comic != null -> {
                Button(onClick = onRead, enabled = !removingDownload, modifier = Modifier.heightIn(min = 48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = net.digitalbc.leafreader.ui.theme.Ochre,
                        contentColor = net.digitalbc.leafreader.ui.theme.Ink,
                    )) {
                    Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (comic.lastPage > 0 || comic.lastSlot > 0)
                        R.string.jellyfin_continue_reading else R.string.jellyfin_read))
                }
                Text(stringResource(R.string.jellyfin_device_position,
                    comic.lastPage.coerceIn(0, (comic.pageCount - 1).coerceAtLeast(0)) + 1, comic.pageCount),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                onSync?.let { sync ->
                    OutlinedButton(onClick = sync, enabled = !syncing && !removingDownload, modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                        Text(stringResource(if (syncing) R.string.loading else R.string.sync_reading_position))
                    }
                }
                syncError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                onRemoveDownload?.let { remove ->
                    OutlinedButton(onClick = remove, enabled = !removingDownload && !syncing,
                        modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                        Text(stringResource(if (removingDownload) R.string.loading else R.string.remove_download))
                    }
                }
            }
            importing -> {
                Text(importDetail ?: stringResource(R.string.importing))
                if (progress == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Text(stringResource(R.string.cancel))
                }
            }
            state.details != null && state.failure == null -> {
                if (state.details.item.isImportable) {
                    Button(onClick = onRead, modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = net.digitalbc.leafreader.ui.theme.Ochre,
                            contentColor = net.digitalbc.leafreader.ui.theme.Ink)) {
                        Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.jellyfin_read))
                    }
                    Text(stringResource(R.string.jellyfin_retained_read_explanation),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onDownload, modifier = Modifier.heightIn(min = 48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                        shape = RoundedCornerShape(12.dp)) {
                        Text(stringResource(R.string.jellyfin_download_offline))
                    }
                } else Text(stringResource(R.string.jellyfin_detail_unsupported))
            }
        }
    }
}

@Composable
private fun DetailHeading(item: JellyfinItem, details: JellyfinItemDetails?) {
    Text(item.name, style = MaterialTheme.typography.headlineMedium,
        fontFamily = Archivo, fontWeight = FontWeight.Bold)
    val metadata = listOfNotNull(
        item.seriesName,
        details?.indexNumber?.let { stringResource(R.string.jellyfin_issue_number, it) },
        item.productionYear?.toString(),
    )
    if (metadata.isNotEmpty()) {
        Text(metadata.joinToString(" · "), color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge)
    }
    if (details != null) {
        val percentage = details.playedPercentage
        val progress = when {
            item.played -> stringResource(R.string.jellyfin_read_on_server)
            percentage != null -> stringResource(R.string.jellyfin_server_percent,
                NumberFormat.getPercentInstance().format(percentage / 100.0))
            item.playbackPositionTicks > 0L -> stringResource(R.string.jellyfin_in_progress_on_server)
            else -> null
        }
        progress?.let { Text(it, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun DetailSection(title: String, inset: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = if (inset) 24.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        content()
    }
}

@Composable
internal fun creatorRole(person: JellyfinPerson): String = when (
    (person.role?.takeIf { it.isNotBlank() } ?: person.type).orEmpty().lowercase()
) {
    "writer", "author" -> stringResource(R.string.creator_role_writer)
    "artist", "penciller", "penciler" -> stringResource(R.string.creator_role_artist)
    "colorist", "colourist", "colors", "colours" -> stringResource(R.string.creator_role_colorist)
    "inker" -> stringResource(R.string.creator_role_inker)
    "letterer" -> stringResource(R.string.creator_role_letterer)
    "editor" -> stringResource(R.string.creator_role_editor)
    else -> person.role?.takeIf { it.isNotBlank() } ?: stringResource(R.string.creator_role_creator)
}

@Preview(name = "Detail • Phone dark", widthDp = 390, heightDp = 844,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES)
@Preview(name = "Detail • Tablet light", widthDp = 840, heightDp = 1000, uiMode = android.content.res.Configuration.UI_MODE_NIGHT_NO)
@Composable
internal fun JellyfinDetailPreview() {
    ComicReaderTheme(mode = ThemeMode.SYSTEM) {
        val syntheticCover = remember { Bitmap.createBitmap(IntArray(96 * 144) { pixel ->
            if (pixel % 96 < 48) 0xFF1C3A27.toInt() else 0xFFD4AF37.toInt()
        }, 96, 144, Bitmap.Config.ARGB_8888) }
        val item = JellyfinItem("synthetic", "The Paper Forest — Volume 1", false,
            container = "cbz", seriesName = "The Paper Forest", productionYear = 2026)
        JellyfinDetailScreen(
            JellyfinDetailState(item, JellyfinItemIdentity("fixture", "fixture", item.id), false,
                JellyfinItemDetails(JellyfinItemIdentity("fixture", "fixture", item.id), item,
                    overview = "A synthetic preview: a cartographer follows a forgotten trail through a forest of stories.",
                    people = listOf(JellyfinPerson("A. Example", "Writer"), JellyfinPerson("B. Example", "Artist")),
                    genres = listOf("Adventure"), indexNumber = 1, playedPercentage = 25.0)),
            {}, false, null, null, {}, {}, {}, {},
            cover = { _, modifier -> JellyfinCoverImage(syntheticCover, modifier) },
            heroBitmap = syntheticCover, onCreator = {},
        )
    }
}

@Preview(name = "Local Continue • Large text", widthDp = 390, heightDp = 844, fontScale = 2f)
@Preview(name = "Local Continue • Tablet", widthDp = 840, heightDp = 1000)
@Composable
internal fun JellyfinLocalReadPreview() {
    ComicReaderTheme(mode = ThemeMode.DARK) {
        val item = JellyfinItem("synthetic", "The Paper Forest", false)
        val comic = net.digitalbc.leafreader.data.db.ComicEntity(
            id = 42, title = item.name, filePath = "/synthetic.cbz", coverPath = null,
            format = "cbz", pageCount = 20, lastPage = 4, lastSlot = 2,
            dateAdded = 0L, lastOpened = 1L,
        )
        JellyfinDetailScreen(
            state = JellyfinDetailState(item, JellyfinItemIdentity("fixture", "fixture", item.id),
                loading = false, failure = JellyfinFailureKind.UNAVAILABLE, localComic = comic),
            onRead = {}, importing = false, importProgress = null, importDetail = null,
            onBack = {}, onRetry = {}, onDownload = {}, onCancel = {},
            cover = { value, modifier -> JellyfinCover(value, modifier) },
        )
    }
}

@Preview(name = "Detail • Large text error", widthDp = 390, heightDp = 844, fontScale = 2f)
@Composable
internal fun JellyfinDetailErrorPreview() {
    ComicReaderTheme(mode = ThemeMode.DARK) {
        val item = JellyfinItem("synthetic", "A comic with unavailable metadata", false)
        JellyfinDetailScreen(JellyfinDetailState(item, null, false, failure = JellyfinFailureKind.AUTHENTICATION),
            {}, false, null, null, {}, {}, {}, {},
            cover = { comic, modifier -> JellyfinCover(comic, modifier) })
    }
}
