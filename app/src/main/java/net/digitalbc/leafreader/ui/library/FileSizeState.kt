package net.digitalbc.leafreader.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun rememberFileSize(filePath: String?): State<Long> = key(filePath) {
    // Reset the state as well as the producer when a lazy card changes files.
    produceState(0L, filePath) {
        value = readFileSize(filePath)
    }
}

internal suspend fun readFileSize(
    filePath: String?,
    readLength: (String) -> Long = { File(it).length() },
): Long = withContext(Dispatchers.IO) {
    // Keep the existing empty-label fallback for missing or inaccessible files.
    runCatching { filePath?.let(readLength) ?: 0L }.getOrDefault(0L)
}
