package dev.alphavideo

import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

@Composable
internal actual fun PlatformAlphaVideo(
    source: AlphaVideoSource,
    modifier: Modifier,
    playing: Boolean,
    loop: Boolean,
    contentScale: ContentScale,
    onStatus: (AlphaVideoStatus) -> Unit,
) {
    val foreground = rememberForeground()
    val shouldPlay by rememberUpdatedState(playing && foreground)
    val shouldLoop by rememberUpdatedState(loop)
    val report by rememberUpdatedState(onStatus)
    var image by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(source) {
        report(AlphaVideoStatus.Loading)
        var decoder: HevcAlphaDecoder? = null
        try {
            val player = HevcAlphaDecoder()
            decoder = player
            withContext(Dispatchers.IO) { player.load(source.path) }
            report(player.metadata)
            var originNanos = 0L
            var firstFrame = true
            var lastTimestamp = 0L
            var cycle = 0
            suspend fun awaitPresentation(timestampMicros: Long) {
                while (currentCoroutineContext().isActive) {
                    if (!shouldPlay && !firstFrame) {
                        val pausedAt = System.nanoTime()
                        while (!shouldPlay) delay(20)
                        originNanos += System.nanoTime() - pausedAt
                    }
                    val remainingNanos = originNanos + timestampMicros * 1000 - System.nanoTime()
                    if (remainingNanos <= 0) return
                    delay((remainingNanos / 1_000_000).coerceIn(1, 10))
                }
            }
            while (currentCoroutineContext().isActive) {
                if (!firstFrame && !shouldPlay) {
                    awaitPresentation(lastTimestamp)
                }
                val frame = withContext(Dispatchers.IO) { player.next() }
                if (frame == null) {
                    // Keep the last frame visible for its remaining duration before looping/ending.
                    awaitPresentation(player.metadata.durationMillis * 1000)
                    if (!shouldLoop) { report(AlphaVideoStatus.Ended); break }
                    withContext(Dispatchers.IO) { player.rewind() }
                    originNanos = 0
                    cycle++
                    Log.i("AlphaVideo", "HEVC_ALPHA_LOOP cycle=$cycle")
                    continue
                }
                val (bitmap, timestamp) = frame
                if (originNanos == 0L) originNanos = System.nanoTime() - timestamp * 1000
                awaitPresentation(timestamp)
                image = bitmap.asImageBitmap()
                lastTimestamp = timestamp
                firstFrame = false
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e("AlphaVideo", "Playback failed", error)
            report(AlphaVideoStatus.Failed(error.message ?: "HEVC Alpha playback failed"))
        } catch (error: UnsatisfiedLinkError) {
            report(AlphaVideoStatus.Failed("Native HEVC decoder is missing: ${error.message}"))
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { decoder?.close() }
            Log.i("AlphaVideo", "HEVC_ALPHA_CLOSED")
        }
    }
    val frame = image
    if (frame == null) Box(modifier) else Image(
        bitmap = frame,
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale,
    )
}
