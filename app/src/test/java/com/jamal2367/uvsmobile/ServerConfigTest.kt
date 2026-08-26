package com.jamal2367.uvsmobile

import com.jamal2367.uvsmobile.data.prefs.AppSettings
import com.jamal2367.uvsmobile.data.prefs.ConnectionMode
import com.jamal2367.uvsmobile.data.prefs.ServerConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules that decide which of the two addresses a request goes to - the one
 * piece of behaviour a reader would notice immediately if it were wrong.
 */
class ServerConfigTest {

    private val local = ServerConfig(
        enabled = true,
        host = "192.168.1.10",
        port = 2367,
        token = "local-token",
    )

    private val remote = ServerConfig(
        enabled = true,
        useHttps = true,
        host = "scanner.example.com",
        port = 443,
        token = "remote-token",
    )

    @Test
    fun `base url carries scheme host and port`() {
        assertEquals("http://192.168.1.10:2367", local.baseUrl)
        assertEquals("https://scanner.example.com:443", remote.baseUrl)
    }

    @Test
    fun `a server without a host is not worth a request`() {
        assertFalse(local.copy(host = "  ").isUsable)
        assertFalse(local.copy(enabled = false).isUsable)
        assertFalse(local.copy(port = 0).isUsable)
        assertTrue(local.isUsable)
    }

    @Test
    fun `automatic mode tries the local address first and the remote one after`() {
        val settings = AppSettings(
            primary = local,
            secondary = remote,
            connectionMode = ConnectionMode.AUTO,
        )
        assertEquals(listOf(local, remote), settings.servers())
    }

    @Test
    fun `a mode that names one address never reaches for the other`() {
        val settings = AppSettings(primary = local, secondary = remote)

        assertEquals(
            listOf(local),
            settings.copy(connectionMode = ConnectionMode.PRIMARY_ONLY).servers(),
        )
        assertEquals(
            listOf(remote),
            settings.copy(connectionMode = ConnectionMode.SECONDARY_ONLY).servers(),
        )
    }

    @Test
    fun `an address that is not filled in drops out of the list`() {
        val settings = AppSettings(
            primary = local.copy(host = ""),
            secondary = remote,
            connectionMode = ConnectionMode.AUTO,
        )
        assertEquals(listOf(remote), settings.servers())
        assertTrue(settings.isConfigured)
        assertFalse(AppSettings().isConfigured)
    }
}
