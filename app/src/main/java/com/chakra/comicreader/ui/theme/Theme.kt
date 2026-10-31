package com.chakra.comicreader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
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

@Composable
fun ComicReaderTheme(amoled: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (amoled) LeafAmoledColorScheme else LeafColorScheme,
        typography = LeafTypography,
        content = content,
    )
}
