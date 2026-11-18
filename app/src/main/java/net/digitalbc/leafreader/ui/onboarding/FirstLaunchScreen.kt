package net.digitalbc.leafreader.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.digitalbc.leafreader.R
import net.digitalbc.leafreader.data.settings.FirstLaunchState
import net.digitalbc.leafreader.ui.brand.LeafWordmark
import net.digitalbc.leafreader.ui.theme.LeafAccent
import net.digitalbc.leafreader.ui.theme.LeafPrimary
import net.digitalbc.leafreader.ui.theme.LeafSecondary
import net.digitalbc.leafreader.ui.theme.PaperWhite
import net.digitalbc.leafreader.ui.theme.DeepInk

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FirstLaunchScreen(
    state: FirstLaunchUi,
    progress: Float,
    onChooseFile: () -> Unit,
    onJellyfin: () -> Unit,
    onConnect: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    onRetrySave: () -> Unit,
) {
    val optional = state.destination == FirstLaunchState.OPTIONAL_JELLYFIN
    val idle = !state.busy && state.presentation == FirstLaunchPresentation.NONE && state.destination != null
    val enabled = idle && state.error != FirstLaunchError.SAVE
    val primaryFocus = remember { FocusRequester() }
    Scaffold(
        topBar = {
            Surface(color = LeafPrimary, contentColor = PaperWhite) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().heightIn(min = 80.dp).padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (optional) {
                        IconButton(onClick = onBack, enabled = enabled) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.onboarding_back))
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    LeafWordmark()
                }
            }
        },
    ) { insets ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets)) {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Scaffold subcomposes its body during measurement. Request focus from this
                    // attached subtree, after a frame, rather than from the outer composition.
                    LaunchedEffect(optional, enabled) {
                        if (enabled) { withFrameNanos { }; primaryFocus.requestFocus() }
                    }
                    Image(painterResource(if (optional) R.drawable.ic_onboarding_jellyfin else R.drawable.ic_leaf_logo),
                        contentDescription = null, modifier = Modifier.size(88.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(if (optional) R.string.onboarding_jellyfin_title else R.string.onboarding_welcome_title),
                        style = MaterialTheme.typography.headlineLarge,
                        modifier = Modifier.semantics { heading() })
                    Text(stringResource(if (optional) R.string.onboarding_jellyfin_body else R.string.onboarding_welcome_body),
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    if (state.busy || state.presentation == FirstLaunchPresentation.IMPORT) {
                        if (state.presentation == FirstLaunchPresentation.IMPORT)
                            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(stringResource(if (state.presentation == FirstLaunchPresentation.IMPORT)
                            R.string.onboarding_importing else R.string.onboarding_saving),
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    }
                    state.error?.let { error ->
                        Text(stringResource(when (error) {
                            FirstLaunchError.SAVE -> R.string.onboarding_save_error
                            FirstLaunchError.PREPARE -> R.string.onboarding_prepare_error
                            FirstLaunchError.IMPORT -> R.string.onboarding_import_error
                            FirstLaunchError.INTERRUPTED -> R.string.onboarding_interrupted
                        }), color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                        if (error == FirstLaunchError.SAVE) OutlinedButton(onClick = onRetrySave, enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                            Text(stringResource(R.string.onboarding_retry_save))
                        }
                    }
                    Button(onClick = if (optional) onConnect else onChooseFile, enabled = enabled,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (optional) LeafSecondary else LeafAccent,
                            contentColor = if (optional) PaperWhite else DeepInk),
                        contentPadding = PaddingValues(16.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).focusRequester(primaryFocus)) {
                        Icon(if (optional) Icons.Default.Dns else Icons.Default.FolderOpen, null)
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(if (optional) R.string.onboarding_connect else R.string.onboarding_choose_file),
                            style = MaterialTheme.typography.titleMedium)
                    }
                    OutlinedButton(onClick = if (optional) onSkip else onJellyfin, enabled = enabled,
                        shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        contentPadding = PaddingValues(16.dp),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text(stringResource(if (optional) R.string.onboarding_without_jellyfin else R.string.onboarding_use_jellyfin),
                            color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                    }
                    if (!optional) TextButton(onClick = onSkip, enabled = enabled,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text(stringResource(R.string.onboarding_later), color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
