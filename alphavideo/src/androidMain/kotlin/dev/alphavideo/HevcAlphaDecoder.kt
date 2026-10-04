package dev.alphavideo

import android.graphics.Bitmap
import androidx.annotation.Keep

/** Single-owner decoder. All calls run sequentially on the decoding coroutine. */
@Keep
internal class HevcAlphaDecoder : AutoCloseable {
    private var handle = 0L
    lateinit var metadata: AlphaVideoStatus.Ready
        private set

    fun load(path: String) {
        check(handle == 0L)
        handle = open(path)
        val values = info(handle)
        metadata = AlphaVideoStatus.Ready(values[0].toInt(), values[1].toInt(), values[2])
    }

    fun next(): Pair<Bitmap, Long>? {
        check(handle != 0L)
        val bitmap = Bitmap.createBitmap(metadata.width, metadata.height, Bitmap.Config.ARGB_8888)
        val timestampMicros = read(handle, bitmap)
        if (timestampMicros < 0) {
            bitmap.recycle()
            return null
        }
        return bitmap to timestampMicros
    }

    fun rewind() { check(handle != 0L); restart(handle) }

    override fun close() {
        if (handle != 0L) { close(handle); handle = 0L }
    }

    private external fun open(path: String): Long
    private external fun info(handle: Long): LongArray
    private external fun read(handle: Long, bitmap: Bitmap): Long
    private external fun restart(handle: Long)
    private external fun close(handle: Long)

    companion object {
        init { System.loadLibrary("alphavideo") }
    }
}
