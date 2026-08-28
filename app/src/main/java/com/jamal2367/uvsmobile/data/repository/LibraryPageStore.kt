package com.jamal2367.uvsmobile.data.repository

import com.jamal2367.uvsmobile.data.model.LibraryPage
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** The remembered page, and what it was the answer to. */
@Serializable
data class StoredLibraryPage(
    val signature: String,
    val etag: String,
    val page: LibraryPage,
)

/**
 * The first page of the library, as it was last seen, kept between launches.
 *
 * The in-memory ETag makes reopening a *running* app instant; a cold start had
 * nothing at all, and every one of them showed a spinner for as long as the
 * whole library took to arrive - which over a mobile connection is seconds.
 * This is what the screen is filled with while that request is on its way.
 *
 * The ETag is kept with the page rather than the page alone, so the request
 * that replaces it is usually answered `304` with no body: the content is
 * already right, and the whole exchange costs one round trip.
 *
 * Written and read on a background thread by [ScannerRepository] - a library of
 * a couple of thousand titles is close to a megabyte of JSON.
 */
class LibraryPageStore(private val file: File, private val json: Json) {

    /**
     * The remembered page, or nothing.
     *
     * Anything unreadable is nothing: the file was written by a build that
     * shaped an entry differently, or a write was cut short by the app being
     * killed. Neither is worth an exception on the way to the first screen.
     */
    fun read(): StoredLibraryPage? = try {
        file.takeIf { it.isFile }
            ?.readText()
            ?.takeIf { it.isNotBlank() }
            ?.let { json.decodeFromString(StoredLibraryPage.serializer(), it) }
    } catch (_: Throwable) {
        null
    }

    /**
     * Keep this page for the next launch.
     *
     * Written beside the real file and moved into place, so a write that is
     * interrupted leaves the last good one where it was rather than half of a
     * new one. A library too large to be worth the disk is not kept at all -
     * the session's own ETag still spares the transfer while the app is up.
     */
    fun write(stored: StoredLibraryPage) {
        if (stored.page.files.size > MAX_ENTRIES) {
            clear()
            return
        }
        try {
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.writeText(json.encodeToString(StoredLibraryPage.serializer(), stored))
            if (!temporary.renameTo(file)) {
                temporary.delete()
            }
        } catch (_: Throwable) {
            // A page that could not be kept is only a slower start next time.
        }
    }

    fun clear() {
        try {
            file.delete()
            File(file.parentFile, "${file.name}.tmp").delete()
        } catch (_: Throwable) {
            // Nothing to do about it, and nothing depends on it having worked.
        }
    }

    private companion object {
        /** Past this, the file costs more to read than the request it saves. */
        const val MAX_ENTRIES = 5_000
    }
}
