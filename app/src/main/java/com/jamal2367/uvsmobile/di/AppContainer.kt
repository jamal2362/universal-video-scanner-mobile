package com.jamal2367.uvsmobile.di

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.jamal2367.uvsmobile.BuildConfig
import com.jamal2367.uvsmobile.data.prefs.SettingsRepository
import com.jamal2367.uvsmobile.data.prefs.StoredReachability
import com.jamal2367.uvsmobile.data.remote.ConnectionTester
import com.jamal2367.uvsmobile.data.remote.FailoverInterceptor
import com.jamal2367.uvsmobile.data.remote.ServerRouter
import com.jamal2367.uvsmobile.data.remote.LiveEvent
import com.jamal2367.uvsmobile.data.remote.SseClient
import com.jamal2367.uvsmobile.data.remote.UpdateChecker
import com.jamal2367.uvsmobile.data.remote.UvsApi
import com.jamal2367.uvsmobile.data.repository.LibraryPageStore
import com.jamal2367.uvsmobile.data.repository.ScannerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.shareIn
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import okio.Path.Companion.toOkioPath
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * The app's single graph, built by hand.
 *
 * Small enough that a framework would be more machinery than the whole network
 * layer: everything is created once, lazily, and handed to the view models by
 * the factories in com.jamal2367.uvsmobile.ui.ViewModelFactories.
 */
class AppContainer(private val context: Context) {

    /**
     * Lenient on purpose. The scanner stores whatever the probes and the online
     * lookups produced, so a field can arrive as a number where another entry
     * has a string, and a future version may add keys this build never heard of
     * - neither should turn into an empty screen.
     */
    val json: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(context) }

    /**
     * Built with the note of what last answered, so a start away from home
     * goes to the address that works rather than waiting out the one that does
     * not.
     */
    val router: ServerRouter by lazy { ServerRouter(StoredReachability(context)) }

    /**
     * Short connect timeout on purpose: in automatic mode a local address that
     * is not on this network has to fail quickly, because the fallback to the
     * remote one waits behind it. An address that still has another one behind
     * it is cut shorter again, in the failover interceptor.
     */
    private val baseClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(6, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * The logger goes on after whatever rewrites the request, never before it:
     * ahead of the failover interceptor every line would read
     * `http://localhost/...` - the placeholder Retrofit was built against -
     * instead of the address the attempt actually went to, which is the one
     * thing a log of a failing connection is read for.
     */
    private fun OkHttpClient.Builder.withDebugLogging(): OkHttpClient.Builder = apply {
        if (BuildConfig.DEBUG) {
            addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC }
            )
        }
    }

    private val apiClient: OkHttpClient by lazy {
        baseClient.newBuilder()
            .addInterceptor(FailoverInterceptor(router))
            .withDebugLogging()
            .build()
    }

    /** For everything that addresses a server itself, without the failover. */
    private val directClient: OkHttpClient by lazy {
        baseClient.newBuilder().withDebugLogging().build()
    }

    /**
     * The event stream gets its own client: it addresses a server directly
     * rather than through the failover interceptor, and it must never time out
     * while reading - an idle stream sends a keep-alive only every 30 seconds.
     */
    private val streamClient: OkHttpClient by lazy {
        directClient.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    private val api: UvsApi by lazy {
        Retrofit.Builder()
            // Never contacted: every request is retargeted at a configured
            // server before it leaves. Retrofit only insists on having one.
            .baseUrl("http://localhost/")
            .client(apiClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(UvsApi::class.java)
    }

    /**
     * The library page a start paints the screen with before anything has been
     * asked of a server.
     */
    private val libraryPageStore: LibraryPageStore by lazy {
        LibraryPageStore(context.filesDir.resolve("library_page.json"), json)
    }

    val repository: ScannerRepository by lazy {
        ScannerRepository(api, json, libraryPageStore)
    }

    val sseClient: SseClient by lazy { SseClient(streamClient, router, json) }

    /** Checks one address in isolation, for the button in the settings. */
    val connectionTester: ConnectionTester by lazy { ConnectionTester(directClient, json) }

    /**
     * Asks GitHub for the newest release, once per launch.
     *
     * On the plain client, like everything that addresses a host of its own:
     * the failover interceptor exists to retarget requests at whichever
     * scanner is answering, and github.com is neither of them.
     */
    val updateChecker: UpdateChecker by lazy { UpdateChecker(directClient, json) }

    private val containerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * One event stream for the whole app.
     *
     * Every screen that wants live progress shares this connection instead of
     * opening its own: the server fans an event out to each subscriber, and
     * five of them from one phone would be five times the work for nothing. It
     * is dropped a few seconds after the last screen stops listening, so a
     * backgrounded app is not holding a connection open.
     */
    val liveEvents: SharedFlow<LiveEvent> by lazy {
        sseClient.events().shareIn(
            scope = containerScope,
            started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
            replay = 1,
        )
    }

    /**
     * Posters are addressed absolutely - the URL already names the server the
     * app is talking to and carries the token as `?token=`, which is the one
     * place the API documents that for - so they skip the failover interceptor
     * and use the plain client. They are kept on disk because a cached poster
     * never changes under its name: the scanner writes a new name instead.
     */
    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { directClient }))
            }
            .memoryCache {
                MemoryCache.Builder().maxSizePercent(context, 0.20).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("poster_cache").toOkioPath())
                    .maxSizeBytes(128L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
