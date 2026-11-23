"""The meadow: one log-polar texture round the walk, painted stroke by stroke in the painting's own colours, and
its mesh. Olive grass in upright flicks, long dark blades and weed stalks, drifts of red bramble leaves, asters
and Queen Anne's lace close to; the beaten red earth of the schoolyard in front of the schoolhouse, and the
meadow going over to a fresher green out towards the woods."""
import math, time, sys
import numpy as np
from PIL import Image
from common import *

NU, NV = 3072, 2048
R0, RMAX = 0.02, 44.0
C = np.array([-0.16, -1.2])
LOGR = math.log(RMAX / R0)
EYE_H = 1.15

def r_of_v(v): return R0 * np.exp(np.asarray(v, np.float64) / NV * LOGR)
def v_of_r(r): return NV * np.log(np.maximum(np.asarray(r, np.float64), 1e-6) / R0) / LOGR
def az_of_u(u): return np.asarray(u, np.float64) / NU * 2 * math.pi - math.pi
def u_of_az(a): return (np.asarray(a, np.float64) + math.pi) / (2 * math.pi) * NU

def world_of(u, v):
    r = r_of_v(v); a = az_of_u(u)
    return C[0] + r * np.sin(a), C[1] - r * np.cos(a)

def uv_of(x, z):
    dx = np.asarray(x) - C[0]; dz = np.asarray(z) - C[1]
    return u_of_az(np.arctan2(dx, -dz)), v_of_r(np.hypot(dx, dz))


def texel_grid():
    u = np.arange(NU) + 0.5; v = np.arange(NV) + 0.5
    r = r_of_v(v)[:, None]; a = az_of_u(u)[None, :]
    X = (C[0] + r * np.sin(a)).astype(np.float32)
    Z = (C[1] - r * np.cos(a)).astype(np.float32)
    return X, Z


def painting_fields():
    """The painting's meadow as colour, blurred within the meadow alone, the colour of its grass with the yard,
    the leaves and the flowers taken out, and where it shows the yard's bare earth."""
    p = painting(); reg = regions()
    ground = (reg['ground'] > 0.5).astype(np.float32)
    others = ((reg['fig'] > 0.05) | (reg['band'] > 0.5)).astype(np.float32)
    near_other = blur(others, 4) > 0.02
    mask = ground * (~near_other) * (blur(ground, 1.5) > 0.98)
    R, G, B = p[..., 0], p[..., 1], p[..., 2]
    ys = np.mgrid[0:PH, 0:PW][0]
    warm = np.clip((R - G - 0.03) * 6, 0, 1) * mask
    yard = warm * (ys < 445)                           # far off, warm is the yard's earth; close to, red leaves
    light = np.clip((np.minimum(np.minimum(R, G), B) - 0.45) * 6, 0, 1) * mask
    def nconv(img, s):
        w = blur(mask, s)
        return blur(img * (mask[..., None] if img.ndim == 3 else mask), s) / np.maximum(w if img.ndim == 2 else w[..., None], 1e-4)
    col = nconv(p, 3.0)
    gmask = mask * (warm < 0.2) * (light < 0.2)
    wg = blur(gmask, 5)
    grass = blur(p * gmask[..., None], 5) / np.maximum(wg, 1e-4)[..., None]
    return dict(mask=mask, col=col, grass=grass, yard=nconv(yard, 3), warm_px=warm, light_px=light)


def palettes(f, rng):
    p = painting()
    m = f['mask'] > 0.5
    ys, xs = np.mgrid[0:PH, 0:PW]
    R, G, B = p[..., 0], p[..., 1], p[..., 2]
    lum = p @ np.array([0.3, 0.55, 0.15], np.float32)
    grass = (f['warm_px'] < 0.05) & (f['light_px'] < 0.05) & m
    yard = m & (ys < 440) & (f['warm_px'] > 0.5)
    far = grass & (ys < 445)
    mid = grass & (ys >= 445) & (ys < 620)
    near = grass & (ys >= 620)
    dark = near & (lum < np.percentile(lum[near], 18))
    bright = (grass & (ys > 430)) & (lum > np.percentile(lum[grass & (ys > 430)], 85))
    # The foreground's bramble leaves, its asters and its weed stalks.
    leaf = m & (ys > 640) & (R > G + 0.15) & (R > 0.35)
    stalk = m & (ys > 600) & (R > G) & (lum < 0.3) & (f['warm_px'] > 0.2)
    green = m & (ys > 640) & (G > R + 0.02) & (G > B + 0.08) & (lum < 0.3)
    print(' palette px: yard', yard.sum(), 'far', far.sum(), 'mid', mid.sum(), 'near', near.sum(), 'leaf', leaf.sum(), 'stalk', stalk.sum(), 'green', green.sum())
    return dict(
        near=Palette(p[near], rng), dark=Palette(p[dark], rng), mid=Palette(p[mid], rng), far=Palette(p[far], rng),
        yard=Palette(p[yard], rng), bright=Palette(p[bright], rng), leaf=Palette(p[leaf], rng),
        stalk=Palette(p[stalk], rng), green=Palette(p[green], rng),
        near_mean=p[near].mean(0), mid_mean=p[mid].mean(0), far_mean=p[far].mean(0), yard_mean=p[yard].mean(0),
    )


# The schoolyard's beaten earth in the world: in front of the schoolhouse, which stands on the ring a little right
# of ahead, running on past the painting's right edge.
YARD_C = np.array([9.0, -31.0]); YARD_R = np.array([12.0, 8.5])


def build_fields(X, Z, f, pal):
    """Colour, grass colour and the yard's bare earth over the whole meadow: the painting's where it sees it,
    carried on round it and then into a meadow of the same paint."""
    t0 = time.time()
    Y = height(X, Z).astype(np.float32)
    P = np.stack([X, Y, Z], -1).astype(np.float32)
    d = P - E0[None, None].astype(np.float32)
    dist = np.linalg.norm(d, axis=-1)
    px, py, zc = to_pixel(d / dist[..., None])
    inside = (zc > 0.05) & (px >= 0) & (px < PW) & (py >= 0) & (py < PH)
    w = np.where(inside, bilinear(f['mask'], px, py), 0).astype(np.float32)
    w = np.where(w > 0.5, 1.0, w * 0.5).astype(np.float32)
    print(' projected', time.time() - t0)
    col = np.where(inside[..., None], bilinear(f['col'], px, py), 0).astype(np.float32)
    grass = np.where(inside[..., None], bilinear(f['grass'], px, py), 0).astype(np.float32)
    yard = np.where(inside, bilinear(f['yard'], px, py), 0).astype(np.float32)
    stack = np.concatenate([col, grass, yard[..., None]], -1)
    ext = pushpull(stack, w)
    print(' extended', time.time() - t0)
    near = np.clip(blur(w, 90, wrap_x=True) * 2.2, 0, 1)
    near = np.maximum(near, w)
    # The meadow everywhere else: olive close to and in the middle distance as the painting's is, going over to
    # a fresher green out towards the woods, with darker hollows and lighter, drier swathes.
    r = np.hypot(X - E0[0], Z - E0[2]) * 0.5 + np.hypot(X - C[0], Z - C[1]) * 0.5
    t_mid = smoothstep(3.0, 9, r)[..., None]
    t_far = smoothstep(12, 35, r)[..., None]
    base = pal['near_mean'] * (1 - t_mid) + pal['mid_mean'] * t_mid
    base = base * (1 - t_far) + pal['far_mean'] * t_far
    drift = (0.5 + 0.5 * fbm(X / 14, Z / 14, seed=21, octaves=4))[..., None]
    g_proc = base * (0.88 + 0.24 * drift)
    dry = smoothstep(0.1, 0.5, fbm(X / 22, Z / 9, seed=57, octaves=3))[..., None] * 0.35
    g_proc = g_proc * (1 - dry) + np.array([0.36, 0.33, 0.16], np.float32) * dry
    greener = (0.5 + 0.5 * fbm(X / 11, Z / 11, seed=53, octaves=3))[..., None] * 0.3
    g_proc = g_proc * (1 - greener) + np.array([0.24, 0.31, 0.12], np.float32) * greener
    g_proc = g_proc * (1 + 0.07 * fbm(X / 6, Z / 6, seed=25, octaves=4))[..., None]
    # The yard: a rounded patch of bare earth before the schoolhouse, ragged where the grass takes it back.
    q = np.hypot((X - YARD_C[0]) / YARD_R[0], (Z - YARD_C[1]) / YARD_R[1])
    yard_proc = smoothstep(1.15, 0.8, q + 0.25 * fbm(X / 3, Z / 3, seed=71, octaves=3))
    n = near[..., None]
    yard_all = (np.clip(ext[..., 6] * 1.4, 0, 1) * near + yard_proc * (1 - near)).clip(0, 1).astype(np.float32)
    col_all = ext[..., 0:3] * n + g_proc * (1 - n)
    grass_all = ext[..., 3:6] * n + g_proc * (1 - n)
    # Where the yard goes on past the painting, the earth's colour goes with it.
    yo = (yard_proc * (1 - near))[..., None]
    col_all = col_all * (1 - yo) + pal['yard_mean'] * (0.95 + 0.1 * drift) * yo
    out = dict(col=col_all.astype(np.float32), grass=grass_all.astype(np.float32), yard=yard_all,
               near=near.astype(np.float32), w=w)
    print(' fields', time.time() - t0)
    return out


def angular(dist, deg, lo=0.3, hi=1.6):
    """A stroke's angular size at a distance: large up close, smaller further off, as the painting's are."""
    return math.radians(deg) * float(np.clip((4.0 / dist) ** 0.45, lo, hi))


# name, coverage, width and height in degrees at four metres, and how near it is laid (metres from the walk)
KINDS = (
    ('dab', 2.0, 0.55, 0.3, 999),
    ('grass', 2.0, 0.16, 0.5, 999),
    ('earth', 1.3, 0.8, 0.3, 999),
    ('grass2', 1.1, 0.1, 0.36, 999),
    ('blade', 0.08, 0.05, 2.2, 6),
    ('stalk', 0.014, 0.03, 2.8, 3.2),
    ('leaf', 0.06, 0.4, 0.26, 8),
    ('aster', 0.012, 0.45, 0.32, 9),
    ('lace', 0.004, 0.55, 0.3, 9),
    ('clover', 0.012, 0.26, 0.2, 8),
    ('light', 0.2, 0.14, 0.36, 30),
)
ORDER = {'dab': 0, 'earth': 1, 'grass': 2, 'grass2': 3, 'clover': 4, 'leaf': 5, 'light': 6, 'blade': 7, 'stalk': 8,
         'aster': 9, 'lace': 10}


def paint(canvas, X, Z, F, pal, rng):
    """Strokes laid as the eye sees them from the walk: each one a size and a slant in the view, put down on the
    ground under it, so a grass flick stands up and a daisy lies flat wherever it is."""
    t0 = time.time()
    count = 0
    band = 16
    cols_all = np.arange(NU)
    for v0 in range(0, NV, band):
        v1 = v0 + band
        rm = float(r_of_v((v0 + v1) / 2))
        dist = math.hypot(rm, EYE_H)
        kh = dist; kv = dist * dist / EYE_H          # metres of ground to a radian across and up the view
        circ = 2 * math.pi * rm / NU; rad = rm * LOGR / NV
        area_band = NU * band
        jobs = []
        for kind, cov, wd, hd, reach_m in KINDS:
            if rm > reach_m: continue
            if kind == 'blade':
                w_, h_ = angular(dist, wd, 0.6, 1.4), angular(dist, hd, 0.5, 1.5)
            else:
                w_, h_ = angular(dist, wd), angular(dist, hd)
            a_tex = math.pi * (w_ * kh / 2 / circ) * (h_ * kv / 2 / rad)
            fade = float(smoothstep(reach_m, reach_m * 0.6, rm)) if reach_m < 999 else 1.0
            n = int(cov * fade * area_band / max(a_tex, 0.5))
            us = rng.uniform(0, NU, n); vs = rng.uniform(v0, v1, n)
            iv_, iu_ = np.clip(vs.astype(int), 0, NV - 1), us.astype(int) % NU
            if kind == 'earth':
                keep = rng.uniform(0, 1, n) < np.clip(F['yard'][iv_, iu_] * 1.3, 0, 1)
                us, vs = us[keep], vs[keep]
            elif kind in ('grass', 'grass2', 'light', 'blade', 'stalk', 'leaf', 'aster', 'lace', 'clover'):
                # Nothing grows on the yard but a few tufts; the bramble leaves and asters come in drifts.
                keep = rng.uniform(0, 1, n) > F['yard'][iv_, iu_] * 0.92
                if kind in ('leaf', 'aster'):
                    X_, Z_ = world_of(us, vs)
                    drift = fbm(X_ / 1.6, Z_ / 1.6, seed=81 if kind == 'leaf' else 83, octaves=2)
                    keep &= rng.uniform(0, 1, n) < smoothstep(-0.1, 0.35, drift)
                us, vs = us[keep], vs[keep]
            jobs += [(kind, u, v, w_, h_) for u, v in zip(us, vs)]
        jobs.sort(key=lambda j: ORDER[j[0]])
        for kind, u, v, w_, h_ in jobs:
            w_ *= rng.uniform(0.7, 1.3); h_ *= rng.uniform(0.7, 1.3)
            cx, cz = world_of(u, v); cx = float(cx); cz = float(cz)
            rx, rz = cx - C[0], cz - C[1]
            rr = math.hypot(rx, rz) + 1e-9
            dl = math.hypot(rr, EYE_H); khs = dl; kvs = dl * dl / EYE_H
            rhat = np.array([rx / rr, rz / rr]); that = np.array([-rhat[1], rhat[0]])
            iv, iu = min(int(v), NV - 1), int(u) % NU
            g = F['grass'][iv, iu]
            blob = False
            # Right under the walk a flick seen from above is no taller than it is wide.
            h_ = min(h_, w_ * (1.0 + 4.0 * float(smoothstep(0.4, 2.6, rr))))
            if kind == 'dab':
                th = rng.normal(0, 0.35)
                la, lb = w_ / 2, h_ / 2
                pick = pal['near'] if dl < 6 else (pal['mid'] if dl < 25 else pal['far'])
                col = pick.near(g, k=4, spread=0.55); op = rng.uniform(0.55, 0.85)
            elif kind in ('grass', 'grass2', 'light'):
                th = math.pi / 2 + rng.normal(0, 0.22)
                la, lb = h_ / 2, w_ / 2
                if kind == 'light':
                    col = np.clip(pal['bright'].near(g, k=3, spread=0.8) * rng.uniform(1.0, 1.12), 0, 1); op = rng.uniform(0.35, 0.6)
                else:
                    pick = pal['near'] if rng.uniform() < float(smoothstep(12, 3, dl)) else (pal['mid'] if rng.uniform() < float(smoothstep(40, 12, dl)) else pal['far'])
                    if rng.uniform() < 0.14: pick = pal['dark']
                    col = pick.near(g, k=3, spread=0.65); op = rng.uniform(0.7, 0.95)
            elif kind == 'earth':
                # The yard's earth, laid flat in broad, worn dabs.
                th = rng.normal(0, 0.25)
                la, lb = w_ / 2, h_ / 2
                col = np.clip(pal['yard'].near(F['col'][iv, iu], k=4, spread=0.7) * rng.uniform(0.9, 1.08), 0, 1)
                op = rng.uniform(0.6, 0.9); blob = rng.uniform() < 0.5
            elif kind == 'blade':
                th = math.pi / 2 + rng.normal(0, 0.28)
                la, lb = h_ / 2, w_ / 2
                col = pal['dark'].any() * rng.uniform(0.7, 0.95) if rng.uniform() < 0.7 else np.clip(pal['bright'].any() * 1.05, 0, 1)
                op = rng.uniform(0.75, 0.95)
            elif kind == 'stalk':
                # The tall weed stalks of the foreground, thin and red-brown, leaning every which way.
                th = math.pi / 2 + rng.normal(0, 0.35)
                la, lb = h_ / 2, w_ / 2
                col = np.clip(pal['stalk'].any() * rng.uniform(0.8, 1.05), 0, 1); op = rng.uniform(0.8, 0.95)
            elif kind == 'leaf':
                # Bramble leaves going red, and here and there a green one.
                th = rng.uniform(0, math.pi)
                la, lb = w_ / 2, h_ / 2
                col = pal['leaf'].any() if rng.uniform() < 0.75 else pal['green'].any()
                col = np.clip(col * rng.uniform(0.9, 1.15), 0, 1); op = rng.uniform(0.75, 0.95)
                blob = True
            elif kind == 'clover':
                th = rng.uniform(0, math.pi)
                la, lb = w_ / 2, h_ / 2
                col = np.clip(pal['dark'].any() * 0.75 + np.array([0.0, 0.03, 0.0]), 0, 1); op = rng.uniform(0.8, 0.95)
                blob = True
            elif kind == 'aster':
                # A head of small pale blue-violet asters.
                th = rng.normal(0, 0.3)
                la, lb = w_ / 2, h_ / 2
                col = np.clip(np.array([0.62, 0.64, 0.78]) * rng.uniform(0.85, 1.08) + rng.normal(0, 0.03, 3), 0, 1); op = rng.uniform(0.8, 0.95)
                blob = True
            else:  # lace
                th = rng.normal(0, 0.15)
                la, lb = w_ / 2, h_ / 2
                col = np.clip(np.array([0.86, 0.86, 0.78]) * rng.uniform(0.9, 1.04), 0, 1); op = rng.uniform(0.8, 0.95)
                blob = True
            ct, st = math.cos(th), math.sin(th)
            reach_w = []
            for sa in (-1, 1):
                for sb in (-1, 1):
                    ah = sa * la * ct - sb * lb * st; av = sa * la * st + sb * lb * ct
                    reach_w.append((ah * khs * that[0] + av * kvs * rhat[0], ah * khs * that[1] + av * kvs * rhat[1]))
            reach = max(math.hypot(*q) for q in reach_w)
            if kind == 'clover': reach *= 2.2
            if rr < reach * 1.2:
                rows = np.arange(0, min(NV, int(v_of_r(rr + reach)) + 2)); cols = cols_all
            else:
                pu, pv = [], []
                for qx, qz in reach_w + [(0.0, 0.0)] + ([(q[0] * 2.2, q[1] * 2.2) for q in reach_w] if kind == 'clover' else []):
                    qu, qv = uv_of(cx + qx, cz + qz); pu.append(float(qu)); pv.append(float(qv))
                du = [((p_ - u + NU / 2) % NU) - NU / 2 for p_ in pu]
                u0 = int(math.floor(u + min(du))) - 1; u1 = int(math.ceil(u + max(du))) + 1
                vv0 = max(int(math.floor(min(pv))) - 1, 0); vv1 = min(int(math.ceil(max(pv))) + 1, NV - 1)
                if vv1 < vv0: continue
                rows = np.arange(vv0, vv1 + 1); cols = np.arange(u0, u1 + 1) % NU
            Xs = X[np.ix_(rows, cols)] - cx; Zs = Z[np.ix_(rows, cols)] - cz
            ah = (Xs * that[0] + Zs * that[1]) / khs; av = (Xs * rhat[0] + Zs * rhat[1]) / kvs
            s = (ah * ct + av * st) / la; t = (-ah * st + av * ct) / lb
            if kind in ('aster', 'lace'):
                # A loose head of little flowers: a few dabs round a centre, the lit ones on top.
                for k in range(5 if kind == 'aster' else 7):
                    ox, oy = rng.normal(0, 0.45), rng.normal(0, 0.35)
                    brush(canvas, rows, cols, (s - ox) / 0.42, (t - oy) / 0.5, col * rng.uniform(0.85, 1.05), op, rng, blob=True)
                if kind == 'aster':
                    brush(canvas, rows, cols, s / 0.2, t / 0.25, np.array([0.85, 0.7, 0.3]), 0.7, rng, blob=True)
            elif kind == 'clover':
                # Three round leaflets about a point.
                for k in range(3):
                    a = th + k * 2 * math.pi / 3
                    brush(canvas, rows, cols, (s - 1.1 * math.cos(a)), (t - 1.1 * math.sin(a)) / 1.1, col * rng.uniform(0.9, 1.1), op, rng, blob=True)
            else:
                brush(canvas, rows, cols, s, t, col, op, rng, blob=blob)
            count += 1
        if v0 % 256 == 0:
            print(f'  rows {v0}: r {rm:.2f} m, {len(jobs)} strokes, {time.time() - t0:.0f}s', flush=True)
    print(' painted', count, 'strokes', time.time() - t0)


# The painting's grey stones lying in the grass: x, z, half-width and half-depth in metres.
STONES = [(0.55, -3.8, 0.21, 0.14), (-2.65, -6.2, 0.1, 0.07)]


def stones(canvas, X, Z, rng):
    """The stones painted into the meadow where they lie, in the painting's greys: a rounded mass lit on its upper
    left, its shadow on the grass low and to the right, a few strokes across it."""
    p = painting()
    ys, xs = np.mgrid[0:PH, 0:PW]
    R, G, B = p[..., 0], p[..., 1], p[..., 2]
    grey = p[(ys > 693) & (ys < 745) & (xs > 733) & (xs < 873) & (np.abs(R - B) < 0.12) & (np.abs(G - B) < 0.12) & (p.mean(-1) > 0.25)]
    light, dark = np.percentile(grey, 75, axis=0) * 1.05, np.percentile(grey, 15, axis=0) * 0.7
    print(' stone greys', (light * 255).round(), (dark * 255).round())
    for sx, sz, a, b in STONES:
        u0, v0 = uv_of(sx, sz)
        r = math.hypot(sx - C[0], sz - C[1])
        dv = int((a + 0.2) * NV / (r * LOGR)) + 4; du = int((a + 0.2) / (2 * math.pi * r) * NU) + 4
        rows = np.arange(max(int(v0) - dv, 0), min(int(v0) + dv, NV)); cols = np.arange(int(u0) - du, int(u0) + du) % NU
        dx = (X[np.ix_(rows, cols)] - sx) / a; dz = (Z[np.ix_(rows, cols)] - sz) / b
        rr = np.hypot(dx, dz) + 0.12 * fbm(dx * 2, dz * 2, seed=91, octaves=2)
        # its shadow, then the stone
        sh = np.hypot((dx - 0.35), (dz - 0.25)) < 1.15
        reg = canvas[np.ix_(rows, cols)]
        reg[sh] = reg[sh] * 0.75
        inside = smoothstep(1.0, 0.85, rr)[..., None]
        lt = np.clip(0.55 - 0.45 * (0.6 * dx + 0.8 * dz), 0, 1)[..., None]
        col = dark * (1 - lt) + light * lt
        # lichen and moss: mottled greens and browns over the grey, and grass grown up round its foot
        mott = fbm(dx * 5, dz * 5, seed=93, octaves=3)[..., None]
        col = col * (1 + 0.12 * mott) + np.array([-0.03, 0.01, -0.04]) * np.clip(mott, 0, 1)
        inside = inside * (1 - 0.6 * smoothstep(0.7, 1.0, rr)[..., None] * (0.5 + 0.5 * fbm(dx * 9, dz * 9, seed=95, octaves=2))[..., None])
        reg[:] = reg * (1 - inside) + col * inside
        canvas[np.ix_(rows, cols)] = reg


def mesh():
    NAZ, NR = 200, 120
    ks = np.arange(NR + 1); js = np.arange(NAZ + 1)
    r = R0 * np.exp(ks / NR * LOGR)
    a = -math.pi + 2 * math.pi * js / NAZ
    rr, aa = np.meshgrid(r, a, indexing='ij')
    x = C[0] + rr * np.sin(aa); z = C[1] - rr * np.cos(aa)
    y = height(x, z)
    pos = np.stack([x, y, z], -1).reshape(-1, 3)
    uv = np.stack([np.broadcast_to(NU * js / NAZ, rr.shape), v_of_r(rr)], -1).reshape(-1, 2)
    centre = len(pos)
    pos = np.vstack([pos, np.repeat([[C[0], 0.0, C[1]]], NAZ, 0)])
    uv = np.vstack([uv, np.stack([NU * (np.arange(NAZ) + 0.5) / NAZ, np.zeros(NAZ)], -1)])
    idx = []
    vid = lambda k, j: k * (NAZ + 1) + j
    for k in range(NR - 1, -1, -1):
        for j in range(NAZ):
            a0, a1, b0, b1 = vid(k, j), vid(k, j + 1), vid(k + 1, j), vid(k + 1, j + 1)
            idx += [a0, b1, b0, a0, a1, b1]
    for j in range(NAZ):
        idx += [centre + j, vid(0, j + 1), vid(0, j)]
    return pos.astype(np.float32), uv.astype(np.float32), np.array(idx, np.int32).reshape(-1, 3)


def far_colour():
    """The meadow's colour by the ring all round, for the strip of field the woods stand on."""
    g = np.asarray(Image.open(f'{WORK}/out_ground.png').convert('RGB')).astype(np.float32) / 255
    v0 = int(v_of_r(34)); v1 = int(v_of_r(40))
    return blur(g[v0:v1].mean(0)[None], 20, wrap_x=True)[0]


if __name__ == '__main__':
    rng = np.random.default_rng(7)
    t0 = time.time()
    X, Z = texel_grid()
    f = painting_fields()
    pal = palettes(f, rng)
    F = build_fields(X, Z, f, pal)
    canvas = np.ascontiguousarray(F['col'] * (1 + 0.04 * fbm(X / 0.5, Z / 0.5, seed=31, octaves=3))[..., None]).astype(np.float32)
    Image.fromarray((np.clip(canvas, 0, 1) * 255).astype(np.uint8)).resize((1536, 1024)).save(f'{WORK}/ground_fill.png')
    if len(sys.argv) > 1 and sys.argv[1] == 'fill':
        sys.exit()
    # Close to, under the painting's own foreground, the grass's colour rather than the painting's flowers and
    # stalks blurred flat, so when the painting gives way, the meadow's own paint takes over cleanly.
    dE = np.hypot(X - E0[0], Z - E0[2])
    k = (smoothstep(6.5, 4.0, dE) * F['near'])[..., None]
    canvas = np.ascontiguousarray(canvas * (1 - k) + F['grass'] * (1 + 0.04 * fbm(X / 0.5, Z / 0.5, seed=31, octaves=3))[..., None] * k).astype(np.float32)
    paint(canvas, X, Z, F, pal, rng)
    stones(canvas, X, Z, rng)
    # The texture's very middle can't hold detail; its innermost rings go to one colour so it never shows.
    v1 = int(v_of_r(0.06)) + 1
    k = smoothstep(v_of_r(0.06), v_of_r(0.025), np.arange(v1) + 0.5)[:, None, None]
    canvas[:v1] = canvas[:v1] * (1 - k) + canvas[:v1].mean((0, 1))[None, None] * k
    Image.fromarray((np.clip(canvas, 0, 1) * 255).astype(np.uint8)).save(f'{WORK}/out_ground.png')
    pos, uv, idx = mesh()
    np.savez(f'{WORK}/mesh_ground.npz', pos=pos, uv=uv, idx=idx)
    print('done', time.time() - t0)
