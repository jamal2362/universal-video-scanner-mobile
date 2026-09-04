package com.jamal2367.uvsmobile.ui.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jamal2367.uvsmobile.UvsApplication
import com.jamal2367.uvsmobile.data.model.LibraryQuery
import com.jamal2367.uvsmobile.data.model.LibraryStats
import com.jamal2367.uvsmobile.data.remote.ApiFailure
import com.jamal2367.uvsmobile.data.remote.LiveEvent
import com.jamal2367.uvsmobile.util.HdrGroup
import com.jamal2367.uvsmobile.util.HdrGroups
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
    /**
     * The grades, counted by their full names.
     *
     * The server counts by the stored format, which files every Dolby Vision
     * title - profile 5, profile 8.1, profile 7 with a layer - under the one
     * heading. These are counted here instead, off a projection of the library.
     * Empty when that projection did not arrive, and then the server's own
     * counts are what the card shows.
     */
    val hdrGroups: List<HdrGroup> = emptyList(),
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
                _state.value = StatsUiState(
                    stats = stats,
                    hdrGroups = _state.value.hdrGroups,
                    isLoading = false,
                    isRefreshing = false,
                )
                loadHdrGroups()
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

    /**
     * Count the grades by the names they are known under.
     *
     * A second call, and the expensive one: the whole library cut down to its
     * three picture fields, which is what it takes to tell a profile 8.1 from a
     * profile 5 - the counts cannot, they only carry the stored format. Asked
     * for with the field list the filter sheet uses, so opening both screens
     * fetches this once and gets a `304` after that.
     *
     * A failure is not reported: the card already has the server's counts on it
     * and they are not wrong, only coarser.
     */
    private suspend fun loadHdrGroups() {
        val groups = try {
            HdrGroups.of(repository.projection(LibraryQuery.VALUE_FIELDS))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            return
        }
        _state.value = _state.value.copy(hdrGroups = groups)
    }
}
