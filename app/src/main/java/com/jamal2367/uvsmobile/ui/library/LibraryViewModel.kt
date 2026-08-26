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
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.withIndex
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/** What the library screen is showing right now. */
data class LibraryUiState(
    val entries: List<LibraryEntry> = emptyList(),
    val total: Int = 0,
    val query: LibraryQuery = LibraryQuery(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val notConfigured: Boolean = false,
    val filterOptions: FilterOptions = FilterOptions(),
    val layout: LibraryLayout = LibraryLayout.GRID,
    val posterWidth: Int = 320,
    /** How many entries fit on a page, or null when the whole library is one. */
    val pageSize: Int? = null,
    /** Which page is on screen, counted from zero. */
    val page: Int = 0,
) {
    /**
     * How many pages the matches come to.
     *
     * `total` counts every match, not just the window on screen, which is what
     * makes a page number knowable at all: the server answers every request
     * with it, so the buttons never have to ask for an empty page to find out
     * there is nothing after this one.
     */
    val pageCount: Int
        get() = pageSize?.let { size -> if (total <= 0) 1 else (total + size - 1) / size } ?: 1

    /** Whether there is more than one page to move between. */
    val isPaged: Boolean
        get() = pageSize != null && pageCount > 1

    val hasPreviousPage: Boolean
        get() = isPaged && page > 0

    val hasNextPage: Boolean
        get() = isPaged && page < pageCount - 1
}

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
    private var filterOptionsJob: Job? = null
    private var settings: AppSettings = AppSettings()

    /**
     * Whether a stored page size has been read yet.
     *
     * The first settings that arrive are not a change of mind - the first load
     * is already on its way - and reloading for them would ask the server the
     * same question twice.
     */
    private var pageSizeKnown = false

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
                val previousPageSize = settings.entriesPerPage
                settings = updated
                _state.value = _state.value.copy(
                    layout = updated.libraryLayout,
                    posterWidth = updated.posterWidth,
                    notConfigured = !updated.isConfigured,
                    pageSize = updated.entriesPerPage,
                )
                // A different page size cuts the library up differently, so
                // "page three" is not the same three titles it was - the only
                // page that still means anything is the first.
                if (pageSizeKnown && previousPageSize != updated.entriesPerPage) {
                    _state.value = _state.value.copy(page = 0)
                    refresh()
                }
                pageSizeKnown = true
            }
            .launchIn(viewModelScope)

        // The only thing that asks the server for a page - the first load
        // included. Asking in `init` as well would put a request on the wire
        // before the stored address has been read, only for the address to
        // arrive a moment later and cancel it.
        //
        // Every keystroke in the address field is a stored settings change, so
        // an address is half-typed several times on the way to being right -
        // and asking `192.168.178.2` for the library costs a connect timeout
        // that the next keystroke has already made pointless. The stored value
        // arrives first and goes through at once; a changed one has to hold
        // still before anything is asked of it.
        container.settingsRepository.settings
            .distinctUntilChangedBy { it.servers() }
            .withIndex()
            .debounce { (index, _) -> if (index == 0) 0L else SERVER_CHANGE_DEBOUNCE_MS }
            .onEach { (_, updated) ->
                settings = updated
                refresh()
                loadFilterOptions()
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
            .debounce(RELOAD_DEBOUNCE_MS.milliseconds)
            .onEach { syncChangedEntries() }
            .launchIn(viewModelScope)
    }

    fun setSearch(term: String) {
        updateQuery { it.copy(search = term) }
    }

    /**
     * Put the library in a different order.
     *
     * Which also settles the direction: an order has a way round it is meant
     * to be read - newest, largest, best first - and having to turn every one
     * of them round by hand afterwards is a second tap for the answer nobody
     * wanted. The two buttons above the list still turn it back.
     */
    fun setSort(sort: SortOption) {
        updateQuery { it.copy(sort = sort, order = sort.defaultOrder) }
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
        loadJob?.cancel()
        loadJob = viewModelScope.launch { load(silent) }
    }

    /**
     * Fetch the page that is on screen.
     *
     * One request, or two in the one case worth a second: the library can
     * shrink under a reader standing on its last page - a scan that cleared out
     * what is gone, say - and rather than an empty screen with a page number
     * nobody can leave, the last page that still exists is fetched instead.
     */
    private suspend fun load(silent: Boolean) {
        val query = _state.value.query
        val size = settings.entriesPerPage
        // Without pages there is only ever one, so a stale page number from a
        // moment ago cannot send the request past the end of the library.
        var page = if (size == null) 0 else _state.value.page

        if (!silent) {
            _state.value = _state.value.copy(
                isRefreshing = _state.value.entries.isNotEmpty(),
                isLoading = _state.value.entries.isEmpty(),
                error = null,
            )
        }
        try {
            var result = repository.library(query, size, offset = page * (size ?: 0))
            val lastPage = lastPageFor(result.total, size)
            if (page > lastPage) {
                page = lastPage
                result = repository.library(query, size, offset = page * (size ?: 0))
            }
            rememberSyncStamp(result.files)
            _state.value = _state.value.copy(
                entries = result.files,
                total = result.total,
                pageSize = size,
                page = page,
                isLoading = false,
                isRefreshing = false,
                error = null,
                notConfigured = false,
            )
        } catch (failure: Throwable) {
            if (failure is kotlinx.coroutines.CancellationException) throw failure
            _state.value = _state.value.copy(
                isLoading = false,
                isRefreshing = false,
                error = failure.toUserMessage(getApplication()),
                notConfigured = failure is ApiFailure.NotConfigured,
            )
        }
    }

    /** The last page that holds anything, given how many matches there are. */
    private fun lastPageFor(total: Int, size: Int?): Int =
        if (size == null || total <= 0) 0 else (total - 1) / size

    /** Move to a page, as the buttons under the library do. */
    fun goToPage(target: Int) {
        val current = _state.value
        if (!current.isPaged) return
        val page = target.coerceIn(0, current.pageCount - 1)
        if (page == current.page) return
        _state.value = current.copy(page = page)
        refresh()
    }

    fun nextPage() = goToPage(_state.value.page + 1)

    fun previousPage() = goToPage(_state.value.page - 1)

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
        } catch (_: Throwable) {
            // A failed sync is not worth an error on screen: the rows on it are
            // still the ones the server last handed over.
        }
    }

    private fun rememberSyncStamp(entries: List<LibraryEntry>) {
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
        // A different question has a different first page.
        _state.value = _state.value.copy(query = updated, page = 0)
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
        // The counts are a second request behind every server change, and a
        // stale one is worth no more than a stale page is.
        filterOptionsJob?.cancel()
        filterOptionsJob = viewModelScope.launch {
            val counted = try {
                repository.stats()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                return@launch
            }

            val values = try {
                repository.distinctValues(LibraryQuery.VALUE_FIELDS)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
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

        /** How long a changed address has to stand still before it is used. */
        const val SERVER_CHANGE_DEBOUNCE_MS = 700L
    }
}
