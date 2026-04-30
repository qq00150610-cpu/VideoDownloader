package com.videodownloader.data

/**
 * Represents a video quality option from the download API
 */
data class VideoQuality(
    val url: String,
    val quality: String,       // e.g. "720p", "1080p"
    val format: String = "mp4",
    val size: String = ""      // e.g. "15.2 MB"
)

/**
 * Result from parsing a Twitter/X URL
 */
data class ParseResult(
    val title: String = "",
    val thumbnail: String = "",
    val qualities: List<VideoQuality> = emptyList(),
    val error: String? = null
)

/**
 * Represents a downloaded video on the device
 */
data class LocalVideo(
    val id: Long,
    val title: String,
    val path: String,
    val uri: String,
    val duration: Long = 0,
    val size: Long = 0,
    val dateAdded: Long = 0,
    val thumbnailUri: String = ""
)

/**
 * Download task state
 */
data class DownloadTask(
    val id: String,
    val url: String,
    val title: String,
    val quality: String,
    val progress: Int = 0,
    val status: DownloadStatus = DownloadStatus.PENDING,
    val filePath: String = "",
    val error: String? = null
)

enum class DownloadStatus {
    PENDING, DOWNLOADING, COMPLETED, FAILED, CANCELLED
}

/**
 * Playback history record
 */
data class PlaybackHistory(
    val id: String,
    val title: String,
    val uri: String,
    val timestamp: Long = System.currentTimeMillis(),
    val duration: Long = 0
)
