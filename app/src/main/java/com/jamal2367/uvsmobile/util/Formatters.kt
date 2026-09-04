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

    /** `14,2 GB` - decimal units, the way a file manager and a disk both count. */
    fun fileSize(bytes: Double?): String? {
        val value = bytes?.takeIf { it > 0 } ?: return null
        val units = listOf("B", "kB", "MB", "GB", "TB")
        var size = value
        var unit = 0
        while (size >= 1000 && unit < units.lastIndex) {
            size /= 1000
            unit++
        }
        val decimals = if (unit == 0 || size >= 100) 0 else 1
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

    /** The scanner stores bitrates in kb/s; anything past a megabit reads better as one. */
    fun bitrate(kilobitsPerSecond: Double?): String? {
        val value = kilobitsPerSecond?.takeIf { it > 0 } ?: return null
        return if (value >= 1000) "${format(value / 1000, 1)} Mb/s" else "${value.roundToInt()} kb/s"
    }

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
        val decimals = when {
            nits >= 100 -> 0
            nits >= 1 -> 1
            else -> 4
        }
        return "${format(nits, decimals)} cd/m²"
    }

    /** `1200 cd/m²` for the content light levels, which are whole numbers. */
    fun nits(value: Double?): String? =
        value?.takeIf { it > 0 }?.let { "${it.roundToInt()} cd/m²" }

    /** A whole number the API happens to carry as a floating point one. */
    fun whole(value: Double?): String? =
        value?.takeIf { abs(it) > 0 }?.roundToInt()?.toString()

    private fun format(value: Double, decimals: Int): String =
        String.format(Locale.getDefault(), "%.${decimals}f", value)
}
