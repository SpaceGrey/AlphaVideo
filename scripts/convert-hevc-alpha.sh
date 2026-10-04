#!/usr/bin/env bash
# Convert a straight-alpha source video using macOS VideoToolbox.
set -euo pipefail
[[ $# == 2 ]] || { echo "Usage: $0 <input-with-alpha.mov> <output.mov>" >&2; exit 1; }
INPUT="$1"
OUTPUT="$2"
[[ "$INPUT" != "$OUTPUT" ]] || { echo "Input and output must differ" >&2; exit 1; }
mkdir -p "$(dirname "$OUTPUT")"
ffmpeg -hide_banner -loglevel warning -n -i "$INPUT" \
  -map 0:v:0 -map_metadata -1 -an \
  -vf 'format=gbrap,premultiply=inplace=1,format=bgra' \
  -c:v hevc_videotoolbox -alpha_quality 1 -b:v 2M -tag:v hvc1 \
  -color_primaries bt709 -color_trc bt709 -colorspace bt709 \
  -fps_mode passthrough -video_track_timescale 90000 -movflags +faststart "$OUTPUT"
