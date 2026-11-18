#!/usr/bin/env bash
# Turn a recording of a demo into a looping GIF for the README.
#
#   scripts/demo-gif.sh <input> <output.gif> [crop] [width]
#
#   crop    ffmpeg crop in source pixels, W:H:X:Y (e.g. 3040:1480:400:420); "" for none
#   width   output width in px, default 640
#
#   FPS=12 COLORS=128 override the frame rate and palette size.
#
# A GIF only stores the pixels that change between frames, so the grain of a
# camera recording costs a whole new frame every frame. A light temporal
# denoise (hqdn3d) holds still areas still without smearing the motion, the
# palette is built from the moving parts (stats_mode=diff), and each frame maps
# only its changed rectangle (diff_mode=rectangle). An ordered (bayer) dither
# keeps gradients from banding while staying put from frame to frame, so it
# compresses far better than error diffusion. Size goes with length: a 37 s
# phone recording comes out around 15 MB, so trim long ones.
set -euo pipefail

in=${1:?input video}
out=${2:?output gif}
crop=${3:-}
width=${4:-640}
fps=${FPS:-12}
colors=${COLORS:-128}

filters="fps=${fps}"
[[ -n $crop ]] && filters="crop=${crop},${filters}"
filters="${filters},scale=${width}:-2:flags=lanczos,hqdn3d=3:3:16:16"

mkdir -p "$(dirname "$out")"
# Two passes (palette, then map) rather than one split graph: the split holds
# every frame in memory until the palette is done, which a 1080p source at
# 60 fps can exhaust.
palette=$(mktemp -t demo-gif-palette).png
trap 'rm -f "$palette"' EXIT
ffmpeg -v error -y -i "$in" -an \
  -vf "${filters},palettegen=max_colors=${colors}:stats_mode=diff" "$palette"
ffmpeg -v error -y -i "$in" -i "$palette" -an -filter_complex \
  "[0:v]${filters}[v];[v][1:v]paletteuse=dither=bayer:bayer_scale=5:diff_mode=rectangle" \
  -loop 0 "$out"

ls -lh "$out" | awk '{print $5, $NF}'
