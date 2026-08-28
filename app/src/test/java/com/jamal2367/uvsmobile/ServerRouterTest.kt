package com.jamal2367.uvsmobile

import com.jamal2367.uvsmobile.data.prefs.AppSettings
import com.jamal2367.uvsmobile.data.prefs.ServerConfig
import com.jamal2367.uvsmobile.data.remote.ReachabilityMemory
import com.jamal2367.uvsmobile.data.remote.ServerRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A note that answers whatever it was last told, as the stored one does. */
private class FakeMemory(private var noted: String? = null) : ReachabilityMemory {
    var forgotten = false
        private set

    override fun lastReachable(): String? = noted
    override fun remember(baseUrl: String) {
        noted = baseUrl
    }

    override fun forget() {
        noted = null
        forgotten = true
    }
}

class ServerRouterTest {

    private val local = ServerConfig(enabled = true, host = "192.168.1.10", port = 2367)
    private val remote = ServerConfig(enabled = true, host = "uvs.example.com", port = 443, useHttps = true)
    private val settings = AppSettings(primary = local, secondary = remote)

    @Test
    fun `without a note the configured order stands`() {
        val router = ServerRouter(FakeMemory())
        router.update(settings)

        assertEquals(listOf(local.baseUrl, remote.baseUrl), router.candidates().map { it.baseUrl })
    }

    @Test
    fun `a note from the last launch is tried first`() {
        val router = ServerRouter(FakeMemory(remote.baseUrl))
        router.update(settings)

        assertEquals(listOf(remote.baseUrl, local.baseUrl), router.candidates().map { it.baseUrl })
    }

    @Test
    fun `a note about an address that is no longer configured changes nothing`() {
        val router = ServerRouter(FakeMemory("http://192.168.9.9:2367"))
        router.update(settings)

        assertEquals(listOf(local.baseUrl, remote.baseUrl), router.candidates().map { it.baseUrl })
    }

    @Test
    fun `what answered in this session outranks the note`() {
        val router = ServerRouter(FakeMemory(remote.baseUrl))
        router.update(settings)
        router.markReachable(local)

        assertEquals(listOf(local.baseUrl, remote.baseUrl), router.candidates().map { it.baseUrl })
    }

    @Test
    fun `a successful call is noted for the next launch`() {
        val memory = FakeMemory()
        val router = ServerRouter(memory)
        router.update(settings)
        router.markReachable(remote)

        assertEquals(remote.baseUrl, memory.lastReachable())
    }

    @Test
    fun `nothing answering leaves the note alone`() {
        val memory = FakeMemory(remote.baseUrl)
        val router = ServerRouter(memory)
        router.update(settings)
        router.markUnreachable()

        assertEquals(listOf(remote.baseUrl, local.baseUrl), router.candidates().map { it.baseUrl })
    }

    @Test
    fun `the first stored configuration is not a change of address`() {
        val memory = FakeMemory(remote.baseUrl)
        val router = ServerRouter(memory)

        // What every launch does: the defaults the router is built with are
        // replaced by what was stored. That must not read as somebody moving
        // the server, or the note is gone before the first request follows it.
        router.update(settings)

        assertEquals(false, memory.forgotten)
        assertEquals(listOf(remote.baseUrl, local.baseUrl), router.candidates().map { it.baseUrl })
    }

    @Test
    fun `changing an address drops the note it was about`() {
        val memory = FakeMemory(remote.baseUrl)
        val router = ServerRouter(memory)
        router.update(settings)
        router.markReachable(remote)

        val moved = remote.copy(host = "other.example.com")
        router.update(settings.copy(secondary = moved))

        assertEquals(true, memory.forgotten)
        assertNull(router.activeServer.value)
        assertEquals(listOf(local.baseUrl, moved.baseUrl), router.candidates().map { it.baseUrl })
    }
}
