package com.jamal2367.uvsmobile.util

import com.jamal2367.uvsmobile.data.model.FilterField
import com.jamal2367.uvsmobile.data.model.LibraryEntry

/**
 * One grade the library holds, named in full, with the filter that finds it.
 *
 * The name and the filter are two different things, and that is the point:
 * "Dolby Vision Profile 8.1" is what a person is looking for, while the field
 * that finds those titles is the detail line rather than the format - the
 * stored format for every one of them is the bare words "Dolby Vision".
 */
data class HdrGroup(
    val label: String,
    val field: FilterField,
    val value: String,
    val count: Int = 0,
)

/**
 * Naming the grades a library holds, by what actually tells them apart.
 *
 * `/library/stats` counts by the stored format, which puts every Dolby Vision
 * title - profile 5, profile 7 with a layer, profile 8.1 - under one heading
 * that says nothing about any of them. The profile is carried in the detail
 * line and the layer in its own field, so the grouping is done here, off a
 * projection of the library, and each group keeps the filter that reproduces it.
 */
object HdrGroups {

    /** What the scanner writes for a title it could not grade. */
    const val UNKNOWN = "Unknown"

    /** The grades in a projection of the library, the commonest first. */
    fun of(entries: List<LibraryEntry>): List<HdrGroup> =
        entries.groupBy { label(it) }
            .map { (label, group) -> group(label, group) }
            .sortedByDescending { it.count }

    /**
     * What one title's grade is called.
     *
     * Dolby Vision by its profile and, where there is one, its enhancement
     * layer. HDR10+ by the plus, which the scanner does not always carry up
     * into the format: a title graded HDR10+ is filed under "HDR10" with only
     * the detail line saying which it really is.
     */
    fun label(entry: LibraryEntry): String {
        val format = entry.hdrFormat?.trim().orEmpty()
        if (format.isEmpty() || format.equals(UNKNOWN, ignoreCase = true)) return UNKNOWN

        val detail = entry.hdrDetail?.trim().orEmpty()
        if (format.contains("Dolby Vision", ignoreCase = true)) {
            return buildString {
                append("Dolby Vision")
                dolbyVisionProfile(detail)?.let { append(" Profile ").append(it) }
                entry.elType?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    append(" (").append(it.uppercase()).append(")")
                }
            }
        }

        if (detail.contains("HDR10+", ignoreCase = true)) return "HDR10+"
        return format
    }

    /**
     * The profile out of a detail line like "Dolby Vision Profile 8.1".
     *
     * Written with the decimal a profile is always spoken with - profile 5
     * reads "5.0" - so a column of them lines up rather than mixing "5" with
     * "8.1".
     */
    fun dolbyVisionProfile(detail: String): String? {
        val number = PROFILE_PATTERN.find(detail)?.groupValues?.get(1) ?: return null
        return if (number.contains('.')) number else "$number.0"
    }

    /**
     * The narrowing that brings a group back.
     *
     * The most exact field that covers the whole group and nothing else: the
     * detail line where every title in it carries the same one, the layer where
     * they at least share that, and the format when neither holds. A filter
     * that is too wide shows a few titles too many; one that is too narrow
     * shows none at all, which is the failure worth avoiding.
     */
    private fun group(label: String, entries: List<LibraryEntry>): HdrGroup {
        val details = entries.mapNotNull { it.hdrDetail?.trim()?.takeIf { d -> d.isNotEmpty() } }
        val layers = entries.mapNotNull { it.elType?.trim()?.takeIf { l -> l.isNotEmpty() } }
        val formats = entries.mapNotNull { it.hdrFormat?.trim()?.takeIf { f -> f.isNotEmpty() } }

        val (field, value) = when {
            details.size == entries.size && details.distinct().size == 1 ->
                FilterField.HDR_DETAIL to details.first()

            layers.size == entries.size && layers.distinct().size == 1 ->
                FilterField.EL_TYPE to layers.first()

            else -> FilterField.HDR_FORMAT to (formats.firstOrNull() ?: UNKNOWN)
        }

        return HdrGroup(label = label, field = field, value = value, count = entries.size)
    }

    private val PROFILE_PATTERN = Regex("""Profile\s+(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
}
