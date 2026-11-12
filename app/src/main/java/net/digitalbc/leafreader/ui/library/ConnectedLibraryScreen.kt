package net.digitalbc.leafreader.ui.library

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.digitalbc.leafreader.data.jellyfin.*
import net.digitalbc.leafreader.ui.brand.LeafWordmark
import net.digitalbc.leafreader.ui.theme.*

@Composable
fun ConnectedLibraryScreen(
    catalogue: JellyfinCatalogueViewModel,
    library: LibraryViewModel,
    onOpenComic: (Long) -> Unit,
    onOpenReadingList: (Long) -> Unit,
    onOpenLocal: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by catalogue.state.collectAsStateWithLifecycle()
    val detail by library.jellyfinDetail.collectAsStateWithLifecycle()
    val browser by library.jellyfinBrowser.collectAsStateWithLifecycle()
    val downloads by catalogue.downloads.collectAsStateWithLifecycle()
    val lists by library.readingLists.collectAsStateWithLifecycle()
    var folders by rememberSaveable { mutableStateOf(false) }
    var series by rememberSaveable(state.library?.id, state.search) { mutableStateOf<String?>(null) }
    val positions = rememberSaveableStateHolder()
    LaunchedEffect(catalogue, library.jellyfinBrowsePositionKey(null)) {
        catalogue.ensure()
        library.refreshDownloadedJellyfinItems()
    }
    BackHandler(enabled = folders && browser.isRoot) { folders = false }
    BackHandler(enabled = !folders && detail == null && (state.collection != null || series != null)) {
        if (state.collection != null) catalogue.closeCollection() else series = null
    }
    if (folders || detail != null) {
        JellyfinBrowserScreen(library, onBack = { folders = false }, onOpenComic,
            onOpenLocal, onOpenSettings)
        return
    }
    val focus = LocalFocusManager.current
    var search by rememberSaveable(state.search, state.view) { mutableStateOf(state.search) }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).imePadding()) {
        Surface(color = LeafPrimary, contentColor = PaperWhite) {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp)) {
                LeafWordmark()
                Text(state.collection?.name ?: state.library?.name ?: "Your library", fontFamily = Archivo,
                    color = PaperWhite)
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = onOpenLocal) { Text("My Comics", color = PaperWhite) }
                    TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = {
                        library.ensureJellyfinLanding()
                        folders = true
                    }) { Text("Folders", color = PaperWhite) }
                    TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = onOpenSettings) { Text("Settings", color = PaperWhite) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            JellyfinCatalogueView.entries.forEach { view ->
                FilterChip(selected = state.view == view, onClick = { series = null; catalogue.select(view) },
                    label = { Text(if (view == JellyfinCatalogueView.DOWNLOADED) "Downloaded" else view.name) },
                    modifier = Modifier.heightIn(min = 48.dp))
            }
        }
        OutlinedTextField(value = search, onValueChange = { search = it },
            label = { Text(if (state.view == JellyfinCatalogueView.DOWNLOADED) "Search downloads" else "Search catalogue") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                cursorColor = MaterialTheme.colorScheme.onSurface,
                focusedLabelColor = MaterialTheme.colorScheme.onSurface),
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { catalogue.search(search); focus.clearFocus() }),
            trailingIcon = { TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { catalogue.search(search); focus.clearFocus() }) { Text("Go") } })
        if (state.library != null && state.libraries.size > 1) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
                state.libraries.forEach { view ->
                    FilterChip(selected = state.library == view, onClick = { series = null; catalogue.chooseLibrary(view) },
                        label = { Text(view.name) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
        }
        val positionKey = listOf(catalogue.positionScope, state.library?.id.orEmpty(),
            state.view.name, state.collection?.id.orEmpty(), state.search, series.orEmpty())
            .joinToString(":") { "${it.length}:$it" }
        positions.SaveableStateProvider(positionKey) {
            val scroll = rememberLazyListState()
            LazyColumn(state = scroll, modifier = Modifier.fillMaxWidth().weight(1f).navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.view == JellyfinCatalogueView.DOWNLOADED) {
                    item { Text("On this device · all sources", style = MaterialTheme.typography.titleMedium) }
                    val visible = downloads.filter { it.title.contains(search, true) || it.series?.contains(search, true) == true }
                    if (visible.isEmpty()) item { Text("No readable downloads match. My Comics keeps all library records.") }
                    items(visible, key = { "local:${it.id}" }) { comic ->
                        Surface(onClick = { onOpenComic(comic.id) }, shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                val cover by produceState<android.graphics.Bitmap?>(null, comic.coverPath) {
                                    value = withContext(Dispatchers.IO) { comic.coverPath?.let { BitmapFactory.decodeFile(it) } }
                                }
                                cover?.let { Image(it.asImageBitmap(), null, Modifier.size(48.dp, 72.dp)) }
                                Column(Modifier.weight(1f)) {
                                    Text(comic.title, style = MaterialTheme.typography.titleMedium)
                                    Text(when (comic.source) { "smb" -> "SMB import"; "jellyfin" -> "Jellyfin download"; else -> "Device import" })
                                    Text("Page ${comic.lastPage + 1} of ${comic.pageCount} · ${comic.format.uppercase()}")
                                }
                            }
                        }
                    }
                } else {
                    if (state.view == JellyfinCatalogueView.LISTS && state.collection == null) {
                        item { Text("Leaf lists", style = MaterialTheme.typography.titleMedium) }
                        if (lists.isEmpty()) item { Text("No Leaf lists yet. Create and manage them in My Comics.") }
                        items(lists.filter { it.name.contains(search, true) }, key = { "leaf:${it.id}" }) { list ->
                            CatalogueAction(list.name, "Leaf list", { onOpenReadingList(list.id) })
                        }
                        item { Text("Jellyfin collections · account-wide", style = MaterialTheme.typography.titleMedium) }
                        item { Text("Members are limited to readable book formats in the selected comic library. Other or unsupported members are omitted. Some servers do not expose comic collections.") }
                    }
                    if (state.collection != null || series != null) {
                        item { TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = {
                            if (state.collection != null) catalogue.closeCollection() else series = null
                        }, modifier = Modifier.heightIn(min = 48.dp)) { Text("Back to ${state.view.name.lowercase()}") } }
                    }
                    if (state.library == null) {
                        items(state.libraries, key = { "library:${it.id}" }) { view ->
                            CatalogueAction(view.name, "Jellyfin Books library", { catalogue.chooseLibrary(view) })
                        }
                        if (state.loaded && state.libraries.isEmpty()) item { Text("No Books library is declared. Browse Folders to choose another library, or use My Comics.") }
                    } else if (state.view == JellyfinCatalogueView.SERIES && series == null) {
                        item { Text("Series metadata from loaded catalogue pages", style = MaterialTheme.typography.bodyMedium) }
                        JellyfinCatalogueLogic.series(state.entries).forEach { (name, entries) ->
                            item(key = "series:${name.orEmpty()}") {
                                CatalogueAction(name ?: "No series metadata", "${entries.size} loaded comics · Jellyfin",
                                    { series = name ?: "" })
                            }
                        }
                    } else {
                        val entries = if (state.view == JellyfinCatalogueView.SERIES)
                            state.entries.filter { it.item.seriesName?.trim().orEmpty() == series } else state.entries
                        items(entries, key = { it.item.id }) { entry ->
                            if (entry.type.equals("BoxSet", true)) {
                                CatalogueAction(entry.item.name, "Jellyfin collection · membership checked on open",
                                    { catalogue.openCollection(entry.item) })
                            } else {
                                Column {
                                    JellyfinItemRow(entry.item, entry.item.id in browser.downloadedItemIds,
                                        false, { library.openJellyfinDetail(entry.item) }, {},
                                        cover = { item, modifier ->
                                            val loader = remember(item.id, item.imageTag) { suspend { library.loadJellyfinCover(item) } }
                                            JellyfinCover(item, modifier, loader)
                                        }, showSelection = false)
                                    Text(when (entry.played) {
                                        null -> "Jellyfin · reading status unavailable"
                                        true -> "Jellyfin · read"
                                        false -> if (entry.item.playbackPositionTicks > 0) "Jellyfin · in progress" else "Jellyfin · unread"
                                    }, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                    if (state.error != null) item {
                        Text(state.error!!, color = MaterialTheme.colorScheme.error)
                        TextButton(colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), onClick = { if (state.library == null) catalogue.ensureRetry() else catalogue.load(state.nextIndex > 0) },
                            modifier = Modifier.heightIn(min = 48.dp)) { Text("Retry") }
                    }
                    if (state.loading) item { CircularProgressIndicator(Modifier.size(32.dp)) }
                    if (state.loaded && !state.loading && state.entries.isEmpty() && state.library != null)
                        item { Text(if (state.view == JellyfinCatalogueView.LISTS) "No compatible collections or members on this page."
                            else "No comics in this view. Missing reading status is not classified as unread.") }
                    if (state.hasMore) item {
                        Text("${state.nextIndex} of ${state.total} server records loaded")
                        OutlinedButton(onClick = { catalogue.load(more = true) }, enabled = !state.loading,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.heightIn(min = 48.dp)) { Text("Load more") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogueAction(title: String, subtitle: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontFamily = Archivo)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
