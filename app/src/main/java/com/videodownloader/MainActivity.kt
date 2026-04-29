package com.videodownloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.videodownloader.data.*
import com.videodownloader.network.TwitterParser
import com.videodownloader.player.PlayerActivity
import com.videodownloader.service.DownloadService
import com.videodownloader.util.VideoScanner
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
import kotlinx.coroutines.flow.collectAsState
        super.onCreate(savedInstanceState)

        // Handle share intent
        val sharedUrl = if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)
        } else null

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFBB86FC),
                    secondary = Color(0xFF03DAC6),
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E),
                    surfaceVariant = Color(0xFF2D2D2D),
                    onPrimary = Color.White,
                    onBackground = Color.White,
                    onSurface = Color.White,
                    error = Color(0xFFCF6679)
                )
            ) {
                MainScreen(sharedUrl = sharedUrl)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(sharedUrl: String?) {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("下载", "播放器", "我的视频")

    Scaffold(
        containerColor = Color(0xFF121212),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.VideoFile,
                            contentDescription = null,
                            tint = Color(0xFFBB86FC),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "视频下载器",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF1E1E1E)
            ) {
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                when (index) {
                                    0 -> Icons.Default.FileDownload
                                    1 -> Icons.Default.PlayCircle
                                    2 -> Icons.Default.Folder
                                    else -> Icons.Default.Help
                                },
                                contentDescription = title
                            )
                        },
                        label = { Text(title) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFFBB86FC),
                            selectedTextColor = Color(0xFFBB86FC),
                            indicatorColor = Color(0xFFBB86FC).copy(alpha = 0.12f),
                            unselectedIconColor = Color.Gray,
                            unselectedTextColor = Color.Gray
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> DownloadTab(sharedUrl = sharedUrl)
                1 -> PlayerTab()
                2 -> VideosTab()
            }
        }
    }
}

// ============ DOWNLOAD TAB ============

@Composable
fun DownloadTab(sharedUrl: String?) {
    var url by remember { mutableStateOf(sharedUrl ?: "") }
    var parseResult by remember { mutableStateOf<ParseResult?>(null) }
    var isParsing by remember { mutableStateOf(false) }
    var selectedQuality by remember { mutableStateOf<VideoQuality?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val parser = remember { TwitterParser() }
    val tasks by DownloadService.tasks.collectAsState()

    // Permission launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(context, "需要通知权限来显示下载进度", Toast.LENGTH_SHORT).show()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // URL Input
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "粘贴 Twitter/X 视频链接",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it; parseResult = null },
                        placeholder = {
                            Text(
                                "https://x.com/user/status/...",
                                color = Color.Gray
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFBB86FC),
                            unfocusedBorderColor = Color.Gray,
                            cursorColor = Color(0xFFBB86FC)
                        ),
                        singleLine = true,
                        trailingIcon = {
                            if (url.isNotEmpty()) {
                                IconButton(onClick = { url = ""; parseResult = null }) {
                                    Icon(
                                        Icons.Default.Clear,
                                        contentDescription = "清除",
                                        tint = Color.Gray
                                    )
                                }
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (url.isBlank()) return@Button
                            isParsing = true
                            parseResult = null
                            scope.launch {
                                parseResult = parser.parseUrl(url)
                                isParsing = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFBB86FC)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        enabled = url.isNotBlank() && !isParsing
                    ) {
                        if (isParsing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("解析中...")
                        } else {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("解析视频", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Parse Result
        parseResult?.let { result ->
            if (result.error != null) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1B1B)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = Color(0xFFCF6679)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(result.error, color = Color(0xFFCF6679))
                        }
                    }
                }
            }

            if (result.qualities.isNotEmpty()) {
                // Thumbnail
                if (result.thumbnail.isNotEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
                        ) {
                            AsyncImage(
                                model = result.thumbnail,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                // Title
                if (result.title.isNotEmpty()) {
                    item {
                        Text(
                            result.title,
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Quality selection
                item {
                    Text(
                        "选择画质",
                        color = Color(0xFFBB86FC),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(result.qualities) { quality ->
                    QualityCard(
                        quality = quality,
                        isSelected = selectedQuality == quality,
                        onClick = { selectedQuality = quality }
                    )
                }

                // Download button
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val q = selectedQuality ?: result.qualities.first()
                            // Check notification permission on Android 13+
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notificationPermissionLauncher.launch(
                                        Manifest.permission.POST_NOTIFICATIONS
                                    )
                                }
                            }

                            // Start download service
                            val serviceIntent = Intent(context, DownloadService::class.java).apply {
                                action = DownloadService.ACTION_DOWNLOAD
                                putExtra(DownloadService.EXTRA_URL, q.url)
                                putExtra(
                                    DownloadService.EXTRA_TITLE,
                                    result.title.ifEmpty { "Twitter_Video" }
                                )
                                putExtra(DownloadService.EXTRA_QUALITY, q.quality)
                            }
                            context.startForegroundService(serviceIntent)

                            Toast.makeText(context, "开始下载 ${q.quality}", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF03DAC6)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            tint = Color.Black
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "下载 ${selectedQuality?.quality ?: result.qualities.first().quality}",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }

        // Download history
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "下载记录",
                color = Color(0xFFBB86FC),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (tasks.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("暂无下载记录", color = Color.Gray)
                        }
                    }
                }
            }
        } else {
            items(tasks.reversed()) { task ->
                DownloadTaskCard(task = task)
            }
        }
    }
}

@Composable
fun QualityCard(quality: VideoQuality, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) Color(0xFFBB86FC).copy(alpha = 0.15f)
            else Color(0xFF1E1E1E)
        ),
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) CardDefaults.outlinedCardBorder().copy(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFBB86FC), Color(0xFF03DAC6))
            )
        ) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.HighQuality,
                    contentDescription = null,
                    tint = if (isSelected) Color(0xFFBB86FC) else Color.Gray
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        quality.quality,
                        color = if (isSelected) Color(0xFFBB86FC) else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    if (quality.size.isNotEmpty()) {
                        Text(quality.size, color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "已选择",
                    tint = Color(0xFFBB86FC)
                )
            }
        }
    }
}

@Composable
fun DownloadTaskCard(task: DownloadTask) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    when (task.status) {
                        DownloadStatus.DOWNLOADING -> Icons.Default.Downloading
                        DownloadStatus.COMPLETED -> Icons.Default.CheckCircle
                        DownloadStatus.FAILED -> Icons.Default.Error
                        DownloadStatus.CANCELLED -> Icons.Default.Cancel
                        DownloadStatus.PENDING -> Icons.Default.Schedule
                    },
                    contentDescription = null,
                    tint = when (task.status) {
                        DownloadStatus.COMPLETED -> Color(0xFF03DAC6)
                        DownloadStatus.FAILED -> Color(0xFFCF6679)
                        DownloadStatus.DOWNLOADING -> Color(0xFFBB86FC)
                        else -> Color.Gray
                    }
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        task.title,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${task.quality} · ${
                            when (task.status) {
                                DownloadStatus.DOWNLOADING -> "下载中 ${task.progress}%"
                                DownloadStatus.COMPLETED -> "已完成"
                                DownloadStatus.FAILED -> "失败: ${task.error ?: ""}"
                                DownloadStatus.CANCELLED -> "已取消"
                                DownloadStatus.PENDING -> "等待中"
                            }
                        }",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }

                if (task.status == DownloadStatus.COMPLETED && task.filePath.isNotEmpty()) {
                    IconButton(onClick = {
                        PlayerActivity.start(context, task.filePath, task.title)
                    }) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = "播放",
                            tint = Color(0xFFBB86FC)
                        )
                    }
                }
            }

            if (task.status == DownloadStatus.DOWNLOADING) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = task.progress / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color(0xFFBB86FC),
                    trackColor = Color(0xFF2D2D2D)
                )
            }
        }
    }
}

// ============ PLAYER TAB ============

@Composable
fun PlayerTab() {
    var onlineUrl by remember { mutableStateOf("") }
    val context = LocalContext.current
    val videos = remember { VideoScanner.scanDownloadedVideos(context) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Online video playback
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Language,
                            contentDescription = null,
                            tint = Color(0xFFBB86FC)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "在线播放",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = onlineUrl,
                        onValueChange = { onlineUrl = it },
                        placeholder = {
                            Text("输入视频URL (mp4/m3u8/...)", color = Color.Gray)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color(0xFFBB86FC),
                            unfocusedBorderColor = Color.Gray,
                            cursorColor = Color(0xFFBB86FC)
                        ),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (onlineUrl.isNotBlank()) {
                                PlayerActivity.start(context, onlineUrl.trim(), "在线视频")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFBB86FC)),
                        shape = RoundedCornerShape(12.dp),
                        enabled = onlineUrl.isNotBlank()
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("在线播放", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Quick access: downloaded videos
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.VideoLibrary,
                    contentDescription = null,
                    tint = Color(0xFFBB86FC)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "已下载的视频",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (videos.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.VideocamOff,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("暂无下载的视频", color = Color.Gray)
                            Text(
                                "去「下载」标签页下载视频",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        } else {
            itemsIndexed(videos) { index, video ->
                VideoCard(
                    video = video,
                    onClick = {
                        PlayerActivity.startPlaylist(context, videos, index)
                    }
                )
            }
        }
    }
}

// ============ VIDEOS TAB ============

@Composable
fun VideosTab() {
    val context = LocalContext.current
    var showAll by remember { mutableStateOf(false) }
    val downloadedVideos = remember { VideoScanner.scanDownloadedVideos(context) }
    val allVideos = remember { VideoScanner.scanAllVideos(context) }

    val displayVideos = if (showAll) allVideos else downloadedVideos

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Toggle
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !showAll,
                    onClick = { showAll = false },
                    label = { Text("已下载 (${downloadedVideos.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFBB86FC).copy(alpha = 0.2f),
                        selectedLabelColor = Color(0xFFBB86FC)
                    )
                )
                FilterChip(
                    selected = showAll,
                    onClick = { showAll = true },
                    label = { Text("全部视频 (${allVideos.size})") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFBB86FC).copy(alpha = 0.2f),
                        selectedLabelColor = Color(0xFFBB86FC)
                    )
                )
            }
        }

        if (displayVideos.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = Color.Gray,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("暂无视频", color = Color.Gray, fontSize = 18.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "下载的视频会出现在这里",
                                color = Color.Gray.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        } else {
            itemsIndexed(displayVideos) { index, video ->
                VideoCard(
                    video = video,
                    onClick = {
                        PlayerActivity.startPlaylist(context, displayVideos, index)
                    }
                )
            }
        }
    }
}

@Composable
fun VideoCard(video: LocalVideo, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail placeholder
            Box(
                modifier = Modifier
                    .size(80.dp, 56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2D2D2D)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = Color(0xFFBB86FC).copy(alpha = 0.7f),
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    video.title,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    if (video.duration > 0) {
                        Text(
                            VideoScanner.formatDuration(video.duration),
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                        Text(" · ", color = Color.Gray, fontSize = 12.sp)
                    }
                    Text(
                        VideoScanner.formatFileSize(video.size),
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }

            Icon(
                Icons.Default.PlayArrow,
                contentDescription = "播放",
                tint = Color(0xFFBB86FC)
            )
        }
    }
}
