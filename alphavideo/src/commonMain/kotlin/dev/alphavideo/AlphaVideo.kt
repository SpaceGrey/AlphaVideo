package dev.alphavideo

import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale

/** Apple HEVC-with-Alpha movie at an absolute local filesystem path. Audio is not played. */
data class AlphaVideoSource(val path: String) {
    init { require(path.startsWith("/")) { "AlphaVideo requires an absolute local file path" } }
}

sealed interface AlphaVideoStatus {
    data object Loading : AlphaVideoStatus
    data class Ready(val width: Int, val height: Int, val durationMillis: Long) : AlphaVideoStatus
    data object Ended : AlphaVideoStatus
    data class Failed(val message: String) : AlphaVideoStatus
}

/**
 * Plays genuine HEVC auxiliary-alpha video over the Compose content behind it.
 * Pauses when the host leaves the foreground; disposal closes the decoder.
 * Only Fit and Crop are supported. No network fetching, sound, or seeking.
 */
@Composable
fun AlphaVideo(
    source: AlphaVideoSource,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
    loop: Boolean = true,
    contentScale: ContentScale = ContentScale.Fit,
    onStatus: (AlphaVideoStatus) -> Unit = {},
) {
    require(contentScale == ContentScale.Fit || contentScale == ContentScale.Crop)
    key(source) {
        PlatformAlphaVideo(source, modifier, playing, loop, contentScale, onStatus)
    }
}

@Composable
internal expect fun PlatformAlphaVideo(
    source: AlphaVideoSource,
    modifier: Modifier,
    playing: Boolean,
    loop: Boolean,
    contentScale: ContentScale,
    onStatus: (AlphaVideoStatus) -> Unit,
)
