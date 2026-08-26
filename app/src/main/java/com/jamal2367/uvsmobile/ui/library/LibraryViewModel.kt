package com.jamal2367.uvsmobile.ui.library

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jamal2367.uvsmobile.UvsApplication
import com.jamal2367.uvsmobile.data.model.FilterField
import com.jamal2367.uvsmobile.data.model.LibraryEntry
import com.jamal2367.uvsmobile.data.model.LibraryQuery
import com.jamal2367.uvsmobile.data.model.RangeField
import com.jamal2367.uvsmobile.data.model.RangeValue
import com.jamal2367.uvsmobile.data.model.SortOption
import com.jamal2367.uvsmobile.data.model.SortOrder
import com.jamal2367.uvsmobile.data.prefs.AppSettings
import com.jamal2367.uvsmobile.data.prefs.LibraryLayout
import com.jamal2367.uvsmobile.data.remote.ApiFailure
import com.jamal2367.uvsmobile.data.remote.LiveEvent
import com.jamal2367.uvsmobile.util.toUserMessage
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/** What the library screen is showing right now. */
data class LibraryUiState(
    val entries: List<LibraryEntry> = emptyList(),
    val total: Int = 0,
    val query: LibraryQuery = LibraryQuery(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
    val notConfigured: Boolean = false,
    val filterOptions: FilterOptions = FilterOptions(),
    val layout: LibraryLayout = LibraryLayout.GRID,
    val posterWidth: Int = 320,
)

/**
 * The values the library actually contains, per filterable field.
 *
 * Read off `/library/stats` rather than guessed: offering "VP8" to someone
 * whose library has never seen one is a dead end, and the counts are one cheap
 * call rather than a walk over the library.
 */
data class FilterOptions(
    val hdrFormats: List<String> = emptyList(),
    val resolutions: List<String> = emptyList(),
    val resolutionClasses: List<String> = emptyList(),
    val videoCodecs: List<String> = emptyList(),
    val audioCodecs: List<String> = emptyList(),
    val hdrDetails: List<String> = emptyList(),
    val elTypes: List<String> = emptyList(),
    val videoEncoders: List<String> = emptyList(),
    val cmVersions: List<String> = emptyList(),
    /**
     * Whether the values above were actually read off the library.
     *
     * Until they were, a field with no values falls back to a text box; after
     * they were, an empty list means the library holds nothing to filter by and
     * the field is left out of the sheet altogether.
     */
    val loaded: Boolean = false,
) {
    fun forField(field: FilterField): List<String> = when (field) {
        FilterField.HDR_FORMAT -> hdrFormats
        FilterField.RESOLUTION -> resolutions
        FilterField.RESOLUTION_CLASS -> resolutionClasses
        FilterField.VIDEO_CODEC -> videoCodecs
        FilterField.AUDIO_CODEC -> audioCodecs
        FilterField.HDR_DETAIL -> hdrDetails
        FilterField.EL_TYPE -> elTypes
        FilterField.VIDEO_ENCODER -> videoEncoders
        FilterField.DV_CM_VERSION -> cmVersions
    }
}

/**
 * The library screen's state.
 *
 * Every question - the search, the filters, the order, the page - is asked of
 * the server, which is the whole point of the API's query parameters: a
 * thousand titles never travel to the phone so that twelve of them can be shown.
 */
@OptIn(FlowPreview::class)
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as UvsApplication).container
    private val repository = container.repository

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    /** Coalesces the burst of events a running scan produces into one reload. */
    private val reloadRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private var loadJob: Job? = null
    private var settings: AppSettings = AppSettings()

    /**
     * The newest change stamp this screen has seen.
     *
     * What `updated_since` is asked with: a scan of a thousand files publishes a
     * thousand events, and answering each of them with a fresh page would move
     * megabytes to change a dozen rows.
     */
    private var lastSyncStamp: Double = 0.0

    init {
        container.settingsRepository.settings
            .onEach { updated ->
                val wasConfigured = settings.isConfigured
                val serversChanged = settings.primary != updated.primary ||
                    settings.secondary != updated.secondary ||
                    settings.connectionMode != updated.connectionMode
                settings = updated
                _state.value = _state.value.copy(
                    layout = updated.libraryLayout,
                    posterWidth = updated.posterWidth,
                    notConfigured = !updated.isConfigured,
                )
                if ((!wasConfigured && updated.isConfigured) || serversChanged) {
                    refresh()
                    // The first attempt runs before the settings have been read,
                    // so the values the filter sheet offers have to be asked for
                    // again once there is somewhere to ask.
                    loadFilterOptions()
                }
            }
            .launchIn(viewModelScope)

        container.liveEvents
            .onEach { event ->
                when (event) {
                    // Only the fact that something changed is delivered, never
                    // the record, so the screen asks for what it needs itself.
                    is LiveEvent.EntryUpdated, is LiveEvent.FileDeleted ->
                        reloadRequests.tryEmit(Unit)

                    // A finished scan can have added entries this window never
                    // held, so that one is worth a fresh page rather than a patch.
                    is LiveEvent.Progress ->
                        if (!event.progress.isScanning) refresh(silent = true)

                    else -> Unit
                }
            }
            .launchIn(viewModelScope)

        reloadRequests
            .debounce(RELOAD_DEBOUNCE_MS)
            .onEach { syncChangedEntries() }
            .launchIn(viewModelScope)

        refresh()
        loadFilterOptions()
    }

    fun setSearch(term: String) {
        updateQuery { it.copy(search = term) }
    }

    fun setSort(sort: SortOption) {
        updateQuery { it.copy(sort = sort) }
    }

    fun setOrder(order: SortOrder) {
        updateQuery { it.copy(order = order) }
    }

    fun setFilter(field: FilterField, value: String?) {
        updateQuery { query ->
            val filters = query.filters.toMutableMap()
            if (value.isNullOrBlank()) filters.remove(field) else filters[field] = value
            query.copy(filters = filters)
        }
    }

    fun setRange(field: RangeField, range: RangeValue) {
        updateQuery { query ->
            val ranges = query.ranges.toMutableMap()
            if (range.isEmpty) ranges.remove(field) else ranges[field] = range
            query.copy(ranges = ranges)
        }
    }

    fun clearNarrowing() {
        updateQuery { it.copy(filters = emptyMap(), ranges = emptyMap(), search = "") }
    }

    fun setLayout(layout: LibraryLayout) {
        viewModelScope.launch { container.settingsRepository.setLibraryLayout(layout) }
    }

    fun refresh(silent: Boolean = false) {
        val query = _state.value.query
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (!silent) {
                _state.value = _state.value.copy(
                    isRefreshing = _state.value.entries.isNotEmpty(),
                    isLoading = _state.value.entries.isEmpty(),
                    error = null,
                )
            }
            try {
                val page = repository.library(query, settings.pageSize, offset = 0)
                rememberSyncStamp(page.files)
                _state.value = _state.value.copy(
                    entries = page.files,
                    total = page.total,
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    endReached = page.files.size >= page.total,
                    error = null,
                    notConfigured = false,
                )
            } catch (failure: Throwable) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                _state.value = _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    isLoadingMore = false,
                    error = failure.toUserMessage(getApplication()),
                    notConfigured = failure is ApiFailure.NotConfigured,
                )
            }
        }
    }

    /**
     * Fetch the next window.
     *
     * `total` counts every match before the window, so the screen knows when it
     * has them all without asking for an empty page to find out.
     */
    fun loadMore() {
        val current = _state.value
        if (current.isLoadingMore || current.endReached || current.isLoading) return
        if (current.entries.isEmpty()) return

        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingMore = true)
            try {
                val page = repository.library(
                    query = current.query,
                    limit = settings.pageSize,
                    offset = current.entries.size,
                )
                // A scan running underneath can shift what lands in a window,
                // so a page may repeat an entry the list already holds. Two rows
                // with the same key is a crash, and one title twice is wrong
                // anyway.
                val known = current.entries.mapTo(HashSet()) { it.path }
                val fresh = page.files.filterNot { it.path in known }
                val combined = current.entries + fresh
                rememberSyncStamp(page.files)
                _state.value = _state.value.copy(
                    entries = combined,
                    total = page.total,
                    isLoadingMore = false,
                    // An empty page also ends it: the library may have shrunk
                    // between two requests, and asking forever would not help.
                    endReached = page.files.isEmpty() || combined.size >= page.total,
                )
            } catch (failure: Throwable) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                _state.value = _state.value.copy(
                    isLoadingMore = false,
                    error = failure.toUserMessage(getApplication()),
                )
            }
        }
    }

    /**
     * Bring the rows on screen up to date without fetching them again.
     *
     * `updated_since` answers with what was written since the last sync - during
     * a scan that is a handful of entries rather than the whole window - and the
     * ones already on screen are replaced in place, so the list neither jumps
     * nor reorders under the reader's finger.
     */
    private suspend fun syncChangedEntries() {
        val current = _state.value
        if (current.entries.isEmpty() || lastSyncStamp <= 0.0) {
            refresh(silent = true)
            return
        }

        try {
            val page = repository.library(
                query = current.query.copy(updatedSince = lastSyncStamp),
                limit = null,
                offset = 0,
            )
            if (page.files.isEmpty()) return

            rememberSyncStamp(page.files)
            val changed = page.files.associateBy { it.path }
            val patched = current.entries.map { changed[it.path] ?: it }
            // Nothing on screen was among them - the changes are elsewhere in
            // the library, and the next page or refresh will pick them up.
            if (patched == current.entries) return

            _state.value = _state.value.copy(entries = patched)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (failure: Throwable) {
            // A failed sync is not worth an error on screen: the rows on it are
            // still the ones the server last handed over.
        }
    }

    private fun rememberSyncStamp(entries: List<com.jamal2367.uvsmobile.data.model.LibraryEntry>) {
        val newest = entries.maxOfOrNull { it.updatedAt ?: 0.0 } ?: return
        if (newest > lastSyncStamp) lastSyncStamp = newest
    }

    /** Drop an error the reader has seen, so it does not sit there forever. */
    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun updateQuery(transform: (LibraryQuery) -> LibraryQuery) {
        val updated = transform(_state.value.query)
        if (updated == _state.value.query) return
        _state.value = _state.value.copy(query = updated, endReached = false)
        refresh()
    }

    /**
     * Fill the filter sheet with the values the library actually holds.
     *
     * Two calls, because the server counts five of the nine filterable fields
     * and not the other four. The counts are the cheap call; the rest are read
     * off a projection of the library, which is four short strings an entry and
     * comes back as a 304 on every later open.
     *
     * It matters that these are real values: the API matches a filter exactly,
     * so a typed "Profile 8" finds nothing at all when the stored detail reads
     * "Dolby Vision Profile 8.1" - a filter that looks broken rather than empty.
     */
    private fun loadFilterOptions() {
        viewModelScope.launch {
            val counted = try {
                repository.stats()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                return@launch
            }

            val values = try {
                repository.distinctValues(LibraryQuery.VALUE_FIELDS)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Throwable) {
                // The sheet falls back to a text box for those four fields;
                // the counted ones are already usable.
                emptyMap()
            }

            _state.value = _state.value.copy(
                filterOptions = FilterOptions(
                    // The counts name Dolby Vision by its enhancement layer,
                    // while the filter matches the stored format - so the
                    // suffix comes off and the layer gets its own field.
                    hdrFormats = counted.hdrFormats.keys
                        .map { it.substringBefore(" (").trim() }
                        .filter { it.isNotBlank() && it != "Unknown" }
                        .distinct()
                        .sorted(),
                    resolutions = counted.resolutions.keys.filter { it != "Unknown" }.sorted(),
                    resolutionClasses = counted.resolutionClasses.keys.filter { it != "Unknown" },
                    videoCodecs = counted.videoCodecs.keys.filter { it != "Unknown" }.sorted(),
                    audioCodecs = counted.audioCodecs.keys.filter { it != "Unknown" }.sorted(),
                    hdrDetails = values["hdr_detail"].orEmpty(),
                    elTypes = values["el_type"].orEmpty(),
                    videoEncoders = values["video_encoder"].orEmpty(),
                    cmVersions = values["dv_cm_version"].orEmpty(),
                    loaded = values.isNotEmpty(),
                )
            )
        }
    }

    private companion object {
        const val RELOAD_DEBOUNCE_MS = 1_500L
    }
}
