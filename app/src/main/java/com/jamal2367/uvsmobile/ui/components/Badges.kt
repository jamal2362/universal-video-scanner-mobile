package com.jamal2367.uvsmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jamal2367.uvsmobile.data.model.LibraryEntry
import com.jamal2367.uvsmobile.ui.theme.BadgeDolbyVision
import com.jamal2367.uvsmobile.ui.theme.BadgeDolbyVisionFel
import com.jamal2367.uvsmobile.ui.theme.BadgeDolbyVisionMel
import com.jamal2367.uvsmobile.ui.theme.BadgeHdr10
import com.jamal2367.uvsmobile.ui.theme.BadgeHdr10Plus
import com.jamal2367.uvsmobile.ui.theme.BadgeSdr
import com.jamal2367.uvsmobile.ui.theme.PillShape

/** A small, quiet chip - the kind that sits under a title without shouting. */
@Composable
fun MetaChip(
    text: String,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    outlined: Boolean = false,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
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
            .padding(horizontal = 8.dp, vertical = 3.dp),
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
fun HdrBadge(entry: LibraryEntry, modifier: Modifier = Modifier) {
    val label = hdrLabel(entry) ?: return
    val (container, content) = hdrColors(entry)
    MetaChip(text = label, container = container, content = content, modifier = modifier)
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
        return dolbyVisionProfile(detail) ?: "DV"
    }

    // The plus is the whole distinction, and the scanner does not always
    // carry it up into the format: a title graded HDR10+ is filed under
    // "HDR10" with the detail line saying which it really is.
    if (detail.contains("HDR10+", ignoreCase = true)) return "HDR10+"
    return format
}

/**
 * The profile out of a detail line like "Dolby Vision Profile 8.1".
 *
 * Written with the decimal a profile is always spoken with - profile 5 reads
 * "5.0" - so a column of badges lines up rather than mixing "5" with "8.1".
 */
private fun dolbyVisionProfile(detail: String): String? {
    val number = PROFILE_PATTERN.find(detail)?.groupValues?.get(1) ?: return null
    return if (number.contains('.')) number else "$number.0"
}

private val PROFILE_PATTERN = Regex("""Profile\s+(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)

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

/** The chips under a poster: grade, frame, codec, track - as many as fit. */
@Composable
fun EntryChipRow(
    entry: LibraryEntry,
    modifier: Modifier = Modifier,
    showCodecs: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    Row(
        modifier = modifier.padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
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
    }
}
