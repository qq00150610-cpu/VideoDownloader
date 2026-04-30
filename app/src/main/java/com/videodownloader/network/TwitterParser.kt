package com.videodownloader.network

import com.videodownloader.data.ParseResult
import com.videodownloader.data.VideoQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Network client for parsing Twitter/X video URLs via the x-twitter-downloader API
 */
class TwitterParser {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    companion object {
        private const val BASE_URL = "https://x-twitter-downloader.com"
        private const val API_URL = "$BASE_URL/api"

        /**
         * Validate if a URL looks like a Twitter/X post URL
         */
        fun isValidTwitterUrl(url: String): Boolean {
            val patterns = listOf(
                Regex("https?://(www\\.)?twitter\\.com/\\w+/status/\\d+"),
                Regex("https?://(www\\.)?x\\.com/\\w+/status/\\d+"),
                Regex("https?://t\\.co/\\w+"),
                Regex("https?://twitter\\.com/\\w+/status/\\d+"),
                Regex("https?://x\\.com/\\w+/status/\\d+")
            )
            return patterns.any { it.containsMatchIn(url.trim()) }
        }
    }

    /**
     * Parse a Twitter/X URL and extract video download options
     */
    /**
     * Check if a URL is a direct video link
     */
    fun isDirectVideoUrl(url: String): Boolean {
        val trimmed = url.trim().lowercase()
        return trimmed.endsWith(".mp4") || trimmed.endsWith(".m3u8") ||
               trimmed.endsWith(".webm") || trimmed.endsWith(".mkv") ||
               trimmed.contains(".mp4?") || trimmed.contains(".m3u8?")
    }

    suspend fun parseUrl(url: String): ParseResult = withContext(Dispatchers.IO) {
        try {
            val cleanUrl = url.trim()

            if (cleanUrl.isBlank()) {
                return@withContext ParseResult(error = "请输入链接")
            }

            // Direct video URL - no parsing needed
            if (isDirectVideoUrl(cleanUrl)) {
                return@withContext ParseResult(
                    title = "在线视频",
                    qualities = listOf(
                        VideoQuality(
                            url = cleanUrl,
                            quality = "原画",
                            format = if (cleanUrl.contains(".m3u8")) "m3u8" else "mp4"
                        )
                    )
                )
            }

            // Twitter/X URL
            if (isValidTwitterUrl(cleanUrl)) {
                // Try the API endpoint first
                val apiResult = tryApiParse(cleanUrl)
                if (apiResult != null) return@withContext apiResult

                // Fallback: scrape the page
                val scrapeResult = tryScrapeParse(cleanUrl)
                if (scrapeResult != null) return@withContext scrapeResult

                return@withContext ParseResult(error = "无法解析该链接，请确认视频公开可用")
            }

            ParseResult(error = "仅支持 Twitter/X 视频链接或直链视频地址")
        } catch (e: Exception) {
            ParseResult(error = "解析失败: ${e.message}")
        }
    }

    private fun tryApiParse(url: String): ParseResult? {
        return try {
            val jsonBody = JSONObject().apply {
                put("url", url)
            }
            val requestBody = jsonBody.toString()
                .toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url("$API_URL/download")
                .post(requestBody)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .header("Referer", BASE_URL)
                .header("Origin", BASE_URL)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                parseApiResponse(body)
            } else {
                null
            }
        } catch (e: Throwable) {
            null
        }
    }

    private fun parseApiResponse(body: String): ParseResult? {
        return try {
            val json = JSONObject(body)
            val qualities = mutableListOf<VideoQuality>()

            // Try different response formats
            when {
                json.has("data") -> {
                    val data = json.getJSONObject("data")
                    val title = data.optString("title", "Twitter Video")
                    val thumb = data.optString("thumbnail", "")

                    if (data.has("videos")) {
                        val videos = data.getJSONArray("videos")
                        for (i in 0 until videos.length()) {
                            val v = videos.getJSONObject(i)
                            qualities.add(
                                VideoQuality(
                                    url = v.optString("url", v.optString("src", "")),
                                    quality = v.optString("quality", "SD"),
                                    format = v.optString("format", "mp4"),
                                    size = v.optString("size", "")
                                )
                            )
                        }
                    } else if (data.has("download")) {
                        val download = data.getJSONObject("download")
                        qualities.add(
                            VideoQuality(
                                url = download.optString("url", ""),
                                quality = download.optString("quality", "HD"),
                                format = "mp4"
                            )
                        )
                    }

                    if (qualities.isNotEmpty()) {
                        return ParseResult(title = title, thumbnail = thumb, qualities = qualities)
                    }
                }

                json.has("downloads") || json.has("links") -> {
                    val arr = json.optJSONArray("downloads")
                        ?: json.optJSONArray("links")
                        ?: return null
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        qualities.add(
                            VideoQuality(
                                url = item.optString("url", ""),
                                quality = item.optString("quality", item.optString("label", "SD")),
                                format = item.optString("format", "mp4"),
                                size = item.optString("size", "")
                            )
                        )
                    }
                }
            }

            if (qualities.isEmpty()) return null
            ParseResult(qualities = qualities)
        } catch (e: Throwable) {
            null
        }
    }

    private fun tryScrapeParse(url: String): ParseResult? {
        return try {
            val request = Request.Builder()
                .url("$BASE_URL/zh-CN")
                .get()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .build()

            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: return null

            // Look for API endpoints in the page JS
            val apiPattern = Regex("fetch\\(['\"]([^'\"]*api[^'\"]*)['\"]")
            val match = apiPattern.find(html)

            if (match != null) {
                val apiUrl = match.groupValues[1]
                // Try the discovered API
                tryDirectApi(apiUrl, url)
            } else {
                null
            }
        } catch (e: Throwable) {
            null
        }
    }

    private fun tryDirectApi(apiEndpoint: String, url: String): ParseResult? {
        return try {
            val fullUrl = if (apiEndpoint.startsWith("http")) apiEndpoint
            else "$BASE_URL$apiEndpoint"

            val formBody = FormBody.Builder()
                .add("url", url)
                .build()

            val request = Request.Builder()
                .url(fullUrl)
                .post(formBody)
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                .header("Referer", BASE_URL)
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return null
                parseApiResponse(body)
            } else {
                null
            }
        } catch (e: Throwable) {
            null
        }
    }
}
