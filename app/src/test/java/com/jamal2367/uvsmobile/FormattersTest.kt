package com.jamal2367.uvsmobile

import com.jamal2367.uvsmobile.util.Formatters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Turning what the scanner stores into what a person reads.
 *
 * The rule these all share: a value the scanner could not determine is stored as
 * 0 or left out, and must come back as null so the screen leaves the row out
 * rather than printing a measurement nobody took.
 */
class FormattersTest {

    @Test
    fun `nothing measured is nothing shown`() {
        assertNull(Formatters.fileSize(null))
        assertNull(Formatters.fileSize(0.0))
        assertNull(Formatters.duration(0.0))
        assertNull(Formatters.durationCompact(0.0))
        assertNull(Formatters.durationExact(0.0))
        assertNull(Formatters.videoBitrate(0.0))
        assertNull(Formatters.audioBitrate(0.0))
        assertNull(Formatters.luminance(0.0))
        assertNull(Formatters.nits(0.0))
        assertNull(Formatters.ratingOutOfTen(0.0))
        assertNull(Formatters.percentage(0.0))
    }

    @Test
    fun `a duration reads in hours and minutes`() {
        assertEquals("2 h 35 min", Formatters.duration(9_345.0))
        assertEquals("48 min", Formatters.duration(2_880.0))
    }

    /** What a tile has room for: the same time without the word for it. */
    @Test
    fun `a compact duration drops the unit off the minutes`() {
        assertEquals("2 h 35", Formatters.durationCompact(9_345.0))
        assertEquals("48 min", Formatters.durationCompact(2_880.0))
    }

    /** A title's own screen counts the seconds, and pads every part. */
    @Test
    fun `an exact duration is a timecode`() {
        assertEquals("02:35:45", Formatters.durationExact(9_345.0))
        assertEquals("00:48:00", Formatters.durationExact(2_880.0))
        assertEquals("00:00:09", Formatters.durationExact(9.0))
    }

    /** A picture in megabits with two decimals, a track always in kilobits. */
    @Test
    fun `each bitrate reads in the unit it is spoken of in`() {
        assertEquals("64,20 Mb/s", Formatters.videoBitrate(64_200.0).normalized())
        assertEquals("640 Kb/s", Formatters.videoBitrate(640.0))
        assertEquals("2116 Kb/s", Formatters.audioBitrate(2_116.0))
        assertEquals("4448 Kb/s", Formatters.audioBitrate(4_448.0))
    }

    @Test
    fun `a file size counts in the decimal units a disk does`() {
        assertEquals("78,00 GB", Formatters.fileSize(78_000_000_000.0).normalized())
        assertEquals("14,25 GB", Formatters.fileSize(14_250_000_000.0).normalized())
        assertEquals("512 B", Formatters.fileSize(512.0))
    }

    /**
     * The paired rows print what the stream does not carry as the zero it is
     * stored as: which half is missing is the thing worth seeing, and the
     * screen decides whether the layer belongs on it at all.
     */
    @Test
    fun `a pair keeps its zeros and carries one unit`() {
        assertEquals(
            "4000 | 0,0050 cd/m²",
            Formatters.luminancePair(4_000.0, 0.005).normalized(),
        )
        assertEquals("833 | 108 cd/m²", Formatters.nitsPair(833.0, 108.0))
        assertEquals("0 | 0 cd/m²", Formatters.nitsPair(0.0, null))
    }

    /** The star is what says which of the numbers under a cover is the rating. */
    @Test
    fun `a rating wears its star`() {
        assertEquals("★ 8,4", Formatters.ratingStarred(8.44).normalized())
        assertNull(Formatters.ratingStarred(0.0))
    }

    /**
     * The tests run under whatever locale the machine has, and the decimal
     * separator follows it; the value is what is under test, not the comma.
     */
    private fun String?.normalized(): String? = this?.replace('.', ',')
}
