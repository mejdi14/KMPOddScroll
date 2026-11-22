"""The far tree line: a ring 194 m out all the way round, the painting's own trees and house where the painting
shows them and the same trees, turned and spaced afresh, everywhere else, on a hedge that never breaks."""
import math, time
import numpy as np
from PIL import Image
from common import *
from scene import load

RB = 194.0
PXD = 21.23                     # texture pixels to a degree round the ring
YTOP, YBOT = 18.65, -1.7
BH = 120                        # rows, top to bottom
HALF = int(round(180 * PXD))    # one half of the ring
FULL = 2 * HALF
GUTTER = 4


def v_of_y(y): return (YTOP - np.asarray(y)) / (YTOP - YBOT) * BH
def y_of_v(v): return YTOP - np.asarray(v) / BH * (YTOP - YBOT)
def u_of_az(az_deg): return (np.asarray(az_deg) + 180.0) * PXD   # 0 .. FULL round the ring, az 0 ahead


def rasterize(dst, tri_xy, tri_uv, src):
    """Resamples src (RGBA) through triangles, destination pixel corners tri_xy, source texture coords tri_uv."""
    H, W = dst.shape[:2]
    for (p0, p1, p2), (t0, t1, t2) in zip(tri_xy, tri_uv):
        x0 = max(int(math.floor(min(p0[0], p1[0], p2[0]))), 0); x1 = min(int(math.ceil(max(p0[0], p1[0], p2[0]))), W - 1)
        y0 = max(int(math.floor(min(p0[1], p1[1], p2[1]))), 0); y1 = min(int(math.ceil(max(p0[1], p1[1], p2[1]))), H - 1)
        if x0 > x1 or y0 > y1: continue
        ys, xs = np.mgrid[y0:y1 + 1, x0:x1 + 1].astype(np.float64) + 0.5
        den = (p1[1] - p2[1]) * (p0[0] - p2[0]) + (p2[0] - p1[0]) * (p0[1] - p2[1])
        if abs(den) < 1e-12: continue
        w0 = ((p1[1] - p2[1]) * (xs - p2[0]) + (p2[0] - p1[0]) * (ys - p2[1])) / den
        w1 = ((p2[1] - p0[1]) * (xs - p2[0]) + (p0[0] - p2[0]) * (ys - p2[1])) / den
        w2 = 1 - w0 - w1
        m = (w0 >= -1e-6) & (w1 >= -1e-6) & (w2 >= -1e-6)
        if not m.any(): continue
        u = w0 * t0[0] + w1 * t1[0] + w2 * t2[0]; v = w0 * t0[1] + w1 * t1[1] + w2 * t2[1]
        reg = dst[y0:y1 + 1, x0:x1 + 1]
        reg[m] = bilinear(src, u[m], v[m])


def old_band_strip():
    hdr, route, L = load(f'{SRC}/museum_poppies_scene.bin')
    b = L[1]
    src = np.asarray(Image.open(f'{SRC}/museum_poppies_band.webp').convert('RGBA')).astype(np.float32) / 255
    p = b['pos']
    az = np.degrees(np.arctan2(p[:, 0], -p[:, 2]))
    xy = np.stack([u_of_az(az), v_of_y(p[:, 1])], -1)
    out = np.zeros((BH, FULL, 4), np.float32)
    rasterize(out, xy[b['idx']], b['uv'][b['idx']], src)
    return out


def comp(dst, spr, x, y_bottom, alpha=1.0):
    """Lays sprite spr (RGBA) over dst with its bottom row at y_bottom and its left at x, wrapping round."""
    h, w = spr.shape[:2]
    y0 = int(round(y_bottom)) - h
    ys = np.arange(y0, y0 + h); keep = (ys >= 0) & (ys < dst.shape[0])
    if not keep.any(): return
    xs = (np.arange(w) + int(round(x))) % dst.shape[1]
    s = spr[keep]
    reg = dst[np.ix_(ys[keep], xs)]
    a = s[..., 3:4] * alpha
    reg[..., :3] = reg[..., :3] * (1 - a) + s[..., :3] * a
    reg[..., 3:4] = reg[..., 3:4] * (1 - a) + a
    dst[np.ix_(ys[keep], xs)] = reg


def resize(spr, sx, sy):
    h, w = spr.shape[:2]
    nw, nh = max(2, int(round(w * sx))), max(2, int(round(h * sy)))
    pm = spr.copy(); pm[..., :3] *= pm[..., 3:4]
    im = np.stack([np.asarray(Image.fromarray(pm[..., c]).resize((nw, nh), Image.BICUBIC)) for c in range(4)], -1)
    im = np.clip(im, 0, 1)
    im[..., :3] = im[..., :3] / np.maximum(im[..., 3:4], 1e-4)
    return np.clip(im, 0, 1).astype(np.float32)


def build(rng):
    t0 = time.time()
    strip = old_band_strip()
    print(' resampled', time.time() - t0)
    # Where the painting shows the tree line, from ahead and a little left of the house to the right edge.
    a = strip[..., 3]
    tops = (a[:int(v_of_y(3.0))] > 0.5).sum(0)
    lo, hi = int(u_of_az(-8.5)), int(u_of_az(30.5))
    def gap_near(u):
        win = np.arange(u - 25, u + 26)
        return int(win[np.argmin(tops[win])])
    cut0, cut1 = gap_near(lo), gap_near(hi)
    seg = strip[:, cut0:cut1].copy()
    # The trees standing in it, for carrying on round the ring.
    sprites = []
    cuts = [0]
    seg_tops = tops[cut0:cut1]
    u = 0
    while u < seg.shape[1] - 30:
        win = np.arange(u + 28, min(u + 90, seg.shape[1] - 1))
        if len(win) == 0: break
        c = int(win[np.argmin(seg_tops[win])])
        cuts.append(c); u = c
    cuts.append(seg.shape[1])
    hedge_v = int(v_of_y(2.0))
    for c0, c1 in zip(cuts[:-1], cuts[1:]):
        spr = seg[:, c0:c1].copy()
        rows = np.where(spr[..., 3].max(1) > 0.3)[0]
        if len(rows) == 0 or c1 - c0 < 18: continue
        top = rows[0]
        if top > hedge_v - 8: continue  # no tree in it, only hedge
        spr = spr[top:]
        # Soften its sides where it was cut out of the row.
        w = spr.shape[1]
        side = np.minimum(np.arange(w) + 1, w - np.arange(w))[None, :]
        spr[..., 3] *= np.clip(side / 4.0, 0, 1)
        sprites.append(spr)
    print(' sprites', len(sprites), [s.shape for s in sprites])
    # The house: the widest light block in the painting's row.
    house_u = int(u_of_az(math.degrees(math.atan2((690 - PW / 2) / FOCAL, 1))))

    ring = np.zeros((BH, FULL, 4), np.float32)
    # A hedge all round under the trees, so the field's edge never shows through.
    hedge_src = seg[hedge_v:, :]
    hp = hedge_src[..., :3][hedge_src[..., 3] > 0.9]
    hedge_pal = Palette(hp, rng)
    base_rows = np.arange(int(v_of_y(1.4)), BH)
    ring[base_rows, :, :3] = hp.mean(0)
    ring[base_rows, :, 3] = 1
    ys_full = np.arange(BH)
    for k in range(int(FULL * 2.2)):
        cxp = rng.uniform(0, FULL)
        hgt = rng.uniform(1.2, 3.2)
        cy = v_of_y(rng.uniform(-0.5, hgt))
        la = rng.uniform(2.5, 6); lb = rng.uniform(1.5, 3.5)
        rows = np.arange(max(int(cy - lb - 2), 0), min(int(cy + lb + 3), BH))
        cols = (np.arange(int(cxp - la - 2), int(cxp + la + 3))) % FULL
        yy, xx = np.meshgrid(rows + 0.5, np.arange(int(cxp - la - 2), int(cxp + la + 3)) + 0.5, indexing='ij')
        s = (xx - cxp) / la; t = (yy - cy) / lb
        brush(ring, rows, cols, s, t, hedge_pal.near(hp.mean(0), k=4, spread=1), rng.uniform(0.6, 0.95), rng, alpha=True)
    # Trees dabbed in the painting's tree colours all round, spaced as the painting spaces them: round ones
    # mostly, singly or in twos and threes, a poplar now and then, low bushes between.
    from trees import TreePalette, tree
    inner = blur((seg[:hedge_v][..., 3] > 0.97).astype(np.float32), 1.2) > 0.97
    tp = seg[:hedge_v][..., :3][inner]
    tp = tp[(tp @ np.array([0.3, 0.55, 0.15], np.float32)) < 0.62]
    tpal = TreePalette(tp, rng)
    ground_v = v_of_y(0.4)
    u = cut1 + 8
    end = cut0 + FULL - 8
    while u < end - 30:
        r = rng.uniform()
        if r < 0.08:
            hgt = rng.uniform(55, 88); wid = hgt * rng.uniform(0.2, 0.28); kind = 'poplar'
        elif r < 0.26:
            hgt = rng.uniform(14, 30); wid = hgt * rng.uniform(1.3, 2.4); kind = 'bush'
        else:
            hgt = rng.uniform(30, 78); wid = hgt * rng.uniform(0.8, 1.35); kind = 'round'
        # Now and then a clump: two to four crowns run together, the middle one tallest.
        n = rng.integers(2, 5) if kind == 'round' and rng.uniform() < 0.35 else 1
        span = 0.0
        for k in range(n):
            h_k = hgt * (rng.uniform(0.7, 1.0) if k != n // 2 else 1.0) if n > 1 else hgt
            w_k = h_k * rng.uniform(0.8, 1.3) if n > 1 else wid
            x = u + span + w_k / 2
            if x + w_k / 2 > end: break
            tree(ring, x, ground_v + rng.uniform(0, 3), w_k, h_k, tpal, rng, kind=kind, dab_px=3.6)
            span += w_k * (rng.uniform(0.5, 0.8) if n > 1 else 1.0)
        span = max(span, 10.0)
        gap = rng.choice([rng.uniform(-0.2, 0.0), rng.uniform(0.05, 0.7), rng.uniform(0.9, 2.2)], p=[0.3, 0.5, 0.2])
        u += span * (1 + gap)
        if rng.uniform() < 0.03:
            u += rng.uniform(60, 200)
    # The painting's own stretch over it, as it was.
    span = (np.arange(cut0, cut1)) % FULL
    # Over the hedge where it has none of its own, so nothing new shows between its trees and its field.
    sa = seg[..., 3:4]
    reg = ring[:, span]
    low = (np.arange(BH) >= int(v_of_y(0.6)))[:, None, None]
    reg = np.where(low, seg, reg)
    reg[..., :3] = reg[..., :3] * (1 - sa) + seg[..., :3] * sa
    reg[..., 3:4] = reg[..., 3:4] * (1 - sa) + sa
    ring[:, span] = reg
    print(' composed', time.time() - t0)
    return ring


def atlas(ring):
    """Two halves, ahead (-90..90) over behind (90..270), one row each."""
    a = np.zeros((2 * BH + GUTTER, HALF + 3, 4), np.float32)
    for k, az0 in enumerate((-90.0, 90.0)):
        cols = (np.arange(HALF + 3) + START(az0)) % FULL
        a[k * (BH + GUTTER):k * (BH + GUTTER) + BH] = ring[:, cols]
    return a


def START(az0): return int(math.floor(u_of_az(az0)))


def mesh():
    STEP = 2.0
    pos, uv, idx = [], [], []
    ys = [YBOT, 1.0, 5.0, 11.0, YTOP]
    for half in (0, 1):
        az0 = -90.0 if half == 0 else 90.0
        base = len(pos)
        n = int(180 / STEP)
        for k in range(n + 1):
            az = az0 + k * STEP
            a = math.radians(az)
            for y in ys:
                pos.append((RB * math.sin(a), y, -RB * math.cos(a)))
                uv.append((float(u_of_az(az)) - START(az0) if half == 0 else float(u_of_az(az)) % FULL - START(az0) if az < 180 else float(u_of_az(az - 360)) + FULL - START(az0), float(v_of_y(y)) + (BH + GUTTER if half else 0)))
        nr = len(ys)
        for k in range(n):
            for j in range(nr - 1):
                a0 = base + k * nr + j; a1 = a0 + 1; b0 = a0 + nr; b1 = b0 + 1
                idx += [a0, b0, b1, a0, b1, a1]
    return np.array(pos, np.float32), np.array(uv, np.float32), np.array(idx, np.int32).reshape(-1, 3)


if __name__ == '__main__':
    rng = np.random.default_rng(5)
    ring = build(rng)
    a = atlas(ring)
    np.save(f'{WORK}/out_band.npy', a)
    pv = Image.new('RGBA', (a.shape[1], a.shape[0]), (255, 0, 255, 255))
    pv.alpha_composite(Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8)))
    pv.convert('RGB').save(f'{WORK}/band_preview.png')
    pos, uv, idx = mesh()
    np.savez(f'{WORK}/mesh_band.npz', pos=pos, uv=uv, idx=idx)
    print('done')
