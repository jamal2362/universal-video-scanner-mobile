package com.jamal2367.uvsmobile.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette the app falls back to when the wallpaper cannot supply one.
 *
 * Built around the deep blue of a darkened room and the cyan of a scanning
 * beam, which is what the scanner's own interface reads like.
 */

// --- light ---
val LightPrimary = Color(0xFF00658E)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFC7E7FF)
val LightOnPrimaryContainer = Color(0xFF001E2E)

val LightSecondary = Color(0xFF4E616D)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFD1E5F4)
val LightOnSecondaryContainer = Color(0xFF091E28)

val LightTertiary = Color(0xFF61587B)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFE7DEFF)
val LightOnTertiaryContainer = Color(0xFF1D1735)

val LightError = Color(0xFFBA1A1A)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFFDAD6)
val LightOnErrorContainer = Color(0xFF410002)

val LightBackground = Color(0xFFFBFCFE)
val LightOnBackground = Color(0xFF191C1E)
val LightSurface = Color(0xFFFBFCFE)
val LightOnSurface = Color(0xFF191C1E)
val LightSurfaceVariant = Color(0xFFDDE3EA)
val LightOnSurfaceVariant = Color(0xFF41484D)
val LightOutline = Color(0xFF71787E)
val LightOutlineVariant = Color(0xFFC1C7CE)

// --- dark ---
val DarkPrimary = Color(0xFF84CFFF)
val DarkOnPrimary = Color(0xFF00344C)
val DarkPrimaryContainer = Color(0xFF004C6C)
val DarkOnPrimaryContainer = Color(0xFFC7E7FF)

val DarkSecondary = Color(0xFFB5C9D7)
val DarkOnSecondary = Color(0xFF20333E)
val DarkSecondaryContainer = Color(0xFF364955)
val DarkOnSecondaryContainer = Color(0xFFD1E5F4)

val DarkTertiary = Color(0xFFCBC0E8)
val DarkOnTertiary = Color(0xFF322B4B)
val DarkTertiaryContainer = Color(0xFF494162)
val DarkOnTertiaryContainer = Color(0xFFE7DEFF)

val DarkError = Color(0xFFFFB4AB)
val DarkOnError = Color(0xFF690005)
val DarkErrorContainer = Color(0xFF93000A)
val DarkOnErrorContainer = Color(0xFFFFDAD6)

val DarkBackground = Color(0xFF101418)
val DarkOnBackground = Color(0xFFE1E2E5)
val DarkSurface = Color(0xFF101418)
val DarkOnSurface = Color(0xFFE1E2E5)
val DarkSurfaceVariant = Color(0xFF41484D)
val DarkOnSurfaceVariant = Color(0xFFC1C7CE)
val DarkOutline = Color(0xFF8B9198)
val DarkOutlineVariant = Color(0xFF41484D)

/**
 * The accents the badges use.
 *
 * Ordered the way the scanner ranks a grade, so the richest formats are the
 * ones that stand out most on a poster.
 */
val BadgeDolbyVision = Color(0xFF1B1035) to Color(0xFFD9C7FF)
val BadgeHdr10Plus = Color(0xFF2B1B00) to Color(0xFFFFD8A8)
val BadgeHdr10 = Color(0xFF00201A) to Color(0xFF9FF2DE)
val BadgeSdr = Color(0xFF1E2226) to Color(0xFFC5CBD2)
