#!/usr/bin/env bash
# Turn a recording of a demo into a small looping GIF for the README.
#
#   scripts/demo-gif.sh <input> <output.gif> [crop] [width]
#
#   crop    ffmpeg crop in source pixels, W:H:X:Y (e.g. 3040:1480:400:420); "" for none
#   width   output width in px, default 520
#
#   FPS=10 COLORS=48 override the frame rate and palette size.
#
# What keeps it small: a GIF only stores the pixels that change between frames,
# so the grain of a camera recording costs a whole new frame every frame. The
# temporal denoise (hqdn3d) holds still areas still, the palette is built from
# the moving parts only (stats_mode=diff), each frame stores only its changed
# rectangle (diff_mode=rectangle), and no dithering keeps flat areas flat.
# 10 fps and 48 colours are plenty for a phone screen; a desktop screen
# recording has no grain and can afford COLORS=128.
set -euo pipefail

in=${1:?input video}
out=${2:?output gif}
crop=${3:-}
width=${4:-520}
fps=${FPS:-10}
colors=${COLORS:-48}

filters="fps=${fps}"
[[ -n $crop ]] && filters="crop=${crop},${filters}"
filters="${filters},scale=${width}:-2:flags=lanczos,hqdn3d=4:4:30:30"

mkdir -p "$(dirname "$out")"
ffmpeg -v error -y -i "$in" -an -filter_complex \
  "${filters},split[a][b];[a]palettegen=max_colors=${colors}:stats_mode=diff[p];[b][p]paletteuse=dither=none:diff_mode=rectangle" \
  -loop 0 "$out"

ls -lh "$out" | awk '{print $5, $NF}'
