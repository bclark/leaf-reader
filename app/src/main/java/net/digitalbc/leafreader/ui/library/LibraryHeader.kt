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
import androidx.compose.ui.unit.dp
import net.digitalbc.leafreader.R
import net.digitalbc.leafreader.ui.theme.Archivo
import net.digitalbc.leafreader.ui.theme.Ochre

@Composable
internal fun LibraryTabs(selected: LibraryFilter, onSelect: (LibraryFilter) -> Unit) {
    val views = LibraryFilter.entries
    val largeType = androidx.compose.ui.platform.LocalConfiguration.current.fontScale > 1.3f
    val content: @Composable () -> Unit = {
        views.forEach { view ->
            Tab(selected = view == selected, onClick = { onSelect(view) },
                modifier = Modifier.heightIn(min = 48.dp),
                selectedContentColor = MaterialTheme.colorScheme.tertiary,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant) {
                Text(stringResource(when (view) {
                    LibraryFilter.ALL -> R.string.library_view_all
                    LibraryFilter.SERIES -> R.string.library_view_series
                    LibraryFilter.READING_LISTS -> R.string.library_view_lists
                    LibraryFilter.OFFLINE -> R.string.library_view_offline
                }), modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp), fontFamily = Archivo,
                    maxLines = if (largeType) 1 else 2, softWrap = !largeType)
            }
        }
    }
    val indicator: @Composable (List<TabPosition>) -> Unit = { positions ->
        TabRowDefaults.SecondaryIndicator(
            Modifier.tabIndicatorOffset(positions[views.indexOf(selected)]), height = 3.dp, color = MaterialTheme.colorScheme.tertiary)
    }
    if (largeType) ScrollableTabRow(selectedTabIndex = views.indexOf(selected), edgePadding = 0.dp,
        containerColor = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground,
        indicator = indicator, tabs = content)
    else TabRow(selectedTabIndex = views.indexOf(selected), containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground, indicator = indicator, tabs = content)

}

@Composable
internal fun UnreadOnlyControl(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, onToolbar: Boolean = false) {
    FilterChip(selected = checked, onClick = { onCheckedChange(!checked) },
        modifier = modifier.heightIn(min = 48.dp),
        label = { Text(stringResource(R.string.library_unread_label)) },
        colors = FilterChipDefaults.filterChipColors(
            labelColor = if (onToolbar) net.digitalbc.leafreader.ui.theme.PaperWhite else MaterialTheme.colorScheme.onSurface,
            iconColor = if (onToolbar) net.digitalbc.leafreader.ui.theme.PaperWhite else MaterialTheme.colorScheme.onSurface,
            selectedLabelColor = net.digitalbc.leafreader.ui.theme.Ink,
            selectedLeadingIconColor = net.digitalbc.leafreader.ui.theme.Ink,
            selectedContainerColor = Ochre),
        leadingIcon = {
            Box {
                Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(20.dp))
                Box(Modifier.size(6.dp).align(Alignment.TopEnd).background(
                    if (checked) net.digitalbc.leafreader.ui.theme.Ink else Ochre,
                    androidx.compose.foundation.shape.CircleShape))
            }
        })
}

@Composable
internal fun UnreadOnlyChip(onClear: () -> Unit) {
    AssistChip(onClick = onClear, label = { Text(stringResource(R.string.library_unread_only)) },
        trailingIcon = { Icon(Icons.Default.Close, stringResource(R.string.library_clear_unread)) },
        colors = AssistChipDefaults.assistChipColors(containerColor = Ochre,
            labelColor = net.digitalbc.leafreader.ui.theme.Ink, trailingIconContentColor = net.digitalbc.leafreader.ui.theme.Ink),
        border = null, modifier = Modifier.heightIn(min = 48.dp))
}
