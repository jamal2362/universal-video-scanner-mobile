package com.jamal2367.uvsmobile.data.repository

import com.jamal2367.uvsmobile.data.model.ApiErrorBody
import com.jamal2367.uvsmobile.data.model.FilePathBody
import com.jamal2367.uvsmobile.data.model.FilePathsBody
import com.jamal2367.uvsmobile.data.model.LibraryEntry
import com.jamal2367.uvsmobile.data.model.LibraryPage
import com.jamal2367.uvsmobile.data.model.LibraryQuery
import com.jamal2367.uvsmobile.data.model.LibraryStats
import com.jamal2367.uvsmobile.data.model.MediaFile
import com.jamal2367.uvsmobile.data.model.SortOption
import com.jamal2367.uvsmobile.data.model.ScanStarted
import com.jamal2367.uvsmobile.data.model.ScanStatus
import com.jamal2367.uvsmobile.data.remote.ApiFailure
import com.jamal2367.uvsmobile.data.remote.NoServerConfiguredException
import com.jamal2367.uvsmobile.data.remote.ServerUnreachableException
import com.jamal2367.uvsmobile.data.remote.UvsApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap

/**
 * Everything the app can ask of a Universal Video Scanner.
 *
 * One layer above Retrofit, for two reasons: every failure comes back as an
 * [ApiFailure] a screen can put words to, and the library's ETag is kept here
 * so a repeated question costs a `304` and no body at all.
 *
 * The first page of that outlives the process, in [store]: a cold start would
 * otherwise have nothing to put on screen until the whole library had arrived.
 */
class ScannerRepository(
    private val api: UvsApi,
    private val json: Json,
    private val store: LibraryPageStore? = null,
) {

    private data class CachedPage(val etag: String, val page: LibraryPage)

    private val pageCache = ConcurrentHashMap<String, CachedPage>()

    /** Whether the page kept from the last launch has been looked for yet. */
    @Volatile
    private var storeRead = false

    /**
     * One window onto the library.
     *
     * The answer to the exact same question is remembered with its ETag; when
     * the library has not changed, the server replies `304` and the remembered
     * page is handed back. That is what makes reopening the app instant on a
     * library of thousands.
     */
    suspend fun library(query: LibraryQuery, limit: Int?, offset: Int): LibraryPage {
        val signature = signatureOf(query, limit, offset)
        val cached = remembered(signature)

        return call {
            val response = api.library(query.toParams(limit, offset), cached?.etag)
            when {
                response.code() == 304 && cached != null -> cached.page
                response.isSuccessful -> {
                    val page = response.body() ?: throw ApiFailure.Malformed(null)
                    response.headers()["ETag"]?.let { etag ->
                        pageCache[signature] = CachedPage(etag, page)
                        keep(signature, etag, page, query, offset)
                    }
                    page
                }

                else -> throw response.toFailure()
            }
        }
    }

    /**
     * The answer to this exact question as it was last seen, without asking.
     *
     * What the library screen paints itself with while the real request is on
     * its way. Null means there is nothing remembered for it - a first launch,
     * a different order, a search nobody has run before.
     */
    suspend fun cachedLibrary(query: LibraryQuery, limit: Int?, offset: Int): LibraryPage? =
        remembered(signatureOf(query, limit, offset))?.page

    /** What identifies one question, independent of which address answers it. */
    private fun signatureOf(query: LibraryQuery, limit: Int?, offset: Int): String =
        query.toParams(limit, offset).entries
            .sortedBy { it.key }
            .joinToString("&") { "${it.key}=${it.value}" }

    /**
     * The remembered answer to one question - from this session, or from the
     * page the last one left behind.
     */
    private suspend fun remembered(signature: String): CachedPage? {
        pageCache[signature]?.let { return it }
        val store = store ?: return null
        if (storeRead) return null

        // Only ever read once: after that the map is the whole truth, and a
        // question with nothing remembered for it must not go back to the disk
        // on every keystroke of a search.
        storeRead = true
        val stored = withContext(Dispatchers.IO) { store.read() } ?: return null
        val restored = CachedPage(stored.etag, stored.page)
        pageCache.putIfAbsent(stored.signature, restored)
        return restored.takeIf { stored.signature == signature }
    }

    /**
     * Keep this page for the next launch, if it is the one a launch will want.
     *
     * There is room for exactly one, so it has to be the page the library
     * screen opens on: the first, in the order and narrowing that were stored,
     * with the fields a row is drawn from. A search nobody will type again, a
     * projection the filter sheet asked for, or the fifth page of anything
     * would take that place and answer nothing on the next start.
     *
     * Reached only when the server actually sent a body - a library that has
     * not changed comes back `304` and never gets this far - so this is a
     * write per change, not a write per refresh.
     */
    private suspend fun keep(
        signature: String,
        etag: String,
        page: LibraryPage,
        query: LibraryQuery,
        offset: Int,
    ) {
        val store = store ?: return
        if (!query.isWhatALaunchOpensOn || offset != 0) return
        storeRead = true
        withContext(Dispatchers.IO) {
            store.write(StoredLibraryPage(signature, etag, page))
        }
    }

    suspend fun stats(): LibraryStats = call { api.stats() }

    /**
     * The distinct values the library holds for a handful of fields.
     *
     * Asked for as a projection of the whole library rather than field by
     * field: the API has no "list the values" endpoint, but `fields=` cuts an
     * entry down to just these, which is a few hundred bytes per thousand
     * entries and comes back as a `304` on every later ask.
     *
     * Sorted and free of blanks and "Unknown", because this feeds a list of
     * choices - a chip that matches nothing is worse than no chip.
     */
    suspend fun distinctValues(fields: List<String>): Map<String, List<String>> {
        val page = library(
            query = LibraryQuery(fields = fields, sort = SortOption.FILENAME),
            limit = null,
            offset = 0,
        )

        val readers: Map<String, (LibraryEntry) -> String?> = mapOf(
            "hdr_detail" to { it.hdrDetail },
            "el_type" to { it.elType },
            "video_encoder" to { it.videoEncoder },
            "dv_cm_version" to { it.dvCmVersion },
            "hdr_format" to { it.hdrFormat },
            "resolution" to { it.resolution },
            "resolution_class" to { it.resolutionClass },
            "video_codec" to { it.videoCodec },
            "audio_codec" to { it.audioCodec },
        )

        return fields.mapNotNull { field ->
            val read = readers[field] ?: return@mapNotNull null
            val values = page.files
                .mapNotNull { read(it)?.trim() }
                .filter { it.isNotEmpty() && !it.equals("Unknown", ignoreCase = true) }
                .distinct()
                .sorted()
            field to values
        }.toMap()
    }

    suspend fun mediaFiles(): List<MediaFile> = call { api.mediaFiles().files }

    suspend fun entry(filePath: String): LibraryEntry =
        call { api.entry(filePath).entry ?: throw ApiFailure.Malformed(null) }

    suspend fun scanStatus(): ScanStatus = call { api.scanStatus() }

    suspend fun startFullScan(): ScanStarted = mutating { api.startScan() }

    suspend fun scanFiles(paths: List<String>): ScanStarted =
        mutating { api.scanFiles(FilePathsBody(paths)) }

    suspend fun cancelScan(): ScanStarted = call { api.cancelScan() }

    suspend fun scanEntry(filePath: String): LibraryEntry =
        mutating { api.scanEntry(FilePathBody(filePath)).entry ?: throw ApiFailure.Malformed(null) }

    suspend fun rescanEntry(filePath: String): LibraryEntry =
        mutating { api.rescanEntry(FilePathBody(filePath)).entry ?: throw ApiFailure.Malformed(null) }

    /** Removes the entry; answers with how many are left. */
    suspend fun deleteEntry(filePath: String): Int =
        mutating { api.deleteEntry(FilePathBody(filePath)).total }

    suspend fun clearDatabase(): Int = mutating { api.clearDatabase().total }

    /** Forget every remembered page - used when the server or its token changes. */
    fun invalidateCache() {
        pageCache.clear()
        // Nothing is left to read back, so there is nothing to go looking for.
        storeRead = true
        store?.clear()
    }

    /** A call that changes the library, after which no cached page can be trusted. */
    private suspend fun <T> mutating(block: suspend () -> T): T = call(block).also { invalidateCache() }

    /**
     * Turn whatever a call throws into an [ApiFailure].
     *
     * Cancellation is deliberately let through: a screen that went away is not
     * an error to report, and swallowing it here would keep coroutines alive.
     */
    private suspend fun <T> call(block: suspend () -> T): T = try {
        block()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: ApiFailure) {
        throw failure
    } catch (http: HttpException) {
        throw http.toFailure()
    } catch (unreachable: ServerUnreachableException) {
        throw ApiFailure.Unreachable(
            serverLabel = unreachable.attempted.joinToString(", ") { it.label },
            timedOut = unreachable.cause is SocketTimeoutException,
            cause = unreachable.cause,
        )
    } catch (_: NoServerConfiguredException) {
        throw ApiFailure.NotConfigured
    } catch (io: IOException) {
        throw ApiFailure.Unreachable(null, io is SocketTimeoutException, io)
    } catch (malformed: SerializationException) {
        throw ApiFailure.Malformed(malformed)
    }

    private fun HttpException.toFailure(): ApiFailure {
        val body = response()?.errorBody()?.string()
        return ApiFailure.Api(code(), body.errorCode(), body.errorMessage() ?: message())
    }

    private fun retrofit2.Response<*>.toFailure(): ApiFailure {
        val body = errorBody()?.string()
        return ApiFailure.Api(code(), body.errorCode(), body.errorMessage() ?: message())
    }

    private fun String?.parsed(): ApiErrorBody? = this?.takeIf { it.isNotBlank() }?.let {
        runCatching { json.decodeFromString(ApiErrorBody.serializer(), it) }.getOrNull()
    }

    private fun String?.errorCode(): String? = parsed()?.code

    private fun String?.errorMessage(): String? = parsed()?.error
}
