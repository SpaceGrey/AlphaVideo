@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package dev.alphavideo.demo

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.*

internal actual suspend fun cacheDemoMovie(bytes: ByteArray): String {
    val path = NSTemporaryDirectory() + "jimeng-hevc-alpha.mov"
    val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
    check(data.writeToFile(path, atomically = true)) { "Could not cache alpha movie" }
    return path
}
