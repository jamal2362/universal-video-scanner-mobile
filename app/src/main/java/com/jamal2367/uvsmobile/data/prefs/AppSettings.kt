package com.jamal2367.uvsmobile.data.prefs

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
    val posterWidth: Int = 320,
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

        /** The page size that means "do not page at all". */
        const val PAGE_SIZE_ALL = 0

        val PAGE_SIZES = listOf(30, 60, 100, 200, PAGE_SIZE_ALL)
    }
}
