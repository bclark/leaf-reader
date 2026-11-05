package net.digitalbc.leafreader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.isSystemInDarkTheme
import net.digitalbc.leafreader.data.settings.ThemeMode
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Leaf color scheme: Forest green primary (#1C3A27), Deep Ink dark background (#14191C),
 * Accent Gold interactive highlights (#D4AF37), and Paper White text (#F9F9FB).
 */
private val LeafColorScheme = darkColorScheme(
    primary = LeafPrimary,
    onPrimary = PaperWhite,
    primaryContainer = LeafPrimaryDark,
    onPrimaryContainer = PaperWhite,
    secondary = LeafSecondary,
    onSecondary = PaperWhite,
    secondaryContainer = DeepInkSurface,
    onSecondaryContainer = PaperWhite,
    tertiary = LeafAccent,
    onTertiary = DeepInk,
    tertiaryContainer = LeafAccent,
    onTertiaryContainer = DeepInk,
    background = DeepInk,
    onBackground = PaperWhite,
    surface = DeepInk,
    onSurface = PaperWhite,
    surfaceVariant = DeepInkSurface,
    onSurfaceVariant = PaperWhiteMuted,
    outline = LeafSecondary,
    error = ComicRose,
    onError = PaperWhite,
)

/**
 * AMOLED variant: the deep ink grounds collapse to true black so OLED pixels switch fully off,
 * while the forest greens, accent gold, and paper white carry through unchanged.
 */
private val LeafAmoledColorScheme = LeafColorScheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color(0xFF0D1113),
)

private val LeafLightColorScheme = lightColorScheme(
    primary = LeafPrimary,
    onPrimary = PaperWhite,
    primaryContainer = Color(0xFFDCE8D9),
    onPrimaryContainer = LeafPrimaryDark,
    secondary = LeafSecondary,
    onSecondary = PaperWhite,
    secondaryContainer = Color(0xFFE5EADD),
    onSecondaryContainer = LeafPrimaryDark,
    tertiary = Color(0xFF795C00),
    onTertiary = Color.White,
    tertiaryContainer = LeafAccent,
    onTertiaryContainer = DeepInk,
    background = Color(0xFFFAFAF7),
    onBackground = DeepInk,
    surface = Color(0xFFFAFAF7),
    onSurface = DeepInk,
    surfaceVariant = Color(0xFFEAEDE5),
    onSurfaceVariant = Color(0xFF4A544C),
    outline = Color(0xFF718075),
    error = Color(0xFF9E354C),
    onError = Color.White,
)

@Composable
fun ComicReaderTheme(
    amoled: Boolean = false,
    mode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = mode.usesDarkTheme(isSystemInDarkTheme())
    MaterialTheme(
        colorScheme = when {
            !dark -> LeafLightColorScheme
            amoled -> LeafAmoledColorScheme
            else -> LeafColorScheme
        },
        typography = LeafTypography,
        content = content,
    )
}
