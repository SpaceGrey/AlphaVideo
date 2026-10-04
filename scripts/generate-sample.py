#!/usr/bin/env python3
"""Generate a real HEVC auxiliary-alpha movie using macOS VideoToolbox."""
import math
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parent.parent
output = root / "demo/src/commonMain/composeResources/files/alpha-demo.mov"
output.parent.mkdir(parents=True, exist_ok=True)
width = height = 256
fps, frames = 30, 90
base = bytearray(width * height * 4)
for y in range(height):
    for x in range(width):
        radius = math.hypot(x - 128, y - 170)
        alpha = int(max(0, min(1, (80 - radius) / 40)) * 96)
        red, green, blue = 80, 255, 100
        if 32 <= x < 224 and 32 <= y < 96:
            red, green, blue, alpha = 255, 64, 128, 128
        offset = (y * width + x) * 4
        # VideoToolbox's default HEVC Alpha mode is premultiplied.
        base[offset:offset + 4] = bytes([
            (blue * alpha + 127) // 255, (green * alpha + 127) // 255,
            (red * alpha + 127) // 255, alpha,
        ])
command = [
    "ffmpeg", "-hide_banner", "-loglevel", "warning", "-y",
    "-f", "rawvideo", "-pixel_format", "bgra", "-video_size", f"{width}x{height}",
    "-framerate", str(fps), "-i", "pipe:0", "-an",
    "-c:v", "hevc_videotoolbox", "-alpha_quality", "1", "-b:v", "240k",
    "-tag:v", "hvc1", "-color_primaries", "bt709", "-color_trc", "bt709",
    "-colorspace", "bt709", "-movflags", "+faststart", str(output),
]
process = subprocess.Popen(command, stdin=subprocess.PIPE)
try:
    for frame in range(frames):
        bgra = bytearray(base)
        cx = 128 + 62 * math.sin(2 * math.pi * frame / frames)
        for y in range(142, 199):
            for x in range(max(0, int(cx) - 28), min(width, int(cx) + 30)):
                if (x - cx) ** 2 + (y - 170) ** 2 <= 28 ** 2:
                    offset = (y * width + x) * 4
                    bgra[offset:offset + 4] = bytes((255, 210, 40, 255))
        process.stdin.write(bgra)
finally:
    process.stdin.close()
if process.wait() != 0:
    raise SystemExit("VideoToolbox HEVC Alpha encoding failed")
print(f"Generated {output}: {output.stat().st_size:,} bytes; {frames} frames at {fps} fps")
