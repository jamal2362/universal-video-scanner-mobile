package com.jamal2367.uvsmobile.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One scanned title, exactly as `/api/v1/library` hands it out.
 *
 * Every field but [path] is optional: a caller may ask for a subset with
 * `fields=`, and the scanner leaves a value out entirely when a lookup never
 * answered. `path` is the one the server always keeps - it identifies the entry
 * and is what every write endpoint is addressed by.
 */
@Serializable
data class LibraryEntry(
    val path: String = "",
    val filename: String? = null,

    // --- picture ---
    @SerialName("hdr_format") val hdrFormat: String? = null,
    @SerialName("hdr_detail") val hdrDetail: String? = null,
    @SerialName("el_type") val elType: String? = null,
    @SerialName("dv_cm_version") val dvCmVersion: String? = null,
    val resolution: String? = null,
    @SerialName("resolution_class") val resolutionClass: String? = null,
    @SerialName("video_codec") val videoCodec: String? = null,
    @SerialName("video_codec_profile") val videoCodecProfile: String? = null,
    @SerialName("video_encoder") val videoEncoder: String? = null,
    @SerialName("video_bitrate") val videoBitrate: Double? = null,
    @SerialName("hdr_metadata") val hdrMetadata: HdrMetadata? = null,

    // --- sound ---
    @SerialName("audio_codec") val audioCodec: String? = null,
    @SerialName("audio_bitrate") val audioBitrate: Double? = null,

    // --- file ---
    val duration: Double? = null,
    @SerialName("file_size") val fileSize: Double? = null,
    val mtime: Double? = null,
    @SerialName("updated_at") val updatedAt: Double? = null,

    // --- metadata ---
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("tmdb_id") val tmdbId: String? = null,
    @SerialName("tmdb_title") val tmdbTitle: String? = null,
    @SerialName("tmdb_year") val tmdbYear: String? = null,
    @SerialName("tmdb_plot") val tmdbPlot: String? = null,
    @SerialName("tmdb_tagline") val tmdbTagline: String? = null,
    @SerialName("tmdb_directors") val tmdbDirectors: List<String> = emptyList(),
    @SerialName("tmdb_cast") val tmdbCast: List<String> = emptyList(),
    @SerialName("tmdb_genres") val tmdbGenres: List<String> = emptyList(),
    @SerialName("imdb_id") val imdbId: String? = null,

    // --- ratings ---
    @SerialName("tmdb_rating") val tmdbRating: Double? = null,
    @SerialName("imdb_rating") val imdbRating: Double? = null,
    @SerialName("rt_rating") val rtRating: Double? = null,
    @SerialName("rt_audience") val rtAudience: Double? = null,
    @SerialName("trakt_rating") val traktRating: Double? = null,
    val metacritic: Double? = null,
    @SerialName("imdb_top250") val imdbTop250: Double? = null,
) {
    /** What to put on screen: the looked-up title, or the file name it was cut from. */
    val displayTitle: String
        get() = tmdbTitle?.takeIf { it.isNotBlank() }
            ?: filename?.takeIf { it.isNotBlank() }
            ?: path.substringAfterLast('/')

    /** The Top 250 rank as a whole number, or null for a title outside the chart. */
    val top250Rank: Int?
        get() = imdbTop250?.takeIf { it > 0 }?.toInt()

    val hasAnyRating: Boolean
        get() = listOfNotNull(tmdbRating, imdbRating, rtRating, rtAudience, traktRating, metacritic)
            .any { it > 0 }
}
