package com.jamal2367.uvsmobile.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jamal2367.uvsmobile.R
import com.jamal2367.uvsmobile.UvsApplication
import com.jamal2367.uvsmobile.data.prefs.AppSettings
import com.jamal2367.uvsmobile.data.prefs.ConnectionMode
import com.jamal2367.uvsmobile.data.prefs.ServerConfig
import com.jamal2367.uvsmobile.data.prefs.ThemeMode
import com.jamal2367.uvsmobile.data.remote.ConnectionTestResult
import com.jamal2367.uvsmobile.util.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** Which of the two stored addresses an edit is about. */
enum class ServerSlot { PRIMARY, SECONDARY }

/** How the last connection test for one slot went. */
sealed interface TestState {
    data object Idle : TestState
    data object Running : TestState
    data class Ok(val apiVersion: String) : TestState
    data class Failed(val message: String) : TestState
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val loaded: Boolean = false,
    val primaryTest: TestState = TestState.Idle,
    val secondaryTest: TestState = TestState.Idle,
    val activeServerLabel: String? = null,
    val activeIsPrimary: Boolean = false,
)

/**
 * The settings, including the two addresses the app can reach an instance at.
 *
 * Every change is written through to storage immediately - there is no save
 * button to forget - and the router picks it up from there, so the next request
 * already goes to the new address.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as UvsApplication).container
    private val repository = container.settingsRepository

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init {
        repository.settings
            .onEach { settings ->
                _state.value = _state.value.copy(settings = settings, loaded = true)
            }
            .launchIn(viewModelScope)

        container.router.activeServer
            .onEach { active ->
                _state.value = _state.value.copy(
                    activeServerLabel = active?.config?.label,
                    activeIsPrimary = active?.isPrimary ?: false,
                )
            }
            .launchIn(viewModelScope)
    }

    fun updateServer(slot: ServerSlot, transform: (ServerConfig) -> ServerConfig) {
        val current = _state.value.settings
        val updated = transform(if (slot == ServerSlot.PRIMARY) current.primary else current.secondary)

        // The stored copy is the one that matters, but the screen would flicker
        // back to the old text for a frame while the write lands - so the state
        // is moved first and the write follows.
        _state.value = _state.value.copy(
            settings = if (slot == ServerSlot.PRIMARY) {
                current.copy(primary = updated)
            } else {
                current.copy(secondary = updated)
            },
            primaryTest = if (slot == ServerSlot.PRIMARY) TestState.Idle else _state.value.primaryTest,
            secondaryTest = if (slot == ServerSlot.SECONDARY) TestState.Idle else _state.value.secondaryTest,
        )

        viewModelScope.launch {
            when (slot) {
                ServerSlot.PRIMARY -> repository.setPrimary(updated)
                ServerSlot.SECONDARY -> repository.setSecondary(updated)
            }
        }
    }

    fun setConnectionMode(mode: ConnectionMode) {
        viewModelScope.launch { repository.setConnectionMode(mode) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { repository.setDynamicColor(enabled) }
    }

    fun setPosterWidth(width: Int) {
        viewModelScope.launch { repository.setPosterWidth(width) }
    }

    fun setPageSize(size: Int) {
        viewModelScope.launch { repository.setPageSize(size) }
    }

    fun setLiveUpdates(enabled: Boolean) {
        viewModelScope.launch { repository.setLiveUpdates(enabled) }
    }

    fun testConnection(slot: ServerSlot) {
        val settings = _state.value.settings
        val server = if (slot == ServerSlot.PRIMARY) settings.primary else settings.secondary

        if (server.host.isBlank()) {
            setTest(slot, TestState.Failed(getApplication<Application>().getString(R.string.settings_invalid_host)))
            return
        }
        if (server.port !in 1..65535) {
            setTest(slot, TestState.Failed(getApplication<Application>().getString(R.string.settings_invalid_port)))
            return
        }

        viewModelScope.launch {
            setTest(slot, TestState.Running)
            try {
                // Tested as if it were switched on, so a reader can check an
                // address before committing to it.
                val result = container.connectionTester.test(server.copy(enabled = true))
                setTest(
                    slot,
                    when (result) {
                        is ConnectionTestResult.Reachable -> TestState.Ok(result.apiVersion)
                        is ConnectionTestResult.Refused ->
                            TestState.Failed(result.failure.toUserMessage(getApplication()))
                    }
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                setTest(slot, TestState.Failed(failure.toUserMessage(getApplication())))
            }
        }
    }

    private fun setTest(slot: ServerSlot, test: TestState) {
        _state.value = when (slot) {
            ServerSlot.PRIMARY -> _state.value.copy(primaryTest = test)
            ServerSlot.SECONDARY -> _state.value.copy(secondaryTest = test)
        }
    }

    /**
     * What a person types is not always what a URL needs: a pasted
     * `http://192.168.1.10:2367/` has to end up as a host on its own.
     */
    fun sanitizeHost(raw: String): String {
        val withoutScheme = raw.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
            .substringBefore('?')
            .trim()

        // A single colon followed by digits is a port, and belongs in the port
        // field - an IPv6 literal has several and keeps all of them.
        if (withoutScheme.count { it == ':' } == 1) {
            val port = withoutScheme.substringAfter(':')
            if (port.isNotEmpty() && port.all { it.isDigit() }) {
                return withoutScheme.substringBefore(':')
            }
        }
        return withoutScheme
    }

    /** A port pasted along with the host, so `10.0.0.5:8080` fills both fields. */
    fun portFromHost(raw: String): Int? {
        val hostPart = raw.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .substringBefore('/')
        // An IPv6 literal is full of colons and carries no port here.
        if (hostPart.count { it == ':' } != 1) return null
        return hostPart.substringAfter(':').toIntOrNull()?.takeIf { it in 1..65535 }
    }

    /** Whether a pasted address said https, so the switch can follow it. */
    fun schemeFromHost(raw: String): Boolean? = when {
        raw.trim().startsWith("https://") -> true
        raw.trim().startsWith("http://") -> false
        else -> null
    }
}
