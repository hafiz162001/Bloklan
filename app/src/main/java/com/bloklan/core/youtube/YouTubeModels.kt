package com.bloklan.core.youtube

data class YouTubeVideo(
    val id: String,
    val title: String,
    val author: String,
    val authorAvatarUrl: String? = null,
    val thumbnailUrl: String,
    val durationFormatted: String = "",
    val viewsFormatted: String = "",
    val publishedText: String = ""
)

data class VideoStreamDetails(
    val id: String,
    val title: String,
    val author: String,
    val authorAvatarUrl: String? = null,
    val subscriberCountText: String = "",
    val description: String = "",
    val viewsFormatted: String = "",
    val likesCount: Long = 0L,
    val streamUrl: String,
    val hlsUrl: String? = null,
    val quality: String = "720p",
    val relatedVideos: List<YouTubeVideo> = emptyList()
)
