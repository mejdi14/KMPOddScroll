"""The ground: one log-polar texture round the walk, painted stroke by stroke in the painting's own colours,
and its mesh."""
import math, time, sys
import numpy as np
from PIL import Image
from common import *
from landmarks import paint_ground

NU, NV = 3072, 2048
R0, RMAX = 0.12, 190.0
C = np.array([-0.85, -3.6])
LOGR = math.log(RMAX / R0)
EYE_H = 2.7

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
    """The painting's ground as colour, poppies and yellow flowers, each blurred within the ground alone."""
    p = painting(); ids = region_ids()
    ground = (ids == ID_GROUND).astype(np.float32)
    others = ((ids == ID_FIG) | (ids == ID_HILLFIG) | (ids == ID_HILLTREES) | (ids == ID_BAND)).astype(np.float32)
    near_other = blur(others, 4) > 0.02
    mask = ground * (~near_other) * (blur(ground, 1.5) > 0.98)
    R, G, B = p[..., 0], p[..., 1], p[..., 2]
    red = np.clip((R - np.maximum(G, B) - 0.12) * 4, 0, 1) * mask
    yellow = np.clip(((R + G) / 2 - B - 0.16) * 5, 0, 1) * np.clip((G - 0.5) * 6, 0, 1) * (1 - red) * mask
    def nconv(img, s):
        w = blur(mask, s)
        return blur(img * (mask[..., None] if img.ndim == 3 else mask), s) / np.maximum(w if img.ndim == 2 else w[..., None], 1e-4)
    col = nconv(p, 3.0)
    # The colour of the grass alone, poppies taken out, for strokes laid under the poppies.
    gmask = mask * (red < 0.15) * (yellow < 0.2)
    wg = blur(gmask, 5)
    grass = blur(p * gmask[..., None], 5) / np.maximum(wg, 1e-4)[..., None]
    return dict(mask=mask, col=col, grass=grass, red=nconv(red, 10), yellow=nconv(yellow, 10), gmask=gmask, red_px=red, yellow_px=yellow)


def palettes(f, rng):
    p = painting()
    m = f['mask'] > 0.5
    ys, xs = np.mgrid[0:PH, 0:PW]
    red = (f['red_px'] > 0.6) & m
    yel = (f['yellow_px'] > 0.6) & m
    grass = (f['red_px'] < 0.05) & (f['yellow_px'] < 0.05) & m
    hill = grass & (f['red'] > 0.12)
    meadow = grass & (f['red'] < 0.03) & (ys < 700)
    nearp = grass & (ys > 600) & (xs < 800)
    np_px = p[nearp]
    lum = np_px @ np.array([0.3, 0.55, 0.15], np.float32)
    return dict(
        near=Palette(np_px, rng), dark=Palette(np_px[lum < np.percentile(lum, 22)], rng),
        poppy=Palette(p[red], rng), yellow=Palette(p[yel], rng),
        hill=Palette(p[hill], rng), meadow=Palette(p[meadow], rng), grass=Palette(p[grass], rng),
        hill_mean=p[hill].mean(0), meadow_mean=p[meadow].mean(0), poppy_mean=p[red].mean(0),
    )


def build_fields(X, Z, f, pal):
    """Colour, grass colour, poppy and yellow density over the whole ground: the painting's where it sees the
    ground, carried on round it and then into a meadow of the same paint."""
    t0 = time.time()
    Y = height(X, Z).astype(np.float32)
    P = np.stack([X, Y, Z], -1).astype(np.float32)
    d = P - E0[None, None].astype(np.float32)
    dist = np.linalg.norm(d, axis=-1)
    px, py, zc = to_pixel(d / dist[..., None])
    inside = (zc > 0.05) & (px >= 0) & (px < PW) & (py >= 0) & (py < PH)
    dep = np.load(f'{WORK}/paint_depth.npy')
    pd = bilinear(np.where(np.isfinite(dep), dep, 1e4).astype(np.float32), px / 2, py / 2)
    visible = inside & (dist < pd * 1.03 + 0.4)
    w = np.where(visible, bilinear(f['mask'], px, py), 0).astype(np.float32)
    w = np.where(w > 0.5, 1.0, w * 0.5).astype(np.float32)
    print(' projected', time.time() - t0)
    col = np.where(visible[..., None], bilinear(f['col'], px, py), 0).astype(np.float32)
    grass = np.where(visible[..., None], bilinear(f['grass'], px, py), 0).astype(np.float32)
    red = np.where(visible, bilinear(f['red'], px, py), 0).astype(np.float32)
    yel = np.where(visible, bilinear(f['yellow'], px, py), 0).astype(np.float32)
    stack = np.concatenate([col, grass, red[..., None], yel[..., None]], -1)
    ext = pushpull(stack, w)
    print(' extended', time.time() - t0)
    # How near the painting's ground: within it 1, fading out over about fifteen degrees round it.
    near = np.clip(blur(w, 90, wrap_x=True) * 2.2, 0, 1)
    near = np.maximum(near, w)
    # A meadow of the same paint everywhere else: poppies on the hill and the swell and in drifts.
    tp = np.where(X >= ARC_FROM_X, RIDGE_Z - Z, np.hypot(X - ARC_C[0], Z - ARC_C[1]) - ARC_R)
    phi = np.degrees(np.arctan2(-(X - ARC_C[0]), ARC_C[1] - Z))
    hill = smoothstep(-26, -12, tp) * (1 - smoothstep(10, 22, tp)) * np.where(X >= ARC_FROM_X, 1 - smoothstep(0, 8, X), 1 - smoothstep(110, 150, phi))
    swell = np.exp(-((X - 34) ** 2 + (Z - 58) ** 2) / (2 * 24.0 ** 2))
    drift = smoothstep(0.2, 0.5, fbm(X / 34, Z / 34, seed=21, octaves=4))
    near_feet = np.exp(-((X - C[0]) ** 2 + (Z - C[1]) ** 2) / (2 * 6.0 ** 2))
    pop = np.clip(0.85 * hill + 0.7 * swell + 0.5 * drift + 0.45 * near_feet, 0, 1)
    pop = pop * (0.7 + 0.3 * fbm(X / 6, Z / 6, seed=23, octaves=3))
    zone = np.clip(pop * 1.4, 0, 1)
    pop = 0.34 * pop
    g_proc = pal['meadow_mean'][None, None] * (1 - zone[..., None]) + pal['hill_mean'][None, None] * zone[..., None]
    g_proc = g_proc * (1 + 0.07 * fbm(X / 9, Z / 9, seed=25, octaves=4))[..., None]
    # The meadow's own cooler patches, lavender and green, as on the painting's right.
    lav = (0.5 + 0.5 * fbm(X / 18, Z / 18, seed=51, octaves=3)) * (1 - zone) * 0.35
    grn = (0.5 + 0.5 * fbm(X / 13, Z / 13, seed=53, octaves=3)) * 0.22
    g_proc = g_proc * (1 - lav - grn)[..., None] + np.array([0.64, 0.61, 0.62], np.float32) * lav[..., None] + np.array([0.55, 0.58, 0.42], np.float32) * grn[..., None]
    # Distance greys and cools the field, as it does in the painting's far meadow.
    far = smoothstep(40, 170, np.hypot(X - C[0], Z - C[1]))[..., None]
    haze = np.array([0.72, 0.71, 0.66], np.float32)
    g_proc = g_proc * (1 - 0.15 * far) + haze * 0.15 * far
    yel_proc = 0.06 * zone * smoothstep(0.0, 0.5, fbm(X / 4, Z / 4, seed=27, octaves=3))
    c_proc = g_proc
    n = near[..., None]
    out = dict(
        col=(ext[..., 3:6] * n + c_proc * (1 - n)).astype(np.float32),
        grass=(ext[..., 3:6] * n + g_proc * (1 - n)).astype(np.float32),
        pop=(ext[..., 6] * 1.3 * near + pop * (1 - near)).clip(0, 0.6).astype(np.float32),
        yel=(ext[..., 7] * 1.3 * near + yel_proc * (1 - near)).clip(0, 0.3).astype(np.float32),
        near=near.astype(np.float32), w=w,
    )
    print(' fields', time.time() - t0)
    return out


def angular(dist, near_deg, far_deg):
    """A stroke's angular size at a distance: large up close, smaller further off, as the painting's are."""
    k = np.clip((8.0 / dist) ** 0.45, 0.35, 1.7)
    return math.radians(near_deg) * k


def paint(canvas, X, Z, F, pal, rng):
    """Strokes laid as the eye sees them from the walk: each one a size and a slant in the view, put down on the
    ground under it, so a grass flick stands up and a poppy lies flat wherever it is."""
    t0 = time.time()
    count = 0
    band = 16
    cols_all = np.arange(NU)
    kinds = (('grass', 3.0), ('grass2', 1.6), ('yellow', 1.0), ('white', 1.0), ('poppy', 1.0), ('light', 0.2))
    for v0 in range(0, NV, band):
        v1 = v0 + band
        rm = float(r_of_v((v0 + v1) / 2))
        dist = math.hypot(rm, EYE_H)
        kh = dist; kv = dist * dist / EYE_H          # metres of ground to a radian across and up the view
        circ = 2 * math.pi * rm / NU; rad = rm * LOGR / NV
        area_band = NU * band
        jobs = []
        for kind, cov in kinds:
            if kind == 'grass': w_, h_ = angular(dist, 0.2, 0), angular(dist, 0.8, 0)
            elif kind == 'grass2': w_, h_ = angular(dist, 0.16, 0), angular(dist, 0.55, 0)
            elif kind == 'poppy': w_ = angular(dist, 0.9, 0); h_ = w_ * 0.7
            elif kind == 'white': w_ = angular(dist, 0.22, 0); h_ = w_ * 0.7
            elif kind == 'yellow': w_ = angular(dist, 0.32, 0); h_ = w_ * 0.65
            else: w_, h_ = angular(dist, 0.2, 0), angular(dist, 0.32, 0)
            a_tex = math.pi * (w_ * kh / 2 / circ) * (h_ * kv / 2 / rad)
            n = int(cov * area_band / max(a_tex, 0.5))
            us = rng.uniform(0, NU, n); vs = rng.uniform(v0, v1, n)
            if kind in ('poppy', 'yellow', 'white'):
                field = F['pop'] if kind == 'poppy' else F['yel']
                dens = field[np.clip(vs.astype(int), 0, NV - 1), us.astype(int) % NU]
                if kind == 'poppy': dens = np.clip(dens * 1.6, 0, 0.75)
                if kind == 'white': dens = 0.03 * (1.2 - field[np.clip(vs.astype(int), 0, NV - 1), us.astype(int) % NU] * 0)
                keep = rng.uniform(0, 1, n) < dens
                us, vs = us[keep], vs[keep]
            jobs += [(kind, u, v, w_, h_) for u, v in zip(us, vs)]
        order = {'grass': 0, 'grass2': 1, 'yellow': 2, 'white': 2, 'poppy': 3, 'light': 4}
        jobs.sort(key=lambda j: order[j[0]])
        for kind, u, v, w_, h_ in jobs:
            w_ *= rng.uniform(0.7, 1.3); h_ *= rng.uniform(0.7, 1.3)
            cx, cz = world_of(u, v); cx = float(cx); cz = float(cz)
            rx, rz = cx - C[0], cz - C[1]
            rr = math.hypot(rx, rz) + 1e-9
            dl = math.hypot(rr, EYE_H); khs = dl; kvs = dl * dl / EYE_H
            rhat = np.array([rx / rr, rz / rr]); that = np.array([-rhat[1], rhat[0]])
            iv, iu = min(int(v), NV - 1), int(u) % NU
            if kind.startswith('grass') or kind == 'light':
                th = math.pi / 2 + rng.normal(0, 0.3)
                la, lb = h_ / 2, w_ / 2
                g = F['grass'][iv, iu]
                if kind == 'light':
                    col = np.clip(g * rng.uniform(1.12, 1.28) + 0.03, 0, 1); op = rng.uniform(0.35, 0.6)
                else:
                    pick = pal['hill'] if rng.uniform() < F['pop'][iv, iu] * 3 else pal['meadow']
                    if rng.uniform() < float(smoothstep(30, 8, dl)) * 0.8: pick = pal['near']
                    if rng.uniform() < 0.13: pick = pal['dark']
                    col = pick.near(g, k=2, spread=1.0); op = rng.uniform(0.7, 0.95)
            elif kind == 'poppy':
                th = rng.normal(0, 0.3)
                la, lb = w_ / 2, h_ / 2
                pk = pal['poppy'].p[rng.integers(0, len(pal['poppy'].p), 5)]
                col = np.clip(pk[np.argmax(pk[:, 0] - pk[:, 1])] * rng.uniform(0.95, 1.12), 0, 1); op = rng.uniform(0.85, 1.0)
            elif kind == 'yellow':
                th = rng.uniform(0, math.pi)
                la, lb = w_ / 2, h_ / 2
                col = pal['yellow'].p[rng.integers(len(pal['yellow'].p))]; op = rng.uniform(0.7, 0.95)
            else:
                th = rng.uniform(0, math.pi)
                la, lb = w_ / 2, h_ / 2
                col = np.clip(np.array([0.88, 0.86, 0.84]) * rng.uniform(0.92, 1.05) + rng.normal(0, 0.02, 3), 0, 1); op = rng.uniform(0.6, 0.85)
            ct, st = math.cos(th), math.sin(th)
            # Its corners on the ground, for the texels to look at.
            reach_w = []
            for sa in (-1, 1):
                for sb in (-1, 1):
                    ah = sa * la * ct - sb * lb * st; av = sa * la * st + sb * lb * ct
                    reach_w.append((ah * khs * that[0] + av * kvs * rhat[0], ah * khs * that[1] + av * kvs * rhat[1]))
            reach = max(math.hypot(*q) for q in reach_w)
            if rr < reach * 1.2:
                rows = np.arange(0, min(NV, int(v_of_r(rr + reach)) + 2)); cols = cols_all
            else:
                pu, pv = [], []
                for qx, qz in reach_w + [(0.0, 0.0)]:
                    qu, qv = uv_of(cx + qx, cz + qz); pu.append(float(qu)); pv.append(float(qv))
                du = [((p_ - u + NU / 2) % NU) - NU / 2 for p_ in pu]
                u0 = int(math.floor(u + min(du))) - 1; u1 = int(math.ceil(u + max(du))) + 1
                vv0 = max(int(math.floor(min(pv))) - 1, 0); vv1 = min(int(math.ceil(max(pv))) + 1, NV - 1)
                if vv1 < vv0: continue
                rows = np.arange(vv0, vv1 + 1); cols = np.arange(u0, u1 + 1) % NU
            Xs = X[np.ix_(rows, cols)] - cx; Zs = Z[np.ix_(rows, cols)] - cz
            ah = (Xs * that[0] + Zs * that[1]) / khs; av = (Xs * rhat[0] + Zs * rhat[1]) / kvs
            s = (ah * ct + av * st) / la; t = (-ah * st + av * ct) / lb
            if kind == 'poppy':
                # A darker underside first, then the flower, then light caught on its upper edge.
                dk = np.clip(col * np.array([0.72, 0.55, 0.55]), 0, 1)
                brush(canvas, rows, cols, s / 1.08, (t + 0.18) / 1.05, dk, 0.8, rng, blob=True)
                brush(canvas, rows, cols, s, t, col, op, rng, blob=True)
                if rng.uniform() < 0.6:
                    hl = np.clip(col * np.array([1.1, 1.3, 1.25]) + 0.06, 0, 1)
                    brush(canvas, rows, cols, (s + rng.uniform(-0.2, 0.2)) / 0.5, (t - 0.38) / 0.38, hl, 0.75, rng, blob=True)
            elif kind in ('yellow', 'white'):
                brush(canvas, rows, cols, s, t, col, op, rng, blob=True)
            else:
                brush(canvas, rows, cols, s, t, col, op, rng)
            count += 1
        if v0 % 256 == 0:
            print(f'  rows {v0}: r {rm:.2f} m, {len(jobs)} strokes, {time.time() - t0:.0f}s', flush=True)
    print(' painted', count, 'strokes', time.time() - t0)


def mesh():
    NAZ, NR = 200, 140
    ks = np.arange(NR + 1); js = np.arange(NAZ + 1)
    r = R0 * np.exp(ks / NR * LOGR)
    a = -math.pi + 2 * math.pi * js / NAZ
    rr, aa = np.meshgrid(r, a, indexing='ij')
    x = C[0] + rr * np.sin(aa); z = C[1] - rr * np.cos(aa)
    y = height(x, z)
    pos = np.stack([x, y, z], -1).reshape(-1, 3)
    uv = np.stack([np.broadcast_to(NU * js / NAZ, rr.shape), v_of_r(rr)], -1).reshape(-1, 2)
    # The middle under the walk, one fan.
    cpos = np.array([[C[0], float(height(np.array([C[0]]), np.array([C[1]]))[0]), C[1]]])
    pos = np.vstack([pos, cpos]); uv = np.vstack([uv, [[NU / 2, 0.0]]])
    centre = len(pos) - 1
    idx = []
    vid = lambda k, j: k * (NAZ + 1) + j
    for k in range(NR - 1, -1, -1):
        for j in range(NAZ):
            a0, a1, b0, b1 = vid(k, j), vid(k, j + 1), vid(k + 1, j), vid(k + 1, j + 1)
            idx += [a0, b1, b0, a0, a1, b1]
    for j in range(NAZ):
        idx += [centre, vid(0, j + 1), vid(0, j)]
    return pos.astype(np.float32), uv.astype(np.float32), np.array(idx, np.int32).reshape(-1, 3)


if __name__ == '__main__':
    rng = np.random.default_rng(7)
    t0 = time.time()
    X, Z = texel_grid()
    f = painting_fields()
    pal = palettes(f, rng)
    F = build_fields(X, Z, f, pal)
    np.save(f'{WORK}/ground_near.npy', F['near'])
    canvas = np.ascontiguousarray(F['col'] * (1 + 0.04 * fbm(X / 0.7, Z / 0.7, seed=31, octaves=3))[..., None]).astype(np.float32)
    Image.fromarray((np.clip(canvas, 0, 1) * 255).astype(np.uint8)).resize((1536, 1024)).save(f'{WORK}/ground_fill.png')
    if len(sys.argv) > 1 and sys.argv[1] == 'fill':
        sys.exit()
    paint(canvas, X, Z, F, pal, rng)
    paint_ground(canvas, X, Z, C)
    Image.fromarray((np.clip(canvas, 0, 1) * 255).astype(np.uint8)).save(f'{WORK}/out_ground.png')
    pos, uv, idx = mesh()
    np.savez(f'{WORK}/mesh_ground.npz', pos=pos, uv=uv, idx=idx)
    print('done', time.time() - t0)
