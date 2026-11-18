package net.digitalbc.leafreader.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.digitalbc.leafreader.data.jellyfin.*

/** Captures the account before showing actions; a later account switch cannot retarget a list entry. */
internal data class RemoteListSelection(val entry: JellyfinCatalogueEntry, val identity: JellyfinItemIdentity)

@Composable
internal fun RemoteListActions(
    selection: RemoteListSelection?, libraryId: String?, viewModel: LibraryViewModel,
    canDownload: Boolean = false, canRemove: Boolean = false,
    onDownload: () -> Unit = {}, onRemove: () -> Unit = {}, onSelect: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    if (selection == null) return
    val lists by viewModel.readingLists.collectAsStateWithLifecycle()
    var name by remember(selection.identity) { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss,
        title = { Text(selection.entry.item.name) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TextButton(onClick = { onDismiss(); viewModel.openJellyfinDetail(selection.entry.item) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(androidx.compose.ui.res.stringResource(net.digitalbc.leafreader.R.string.compact_details))
                }
                if (canDownload) TextButton(onClick = { onDismiss(); onDownload() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(androidx.compose.ui.res.pluralStringResource(net.digitalbc.leafreader.R.plurals.compact_download_count, 1, 1))
                }
                if (canRemove) TextButton(onClick = { onDismiss(); onRemove() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(androidx.compose.ui.res.pluralStringResource(net.digitalbc.leafreader.R.plurals.compact_remove_count, 1, 1))
                }
                if (onSelect != null) TextButton(onClick = { onDismiss(); onSelect() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(androidx.compose.ui.res.stringResource(net.digitalbc.leafreader.R.string.compact_select))
                }
                HorizontalDivider()
                Text("Add to Leaf list")
                Text("List membership does not download the comic.")
                lists.forEach { list ->
                    TextButton(onClick = {
                        viewModel.addRemoteToList(list.id, selection.entry, selection.identity, libraryId)
                        onDismiss()
                    }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(list.name) }
                }
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("New list name") })
                TextButton(enabled = name.isNotBlank(), onClick = {
                    viewModel.createReadingList(name) { id ->
                        viewModel.addRemoteToList(id, selection.entry, selection.identity, libraryId)
                    }
                    onDismiss()
                }) { Text("Create list and add") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } })
}
