package com.videodownloader.util

import android.content.Context
import com.videodownloader.data.PlaybackHistory
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manages playback history using SharedPreferences + JSON storage
 */
object PlaybackHistoryManager {

    private const val PREFS_NAME = "playback_history"
    private const val KEY_HISTORY = "history_list"
    private const val MAX_HISTORY = 100

    private fun getPrefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun addHistory(context: Context, title: String, uri: String, duration: Long = 0) {
        val prefs = getPrefs(context)
        val history = getHistory(context).toMutableList()

        // Remove duplicate (same uri)
        history.removeAll { it.uri == uri }

        // Add new entry at the beginning
        history.add(0, PlaybackHistory(
            id = System.currentTimeMillis().toString(),
            title = title,
            uri = uri,
            timestamp = System.currentTimeMillis(),
            duration = duration
        ))

        // Trim to max size
        if (history.size > MAX_HISTORY) {
            history.subList(MAX_HISTORY, history.size).clear()
        }

        saveHistory(prefs, history)
    }

    fun getHistory(context: Context): List<PlaybackHistory> {
        val prefs = getPrefs(context)
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()

        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                try {
                    val obj = array.getJSONObject(i)
                    PlaybackHistory(
                        id = obj.optString("id", ""),
                        title = obj.optString("title", ""),
                        uri = obj.optString("uri", ""),
                        timestamp = obj.optLong("timestamp", 0),
                        duration = obj.optLong("duration", 0)
                    )
                } catch (e: Exception) { null }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun removeHistory(context: Context, id: String) {
        val prefs = getPrefs(context)
        val history = getHistory(context).toMutableList()
        history.removeAll { it.id == id }
        saveHistory(prefs, history)
    }

    fun clearHistory(context: Context) {
        val prefs = getPrefs(context)
        prefs.edit().remove(KEY_HISTORY).apply()
    }

    private fun saveHistory(prefs: android.content.SharedPreferences, history: List<PlaybackHistory>) {
        val array = JSONArray()
        history.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("uri", item.uri)
                put("timestamp", item.timestamp)
                put("duration", item.duration)
            }
            array.put(obj)
        }
        prefs.edit().putString(KEY_HISTORY, array.toString()).apply()
    }

    /**
     * Format timestamp to relative time string
     */
    fun formatRelativeTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        return when {
            diff < 60_000 -> "刚刚"
            diff < 3_600_000 -> "${diff / 60_000}分钟前"
            diff < 86_400_000 -> "${diff / 3_600_000}小时前"
            diff < 172_800_000 -> "昨天"
            diff < 604_800_000 -> "${diff / 86_400_000}天前"
            else -> {
                val sdf = java.text.SimpleDateFormat("yyyy/MM/dd", java.util.Locale.getDefault())
                sdf.format(java.util.Date(timestamp))
            }
        }
    }
}
