package com.jamal2367.uvsmobile.util

import com.jamal2367.uvsmobile.data.model.LibraryEntry
import com.jamal2367.uvsmobile.data.prefs.ServerConfig
import java.net.URLEncoder

/** Which of a title's two images is wanted. */
enum class Artwork {
    /** The upright 2:3 cover - what a grid of covers is laid out for. */
    PORTRAIT,

    /** The 16:9 backdrop - a header image, never a tile in the grid. */
    LANDSCAPE,
}

/**
 * Where a title's artwork is fetched from.
 *
 * The scanner keeps two images per entry: `poster_url` is the 16:9 backdrop the
 * web interface is built around, `portrait_url` the upright cover. Each is
 * `/poster/<name>.jpg` once the scanner managed to cache it - those are served
 * inside the API, resized to the width the layout actually needs - and the
 * remote URL when it could not, which is loaded from its own host.
 */
object PosterUrls {

    private const val CACHED_PREFIX = "/poster/"

    /**
     * The URL for one of an entry's two images, or null when it has none.
     *
     * A request for the cover falls back to the backdrop: an instance that
     * predates `portrait_url`, or a title neither source had cover art for,
     * should still show something rather than a grid of blanks. The other
     * direction has no fallback - a cover letterboxed into a 16:9 header looks
     * worse than no header at all.
     */
    fun forEntry(
        entry: LibraryEntry,
        server: ServerConfig?,
        width: Int?,
        artwork: Artwork = Artwork.PORTRAIT,
    ): String? = when (artwork) {
        Artwork.PORTRAIT ->
            imageUrl(entry.portraitUrl, server, width) ?: imageUrl(entry.posterUrl, server, width)

        Artwork.LANDSCAPE -> imageUrl(entry.posterUrl, server, width)
    }

    /**
     * The URL one stored image value resolves to, or null when there is none.
     *
     * [width] has to be one of the four the API produces; anything else is
     * refused with a 400, so an unexpected value asks for the original instead.
     */
    fun imageUrl(storedUrl: String?, server: ServerConfig?, width: Int?): String? {
        val url = storedUrl?.takeIf { it.isNotBlank() } ?: return null

        if (!url.startsWith(CACHED_PREFIX)) {
            // Already absolute: TMDB or Fanart.tv, fetched from there.
            return url.takeIf { it.startsWith("http://") || it.startsWith("https://") }
        }

        val target = server?.takeIf { it.isUsable } ?: return null
        val name = url.removePrefix(CACHED_PREFIX)

        val params = buildList {
            if (width != null && width in SUPPORTED_WIDTHS) add("w=$width")
            // The token as a query parameter is what the API documents for image
            // loaders: Coil sets no headers of its own on a plain URL.
            if (target.token.isNotBlank()) add("token=${target.token.trim().encoded()}")
        }

        return buildString {
            append(target.baseUrl)
            append("/api/v1/posters/")
            append(name.encoded())
            if (params.isNotEmpty()) {
                append('?')
                append(params.joinToString("&"))
            }
        }
    }

    private fun String.encoded(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

    val SUPPORTED_WIDTHS = listOf(160, 320, 480, 640)
}
