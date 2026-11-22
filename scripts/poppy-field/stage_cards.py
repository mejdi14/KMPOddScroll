"""The hill's trees and the figures: the painting's stretch of the hill as it was, its trees standing again
along the crest where it bends round, each on a card turned to the walk, and the two pairs of figures."""
import math
import numpy as np
from PIL import Image
from common import *
from scene import load
from trees import TreePalette, tree

OLD_U0, OLD_U1 = 1884, 2433     # the hill row's stretch inside the painting, in the old cards texture
PX_PER_M = 62.52
ROW_BOTTOM = 279


def build(rng):
    old = np.asarray(Image.open(f'{SRC}/museum_poppies_cards.webp').convert('RGBA')).astype(np.float32) / 255
    rowA = old[:285, OLD_U0:OLD_U1].copy()
    # Where it is cut at the painting's left edge, soften it.
    rowA[..., 3] *= np.clip(np.arange(rowA.shape[1]) / 10.0, 0, 1)[None, :]
    figs = old[280:560, 0:400].copy()
    # Trees for the crest: the painting's cypress, bushes and tall tree, their feet faded into the slope.
    cuts = {'cypress': (36, 98), 'bushes': (106, 318), 'tall': (316, 540)}
    feet = smoothstep(ROW_BOTTOM, 244, np.arange(285))[:, None]
    sprites = {}
    for k, (a, b) in cuts.items():
        s = old[:285, OLD_U0 + a:OLD_U0 + b].copy()
        s[..., 3] *= feet
        side = np.minimum(np.arange(b - a) + 1, b - a - np.arange(b - a))[None, :]
        s[..., 3] *= np.clip(side / 5.0, 0, 1)
        rows = np.where(s[..., 3].max(1) > 0.05)[0]
        sprites[k] = s[rows[0]:ROW_BOTTOM]
    # And the same three again, re-tinted and reshaped, so the crest is not one tree over and over.
    from stage_band import resize
    def tint(sp, t, k):
        o = sp.copy(); o[..., :3] = np.clip(o[..., :3] * np.array(t, np.float32) * k, 0, 1); return o
    half = sprites['bushes'][:, :sprites['bushes'].shape[1] // 2 + 20].copy()
    hw = half.shape[1]
    half[..., 3] *= np.clip((hw - np.arange(hw)) / 14.0, 0, 1)[None, :]
    made = [
        tint(resize(sprites['bushes'], 1.25, 1.0), (1.04, 1.02, 0.9), 0.98),
        tint(resize(sprites['tall'], 0.9, 1.08), (0.95, 0.99, 1.02), 0.9),
        tint(resize(sprites['cypress'], 1.1, 1.3), (0.96, 1.0, 1.0), 0.9),
        tint(half, (1.02, 1.03, 0.95), 1.03),
    ]
    # The atlas: the row, the figures, then the crest's trees side by side.
    pieces = [sprites['cypress'], sprites['bushes'], sprites['tall']] + made
    W = max(rowA.shape[1] + 8 + 0, 400, sum(p.shape[1] + 6 for p in pieces[:4]))
    W = max(W, 1200)
    hC = max(p.shape[0] for p in pieces)
    rects = []
    x = 0; y = 577
    for p in pieces:
        if x + p.shape[1] > W: x = 0; y += hC + 4
        rects.append((x, y, p.shape[1], p.shape[0]))
        x += p.shape[1] + 6
    atlas = np.zeros((max(r[1] + r[3] for r in rects) + 4, W, 4), np.float32)
    atlas[:285, :rowA.shape[1]] = rowA
    atlas[291:571, :400] = figs
    for p, (x, y, w, h) in zip(pieces, rects):
        atlas[y:y + h, x:x + w] = p
    atlas = atlas[:max(r[1] + r[3] for r in rects) + 2]
    return atlas, rects


def crest_points():
    """Where trees stand along the crest left of the painting: (x, z) and how many stand together."""
    pts = [(-12.6, -18.44, 1)]
    for phi in (5, 11, 18, 26, 34, 43, 55, 66, 79, 93, 108, 121):
        a = math.radians(phi)
        r = ARC_R - 3.0
        pts.append((ARC_C[0] - r * math.sin(a), ARC_C[1] - r * math.cos(a), 1 + (phi % 3 == 0) + (phi % 4 == 0)))
    return pts


def billboards(rects, rng):
    Cm = np.array([-0.85, -3.6])
    quads = []
    for x, z, n in crest_points():
        to = np.array([x, z]) - Cm; dist = np.linalg.norm(to); fwd = to / dist
        right = np.array([-fwd[1], fwd[0]])
        off = 0.0
        for k in range(n):
            ri = rng.choice(len(rects), p=[0.12, 0.2, 0.2, 0.14, 0.14, 0.08, 0.12])
            rx, ry, rw, rh = rects[ri]
            scale = rng.uniform(0.85, 1.25) * (0.8 if ri == 1 else 1.0)
            w = rw / PX_PER_M * scale; h = rh / PX_PER_M * scale
            cx = x + right[0] * off; cz = z + right[1] * off
            # Round the arc the crest lies further back from its line; stand them on it.
            base = float(height(np.array([cx]), np.array([cz]))[0]) - 0.3 * scale
            flip = rng.uniform() < 0.5
            quads.append((np.linalg.norm(np.array([cx, cz]) - Cm), cx, cz, base, right, w, h, (rx, ry, rw, rh), flip))
            off += w * rng.uniform(0.55, 0.85) * (1 if k % 2 == 0 else -1.6)
    quads.sort(key=lambda q: -q[0])
    pos, uv, idx = [], [], []
    for _, cx, cz, base, right, w, h, (rx, ry, rw, rh), flip in quads:
        b0 = len(pos)
        NX, NY = 4, 2
        for j in range(NY + 1):
            for i in range(NX + 1):
                fx = i / NX; fy = j / NY
                px = cx + right[0] * (fx - 0.5) * w; pz = cz + right[1] * (fx - 0.5) * w
                pos.append((px, base + (1 - fy) * h, pz))
                uu = rx + (1 - fx if flip else fx) * rw
                uv.append((uu, ry + fy * rh))
        for j in range(NY):
            for i in range(NX):
                a0 = b0 + j * (NX + 1) + i; a1 = a0 + 1; c0 = a0 + NX + 1; c1 = c0 + 1
                idx += [a0, c0, c1, a0, c1, a1]
    return np.array(pos, np.float32), np.array(uv, np.float32), np.array(idx, np.int32).reshape(-1, 3)


def old_layers():
    """The painting's stretch of the hill row (trimmed to it) and the two pairs of figures, put on the new atlas."""
    hdr, route, L = load(f'{SRC}/museum_poppies_scene.bin')
    row = L[3]
    keep = row['pos'][:, 0] >= -10.0 - 1e-3
    tri = row['idx']
    tk = keep[tri].all(1)
    used = np.unique(tri[tk])
    remap = -np.ones(len(keep), np.int64); remap[used] = np.arange(len(used))
    rpos = row['pos'][used]; ruv = row['uv'][used].copy(); ruv[:, 0] -= OLD_U0
    ruv[:, 0] = np.clip(ruv[:, 0], 0.5, OLD_U1 - OLD_U0 - 0.5)
    ridx = remap[tri[tk]]
    out = [(rpos, ruv, ridx)]
    for k in (4, 5):
        f = L[k]; uv = f['uv'].copy(); uv[:, 1] += 11
        out.append((f['pos'], uv, f['idx']))
    return out


if __name__ == '__main__':
    rng = np.random.default_rng(9)
    atlas, rects = build(rng)
    np.save(f'{WORK}/out_cards.npy', atlas)
    pv = Image.new('RGBA', (atlas.shape[1], atlas.shape[0]), (255, 0, 255, 255))
    pv.alpha_composite(Image.fromarray((np.clip(atlas, 0, 1) * 255).astype(np.uint8)))
    pv.convert('RGB').save(f'{WORK}/cards_preview.png')
    bpos, buv, bidx = billboards(rects, rng)
    np.savez(f'{WORK}/mesh_crest.npz', pos=bpos, uv=buv, idx=bidx)
    for name, (p, u, i) in zip(('row', 'hillfig', 'fig'), old_layers()):
        np.savez(f'{WORK}/mesh_{name}.npz', pos=p, uv=u, idx=i)
    print('atlas', atlas.shape, 'crest quads', len(bpos) // 15, rects)
