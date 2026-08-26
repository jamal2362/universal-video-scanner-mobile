@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.jamal2367.uvsmobile.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jamal2367.uvsmobile.BuildConfig
import com.jamal2367.uvsmobile.R
import com.jamal2367.uvsmobile.data.prefs.AppSettings
import com.jamal2367.uvsmobile.data.prefs.ConnectionMode
import com.jamal2367.uvsmobile.data.prefs.ServerConfig
import com.jamal2367.uvsmobile.data.prefs.ThemeMode
import com.jamal2367.uvsmobile.ui.components.InfoRow
import com.jamal2367.uvsmobile.ui.components.SectionCard

/**
 * Where the two addresses live.
 *
 * The whole reason there are two: the instance on the shelf at home is reached
 * by its address on the network, and the same instance from a train is reached
 * by whatever way in was published for it - a different scheme, a different
 * port, usually a different token. Which one the app uses is not a switch the
 * reader has to flip on the way out of the door: in automatic mode the local
 * one is tried first and the remote one takes over the moment it does not
 * answer.
 */
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = state.settings

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.nav_settings)) }) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.settings_servers_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Which of the two actually answered last - in automatic
                    // mode that is the only way to tell from the outside.
                    state.activeServerLabel?.let { label ->
                        Text(
                            text = stringResource(
                                if (state.activeIsPrimary) {
                                    R.string.server_active_primary
                                } else {
                                    R.string.server_active_secondary
                                }
                            ) + " · " + label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            item {
                ServerCard(
                    title = stringResource(R.string.settings_server_primary),
                    config = settings.primary,
                    test = state.primaryTest,
                    isActive = state.activeServerLabel != null && state.activeIsPrimary,
                    onChange = { viewModel.updateServer(ServerSlot.PRIMARY) { _ -> it } },
                    onTest = { viewModel.testConnection(ServerSlot.PRIMARY) },
                    sanitizeHost = viewModel::sanitizeHost,
                    portFromHost = viewModel::portFromHost,
                    schemeFromHost = viewModel::schemeFromHost,
                )
            }

            item {
                ServerCard(
                    title = stringResource(R.string.settings_server_secondary),
                    config = settings.secondary,
                    test = state.secondaryTest,
                    isActive = state.activeServerLabel != null && !state.activeIsPrimary &&
                        settings.secondary.label == state.activeServerLabel,
                    onChange = { viewModel.updateServer(ServerSlot.SECONDARY) { _ -> it } },
                    onTest = { viewModel.testConnection(ServerSlot.SECONDARY) },
                    sanitizeHost = viewModel::sanitizeHost,
                    portFromHost = viewModel::portFromHost,
                    schemeFromHost = viewModel::schemeFromHost,
                )
            }

            item { ConnectionModeCard(settings.connectionMode, viewModel::setConnectionMode) }
            item { AppearanceCard(settings, viewModel) }
            item { LibraryCard(settings, viewModel) }
            item { AboutCard() }
        }
    }
}

@Composable
private fun ServerCard(
    title: String,
    config: ServerConfig,
    test: TestState,
    isActive: Boolean,
    onChange: (ServerConfig) -> Unit,
    onTest: () -> Unit,
    sanitizeHost: (String) -> String,
    portFromHost: (String) -> Int?,
    schemeFromHost: (String) -> Boolean?,
) {
    var tokenVisible by rememberSaveable { mutableStateOf(false) }
    var hostText by rememberSaveable(config.host) { mutableStateOf(config.host) }
    var portText by rememberSaveable(config.port) { mutableStateOf(config.port.toString()) }

    SectionCard(
        title = title,
        trailing = {
            if (isActive) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    ) {
        SwitchRow(
            label = stringResource(R.string.settings_server_enabled),
            checked = config.enabled,
            onCheckedChange = { onChange(config.copy(enabled = it)) },
        )

        OutlinedTextField(
            value = hostText,
            onValueChange = { raw ->
                // A pasted `https://scanner.example.com:8443/` should fill all
                // three fields rather than end up in the host as it stands.
                val host = sanitizeHost(raw)
                val pastedPort = portFromHost(raw)
                val pastedHttps = schemeFromHost(raw)
                hostText = host
                pastedPort?.let { portText = it.toString() }
                onChange(
                    config.copy(
                        host = host,
                        port = pastedPort ?: config.port,
                        useHttps = pastedHttps ?: config.useHttps,
                    )
                )
            },
            label = { Text(stringResource(R.string.settings_host)) },
            placeholder = { Text("192.168.1.10") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = portText,
                onValueChange = { raw ->
                    val digits = raw.filter { it.isDigit() }.take(5)
                    portText = digits
                    digits.toIntOrNull()?.takeIf { it in 1..65535 }?.let {
                        onChange(config.copy(port = it))
                    }
                },
                label = { Text(stringResource(R.string.settings_port)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                SwitchRow(
                    label = stringResource(R.string.settings_https),
                    checked = config.useHttps,
                    onCheckedChange = { onChange(config.copy(useHttps = it)) },
                )
            }
        }

        OutlinedTextField(
            value = config.token,
            onValueChange = { onChange(config.copy(token = it)) },
            label = { Text(stringResource(R.string.settings_token)) },
            supportingText = { Text(stringResource(R.string.settings_token_hint)) },
            singleLine = true,
            visualTransformation = if (tokenVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            trailingIcon = {
                IconButton(onClick = { tokenVisible = !tokenVisible }) {
                    Icon(
                        imageVector = if (tokenVisible) {
                            Icons.Outlined.VisibilityOff
                        } else {
                            Icons.Outlined.Visibility
                        },
                        contentDescription = stringResource(
                            if (tokenVisible) {
                                R.string.settings_token_hide
                            } else {
                                R.string.settings_token_show
                            }
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onTest, enabled = test != TestState.Running) {
                Text(
                    stringResource(
                        if (test == TestState.Running) {
                            R.string.settings_test_running
                        } else {
                            R.string.settings_test
                        }
                    )
                )
            }
            TestResult(test, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

@Composable
private fun TestResult(test: TestState, modifier: Modifier = Modifier) {
    when (test) {
        TestState.Idle -> Unit

        TestState.Running -> CircularProgressIndicator(
            modifier = modifier.size(18.dp),
            strokeWidth = 2.dp,
        )

        is TestState.Ok -> Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.settings_test_ok, test.apiVersion),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 6.dp),
            )
        }

        is TestState.Failed -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = test.message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

@Composable
private fun ConnectionModeCard(current: ConnectionMode, onSelect: (ConnectionMode) -> Unit) {
    SectionCard(title = stringResource(R.string.settings_mode)) {
        ConnectionMode.entries.forEach { mode ->
            val (labelRes, descriptionRes) = when (mode) {
                ConnectionMode.AUTO ->
                    R.string.settings_mode_auto to R.string.settings_mode_auto_desc

                ConnectionMode.PRIMARY_ONLY ->
                    R.string.settings_mode_primary to R.string.settings_mode_primary_desc

                ConnectionMode.SECONDARY_ONLY ->
                    R.string.settings_mode_secondary to R.string.settings_mode_secondary_desc
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(mode) },
                verticalAlignment = Alignment.Top,
            ) {
                RadioButton(selected = current == mode, onClick = { onSelect(mode) })
                Column(modifier = Modifier.padding(start = 4.dp, top = 12.dp)) {
                    Text(text = stringResource(labelRes), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = stringResource(descriptionRes),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceCard(settings: AppSettings, viewModel: SettingsViewModel) {
    SectionCard(title = stringResource(R.string.settings_appearance)) {
        Text(
            text = stringResource(R.string.settings_theme),
            style = MaterialTheme.typography.bodyMedium,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.themeMode == mode,
                    onClick = { viewModel.setThemeMode(mode) },
                    label = {
                        Text(
                            stringResource(
                                when (mode) {
                                    ThemeMode.SYSTEM -> R.string.settings_theme_system
                                    ThemeMode.LIGHT -> R.string.settings_theme_light
                                    ThemeMode.DARK -> R.string.settings_theme_dark
                                }
                            )
                        )
                    },
                )
            }
        }

        SwitchRow(
            label = stringResource(R.string.settings_dynamic_color),
            description = stringResource(R.string.settings_dynamic_color_desc),
            checked = settings.dynamicColor,
            onCheckedChange = viewModel::setDynamicColor,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryCard(settings: AppSettings, viewModel: SettingsViewModel) {
    SectionCard(title = stringResource(R.string.settings_library)) {
        Text(
            text = stringResource(R.string.settings_poster_width),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.settings_poster_width_desc),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppSettings.POSTER_WIDTHS.forEach { width ->
                FilterChip(
                    selected = settings.posterWidth == width,
                    onClick = { viewModel.setPosterWidth(width) },
                    label = { Text("$width px") },
                )
            }
        }

        Text(
            text = stringResource(R.string.settings_page_size),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = stringResource(R.string.settings_page_size_desc),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppSettings.PAGE_SIZES.forEach { size ->
                FilterChip(
                    selected = settings.pageSize == size,
                    onClick = { viewModel.setPageSize(size) },
                    label = { Text(size.toString()) },
                )
            }
        }

        SwitchRow(
            label = stringResource(R.string.settings_live_updates),
            description = stringResource(R.string.settings_live_updates_desc),
            checked = settings.liveUpdates,
            onCheckedChange = viewModel::setLiveUpdates,
        )
    }
}

@Composable
private fun AboutCard() {
    SectionCard(title = stringResource(R.string.settings_about)) {
        InfoRow(
            label = stringResource(R.string.settings_about_version),
            value = BuildConfig.VERSION_NAME,
        )
        InfoRow(
            label = stringResource(R.string.settings_about_api),
            value = "/api/v1",
        )
        InfoRow(
            label = stringResource(R.string.settings_about_project),
            value = "Universal Video Scanner",
        )
    }
}

@Composable
private fun SwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    description: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
