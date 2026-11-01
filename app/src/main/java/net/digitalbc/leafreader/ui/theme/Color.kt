package net.digitalbc.leafreader.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Leaf brand palette.
 */
// Brand Primary & Secondary
val LeafPrimary = Color(0xFF1C3A27)       // Forest Primary
val LeafPrimaryDark = Color(0xFF122519)   // Deep Forest
val LeafSecondary = Color(0xFF4A7C59)     // Sage Secondary
val LeafAccent = Color(0xFFD4AF37)        // Accent Gold

// Interface and Support
val ComicRose = Color(0xFF935B6D)         // Warm accent / label
val PaperWhite = Color(0xFFF9F9FB)        // Light BG / Text
val DeepInk = Color(0xFF14191C)           // Dark Mode BG
val DeepInkSurface = Color(0xFF1E2529)    // Cards / bars in Dark Mode

// Secondary / metadata text on dark grounds
val PaperWhiteMuted = Color(0x8CF9F9FB)

// Compatibility aliases for existing design components
val Ink = DeepInk
val InkSoft = DeepInkSurface
val Crimson = LeafPrimary
val CrimsonBright = LeafSecondary
val Maroon = LeafPrimaryDark
val Cream = PaperWhite
val Ochre = LeafAccent
val CreamMuted = PaperWhiteMuted
