@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jamal2367.uvsmobile.ui.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jamal2367.uvsmobile.R
import com.jamal2367.uvsmobile.ui.components.EmptyState
import com.jamal2367.uvsmobile.ui.components.ErrorState
import com.jamal2367.uvsmobile.ui.components.LoadingState
import androidx.compose.ui.platform.LocalResources

/**
 * What is in the media directory, and what of it the library already holds.
 *
 * The point of the screen is the gap between the two: pick the files that were
 * never scanned and hand exactly those to `/scan/files`, rather than asking the
 * server to walk the whole directory again.
 */
@Composable
fun FilesScreen(
    onOpenEntry: (String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: FilesViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val scannedOneFormat = stringResource(R.string.files_scan_one_done)

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(state.error) {
        val error = state.error ?: return@LaunchedEffect
        if (state.files.isNotEmpty()) {
            snackbarHostState.showSnackbar(error)
            viewModel.dismissError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.files_title)) },
                actions = {
                    if (state.selected.isNotEmpty()) {
                        TextButton(onClick = viewModel::clearSelection) {
                            Text(stringResource(R.string.files_selection_clear))
                        }
                    } else if (state.unscannedCount > 0) {
                        TextButton(onClick = viewModel::selectAllUnscanned) {
                            Text(stringResource(R.string.files_select_all_unscanned))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.selected.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        viewModel.scanSelected { queued, skipped ->
                            resources.getQuantityString(
                                R.plurals.files_queued,
                                queued,
                                queued,
                                skipped,
                            )
                        }
                    },
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    text = {
                        Text(stringResource(R.string.files_scan_selected, state.selected.size))
                    },
                )
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = state.search,
                onValueChange = viewModel::setSearch,
                placeholder = { Text(stringResource(R.string.files_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.search.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearch("") }) {
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FileFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = {
                            Text(
                                stringResource(
                                    when (filter) {
                                        FileFilter.ALL -> R.string.files_filter_all
                                        FileFilter.UNSCANNED -> R.string.files_unscanned
                                        FileFilter.SCANNED -> R.string.files_scanned
                                    }
                                )
                            )
                        },
                    )
                }
            }

            if (state.files.isNotEmpty()) {
                Text(
                    text = pluralStringResource(
                        R.plurals.files_count,
                        state.files.size,
                        state.files.size,
                        state.unscannedCount,
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { viewModel.load() },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.notConfigured -> EmptyState(
                        icon = Icons.Outlined.Folder,
                        title = stringResource(R.string.error_not_configured),
                        actionLabel = stringResource(R.string.action_open_settings),
                        onAction = onOpenSettings,
                    )

                    state.isLoading && state.files.isEmpty() -> LoadingState()

                    state.error != null && state.files.isEmpty() -> ErrorState(
                        message = state.error.orEmpty(),
                        onRetry = { viewModel.load() },
                        actionLabel = stringResource(R.string.action_open_settings),
                        onAction = onOpenSettings,
                    )

                    state.visibleFiles.isEmpty() -> EmptyState(
                        icon = Icons.Outlined.Folder,
                        title = stringResource(R.string.files_empty),
                    )

                    else -> LazyColumn(
                        contentPadding = PaddingValues(bottom = 96.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.visibleFiles, key = { it.path }) { file ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = file.name,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        text = stringResource(
                                            if (file.scanned) {
                                                R.string.files_scanned
                                            } else {
                                                R.string.files_unscanned
                                            }
                                        ),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (file.scanned) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                    )
                                },
                                leadingContent = {
                                    // A file already in the library is opened,
                                    // not queued again - there is nothing to
                                    // select it for.
                                    if (file.scanned) {
                                        Icon(
                                            Icons.Outlined.Folder,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    } else {
                                        Checkbox(
                                            checked = file.path in state.selected,
                                            onCheckedChange = { viewModel.toggleSelection(file) },
                                        )
                                    }
                                },
                                trailingContent = {
                                    if (!file.scanned) {
                                        if (state.scanningPath == file.path) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                            )
                                        } else {
                                            IconButton(
                                                onClick = {
                                                    viewModel.scanOne(file.path) { title ->
                                                        String.format(
                                                            java.util.Locale.getDefault(),
                                                            scannedOneFormat,
                                                            title,
                                                        )
                                                    }
                                                },
                                                enabled = state.scanningPath == null,
                                            ) {
                                                Icon(
                                                    Icons.Filled.PlayArrow,
                                                    contentDescription = stringResource(
                                                        R.string.files_scan_now
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                },
                                colors = ListItemDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                                modifier = Modifier.then(
                                    if (file.scanned) {
                                        Modifier.clickableRow { onOpenEntry(file.path) }
                                    } else {
                                        Modifier.clickableRow { viewModel.toggleSelection(file) }
                                    }
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = clickable(onClick = onClick)
