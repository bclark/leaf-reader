package net.digitalbc.leafreader.ui.library

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.digitalbc.leafreader.data.jellyfin.*

@androidx.compose.ui.tooling.preview.Preview(name = "Main grid · remote covers", widthDp = 390, heightDp = 844)
@androidx.compose.ui.tooling.preview.Preview(name = "Main grid · large text", widthDp = 390, heightDp = 844, fontScale = 2f)
@androidx.compose.runtime.Composable
internal fun MainLibraryRemoteCardsPreview() {
    net.digitalbc.leafreader.ui.theme.ComicReaderTheme(mode = net.digitalbc.leafreader.data.settings.ThemeMode.DARK) {
        Surface {
            LazyVerticalGrid(columns = GridCells.Fixed(2), contentPadding = PaddingValues(18.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                mainCatalogueGrid(CatalogueState(
                    library = JellyfinView("fixture", "Comics", "books"),
                    entries = listOf(
                        JellyfinCatalogueEntry(JellyfinItem("one", "The Paper Forest", false, container = "cbz"), "Book", null),
                        JellyfinCatalogueEntry(JellyfinItem("two", "Moon Garden", false, container = "cbz"), "Book", false)),
                    loaded = true), 2, null, emptySet(), {}, {}, {}, {}, {}, {}, {}, { null })
            }
        }
    }
}

/** A failed offline image request must restart once the catalogue reconnects. */
internal val LocalRemoteCoverOffline = androidx.compose.runtime.staticCompositionLocalOf { false }

/** Remote metadata uses the main library's existing cover card; no local records are fabricated. */
internal fun LazyGridScope.mainCatalogueGrid(
    state: CatalogueState,
    columns: Int,
    selectedSeries: String?,
    downloadedIds: Set<String>,
    onSeries: (String) -> Unit,
    onBack: () -> Unit,
    onItem: (JellyfinItem) -> Unit,
    onCollection: (JellyfinItem) -> Unit,
    onLibrary: (JellyfinView) -> Unit,
    onRetry: () -> Unit,
    onMore: () -> Unit,
    loadCover: suspend (JellyfinItem) -> Bitmap?,
    accountScope: String = "",
    onLongClick: (JellyfinCatalogueEntry) -> Unit = {},
    coverWidthPx: Int = 400,
    coverSelections: Map<String, String> = emptyMap(),
    onCoverSelected: (String, String) -> Unit = { _, _ -> },
    showEmpty: Boolean = true,
    selecting: Boolean = false,
    selectedIds: Set<String> = emptySet(),
    onToggleSelection: (JellyfinCatalogueEntry) -> Unit = {},
    connectionNoticeShown: Boolean = false,
    onClearSearch: () -> Unit = {},
) {
    if (state.library == null || state.libraries.size > 1) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                state.libraries.forEach { library ->
                    TextButton(onClick = { onLibrary(library) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                        modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (state.library == library) "✓ ${library.name}" else library.name)
                    }
                }
                if (state.loaded && state.libraries.isEmpty())
                    Text("No server libraries are available. Check your connection in Settings, or use Downloaded.")
            }
        }
    }
    if (selectedSeries != null || state.collection != null) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            TextButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                Text("Back to ${state.view.name.lowercase()}")
            }
        }
    }
    val sharedConnectionFailure = connectionNoticeShown && state.launchFailure != null
    if (state.indexing || (!state.indexComplete && state.loaded) || (state.offline && !sharedConnectionFailure) ||
        (state.indexError != null && !sharedConnectionFailure) || state.collection != null || state.partialSearch) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                if (state.indexing) Text("Indexing catalogue · ${state.indexedRecords} of ${state.indexTotal} records")
                else if (!state.indexComplete && state.loaded)
                    Text("Saved catalogue is partial · ${state.indexedRecords} records indexed")
                else if (state.offline && !sharedConnectionFailure) Text("Searching and browsing saved metadata · connection needed for remote-only comics")
                state.indexError?.takeUnless { sharedConnectionFailure }?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onRetry, enabled = !state.indexing,
                        modifier = Modifier.heightIn(min = 48.dp)) { Text("Retry refresh") }
                }
                state.collection?.let { Text("Collection · ${it.name}") }
                if (state.partialSearch) Text("Offline search covers only saved members of this collection.")
            }
        }
    }
    if (state.view == JellyfinCatalogueView.SERIES && selectedSeries == null) {
        state.seriesGroups.forEach { group ->
            item(key = "remote-series:${group.key}", contentType = "remote-series") {
                androidx.compose.runtime.CompositionLocalProvider(LocalRemoteCoverOffline provides state.offline) {
                    RemoteSeriesStackCard(group, columns, loadCover, coverWidthPx,
                        selectedCoverId = coverSelections["remote:${group.key}"],
                        onCoverSelected = { onCoverSelected("remote:${group.key}", it) }) { onSeries(group.key) }
                }
            }
        }
    } else {
        val entries = if (state.view == JellyfinCatalogueView.SERIES)
            state.seriesGroups.firstOrNull { it.key == selectedSeries }?.entries.orEmpty() else state.entries
        if (selectedSeries != null && entries.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(if (state.unreadOnly) androidx.compose.ui.res.stringResource(net.digitalbc.leafreader.R.string.library_no_unread)
                    else "No comics in this series.")
            }
        }
        if (state.view == JellyfinCatalogueView.LISTS && state.collection == null) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text("Jellyfin collections · account-wide. Only readable members in this comic library are shown on open.")
            }
        }
        items(entries, key = { "remote:${state.library?.id}:${it.item.id}" },
            contentType = { if (it.type.equals("BoxSet", true)) "remote-collection" else "remote-comic" }) { entry ->
            val collection = entry.type.equals("BoxSet", true)
            val status = if (collection) "Jellyfin collection · read-only" else when (entry.played) {
                null -> "Jellyfin · reading status unavailable"
                true -> "Jellyfin · read"
                false -> if (entry.item.playbackPositionTicks > 0) "Jellyfin · in progress" else "Jellyfin · unread"
            }
            androidx.compose.runtime.CompositionLocalProvider(LocalRemoteCoverOffline provides state.offline) {
                ComicCard(remote = entry, columns = columns, remoteCoverWidthPx = coverWidthPx,
                    isEditMode = selecting && !collection,
                    isSelected = entry.item.id in selectedIds,
                    remoteSubtitle = status + if (entry.item.id in downloadedIds) " · Downloaded" else "",
                    onClick = {
                        if (collection) onCollection(entry.item)
                        else if (selecting) onToggleSelection(entry) else onItem(entry.item)
                    },
                    onLongClick = if (collection) null else ({ onLongClick(entry) }), loadRemoteCover = loadCover)
            }
        }
    }
    if (state.loading) item(span = { GridItemSpan(maxLineSpan) }) {
        CircularProgressIndicator(Modifier.size(32.dp))
    }
    state.error?.takeUnless { sharedConnectionFailure && state.offline && state.collection == null && !state.hasMore }?.let { error ->
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(error, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Text("Retry") }
            }
        }
    }
    if (showEmpty && state.loaded && !state.loading && state.entries.isEmpty() && state.library != null && state.error == null) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            if (state.search.isNotBlank()) {
                Column {
                    Text(androidx.compose.ui.res.stringResource(net.digitalbc.leafreader.R.string.library_search_no_matches, state.search))
                    TextButton(onClick = onClearSearch, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(androidx.compose.ui.res.stringResource(net.digitalbc.leafreader.R.string.library_clear_search))
                    }
                }
            } else Text(if (state.creator != null) androidx.compose.ui.res.stringResource(net.digitalbc.leafreader.R.string.creator_no_matches)
                else if (state.view == JellyfinCatalogueView.LISTS && state.collection == null) "No compatible Jellyfin collections."
                else if (state.unreadOnly) androidx.compose.ui.res.stringResource(net.digitalbc.leafreader.R.string.library_no_unread)
                else if (state.view == JellyfinCatalogueView.LISTS) "No compatible collections or members on this page."
                else "No comics in this view. Unknown reading state is not classified as unread.")
        }
    }
    if (state.hasMore) item(span = { GridItemSpan(maxLineSpan) }) {
        Column {
            Text("${state.nextIndex} of ${state.total} server records loaded")
            Text(if (state.error == null) "More results load as you scroll." else "Use Retry to load the next results.")
        }
    }
}
