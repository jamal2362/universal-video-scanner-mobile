package com.jamal2367.uvsmobile.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "uvs_settings")

/**
 * Where the settings live.
 *
 * Everything the app is configured with is one flow, so a screen never reads a
 * half-changed configuration: switching the connection mode and the server it
 * points at arrive together.
 */
class SettingsRepository(private val context: Context) {

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun setPrimary(config: ServerConfig) = writeServer(PRIMARY, config)

    suspend fun setSecondary(config: ServerConfig) = writeServer(SECONDARY, config)

    suspend fun setConnectionMode(mode: ConnectionMode) = edit {
        it[KEY_MODE] = mode.name
    }

    suspend fun setThemeMode(mode: ThemeMode) = edit {
        it[KEY_THEME] = mode.name
    }

    suspend fun setDynamicColor(enabled: Boolean) = edit {
        it[KEY_DYNAMIC_COLOR] = enabled
    }

    suspend fun setLibraryLayout(layout: LibraryLayout) = edit {
        it[KEY_LAYOUT] = layout.name
    }

    suspend fun setPosterWidth(width: Int) = edit {
        it[KEY_POSTER_WIDTH] = width
    }

    suspend fun setPageSize(size: Int) = edit {
        it[KEY_PAGE_SIZE] = size
    }

    suspend fun setLiveUpdates(enabled: Boolean) = edit {
        it[KEY_LIVE_UPDATES] = enabled
    }

    private suspend fun writeServer(prefix: String, config: ServerConfig) = edit {
        it[booleanPreferencesKey("$prefix$SUFFIX_ENABLED")] = config.enabled
        it[booleanPreferencesKey("$prefix$SUFFIX_HTTPS")] = config.useHttps
        it[stringPreferencesKey("$prefix$SUFFIX_HOST")] = config.host.trim()
        it[intPreferencesKey("$prefix$SUFFIX_PORT")] = config.port
        it[stringPreferencesKey("$prefix$SUFFIX_TOKEN")] = config.token.trim()
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private fun Preferences.toSettings(): AppSettings = AppSettings(
        // The local server is on by default: a first launch should only need an
        // address typed in, not a switch found first.
        primary = readServer(PRIMARY, defaultEnabled = true),
        secondary = readServer(SECONDARY, defaultEnabled = false),
        connectionMode = this[KEY_MODE].toEnum(ConnectionMode.AUTO),
        themeMode = this[KEY_THEME].toEnum(ThemeMode.SYSTEM),
        dynamicColor = this[KEY_DYNAMIC_COLOR] ?: true,
        libraryLayout = this[KEY_LAYOUT].toEnum(LibraryLayout.GRID),
        posterWidth = this[KEY_POSTER_WIDTH] ?: 320,
        pageSize = this[KEY_PAGE_SIZE] ?: AppSettings.PAGE_SIZE_ALL,
        liveUpdates = this[KEY_LIVE_UPDATES] ?: true,
    )

    private fun Preferences.readServer(prefix: String, defaultEnabled: Boolean) = ServerConfig(
        enabled = this[booleanPreferencesKey("$prefix$SUFFIX_ENABLED")] ?: defaultEnabled,
        useHttps = this[booleanPreferencesKey("$prefix$SUFFIX_HTTPS")] ?: false,
        host = this[stringPreferencesKey("$prefix$SUFFIX_HOST")].orEmpty(),
        port = this[intPreferencesKey("$prefix$SUFFIX_PORT")] ?: ServerConfig.DEFAULT_PORT,
        token = this[stringPreferencesKey("$prefix$SUFFIX_TOKEN")].orEmpty(),
    )

    private inline fun <reified T : Enum<T>> String?.toEnum(fallback: T): T =
        this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: fallback

    private companion object {
        const val PRIMARY = "primary"
        const val SECONDARY = "secondary"
        const val SUFFIX_ENABLED = "_enabled"
        const val SUFFIX_HTTPS = "_https"
        const val SUFFIX_HOST = "_host"
        const val SUFFIX_PORT = "_port"
        const val SUFFIX_TOKEN = "_token"

        val KEY_MODE = stringPreferencesKey("connection_mode")
        val KEY_THEME = stringPreferencesKey("theme_mode")
        val KEY_DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val KEY_LAYOUT = stringPreferencesKey("library_layout")
        val KEY_POSTER_WIDTH = intPreferencesKey("poster_width")
        val KEY_PAGE_SIZE = intPreferencesKey("page_size")
        val KEY_LIVE_UPDATES = booleanPreferencesKey("live_updates")
    }
}
