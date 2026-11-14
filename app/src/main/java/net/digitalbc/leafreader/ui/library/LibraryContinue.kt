package net.digitalbc.leafreader.ui.library

import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import java.util.Locale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.digitalbc.leafreader.R
import net.digitalbc.leafreader.data.jellyfin.JellyfinItem
import net.digitalbc.leafreader.ui.theme.*

@Composable
internal fun LibraryContinue(
    candidates: List<ContinueReading>, busyKey: String?, progress: Float?,
    onContinue: (ContinueReading) -> Unit, onCancel: () -> Unit,
    loadCover: suspend (JellyfinItem, Int) -> Bitmap?,
) {
    Box(Modifier.fillMaxWidth()) {
        val wide = LocalConfiguration.current.screenWidthDp >= 600
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (wide) Text(stringResource(R.string.library_continue_reading).uppercase(Locale.getDefault()), fontFamily = Anton,
                color = MaterialTheme.colorScheme.tertiary, fontSize = 20.sp)
            val cards: @Composable RowScope.() -> Unit = {
                candidates.take(if (wide) 5 else 1).forEach { candidate ->
                    ContinueCard(candidate, busyKey == candidate.key, progress, wide,
                        if (wide) Modifier.width(340.dp) else Modifier.fillMaxWidth(),
                        { onContinue(candidate) }, onCancel, loadCover)
                }
            }
            Row(if (wide) Modifier.horizontalScroll(rememberScrollState()) else Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp), content = cards)
        }
    }
}

@Composable
private fun ContinueCard(candidate: ContinueReading, busy: Boolean, progress: Float?, wide: Boolean,
    modifier: Modifier, onContinue: () -> Unit, onCancel: () -> Unit,
    loadCover: suspend (JellyfinItem, Int) -> Bitmap?) {
    val largeType = LocalConfiguration.current.fontScale > 1.3f
    val cover = rememberCover(candidate.local?.coverPath)
    val remote by produceState<Bitmap?>(null, candidate.key, candidate.remote?.item?.imageTag) {
        value = candidate.remote?.item?.let { loadCover(it, 256) }
    }
    val pageLabel = candidate.total?.let { stringResource(R.string.library_continue_page_total, candidate.page, it) }
        ?: stringResource(R.string.library_continue_page, candidate.page)
    val accessibleName = stringResource(R.string.library_continue_accessible, candidate.title, pageLabel)
    val cardModifier = if (busy) modifier else modifier.clickable(role = Role.Button, onClick = onContinue)
        .semantics(mergeDescendants = true) { contentDescription = accessibleName }
    val contentsModifier = if (busy) Modifier else Modifier.clearAndSetSemantics { }
    Surface(cardModifier, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))) {
        val image: @Composable () -> Unit = {
            Box(Modifier.size(if (wide) 76.dp else 92.dp, if (wide) 108.dp else 131.dp)
                .clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                val bitmap = cover ?: remote?.asImageBitmap()
                if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else GeneratedCover(candidate.title, columns = 4)
            }
        }
        val text: @Composable ColumnScope.() -> Unit = {
            if (!wide) Text(stringResource(R.string.library_continue_reading).uppercase(Locale.getDefault()), fontFamily = Anton,
                color = MaterialTheme.colorScheme.tertiary, fontSize = 14.sp)
            Text(candidate.title, fontFamily = Anton, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (largeType) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
            Text(pageLabel, fontFamily = Archivo, color = MaterialTheme.colorScheme.onSurfaceVariant)
            candidate.fraction?.let { LinearProgressIndicator(progress = { it }, modifier = Modifier.fillMaxWidth().height(4.dp), color = MaterialTheme.colorScheme.tertiary, trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)) }
            if (busy) {
                if (progress == null) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) { Text(stringResource(android.R.string.cancel)) }
            } else Surface(Modifier.fillMaxWidth().heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp), color = Ochre, contentColor = Ink) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.library_continue), fontFamily = Archivo)
                }
            }
        }
        if (largeType) Column(contentsModifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            image(); Column(verticalArrangement = Arrangement.spacedBy(8.dp), content = text)
        } else Row(contentsModifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            image(); Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), content = text)
        }
    }
}

@Composable
internal fun LibraryBottomAction(lists: Boolean, importing: Boolean, onClick: () -> Unit) {
    HorizontalDivider()
    val gutter = if (LocalConfiguration.current.screenWidthDp >= 600) 32.dp else 18.dp
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = gutter, vertical = 12.dp)) {
        Button(onClick, enabled = !importing, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
            shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Ochre, contentColor = Ink, disabledContainerColor = Ochre, disabledContentColor = Ink)) {
            if (importing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Default.Add, null)
            Spacer(Modifier.width(8.dp))
            Text(stringResource(if (lists) R.string.library_create_reading_list else R.string.library_add_comics), fontFamily = Archivo)
        }
    }
}

@Composable
internal fun LibraryEmpty(onAdd: () -> Unit, onConnect: () -> Unit, importing: Boolean) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Image(painterResource(R.drawable.ic_leaf_logo), null, Modifier.size(80.dp))
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.library_empty_title), fontFamily = Anton, fontSize = 28.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.library_empty_body), fontFamily = Archivo,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 440.dp))
        Spacer(Modifier.height(24.dp))
        Button(onAdd, enabled = !importing, modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth().heightIn(min = 52.dp),
            shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Ochre, contentColor = Ink)) {
            Text(stringResource(R.string.library_add_comics))
        }
        Spacer(Modifier.height(12.dp))
        FilledTonalButton(onConnect, enabled = !importing, modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(12.dp)) {
            Icon(Icons.Default.Storage, null); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.library_connect_jellyfin))
        }
    }
}
