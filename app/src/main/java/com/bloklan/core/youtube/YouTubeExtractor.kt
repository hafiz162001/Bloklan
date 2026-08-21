package com.bloklan.core.youtube

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object YouTubeExtractor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Multiple public Invidious API instances for high reliability and fallback
    private val INVIDIOUS_INSTANCES = listOf(
        "https://inv.nadeko.net",
        "https://yewtu.be",
        "https://invidious.nerdvpn.de",
        "https://iv.datura.network",
        "https://invidious.jing.rocks"
    )

    suspend fun getTrendingVideos(region: String = "ID"): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        for (baseUrl in INVIDIOUS_INSTANCES) {
            try {
                val url = "$baseUrl/api/v1/trending?region=$region"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) continue

                val jsonBody = response.body?.string() ?: continue
                val jsonArray = JSONArray(jsonBody)
                val list = mutableListOf<YouTubeVideo>()

                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.optJSONObject(i) ?: continue
                    val videoId = item.optString("videoId", "")
                    if (videoId.isEmpty()) continue

                    val title = item.optString("title", "")
                    val author = item.optString("author", "")
                    val lengthSeconds = item.optLong("lengthSeconds", 0L)
                    val viewCount = item.optLong("viewCount", 0L)
                    val publishedText = item.optString("publishedText", "")

                    val thumbs = item.optJSONArray("videoThumbnails")
                    var thumbUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                    if (thumbs != null && thumbs.length() > 0) {
                        thumbUrl = thumbs.optJSONObject(thumbs.length() - 1)?.optString("url") ?: thumbUrl
                    }

                    list.add(
                        YouTubeVideo(
                            id = videoId,
                            title = title,
                            author = author,
                            thumbnailUrl = thumbUrl,
                            durationFormatted = formatSeconds(lengthSeconds),
                            viewsFormatted = formatViewCount(viewCount),
                            publishedText = publishedText
                        )
                    )
                }

                if (list.isNotEmpty()) {
                    return@withContext list
                }
            } catch (e: Exception) {
                // Try next instance
            }
        }
        return@withContext emptyList()
    }

    suspend fun searchVideos(query: String): List<YouTubeVideo> = withContext(Dispatchers.IO) {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        for (baseUrl in INVIDIOUS_INSTANCES) {
            try {
                val url = "$baseUrl/api/v1/search?q=$encodedQuery&type=video"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) continue

                val jsonBody = response.body?.string() ?: continue
                val jsonArray = JSONArray(jsonBody)
                val list = mutableListOf<YouTubeVideo>()

                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.optJSONObject(i) ?: continue
                    val type = item.optString("type", "video")
                    if (type != "video") continue

                    val videoId = item.optString("videoId", "")
                    if (videoId.isEmpty()) continue

                    val title = item.optString("title", "")
                    val author = item.optString("author", "")
                    val lengthSeconds = item.optLong("lengthSeconds", 0L)
                    val viewCount = item.optLong("viewCount", 0L)
                    val publishedText = item.optString("publishedText", "")

                    val thumbs = item.optJSONArray("videoThumbnails")
                    var thumbUrl = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                    if (thumbs != null && thumbs.length() > 0) {
                        thumbUrl = thumbs.optJSONObject(0)?.optString("url") ?: thumbUrl
                    }

                    list.add(
                        YouTubeVideo(
                            id = videoId,
                            title = title,
                            author = author,
                            thumbnailUrl = thumbUrl,
                            durationFormatted = formatSeconds(lengthSeconds),
                            viewsFormatted = formatViewCount(viewCount),
                            publishedText = publishedText
                        )
                    )
                }

                if (list.isNotEmpty()) {
                    return@withContext list
                }
            } catch (e: Exception) {
                // Try next instance
            }
        }
        return@withContext emptyList()
    }

    suspend fun getVideoDetails(videoId: String): VideoStreamDetails? = withContext(Dispatchers.IO) {
        for (baseUrl in INVIDIOUS_INSTANCES) {
            try {
                val url = "$baseUrl/api/v1/videos/$videoId"
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) continue

                val jsonBody = response.body?.string() ?: continue
                val json = JSONObject(jsonBody)

                val title = json.optString("title", "")
                val author = json.optString("author", "")
                val description = json.optString("description", "")
                val viewCount = json.optLong("viewCount", 0L)
                val likeCount = json.optLong("likeCount", 0L)
                val subCountText = json.optString("subCountText", "")
                val hlsUrl = json.optString("hlsUrl", "").ifEmpty { null }

                // Extract direct video format stream
                val formatStreams = json.optJSONArray("formatStreams")
                var directStreamUrl: String? = null
                var streamQuality = "720p"

                if (formatStreams != null && formatStreams.length() > 0) {
                    // Pick the highest resolution mp4 stream
                    for (i in formatStreams.length() - 1 downTo 0) {
                        val format = formatStreams.optJSONObject(i) ?: continue
                        val streamU = format.optString("url", "")
                        if (streamU.isNotEmpty()) {
                            directStreamUrl = streamU
                            streamQuality = format.optString("qualityLabel", "720p")
                            break
                        }
                    }
                }

                // If no format stream, fallback to HLS or first available
                val finalStreamUrl = directStreamUrl ?: hlsUrl
                if (finalStreamUrl != null) {
                    // Parse related videos
                    val relatedArray = json.optJSONArray("recommendedVideos")
                    val relatedList = mutableListOf<YouTubeVideo>()
                    if (relatedArray != null) {
                        for (i in 0 until minOf(relatedArray.length(), 10)) {
                            val rel = relatedArray.optJSONObject(i) ?: continue
                            val relId = rel.optString("videoId", "")
                            if (relId.isNotEmpty()) {
                                relatedList.add(
                                    YouTubeVideo(
                                        id = relId,
                                        title = rel.optString("title", ""),
                                        author = rel.optString("author", ""),
                                        thumbnailUrl = "https://i.ytimg.com/vi/$relId/hqdefault.jpg",
                                        durationFormatted = formatSeconds(rel.optLong("lengthSeconds", 0L)),
                                        viewsFormatted = formatViewCount(rel.optLong("viewCount", 0L))
                                    )
                                )
                            }
                        }
                    }

                    return@withContext VideoStreamDetails(
                        id = videoId,
                        title = title,
                        author = author,
                        subscriberCountText = subCountText,
                        description = description,
                        viewsFormatted = formatViewCount(viewCount),
                        likesCount = likeCount,
                        streamUrl = finalStreamUrl,
                        hlsUrl = hlsUrl,
                        quality = streamQuality,
                        relatedVideos = relatedList
                    )
                }
            } catch (e: Exception) {
                // Try next instance
            }
        }
        return@withContext null
    }

    private fun formatSeconds(seconds: Long): String {
        if (seconds <= 0) return ""
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) {
            String.format("%d:%02d:%02d", h, m, s)
        } else {
            String.format("%d:%02d", m, s)
        }
    }

    private fun formatViewCount(views: Long): String {
        return when {
            views >= 1_000_000 -> String.format("%.1fM x ditonton", views / 1_000_000.0)
            views >= 1_000 -> String.format("%.1fK x ditonton", views / 1_000.0)
            views > 0 -> "$views x ditonton"
            else -> ""
        }
    }
}
