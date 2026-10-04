# Runtime verification

The Demo now uses the supplied ProRes conversion. See [its verification](jimeng-verification.md)
and [the iOS performance fix](ios-performance.md). The checks below describe the
original synthetic 256×256 sample, before this asset change.

Recorded on 2026-10-04, Asia/Singapore. No physical-device performance assessment
was performed. These are runtime observations and build results, not added test suites.

## Asset

Generated with `scripts/generate-sample.py` using macOS VideoToolbox. Both apps
load the same `demo/src/commonMain/composeResources/files/alpha-demo.mov`.

| Property | Observed value |
| --- | --- |
| Codec / sample entry | HEVC / `hvc1` |
| Dimensions | 256 × 256 |
| Duration / frame count | 3.000 seconds / 90 frames |
| Frame rate | 30 fps |
| File size | 102,199 bytes |
| Decoded first-frame alpha | minimum 0, maximum 255 |
| Partially transparent pixels in first frame | 29,529 |
| Alpha association | premultiplied, from Apple alpha SEI |

The host FFmpeg decode and Android JNI decode both retained the auxiliary alpha
plane. The Android runtime reported:

```text
HEVC_ALPHA_RGBA width=256 height=256 alpha_min=0 alpha_max=255 semi_pixels=29529 premultiplied=1
```

## Android

Pixel 7 emulator, Android 16 / API 36, x86_64. Both x86_64 and arm64-v8a native
decoder libraries were built. The app was built, installed, and launched with the
repository script.

| Check | Result |
| --- | --- |
| Transparent corners reveal Compose background | Passed on checker, dark, and blue backgrounds |
| Half-alpha rectangle and gradient halo | Passed |
| Animation | Passed; successive video crops differ |
| Pause | Passed; video crops remained pixel-identical across a 0.7-second interval |
| Resume | Passed; motion returned |
| Loop | Passed; repeated `HEVC_ALPHA_LOOP` records, approximately 3 seconds apart |
| Loop disabled | Passed; status becomes `Ended` |
| Restart | Passed |
| Detach / attach | Passed; decoder closes and reopens |
| Background / foreground | Passed; loop progression stops while backgrounded, then resumes |

Pixel checks on the dark background: a transparent corner remained `(32,39,53)`,
and a sample of the half-alpha rectangle was `(136,38,91)`, matching decoded
premultiplied color blended over that background. A blue-background transparent
corner remained `(53,108,255)`.

[Checker screenshot](verification/android-checker.png),
[dark screenshot](verification/android-dark.png),
[blue screenshot](verification/android-blue.png),
[paused frame A](verification/android-paused-a.png),
[paused frame B](verification/android-paused-b.png).

## iOS

iPhone 18 Pro simulator, iOS 27, arm64, Xcode 27. Built, installed, and launched
through `scripts/run-ios.sh --simulator`. The final implementation uses
AVPlayerItemVideoOutput and Compose Image; it has no native video overlay.

| Check | Result |
| --- | --- |
| AVFoundation recognizes alpha video track | Passed; preparation reaches `Ready` |
| Transparent corners reveal Compose checker | Passed |
| Half-alpha rectangle | Passed; underlying checker remains visible through it |
| Gradient halo | Passed; soft transparency blends over the checker |
| Opaque cyan circle | Passed |
| Animation | Passed; circle moves between recorded frames |
| Loop | Passed; animation continues beyond the 3-second asset duration |
| Incremental Kotlin rebuild and relink | Passed; changed native code appears in the rebuilt app binary |
| Pause/resume, background switch, loop-off, detach/attach buttons | Not rechecked on the final iOS implementation; Mac locked during verification |

The JPEG screenshot has transparent corner samples `(255,255,255)` and
`(183,191,204)`, matching the two checker colors within JPEG rounding. Rectangle
samples over gray and white are `(211,111,165)` and `(246,147,194)` respectively;
their change with the background confirms partial transparency. The opaque circle
sample is `(55,226,252)`. Screenshot color conversion/compression prevents exact
byte comparison with decoded pixels.

[Checker screenshot](verification/ios-checker.jpg),
[moving frame A](verification/ios-moving-a.jpg),
[moving frame B](verification/ios-moving-b.jpg).

Two implementation issues were resolved during verification: CFString constants
must become NSString-compatible dictionary keys when requesting BGRA in Kotlin,
and Xcode must track the generated static archive explicitly to relink Kotlin
changes. Intermediate failing screenshots are not used as verification evidence.

## Builds

All completed successfully with the final code:

```text
./gradlew :alphavideo:assemble :demo:compileKotlinIosArm64 :androidApp:installDebug
ALPHAVIDEO_SIMULATOR_ID="<simulator-uuid>" ./scripts/run-ios.sh --simulator
```

The Android AAR is `alphavideo/build/outputs/aar/alphavideo.aar`. iOS device-arm64
Kotlin compilation passed; simulator playback passed. Device compilation is not
evidence of physical-device runtime behavior.

Android uses software decoding, with approximately 3.2 MiB of uncompressed native
libraries per arm64 ABI. Both platforms allocate decoded image data per frame.
High-resolution video, several simultaneous players, rotation, audio, network
sources, and public seeking are outside the verified scope of this component.
