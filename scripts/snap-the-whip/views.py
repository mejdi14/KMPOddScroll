"""Renders the place at phone size the way the app draws it, each view a route,yaw,pitch, side by side:

    python3 views.py look 0,0,0 1,0,0 1,-70,0 1,180,0
"""
import sys
import numpy as np
from PIL import Image
import render as R
from scene import load
from common import WORK, NAME

if __name__ == '__main__':
    sc = load(f'{WORK}/out/{NAME}_scene.bin'); T = R.textures(['far', 'ground', 'paint', 'band', 'cards'])
    tag = sys.argv[1]
    views = [tuple(map(float, v.split(','))) for v in sys.argv[2:]]
    ims = []
    for route, yaw, pitch in views:
        im, _ = R.render(sc, T, W=390, H=844, route=route, yaw=yaw, pitch=pitch)
        ims.append(Image.fromarray(im))
    W = sum(i.width for i in ims) + 10 * (len(ims) - 1)
    s = Image.new('RGB', (W, ims[0].height), 'white'); x = 0
    for i in ims: s.paste(i, (x, 0)); x += i.width + 10
    s.save(f'{WORK}/{tag}.png')
