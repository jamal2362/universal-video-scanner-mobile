package com.jamal2367.uvsmobile.ui.scan

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jamal2367.uvsmobile.UvsApplication
import com.jamal2367.uvsmobile.data.model.ScanProgress
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

/** Whether the phone is actually being told about changes as they happen. */
enum class LiveStatus { CONNECTING, CONNECTED, OFFLINE, DISABLED }

/** One thing that happened, for the activity list. */
data class ActivityItem(
    val kind: Kind,
    val name: String,
    val at: Long = System.currentTimeMillis(),
) {
    enum class Kind { UPDATED, DELETED }
}

data class ScanUiState(
    val progress: ScanProgress = ScanProgress(),
    val running: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isStarting: Boolean = false,
    val isCancelling: Boolean = false,
    val isClearing: Boolean = false,
    val live: LiveStatus = LiveStatus.CONNECTING,
    val activity: List<ActivityItem> = emptyList(),
    val error: String? = null,
    val message: String? = null,
    val notConfigured: Boolean = false,
)

/**
 * The scan: what it is doing, and the three ways to change that.
 *
 * `/scan/status` is asked once when the screen opens - the stream only reports
 * what happens next, so without it a screen joining mid-scan would show nothing
 * until the next file finished - and everything after that arrives live.
 */
class ScanViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as UvsApplication).container
    private val repository = container.repository

    private val _state = MutableStateFlow(ScanUiState())
    val state: StateFlow<ScanUiState> = _state.asStateFlow()

    init {
        container.settingsRepository.settings
            .onEach { settings ->
                _state.value = _state.value.copy(
                    notConfigured = !settings.isConfigured,
                    live = if (settings.liveUpdates) _state.value.live else LiveStatus.DISABLED,
                )
                if (settings.liveUpdates && _state.value.live == LiveStatus.DISABLED) {
                    _state.value = _state.value.copy(live = LiveStatus.CONNECTING)
                }
            }
            .launchIn(viewModelScope)

        container.liveEvents
            .onEach(::onLiveEvent)
            .launchIn(viewModelScope)

        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isRefreshing = !_state.value.isLoading, error = null)
            try {
                val status = repository.scanStatus()
                _state.value = _state.value.copy(
                    progress = status.scan,
                    running = status.running,
                    isLoading = false,
                    isRefreshing = false,
                    error = null,
                )
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

    fun startFullScan(startedMessage: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isStarting = true, error = null)
            try {
                repository.startFullScan()
                _state.value = _state.value.copy(
                    isStarting = false,
                    running = true,
                    message = startedMessage,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                _state.value = _state.value.copy(
                    isStarting = false,
                    error = failure.toUserMessage(getApplication()),
                )
            }
        }
    }

    /**
     * Ask the running scan to stop.
     *
     * The files already being probed are seen through, so nothing is left half
     * written; what was scanned stays in the library.
     */
    fun cancelScan() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isCancelling = true, error = null)
            try {
                repository.cancelScan()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                _state.value = _state.value.copy(error = failure.toUserMessage(getApplication()))
            } finally {
                _state.value = _state.value.copy(isCancelling = false)
            }
        }
    }

    fun clearDatabase(doneMessage: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isClearing = true, error = null)
            try {
                repository.clearDatabase()
                _state.value = _state.value.copy(
                    isClearing = false,
                    message = doneMessage,
                    activity = emptyList(),
                )
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                _state.value = _state.value.copy(
                    isClearing = false,
                    error = failure.toUserMessage(getApplication()),
                )
            }
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun onLiveEvent(event: LiveEvent) {
        val current = _state.value
        _state.value = when (event) {
            is LiveEvent.Connected -> current.copy(live = LiveStatus.CONNECTED)
            is LiveEvent.Disconnected -> current.copy(live = LiveStatus.OFFLINE)
            is LiveEvent.NotConfigured -> current.copy(live = LiveStatus.OFFLINE)

            is LiveEvent.State -> current.copy(
                progress = event.progress,
                running = event.progress.isScanning,
                isLoading = false,
                live = LiveStatus.CONNECTED,
            )

            is LiveEvent.Progress -> current.copy(
                progress = event.progress,
                running = event.progress.isScanning,
                isLoading = false,
                live = LiveStatus.CONNECTED,
            )

            is LiveEvent.EntryUpdated -> current.copy(
                live = LiveStatus.CONNECTED,
                activity = current.activity.prepend(
                    ActivityItem(
                        kind = ActivityItem.Kind.UPDATED,
                        name = event.event.filePath.substringAfterLast('/'),
                    )
                ),
            )

            is LiveEvent.FileDeleted -> current.copy(
                live = LiveStatus.CONNECTED,
                activity = current.activity.prepend(
                    ActivityItem(
                        kind = ActivityItem.Kind.DELETED,
                        name = (event.event.affectedPath ?: event.event.filename.orEmpty())
                            .substringAfterLast('/'),
                    )
                ),
            )
        }
    }

    /** Newest first, and bounded: a scan of thousands must not grow the list forever. */
    private fun List<ActivityItem>.prepend(item: ActivityItem): List<ActivityItem> =
        (listOf(item) + this).take(ACTIVITY_LIMIT)

    private companion object {
        const val ACTIVITY_LIMIT = 60
    }
}
