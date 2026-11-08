package net.digitalbc.leafreader.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.digitalbc.leafreader.R
import net.digitalbc.leafreader.data.jellyfin.JellyfinItem
import net.digitalbc.leafreader.data.jellyfin.JellyfinProgress
import net.digitalbc.leafreader.data.jellyfin.JellyfinView
import net.digitalbc.leafreader.ui.brand.OchreBadge
import net.digitalbc.leafreader.ui.brand.halftone
import net.digitalbc.leafreader.ui.theme.Anton
import net.digitalbc.leafreader.ui.theme.Archivo
import net.digitalbc.leafreader.ui.theme.DeepInk
import net.digitalbc.leafreader.ui.theme.Ink
import net.digitalbc.leafreader.ui.theme.LeafAccent
import net.digitalbc.leafreader.ui.theme.LeafPrimary
import net.digitalbc.leafreader.ui.theme.LeafSecondary
import net.digitalbc.leafreader.ui.theme.Ochre
import net.digitalbc.leafreader.ui.theme.PaperWhite
import net.digitalbc.leafreader.ui.theme.PaperWhiteMuted

@Composable
fun JellyfinBrowserScreen(
    viewModel: LibraryViewModel,
    onBack: () -> Unit,
) {
    val browser by viewModel.jellyfinBrowser.collectAsStateWithLifecycle()
    val importing by viewModel.importing.collectAsStateWithLifecycle()
    val importProgress by viewModel.importProgress.collectAsStateWithLifecycle()
    val importDetail by viewModel.importDetail.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    DisposableEffect(viewModel) {
        onDispose { viewModel.cancelJellyfinBrowse() }
    }

    val selectedCount = browser.selectedItemIds.size
    val importableItems = remember(browser.items, browser.downloadedItemIds) {
        browser.items.filter { it.isImportable && it.id !in browser.downloadedItemIds }
    }
    val allSelected = importableItems.isNotEmpty() && selectedCount == importableItems.size

    BackHandler(enabled = !importing) {
        if (selectedCount > 0) {
            viewModel.clearJellyfinSelection()
        } else if (!browser.isRoot) {
            viewModel.navigateJellyfinUp()
        } else {
            onBack()
        }
    }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Top Bar
        Surface(
            color = LeafPrimary,
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = "Back"
                        ) {
                            if (selectedCount > 0) {
                                viewModel.clearJellyfinSelection()
                            } else if (!browser.isRoot) {
                                viewModel.navigateJellyfinUp()
                            } else {
                                onBack()
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(PaperWhite.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = PaperWhite,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.size(10.dp))
                Column {
                    OchreBadge("JELLYFIN")
                    Text(
                        browser.currentTitle,
                        fontFamily = Archivo,
                        fontSize = 11.sp,
                        color = PaperWhiteMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            Box(Modifier.matchParentSize().halftone(LeafSecondary, alpha = 0.05f))

            Column(
                Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 16.dp),
            ) {
                // Header / selection controls (when not at root)
                if (!browser.isRoot && importableItems.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (selectedCount > 0) "$selectedCount SELECTED" else "${importableItems.size} COMICS AVAILABLE",
                            fontFamily = Archivo,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.5.sp,
                            color = if (selectedCount > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (selectedCount > 0 && !allSelected) {
                                Box(
                                    modifier = Modifier
                                        .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .border(1.dp, DeepInk, RoundedCornerShape(4.dp))
                                        .clickable(role = Role.Button) { viewModel.clearJellyfinSelection() }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        stringResource(R.string.jellyfin_deselect_all),
                                        fontFamily = Anton,
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (allSelected) Ochre else MaterialTheme.colorScheme.surfaceVariant)
                                    .border(1.dp, if (allSelected) Ochre else DeepInk, RoundedCornerShape(4.dp))
                                    .clickable(role = Role.Button) {
                                        if (allSelected) viewModel.clearJellyfinSelection() else viewModel.selectAllJellyfin()
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    if (allSelected) stringResource(R.string.jellyfin_clear) else stringResource(R.string.jellyfin_select_all),
                                    fontFamily = Anton,
                                    fontSize = 10.5.sp,
                                    color = if (allSelected) Ink else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // Main content
                if (browser.loading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Ochre, strokeWidth = 3.dp)
                    }
                } else if (browser.failure != null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "Failed to load: ${browser.failure}",
                                fontFamily = Archivo,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Ochre)
                                    .clickable(role = Role.Button) {
                                        if (browser.isRoot) viewModel.loadJellyfinRoot()
                                        else browser.currentParentId?.let { viewModel.loadJellyfinFolder(it, browser.currentTitle) }
                                    }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(stringResource(R.string.jellyfin_retry), fontFamily = Anton, fontSize = 12.sp, color = Ink)
                            }
                        }
                    }
                } else if (browser.isRoot) {
                    // Root: show library views
                    if (browser.views.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.jellyfin_no_libraries),
                                fontFamily = Archivo,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                        ) {
                            items(browser.views, key = { it.id }) { view ->
                                JellyfinViewRow(
                                    view = view,
                                    onClick = { viewModel.loadJellyfinFolder(view.id, view.name) },
                                )
                            }
                        }
                    }
                } else {
                    // Inside library/folder: show folders and items
                    if (browser.items.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.jellyfin_no_items),
                                fontFamily = Archivo,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 4.dp),
                        ) {
                            items(browser.items, key = { it.id }) { item ->
                                val isDownloaded = item.id in browser.downloadedItemIds
                                val isSelected = item.id in browser.selectedItemIds

                                JellyfinItemRow(
                                    item = item,
                                    isDownloaded = isDownloaded,
                                    isSelected = isSelected,
                                    onClick = {
                                        if (item.isFolder) {
                                            viewModel.loadJellyfinFolder(item.id, item.name)
                                        } else if (item.isImportable && !isDownloaded) {
                                            viewModel.toggleJellyfinSelection(item.id)
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                // Bottom import action bar
                AnimatedVisibility(visible = selectedCount > 0 && !importing) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Ochre)
                            .clickable(role = Role.Button) {
                                val selectedItems = browser.items.filter { it.id in browser.selectedItemIds }
                                viewModel.importJellyfinItems(selectedItems) {
                                    viewModel.clearJellyfinSelection()
                                }
                            }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "DOWNLOAD $selectedCount ${if (selectedCount == 1) "COMIC" else "COMICS"}",
                            fontFamily = Anton,
                            fontSize = 15.sp,
                            letterSpacing = 0.5.sp,
                            color = Ink,
                        )
                    }
                }

                // In-progress import banner
                AnimatedVisibility(visible = importing) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            importDetail ?: "Downloading from Jellyfin…",
                            fontFamily = Archivo,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (importProgress != null) {
                            LinearProgressIndicator(
                                progress = { importProgress ?: 0f },
                                modifier = Modifier.fillMaxWidth(),
                                color = Ochre,
                                trackColor = DeepInk.copy(alpha = 0.2f),
                            )
                        } else {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = Ochre,
                                trackColor = DeepInk.copy(alpha = 0.2f),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            Box(
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .clickable(role = Role.Button) { viewModel.cancelImport() }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    stringResource(R.string.cancel).uppercase(),
                                    fontFamily = Anton,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun JellyfinViewRow(
    view: JellyfinView,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = "Library",
            tint = Ochre,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                view.name,
                fontFamily = Archivo,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            view.collectionType?.let { type ->
                Text(
                    type.uppercase(),
                    fontFamily = Archivo,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun JellyfinItemRow(
    item: JellyfinItem,
    isDownloaded: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(6.dp)
    val bgColor = if (isSelected) {
        Ochre.copy(alpha = 0.15f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val borderColor = if (isSelected) Ochre else Color.Transparent

    val role = if (item.isFolder) {
        Role.Button
    } else if (item.isImportable && !isDownloaded) {
        Role.Checkbox
    } else {
        null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clip(shape)
            .background(bgColor)
            .border(1.dp, borderColor, shape)
            .then(
                if (role != null) {
                    Modifier.clickable(role = role, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (item.isFolder) Icons.Default.Folder else Icons.AutoMirrored.Filled.MenuBook,
            contentDescription = if (item.isFolder) "Folder" else "Comic",
            tint = if (item.isFolder) Ochre else LeafAccent,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.name,
                fontFamily = Archivo,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!item.isFolder) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Text(
                        item.fileExtension.uppercase(),
                        fontFamily = Archivo,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    if (item.sizeBytes > 0) {
                        Text(
                            "•  ${formatFileSize(item.sizeBytes)}",
                            fontFamily = Archivo,
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (item.played) {
                        Text(
                            "•  ${stringResource(R.string.jellyfin_badge_read)}",
                            fontFamily = Archivo,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            color = LeafAccent,
                        )
                    } else if (item.playbackPositionTicks > 0L) {
                        Text(
                            "•  PAGE ${JellyfinProgress.ticksToPage(item.playbackPositionTicks) + 1}",
                            fontFamily = Archivo,
                            fontSize = 10.sp,
                            color = Ochre,
                        )
                    }
                }
            }
        }

        if (isDownloaded) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .background(LeafAccent.copy(alpha = 0.2f))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = stringResource(R.string.jellyfin_badge_downloaded),
                        tint = LeafAccent,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        stringResource(R.string.jellyfin_badge_downloaded),
                        fontFamily = Archivo,
                        fontWeight = FontWeight.Bold,
                        fontSize = 9.sp,
                        color = LeafAccent,
                    )
                }
            }
        } else if (item.isImportable) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .border(
                        1.5.dp,
                        if (isSelected) Ochre else MaterialTheme.colorScheme.onSurfaceVariant,
                        RoundedCornerShape(4.dp),
                    )
                    .background(if (isSelected) Ochre else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = Ink,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> String.format(java.util.Locale.US, "%.1f GB", gb)
        mb >= 1.0 -> String.format(java.util.Locale.US, "%.1f MB", mb)
        kb >= 1.0 -> String.format(java.util.Locale.US, "%.0f KB", kb)
        else -> "$bytes B"
    }
}
