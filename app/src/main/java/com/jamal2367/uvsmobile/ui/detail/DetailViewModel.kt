package com.jamal2367.uvsmobile.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.jamal2367.uvsmobile.UvsApplication
import com.jamal2367.uvsmobile.data.model.LibraryEntry
import com.jamal2367.uvsmobile.data.remote.LiveEvent
import com.jamal2367.uvsmobile.ui.navigation.Routes
import com.jamal2367.uvsmobile.util.toUserMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class DetailUiState(
    val entry: LibraryEntry? = null,
    val isLoading: Boolean = true,
    val isWorking: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val deleted: Boolean = false,
    val posterWidth: Int = 640,
)

/**
 * One title, and the three things that can be done to it.
 *
 * The entry is fetched by path from `/entries` rather than carried over from
 * the list: the list asks for a dozen fields, and this screen shows all of them.
 */
class DetailViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {

    private val container = (application as UvsApplication).container
    private val repository = container.repository

    private val filePath: String =
        Routes.decodePath(savedStateHandle.get<String>(Routes.ARG_PATH).orEmpty())

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    init {
        container.settingsRepository.settings
            .onEach { _state.value = _state.value.copy(posterWidth = it.posterWidth) }
            .launchIn(viewModelScope)

        // A rescan started elsewhere - from the scan screen, from the web
        // interface, by the file watcher - is worth picking up here too.
        container.liveEvents
            .onEach { event ->
                when {
                    event is LiveEvent.EntryUpdated && event.event.filePath == filePath -> load()
                    event is LiveEvent.FileDeleted && event.event.affectedPath == filePath ->
                        _state.value = _state.value.copy(deleted = true)

                    else -> Unit
                }
            }
            .launchIn(viewModelScope)

        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = _state.value.entry == null, error = null)
            try {
                val entry = repository.entry(filePath)
                _state.value = _state.value.copy(entry = entry, isLoading = false, error = null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = failure.toUserMessage(getApplication()),
                )
            }
        }
    }

    /** Read the file from scratch, including every online lookup. */
    fun rescan(doneMessage: String) {
        runAction(doneMessage) { repository.rescanEntry(filePath) }
    }

    fun delete(doneMessage: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isWorking = true, error = null)
            try {
                repository.deleteEntry(filePath)
                _state.value = _state.value.copy(
                    isWorking = false,
                    deleted = true,
                    message = doneMessage,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                _state.value = _state.value.copy(
                    isWorking = false,
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

    private fun runAction(doneMessage: String, block: suspend () -> LibraryEntry) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isWorking = true, error = null)
            try {
                val entry = block()
                _state.value = _state.value.copy(
                    entry = entry,
                    isWorking = false,
                    message = doneMessage,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                _state.value = _state.value.copy(
                    isWorking = false,
                    error = failure.toUserMessage(getApplication()),
                )
            }
        }
    }
}
