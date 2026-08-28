package com.jamal2367.uvsmobile.data.prefs

import com.jamal2367.uvsmobile.data.model.FilterField
import com.jamal2367.uvsmobile.data.model.RangeField
import com.jamal2367.uvsmobile.data.model.RangeValue
import com.jamal2367.uvsmobile.data.model.SortOption
import com.jamal2367.uvsmobile.data.model.SortOrder

/**
 * One Universal Video Scanner instance the app may talk to.
 *
 * Two of these are stored - the address inside the network and the one that
 * reaches the same instance from outside - because those differ in every part
 * that matters: scheme, host, port, and often the token, when the way in from
 * outside is a reverse proxy of its own.
 */
data class ServerConfig(
    val enabled: Boolean = false,
    val useHttps: Boolean = false,
    val host: String = "",
    val port: Int = DEFAULT_PORT,
    val token: String = "",
) {
    /** Whether this one is filled in far enough to be worth a request. */
    val isUsable: Boolean
        get() = enabled && host.isNotBlank() && port in 1..65535

    /** The instance's root, without a trailing slash: `http://192.168.1.10:2367`. */
    val baseUrl: String
        get() = "${if (useHttps) "https" else "http"}://${host.trim().trimEnd('/')}:$port"

    /** What to call this server in a message to the user. */
    val label: String
        get() = if (host.isBlank()) "" else "${host.trim()}:$port"

    companion object {
        const val DEFAULT_PORT = 2367
    }
}

/** Which of the two servers the app is allowed to reach for. */
enum class ConnectionMode {
    /** The local one first; the remote one whenever it cannot be reached. */
    AUTO,

    /** Only ever the local address. */
    PRIMARY_ONLY,

    /** Only ever the remote address. */
    SECONDARY_ONLY,
}

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class LibraryLayout { GRID, LIST }

/** Everything the app remembers between launches. */
data class AppSettings(
    val primary: ServerConfig = ServerConfig(enabled = true),
    val secondary: ServerConfig = ServerConfig(),
    val connectionMode: ConnectionMode = ConnectionMode.AUTO,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val libraryLayout: LibraryLayout = LibraryLayout.GRID,
    /**
     * The order the library was last put in.
     *
     * Remembered like the layout is: someone who sorts by year is not asking
     * for one screen, and having to pick it again after every launch is a tap
     * for an answer they already gave.
     */
    val librarySort: SortOption = SortOption.FILENAME,
    val librarySortOrder: SortOrder = SortOption.FILENAME.defaultOrder,
    /**
     * The narrowing the library was last looked at through.
     *
     * Kept for the same reason the order is: someone who only ever wants their
     * Dolby Vision titles has said so once. The search term is not kept with
     * it - that is a question asked about one moment, not a way of looking at
     * the library, and a library that opens already searching for something
     * typed yesterday looks empty for no reason anyone can see.
     */
    val libraryFilters: Map<FilterField, String> = emptyMap(),
    val libraryRanges: Map<RangeField, RangeValue> = emptyMap(),
    val posterWidth: Int = 320,
    /**
     * How many covers stand side by side in the library's grid.
     *
     * A count rather than a width, because that is the thing anyone looking at
     * the screen is actually deciding: three is what fits a phone comfortably,
     * two makes the covers large enough to read a title off, four fits more of
     * the library on screen at once. One is the exception - a single upright
     * cover across the whole screen would be taller than the screen, so that
     * column shows the 16:9 backdrop instead.
     */
    val gridColumns: Int = DEFAULT_GRID_COLUMNS,
    val pageSize: Int = PAGE_SIZE_ALL,
    val liveUpdates: Boolean = true,
) {
    /**
     * How many entries one page of the library holds, or null for all of them.
     *
     * Stored as a number because preferences hold numbers; [PAGE_SIZE_ALL]
     * means the library is not cut into pages at all, which is also what the
     * API does when it is asked without a `limit`.
     */
    val entriesPerPage: Int?
        get() = pageSize.takeIf { it > 0 }

    /** True once at least one server is filled in far enough to try. */
    val isConfigured: Boolean
        get() = servers().isNotEmpty()

    /**
     * The servers to try, in the order they should be tried.
     *
     * The mode decides the list rather than a flag read at the call site, so
     * every caller - a request, a poster, the event stream - fails over the
     * same way.
     */
    fun servers(): List<ServerConfig> = when (connectionMode) {
        ConnectionMode.AUTO -> listOf(primary, secondary).filter { it.isUsable }
        ConnectionMode.PRIMARY_ONLY -> listOf(primary).filter { it.isUsable }
        ConnectionMode.SECONDARY_ONLY -> listOf(secondary).filter { it.isUsable }
    }

    companion object {
        val POSTER_WIDTHS = listOf(160, 320, 480, 640)

        /** What the grid holds until someone says otherwise. */
        const val DEFAULT_GRID_COLUMNS = 3

        val GRID_COLUMNS = listOf(1, 2, 3, 4)

        /** The one column that is laid out for the backdrop, not the cover. */
        const val SINGLE_GRID_COLUMN = 1

        /** The page size that means "do not page at all". */
        const val PAGE_SIZE_ALL = 0

        val PAGE_SIZES = listOf(30, 60, 100, 200, PAGE_SIZE_ALL)
    }
}
