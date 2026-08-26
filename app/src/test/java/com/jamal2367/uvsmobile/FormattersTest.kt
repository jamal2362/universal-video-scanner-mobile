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
        assertNull(Formatters.bitrate(0.0))
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

    @Test
    fun `a bitrate past a megabit reads as one`() {
        assertEquals("64,2 Mb/s", Formatters.bitrate(64_200.0).normalized())
        assertEquals("640 kb/s", Formatters.bitrate(640.0))
    }

    @Test
    fun `a file size counts in the decimal units a disk does`() {
        assertEquals("78,0 GB", Formatters.fileSize(78_000_000_000.0).normalized())
        assertEquals("512 B", Formatters.fileSize(512.0))
    }

    /**
     * The tests run under whatever locale the machine has, and the decimal
     * separator follows it; the value is what is under test, not the comma.
     */
    private fun String?.normalized(): String? = this?.replace('.', ',')
}
