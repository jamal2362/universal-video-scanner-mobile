package com.jamal2367.uvsmobile.ui.files

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jamal2367.uvsmobile.UvsApplication
import com.jamal2367.uvsmobile.data.model.MediaFile
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

/** Which of the media directory's files the list is showing. */
enum class FileFilter { ALL, UNSCANNED, SCANNED }

data class FilesUiState(
    val files: List<MediaFile> = emptyList(),
    val filter: FileFilter = FileFilter.ALL,
    val search: String = "",
    val selected: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isStartingScan: Boolean = false,
    /** The one file being read right now, when a single file was picked out. */
    val scanningPath: String? = null,
    val error: String? = null,
    val message: String? = null,
    val notConfigured: Boolean = false,
) {
    val unscannedCount: Int get() = files.count { !it.scanned }

    /** What the list actually draws, after the filter and the search box. */
    val visibleFiles: List<MediaFile>
        get() {
            val term = search.trim()
            return files.asSequence()
                .filter {
                    when (filter) {
                        FileFilter.ALL -> true
                        FileFilter.UNSCANNED -> !it.scanned
                        FileFilter.SCANNED -> it.scanned
                    }
                }
                .filter { term.isEmpty() || it.name.contains(term, ignoreCase = true) }
                .toList()
        }
}

/**
 * What is in the media directory, and what of it the library already knows.
 *
 * The server sorts the unscanned files first, so the ones still missing are
 * what a reader sees when the screen opens.
 */
class FilesViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as UvsApplication).container
    private val repository = container.repository

    private val _state = MutableStateFlow(FilesUiState())
    val state: StateFlow<FilesUiState> = _state.asStateFlow()

    init {
        container.settingsRepository.settings
            .onEach { settings ->
                _state.value = _state.value.copy(notConfigured = !settings.isConfigured)
            }
            .launchIn(viewModelScope)

        container.liveEvents
            .onEach { event ->
                when {
                    event is LiveEvent.Progress && !event.progress.isScanning -> load(silent = true)
                    event is LiveEvent.FileDeleted -> load(silent = true)
                    else -> Unit
                }
            }
            .launchIn(viewModelScope)

        load()
    }

    fun load(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) {
                _state.value = _state.value.copy(
                    isLoading = _state.value.files.isEmpty(),
                    isRefreshing = _state.value.files.isNotEmpty(),
                    error = null,
                )
            }
            try {
                val files = repository.mediaFiles()
                _state.value = _state.value.copy(
                    files = files,
                    isLoading = false,
                    isRefreshing = false,
                    error = null,
                    // A file that was scanned in the meantime is no longer a
                    // candidate, so it quietly leaves the selection.
                    selected = _state.value.selected.intersect(
                        files.filterNot { it.scanned }.map { it.path }.toSet()
                    ),
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

    fun setFilter(filter: FileFilter) {
        _state.value = _state.value.copy(filter = filter)
    }

    fun setSearch(term: String) {
        _state.value = _state.value.copy(search = term)
    }

    fun toggleSelection(file: MediaFile) {
        val selected = _state.value.selected.toMutableSet()
        if (!selected.add(file.path)) selected.remove(file.path)
        _state.value = _state.value.copy(selected = selected)
    }

    fun selectAllUnscanned() {
        _state.value = _state.value.copy(
            selected = _state.value.visibleFiles.filterNot { it.scanned }.map { it.path }.toSet()
        )
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selected = emptySet())
    }

    /**
     * Queue the selected files.
     *
     * The server answers with how many it took and how many it skipped - a file
     * already in the library is skipped rather than scanned twice - and that is
     * what the reader is told.
     */
    fun scanSelected(formatQueued: (queued: Int, skipped: Int) -> String) {
        val paths = _state.value.selected.toList()
        if (paths.isEmpty()) return

        viewModelScope.launch {
            _state.value = _state.value.copy(isStartingScan = true, error = null)
            try {
                val started = repository.scanFiles(paths)
                _state.value = _state.value.copy(
                    isStartingScan = false,
                    selected = emptySet(),
                    message = formatQueued(started.queued ?: paths.size, started.skipped ?: 0),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                _state.value = _state.value.copy(
                    isStartingScan = false,
                    error = failure.toUserMessage(getApplication()),
                )
            }
        }
    }

    /**
     * Read one file and wait for the answer.
     *
     * The bulk endpoint queues and returns immediately, which is right for
     * hundreds of files and wrong for one: picking a single title out of the
     * list should say what came of it, and `/entries/scan` is the endpoint that
     * answers with the entry itself.
     */
    fun scanOne(path: String, doneMessage: (String) -> String) {
        if (_state.value.scanningPath != null) return

        viewModelScope.launch {
            _state.value = _state.value.copy(scanningPath = path, error = null)
            try {
                val entry = repository.scanEntry(path)
                _state.value = _state.value.copy(
                    scanningPath = null,
                    message = doneMessage(entry.displayTitle),
                )
                load(silent = true)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                _state.value = _state.value.copy(
                    scanningPath = null,
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
}
