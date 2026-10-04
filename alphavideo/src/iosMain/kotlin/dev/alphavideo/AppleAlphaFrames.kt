@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.alphavideo

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import platform.AVFoundation.*
import platform.CoreFoundation.CFRelease
import platform.CoreVideo.*
import platform.Foundation.NSLog
import platform.posix.memcpy

internal fun copyAlphaFrame(session: AppleAlphaPlayer, logAlpha: Boolean): ImageBitmap? {
    val time = session.player.currentTime()
    if (!session.output.hasNewPixelBufferForItemTime(time)) return null
    // The Objective-C copy API remains available; its tuple replacement is Swift-only.
    val buffer = session.output.copyPixelBufferForItemTime(time, itemTimeForDisplay = null)
        ?: return null
    try {
        val pixelFormat = CVPixelBufferGetPixelFormatType(buffer)
        check(pixelFormat == kCVPixelFormatType_32BGRA) {
            "Expected BGRA video output, got pixel format $pixelFormat"
        }
        check(CVPixelBufferLockBaseAddress(buffer, kCVPixelBufferLock_ReadOnly) == kCVReturnSuccess) {
            "Could not lock decoded video pixels"
        }
        val width = CVPixelBufferGetWidth(buffer).toInt()
        val height = CVPixelBufferGetHeight(buffer).toInt()
        val rowBytes = CVPixelBufferGetBytesPerRow(buffer).toInt()
        val bytes = try {
            val baseAddress = checkNotNull(CVPixelBufferGetBaseAddress(buffer))
            ByteArray(rowBytes * height).also { pixels ->
                // readBytes performs a slow Kotlin copy in Native debug builds.
                // Pin only during memcpy; Skia receives its own immutable pixel storage below.
                pixels.usePinned { memcpy(it.addressOf(0), baseAddress, pixels.size.convert()) }
            }
        } finally {
            CVPixelBufferUnlockBaseAddress(buffer, kCVPixelBufferLock_ReadOnly)
        }
        if (logAlpha) {
            var minimum = 255
            var maximum = 0
            var semiTransparent = 0
            for (y in 0 until height) for (x in 0 until width) {
                val alpha = bytes[y * rowBytes + x * 4 + 3].toInt() and 255
                minimum = minOf(minimum, alpha)
                maximum = maxOf(maximum, alpha)
                if (alpha in 1..254) semiTransparent++
            }
            NSLog("HEVC_ALPHA_BGRA ${width}x$height alpha_min=$minimum alpha_max=$maximum semi_pixels=$semiTransparent")
        }
        val bitmap = Bitmap()
        try {
            check(bitmap.installPixels(
                ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.PREMUL),
                bytes, rowBytes,
            )) { "Could not install decoded pixels into Compose bitmap" }
            bitmap.setImmutable()
            return bitmap.asComposeImageBitmap()
        } catch (error: Throwable) {
            bitmap.close()
            throw error
        }
    } finally {
        CFRelease(buffer)
    }
}
