package net.digitalbc.leafreader.ui.menu

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import net.digitalbc.leafreader.R
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.digitalbc.leafreader.data.settings.AppSettings
import net.digitalbc.leafreader.data.settings.ThemeMode
import net.digitalbc.leafreader.data.settings.LauncherIcon
import net.digitalbc.leafreader.data.smb.SmbClient
import net.digitalbc.leafreader.data.smb.SmbErrors
import kotlinx.coroutines.CancellationException
import androidx.compose.material3.Surface
import net.digitalbc.leafreader.ui.brand.LeafWordmark
import net.digitalbc.leafreader.ui.brand.OchreBadge
import net.digitalbc.leafreader.ui.brand.comicShadow
import net.digitalbc.leafreader.ui.brand.halftone
import net.digitalbc.leafreader.ui.theme.Anton
import net.digitalbc.leafreader.ui.theme.Archivo
import net.digitalbc.leafreader.ui.theme.Cream
import net.digitalbc.leafreader.ui.theme.Crimson
import net.digitalbc.leafreader.ui.theme.Ink
import net.digitalbc.leafreader.ui.theme.LeafPrimary
import net.digitalbc.leafreader.ui.theme.LeafSecondary
import net.digitalbc.leafreader.ui.theme.Ochre
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import net.digitalbc.leafreader.data.backup.BackupConflictStrategy
import net.digitalbc.leafreader.data.backup.BackupPreview
import net.digitalbc.leafreader.data.library.LibraryRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val SOURCE_URL = "https://github.com/bclark/leaf-reader"
private const val UPSTREAM_URL = "https://github.com/batunii/chika"
private const val PRIVACY_URL = "https://github.com/bclark/leaf-reader/blob/main/PRIVACY.md"
private const val LICENSES_URL = "https://github.com/bclark/leaf-reader/blob/main/THIRD_PARTY_NOTICES.md"
private const val SUPPORT_KOFI_URL = "https://ko-fi.com/bryanclark"

@Composable
fun MenuScreen(
    themeMode: ThemeMode,
    onSetThemeMode: (ThemeMode) -> Unit,
    launcherIcon: LauncherIcon,
    onSetLauncherIcon: (LauncherIcon) -> Unit,
    amoledTheme: Boolean,
    onSetAmoledTheme: (Boolean) -> Unit,
    pageSlideEnabled: Boolean,
    onSetPageSlideEnabled: (Boolean) -> Unit,
    pageFitMode: net.digitalbc.leafreader.ui.reader.PageFitMode = net.digitalbc.leafreader.ui.reader.PageFitMode.FIT_PAGE,
    onSetPageFitMode: (net.digitalbc.leafreader.ui.reader.PageFitMode) -> Unit = {},
    portraitSpread: Boolean = false,
    onSetPortraitSpread: (Boolean) -> Unit = {},
    landscapeSpread: Boolean = false,
    onSetLandscapeSpread: (Boolean) -> Unit = {},
    settings: AppSettings,
    repository: LibraryRepository? = null,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var smbHost by remember { mutableStateOf(settings.smbHost) }
    var smbShare by remember { mutableStateOf(settings.smbShare) }
    var smbUsername by remember { mutableStateOf(settings.smbUsername) }
    var smbPassword by remember { mutableStateOf(settings.smbPassword) }
    var smbTesting by remember { mutableStateOf(false) }
    var smbValid by remember { mutableStateOf<Boolean?>(null) }
    var smbStatusMessage by remember { mutableStateOf<String?>(null) }
    var showLicensesDialog by remember { mutableStateOf(false) }

    var isExporting by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var pendingBackupJson by remember { mutableStateOf<String?>(null) }
    var restorePreview by remember { mutableStateOf<BackupPreview?>(null) }
    var selectedStrategy by remember { mutableStateOf(BackupConflictStrategy.MOST_RECENT) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null || repository == null) return@rememberLauncherForActivityResult
        isExporting = true
        scope.launch {
            try {
                val appVer = runCatching {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName
                }.getOrNull() ?: "0.3.4"
                val json = repository.exportBackup(appVer)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(json.toByteArray(Charsets.UTF_8))
                    }
                }
                Toast.makeText(context, "✓ Backup exported successfully", Toast.LENGTH_SHORT).show()
            } catch (t: Throwable) {
                Toast.makeText(context, "✕ Export failed: ${t.message}", Toast.LENGTH_LONG).show()
            } finally {
                isExporting = false
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null || repository == null) return@rememberLauncherForActivityResult
        isImporting = true
        scope.launch {
            try {
                val json = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.bufferedReader(Charsets.UTF_8).readText()
                    }
                } ?: throw IllegalArgumentException("Could not read backup file")
                val previewResult = repository.previewBackup(json)
                if (previewResult.isSuccess) {
                    pendingBackupJson = json
                    restorePreview = previewResult.getOrThrow()
                } else {
                    val err = previewResult.exceptionOrNull()?.message ?: "Unknown error"
                    Toast.makeText(context, "✕ Invalid backup: $err", Toast.LENGTH_LONG).show()
                }
            } catch (t: Throwable) {
                Toast.makeText(context, "✕ Failed to open backup: ${t.message}", Toast.LENGTH_LONG).show()
            } finally {
                isImporting = false
            }
        }
    }

    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }
    val openSource = {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)))
    }
    val openUpstream = {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(UPSTREAM_URL)))
    }
    val openKofi = {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_KOFI_URL)))
        }.onFailure {
            Toast.makeText(context, "No web browser found to open link.", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // App Bar: leaf_primary (#1C3A27) anchors the brand at the top of the screen
        Surface(
            color = LeafPrimary,
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Cream.copy(alpha = 0.14f))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, "Back",
                        tint = Cream, modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.size(14.dp))
                OchreBadge("MENU")
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            Box(Modifier.matchParentSize().halftone(LeafSecondary, alpha = 0.05f))

            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(top = 16.dp, bottom = 28.dp),
            ) {

            Spacer(Modifier.size(28.dp))

            // ---- Theme ---------------------------------------------------------------
            SectionLabel("APPEARANCE")
            Spacer(Modifier.size(10.dp))
            AppearanceChoice(
                title = "App theme",
                options = listOf(ThemeMode.SYSTEM to "System", ThemeMode.LIGHT to "Light", ThemeMode.DARK to "Dark"),
                selected = themeMode,
                onSelect = onSetThemeMode,
            )
            Spacer(Modifier.size(12.dp))
            if (themeMode != ThemeMode.LIGHT) {
                ToggleRow(
                    title = "AMOLED Black",
                    subtitle = "Use a true-black background when the app is in dark mode.",
                    checked = amoledTheme,
                    onCheckedChange = onSetAmoledTheme,
                )
                Spacer(Modifier.size(12.dp))
            }
            AppearanceChoice(
                title = "App icon",
                options = listOf(LauncherIcon.LIGHT to "Light", LauncherIcon.DARK to "Dark"),
                selected = launcherIcon,
                onSelect = { icon ->
                    runCatching { onSetLauncherIcon(icon) }.onFailure {
                        Toast.makeText(context, "Could not change app icon. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                },
            )
            Text(
                "Your launcher controls themed icons and may take a moment to refresh the illustration.",
                fontFamily = Archivo,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.size(32.dp))

            // ---- Reading -------------------------------------------------------------
            SectionLabel("READING")
            Spacer(Modifier.size(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (!net.digitalbc.leafreader.ComicReaderApp.EXPERIMENT_DISABLE_PANEL_READING) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Reading Mode",
                            fontFamily = Archivo,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            if (pageSlideEnabled) {
                                "Turn full pages with a smooth slide transition."
                            } else {
                                "Guided reading that automatically zooms and steps through each detected panel."
                            },
                            fontFamily = Archivo,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (pageSlideEnabled) Ochre else Color.Transparent)
                                    .clickable { onSetPageSlideEnabled(true) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "PAGE FLIP",
                                    fontFamily = Archivo,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (pageSlideEnabled) Ink else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (!pageSlideEnabled) Ochre else Color.Transparent)
                                    .clickable { onSetPageSlideEnabled(false) }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "PANEL BY PANEL",
                                    fontFamily = Archivo,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = if (!pageSlideEnabled) Ink else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Page Fit",
                        fontFamily = Archivo,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (pageFitMode == net.digitalbc.leafreader.ui.reader.PageFitMode.FIT_WIDTH) {
                            "Fit page to screen width for easier reading of small text and speech bubbles."
                        } else {
                            "Fit the entire page on screen."
                        },
                        fontFamily = Archivo,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (pageFitMode == net.digitalbc.leafreader.ui.reader.PageFitMode.FIT_PAGE) Ochre else Color.Transparent)
                                .clickable { onSetPageFitMode(net.digitalbc.leafreader.ui.reader.PageFitMode.FIT_PAGE) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "FIT TO PAGE",
                                fontFamily = Archivo,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (pageFitMode == net.digitalbc.leafreader.ui.reader.PageFitMode.FIT_PAGE) Ink else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (pageFitMode == net.digitalbc.leafreader.ui.reader.PageFitMode.FIT_WIDTH) Ochre else Color.Transparent)
                                .clickable { onSetPageFitMode(net.digitalbc.leafreader.ui.reader.PageFitMode.FIT_WIDTH) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "FIT TO WIDTH",
                                fontFamily = Archivo,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (pageFitMode == net.digitalbc.leafreader.ui.reader.PageFitMode.FIT_WIDTH) Ink else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                LayoutChoice("Portrait layout", portraitSpread, onSetPortraitSpread)
                LayoutChoice("Landscape layout", landscapeSpread, onSetLandscapeSpread)
                Text(
                    "The cover and wide pages stay single in either layout.",
                    fontFamily = Archivo, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(32.dp))

            // ---- Library -------------------------------------------------------------
            SectionLabel("LIBRARY")
            Spacer(Modifier.size(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Grid Columns",
                    fontFamily = Archivo,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "Choose how many comic covers appear per row on the library screen.",
                    fontFamily = Archivo,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    var selectedColumns by remember { mutableIntStateOf(settings.libraryColumns) }
                    listOf(2, 3, 4, 6).forEach { count ->
                        val isSelected = selectedColumns == count
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (isSelected) Ochre else Color.Transparent)
                                .clickable {
                                    settings.libraryColumns = count
                                    selectedColumns = count
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "$count COL",
                                fontFamily = Archivo,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (isSelected) Ink else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.size(32.dp))

            // ---- SMB Drive -----------------------------------------------------------
            SectionLabel("SMB DRIVE")
            Spacer(Modifier.size(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SmbField(
                    label = "Host / IP (e.g. 192.168.1.50)",
                    value = smbHost,
                    onValueChange = { smbHost = it; smbValid = null; smbStatusMessage = null },
                )
                SmbField(
                    label = "Share (e.g. comics or media/comics)",
                    value = smbShare,
                    onValueChange = { smbShare = it; smbValid = null; smbStatusMessage = null },
                )
                SmbField(
                    label = "Username (optional)",
                    value = smbUsername,
                    onValueChange = { smbUsername = it; smbValid = null; smbStatusMessage = null },
                )
                SmbField(
                    label = "Password (optional)",
                    value = smbPassword,
                    password = true,
                    onValueChange = { smbPassword = it; smbValid = null; smbStatusMessage = null },
                )

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Ochre)
                        .clickable(enabled = !smbTesting) {
                            settings.smbHost = smbHost
                            settings.smbShare = smbShare
                            settings.smbUsername = smbUsername
                            settings.smbPassword = smbPassword
                            smbTesting = true
                            smbValid = null
                            smbStatusMessage = "Testing connection…"
                            scope.launch {
                                val res = withContext(Dispatchers.IO) {
                                    try { Result.success(SmbClient.testConnection(settings)) }
                                    catch (error: CancellationException) { throw error }
                                    catch (error: Exception) { Result.failure(error) }
                                }
                                smbTesting = false
                                res.onSuccess {
                                    smbValid = true
                                    smbStatusMessage = "✓ Connection valid — saved"
                                }.onFailure { err ->
                                    smbValid = false
                                    smbStatusMessage = "✕ ${SmbErrors.message(err)}"
                                }
                            }
                        }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (smbTesting) {
                        CircularProgressIndicator(
                            color = Ink,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(
                        if (smbTesting) "TESTING…" else "SAVE & TEST CONNECTION",
                        fontFamily = Anton,
                        fontSize = 14.sp,
                        letterSpacing = 0.5.sp,
                        color = Ink,
                    )
                }

                smbStatusMessage?.let { status ->
                    Text(
                        status,
                        fontFamily = Archivo,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = when (smbValid) {
                            true -> Ochre
                            false -> Crimson
                            null -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            Spacer(Modifier.size(32.dp))

            if (repository != null) {
                // ---- Backup & Restore ----------------------------------------------------
                SectionLabel("BACKUP & RESTORE")
                Spacer(Modifier.size(10.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Export or restore reading progress, reading lists, and app preferences. Secrets (such as SMB passwords) and internal file paths are strictly excluded.",
                        fontFamily = Archivo,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (isExporting) MaterialTheme.colorScheme.surface else Ochre)
                                .clickable(enabled = !isExporting && !isImporting) {
                                    val fileName = "leaf_backup_${SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())}.json"
                                    exportLauncher.launch(fileName)
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (isExporting) "EXPORTING…" else "EXPORT BACKUP",
                                fontFamily = Anton,
                                fontSize = 13.sp,
                                letterSpacing = 0.5.sp,
                                color = if (isExporting) MaterialTheme.colorScheme.onSurfaceVariant else Ink,
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.5.dp, Ochre, RoundedCornerShape(4.dp))
                                .clickable(enabled = !isExporting && !isImporting) {
                                    importLauncher.launch(arrayOf("application/json", "*/*"))
                                }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (isImporting) "READING…" else "RESTORE…",
                                fontFamily = Anton,
                                fontSize = 13.sp,
                                letterSpacing = 0.5.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
                Spacer(Modifier.size(32.dp))
            }

            // ---- About ---------------------------------------------------------------
            SectionLabel("ABOUT")
            Spacer(Modifier.size(16.dp))
            LeafWordmark()
            Spacer(Modifier.size(12.dp))
            OchreBadge("VERSION $version")
            Spacer(Modifier.size(14.dp))
            Text(
                "A comic reader for Android",
                fontFamily = Archivo,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.size(14.dp))
            Text(
                "Leaf is an offline comic reader with on-device ML panel detection, interactive 3D page curl, network SMB drive support, and multi-format archives.",
                fontFamily = Archivo,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.size(14.dp))
            Text(
                "Source: github.com/bclark/leaf-reader",
                fontFamily = Archivo,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.clickable(onClick = openSource).padding(top = 2.dp),
            )
            Spacer(Modifier.size(8.dp))
            Text(
                "Forked with appreciation from Chika by Chakra.",
                fontFamily = Archivo,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(4.dp))
            Text(
                "Upstream: github.com/batunii/chika",
                fontFamily = Archivo,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = openUpstream).padding(top = 2.dp),
            )

            Spacer(Modifier.size(18.dp))
            LinkRow("Privacy policy — Leaf collects no data.") {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)))
            }
            Spacer(Modifier.size(8.dp))
            LinkRow("Open-source licenses & notices.") {
                showLicensesDialog = true
            }

            Spacer(Modifier.size(24.dp))

            // ---- Support Leaf --------------------------------------------------------
            SectionLabel(stringResource(R.string.support_leaf_title))
            Spacer(Modifier.size(10.dp))
            Text(
                stringResource(R.string.support_leaf_description),
                fontFamily = Archivo,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.size(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .comicShadow(offset = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Crimson)
                    .clickable(
                        onClickLabel = stringResource(R.string.support_leaf_kofi_cd),
                        onClick = { openKofi() },
                    )
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = Cream, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(10.dp))
                Text(
                    stringResource(R.string.support_leaf_kofi_button).uppercase(),
                    fontFamily = Anton,
                    fontSize = 15.sp,
                    letterSpacing = 0.5.sp,
                    color = Cream,
                )
            }
            Spacer(Modifier.size(6.dp))
            Text(
                "ko-fi.com/bryanclark",
                fontFamily = Archivo,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier
                    .clickable(
                        onClickLabel = stringResource(R.string.support_leaf_kofi_cd),
                        onClick = { openKofi() },
                    )
                    .padding(top = 2.dp),
            )

            Spacer(Modifier.size(24.dp))

            // ---- Support Upstream ----------------------------------------------------
            Text(
                "Support the upstream creator of Chika:",
                fontFamily = Archivo,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.size(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .comicShadow(offset = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.5.dp, Ochre, RoundedCornerShape(4.dp))
                    .clickable(onClick = openUpstream)
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(10.dp))
                Text(
                    "SUPPORT ORIGINAL PROJECT",
                    fontFamily = Anton,
                    fontSize = 15.sp,
                    letterSpacing = 0.5.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            Spacer(Modifier.size(6.dp))
            Text(
                "github.com/batunii/chika",
                fontFamily = Archivo,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.clickable(onClick = openUpstream).padding(top = 2.dp),
            )
        }
    }

    if (showLicensesDialog) {
        val licensesText = remember {
            runCatching {
                context.assets.open("THIRD_PARTY_NOTICES.md").bufferedReader().use { it.readText() }
            }.getOrDefault("Failed to load third-party notices.")
        }
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = {
                Text(
                    text = "THIRD-PARTY NOTICES",
                    fontFamily = Anton,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = licensesText,
                        fontFamily = Archivo,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showLicensesDialog = false
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LICENSES_URL)))
                    }
                }) {
                    Text("VIEW ON GITHUB", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLicensesDialog = false }) {
                    Text("CLOSE", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }

    restorePreview?.let { preview ->
        AlertDialog(
            onDismissRequest = {
                restorePreview = null
                pendingBackupJson = null
            },
            title = {
                Text(
                    text = "RESTORE BACKUP (v${preview.version})",
                    fontFamily = Anton,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Exported: ${SimpleDateFormat("MMM d, yyyy HH:mm", Locale.getDefault()).format(Date(preview.exportedAt))} (Leaf v${preview.appVersion})",
                        fontFamily = Archivo,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "• ${preview.totalComicsInBackup} comics in backup (${preview.matchingComicsCount} match existing library)\n" +
                                "• ${preview.readingListsCount} reading lists\n" +
                                "• App settings: ${if (preview.hasSettings) "Included" else "None"}",
                        fontFamily = Archivo,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        text = "Progress Conflict Strategy:",
                        fontFamily = Archivo,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Column(
                        Modifier.selectableGroup(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(
                            BackupConflictStrategy.MOST_RECENT to "Most Recent (Recommended: updates if backup is newer)",
                            BackupConflictStrategy.OVERWRITE to "Overwrite (Always replace local progress with backup)",
                            BackupConflictStrategy.KEEP_LOCAL to "Keep Local (Retain local progress; restore lists & settings)",
                        ).forEach { (strat, label) ->
                            val isSelected = selectedStrategy == strat
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                                    .selectable(
                                        selected = isSelected,
                                        onClick = { selectedStrategy = strat },
                                    )
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, if (isSelected) Ochre else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape)
                                        .background(if (isSelected) Ochre else Color.Transparent)
                                )
                                Spacer(Modifier.size(8.dp))
                                Text(
                                    text = label,
                                    fontFamily = Archivo,
                                    fontSize = 12.sp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val json = pendingBackupJson
                        if (json != null && repository != null) {
                            scope.launch {
                                val result = repository.applyBackup(json, selectedStrategy)
                                result.onSuccess { count ->
                                    Toast.makeText(context, "✓ Restored progress for $count comic(s)", Toast.LENGTH_SHORT).show()
                                }.onFailure { err ->
                                    Toast.makeText(context, "✕ Restore failed: ${err.message}", Toast.LENGTH_LONG).show()
                                }
                                restorePreview = null
                                pendingBackupJson = null
                            }
                        }
                    }
                ) {
                    Text("APPLY RESTORE", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    restorePreview = null
                    pendingBackupJson = null
                }) {
                    Text("CANCEL", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}
}

@Composable
private fun <T> AppearanceChoice(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, fontFamily = Archivo, fontWeight = FontWeight.Bold,
            fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (value, label) ->
                val active = value == selected
                Box(
                    Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(4.dp))
                        .background(if (active) LeafPrimary else MaterialTheme.colorScheme.surface)
                        .selectable(selected = active, role = androidx.compose.ui.semantics.Role.RadioButton,
                            onClick = { onSelect(value) })
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, fontFamily = Archivo, fontWeight = FontWeight.SemiBold, fontSize = 13.sp,
                        color = if (active) Color.White else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    Text(
        text,
        fontFamily = Archivo,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 2.dp),
    )
}

@Composable
private fun SmbField(
    label: String,
    value: String,
    password: Boolean = false,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontFamily = Archivo, fontSize = 11.sp) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = MaterialTheme.colorScheme.onSurface,
            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
            focusedBorderColor = MaterialTheme.colorScheme.tertiary,
            unfocusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            focusedLabelColor = MaterialTheme.colorScheme.tertiary,
            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            cursorColor = MaterialTheme.colorScheme.tertiary,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontFamily = Archivo,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 9.sp,
        letterSpacing = 2.5.sp,
        color = MaterialTheme.colorScheme.tertiary,
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onCheckedChange(!checked) }
            .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                title,
                fontFamily = Archivo,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                subtitle,
                fontFamily = Archivo,
                fontWeight = FontWeight.Medium,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ink,
                checkedTrackColor = MaterialTheme.colorScheme.tertiary,
                checkedBorderColor = MaterialTheme.colorScheme.tertiary,
                uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                uncheckedTrackColor = Ink,
                uncheckedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        )
    }
}


@Composable
private fun LayoutChoice(title: String, spread: Boolean, onChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, fontFamily = Archivo, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surface).padding(2.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            for (option in listOf(false, true)) {
                Box(
                    Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(3.dp))
                        .background(if (spread == option) Ochre else Color.Transparent)
                        .selectable(selected = spread == option, role = androidx.compose.ui.semantics.Role.RadioButton,
                            onClick = { onChange(option) })
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (option) "Two-page spread" else "Single page",
                        fontFamily = Archivo, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                        color = if (spread == option) Ink else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
