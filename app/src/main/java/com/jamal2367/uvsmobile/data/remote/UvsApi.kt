package com.jamal2367.uvsmobile.data.remote

import com.jamal2367.uvsmobile.data.model.ApiIndex
import com.jamal2367.uvsmobile.data.model.EntryResponse
import com.jamal2367.uvsmobile.data.model.FilePathBody
import com.jamal2367.uvsmobile.data.model.FilePathsBody
import com.jamal2367.uvsmobile.data.model.LibraryPage
import com.jamal2367.uvsmobile.data.model.LibraryStats
import com.jamal2367.uvsmobile.data.model.MediaFileList
import com.jamal2367.uvsmobile.data.model.ScanStarted
import com.jamal2367.uvsmobile.data.model.ScanStatus
import com.jamal2367.uvsmobile.data.model.TotalResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.QueryMap

/**
 * Every endpoint `/api/v1` offers - the whole surface, nothing left out.
 *
 * The paths are relative: the host they end up at is decided per request by
 * [FailoverInterceptor].
 */
interface UvsApi {

    /** The version and the endpoint list - the cheapest way to check a token. */
    @GET("api/v1/")
    suspend fun index(): ApiIndex

    /**
     * A window onto the library.
     *
     * The filters, ranges, search, sort, order, paging and the `fields` subset
     * all travel in [params]; LibraryQuery builds them. `If-None-Match` lets
     * the server answer `304` when nothing changed, which is why the raw
     * [Response] is returned rather than the body alone.
     */
    @GET("api/v1/library")
    suspend fun library(
        @QueryMap params: Map<String, String>,
        @Header("If-None-Match") ifNoneMatch: String? = null,
    ): Response<LibraryPage>

    @GET("api/v1/library/stats")
    suspend fun stats(): LibraryStats

    @GET("api/v1/files")
    suspend fun mediaFiles(): MediaFileList

    @GET("api/v1/entries")
    suspend fun entry(@Query("file_path") filePath: String): EntryResponse

    @GET("api/v1/scan/status")
    suspend fun scanStatus(): ScanStatus

    @POST("api/v1/scan")
    suspend fun startScan(): ScanStarted

    @POST("api/v1/scan/files")
    suspend fun scanFiles(@Body body: FilePathsBody): ScanStarted

    @POST("api/v1/scan/cancel")
    suspend fun cancelScan(): ScanStarted

    @POST("api/v1/entries/scan")
    suspend fun scanEntry(@Body body: FilePathBody): EntryResponse

    @POST("api/v1/entries/rescan")
    suspend fun rescanEntry(@Body body: FilePathBody): EntryResponse

    @POST("api/v1/entries/delete")
    suspend fun deleteEntry(@Body body: FilePathBody): TotalResponse

    @POST("api/v1/database/clear")
    suspend fun clearDatabase(): TotalResponse
}
