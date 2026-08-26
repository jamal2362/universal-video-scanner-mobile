package com.jamal2367.uvsmobile.data.remote

import com.jamal2367.uvsmobile.data.model.EntryUpdatedEvent
import com.jamal2367.uvsmobile.data.model.FileDeletedEvent
import com.jamal2367.uvsmobile.data.model.ScanProgress
import com.jamal2367.uvsmobile.data.prefs.ServerConfig
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

/** What arrives on `/api/v1/events`, plus the state of the connection itself. */
sealed interface LiveEvent {
    /** The stream is open; what follows is live. */
    data object Connected : LiveEvent

    /** The stream is gone; the client will try again shortly. */
    data object Disconnected : LiveEvent

    /** Nothing is configured to connect to. */
    data object NotConfigured : LiveEvent

    /** The snapshot the server opens every stream with. */
    data class State(val progress: ScanProgress) : LiveEvent

    /** One step of a running scan. */
    data class Progress(val progress: ScanProgress) : LiveEvent

    /** One entry was written - fetch it with `updated_since` if you want it. */
    data class EntryUpdated(val event: EntryUpdatedEvent) : LiveEvent

    /** A file left the media directory. */
    data class FileDeleted(val event: FileDeletedEvent) : LiveEvent
}

/**
 * The live connection to `/api/v1/events`.
 *
 * Reconnects on its own and remembers the last event id it saw, so a phone that
 * loses its connection mid-scan is handed what it missed instead of having to
 * reconcile from scratch. The token travels as `?token=` here: it is the one
 * place the API documents that for, because an event stream carries no headers
 * in the browser and the server accepts both forms.
 */
class SseClient(
    private val client: OkHttpClient,
    private val router: ServerRouter,
    private val json: Json,
) {

    fun events(): Flow<LiveEvent> = flow {
        var lastEventId: String? = null
        var attempt = 0

        while (true) {
            // Switched off in the settings: the screens fall back to asking, and
            // no connection is held open in the background.
            if (!router.settings.liveUpdates) {
                emit(LiveEvent.Disconnected)
                delay(DISABLED_RETRY_MS)
                continue
            }

            val servers = router.candidates()
            if (servers.isEmpty()) {
                emit(LiveEvent.NotConfigured)
                delay(NOT_CONFIGURED_RETRY_MS)
                continue
            }

            // The stream addresses a server directly rather than going through
            // the failover interceptor, so it walks the same list itself: an
            // address that never opens hands over to the next one.
            val server = servers[attempt % servers.size]

            var sawAnything = false
            connect(server, lastEventId).collect { (id, event) ->
                if (id != null) lastEventId = id
                sawAnything = true
                emit(event)
            }

            emit(LiveEvent.Disconnected)
            if (sawAnything) {
                attempt = 0
                // What the server itself asks a client to wait.
                delay(RECONNECT_MS)
            } else {
                attempt++
                // Never even opened: try the other address, and back off so an
                // instance that is simply not there is not hammered.
                delay(if (attempt % servers.size == 0) FAILED_RETRY_MS else 0)
            }
        }
    }

    private fun connect(server: ServerConfig, lastEventId: String?): Flow<Pair<String?, LiveEvent>> =
        callbackFlow {
            val url = server.baseUrl.toEventsUrl(server.token, lastEventId)
            val request = Request.Builder()
                .url(url)
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")
                .apply {
                    if (server.token.isNotBlank()) {
                        header(FailoverInterceptor.TOKEN_HEADER, server.token.trim())
                    }
                    if (lastEventId != null) header("Last-Event-ID", lastEventId)
                }
                .build()

            val listener = object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: Response) {
                    trySend(null to LiveEvent.Connected)
                }

                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String,
                ) {
                    parse(type, data)?.let { trySend(id to it) }
                }

                override fun onClosed(eventSource: EventSource) {
                    close()
                }

                override fun onFailure(
                    eventSource: EventSource,
                    t: Throwable?,
                    response: Response?,
                ) {
                    // Not an error worth surfacing: the outer loop reconnects,
                    // and a dropped stream is the normal state of a phone.
                    close()
                }
            }

            val source = EventSources.createFactory(client).newEventSource(request, listener)
            awaitClose { source.cancel() }
        }

    private fun parse(type: String?, data: String): LiveEvent? = try {
        when (type) {
            "scan_state" -> LiveEvent.State(json.decodeFromString(ScanProgress.serializer(), data))
            "scan_progress" -> LiveEvent.Progress(json.decodeFromString(ScanProgress.serializer(), data))
            "entry_updated" -> LiveEvent.EntryUpdated(
                json.decodeFromString(EntryUpdatedEvent.serializer(), data)
            )

            "file_deleted" -> LiveEvent.FileDeleted(
                json.decodeFromString(FileDeletedEvent.serializer(), data)
            )

            else -> null
        }
    } catch (e: Exception) {
        // An event this build does not understand is not worth dropping the
        // stream for - the next one may well be one it does.
        null
    }

    private fun String.toEventsUrl(token: String, lastEventId: String?): String = buildString {
        append(this@toEventsUrl)
        append("/api/v1/events")
        val params = buildList {
            if (token.isNotBlank()) add("token=${token.trim().urlEncoded()}")
            if (lastEventId != null) add("last_event_id=${lastEventId.urlEncoded()}")
        }
        if (params.isNotEmpty()) {
            append("?")
            append(params.joinToString("&"))
        }
    }

    private fun String.urlEncoded(): String =
        java.net.URLEncoder.encode(this, Charsets.UTF_8.name())

    private companion object {
        const val RECONNECT_MS = 3_000L
        const val FAILED_RETRY_MS = 10_000L
        const val NOT_CONFIGURED_RETRY_MS = 15_000L
        const val DISABLED_RETRY_MS = 30_000L
    }
}
