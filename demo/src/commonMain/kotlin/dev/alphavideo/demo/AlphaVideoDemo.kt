package dev.alphavideo.demo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.alphavideo.AlphaVideo
import dev.alphavideo.AlphaVideoSource
import dev.alphavideo.AlphaVideoStatus
import dev.alphavideo.demo.resources.Res

@Composable
fun AlphaVideoDemo() {
    var source by remember { mutableStateOf<AlphaVideoSource?>(null) }
    var status by remember { mutableStateOf<AlphaVideoStatus>(AlphaVideoStatus.Loading) }
    var playing by remember { mutableStateOf(true) }
    var loop by remember { mutableStateOf(true) }
    var background by remember { mutableStateOf(0) }
    var attached by remember { mutableStateOf(true) }
    var generation by remember { mutableStateOf(0) }
    var movieBytes by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        try {
            val bytes = Res.readBytes("files/jimeng-hevc-alpha.mov")
            movieBytes = bytes.size
            source = AlphaVideoSource(cacheDemoMovie(bytes))
        } catch (error: Exception) {
            status = AlphaVideoStatus.Failed(error.message ?: "Could not load sample")
        }
    }
    MaterialTheme {
        Column(
            Modifier.fillMaxSize().background(Color(0xFFF4F5F8)).safeDrawingPadding()
                .verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("HEVC Alpha", style = MaterialTheme.typography.headlineLarge)
            Text("Jimeng · HEVC Alpha · Android + iOS\n1160 × 798 · 144 frames · 6.02 s · ${movieBytes / 1000} KB")
            val ready = status as? AlphaVideoStatus.Ready
            val aspect = ready?.let { it.width.toFloat() / it.height } ?: (1160f / 798f)
            Box(Modifier.fillMaxWidth().aspectRatio(aspect).clipToBounds()) {
                Canvas(Modifier.fillMaxSize()) {
                    if (background == 0) {
                        val cell = size.width / 8
                        for (row in 0..7) for (column in 0..7) drawRect(
                            color = if ((row + column) % 2 == 0) Color.White else Color(0xFFB8BFCC),
                            topLeft = Offset(column * cell, row * cell),
                            size = Size(cell + 1, cell + 1),
                        )
                    } else drawRect(if (background == 1) Color(0xFF202735) else Color(0xFF356CFF))
                }
                if (attached) source?.let { movie ->
                    androidx.compose.runtime.key(generation) {
                        AlphaVideo(movie, Modifier.fillMaxSize(), playing, loop, onStatus = { status = it })
                    }
                }
            }
            Text(when (val current = status) {
                AlphaVideoStatus.Loading -> "Loading…"
                is AlphaVideoStatus.Ready -> "Ready · ${current.width} × ${current.height} · ${current.durationMillis} ms"
                AlphaVideoStatus.Ended -> "Ended"
                is AlphaVideoStatus.Failed -> "Error: ${current.message}"
            })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { playing = !playing }) { Text(if (playing) "Pause" else "Play") }
                Button(onClick = { background = (background + 1) % 3 }) { Text("Background") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { loop = !loop }) { Text(if (loop) "Loop: on" else "Loop: off") }
                Button(onClick = { generation++; attached = true }) { Text("Restart") }
            }
            Button(onClick = { attached = !attached }) { Text(if (attached) "Detach" else "Attach") }
            Text("Original ProRes alpha preserved\nChecker / dark / blue backgrounds reveal transparency",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

internal expect suspend fun cacheDemoMovie(bytes: ByteArray): String
