package com.videodownloader.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.ui.PlayerView
import com.videodownloader.data.LocalVideo
import com.videodownloader.util.PlaybackHistoryManager
import com.videodownloader.util.VideoScanner
import kotlinx.coroutines.delay

/**
 * Full-screen video player activity with PiP and media controls
 */
class PlayerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_VIDEO_URI = "video_uri"
        const val EXTRA_VIDEO_TITLE = "video_title"
        const val EXTRA_VIDEO_LIST = "video_list"
        const val EXTRA_VIDEO_POSITION = "video_position"

        fun start(context: Context, uri: String, title: String) {
            val intent = Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URI, uri)
                putExtra(EXTRA_VIDEO_TITLE, title)
            }
            context.startActivity(intent)
        }

        fun startPlaylist(context: Context, videos: List<LocalVideo>, position: Int) {
            val intent = Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_POSITION, position)
                putStringArrayListExtra(
                    EXTRA_VIDEO_LIST,
                    ArrayList(videos.map { it.uri })
                )
                putExtra(EXTRA_VIDEO_TITLE, videos[position].title)
            }
            context.startActivity(intent)
        }
    }

    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val videoUri = intent.getStringExtra(EXTRA_VIDEO_URI) ?: ""
        val videoTitle = intent.getStringExtra(EXTRA_VIDEO_TITLE) ?: ""
        val videoList = intent.getStringArrayListExtra(EXTRA_VIDEO_LIST)
        val position = intent.getIntExtra(EXTRA_VIDEO_POSITION, 0)

        exoPlayer = ExoPlayer.Builder(this).build().apply {
            if (videoList != null && videoList.isNotEmpty()) {
                videoList.forEach { uri ->
                    addMediaItem(MediaItem.fromUri(uri))
                }
                seekToDefaultPosition(position)
            } else {
                setMediaItem(MediaItem.fromUri(Uri.parse(videoUri)))
            }
            prepare()
            playWhenReady = true
        }

        // Save playback history
        PlaybackHistoryManager.addHistory(
            this@PlayerActivity,
            videoTitle.ifBlank { "视频" },
            videoUri.ifBlank { videoList?.getOrNull(position) ?: "" }
        )

        mediaSession = MediaSession.Builder(this, exoPlayer!!).build()

        setContent {
            VideoPlayerScreen(
                player = exoPlayer!!,
                title = videoTitle,
                onBack = { finish() },
                onPip = { enterPip() },
                onClose = { finish() }
            )
        }
    }

    private fun enterPip() {
        if (packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        // PlayerView will auto-hide controls in PiP
    }

    override fun onStop() {
        super.onStop()
        if (!isInPictureInPictureMode) {
            exoPlayer?.pause()
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        exoPlayer = null
        super.onDestroy()
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    player: ExoPlayer,
    title: String,
    onBack: () -> Unit,
    onPip: () -> Unit,
    onClose: () -> Unit
) {
    var showControls by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var playbackState by remember { mutableStateOf(Player.STATE_IDLE) }

    // Track player state
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // Update position
    LaunchedEffect(player) {
        while (true) {
            currentPosition = player.currentPosition
            duration = player.duration.coerceAtLeast(0)
            delay(200)
        }
    }

    // Auto-hide controls
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4000)
            showControls = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {
                showControls = !showControls
            }
    ) {
        // Player View
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = false // We use custom controls
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Custom Controls Overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Top gradient + bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                            )
                        )
                        .padding(top = 40.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.Default.ArrowBack,
                                contentDescription = "返回",
                                tint = Color.White
                            )
                        }
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 16.sp,
                            maxLines = 1,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp)
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            IconButton(onClick = onPip) {
                                Icon(
                                    Icons.Default.PictureInPicture,
                                    contentDescription = "画中画",
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }

                // Center controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rewind 10s
                    IconButton(
                        onClick = {
                            player.seekTo((player.currentPosition - 10000).coerceAtLeast(0))
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Replay10,
                                contentDescription = "快退10秒",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    // Play/Pause
                    IconButton(
                        onClick = {
                            if (isPlaying) player.pause() else player.play()
                        },
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color.White.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    // Forward 10s
                    IconButton(
                        onClick = {
                            player.seekTo(
                                (player.currentPosition + 10000).coerceAtMost(duration)
                            )
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Forward10,
                            contentDescription = "快进10秒",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                // Bottom controls
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                            )
                        )
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    // Seek bar
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = VideoScanner.formatDuration(currentPosition),
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        Slider(
                            value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                            onValueChange = { fraction ->
                                player.seekTo((fraction * duration).toLong())
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 8.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = Color(0xFFBB86FC),
                                inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                            )
                        )
                        Text(
                            text = VideoScanner.formatDuration(duration),
                            color = Color.White,
                            fontSize = 12.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Bottom action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Previous
                        IconButton(onClick = {
                            if (player.hasPreviousMediaItem()) player.seekToPreviousMediaItem()
                        }) {
                            Icon(
                                Icons.Default.SkipPrevious,
                                contentDescription = "上一个",
                                tint = if (player.hasPreviousMediaItem()) Color.White
                                else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Speed control
                        var speedMenuExpanded by remember { mutableStateOf(false) }
                        var currentSpeed by remember { mutableFloatStateOf(1f) }
                        Box {
                            TextButton(onClick = { speedMenuExpanded = true }) {
                                Text(
                                    "${currentSpeed}x",
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                            DropdownMenu(
                                expanded = speedMenuExpanded,
                                onDismissRequest = { speedMenuExpanded = false }
                            ) {
                                listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                                    DropdownMenuItem(
                                        text = { Text("${speed}x") },
                                        onClick = {
                                            currentSpeed = speed
                                            player.setPlaybackSpeed(speed)
                                            speedMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // PiP
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            IconButton(onClick = onPip) {
                                Icon(
                                    Icons.Default.PictureInPicture,
                                    contentDescription = "画中画",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        // Next
                        IconButton(onClick = {
                            if (player.hasNextMediaItem()) player.seekToNextMediaItem()
                        }) {
                            Icon(
                                Icons.Default.SkipNext,
                                contentDescription = "下一个",
                                tint = if (player.hasNextMediaItem()) Color.White
                                else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
