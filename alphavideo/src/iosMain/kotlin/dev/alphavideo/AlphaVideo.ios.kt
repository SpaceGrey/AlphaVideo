@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.alphavideo

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import platform.AVFoundation.*
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.NSLog

@Composable
internal actual fun PlatformAlphaVideo(
    source: AlphaVideoSource,
    modifier: Modifier,
    playing: Boolean,
    loop: Boolean,
    contentScale: ContentScale,
    onStatus: (AlphaVideoStatus) -> Unit,
) {
    val session = remember { AppleAlphaPlayer(source.path) }
    var image by remember { mutableStateOf<ImageBitmap?>(null) }
    val foreground = rememberForeground()
    val shouldPlay by rememberUpdatedState(playing && foreground)
    val shouldLoop by rememberUpdatedState(loop)
    val report by rememberUpdatedState(onStatus)

    DisposableEffect(session) {
        onDispose { session.close(); NSLog("HEVC_ALPHA_CLOSED") }
    }
    LaunchedEffect(session) {
        report(AlphaVideoStatus.Loading)
        try {
            session.requireAlpha()
            var ready: AlphaVideoStatus.Ready? = null
            while (ready == null) { ready = session.ready(); if (ready == null) delay(20) }
            report(ready)
            NSLog("HEVC_ALPHA_READY ${ready.width}x${ready.height} duration=${ready.durationMillis}ms")
            var cycle = 0
            var loggedAlpha = false
            while (currentCoroutineContext().isActive) {
                session.ready()
                session.setPlaying(shouldPlay)
                copyAlphaFrame(session, !loggedAlpha)?.let {
                    image = it
                    loggedAlpha = true
                }
                val time = CMTimeGetSeconds(session.player.currentTime())
                if (time.isFinite() && time * 1000 >= ready.durationMillis - 1) {
                    session.player.pause()
                    if (!shouldLoop) { report(AlphaVideoStatus.Ended); break }
                    session.rewind()
                    cycle++
                    NSLog("HEVC_ALPHA_LOOP cycle=$cycle")
                }
                delay(10)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            session.player.pause()
            NSLog("HEVC_ALPHA_FAILED ${error.message}")
            report(AlphaVideoStatus.Failed(error.message ?: "HEVC Alpha playback failed"))
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
