package com.jamal2367.uvsmobile.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jamal2367.uvsmobile.data.model.FilterField
import com.jamal2367.uvsmobile.data.model.RangeField
import com.jamal2367.uvsmobile.data.model.RangeValue
import com.jamal2367.uvsmobile.data.model.SortOption
import com.jamal2367.uvsmobile.data.model.SortOrder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "uvs_settings")

/** One end of a range, the other, or both - as it is written to preferences. */
@Serializable
private data class StoredRange(val min: Double? = null, val max: Double? = null)

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

    /** Keep an order, and the direction picking it settled on. */
    suspend fun setLibrarySort(sort: SortOption, order: SortOrder) = edit {
        it[KEY_SORT] = sort.name
        it[KEY_SORT_ORDER] = order.name
    }

    suspend fun setLibrarySortOrder(order: SortOrder) = edit {
        it[KEY_SORT_ORDER] = order.name
    }

    /** Keep the filters and ranges the library is being read through. */
    suspend fun setLibraryNarrowing(
        filters: Map<FilterField, String>,
        ranges: Map<RangeField, RangeValue>,
    ) = edit {
        it[KEY_FILTERS] = json.encodeToString(filters.mapKeys { (field, _) -> field.name })
        it[KEY_RANGES] = json.encodeToString(
            ranges.mapKeys { (field, _) -> field.name }
                .mapValues { (_, range) -> StoredRange(range.min, range.max) },
        )
    }

    suspend fun setPosterWidth(width: Int) = edit {
        it[KEY_POSTER_WIDTH] = width
    }

    /** How many covers the grid puts side by side, within what it offers. */
    suspend fun setGridColumns(columns: Int) = edit {
        it[KEY_GRID_COLUMNS] = columns.coerceIn(GRID_COLUMN_RANGE)
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
        librarySort = this[KEY_SORT].toEnum(SortOption.FILENAME),
        librarySortOrder = this[KEY_SORT_ORDER]
            .toEnum(this[KEY_SORT].toEnum(SortOption.FILENAME).defaultOrder),
        libraryFilters = readFilters(),
        libraryRanges = readRanges(),
        posterWidth = this[KEY_POSTER_WIDTH] ?: 320,
        // Coerced rather than trusted: a count written by a build that offered
        // a wider choice would otherwise lay the grid out in a way this one has
        // no chip to get back from.
        gridColumns = (this[KEY_GRID_COLUMNS] ?: AppSettings.DEFAULT_GRID_COLUMNS)
            .coerceIn(GRID_COLUMN_RANGE),
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

    /**
     * The stored filters, minus anything this build no longer knows.
     *
     * A field that has since been renamed or dropped is left out rather than
     * refused: one stale key is not a reason to open the library unfiltered
     * when the other four are still good, and a value the API no longer
     * matches would only ever answer with nothing.
     */
    private fun Preferences.readFilters(): Map<FilterField, String> =
        decode<Map<String, String>>(this[KEY_FILTERS])
            .orEmpty()
            .mapNotNull { (name, value) ->
                val field = FilterField.entries.firstOrNull { it.name == name }
                    ?: return@mapNotNull null
                value.takeIf { it.isNotBlank() }?.let { field to it }
            }
            .toMap()

    private fun Preferences.readRanges(): Map<RangeField, RangeValue> =
        decode<Map<String, StoredRange>>(this[KEY_RANGES])
            .orEmpty()
            .mapNotNull { (name, stored) ->
                val field = RangeField.entries.firstOrNull { it.name == name }
                    ?: return@mapNotNull null
                RangeValue(stored.min, stored.max).takeIf { !it.isEmpty }?.let { field to it }
            }
            .toMap()

    /**
     * Read a stored blob back, or nothing at all.
     *
     * Preferences are as old as the install: a blob written by a build that
     * shaped it differently is not worth an exception on the way to the first
     * screen, and the default it falls back to is a library nobody narrowed.
     */
    private inline fun <reified T> decode(raw: String?): T? = raw?.let {
        try {
            json.decodeFromString<T>(it)
        } catch (_: Throwable) {
            null
        }
    }

    private inline fun <reified T : Enum<T>> String?.toEnum(fallback: T): T =
        this?.let { name -> enumValues<T>().firstOrNull { it.name == name } } ?: fallback

    private companion object {
        val json = Json { ignoreUnknownKeys = true }

        /** The counts the settings screen offers, as a range to clamp to. */
        val GRID_COLUMN_RANGE = AppSettings.GRID_COLUMNS.min()..AppSettings.GRID_COLUMNS.max()

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
        val KEY_SORT = stringPreferencesKey("library_sort")
        val KEY_SORT_ORDER = stringPreferencesKey("library_sort_order")
        val KEY_FILTERS = stringPreferencesKey("library_filters")
        val KEY_RANGES = stringPreferencesKey("library_ranges")
        val KEY_POSTER_WIDTH = intPreferencesKey("poster_width")
        val KEY_GRID_COLUMNS = intPreferencesKey("grid_columns")
        val KEY_PAGE_SIZE = intPreferencesKey("page_size")
        val KEY_LIVE_UPDATES = booleanPreferencesKey("live_updates")
    }
}
