# Supplied video conversion and playback

Converted and checked on 2026-10-04. The supplied `.mov` was already ProRes 4444
with an alpha plane, despite its green-screen filename. No additional chroma key
was applied. The existing subject, edge transparency, and green shadow were retained.

| Property | Original | Converted |
| --- | --- | --- |
| Codec | ProRes 4444 (`ap4h`) | HEVC with auxiliary alpha (`hvc1`) |
| Dimensions | 1160 × 798 | 1160 × 798 |
| Frames / duration | 144 / 6.019933 seconds | 144 / 6.019933 seconds |
| Average cadence | approximately 23.92 fps | approximately 23.92 fps |
| File size | 111,239,972 bytes | 2,600,781 bytes |

The resulting file is **97.66% smaller**. It is located at
`demo/src/commonMain/composeResources/files/jimeng-hevc-alpha.mov` and is now the
Demo's default movie on both platforms. The synthetic asset remains in the resource
folder for future use.

The conversion script premultiplies straight-alpha RGB before VideoToolbox encoding,
uses 2 Mbit/s for the base HEVC layer and alpha quality 1, and writes a 90,000-timescale
MOV. Source timestamps are retained within that timescale's precision. RGB is lossy;
the component's supported output is 8-bit alpha, rather than the source's higher
precision.

## Alpha verification

All **144 frames** of both files were decoded to 8-bit RGBA and compared:

- Alpha mean absolute error: **0**.
- Maximum alpha difference: **0**.
- Number of mismatching alpha pixels: **0**.
- First output frame: alpha minimum 0, maximum 255; 486,459 fully transparent pixels
  and 66,390 partially transparent pixels.

This establishes equality of the 8-bit alpha samples, not lossless preservation of
the source's higher bit depth or its RGB. See [the complete alpha report](verification/jimeng/alpha-comparison.json)
and [output metadata](verification/jimeng/output-probe.json).

## Runtime

Android: Pixel 7 emulator, API 36 / Android 16, x86_64. iOS: iPhone 18 Pro simulator,
iOS 27, arm64. Both apps were built and launched with their repository scripts.

| Check | Android | iOS final optimized build |
| --- | --- | --- |
| Loads this 1160×798 file | Passed | Passed |
| Transparent background exposes Compose checker | Passed | Passed |
| Animated subject | Passed | Passed |
| Repeated loops | Passed; observed cycles about 6.03 seconds apart | Passed |
| Pause / resume with this movie | Not rechecked on Android | Passed; two paused crops were pixel-identical |
| Dark and blue backgrounds with this movie | Not rechecked on Android | Passed |
| Loop disabled reaches Ended | Not rechecked on Android | Passed |
| Restart and detach / attach | Not rechecked on Android | Passed |

The original Android control checks are recorded separately in `verification.md`.
For this file, Android reported alpha minimum 0, maximum 255, 66,390 partially
transparent pixels, and premultiplied-alpha metadata. The demo's checker is clipped
to the video's rectangular bounds so it does not paint behind controls.

The initial iOS implementation displayed the movie but drew only about **1.78 fps**.
It was therefore not adequate for this asset. The measured and fixed final path
draws about **23.78 fps**, compared with the source's 23.92 fps. The [performance report](ios-performance.md)
contains the cause, code change, and before/after records.

Screenshots: [Android](verification/jimeng/android-checker.png),
[iOS checker](verification/jimeng/ios-checker.jpg),
[iOS dark](verification/jimeng/ios-dark.jpg),
[iOS blue](verification/jimeng/ios-blue.jpg),
[paused A](verification/jimeng/ios-paused-a.jpg),
[paused B](verification/jimeng/ios-paused-b.jpg).

Build records: [Android](verification/jimeng/android-build-run.log),
[iOS final optimized build](verification/performance/final-build-run.log).
No physical-device performance or battery measurement was performed.
