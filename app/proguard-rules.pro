# ProGuard rules for VideoDownloader

# Keep ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Keep OkHttp
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**

# Keep Compose
-keep class androidx.compose.** { *; }

# Keep data classes
-keep class com.videodownloader.data.** { *; }
