@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jamal2367.uvsmobile.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jamal2367.uvsmobile.R
import com.jamal2367.uvsmobile.data.prefs.AppSettings
import com.jamal2367.uvsmobile.data.prefs.LibraryLayout
import com.jamal2367.uvsmobile.ui.components.EmptyState
import com.jamal2367.uvsmobile.ui.components.ErrorState
import com.jamal2367.uvsmobile.ui.components.LoadingState
import com.jamal2367.uvsmobile.ui.components.SearchField
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * The library.
 *
 * Everything on this screen is a question put to the server: the search box, the
 * chips, the order, and which page of the answer to hand over. Nothing is
 * filtered on the phone - a library of thousands would have to travel here first
 * for that, which is exactly what the API's query parameters exist to avoid.
 */
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onOpenEntry: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showSort by rememberSaveable { mutableStateOf(false) }
    var showFilter by rememberSaveable { mutableStateOf(false) }
    var searchText by rememberSaveable { mutableStateOf(state.query.search) }

    // The title gets out of the way as the library is scrolled and comes back
    // on the way up: on a phone held in one hand it is a strip of nothing
    // between the clock and the first poster.
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()

    // A keystroke is not a question: the search waits for a pause before it
    // asks, so typing eight letters is one request rather than eight.
    LaunchedEffect(searchText) {
        if (searchText != state.query.search) {
            delay(SEARCH_DEBOUNCE_MS.milliseconds)
            viewModel.setSearch(searchText)
        }
    }

    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        if (state.entries.isNotEmpty()) {
            snackbarHostState.showSnackbar(error)
            viewModel.dismissError()
        }
    }

    // A new page starts at its top, title and all - it is a different set of
    // titles, not a continuation of the one that was just scrolled through.
    LaunchedEffect(state.page) {
        scrollBehavior.state.heightOffset = 0f
    }

    // Everything above the first poster travels with it rather than sitting on
    // top of the list: on a phone the search box and the chips would otherwise
    // take a third of the screen away from the library itself.
    val header: @Composable (Modifier) -> Unit = { modifier ->
        LibraryHeader(
            state = state,
            searchText = searchText,
            onSearchChange = { searchText = it },
            onSort = { showSort = true },
            onFilter = { showFilter = true },
            modifier = modifier,
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_library)) },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.setLayout(
                                if (state.layout == LibraryLayout.GRID) {
                                    LibraryLayout.LIST
                                } else {
                                    LibraryLayout.GRID
                                }
                            )
                        }
                    ) {
                        Icon(
                            imageVector = if (state.layout == LibraryLayout.GRID) {
                                Icons.AutoMirrored.Outlined.ViewList
                            } else {
                                Icons.Outlined.GridView
                            },
                            contentDescription = stringResource(
                                if (state.layout == LibraryLayout.GRID) {
                                    R.string.library_layout_list
                                } else {
                                    R.string.library_layout_grid
                                }
                            ),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = {
            // Only when the library was actually cut into pages - at "all"
            // there is nothing to page to.
            if (state.isPaged) {
                PageBar(
                    page = state.page,
                    pageCount = state.pageCount,
                    hasPrevious = state.hasPreviousPage,
                    hasNext = state.hasNextPage,
                    onPrevious = viewModel::previousPage,
                    onNext = viewModel::nextPage,
                )
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                state.notConfigured -> EmptyState(
                    icon = Icons.Outlined.VideoLibrary,
                    title = stringResource(R.string.error_not_configured),
                    actionLabel = stringResource(R.string.action_open_settings),
                    onAction = onOpenSettings,
                )

                state.isLoading && state.entries.isEmpty() -> LoadingState()

                state.error != null && state.entries.isEmpty() -> ErrorState(
                    message = state.error.orEmpty(),
                    onRetry = { viewModel.refresh() },
                    actionLabel = stringResource(R.string.action_open_settings),
                    onAction = onOpenSettings,
                )

                // Nothing to show, but the search box has to stay within reach:
                // it is usually what emptied the screen in the first place.
                state.entries.isEmpty() -> Column(Modifier.fillMaxSize()) {
                    header(Modifier.padding(horizontal = 16.dp))
                    EmptyState(
                        icon = Icons.Outlined.VideoLibrary,
                        title = stringResource(
                            if (state.query.hasAnyNarrowing) {
                                R.string.library_no_results
                            } else {
                                R.string.library_empty
                            }
                        ),
                        actionLabel = if (state.query.hasAnyNarrowing) {
                            stringResource(R.string.filter_reset_all)
                        } else {
                            null
                        },
                        onAction = if (state.query.hasAnyNarrowing) {
                            {
                                searchText = ""
                                viewModel.clearNarrowing()
                            }
                        } else {
                            null
                        },
                    )
                }

                state.layout == LibraryLayout.GRID -> LibraryGrid(
                    state = state,
                    onOpenEntry = onOpenEntry,
                    header = { header(Modifier) },
                )

                else -> LibraryList(
                    state = state,
                    onOpenEntry = onOpenEntry,
                    header = { header(Modifier) },
                )
            }
        }
    }

    if (showSort) {
        SortSheet(
            current = state.query.sort,
            order = state.query.order,
            onSelect = {
                viewModel.setSort(it)
                showSort = false
            },
            onOrder = viewModel::setOrder,
            onDismiss = { showSort = false },
        )
    }

    if (showFilter) {
        // The values the sheet offers as chips are fetched here rather than at
        // startup: this is the moment they are wanted, and at startup they are
        // two requests - one of them across the whole library - that the first
        // page would have to share the connection with. Here rather than in the
        // button, so a sheet that was open before the app was killed fills in
        // too.
        LaunchedEffect(Unit) { viewModel.ensureFilterOptions() }

        FilterSheet(
            query = state.query,
            options = state.filterOptions,
            onFilter = viewModel::setFilter,
            onFilters = viewModel::setFilters,
            onRange = viewModel::setRange,
            onReset = {
                searchText = ""
                viewModel.clearNarrowing()
            },
            onDismiss = { showFilter = false },
        )
    }
}

/** The search box, the two chips and the count - everything above the titles. */
@Composable
private fun LibraryHeader(
    state: LibraryUiState,
    searchText: String,
    onSearchChange: (String) -> Unit,
    onSort: () -> Unit,
    onFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SearchField(
            value = searchText,
            onValueChange = onSearchChange,
            placeholder = stringResource(R.string.library_search_hint),
        )

        QueryChips(
            sortLabel = stringResource(state.query.sort.labelRes),
            activeFilters = state.query.activeFilterCount,
            onSort = onSort,
            onFilter = onFilter,
        )

        if (state.entries.isNotEmpty()) {
            Text(
                text = pluralStringResource(
                    R.plurals.library_count,
                    state.total,
                    state.entries.size,
                    state.total,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun QueryChips(
    sortLabel: String,
    activeFilters: Int,
    onSort: () -> Unit,
    onFilter: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AssistChip(
            onClick = onSort,
            label = { Text(sortLabel) },
            leadingIcon = {
                Icon(
                    Icons.Outlined.SortByAlpha,
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
        )
        BadgedBox(
            badge = { if (activeFilters > 0) Badge { Text(activeFilters.toString()) } },
        ) {
            AssistChip(
                onClick = onFilter,
                label = { Text(stringResource(R.string.library_filter)) },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
            )
        }
    }
}

/**
 * One page back, one page on, and which page this is.
 *
 * Only there when the library was asked for in pages: at "all entries" the
 * whole answer is already on screen and there is nowhere to page to.
 */
@Composable
private fun PageBar(
    page: Int,
    pageCount: Int,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Nothing when the shell above has already padded for the
                // system's gesture bar, the gesture bar's height when it has
                // not - which is the case beside a navigation rail.
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilledTonalButton(onClick = onPrevious, enabled = hasPrevious) {
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.library_page_previous),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            Text(
                text = stringResource(R.string.library_page_of, page + 1, pageCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            FilledTonalButton(onClick = onNext, enabled = hasNext) {
                Text(
                    text = stringResource(R.string.library_page_next),
                    modifier = Modifier.padding(end = 6.dp),
                )
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun LibraryGrid(
    state: LibraryUiState,
    onOpenEntry: (String) -> Unit,
    header: @Composable () -> Unit,
) {
    val gridState = rememberLazyGridState()

    // A page turned is a different set of titles: it starts at the top rather
    // than wherever the last one was left.
    LaunchedEffect(state.page) { gridState.scrollToItem(0) }

    LazyVerticalGrid(
        state = gridState,
        // A count rather than a minimum width: how many covers stand beside
        // each other is the thing the settings let a reader decide, and an
        // adaptive grid would quietly overrule it on a wider screen.
        columns = GridCells.Fixed(state.gridColumns),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = HEADER_KEY, span = { GridItemSpan(maxLineSpan) }) { header() }
        items(state.entries, key = { it.path }) { entry ->
            EntryGridCard(
                entry = entry,
                posterWidth = state.posterWidth,
                onClick = { onOpenEntry(entry.path) },
                // One per row is the wide layout: at that width the upright
                // cover would be taller than the screen, so the tile shows the
                // 16:9 backdrop instead.
                landscape = state.gridColumns == AppSettings.SINGLE_GRID_COLUMN,
            )
        }
    }
}

@Composable
private fun LibraryList(
    state: LibraryUiState,
    onOpenEntry: (String) -> Unit,
    header: @Composable () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(state.page) { listState.scrollToItem(0) }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = HEADER_KEY) { header() }
        items(state.entries, key = { it.path }) { entry ->
            EntryListRow(
                entry = entry,
                posterWidth = state.posterWidth,
                onClick = { onOpenEntry(entry.path) },
            )
        }
    }
}

private const val SEARCH_DEBOUNCE_MS = 350L

/** Keeps the header apart from the entries, whose keys are their paths. */
private const val HEADER_KEY = "library-header"
