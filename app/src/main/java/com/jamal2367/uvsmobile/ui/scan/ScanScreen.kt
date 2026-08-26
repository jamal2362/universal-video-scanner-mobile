@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jamal2367.uvsmobile.ui.scan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jamal2367.uvsmobile.R
import com.jamal2367.uvsmobile.ui.components.EmptyState
import com.jamal2367.uvsmobile.ui.components.MetaChip
import com.jamal2367.uvsmobile.ui.components.SectionCard

/**
 * The scan: what it is doing, and the three ways to change that.
 *
 * The progress comes over the event stream rather than from polling, so the bar
 * moves as the server works. What the server reports is the truth here - a scan
 * started from the web interface, or the one the container runs at startup,
 * shows up on this screen just the same.
 */
@Composable
fun ScanScreen(
    onOpenSettings: () -> Unit,
    viewModel: ScanViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmClear by rememberSaveable { mutableStateOf(false) }

    val startedMessage = stringResource(R.string.scan_started)
    val clearedMessage = stringResource(R.string.scan_clear_db_done)

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_scan)) },
                actions = { LiveIndicator(state.live) },
            )
        },
    ) { padding ->
        if (state.notConfigured) {
            EmptyState(
                icon = Icons.Outlined.Radar,
                title = stringResource(R.string.error_not_configured),
                actionLabel = stringResource(R.string.action_open_settings),
                onAction = onOpenSettings,
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item { ProgressCard(state) }
                item {
                    ControlsCard(
                        state = state,
                        onStart = { viewModel.startFullScan(startedMessage) },
                        onCancel = viewModel::cancelScan,
                    )
                }
                item { ActivityCard(state) }
                item {
                    DangerCard(
                        isClearing = state.isClearing,
                        onClear = { confirmClear = true },
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.scan_clear_db_title)) },
            text = { Text(stringResource(R.string.scan_clear_db_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClear = false
                        viewModel.clearDatabase(clearedMessage)
                    }
                ) {
                    Text(stringResource(R.string.scan_clear_db))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun LiveIndicator(status: LiveStatus) {
    val (color, labelRes) = when (status) {
        LiveStatus.CONNECTED -> MaterialTheme.colorScheme.primary to R.string.scan_live_connected
        LiveStatus.CONNECTING -> MaterialTheme.colorScheme.tertiary to R.string.scan_live_connecting
        LiveStatus.OFFLINE -> MaterialTheme.colorScheme.error to R.string.scan_live_offline
        LiveStatus.DISABLED -> MaterialTheme.colorScheme.outline to R.string.scan_live_offline
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(end = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = stringResource(labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProgressCard(state: ScanUiState) {
    val progress = state.progress
    val statusRes = when {
        state.running || progress.isScanning -> R.string.scan_state_scanning
        progress.status == "done" -> R.string.scan_state_done
        progress.status == "cancelled" -> R.string.scan_state_cancelled
        progress.status == "error" -> R.string.scan_state_error
        else -> R.string.scan_state_idle
    }

    SectionCard(title = stringResource(statusRes)) {
        AnimatedVisibility(visible = progress.total > 0) {
            Column {
                LinearProgressIndicator(
                    progress = { (progress.percent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                )
                Text(
                    text = stringResource(
                        R.string.scan_progress,
                        progress.current,
                        progress.total,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (progress.filename.isNotBlank()) {
            Text(
                text = stringResource(R.string.scan_current_file),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = progress.filename,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            progress.newFiles?.let {
                MetaChip(text = stringResource(R.string.scan_summary_new, it))
            }
            progress.removedFiles?.takeIf { it > 0 }?.let {
                MetaChip(text = stringResource(R.string.scan_summary_removed, it))
            }
            progress.totalFiles?.let {
                MetaChip(text = stringResource(R.string.scan_summary_total, it))
            }
        }

        progress.error?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ControlsCard(
    state: ScanUiState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.nav_scan)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onStart,
                enabled = !state.running && !state.isStarting,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = stringResource(R.string.scan_start_full),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            OutlinedButton(
                onClick = onCancel,
                enabled = state.running && !state.isCancelling,
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(
                    text = stringResource(
                        if (state.isCancelling) R.string.scan_cancelling else R.string.scan_cancel
                    ),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ActivityCard(state: ScanUiState) {
    SectionCard(title = stringResource(R.string.scan_activity)) {
        if (state.activity.isEmpty()) {
            Text(
                text = stringResource(R.string.scan_activity_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        state.activity.take(ACTIVITY_ROWS).forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            when (item.kind) {
                                ActivityItem.Kind.UPDATED -> MaterialTheme.colorScheme.primary
                                ActivityItem.Kind.DELETED -> MaterialTheme.colorScheme.error
                            }
                        )
                )
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text(
                        text = item.name.ifBlank { stringResource(R.string.state_unknown) },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(
                            when (item.kind) {
                                ActivityItem.Kind.UPDATED -> R.string.scan_event_updated
                                ActivityItem.Kind.DELETED -> R.string.scan_event_deleted
                            }
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DangerCard(isClearing: Boolean, onClear: () -> Unit) {
    SectionCard(title = stringResource(R.string.scan_danger_zone)) {
        Text(
            text = stringResource(R.string.scan_clear_db_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(onClick = onClear, enabled = !isClearing) {
            if (isClearing) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    Icons.Outlined.DeleteForever,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = stringResource(R.string.scan_clear_db),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

private const val ACTIVITY_ROWS = 20
