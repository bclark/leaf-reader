package net.digitalbc.leafreader.ui.library

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

/** One native multi-file contract and MIME policy for Library and first launch. */
@Composable
internal fun rememberComicFilePicker(onPicked: (List<Uri>) -> Unit): ActivityResultLauncher<Array<String>> =
    rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { onPicked(it.orEmpty()) }

internal val comicFileMimeTypes: Array<String> get() = arrayOf("*/*")
