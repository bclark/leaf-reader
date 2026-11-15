package net.digitalbc.leafreader.ui.library

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.digitalbc.leafreader.R
import net.digitalbc.leafreader.data.jellyfin.JellyfinProgress
import net.digitalbc.leafreader.data.jellyfin.JellyfinProgressChoice
import net.digitalbc.leafreader.data.jellyfin.JellyfinProgressConflict

@Composable
internal fun ProgressConflictHost(viewModel: LibraryViewModel) {
    val conflicts by viewModel.progressConflicts.collectAsStateWithLifecycle()
    val resolving by viewModel.resolvingProgress.collectAsStateWithLifecycle()
    val error by viewModel.progressChoiceError.collectAsStateWithLifecycle()
    val retryMessage = stringResource(R.string.sync_choice_retry)
    conflicts.firstOrNull { !it.deferred }?.let { conflict ->
        ProgressConflictDialog(conflict, resolving, error,
            onChoice = { viewModel.resolveProgressConflict(conflict, it, retryMessage) },
            onDefer = { viewModel.deferProgressConflict(conflict.key) })
    }
}

@Composable
internal fun ProgressConflictDialog(
    conflict: JellyfinProgressConflict,
    resolving: Boolean,
    error: String?,
    onChoice: (JellyfinProgressChoice) -> Unit,
    onDefer: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!resolving) onDefer() },
        title = { Text(stringResource(R.string.sync_positions_differ)) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.sync_choose_position, conflict.title))
                Text(stringResource(R.string.sync_device), Modifier.semantics { heading() })
                Text(stringResource(R.string.sync_position_state, conflict.localPage + 1, conflict.pageCount,
                    stringResource(if (conflict.localPlayed) R.string.sync_read else R.string.sync_not_completed)))
                Text(stringResource(R.string.sync_server), Modifier.semantics { heading() })
                Text(stringResource(R.string.sync_position_state,
                    JellyfinProgress.ticksToPage(conflict.remote.positionTicks!!, conflict.pageCount) + 1,
                    conflict.pageCount,
                    stringResource(if (conflict.remote.played == true) R.string.sync_read else R.string.sync_not_completed)))
                if (resolving) CircularProgressIndicator(Modifier.size(24.dp))
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Column {
                TextButton(onClick = { onChoice(JellyfinProgressChoice.DEVICE) }, enabled = !resolving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Text(stringResource(R.string.sync_use_device))
                }
                TextButton(onClick = { onChoice(JellyfinProgressChoice.SERVER) }, enabled = !resolving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Text(stringResource(R.string.sync_use_server))
                }
                TextButton(onClick = onDefer, enabled = !resolving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Text(stringResource(R.string.sync_keep_both))
                }
            }
        },
    )
}
