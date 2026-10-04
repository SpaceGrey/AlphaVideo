# iOS video frame-rate measurement

Measured on 2026-10-04 using the iPhone 18 Pro arm64 simulator, iOS 27, Xcode 27,
Kotlin/Native 2.4.10, Debug builds. Both runs used the same 1160×798 HEVC Alpha
movie, with 144 frames in 6.019933 seconds: approximately **23.92 fps**.

## Before / after

The first four warm playback windows in each run were used. Each window lasted
at least 5 seconds; the first 2 seconds of startup were excluded.

| Measured value | Original | Native memcpy |
| --- | ---: | ---: |
| Frames retrieved from AVPlayerItemVideoOutput / second | 2.00 | 23.78 |
| Distinct video images drawn by Compose / second | 1.78 | 23.78 |
| Pixel-buffer copy, mean ms / frame | 484.95 | 0.88 |
| Video-output retrieval call, mean ms / frame | 0.11 | 0.26 |
| Skia bitmap installation, mean ms / frame | 0.52 | 0.64 |

The resulting distinct-draw rate is approximately 99.4% of the source frame rate
and about 13.4 times the original draw rate. A short-window counter has boundary
error of up to one frame and includes loop seek overhead.

## Cause and change

`CPointer.readBytes()` delegates to Kotlin/Native's `nativeMemUtils.getByteArray`.
In the bundled 2.4.10 `nativeMain/kotlinx/cinterop/NativeMem.kt`, that function uses
a Kotlin loop to read and assign individual bytes. A frame here has approximately
3.7 MB of BGRA data. Running that loop on the main thread blocked each frame for
about half a second in the measured Debug build.

`AppleAlphaFrames.kt` now allocates the destination ByteArray, pins it for a single
native `memcpy`, then gives the copied data to a new immutable Skia bitmap. It
preserves CVPixelBuffer row stride, releases the lock in `finally`, and releases
the retained pixel buffer after processing. The displayed bitmap is not reused or
mutated, avoiding a race with Compose rendering. Video pixels and alpha are not
downscaled or removed to achieve the improvement.

## Method and artifacts

Published build logs replace local filesystem paths and simulator UUIDs with
placeholders; measurement values are unchanged.

Temporary instrumentation measured the output call, locked buffer copy, and bitmap
installation separately with a monotonic uptime clock. A `drawWithContent` callback
counted distinct ImageBitmap instances actually drawn by Compose, excluding repeated
draws of the same image. Counters were written to a CSV every 5 seconds.

- [Baseline measurements](verification/performance/baseline.csv)
- [Optimized measurements](verification/performance/optimized.csv)
- [Four-window means](verification/performance/summary.json)
- [Baseline build and launch](verification/performance/baseline-build-run.log)
- [Optimized build and launch](verification/performance/memcpy-build-run.log)
- [Final build after removing instrumentation](verification/performance/final-build-run.log)
- [Device-arm64 Kotlin compilation](verification/performance/device-compilation.log)

The temporary profiler was removed after capture. The final component retains the
native copy optimization. Both profiled builds had identical instrumentation and
the same playback flow. The final app was rebuilt and manually checked for
transparency, pause/resume, background changes, looping and disposal.

These counts measure distinct Compose draw submissions, not GPU scan-out timestamps.
They do not establish physical-device battery use, Release performance, or performance
with multiple videos. The remaining CPU pixel copy, per-frame allocation, and GPU
upload could be further reduced with native texture integration if future workloads
need it; this single video already reaches its source cadence in the measured setup.
