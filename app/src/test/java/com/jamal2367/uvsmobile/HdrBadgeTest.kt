package com.jamal2367.uvsmobile

import com.jamal2367.uvsmobile.data.model.LibraryEntry
import com.jamal2367.uvsmobile.ui.components.hdrLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * What the badge on a cover says.
 *
 * One chip's worth of room, so it carries what tells one title apart from the
 * next - the enhancement layer, the profile, the format - and never the family
 * name every title in that colour already shares.
 */
class HdrBadgeTest {

    @Test
    fun `dolby vision is named by its enhancement layer`() {
        assertEquals(
            "FEL",
            hdrLabel(
                LibraryEntry(
                    hdrFormat = "Dolby Vision",
                    hdrDetail = "Dolby Vision Profile 7 (FEL)",
                    elType = "FEL",
                )
            ),
        )
        assertEquals(
            "MEL",
            hdrLabel(
                LibraryEntry(
                    hdrFormat = "Dolby Vision",
                    hdrDetail = "Dolby Vision Profile 7 (MEL)",
                    elType = "MEL",
                )
            ),
        )
    }

    @Test
    fun `a title without an enhancement layer is named by its profile`() {
        assertEquals(
            "8.1",
            hdrLabel(
                LibraryEntry(hdrFormat = "Dolby Vision", hdrDetail = "Dolby Vision Profile 8.1")
            ),
        )
        // Spoken as five point oh, and written that way, so a column of badges
        // lines up rather than mixing "5" with "8.1".
        assertEquals(
            "5.0",
            hdrLabel(
                LibraryEntry(hdrFormat = "Dolby Vision", hdrDetail = "Dolby Vision Profile 5")
            ),
        )
    }

    @Test
    fun `dolby vision with nothing to go on still says so`() {
        assertEquals("DV", hdrLabel(LibraryEntry(hdrFormat = "Dolby Vision")))
    }

    @Test
    fun `every other format is its own short name`() {
        assertEquals("HDR10", hdrLabel(LibraryEntry(hdrFormat = "HDR10")))
        assertEquals("HDR10+", hdrLabel(LibraryEntry(hdrFormat = "HDR10+")))
        assertEquals("HLG", hdrLabel(LibraryEntry(hdrFormat = "HLG")))
        assertEquals("SDR", hdrLabel(LibraryEntry(hdrFormat = "SDR")))
    }

    @Test
    fun `the plus is read off the detail when the format leaves it out`() {
        assertEquals(
            "HDR10+",
            hdrLabel(LibraryEntry(hdrFormat = "HDR10", hdrDetail = "HDR10+ Profile B")),
        )
    }

    @Test
    fun `a grade the scanner could not determine says nothing at all`() {
        assertNull(hdrLabel(LibraryEntry()))
        assertNull(hdrLabel(LibraryEntry(hdrFormat = "")))
        assertNull(hdrLabel(LibraryEntry(hdrFormat = "Unknown")))
    }
}
