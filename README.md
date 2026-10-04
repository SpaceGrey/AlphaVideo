# AlphaVideo

**简体中文** | [English](README.en.md)

面向 Compose Multiplatform 的跨平台视频播放组件，为 Android 和 iOS 提供统一的播放 API，支持透明视频与界面内容叠加。

## 特性

- Android 和 iOS 共用一套 Compose API。
- 支持 HEVC Alpha 透明视频。
- 播放、暂停与循环播放。
- 适应或裁剪视频尺寸。
- 播放状态回调。
- 自动处理应用前后台切换和组件生命周期。

## 平台支持

| 平台 | 最低版本 |
| --- | --- |
| Android | Android 10 / API 29 |
| iOS | iOS 18 |

当前支持本地 HEVC Alpha `.mov` 文件，使用文件绝对路径作为播放源。

## 接入

将 `alphavideo` 模块加入工程，并保留仓库中的 `scripts` 和 `native` 目录。

```kotlin
// settings.gradle.kts
include(":alphavideo")
```

在 KMP 模块中添加依赖：

```kotlin
commonMain.dependencies {
    implementation(project(":alphavideo"))
}
```

## 使用

```kotlin
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import dev.alphavideo.AlphaVideo
import dev.alphavideo.AlphaVideoSource

// 在 Composable 中调用
AlphaVideo(
    source = AlphaVideoSource("/absolute/path/video.mov"),
    modifier = Modifier.fillMaxSize(),
    playing = true,
    loop = true,
    contentScale = ContentScale.Fit,
    onStatus = { status ->
        // 处理播放状态
    },
)
```

通过 `playing` 控制播放和暂停，通过 `loop` 控制循环。`contentScale` 支持 `Fit` 和 `Crop`，`onStatus` 提供 `Loading`、`Ready`、`Ended` 和 `Failed` 状态。

## Demo

仓库包含 Android 和 iOS 示例应用。

```sh
# Android
./scripts/run-android.sh

# iOS
./scripts/run-ios.sh --simulator
```

## 第三方许可证

[FFmpeg LGPL 2.1](native/licenses/FFmpeg-LGPL-2.1.txt)
