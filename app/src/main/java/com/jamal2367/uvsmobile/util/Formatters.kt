package com.jamal2367.uvsmobile.util

import android.content.Context
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Turning what the scanner stores into what a person reads.
 *
 * The scanner reports a value it could not determine as 0 or leaves it out, so
 * every helper here answers null for "nothing to show" and the screens leave
 * the row out rather than printing a confident zero.
 */
object Formatters {

    /**
     * `14,25 GB` - decimal units, the way a file manager and a disk both count.
     *
     * Two decimals from kilobytes up: at the size a film is, the first one is
     * a difference of a hundred megabytes and the second is worth reading too.
     * Bytes are whole - there is nothing under one.
     */
    fun fileSize(bytes: Double?): String? {
        val value = bytes?.takeIf { it > 0 } ?: return null
        val units = listOf("B", "kB", "MB", "GB", "TB")
        var size = value
        var unit = 0
        while (size >= 1000 && unit < units.lastIndex) {
            size /= 1000
            unit++
        }
        val decimals = if (unit == 0) 0 else 2
        return "${format(size, decimals)} ${units[unit]}"
    }

    /** `2 h 17 min`, or `48 min` for anything under the hour. */
    fun duration(seconds: Double?): String? {
        val total = seconds?.takeIf { it > 0 }?.roundToInt() ?: return null
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        return when {
            hours > 0 -> "$hours h $minutes min"
            minutes > 0 -> "$minutes min"
            else -> "$total s"
        }
    }

    /**
     * `01:53:09` - the running time to the second, on the clock.
     *
     * For the one screen that is about this file rather than about the library:
     * two encodes of the same film are minutes apart, two rips of the same disc
     * are seconds apart, and the seconds are how one is told from the other.
     * Every part padded to two digits and none of them left off, so a column of
     * running times lines up and is read as the timecode it is.
     */
    fun durationExact(seconds: Double?): String? {
        val total = seconds?.takeIf { it > 0 }?.roundToInt() ?: return null
        return String.format(
            Locale.getDefault(),
            "%02d:%02d:%02d",
            total / 3600,
            (total % 3600) / 60,
            total % 60,
        )
    }

    /**
     * `2 h 17` - the same running time, for a tile that has no room for it.
     *
     * Four covers across a phone leave each one about a dozen characters a
     * line, and "min" spelled out is a third of them spent on a word the colon
     * of a running time already implies.
     */
    fun durationCompact(seconds: Double?): String? {
        val total = seconds?.takeIf { it > 0 }?.roundToInt() ?: return null
        val hours = total / 3600
        val minutes = (total % 3600) / 60
        return when {
            hours > 0 -> "$hours h $minutes"
            minutes > 0 -> "$minutes min"
            else -> "$total s"
        }
    }

    /**
     * `49,09 Mb/s` - a picture's bitrate, the way the web interface prints it.
     *
     * The scanner stores bitrates in kilobits, and a feature film's picture is
     * tens of megabits: two decimals is what tells one encode from another at
     * that size. Anything under a megabit stays in kilobits, where a decimal
     * would say nothing.
     */
    fun videoBitrate(kilobitsPerSecond: Double?): String? {
        val value = kilobitsPerSecond?.takeIf { it > 0 } ?: return null
        return if (value >= 1000) {
            "${format(value / 1000, 2)} Mb/s"
        } else {
            "${value.roundToInt()} Kb/s"
        }
    }

    /**
     * `2116 Kb/s` - a track's bitrate, always in kilobits.
     *
     * The unit a track is specified and spoken of in: 640, 1509, 4448. Turning
     * the loud ones into megabits would be the one number on the screen that
     * has to be converted back before it can be compared with anything.
     */
    fun audioBitrate(kilobitsPerSecond: Double?): String? =
        kilobitsPerSecond?.takeIf { it > 0 }?.let { "${it.roundToInt()} Kb/s" }

    /** An epoch stamp in the reader's own date and time format. */
    fun timestamp(context: Context, epochSeconds: Double?): String? {
        val value = epochSeconds?.takeIf { it > 0 } ?: return null
        val date = Date((value * 1000).toLong())
        val dateFormat = android.text.format.DateFormat.getDateFormat(context)
        val timeFormat = android.text.format.DateFormat.getTimeFormat(context)
        return "${dateFormat.format(date)} ${timeFormat.format(date)}"
    }

    /** `8,4` - a rating out of ten. */
    fun ratingOutOfTen(value: Double?): String? =
        value?.takeIf { it > 0 }?.let { format(it, 1) }

    /**
     * `★ 8,4` - the same rating, said to be one.
     *
     * A bare number in a line that already carries a year, a frame size and a
     * running time is the one value on it nobody can name at a glance; the star
     * is what says which of the four it is.
     */
    fun ratingStarred(value: Double?): String? =
        ratingOutOfTen(value)?.let { "★ $it" }

    /** `86 %` - a rating out of a hundred. */
    fun percentage(value: Double?): String? =
        value?.takeIf { it > 0 }?.let { "${it.roundToInt()} %" }

    /** `4000 cd/m²`, with the fractions a minimum luminance needs. */
    fun luminance(value: Double?): String? {
        val nits = value?.takeIf { it > 0 } ?: return null
        return "${luminanceNumber(nits)} cd/m²"
    }

    /**
     * `4000 | 0,0050 cd/m²` - a mastering display's two ends in one line.
     *
     * The pair the web interface prints under "HDR10 MDL", brightest first: the
     * two are read against each other, and a unit repeated on both halves is
     * the same three characters twice.
     *
     * A half the stream does not carry is printed as the `0` it is stored as,
     * rather than taking the row away: which of the two is missing is itself
     * worth seeing, and the screen decides whether the layer is there at all.
     */
    fun luminancePair(max: Double?, min: Double?): String =
        "${luminanceNumber(max ?: 0.0)} | ${luminanceNumber(min ?: 0.0)} cd/m²"

    /** `1200 cd/m²` for the content light levels, which are whole numbers. */
    fun nits(value: Double?): String? =
        value?.takeIf { it > 0 }?.let { "${it.roundToInt()} cd/m²" }

    /**
     * `833 | 108 cd/m²` - MaxCLL and MaxFALL, paired the way the web interface
     * pairs them, and zero where the stream carries no level.
     */
    fun nitsPair(maxCll: Double?, maxFall: Double?): String =
        "${(maxCll ?: 0.0).roundToInt()} | ${(maxFall ?: 0.0).roundToInt()} cd/m²"

    /** How many decimals a luminance is worth: none at 4000, four at 0,0050. */
    private fun luminanceNumber(nits: Double): String {
        val decimals = when {
            nits >= 100 -> 0
            nits >= 1 -> 1
            else -> 4
        }
        return format(nits, decimals)
    }

    /** A whole number the API happens to carry as a floating point one. */
    fun whole(value: Double?): String? =
        value?.takeIf { abs(it) > 0 }?.roundToInt()?.toString()

    private fun format(value: Double, decimals: Int): String =
        String.format(Locale.getDefault(), "%.${decimals}f", value)
}
