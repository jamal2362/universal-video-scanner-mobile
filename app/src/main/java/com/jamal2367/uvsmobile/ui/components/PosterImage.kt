package com.jamal2367.uvsmobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.jamal2367.uvsmobile.data.prefs.ServerConfig
import com.jamal2367.uvsmobile.util.PosterUrls

/**
 * A title's cover.
 *
 * The server keeps four widths of every cached poster and makes the one asked
 * for on first use, so a grid of covers costs a kilobyte or two apiece instead
 * of the full-size image. A title without a usable poster falls back to a plain
 * placeholder rather than an empty hole.
 */
@Composable
fun PosterImage(
    posterUrl: String?,
    server: ServerConfig?,
    width: Int,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val url = PosterUrls.forEntry(posterUrl, server, width)
    val context = LocalContext.current

    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url == null) {
            PosterPlaceholder()
            return@Box
        }

        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context)
                .data(url)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize(),
            loading = { PosterPlaceholder(dimmed = true) },
            error = { PosterPlaceholder() },
        )
    }
}

@Composable
private fun PosterPlaceholder(dimmed: Boolean = false) {
    Icon(
        imageVector = Icons.Outlined.Movie,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (dimmed) 0.25f else 0.45f),
        modifier = Modifier.size(32.dp),
    )
}
