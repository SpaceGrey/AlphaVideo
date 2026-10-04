package dev.alphavideo.demo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Initialized by MainActivity before composition; no Activity reference is retained.
object DemoCache { lateinit var directory: File }

internal actual suspend fun cacheDemoMovie(bytes: ByteArray): String = withContext(Dispatchers.IO) {
    File(DemoCache.directory, "jimeng-hevc-alpha.mov").apply { writeBytes(bytes) }.absolutePath
}
