package com.jamal2367.uvsmobile.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `GET /api/v1` - the version and what the instance offers. */
@Serializable
data class ApiIndex(
    val success: Boolean = false,
    val version: String = "",
    val endpoints: Map<String, String> = emptyMap(),
)

/** `GET /api/v1/library` */
@Serializable
data class LibraryPage(
    val success: Boolean = false,
    val count: Int = 0,
    val total: Int = 0,
    val offset: Int = 0,
    val limit: Int? = null,
    val files: List<LibraryEntry> = emptyList(),
)

/** `GET /api/v1/library/stats` */
@Serializable
data class LibraryStats(
    val success: Boolean = false,
    val total: Int = 0,
    @SerialName("total_size") val totalSize: Double = 0.0,
    @SerialName("hdr_formats") val hdrFormats: Map<String, Int> = emptyMap(),
    val resolutions: Map<String, Int> = emptyMap(),
    @SerialName("resolution_classes") val resolutionClasses: Map<String, Int> = emptyMap(),
    @SerialName("video_codecs") val videoCodecs: Map<String, Int> = emptyMap(),
    @SerialName("audio_codecs") val audioCodecs: Map<String, Int> = emptyMap(),
)

/** One row of `GET /api/v1/files`. */
@Serializable
data class MediaFile(
    val path: String = "",
    val name: String = "",
    val scanned: Boolean = false,
)

/** `GET /api/v1/files` */
@Serializable
data class MediaFileList(
    val success: Boolean = false,
    val count: Int = 0,
    val files: List<MediaFile> = emptyList(),
)

/** `GET /api/v1/entries`, `POST /api/v1/entries/scan`, `POST /api/v1/entries/rescan` */
@Serializable
data class EntryResponse(
    val success: Boolean = false,
    val entry: LibraryEntry? = null,
)

/** The progress of the scan the server is running. */
@Serializable
data class ScanProgress(
    val status: String = "idle",
    val current: Int = 0,
    val total: Int = 0,
    val percent: Int = 0,
    val filename: String = "",
    @SerialName("new_files") val newFiles: Int? = null,
    @SerialName("removed_files") val removedFiles: Int? = null,
    @SerialName("total_files") val totalFiles: Int? = null,
    val error: String? = null,
) {
    val isScanning: Boolean get() = status == "scanning"
}

/** `GET /api/v1/scan/status` */
@Serializable
data class ScanStatus(
    val success: Boolean = false,
    val running: Boolean = false,
    val scan: ScanProgress = ScanProgress(),
)

/** `POST /api/v1/scan`, `POST /api/v1/scan/cancel` */
@Serializable
data class ScanStarted(
    val success: Boolean = false,
    val message: String? = null,
    val queued: Int? = null,
    val skipped: Int? = null,
)

/** `POST /api/v1/entries/delete`, `POST /api/v1/database/clear` */
@Serializable
data class TotalResponse(
    val success: Boolean = false,
    val total: Int = 0,
)

/** The body every failing endpoint answers with. */
@Serializable
data class ApiErrorBody(
    val success: Boolean = false,
    val error: String? = null,
    val code: String? = null,
)

/** The bodies the write endpoints take. */
@Serializable
data class FilePathBody(@SerialName("file_path") val filePath: String)

@Serializable
data class FilePathsBody(@SerialName("file_paths") val filePaths: List<String>)

/** The payload of the `entry_updated` event. */
@Serializable
data class EntryUpdatedEvent(
    @SerialName("file_path") val filePath: String = "",
    @SerialName("updated_at") val updatedAt: Double? = null,
)

/** The payload of the `file_deleted` event. */
@Serializable
data class FileDeletedEvent(
    @SerialName("file_path") val filePath: String? = null,
    val path: String? = null,
    val filename: String? = null,
    val action: String? = null,
    val total: Int? = null,
) {
    val affectedPath: String? get() = filePath ?: path
}
