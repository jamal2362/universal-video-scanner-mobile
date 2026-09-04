package com.jamal2367.uvsmobile.ui.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jamal2367.uvsmobile.data.model.LibraryEntry
import com.jamal2367.uvsmobile.data.prefs.AppSettings
import com.jamal2367.uvsmobile.ui.components.AudioBadge
import com.jamal2367.uvsmobile.ui.components.HdrBadge
import com.jamal2367.uvsmobile.ui.components.MetaChip
import com.jamal2367.uvsmobile.ui.components.PosterImage
import com.jamal2367.uvsmobile.util.Artwork
import com.jamal2367.uvsmobile.util.Formatters

/**
 * One title in the grid: the cover, what it is called, and how it is graded.
 *
 * [landscape] swaps the upright cover for the 16:9 backdrop, which is what the
 * one-per-row grid asks for: an upright cover the full width of a phone stands
 * taller than the screen, so a row would hold nothing else, while the backdrop
 * at that width is the header image it was made to be.
 */
@Composable
fun EntryGridCard(
    entry: LibraryEntry,
    posterWidth: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    landscape: Boolean = false,
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box {
            PosterImage(
                entry = entry,
                // A tile that fills the row is twice the width of one in a row
                // of two, so it is asked for the next copy up rather than the
                // one the settings picked for a grid of covers.
                width = if (landscape) wideTileWidth(posterWidth) else posterWidth,
                artwork = if (landscape) Artwork.LANDSCAPE else Artwork.PORTRAIT,
                contentDescription = entry.displayTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (landscape) 16f / 9f else 2f / 3f)
                    .clip(RoundedCornerShape(18.dp)),
            )
            entry.top250Rank?.let { rank ->
                MetaChip(
                    text = "#$rank",
                    container = MaterialTheme.colorScheme.primaryContainer,
                    content = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                )
            }
            HdrBadge(
                entry = entry,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
            )
        }

        // A tile the full width of the screen carries its title at the size a
        // line that wide is read at; the small label is for a cover in a row of
        // three or four.
        //
        // A narrow tile is given a third line for it: at four covers across a
        // phone a title has about a dozen characters to a line, and two lines
        // cut "Deja Vu - Wettlauf gegen die Zeit" in half. The wide tile keeps
        // two, which at that width is a title nobody has.
        Text(
            text = entry.displayTitle,
            style = if (landscape) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.labelLarge
            },
            maxLines = if (landscape) 2 else 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )

        // Four values do not fit on one line of a narrow tile, so it wraps onto
        // a second rather than ending in an ellipsis: the running time is the
        // last of them, and a line that cuts off is a line that only ever shows
        // the year. The running time is shortened for the same reason - "min"
        // spelled out is a third of a line spent on a word.
        Text(
            text = listOfNotNull(
                entry.tmdbYear?.takeIf { it.isNotBlank() },
                entry.resolutionClass?.takeIf { it.isNotBlank() && it != "Unknown" },
                Formatters.ratingStarred(entry.imdbRating ?: entry.tmdbRating),
                if (landscape) {
                    Formatters.duration(entry.duration)
                } else {
                    Formatters.durationCompact(entry.duration)
                },
            ).joinToString(" · "),
            style = if (landscape) {
                MaterialTheme.typography.bodyMedium
            } else {
                MaterialTheme.typography.labelSmall
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (landscape) 1 else 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The copy to ask for when one tile fills the row: one step up from the chosen
 * width, or that width itself when the server keeps nothing larger.
 */
private fun wideTileWidth(posterWidth: Int): Int =
    AppSettings.POSTER_WIDTHS.firstOrNull { it > posterWidth } ?: posterWidth

/**
 * One title in the list: the same cover, smaller, with room for the technical
 * detail a grid has no space for.
 *
 * Four lines, in the order they are read: the title, the file in one line -
 * year, frame, rating, running time, size - then how it is graded, then its
 * track. Each badge on its own line, so the column fills the height the cover
 * sets rather than leaving a band of empty card under it.
 */
@Composable
fun EntryListRow(
    entry: LibraryEntry,
    posterWidth: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PosterImage(
                entry = entry,
                width = posterWidth,
                contentDescription = entry.displayTitle,
                modifier = Modifier
                    .width(60.dp)
                    .height(90.dp)
                    .clip(RoundedCornerShape(14.dp)),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = entry.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = listOfNotNull(
                        entry.tmdbYear?.takeIf { it.isNotBlank() },
                        entry.resolution?.takeIf { it.isNotBlank() && it != "Unknown" },
                        Formatters.ratingStarred(entry.imdbRating ?: entry.tmdbRating),
                        Formatters.duration(entry.duration),
                        Formatters.fileSize(entry.fileSize),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                HdrBadge(entry)

                AudioBadge(entry)
            }
        }
    }
}
