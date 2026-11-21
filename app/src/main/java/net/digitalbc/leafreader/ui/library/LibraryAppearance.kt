package net.digitalbc.leafreader.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.digitalbc.leafreader.R
import net.digitalbc.leafreader.data.settings.AppSettings
import net.digitalbc.leafreader.data.settings.LibraryAppearance

internal val LocalLibraryAppearance = staticCompositionLocalOf { LibraryAppearance() }

@Composable
internal fun LibraryAppearanceControls(settings: AppSettings) {
    val appearance by settings.libraryAppearanceFlow.collectAsStateWithLifecycle()
    Column {
        AppearanceSwitch(stringResource(R.string.library_show_titles), appearance.showTitles) {
            settings.libraryAppearance = appearance.copy(showTitles = it)
        }
        AppearanceSwitch(stringResource(R.string.library_show_details), appearance.showDetails) {
            settings.libraryAppearance = appearance.copy(showDetails = it)
        }
        AppearanceSwitch(stringResource(R.string.library_show_reading), appearance.showReadingIndicators) {
            settings.libraryAppearance = appearance.copy(showReadingIndicators = it)
        }
        Text(stringResource(R.string.library_appearance_availability), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AppearanceSwitch(label: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, role = Role.Switch, onValueChange = change)
        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface)
        Switch(checked, onCheckedChange = null, colors = SwitchDefaults.colors(
            checkedThumbColor = net.digitalbc.leafreader.ui.theme.Ink,
            checkedTrackColor = net.digitalbc.leafreader.ui.theme.Ochre))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryAppearanceSheet(settings: AppSettings, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(24.dp)) {
            Text(stringResource(R.string.library_appearance), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(16.dp))
            LibraryAppearanceControls(settings)
            TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp)) {
                Text(stringResource(R.string.library_appearance_done))
            }
        }
    }
}

/** Only real fractions get a strip; remote position without a denominator gets a marker. */
@Composable
internal fun BoxScope.CoverReadingIndicator(completed: Boolean, started: Boolean, progress: Float?) {
    if (!LocalLibraryAppearance.current.showReadingIndicators) return
    if (completed || (started && progress == null)) {
        Surface(Modifier.align(Alignment.TopStart).padding(6.dp).size(24.dp), shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = .95f)) {
            Icon(if (completed) Icons.Default.Check else Icons.Default.Bookmark, null,
                Modifier.padding(4.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
    } else if (started && progress != null) {
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = .25f))) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(MaterialTheme.colorScheme.tertiary))
        }
    }
}

/** Availability is independent of optional reading indicators and metadata captions. */
@Composable
internal fun BoxScope.CoverAvailability(downloaded: Boolean) {
    Surface(Modifier.align(Alignment.BottomEnd).padding(6.dp).size(24.dp), shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = .95f)) {
        Icon(if (downloaded) Icons.Default.OfflinePin else Icons.Default.CloudDownload,
            null, Modifier.padding(4.dp), tint = MaterialTheme.colorScheme.onSurface)
    }
}
