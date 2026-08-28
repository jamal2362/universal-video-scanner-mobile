package com.jamal2367.uvsmobile.data.model

import androidx.annotation.StringRes
import com.jamal2367.uvsmobile.R

/** A field the library can be narrowed down by, matched exactly. */
enum class FilterField(val key: String, @StringRes val labelRes: Int) {
    HDR_FORMAT("hdr_format", R.string.field_hdr_format),
    HDR_DETAIL("hdr_detail", R.string.field_hdr_detail),
    EL_TYPE("el_type", R.string.field_el_type),
    DV_CM_VERSION("dv_cm_version", R.string.field_dv_cm_version),
    RESOLUTION("resolution", R.string.field_resolution),
    RESOLUTION_CLASS("resolution_class", R.string.field_resolution_class),
    VIDEO_CODEC("video_codec", R.string.field_video_codec),
    VIDEO_ENCODER("video_encoder", R.string.field_video_encoder),
    AUDIO_CODEC("audio_codec", R.string.field_audio_codec),
}

/** How the two ends of a range should be typed in and printed. */
enum class RangeUnit { SECONDS, BYTES, KILOBITS, YEAR, RATING_10, RATING_100, RANK }

/** A field that takes a `min_`/`max_` pair. */
enum class RangeField(
    val key: String,
    @StringRes val labelRes: Int,
    val unit: RangeUnit,
) {
    DURATION("duration", R.string.field_duration, RangeUnit.SECONDS),
    FILE_SIZE("file_size", R.string.field_file_size, RangeUnit.BYTES),
    VIDEO_BITRATE("video_bitrate", R.string.field_video_bitrate, RangeUnit.KILOBITS),
    AUDIO_BITRATE("audio_bitrate", R.string.field_audio_bitrate, RangeUnit.KILOBITS),
    TMDB_YEAR("tmdb_year", R.string.field_tmdb_year, RangeUnit.YEAR),
    TMDB_RATING("tmdb_rating", R.string.rating_tmdb, RangeUnit.RATING_10),
    IMDB_RATING("imdb_rating", R.string.rating_imdb, RangeUnit.RATING_10),
    RT_RATING("rt_rating", R.string.rating_rt, RangeUnit.RATING_100),
    RT_AUDIENCE("rt_audience", R.string.rating_rt_audience, RangeUnit.RATING_100),
    TRAKT_RATING("trakt_rating", R.string.rating_trakt, RangeUnit.RATING_100),
    METACRITIC("metacritic", R.string.rating_metacritic, RangeUnit.RATING_100),
    // Its own label rather than `rating_top250`: that one is the sentence a
    // title's screen puts a rank into - `IMDb Top 250: #%1$d` - and a filter
    // row is a name for two boxes, with no rank to put anywhere.
    IMDB_TOP250("imdb_top250", R.string.field_top250, RangeUnit.RANK),
    MTIME("mtime", R.string.field_mtime, RangeUnit.SECONDS),
}

/** One end, the other, or both - either may be left open. */
data class RangeValue(val min: Double? = null, val max: Double? = null) {
    val isEmpty: Boolean get() = min == null && max == null
}

/** The single fields `sort` accepts. */
enum class SortField(val key: String) {
    FILENAME("filename"),
    TMDB_TITLE("tmdb_title"),
    MTIME("mtime"),
    UPDATED_AT("updated_at"),
    FILE_SIZE("file_size"),
    DURATION("duration"),
    RESOLUTION("resolution"),
    VIDEO_CODEC("video_codec"),
    VIDEO_BITRATE("video_bitrate"),
    AUDIO_BITRATE("audio_bitrate"),
    HDR_FORMAT("hdr_format"),
    AUDIO_CODEC("audio_codec"),
    DV_CM_VERSION("dv_cm_version"),
    TMDB_YEAR("tmdb_year"),
    TMDB_RATING("tmdb_rating"),
    IMDB_RATING("imdb_rating"),
    RT_RATING("rt_rating"),
    RT_AUDIENCE("rt_audience"),
    TRAKT_RATING("trakt_rating"),
    METACRITIC("metacritic"),
}

/**
 * The orders offered on screen - every one the web interface has, ranked the
 * same way, including its combined modes.
 *
 * `sort` may name several fields separated by commas: it sorts by the first and
 * settles ties with the next, so "HDR format + audio track" is one parameter
 * rather than a second pass on the phone.
 *
 * Each carries the direction it is normally wanted in. Nobody asks for a
 * library by rating and means the worst film first, and "recently added" read
 * upwards is the oldest file on the disk - so picking an order sets its
 * direction too, and the two buttons above the list turn it round again for
 * the times that is not what was meant.
 */
enum class SortOption(
    val fields: List<SortField>,
    @StringRes val labelRes: Int,
    val defaultOrder: SortOrder = SortOrder.ASC,
) {
    FILENAME(listOf(SortField.FILENAME), R.string.sort_filename),
    TITLE(listOf(SortField.TMDB_TITLE), R.string.sort_tmdb_title),

    /**
     * What landed in the library last.
     *
     * The file's own modification time, because the scanner keeps no "added"
     * stamp of its own: `updated_at` is written again every time a rating or a
     * poster is filled in later, which would answer a different question.
     */
    RECENTLY_ADDED(listOf(SortField.MTIME), R.string.sort_recently_added, SortOrder.DESC),
    UPDATED(listOf(SortField.UPDATED_AT), R.string.sort_updated_at, SortOrder.DESC),
    YEAR(listOf(SortField.TMDB_YEAR), R.string.sort_tmdb_year, SortOrder.DESC),
    FILE_SIZE(listOf(SortField.FILE_SIZE), R.string.sort_file_size, SortOrder.DESC),
    DURATION(listOf(SortField.DURATION), R.string.sort_duration, SortOrder.DESC),
    RESOLUTION(listOf(SortField.RESOLUTION), R.string.sort_resolution, SortOrder.DESC),
    VIDEO_CODEC(listOf(SortField.VIDEO_CODEC), R.string.sort_video_codec, SortOrder.DESC),
    VIDEO_BITRATE(listOf(SortField.VIDEO_BITRATE), R.string.sort_video_bitrate, SortOrder.DESC),
    AUDIO_BITRATE(listOf(SortField.AUDIO_BITRATE), R.string.sort_audio_bitrate, SortOrder.DESC),
    HDR_FORMAT(listOf(SortField.HDR_FORMAT), R.string.sort_hdr_format, SortOrder.DESC),
    AUDIO_CODEC(listOf(SortField.AUDIO_CODEC), R.string.sort_audio_codec, SortOrder.DESC),
    CM_VERSION(listOf(SortField.DV_CM_VERSION), R.string.sort_dv_cm_version, SortOrder.DESC),
    HDR_AND_AUDIO(
        listOf(SortField.HDR_FORMAT, SortField.AUDIO_CODEC),
        R.string.sort_hdr_audio_codec,
        SortOrder.DESC,
    ),
    HDR_AND_VIDEO_BITRATE(
        listOf(SortField.HDR_FORMAT, SortField.VIDEO_BITRATE),
        R.string.sort_hdr_video_bitrate,
        SortOrder.DESC,
    ),
    HDR_AND_AUDIO_BITRATE(
        listOf(SortField.HDR_FORMAT, SortField.AUDIO_BITRATE),
        R.string.sort_hdr_audio_bitrate,
        SortOrder.DESC,
    ),
    AUDIO_AND_BITRATE(
        listOf(SortField.AUDIO_CODEC, SortField.AUDIO_BITRATE),
        R.string.sort_audio_codec_bitrate,
        SortOrder.DESC,
    ),
    TMDB_RATING(listOf(SortField.TMDB_RATING), R.string.sort_tmdb_rating, SortOrder.DESC),
    IMDB_RATING(listOf(SortField.IMDB_RATING), R.string.sort_imdb_rating, SortOrder.DESC),
    RT_RATING(listOf(SortField.RT_RATING), R.string.sort_rt_rating, SortOrder.DESC),
    RT_AUDIENCE(listOf(SortField.RT_AUDIENCE), R.string.sort_rt_audience, SortOrder.DESC),
    TRAKT_RATING(listOf(SortField.TRAKT_RATING), R.string.sort_trakt_rating, SortOrder.DESC),
    METACRITIC(listOf(SortField.METACRITIC), R.string.sort_metacritic, SortOrder.DESC);

    val queryValue: String get() = fields.joinToString(",") { it.key }
}

enum class SortOrder(val key: String) { ASC("asc"), DESC("desc") }

/**
 * Everything a library request asks for.
 *
 * All of it is answered by the server: the filtering, the ordering and the
 * paging happen there, so a library of thousands never has to travel to the
 * phone to have a handful of titles picked out of it.
 */
data class LibraryQuery(
    val search: String = "",
    val filters: Map<FilterField, String> = emptyMap(),
    val ranges: Map<RangeField, RangeValue> = emptyMap(),
    val sort: SortOption = SortOption.FILENAME,
    val order: SortOrder = SortOrder.ASC,
    val updatedSince: Double? = null,
    /** The subset of an entry to fetch; null asks for the whole record. */
    val fields: List<String>? = LIST_FIELDS,
) {
    /** How many narrowing choices are in force, for the badge on the filter button. */
    val activeFilterCount: Int
        get() = filters.count { it.value.isNotBlank() } + ranges.count { !it.value.isEmpty }

    val hasAnyNarrowing: Boolean
        get() = activeFilterCount > 0 || search.isNotBlank()

    /**
     * Whether this is a question the library screen opens on by itself.
     *
     * The order and the narrowing are stored and put back, so those still
     * count; a search term is not - it is asked about one moment and never
     * restored - and neither is a projection for the filter sheet or a
     * `updated_since` catch-up. Only this shape is worth keeping on disk for
     * the next launch to fill its screen with.
     */
    val isWhatALaunchOpensOn: Boolean
        get() = search.isBlank() && updatedSince == null && fields == LIST_FIELDS

    /** The query string, exactly as `/api/v1/library` documents it. */
    fun toParams(limit: Int?, offset: Int): Map<String, String> = buildMap {
        put("sort", sort.queryValue)
        put("order", order.key)
        limit?.let { put("limit", it.toString()) }
        if (offset > 0) put("offset", offset.toString())
        search.trim().takeIf { it.isNotEmpty() }?.let { put("search", it) }

        filters.forEach { (field, value) ->
            value.trim().takeIf { it.isNotEmpty() }?.let { put(field.key, it) }
        }

        ranges.forEach { (field, range) ->
            range.min?.let { put("min_${field.key}", it.formatForQuery()) }
            range.max?.let { put("max_${field.key}", it.formatForQuery()) }
        }

        updatedSince?.let { put("updated_since", it.formatForQuery()) }
        fields?.takeIf { it.isNotEmpty() }?.let { put("fields", it.joinToString(",")) }
    }

    private fun Double.formatForQuery(): String =
        if (this == kotlin.math.floor(this) && !isInfinite()) toLong().toString() else toString()

    companion object {
        /**
         * What a list row actually shows.
         *
         * Asking for these instead of the whole record is the difference
         * between roughly 0.5 kB and 1.7 kB per entry - on a library of two
         * thousand titles, a megabyte less over a mobile connection.
         */
        /**
         * The fields whose distinct values the filter sheet offers as chips.
         *
         * `/library/stats` counts the other filterable fields, but not these
         * four - and they are exactly the ones nobody can type from memory:
         * the API matches a filter exactly, so "Profile 8" never finds a title
         * whose detail reads "Dolby Vision Profile 8.1". Asked for on their own
         * with `fields=`, the whole library costs about 150 bytes an entry.
         */
        val VALUE_FIELDS = listOf("hdr_detail", "el_type", "video_encoder", "dv_cm_version")

        val LIST_FIELDS = listOf(
            "path", "filename", "poster_url", "portrait_url", "tmdb_title", "tmdb_year",
            "resolution", "resolution_class", "hdr_format", "hdr_detail", "el_type",
            "video_codec", "audio_codec", "file_size", "duration",
            "imdb_rating", "tmdb_rating", "imdb_top250", "updated_at",
        )
    }
}
