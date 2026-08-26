@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jamal2367.uvsmobile.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.SortByAlpha
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jamal2367.uvsmobile.R
import com.jamal2367.uvsmobile.data.prefs.LibraryLayout
import com.jamal2367.uvsmobile.ui.components.EmptyState
import com.jamal2367.uvsmobile.ui.components.ErrorState
import com.jamal2367.uvsmobile.ui.components.LoadingState
import kotlinx.coroutines.delay

/**
 * The library.
 *
 * Everything on this screen is a question put to the server: the search box, the
 * chips, the order, and the next page when the list runs out. Nothing is
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
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showSort by rememberSaveable { mutableStateOf(false) }
    var showFilter by rememberSaveable { mutableStateOf(false) }
    var searchText by rememberSaveable { mutableStateOf(state.query.search) }

    // A keystroke is not a question: the search waits for a pause before it
    // asks, so typing eight letters is one request rather than eight.
    LaunchedEffect(searchText) {
        if (searchText != state.query.search) {
            delay(SEARCH_DEBOUNCE_MS)
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

    Scaffold(
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
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                placeholder = { Text(stringResource(R.string.library_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { searchText = "" }) {
                            Icon(
                                Icons.Outlined.Close,
                                contentDescription = stringResource(R.string.action_clear),
                            )
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )

            QueryChips(
                sortLabel = stringResource(state.query.sort.labelRes),
                activeFilters = state.query.activeFilterCount,
                onSort = { showSort = true },
                onFilter = { showFilter = true },
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
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier.fillMaxSize(),
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

                    state.entries.isEmpty() -> EmptyState(
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

                    state.layout == LibraryLayout.GRID -> LibraryGrid(
                        state = state,
                        onOpenEntry = onOpenEntry,
                        onLoadMore = viewModel::loadMore,
                    )

                    else -> LibraryList(
                        state = state,
                        onOpenEntry = onOpenEntry,
                        onLoadMore = viewModel::loadMore,
                    )
                }
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
        FilterSheet(
            query = state.query,
            options = state.filterOptions,
            onFilter = viewModel::setFilter,
            onRange = viewModel::setRange,
            onReset = {
                searchText = ""
                viewModel.clearNarrowing()
            },
            onDismiss = { showFilter = false },
        )
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
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

@Composable
private fun LibraryGrid(
    state: LibraryUiState,
    onOpenEntry: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    val gridState = rememberLazyGridState()

    // Ask for the next window a screenful before the list runs out, so scrolling
    // never stops at the bottom waiting for it.
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.entries.size - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(shouldLoadMore, state.entries.size) {
        if (shouldLoadMore) onLoadMore()
    }

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Adaptive(minSize = 116.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.entries, key = { it.path }) { entry ->
            EntryGridCard(
                entry = entry,
                posterWidth = state.posterWidth,
                onClick = { onOpenEntry(entry.path) },
            )
        }
        if (state.isLoadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                LoadingFooter()
            }
        }
    }
}

@Composable
private fun LibraryList(
    state: LibraryUiState,
    onOpenEntry: (String) -> Unit,
    onLoadMore: () -> Unit,
) {
    val listState = rememberLazyListState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.entries.size - LOAD_MORE_THRESHOLD
        }
    }
    LaunchedEffect(shouldLoadMore, state.entries.size) {
        if (shouldLoadMore) onLoadMore()
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.entries, key = { it.path }) { entry ->
            EntryListRow(
                entry = entry,
                posterWidth = state.posterWidth,
                onClick = { onOpenEntry(entry.path) },
            )
        }
        if (state.isLoadingMore) {
            item { LoadingFooter() }
        }
    }
}

@Composable
private fun LoadingFooter() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(
            text = stringResource(R.string.library_loading_more),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

private const val SEARCH_DEBOUNCE_MS = 350L
private const val LOAD_MORE_THRESHOLD = 8
