"""Puts the place together: its textures as webp and its scene file, then renders it as the app would."""
import math, os, sys
import numpy as np
from PIL import Image
from common import *
from scene import save, load

OUT = f'{WORK}/out'
os.makedirs(OUT, exist_ok=True)
SKY, SOLID, CARD, DECAL = 0, 1, 2, 4
NAMES = ['far', 'ground', 'paint', 'band', 'cards']


def write_textures():
    def rgb(path, name, q):
        im = Image.open(path).convert('RGB')
        im.save(f'{OUT}/museum_poppies_{name}.webp', quality=q, method=6)
    def rgba(npy, name, q):
        a = np.load(npy)
        a = np.clip(a, 0, 1)
        # Colour under transparent texels carried out from the edge, so filtering never pulls in black.
        w = (a[..., 3] > 0.02).astype(np.float32)
        fill = pushpull(a[..., :3], w)
        a[..., :3] = np.where(w[..., None] > 0, a[..., :3], fill)
        Image.fromarray((a * 255 + 0.5).astype(np.uint8), 'RGBA').save(f'{OUT}/museum_poppies_{name}.webp', quality=q, alpha_quality=90, method=6)
    rgb(f'{WORK}/out_sky.png', 'far', 84)
    rgb(f'{WORK}/out_ground.png', 'ground', 82)
    rgba(f'{WORK}/out_paint.npy', 'paint', 92)
    rgba(f'{WORK}/out_band.npy', 'band', 88)
    rgba(f'{WORK}/out_cards.npy', 'cards', 90)


def write_scene():
    hdr = dict(hFov=HFOV, tilt=TILT, yawMax=180.0, pitchDown=35.0, pitchUp=35.0)
    layers = []
    for kind, tex, name in ((SKY, 0, 'sky'), (SKY, 2, 'skydecal'), (CARD, 3, 'band'), (SOLID, 1, 'ground'),
                            (DECAL, 2, 'grounddecal'), (CARD, 4, 'crest'), (CARD, 4, 'row'), (CARD, 4, 'hillfig'), (CARD, 4, 'fig')):
        m = np.load(f'{WORK}/mesh_{name}.npz')
        assert len(m['pos']) < 32768, (name, len(m['pos']))
        w = m['w'] if 'w' in m.files else np.ones(len(m['pos']), np.float32)
        layers.append(dict(kind=kind, tex=tex, pos=m['pos'], uv=m['uv'], w=w, idx=m['idx']))
        print(f'  {name}: {len(m["pos"])} vertices, {len(m["idx"])} triangles')
    save(f'{OUT}/museum_poppies_scene.bin', hdr, ROUTE.astype(np.float32), layers)


if __name__ == '__main__':
    if 'tex' in sys.argv: write_textures()
    write_scene()
    for f in sorted(os.listdir(OUT)):
        print(f, os.path.getsize(f'{OUT}/{f}') // 1024, 'KB')
