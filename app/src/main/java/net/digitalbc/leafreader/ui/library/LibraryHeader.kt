package net.digitalbc.leafreader.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.digitalbc.leafreader.R
import net.digitalbc.leafreader.ui.theme.Archivo
import net.digitalbc.leafreader.ui.theme.Ochre

@Composable
internal fun LibraryTabs(selected: LibraryFilter, onSelect: (LibraryFilter) -> Unit) {
    val views = LibraryFilter.entries
    TabRow(
        selectedTabIndex = views.indexOf(selected),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        indicator = { positions ->
            TabRowDefaults.SecondaryIndicator(
                Modifier.tabIndicatorOffset(positions[views.indexOf(selected)]), color = Ochre)
        },
    ) {
        views.forEach { view ->
            Tab(selected = view == selected, onClick = { onSelect(view) },
                modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(when (view) {
                    LibraryFilter.ALL -> R.string.library_view_all
                    LibraryFilter.SERIES -> R.string.library_view_series
                    LibraryFilter.READING_LISTS -> R.string.library_view_lists
                    LibraryFilter.OFFLINE -> R.string.library_view_offline
                }), modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp), fontFamily = Archivo)
            }
        }
    }
}

@Composable
internal fun UnreadOnlyControl(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, onToolbar: Boolean = false) {
    IconToggleButton(checked = checked, onCheckedChange = onCheckedChange,
        modifier = modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { selected = checked },
        colors = IconButtonDefaults.iconToggleButtonColors(
            contentColor = if (onToolbar) net.digitalbc.leafreader.ui.theme.PaperWhite else MaterialTheme.colorScheme.onSurface,
            checkedContentColor = net.digitalbc.leafreader.ui.theme.Ink,
            checkedContainerColor = Ochre)) {
        Box {
            Icon(Icons.AutoMirrored.Filled.MenuBook, stringResource(R.string.library_unread_only))
            Box(Modifier.size(6.dp).align(Alignment.TopEnd).background(if (checked) net.digitalbc.leafreader.ui.theme.Ink else Ochre, androidx.compose.foundation.shape.CircleShape))
        }
    }
}

@Composable
internal fun UnreadOnlyChip(onClear: () -> Unit) {
    AssistChip(onClick = onClear, label = { Text(stringResource(R.string.library_unread_only)) },
        trailingIcon = { Icon(Icons.Default.Close, stringResource(R.string.library_clear_unread)) },
        modifier = Modifier.heightIn(min = 48.dp))
}
