package net.digitalbc.leafreader.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.digitalbc.leafreader.R
import net.digitalbc.leafreader.data.jellyfin.JellyfinFailureKind
import net.digitalbc.leafreader.ui.theme.ComicReaderTheme

/** A connection problem never covers or disables the local library. */
@Composable
internal fun JellyfinLaunchNotice(
    failure: JellyfinFailureKind,
    retrying: Boolean,
    onRetry: () -> Unit,
    onSettings: () -> Unit,
    showingDownloads: Boolean = true,
) {
    val message = when {
        !showingDownloads -> when (failure) {
            JellyfinFailureKind.AUTHENTICATION -> R.string.jellyfin_saved_auth
            JellyfinFailureKind.ACCESS_DENIED -> R.string.jellyfin_saved_denied
            JellyfinFailureKind.TIMEOUT -> R.string.jellyfin_saved_timeout
            JellyfinFailureKind.UNAVAILABLE, JellyfinFailureKind.IO, JellyfinFailureKind.SERVER_ERROR -> R.string.jellyfin_saved_unreachable
            else -> R.string.jellyfin_saved_failed
        }
        else -> when (failure) {
            JellyfinFailureKind.AUTHENTICATION -> R.string.jellyfin_launch_auth
            JellyfinFailureKind.ACCESS_DENIED -> R.string.jellyfin_launch_denied
            JellyfinFailureKind.TIMEOUT -> R.string.jellyfin_launch_timeout
            JellyfinFailureKind.UNAVAILABLE, JellyfinFailureKind.IO, JellyfinFailureKind.SERVER_ERROR ->
                R.string.jellyfin_launch_unreachable
            else -> R.string.jellyfin_launch_failed
        }
    }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(message), color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (retrying) Text(stringResource(R.string.jellyfin_launch_retrying))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onRetry, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), enabled = !retrying, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(stringResource(R.string.jellyfin_retry))
                }
                if (failure in listOf(JellyfinFailureKind.AUTHENTICATION,
                        JellyfinFailureKind.ACCESS_DENIED, JellyfinFailureKind.INVALID_CONFIGURATION)) {
                    TextButton(onClick = onSettings, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface), modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.jellyfin_settings_action))
                    }
                }
            }
        }
    }
}

@Preview(name = "Offline launch · large text", widthDp = 390, fontScale = 2f)
@Composable
internal fun JellyfinLaunchNoticePreview() {
    ComicReaderTheme {
        JellyfinLaunchNotice(JellyfinFailureKind.UNAVAILABLE, false, {}, {})
    }
}
