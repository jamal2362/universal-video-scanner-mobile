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
 * A colour per grade, so a scroll through the library can be read without
 * reading a word of it: green for a full enhancement layer, amber for a
 * minimal one, violet for the Dolby Vision profiles that carry neither, blue
 * for HDR10, red for HDR10+, and a grey that stays out of the way for SDR.
 *
 * Each is a deep ground under a light face rather than a pair drawn from the
 * theme, because a badge sits on a poster: whatever the wallpaper or the time
 * of day has done to the rest of the screen, it has to hold up over a bright
 * frame and a dark one alike.
 */
val BadgeDolbyVisionFel = Color(0xFF07271A) to Color(0xFF8FEEBC)
val BadgeDolbyVisionMel = Color(0xFF2E1B00) to Color(0xFFFFC98A)
val BadgeDolbyVision = Color(0xFF1B1035) to Color(0xFFD9C7FF)
val BadgeHdr10Plus = Color(0xFF3A0808) to Color(0xFFFFB3AC)
val BadgeHdr10 = Color(0xFF07203D) to Color(0xFFA8CDFF)
val BadgeSdr = Color(0xFF1E2226) to Color(0xFFC5CBD2)

/**
 * The accents a track uses.
 *
 * Read by what the track can do rather than by whose name is on it: magenta
 * where the sound is placed in a room - Atmos, DTS:X - cyan where it is
 * lossless, and the same quiet grey as SDR for a track that is neither. Hues
 * the grades do not use, so the two lines under a title never have to be told
 * apart by reading them.
 */
val BadgeAudioObject = Color(0xFF2E0A22) to Color(0xFFFFB0DE)
val BadgeAudioLossless = Color(0xFF04262B) to Color(0xFF8FE7F5)
val BadgeAudioLossy = Color(0xFF1E2226) to Color(0xFFC5CBD2)

/**
 * The corner of a poster a rating sits in.
 *
 * The gold of the star it is written with, on the same kind of deep ground the
 * grades use: it lies on artwork, so it has to hold up over a bright frame and
 * a dark one alike. A hue none of the grades or tracks has taken, because the
 * three badges on a cover are told apart by colour before any of them is read.
 */
val BadgeRating = Color(0xFF2B2006) to Color(0xFFFFD98F)
