"""The pasture: one log-polar texture round the walk, painted stroke by stroke in the painting's own colours, and
its mesh. Olive grass in upright flicks, rust-red sorrel, long dark blades and the odd daisy close to, the field
going over to rust and olive towards the fence the way the painting's does."""
import math, time, sys
import numpy as np
from PIL import Image
from common import *

NU, NV = 3072, 2048
R0, RMAX = 0.02, 72.0
C = np.array([-0.22, -0.88])
LOGR = math.log(RMAX / R0)
EYE_H = 0.9

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
    """The painting's pasture as colour, blurred within the pasture alone, and the colour of its grass with the
    sorrel and flowers taken out."""
    p = painting(); reg = regions()
    ground = (reg['ground'] > 0.5).astype(np.float32)
    others = ((reg['fig'] > 0.05) | (reg['band'] > 0.5)).astype(np.float32)
    near_other = blur(others, 4) > 0.02
    mask = ground * (~near_other) * (blur(ground, 1.5) > 0.98)
    R, G, B = p[..., 0], p[..., 1], p[..., 2]
    rust = np.clip((R - G - 0.03) * 6, 0, 1) * mask
    light = np.clip((np.minimum(np.minimum(R, G), B) - 0.55) * 6, 0, 1) * mask
    def nconv(img, s):
        w = blur(mask, s)
        return blur(img * (mask[..., None] if img.ndim == 3 else mask), s) / np.maximum(w if img.ndim == 2 else w[..., None], 1e-4)
    col = nconv(p, 3.0)
    gmask = mask * (rust < 0.2) * (light < 0.2)
    wg = blur(gmask, 5)
    grass = blur(p * gmask[..., None], 5) / np.maximum(wg, 1e-4)[..., None]
    return dict(mask=mask, col=col, grass=grass, rust=nconv(rust, 8), rust_px=rust, light_px=light)


def palettes(f, rng):
    p = painting()
    m = f['mask'] > 0.5
    ys, xs = np.mgrid[0:PH, 0:PW]
    rust = (f['rust_px'] > 0.5) & m & (ys > 420)
    grass = (f['rust_px'] < 0.05) & (f['light_px'] < 0.05) & m
    far = m & (ys < 250)
    mid = m & (f['light_px'] < 0.05) & (ys >= 250) & (ys < 450)
    near = grass & (ys > 560)
    lum = p @ np.array([0.3, 0.55, 0.15], np.float32)
    dark = near & (lum < np.percentile(lum[near], 18))
    bright = (grass & (ys > 400)) & (lum > np.percentile(lum[grass & (ys > 400)], 85))
    # The rusty patches of the middle distance.
    midrust = m & (ys >= 230) & (ys < 450) & (f['rust_px'] > 0.3)
    return dict(
        near=Palette(p[near], rng), dark=Palette(p[dark], rng), mid=Palette(p[mid], rng), far=Palette(p[far], rng),
        rust=Palette(p[rust], rng), midrust=Palette(p[midrust], rng), bright=Palette(p[bright], rng),
        near_mean=p[near].mean(0), mid_mean=p[mid].mean(0), far_mean=p[far].mean(0), rust_mean=p[rust].mean(0),
        midrust_mean=p[midrust].mean(0),
    )


def build_fields(X, Z, f, pal):
    """Colour, grass colour and sorrel density over the whole pasture: the painting's where it sees it, carried
    on round it and then into a field of the same paint."""
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
    rust = np.where(inside, bilinear(f['rust'], px, py), 0).astype(np.float32)
    stack = np.concatenate([col, grass, rust[..., None]], -1)
    ext = pushpull(stack, w)
    print(' extended', time.time() - t0)
    near = np.clip(blur(w, 90, wrap_x=True) * 2.2, 0, 1)
    near = np.maximum(near, w)
    # The field everywhere else: green close to, olive further off, rust and olive towards the fence, as the
    # painting goes; with drifts of sorrel through it and greener hollows.
    r = np.hypot(X - E0[0], Z - E0[2]) * 0.5 + np.hypot(X - C[0], Z - C[1]) * 0.5
    t_mid = smoothstep(2.5, 9, r)[..., None]
    t_far = smoothstep(14, 45, r)[..., None]
    base = pal['near_mean'] * (1 - t_mid) + pal['mid_mean'] * t_mid
    base = base * (1 - t_far) + pal['far_mean'] * t_far
    drift = smoothstep(0.05, 0.4, fbm(X / 16, Z / 16, seed=21, octaves=4))
    patch_rust = (drift * (0.45 + 0.5 * smoothstep(4, 20, r)))[..., None]
    g_proc = base * (1 - 0.65 * patch_rust) + pal['midrust_mean'] * 0.65 * patch_rust
    # Towards the fence the whole field goes over to rust and olive in long swathes, as the painting's does.
    sw = (0.5 + 0.5 * fbm(X / 30, Z / 9, seed=57, octaves=3))[..., None] * smoothstep(12, 35, r)[..., None]
    g_proc = g_proc * (1 - 0.45 * sw) + pal['midrust_mean'] * 0.45 * sw
    greener = (0.5 + 0.5 * fbm(X / 11, Z / 11, seed=53, octaves=3))[..., None] * 0.3 * (1 - t_far)
    g_proc = g_proc * (1 - greener) + np.array([0.36, 0.42, 0.18], np.float32) * greener
    g_proc = g_proc * (1 + 0.07 * fbm(X / 6, Z / 6, seed=25, octaves=4))[..., None]
    # Distance warms and hazes it a little, as it does by the painting's fence.
    haze = smoothstep(30, 70, r)[..., None]
    g_proc = g_proc * (1 - 0.12 * haze) + np.array([0.62, 0.52, 0.36], np.float32) * 0.12 * haze
    rust_proc = (0.22 + 0.55 * drift) * (1 - 0.3 * smoothstep(25, 60, r))
    n = near[..., None]
    out = dict(
        col=(ext[..., 0:3] * n + g_proc * (1 - n)).astype(np.float32),
        grass=(ext[..., 3:6] * n + g_proc * (1 - n)).astype(np.float32),
        rust=(ext[..., 6] * 1.2 * near + rust_proc * (1 - near)).clip(0, 0.8).astype(np.float32),
        near=near.astype(np.float32), w=w,
    )
    print(' fields', time.time() - t0)
    return out


def angular(dist, deg, lo=0.3, hi=1.6):
    """A stroke's angular size at a distance: large up close, smaller further off, as the painting's are."""
    return math.radians(deg) * float(np.clip((4.0 / dist) ** 0.45, lo, hi))


# name, coverage, width and height in degrees at four metres, and how near it is laid (metres from the walk)
KINDS = (
    ('dab', 1.5, 0.55, 0.3, 999),
    ('grass', 1.5, 0.16, 0.5, 999),
    ('rust', 1.0, 0.7, 0.42, 999),
    ('grass2', 0.9, 0.1, 0.36, 999),
    ('blade', 0.05, 0.05, 2.2, 6),
    ('speck', 0.02, 0.17, 0.15, 9),
    ('daisy', 0.004, 0.5, 0.33, 9),
    ('clover', 0.012, 0.26, 0.2, 8),
    ('light', 0.2, 0.14, 0.36, 30),
)
ORDER = {'dab': 0, 'grass': 1, 'rust': 2, 'grass2': 3, 'clover': 4, 'light': 5, 'blade': 6, 'speck': 7, 'daisy': 8}


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
            if kind == 'rust':
                dens = F['rust'][np.clip(vs.astype(int), 0, NV - 1), us.astype(int) % NU]
                keep = rng.uniform(0, 1, n) < np.clip(dens * 1.5, 0, 0.9)
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
            elif kind == 'rust':
                th = rng.normal(0, 0.5)
                la, lb = w_ / 2, h_ / 2
                pk = pal['rust'] if dl < 12 else pal['midrust']
                col = np.clip(pk.near(np.array([0.5, 0.27, 0.12]), k=3, spread=0.8) * rng.uniform(0.9, 1.1), 0, 1); op = rng.uniform(0.6, 0.9)
                blob = rng.uniform() < 0.6
            elif kind == 'blade':
                th = math.pi / 2 + rng.normal(0, 0.28)
                la, lb = h_ / 2, w_ / 2
                col = pal['dark'].any() * rng.uniform(0.7, 0.95) if rng.uniform() < 0.7 else np.clip(pal['bright'].any() * 1.05, 0, 1)
                op = rng.uniform(0.75, 0.95)
            elif kind == 'speck':
                th = rng.uniform(0, math.pi)
                la, lb = w_ / 2, h_ / 2
                choice = rng.uniform()
                col = np.array([0.78, 0.33, 0.13]) if choice < 0.55 else (np.array([0.55, 0.35, 0.5]) if choice < 0.8 else np.array([0.82, 0.62, 0.66]))
                col = np.clip(col * rng.uniform(0.85, 1.1), 0, 1); op = rng.uniform(0.7, 0.95)
                blob = True
            elif kind == 'clover':
                th = rng.uniform(0, math.pi)
                la, lb = w_ / 2, h_ / 2
                col = np.clip(pal['dark'].any() * 0.75 + np.array([0.0, 0.03, 0.0]), 0, 1); op = rng.uniform(0.8, 0.95)
                blob = True
            else:  # daisy
                th = rng.normal(0, 0.15)
                la, lb = w_ / 2, h_ / 2
                col = np.clip(np.array([0.93, 0.92, 0.88]) * rng.uniform(0.92, 1.03), 0, 1); op = rng.uniform(0.85, 1.0)
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
            if kind == 'daisy':
                # Petals, then a yellow eye set a little up in them.
                brush(canvas, rows, cols, s, t, col * 0.8, 0.5, rng, blob=True)
                brush(canvas, rows, cols, s / 0.9, (t + 0.1) / 0.85, col, op, rng, blob=True)
                brush(canvas, rows, cols, s / 0.32, (t + 0.05) / 0.45, np.array([0.86, 0.66, 0.2]), 0.95, rng, blob=True)
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
    """The pasture's colour by the fence all round, for the strip of field the tree line stands on."""
    g = np.asarray(Image.open(f'{WORK}/out_ground.png').convert('RGB')).astype(np.float32) / 255
    v0 = int(v_of_r(60)); v1 = int(v_of_r(70))
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
    paint(canvas, X, Z, F, pal, rng)
    # The texture's very middle can't hold detail; its innermost rings go to one colour so it never shows.
    v1 = int(v_of_r(0.06)) + 1
    k = smoothstep(v_of_r(0.06), v_of_r(0.025), np.arange(v1) + 0.5)[:, None, None]
    canvas[:v1] = canvas[:v1] * (1 - k) + canvas[:v1].mean((0, 1))[None, None] * k
    Image.fromarray((np.clip(canvas, 0, 1) * 255).astype(np.uint8)).save(f'{WORK}/out_ground.png')
    pos, uv, idx = mesh()
    np.savez(f'{WORK}/mesh_ground.npz', pos=pos, uv=uv, idx=idx)
    print('done', time.time() - t0)
