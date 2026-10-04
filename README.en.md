# AlphaVideo

[简体中文](README.md) | **English**

A cross-platform video playback component for Compose Multiplatform, with a shared API for Android and iOS and support for transparent video over your UI.

## Features

- A shared Compose API for Android and iOS.
- HEVC Alpha transparent video support.
- Play, pause, and looping.
- Fit or crop video to its container.
- Playback status callbacks.
- Automatic handling of app foreground/background transitions and component lifecycle.

## Supported platforms

| Platform | Minimum version |
| --- | --- |
| Android | Android 10 / API 29 |
| iOS | iOS 18 |

Currently supports local HEVC Alpha `.mov` files, using an absolute file path as the playback source.

## Integration

Add the `alphavideo` module to your project and retain the repository's `scripts` and `native` directories.

```kotlin
// settings.gradle.kts
include(":alphavideo")
```

Add the dependency to your KMP module:

```kotlin
commonMain.dependencies {
    implementation(project(":alphavideo"))
}
```

## Usage

```kotlin
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import dev.alphavideo.AlphaVideo
import dev.alphavideo.AlphaVideoSource

// Call from a Composable
AlphaVideo(
    source = AlphaVideoSource("/absolute/path/video.mov"),
    modifier = Modifier.fillMaxSize(),
    playing = true,
    loop = true,
    contentScale = ContentScale.Fit,
    onStatus = { status ->
        // Handle playback status
    },
)
```

Use `playing` to play or pause and `loop` to control looping. `contentScale` supports `Fit` and `Crop`. `onStatus` reports `Loading`, `Ready`, `Ended`, and `Failed`.

## Demo

The repository includes Android and iOS sample apps.

```sh
# Android
./scripts/run-android.sh

# iOS
./scripts/run-ios.sh --simulator
```

## Third-party licenses

[FFmpeg LGPL 2.1](native/licenses/FFmpeg-LGPL-2.1.txt)
