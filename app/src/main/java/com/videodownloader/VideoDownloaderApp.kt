package com.videodownloader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class VideoDownloaderApp : Application() {

    companion object {
        const val DOWNLOAD_CHANNEL_ID = "download_channel"
        const val PLAYER_CHANNEL_ID = "player_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val downloadChannel = NotificationChannel(
            DOWNLOAD_CHANNEL_ID,
            "视频下载",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "显示视频下载进度"
        }

        val playerChannel = NotificationChannel(
            PLAYER_CHANNEL_ID,
            "视频播放",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "视频播放控制"
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(downloadChannel)
        manager.createNotificationChannel(playerChannel)
    }
}
