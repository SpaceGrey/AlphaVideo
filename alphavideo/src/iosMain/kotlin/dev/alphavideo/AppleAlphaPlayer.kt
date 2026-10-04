@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.alphavideo

import kotlinx.cinterop.useContents
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFoundation.*
import platform.CoreVideo.*
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMake
import platform.Foundation.NSURL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class AppleAlphaPlayer(path: String) {
    private val asset = AVURLAsset(NSURL.fileURLWithPath(path), options = null)
    val item = AVPlayerItem(asset)
    val player = AVPlayer.playerWithPlayerItem(item).apply { muted = true }
    val output = AVPlayerItemVideoOutput(pixelBufferAttributes = mapOf(
        // CFStringRef constants are C pointers in Kotlin; NSDictionary needs a string key.
        "PixelFormatType" to kCVPixelFormatType_32BGRA.toLong(),
    )).apply { suppressesPlayerRendering = true }

    init { item.addOutput(output) }

    suspend fun requireAlpha() = suspendCancellableCoroutine<Unit> { continuation ->
        asset.loadTracksWithMediaCharacteristic(AVMediaCharacteristicContainsAlphaChannel) { tracks, error ->
            if (continuation.isActive) {
                if (error != null) {
                    continuation.resumeWithException(IllegalStateException(error.localizedDescription))
                } else if (tracks.isNullOrEmpty()) {
                    continuation.resumeWithException(IllegalArgumentException("Movie has no alpha video track"))
                } else continuation.resume(Unit)
            }
        }
    }

    fun ready(): AlphaVideoStatus.Ready? {
        if (item.status == AVPlayerItemStatusFailed) {
            throw IllegalStateException(item.error?.localizedDescription ?: "HEVC Alpha playback failed")
        }
        if (item.status != AVPlayerItemStatusReadyToPlay) return null
        val duration = CMTimeGetSeconds(item.duration)
        if (!duration.isFinite() || duration <= 0) return null
        return item.presentationSize.useContents {
            if (width <= 0 || height <= 0) return@useContents null
            AlphaVideoStatus.Ready(width.toInt(), height.toInt(), (duration * 1000).toLong())
        }
    }

    fun setPlaying(playing: Boolean) {
        if (playing) player.play() else player.pause()
    }

    suspend fun rewind() = suspendCancellableCoroutine<Unit> { continuation ->
        player.seekToTime(CMTimeMake(0, 1), toleranceBefore = CMTimeMake(0, 1),
            toleranceAfter = CMTimeMake(0, 1)) { finished ->
            if (continuation.isActive) {
                if (finished) continuation.resume(Unit)
                else continuation.resumeWithException(IllegalStateException("Video loop seek was interrupted"))
            }
        }
    }

    fun close() {
        player.pause()
        item.removeOutput(output)
        player.replaceCurrentItemWithPlayerItem(null)
        asset.cancelLoading()
    }
}
