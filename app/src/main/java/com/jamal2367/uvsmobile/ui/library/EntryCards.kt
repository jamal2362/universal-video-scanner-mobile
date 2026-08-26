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
import com.jamal2367.uvsmobile.ui.LocalPosterServer
import com.jamal2367.uvsmobile.ui.components.HdrBadge
import com.jamal2367.uvsmobile.ui.components.MetaChip
import com.jamal2367.uvsmobile.ui.components.PosterImage
import com.jamal2367.uvsmobile.util.Formatters

/** One title in the grid: the cover, what it is called, and how it is graded. */
@Composable
fun EntryGridCard(
    entry: LibraryEntry,
    posterWidth: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box {
            PosterImage(
                posterUrl = entry.posterUrl,
                server = LocalPosterServer.current,
                width = posterWidth,
                contentDescription = entry.displayTitle,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp)),
            )
            entry.top250Rank?.let { rank ->
                MetaChip(
                    text = "#$rank",
                    container = MaterialTheme.colorScheme.tertiaryContainer,
                    content = MaterialTheme.colorScheme.onTertiaryContainer,
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

        Text(
            text = entry.displayTitle,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )

        Text(
            text = listOfNotNull(
                entry.tmdbYear?.takeIf { it.isNotBlank() },
                entry.resolutionClass?.takeIf { it.isNotBlank() && it != "Unknown" },
                Formatters.ratingOutOfTen(entry.imdbRating ?: entry.tmdbRating),
            ).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One title in the list: the same cover, smaller, with room for the technical
 * detail a grid has no space for.
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
        Row(modifier = Modifier.padding(10.dp)) {
            PosterImage(
                posterUrl = entry.posterUrl,
                server = LocalPosterServer.current,
                width = posterWidth,
                contentDescription = entry.displayTitle,
                modifier = Modifier
                    .width(60.dp)
                    .height(90.dp)
                    .clip(RoundedCornerShape(8.dp)),
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
                        Formatters.duration(entry.duration),
                        Formatters.fileSize(entry.fileSize),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HdrBadge(entry)
                    entry.videoCodec?.takeIf { it.isNotBlank() }?.let {
                        MetaChip(text = it, outlined = true)
                    }
                    entry.audioCodec?.takeIf { it.isNotBlank() }?.let {
                        MetaChip(text = it, outlined = true)
                    }
                }
            }
        }
    }
}
