package com.chakra.comicreader.ui.menu

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chakra.comicreader.data.settings.AppSettings
import com.chakra.comicreader.data.smb.SmbClient
import androidx.compose.material3.Surface
import com.chakra.comicreader.ui.brand.LeafWordmark
import com.chakra.comicreader.ui.brand.OchreBadge
import com.chakra.comicreader.ui.brand.comicShadow
import com.chakra.comicreader.ui.brand.halftone
import com.chakra.comicreader.ui.theme.Anton
import com.chakra.comicreader.ui.theme.Archivo
import com.chakra.comicreader.ui.theme.Cream
import com.chakra.comicreader.ui.theme.CreamMuted
import com.chakra.comicreader.ui.theme.Crimson
import com.chakra.comicreader.ui.theme.Ink
import com.chakra.comicreader.ui.theme.InkSoft
import com.chakra.comicreader.ui.theme.LeafPrimary
import com.chakra.comicreader.ui.theme.LeafSecondary
import com.chakra.comicreader.ui.theme.Ochre
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val DONATION_URL = "https://github.com/batunii/chika"
private const val PRIVACY_URL = "https://github.com/batunii/chika/blob/main/PRIVACY.md"
private const val LICENSES_URL = "https://github.com/batunii/chika/blob/main/THIRD_PARTY_NOTICES.md"

@Composable
fun MenuScreen(
    amoledTheme: Boolean,
    onSetAmoledTheme: (Boolean) -> Unit,
    pageSlideEnabled: Boolean,
    onSetPageSlideEnabled: (Boolean) -> Unit,
    settings: AppSettings,
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

    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "—"
    }
    val openDonation = {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DONATION_URL)))
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
            SectionLabel("THEME")
            Spacer(Modifier.size(10.dp))
            ToggleRow(
                title = "AMOLED Black",
                subtitle = "True-black background — easy on OLED screens and battery.",
                checked = amoledTheme,
                onCheckedChange = onSetAmoledTheme,
            )

            Spacer(Modifier.size(32.dp))

            // ---- Reading -------------------------------------------------------------
            SectionLabel("READING")
            Spacer(Modifier.size(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(InkSoft)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Reading Mode",
                    fontFamily = Archivo,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Cream,
                )
                Text(
                    if (pageSlideEnabled) {
                        "Turn full pages with a smooth slide transition."
                    } else {
                        "Guided reading that automatically zooms and steps through each detected panel."
                    },
                    fontFamily = Archivo,
                    fontSize = 12.sp,
                    color = CreamMuted,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Ink)
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
                            color = if (pageSlideEnabled) Ink else CreamMuted,
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
                            color = if (!pageSlideEnabled) Ink else CreamMuted,
                        )
                    }
                }
            }

            Spacer(Modifier.size(32.dp))

            // ---- Library -------------------------------------------------------------
            SectionLabel("LIBRARY")
            Spacer(Modifier.size(10.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(InkSoft)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "Grid Columns",
                    fontFamily = Archivo,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Cream,
                )
                Text(
                    "Choose how many comic covers appear per row on the library screen.",
                    fontFamily = Archivo,
                    fontSize = 12.sp,
                    color = CreamMuted,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Ink)
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
                                color = if (isSelected) Ink else CreamMuted,
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
                    .background(InkSoft)
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
                                    runCatching { SmbClient.testConnection(settings) }
                                }
                                smbTesting = false
                                res.onSuccess {
                                    smbValid = true
                                    smbStatusMessage = "✓ Connection valid — saved"
                                }.onFailure { err ->
                                    smbValid = false
                                    smbStatusMessage = "✕ Connection failed: ${err.message ?: "Unable to connect"}"
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
                            null -> CreamMuted
                        },
                    )
                }
            }

            Spacer(Modifier.size(32.dp))

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
                color = Cream,
            )
            Spacer(Modifier.size(14.dp))
            Text(
                "Leaf is an offline comic reader with on-device ML panel detection, interactive 3D page curl, network SMB drive support, and multi-format archives.",
                fontFamily = Archivo,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = Cream,
            )
            Spacer(Modifier.size(14.dp))
            Text(
                "Forked with appreciation from Chika by Chakra.",
                fontFamily = Archivo,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                color = CreamMuted,
            )
            Spacer(Modifier.size(6.dp))
            Text(
                "Upstream: github.com/batunii/chika",
                fontFamily = Archivo,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = Ochre,
                modifier = Modifier.clickable {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/batunii/chika")))
                }.padding(top = 2.dp),
            )

            Spacer(Modifier.size(18.dp))
            LinkRow("Privacy policy — Leaf collects no data.") {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_URL)))
            }
            Spacer(Modifier.size(8.dp))
            LinkRow("Open-source licenses & notices.") {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(LICENSES_URL)))
            }

            Spacer(Modifier.size(24.dp))

            // ---- Support -------------------------------------------------------------
            Text(
                "Support the upstream creator of Chika:",
                fontFamily = Archivo,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = CreamMuted,
            )
            Spacer(Modifier.size(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .comicShadow(offset = 4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Crimson)
                    .clickable(onClick = openDonation)
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = Cream, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(10.dp))
                Text(
                    "SUPPORT ORIGINAL PROJECT",
                    fontFamily = Anton,
                    fontSize = 15.sp,
                    letterSpacing = 0.5.sp,
                    color = Cream,
                )
            }
            Spacer(Modifier.size(6.dp))
            Text(
                "github.com/batunii/chika",
                fontFamily = Archivo,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = Ochre,
                modifier = Modifier.clickable(onClick = openDonation).padding(top = 2.dp),
            )
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
        color = Ochre,
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
            focusedTextColor = Cream,
            unfocusedTextColor = Cream,
            focusedBorderColor = Ochre,
            unfocusedBorderColor = CreamMuted,
            focusedLabelColor = Ochre,
            unfocusedLabelColor = CreamMuted,
            cursorColor = Ochre,
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
        color = Ochre,
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
            .background(InkSoft)
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
                color = Cream,
            )
            Text(
                subtitle,
                fontFamily = Archivo,
                fontWeight = FontWeight.Medium,
                fontSize = 10.5.sp,
                lineHeight = 14.sp,
                color = CreamMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Ink,
                checkedTrackColor = Ochre,
                checkedBorderColor = Ochre,
                uncheckedThumbColor = CreamMuted,
                uncheckedTrackColor = Ink,
                uncheckedBorderColor = CreamMuted,
            ),
        )
    }
}
