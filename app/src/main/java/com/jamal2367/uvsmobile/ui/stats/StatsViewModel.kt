package com.jamal2367.uvsmobile.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jamal2367.uvsmobile.UvsApplication
import com.jamal2367.uvsmobile.data.model.LibraryStats
import com.jamal2367.uvsmobile.data.remote.ApiFailure
import com.jamal2367.uvsmobile.data.remote.LiveEvent
import com.jamal2367.uvsmobile.util.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class StatsUiState(
    val stats: LibraryStats? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val notConfigured: Boolean = false,
)

/**
 * The library in numbers.
 *
 * `/library/stats` is the cheap call the API exists for: the counts without the
 * megabytes of the library behind them, which is exactly what a dashboard wants.
 */
class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as UvsApplication).container
    private val repository = container.repository

    private val _state = MutableStateFlow(StatsUiState())
    val state: StateFlow<StatsUiState> = _state.asStateFlow()

    init {
        container.settingsRepository.settings
            .onEach { settings ->
                _state.value = _state.value.copy(notConfigured = !settings.isConfigured)
            }
            .launchIn(viewModelScope)

        // A finished scan changes every count on this screen.
        container.liveEvents
            .onEach { event ->
                if (event is LiveEvent.Progress && !event.progress.isScanning) load(silent = true)
            }
            .launchIn(viewModelScope)

        load()
    }

    fun load(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _state.value = _state.value.copy(
                    isLoading = _state.value.stats == null,
                    isRefreshing = _state.value.stats != null,
                    error = null,
                )
            }
            try {
                val stats = repository.stats()
                _state.value = StatsUiState(stats = stats, isLoading = false, isRefreshing = false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = failure.toUserMessage(getApplication()),
                    notConfigured = failure is ApiFailure.NotConfigured,
                )
            }
        }
    }
}
