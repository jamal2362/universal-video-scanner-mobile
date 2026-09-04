package com.jamal2367.uvsmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jamal2367.uvsmobile.data.model.LibraryEntry
import com.jamal2367.uvsmobile.ui.theme.BadgeAudioLossless
import com.jamal2367.uvsmobile.ui.theme.BadgeAudioLossy
import com.jamal2367.uvsmobile.ui.theme.BadgeAudioObject
import com.jamal2367.uvsmobile.ui.theme.BadgeDolbyVision
import com.jamal2367.uvsmobile.ui.theme.BadgeDolbyVisionFel
import com.jamal2367.uvsmobile.ui.theme.BadgeDolbyVisionMel
import com.jamal2367.uvsmobile.ui.theme.BadgeHdr10
import com.jamal2367.uvsmobile.ui.theme.BadgeHdr10Plus
import com.jamal2367.uvsmobile.ui.theme.BadgeRating
import com.jamal2367.uvsmobile.ui.theme.BadgeSdr
import com.jamal2367.uvsmobile.ui.theme.PillShape
import com.jamal2367.uvsmobile.util.Formatters
import com.jamal2367.uvsmobile.util.HdrGroups

/**
 * A small, quiet chip - the kind that sits under a title without shouting.
 *
 * [large] is for the one place where it has to shout a little: a chip lying on
 * a poster is read as a mark on a picture rather than as a word in a line, and
 * at the size that suits a line of text it is a smudge on the artwork.
 */
@Composable
fun MetaChip(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    outlined: Boolean = false,
    large: Boolean = false,
) {
    Text(
        text = text,
        style = if (large) {
            MaterialTheme.typography.labelMedium
        } else {
            MaterialTheme.typography.labelSmall
        },
        color = content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .background(
                color = if (outlined) Color.Transparent else container,
                shape = PillShape,
            )
            .then(
                if (outlined) {
                    Modifier.border(1.dp, content.copy(alpha = 0.4f), PillShape)
                } else {
                    Modifier
                }
            )
            .padding(
                horizontal = if (large) 9.dp else 8.dp,
                vertical = if (large) 4.dp else 3.dp,
            ),
    )
}

/**
 * How a title is graded, in one chip.
 *
 * The badge says the one thing that sets this title apart from the next -
 * FEL, MEL, 8.1, 5.0, HDR10, HDR10+, SDR - and no more. "Dolby Vision" in
 * front of a layer is a word every Dolby Vision title on the screen carries,
 * which is a lot of a small chip spent on nothing; the colour already says
 * which family it belongs to.
 */
@Composable
fun HdrBadge(entry: LibraryEntry, modifier: Modifier = Modifier, large: Boolean = false) {
    val label = hdrLabel(entry) ?: return
    val (container, content) = hdrColors(entry)
    MetaChip(
        text = label,
        container = container,
        content = content,
        large = large,
        modifier = modifier,
    )
}

/**
 * The label an HDR badge carries, or null when the grade is unknown.
 *
 * Dolby Vision is named by what distinguishes one from another: the
 * enhancement layer where there is one - a profile 7 disc is a FEL or a MEL
 * before it is anything else - and otherwise the profile the scanner read,
 * 8.1 or 5.0. Anything else is the format itself, which is already the short
 * name everyone uses for it.
 */
fun hdrLabel(entry: LibraryEntry): String? {
    val format = entry.hdrFormat?.trim().orEmpty()
    if (format.isEmpty() || format.equals("Unknown", ignoreCase = true)) return null

    val detail = entry.hdrDetail?.trim().orEmpty()
    if (format.contains("Dolby Vision", ignoreCase = true)) {
        entry.elType?.trim()?.takeIf { it.isNotEmpty() }?.let { return it.uppercase() }
        return HdrGroups.dolbyVisionProfile(detail) ?: "DV"
    }

    // The plus is the whole distinction, and the scanner does not always
    // carry it up into the format: a title graded HDR10+ is filed under
    // "HDR10" with the detail line saying which it really is.
    if (detail.contains("HDR10+", ignoreCase = true)) return "HDR10+"
    return format
}

/**
 * The ground and face a grade is drawn in.
 *
 * Dolby Vision splits by enhancement layer rather than sharing one violet,
 * because that is the distinction anyone scrolling is actually looking for -
 * a FEL and a MEL are told apart at a glance, and the profiles that carry
 * neither keep the violet.
 */
private fun hdrColors(entry: LibraryEntry): Pair<Color, Color> {
    val format = entry.hdrFormat?.lowercase().orEmpty()
    val detail = entry.hdrDetail?.lowercase().orEmpty()
    val layer = entry.elType?.trim().orEmpty()
    return when {
        format.contains("dolby vision") -> when {
            layer.equals("FEL", ignoreCase = true) -> BadgeDolbyVisionFel
            layer.equals("MEL", ignoreCase = true) -> BadgeDolbyVisionMel
            else -> BadgeDolbyVision
        }
        format.contains("hdr10+") || detail.contains("hdr10+") -> BadgeHdr10Plus
        format.contains("hdr") || format.contains("hlg") -> BadgeHdr10
        else -> BadgeSdr
    }
}

/**
 * How a title is rated, in one chip.
 *
 * IMDb where there is one, TMDB where there is not - the same order the line
 * under a cover reads them in, and the star says which of the numbers on a
 * poster this is.
 */
@Composable
fun RatingBadge(entry: LibraryEntry, modifier: Modifier = Modifier, large: Boolean = false) {
    val label = Formatters.ratingStarred(entry.imdbRating ?: entry.tmdbRating) ?: return
    val (container, content) = BadgeRating
    MetaChip(
        text = label,
        container = container,
        content = content,
        large = large,
        modifier = modifier,
    )
}

/**
 * The track a title carries, in one chip.
 *
 * Coloured like the grade above it, and for the same reason: "Dolby TrueHD
 * 7.1 (Atmos)" and "Dolby Digital 5.1" begin with the same word and are not
 * the same evening, and a colour says which before the line has been read.
 */
@Composable
fun AudioBadge(entry: LibraryEntry, modifier: Modifier = Modifier) {
    val codec = entry.audioCodec?.trim()?.takeIf { it.isNotEmpty() } ?: return
    val (container, content) = audioColors(codec)
    MetaChip(text = codec, container = container, content = content, modifier = modifier)
}

/**
 * The ground and face a track is drawn in.
 *
 * By what the track can do, not by whose name is on it: a mix placed in a room
 * first, then whether it survives the disc intact, then everything else. The
 * order matters - a Dolby TrueHD track with Atmos in it is both, and the room
 * is the thing worth seeing first.
 */
fun audioColors(codec: String): Pair<Color, Color> {
    val name = codec.lowercase()
    return when {
        name.contains("atmos") || name.contains("dts:x") || name.contains("dts-x") ->
            BadgeAudioObject

        name.contains("truehd") ||
            name.contains("dts-hd ma") ||
            name.contains("dts-hd master") ||
            name.contains("flac") ||
            name.contains("pcm") ||
            name.contains("alac") -> BadgeAudioLossless

        else -> BadgeAudioLossy
    }
}

/**
 * The chips under a poster: grade, frame, codec, track.
 *
 * A flow rather than a row, because a track spells itself out - "Dolby TrueHD
 * 7.1 (Atmos)" is wider than the three chips before it put together - and the
 * last chip on a line is not the one worth dropping off the edge of a phone.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryChipRow(
    entry: LibraryEntry,
    modifier: Modifier = Modifier,
    showCodecs: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    FlowRow(
        modifier = modifier.padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        HdrBadge(entry)
        entry.resolutionClass?.takeIf { it.isNotBlank() && it != "Unknown" }?.let {
            MetaChip(text = it, outlined = true)
        }
        if (showCodecs) {
            entry.videoCodec?.takeIf { it.isNotBlank() }?.let {
                MetaChip(text = it, outlined = true)
            }
        }
        AudioBadge(entry)
    }
}
