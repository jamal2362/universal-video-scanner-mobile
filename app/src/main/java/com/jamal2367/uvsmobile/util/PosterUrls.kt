package com.jamal2367.uvsmobile.util

import com.jamal2367.uvsmobile.data.prefs.ServerConfig
import java.net.URLEncoder

/**
 * Where a poster is fetched from.
 *
 * An entry's `poster_url` is `/poster/<name>.jpg` once the scanner managed to
 * cache the image - that one is served inside the API, resized to the width the
 * grid actually needs. An entry whose image could not be cached carries the
 * remote URL instead, and that one is loaded from its own host.
 */
object PosterUrls {

    private const val CACHED_PREFIX = "/poster/"

    /**
     * The URL to hand an image loader, or null when the entry has no poster.
     *
     * [width] has to be one of the four the API produces; anything else is
     * refused with a 400, so an unexpected value asks for the original instead.
     */
    fun forEntry(posterUrl: String?, server: ServerConfig?, width: Int?): String? {
        val url = posterUrl?.takeIf { it.isNotBlank() } ?: return null

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
