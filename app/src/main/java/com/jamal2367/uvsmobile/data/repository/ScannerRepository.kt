package com.jamal2367.uvsmobile.data.repository

import com.jamal2367.uvsmobile.data.model.ApiErrorBody
import com.jamal2367.uvsmobile.data.model.ApiIndex
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
 */
class ScannerRepository(
    private val api: UvsApi,
    private val json: Json,
) {

    private data class CachedPage(val etag: String, val page: LibraryPage)

    private val pageCache = ConcurrentHashMap<String, CachedPage>()

    /** The version and endpoint list - what a connection test asks for. */
    suspend fun index(): ApiIndex = call { api.index() }

    /**
     * One window onto the library.
     *
     * The answer to the exact same question is remembered with its ETag; when
     * the library has not changed, the server replies `304` and the remembered
     * page is handed back. That is what makes reopening the app instant on a
     * library of thousands.
     */
    suspend fun library(query: LibraryQuery, limit: Int?, offset: Int): LibraryPage {
        val params = query.toParams(limit, offset)
        val signature = params.entries.sortedBy { it.key }.joinToString("&") { "${it.key}=${it.value}" }
        val cached = pageCache[signature]

        return call {
            val response = api.library(params, cached?.etag)
            when {
                response.code() == 304 && cached != null -> cached.page
                response.isSuccessful -> {
                    val page = response.body() ?: throw ApiFailure.Malformed(null)
                    response.headers()["ETag"]?.let { pageCache[signature] = CachedPage(it, page) }
                    page
                }

                else -> throw response.toFailure()
            }
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
    fun invalidateCache() = pageCache.clear()

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
    } catch (notConfigured: NoServerConfiguredException) {
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
