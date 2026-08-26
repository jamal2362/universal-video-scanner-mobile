package com.jamal2367.uvsmobile

import com.jamal2367.uvsmobile.data.model.FilterField
import com.jamal2367.uvsmobile.data.model.LibraryQuery
import com.jamal2367.uvsmobile.data.model.RangeField
import com.jamal2367.uvsmobile.data.model.RangeValue
import com.jamal2367.uvsmobile.data.model.SortOption
import com.jamal2367.uvsmobile.data.model.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the library screen actually asks the server.
 *
 * Every one of these parameters is documented in the API's section 7.5; getting
 * the spelling wrong is answered with a `400`, not with a wrong result, so it is
 * worth pinning down here.
 */
class LibraryQueryTest {

    @Test
    fun `a bare query still names its order`() {
        val params = LibraryQuery(fields = null).toParams(limit = 60, offset = 0)

        assertEquals("filename", params["sort"])
        assertEquals("asc", params["order"])
        assertEquals("60", params["limit"])
        assertNull(params["offset"])
        assertNull(params["search"])
    }

    @Test
    fun `a combined order travels as one comma-separated sort`() {
        val params = LibraryQuery(
            sort = SortOption.HDR_AND_AUDIO,
            order = SortOrder.DESC,
            fields = null,
        ).toParams(limit = null, offset = 0)

        assertEquals("hdr_format,audio_codec", params["sort"])
        assertEquals("desc", params["order"])
    }

    @Test
    fun `filters and ranges are spelled the way the API reads them`() {
        val params = LibraryQuery(
            search = "  x265  ",
            filters = mapOf(
                FilterField.EL_TYPE to "FEL",
                FilterField.RESOLUTION_CLASS to "4K",
                // An empty value is not a filter - it would match nothing.
                FilterField.VIDEO_ENCODER to "   ",
            ),
            ranges = mapOf(
                RangeField.VIDEO_BITRATE to RangeValue(min = 60_000.0),
                RangeField.IMDB_TOP250 to RangeValue(min = 1.0, max = 250.0),
                RangeField.TMDB_RATING to RangeValue(max = 7.5),
            ),
            fields = null,
        ).toParams(limit = 20, offset = 40)

        assertEquals("x265", params["search"])
        assertEquals("FEL", params["el_type"])
        assertEquals("4K", params["resolution_class"])
        assertFalse(params.containsKey("video_encoder"))
        assertEquals("60000", params["min_video_bitrate"])
        assertEquals("1", params["min_imdb_top250"])
        assertEquals("250", params["max_imdb_top250"])
        assertEquals("7.5", params["max_tmdb_rating"])
        assertEquals("40", params["offset"])
    }

    @Test
    fun `the list view asks for the fields it draws and no others`() {
        val params = LibraryQuery().toParams(limit = 60, offset = 0)
        val fields = params.getValue("fields").split(",")

        assertTrue(fields.contains("path"))
        assertTrue(fields.contains("poster_url"))
        assertTrue(fields.contains("hdr_format"))
        // The plot is thousands of characters and no list row shows it.
        assertFalse(fields.contains("tmdb_plot"))
        assertFalse(fields.contains("tmdb_cast"))
    }

    @Test
    fun `updated_since is passed as whole epoch seconds`() {
        val params = LibraryQuery(updatedSince = 1_750_000_000.0, fields = null)
            .toParams(limit = null, offset = 0)

        assertEquals("1750000000", params["updated_since"])
    }

    @Test
    fun `the badge counts every narrowing choice that is in force`() {
        val query = LibraryQuery(
            search = "dune",
            filters = mapOf(FilterField.HDR_FORMAT to "Dolby Vision"),
            ranges = mapOf(
                RangeField.TMDB_YEAR to RangeValue(min = 2020.0),
                RangeField.DURATION to RangeValue(),
            ),
        )

        // The search box has its own field on screen, so it is not in the badge.
        assertEquals(2, query.activeFilterCount)
        assertTrue(query.hasAnyNarrowing)
        assertFalse(LibraryQuery().hasAnyNarrowing)
    }
}
