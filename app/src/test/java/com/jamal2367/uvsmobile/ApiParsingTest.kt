package com.jamal2367.uvsmobile

import com.jamal2367.uvsmobile.data.model.LibraryEntry
import com.jamal2367.uvsmobile.data.model.LibraryPage
import com.jamal2367.uvsmobile.data.model.LibraryStats
import com.jamal2367.uvsmobile.data.model.ScanProgress
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading what the scanner actually sends.
 *
 * The database holds whatever the probes and the online lookups produced, so a
 * field can be a number in one entry and a string in another, absent in a third,
 * and a newer server may add keys this build has never heard of. None of that
 * may turn into an empty screen.
 */
class ApiParsingTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Test
    fun `a full entry is read field for field`() {
        val page = json.decodeFromString(
            LibraryPage.serializer(),
            """
            {
              "success": true, "count": 1, "total": 1, "offset": 0, "limit": 60,
              "files": [{
                "path": "/media/Dune.mkv",
                "filename": "Dune.2021.2160p.mkv",
                "hdr_format": "Dolby Vision",
                "hdr_detail": "Dolby Vision Profile 7 (FEL)",
                "el_type": "FEL",
                "dv_cm_version": "CMv4.0 (ST-DL)",
                "resolution": "3840x1600",
                "resolution_class": "4K",
                "video_codec": "H.265",
                "video_codec_profile": "Main 10",
                "video_encoder": "x265",
                "audio_codec": "Dolby TrueHD Atmos 7.1",
                "duration": 9345.6,
                "video_bitrate": 64200,
                "audio_bitrate": 4500,
                "file_size": 78000000000,
                "mtime": 1750000000,
                "updated_at": 1750000900.5,
                "poster_url": "/poster/tmdb_438631.jpg",
                "portrait_url": "/poster/tmdb_portrait_438631.jpg",
                "tmdb_id": 438631,
                "tmdb_title": "Dune",
                "tmdb_year": "2021",
                "tmdb_rating": 7.8,
                "tmdb_plot": "Paul Atreides...",
                "tmdb_tagline": "Beyond fear, destiny awaits.",
                "tmdb_directors": ["Denis Villeneuve"],
                "tmdb_cast": ["Timothee Chalamet", "Rebecca Ferguson"],
                "tmdb_genres": ["Science Fiction", "Adventure"],
                "imdb_id": "tt1160419",
                "imdb_rating": 8.0,
                "imdb_top250": 172,
                "rt_rating": 83,
                "rt_audience": 90,
                "trakt_rating": 82,
                "metacritic": 74,
                "hdr_metadata": {
                  "hdr10_mdl_max": 4000, "hdr10_mdl_min": 0.005,
                  "hdr10_max_cll": 1200, "hdr10_max_fall": 250,
                  "rpu_mdl_max": 4000, "rpu_mdl_min": 0.005,
                  "rpu_max_cll": 1100, "rpu_max_fall": 240,
                  "l5_left": 0, "l5_right": 0, "l5_top": 140, "l5_bottom": 140
                }
              }]
            }
            """.trimIndent(),
        )

        val entry = page.files.single()
        assertEquals(1, page.total)
        assertEquals("Dune", entry.displayTitle)
        assertEquals("FEL", entry.elType)
        // The id arrives as a number and is kept as the text it identifies with.
        assertEquals("438631", entry.tmdbId)
        assertEquals(172, entry.top250Rank)
        assertTrue(entry.hasAnyRating)
        assertEquals(2, entry.tmdbCast.size)
        assertTrue(entry.hdrMetadata!!.hasRpu)
        assertTrue(entry.hdrMetadata!!.hasActiveArea)
        assertEquals("/poster/tmdb_portrait_438631.jpg", entry.portraitUrl)
    }

    @Test
    fun `an instance too old to know the cover still reads`() {
        // portrait_url arrived with the mobile app; an older scanner sends only
        // the backdrop, and an entry has to come back whole either way.
        val entry = json.decodeFromString(
            LibraryEntry.serializer(),
            """{"path": "/media/A.mkv", "poster_url": "/poster/tmdb_1.jpg"}""",
        )

        assertEquals("/poster/tmdb_1.jpg", entry.posterUrl)
        assertNull(entry.portraitUrl)
    }

    @Test
    fun `an entry the lookups never answered for reads as nothing rather than zero`() {
        val entry = json.decodeFromString(
            LibraryEntry.serializer(),
            """{"path": "/media/Home Video.mkv", "filename": "Home Video.mkv",
                "hdr_format": "SDR", "imdb_rating": null, "tmdb_rating": null}""",
        )

        assertNull(entry.imdbRating)
        assertNull(entry.top250Rank)
        assertFalse(entry.hasAnyRating)
        assertEquals("Home Video.mkv", entry.displayTitle)
        assertTrue(entry.tmdbGenres.isEmpty())
    }

    @Test
    fun `a projected answer carries only the fields that were asked for`() {
        val page = json.decodeFromString(
            LibraryPage.serializer(),
            """{"success": true, "count": 1, "total": 940, "offset": 60, "limit": 60,
                "files": [{"path": "/media/A.mkv", "filename": "A.mkv"}]}""",
        )

        assertEquals(940, page.total)
        assertEquals(60, page.offset)
        assertNull(page.files.single().videoCodec)
    }

    @Test
    fun `a key this build has never heard of does not spoil the answer`() {
        val entry = json.decodeFromString(
            LibraryEntry.serializer(),
            """{"path": "/media/A.mkv", "something_new": {"nested": [1, 2, 3]}}""",
        )

        assertEquals("/media/A.mkv", entry.path)
    }

    @Test
    fun `the statistics are read as the counts they are`() {
        val stats = json.decodeFromString(
            LibraryStats.serializer(),
            """{"success": true, "total": 3, "total_size": 120000000000,
                "hdr_formats": {"Dolby Vision (FEL)": 2, "SDR": 1},
                "resolutions": {"3840x2160": 2, "1920x1080": 1},
                "resolution_classes": {"4K": 2, "FHD": 1},
                "video_codecs": {"H.265": 2, "H.264": 1},
                "audio_codecs": {"Dolby TrueHD Atmos 7.1": 1, "DTS-HD MA 5.1": 2}}""",
        )

        assertEquals(3, stats.total)
        assertEquals(2, stats.hdrFormats["Dolby Vision (FEL)"])
        assertEquals(1, stats.resolutionClasses["FHD"])
    }

    @Test
    fun `scan progress reports what it is doing and what it finished with`() {
        val scanning = json.decodeFromString(
            ScanProgress.serializer(),
            """{"current": 12, "total": 200, "percent": 6, "status": "scanning",
                "filename": "Dune.mkv"}""",
        )
        assertTrue(scanning.isScanning)

        val done = json.decodeFromString(
            ScanProgress.serializer(),
            """{"current": 200, "total": 200, "percent": 100, "status": "done",
                "new_files": 8, "removed_files": 1, "total_files": 940}""",
        )
        assertFalse(done.isScanning)
        assertEquals(8, done.newFiles)
        assertEquals(940, done.totalFiles)
    }
}
