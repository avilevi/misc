package com.hcexport.ui.journal

import android.graphics.Color as AndroidColor
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.hcexport.WorkoutVideo
import com.hcexport.ui.theme.CardHigh
import com.hcexport.ui.theme.Snow

/**
 * The movement demos Fitr attaches to a workout section, shown at the bottom of
 * that section. A thumbnail card opens the video in an in-app player.
 */
@Composable
internal fun WorkoutVideoStrip(videos: List<WorkoutVideo>, accent: Color) {
    if (videos.isEmpty()) return
    var playing by remember { mutableStateOf<WorkoutVideo?>(null) }
    JournalSectionHeader(title = "Videos", accent = accent)
    Spacer(Modifier.height(10.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(videos.size) { index ->
            val video = videos[index]
            WorkoutVideoCard(video, accent) { playing = video }
        }
    }
    playing?.let { video ->
        WorkoutVideoDialog(video) { playing = null }
    }
}

@Composable
private fun WorkoutVideoCard(video: WorkoutVideo, accent: Color, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(Modifier.width(210.dp).clip(shape).clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(118.dp)
                .clip(shape)
                .background(
                    Brush.linearGradient(listOf(accent.copy(alpha = 0.35f), CardHigh))
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (!video.thumbUrl.isNullOrBlank()) {
                AsyncImage(
                    model = video.thumbUrl,
                    contentDescription = video.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Play ${video.title}",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            video.title,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = Snow,
        )
    }
}

@Composable
private fun WorkoutVideoDialog(video: WorkoutVideo, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        var webView by remember { mutableStateOf<WebView?>(null) }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        setBackgroundColor(AndroidColor.BLACK)
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        // A plain Chrome UA keeps the YouTube embed from being
                        // treated as an unsupported in-app browser.
                        settings.userAgentString = settings.userAgentString
                            .replace("; wv", "")
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        webView = this
                        val videoId = video.videoId
                        val url = video.url
                        when {
                            !videoId.isNullOrBlank() -> loadUrl(
                                "https://www.youtube.com/embed/$videoId" +
                                    "?autoplay=1&playsinline=1&rel=0&modestbranding=1"
                            )
                            !url.isNullOrBlank() -> {
                                val safe = url.replace("\"", "&quot;")
                                loadDataWithBaseURL(
                                    "https://app.fitr.training",
                                    """
                                    <!doctype html><html><head>
                                    <meta name="viewport" content="width=device-width,initial-scale=1">
                                    </head><body style="margin:0;background:#000;height:100vh;
                                    display:flex;align-items:center;justify-content:center">
                                    <video src="$safe" controls autoplay playsinline
                                     style="width:100%;height:100%"></video></body></html>
                                    """.trimIndent(),
                                    "text/html",
                                    "UTF-8",
                                    null,
                                )
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .align(Alignment.Center),
            )
            DisposableEffect(Unit) {
                onDispose { webView?.destroy() }
            }
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    video.title,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "CLOSE",
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }
        }
    }
}
