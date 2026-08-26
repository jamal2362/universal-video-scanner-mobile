@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jamal2367.uvsmobile.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jamal2367.uvsmobile.R
import com.jamal2367.uvsmobile.data.model.FilterField
import com.jamal2367.uvsmobile.ui.components.EmptyState
import com.jamal2367.uvsmobile.ui.components.ErrorState
import com.jamal2367.uvsmobile.ui.components.LoadingState
import com.jamal2367.uvsmobile.ui.components.SectionCard
import com.jamal2367.uvsmobile.ui.components.StatBar
import com.jamal2367.uvsmobile.ui.components.StatTile
import com.jamal2367.uvsmobile.util.Formatters

/**
 * The library in numbers.
 *
 * Every row is also a way in: tapping one opens the library filtered to it, so
 * "48 titles in H.264" and "show me those 48" are the same gesture.
 */
@Composable
fun StatsScreen(
    onShowInLibrary: (FilterField, String) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: StatsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_stats)) }) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.load() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val stats = state.stats
            when {
                state.notConfigured -> EmptyState(
                    icon = Icons.Outlined.Insights,
                    title = stringResource(R.string.error_not_configured),
                    actionLabel = stringResource(R.string.action_open_settings),
                    onAction = onOpenSettings,
                )

                state.isLoading && stats == null -> LoadingState()

                stats == null -> ErrorState(
                    message = state.error.orEmpty(),
                    onRetry = { viewModel.load() },
                    actionLabel = stringResource(R.string.action_open_settings),
                    onAction = onOpenSettings,
                )

                stats.total == 0 -> EmptyState(
                    icon = Icons.Outlined.Insights,
                    title = stringResource(R.string.stats_empty),
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item { SummaryTiles(total = stats.total, totalSize = stats.totalSize) }

                    item {
                        BreakdownCard(
                            title = stringResource(R.string.stats_hdr_formats),
                            counts = stats.hdrFormats,
                            // The counts name Dolby Vision by its enhancement
                            // layer; the filter matches the stored format, so
                            // the suffix is what the layer filter gets.
                            onRowClick = { label -> onHdrRowClick(label, onShowInLibrary) },
                        )
                    }

                    item {
                        BreakdownCard(
                            title = stringResource(R.string.stats_resolution_classes),
                            counts = stats.resolutionClasses,
                            onRowClick = { onShowInLibrary(FilterField.RESOLUTION_CLASS, it) },
                        )
                    }

                    item {
                        BreakdownCard(
                            title = stringResource(R.string.stats_video_codecs),
                            counts = stats.videoCodecs,
                            onRowClick = { onShowInLibrary(FilterField.VIDEO_CODEC, it) },
                        )
                    }

                    item {
                        BreakdownCard(
                            title = stringResource(R.string.stats_audio_codecs),
                            counts = stats.audioCodecs,
                            onRowClick = { onShowInLibrary(FilterField.AUDIO_CODEC, it) },
                        )
                    }

                    item {
                        BreakdownCard(
                            title = stringResource(R.string.stats_resolutions),
                            counts = stats.resolutions,
                            collapsedRows = 6,
                            onRowClick = { onShowInLibrary(FilterField.RESOLUTION, it) },
                        )
                    }
                }
            }
        }
    }
}

private fun onHdrRowClick(label: String, onShowInLibrary: (FilterField, String) -> Unit) {
    val layer = label.substringAfter('(', "").substringBefore(')').trim()
    if (layer.isNotEmpty()) {
        onShowInLibrary(FilterField.EL_TYPE, layer)
    } else {
        onShowInLibrary(FilterField.HDR_FORMAT, label)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryTiles(total: Int, totalSize: Double) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatTile(
            value = total.toString(),
            caption = stringResource(R.string.stats_total_entries),
        )
        StatTile(
            value = Formatters.fileSize(totalSize) ?: stringResource(R.string.state_none),
            caption = stringResource(R.string.stats_total_size),
            container = MaterialTheme.colorScheme.secondaryContainer,
            content = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/**
 * One breakdown, longest bar first.
 *
 * Only the first few rows are drawn: a library can hold two dozen distinct
 * frame sizes, and the tail of that list is never what someone came for.
 */
@Composable
private fun BreakdownCard(
    title: String,
    counts: Map<String, Int>,
    collapsedRows: Int = 8,
    onRowClick: ((String) -> Unit)? = null,
) {
    if (counts.isEmpty()) return

    var expanded by rememberSaveable(title) { mutableStateOf(false) }
    val ordered = remember(counts) { counts.entries.sortedByDescending { it.value } }
    val maxCount = ordered.firstOrNull()?.value ?: 0
    val visible = if (expanded) ordered else ordered.take(collapsedRows)

    SectionCard(
        title = title,
        trailing = {
            if (ordered.size > collapsedRows) {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        text = if (expanded) {
                            stringResource(R.string.stats_show_less)
                        } else {
                            stringResource(R.string.stats_show_all, ordered.size)
                        }
                    )
                }
            }
        },
    ) {
        visible.forEach { (label, count) ->
            StatBar(
                label = label,
                count = count,
                maxCount = maxCount,
                onClick = onRowClick?.takeIf { label != "Unknown" }?.let { click -> { click(label) } },
            )
        }
        if (onRowClick != null) {
            Text(
                text = stringResource(R.string.stats_tap_to_filter),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
