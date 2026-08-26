package com.jamal2367.uvsmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.jamal2367.uvsmobile.ui.theme.BadgeHdr10
import com.jamal2367.uvsmobile.ui.theme.BadgeHdr10Plus
import com.jamal2367.uvsmobile.ui.theme.BadgeSdr

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
                shape = RoundedCornerShape(6.dp),
            )
            .then(
                if (outlined) {
                    Modifier.border(1.dp, content.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 6.dp, vertical = 3.dp),
    )
}

/**
 * How a title is graded, in one chip.
 *
 * Dolby Vision names its enhancement layer, because FEL and MEL are the whole
 * point of the distinction; anything else shows the format the scanner
 * determined, and a title it could not determine shows nothing at all rather
 * than a confident "SDR".
 */
@Composable
fun HdrBadge(entry: LibraryEntry, modifier: Modifier = Modifier) {
    val label = hdrLabel(entry) ?: return
    val (container, content) = hdrColors(entry)
    MetaChip(text = label, container = container, content = content, modifier = modifier)
}

/** The label an HDR badge carries, or null when the grade is unknown. */
fun hdrLabel(entry: LibraryEntry): String? {
    val format = entry.hdrFormat?.trim().orEmpty()
    if (format.isEmpty() || format.equals("Unknown", ignoreCase = true)) return null

    if (format.contains("Dolby Vision", ignoreCase = true)) {
        val layer = entry.elType?.trim().orEmpty()
        return if (layer.isNotEmpty()) "DV $layer" else "DV"
    }
    return format
}

private fun hdrColors(entry: LibraryEntry): Pair<Color, Color> {
    val format = entry.hdrFormat?.lowercase().orEmpty()
    val detail = entry.hdrDetail?.lowercase().orEmpty()
    return when {
        format.contains("dolby vision") -> BadgeDolbyVision
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
