package com.jamal2367.uvsmobile

import com.jamal2367.uvsmobile.data.prefs.ServerConfig
import com.jamal2367.uvsmobile.util.PosterUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where a poster comes from.
 *
 * An entry carries either a cached name this server can serve or the remote URL
 * the image was never cached from - telling those apart wrongly means either a
 * grid of holes or a request to a host that knows nothing about it.
 */
class PosterUrlsTest {

    private val server = ServerConfig(
        enabled = true,
        host = "192.168.1.10",
        port = 2367,
        token = "s3cret",
    )

    @Test
    fun `a cached poster is served through the api at the width the grid needs`() {
        val url = PosterUrls.forEntry("/poster/12345.jpg", server, width = 320)

        assertEquals("http://192.168.1.10:2367/api/v1/posters/12345.jpg?w=320&token=s3cret", url)
    }

    @Test
    fun `a width the server does not produce is left off rather than refused`() {
        val url = PosterUrls.forEntry("/poster/12345.jpg", server, width = 321)

        assertTrue(url!!.contains("/api/v1/posters/12345.jpg"))
        assertTrue(!url.contains("w="))
    }

    @Test
    fun `an instance without a token asks without one`() {
        val url = PosterUrls.forEntry("/poster/a.jpg", server.copy(token = ""), width = null)

        assertEquals("http://192.168.1.10:2367/api/v1/posters/a.jpg", url)
    }

    @Test
    fun `an image that could not be cached is fetched from its own host`() {
        val remote = "https://image.tmdb.org/t/p/w500/abc.jpg"

        assertEquals(remote, PosterUrls.forEntry(remote, server, width = 320))
        // Even with no server configured - it does not need one.
        assertEquals(remote, PosterUrls.forEntry(remote, null, width = 320))
    }

    @Test
    fun `nothing to show is null rather than a broken request`() {
        assertNull(PosterUrls.forEntry(null, server, width = 320))
        assertNull(PosterUrls.forEntry("   ", server, width = 320))
        // A cached name needs a server to serve it.
        assertNull(PosterUrls.forEntry("/poster/a.jpg", null, width = 320))
        assertNull(PosterUrls.forEntry("/poster/a.jpg", server.copy(host = ""), width = 320))
    }
}
