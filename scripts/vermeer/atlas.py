"""Pack painted cutouts with transparent gutters for bilinear filtering."""

import numpy as np
from PIL import Image


def pack_sprites(sprites):
    atlas = Image.new("RGBA", (2048, 2048))
    heights = np.zeros(atlas.width, int)
    bounds = {}
    for name, sprite in sorted(sprites.items(), key=lambda item: -item[1].height):
        w, h = sprite.size
        peaks = np.lib.stride_tricks.sliding_window_view(heights, w+4).max(axis=1)
        left = int(peaks.argmin())
        top = int(peaks[left])
        assert top+h+4 <= atlas.height
        atlas.paste(sprite, (left+2, top+2))
        bounds[name] = (left+2, top+2, w, h)
        heights[left:left+w+4] = top+h+4
    return atlas.crop((0, 0, atlas.width, int(heights.max()))), bounds
