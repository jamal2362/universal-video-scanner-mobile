package com.jamal2367.uvsmobile.data.remote

import com.jamal2367.uvsmobile.data.prefs.AppSettings
import com.jamal2367.uvsmobile.data.prefs.ConnectionMode
import com.jamal2367.uvsmobile.data.prefs.ServerConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which of the two configured instances a request goes to.
 *
 * The interesting case is a phone that leaves the flat: the local address stops
 * resolving and every call would fail, so in [ConnectionMode.AUTO] the router
 * hands out both and lets the caller walk down the list. The one that last
 * answered is remembered and tried first, so the fallback costs one failed
 * connection rather than one per request for the rest of the session.
 */
class ServerRouter {

    private val _settings = MutableStateFlow(AppSettings())

    private val _activeServer = MutableStateFlow<ActiveServer?>(null)

    /** Which server the last successful call went to, for the status line. */
    val activeServer: StateFlow<ActiveServer?> = _activeServer.asStateFlow()

    val settings: AppSettings get() = _settings.value

    fun update(settings: AppSettings) {
        val previous = _settings.value
        _settings.value = settings
        // A changed address is a different server: what answered before says
        // nothing about what will answer now.
        if (previous.primary != settings.primary ||
            previous.secondary != settings.secondary ||
            previous.connectionMode != settings.connectionMode
        ) {
            _activeServer.value = null
        }
    }

    /**
     * The servers to try, best guess first.
     *
     * Empty when nothing is configured - the caller turns that into
     * [ApiFailure.NotConfigured] rather than a connection error, because the
     * two want completely different words on screen.
     */
    fun candidates(): List<ServerConfig> {
        val servers = _settings.value.servers()
        val lastGood = _activeServer.value?.config ?: return servers
        val index = servers.indexOfFirst { it.baseUrl == lastGood.baseUrl }
        if (index <= 0) return servers
        return listOf(servers[index]) + servers.filterIndexed { i, _ -> i != index }
    }

    fun markReachable(config: ServerConfig) {
        _activeServer.value = ActiveServer(config, isPrimary(config))
    }

    fun markUnreachable() {
        _activeServer.value = null
    }

    private fun isPrimary(config: ServerConfig): Boolean =
        _settings.value.primary.isUsable && _settings.value.primary.baseUrl == config.baseUrl

    data class ActiveServer(val config: ServerConfig, val isPrimary: Boolean)
}
