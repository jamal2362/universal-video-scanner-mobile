package com.jamal2367.uvsmobile

import com.jamal2367.uvsmobile.data.model.LibraryEntry
import com.jamal2367.uvsmobile.data.prefs.ServerConfig
import com.jamal2367.uvsmobile.util.Artwork
import com.jamal2367.uvsmobile.util.PosterUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a title's artwork comes from.
 *
 * An entry carries two images - the 16:9 backdrop the web interface shows and
 * the upright cover this app is laid out for - and each is either a name this
 * server can serve or the remote URL it was never cached from. Confusing any of
 * those four cases means a grid of holes, a cropped backdrop where a cover
 * belongs, or a request to a host that knows nothing about it.
 */
class PosterUrlsTest {

    private val server = ServerConfig(
        enabled = true,
        host = "192.168.1.10",
        port = 2367,
        token = "s3cret",
    )

    private val entry = LibraryEntry(
        path = "/media/Dune.mkv",
        posterUrl = "/poster/tmdb_438631.jpg",
        portraitUrl = "/poster/tmdb_portrait_438631.jpg",
    )

    @Test
    fun `a cached image is served through the api at the width the layout needs`() {
        val url = PosterUrls.imageUrl("/poster/12345.jpg", server, width = 320)

        assertEquals("http://192.168.1.10:2367/api/v1/posters/12345.jpg?w=320&token=s3cret", url)
    }

    @Test
    fun `a width the server does not produce is left off rather than refused`() {
        val url = PosterUrls.imageUrl("/poster/12345.jpg", server, width = 321)

        assertTrue(url!!.contains("/api/v1/posters/12345.jpg"))
        assertTrue(!url.contains("w="))
    }

    @Test
    fun `an instance without a token asks without one`() {
        val url = PosterUrls.imageUrl("/poster/a.jpg", server.copy(token = ""), width = null)

        assertEquals("http://192.168.1.10:2367/api/v1/posters/a.jpg", url)
    }

    @Test
    fun `an image that could not be cached is fetched from its own host`() {
        val remote = "https://image.tmdb.org/t/p/w500/abc.jpg"

        assertEquals(remote, PosterUrls.imageUrl(remote, server, width = 320))
        // Even with no server configured - it does not need one.
        assertEquals(remote, PosterUrls.imageUrl(remote, null, width = 320))
    }

    @Test
    fun `nothing to show is null rather than a broken request`() {
        assertNull(PosterUrls.imageUrl(null, server, width = 320))
        assertNull(PosterUrls.imageUrl("   ", server, width = 320))
        // A cached name needs a server to serve it.
        assertNull(PosterUrls.imageUrl("/poster/a.jpg", null, width = 320))
        assertNull(PosterUrls.imageUrl("/poster/a.jpg", server.copy(host = ""), width = 320))
    }

    @Test
    fun `a tile asks for the cover, a header for the backdrop`() {
        val cover = PosterUrls.forEntry(entry, server, width = 320)
        val backdrop = PosterUrls.forEntry(entry, server, width = 640, artwork = Artwork.LANDSCAPE)

        assertTrue(cover!!.contains("tmdb_portrait_438631.jpg"))
        assertTrue(cover.contains("w=320"))
        assertTrue(backdrop!!.contains("tmdb_438631.jpg"))
        assertTrue(backdrop.contains("w=640"))
    }

    @Test
    fun `a title with no cover falls back to its backdrop`() {
        val withoutCover = entry.copy(portraitUrl = null)

        assertTrue(
            PosterUrls.forEntry(withoutCover, server, width = 320)!!
                .contains("tmdb_438631.jpg")
        )
    }

    @Test
    fun `a backdrop is never stood in for by a cover`() {
        val coverOnly = entry.copy(posterUrl = null)

        // A 2:3 cover letterboxed into a 16:9 header looks worse than no header.
        assertNull(PosterUrls.forEntry(coverOnly, server, width = 640, artwork = Artwork.LANDSCAPE))
        assertTrue(
            PosterUrls.forEntry(coverOnly, server, width = 320)!!
                .contains("tmdb_portrait_438631.jpg")
        )
    }

    @Test
    fun `an entry with neither image has nothing to show`() {
        val bare = LibraryEntry(path = "/media/Home Video.mkv")

        assertNull(PosterUrls.forEntry(bare, server, width = 320))
        assertNull(PosterUrls.forEntry(bare, server, width = 640, artwork = Artwork.LANDSCAPE))
    }
}
