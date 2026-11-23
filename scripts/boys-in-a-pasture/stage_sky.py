"""The sky: one polar texture, the zenith in its middle and the horizon round it, the painting's sky carried on
into a summer sky of drifting cloud in the painting's own strokes and colours; and its dome."""
import math, time
import numpy as np
from PIL import Image
from common import *

NS = 2048
RH = 925.0
EL_MIN = -6.0


def g_of_el(el): rho = 1 - np.asarray(el, np.float64) / 90; return 0.5 * rho + 0.5 * rho * rho
def el_of_g(g): return 90 * (1 - (-0.5 + np.sqrt(0.25 + 2 * np.asarray(g, np.float64))))


def tex_of_dir(d):
    d = np.asarray(d, np.float64)
    el = np.degrees(np.arcsin(np.clip(d[..., 1], -1, 1)))
    az = np.arctan2(d[..., 0], -d[..., 2])
    r = RH * g_of_el(el)
    return NS / 2 + r * np.sin(az), NS / 2 - r * np.cos(az)


def dir_of_el_az(el, az):
    e = np.radians(el)
    return np.stack([np.cos(e) * np.sin(az), np.sin(e), -np.cos(e) * np.cos(az)], -1)


def texel_dirs():
    y, x = np.mgrid[0:NS, 0:NS].astype(np.float64) + 0.5
    dx, dy = x - NS / 2, y - NS / 2
    r = np.hypot(dx, dy)
    az = np.arctan2(dx, -dy)
    el = el_of_g(r / RH)
    return dir_of_el_az(el, az).astype(np.float32), el.astype(np.float32), az.astype(np.float32)


def sky_fields(D, el):
    p = painting(); reg = regions()
    sky = reg['sky']
    mask = sky * (blur((sky > 0.5).astype(np.float32), 4) > 0.99)
    R, G, B = p[..., 0], p[..., 1], p[..., 2]
    m = mask > 0.5
    # Under its old varnish the painting's blue is a teal; its clouds are the warm cream in it.
    blue = m & (B - R > -0.02)
    cloud = m & (R - B > 0.1)
    pal = Palette(p[m], np.random.default_rng(3))
    blue_px = p[blue]; cloud_px = p[cloud]
    ys = np.mgrid[0:PH, 0:PW][0]
    hb = ys[blue]
    def band_mean(lo, hi):
        sel = (hb >= lo) & (hb < hi)
        return blue_px[sel].mean(0) if sel.sum() > 50 else blue_px.mean(0)
    # The painting's sky deepens upward; carry that on up to the zenith a little further.
    blue_top = band_mean(0, 40)
    blue_low = band_mean(110, 175)
    blue_zen = np.clip(blue_top + (blue_top - blue_low) * 1.2, 0, 1)
    lum = cloud_px.mean(1)
    cloud_light = cloud_px[lum > np.percentile(lum, 75)].mean(0)
    cloud_mid = cloud_px[(lum > np.percentile(lum, 40)) & (lum < np.percentile(lum, 70))].mean(0)
    cloud_shade = cloud_px[lum < np.percentile(lum, 15)].mean(0)
    horizon = p[m & (ys > 165) & (ys < 186)].mean(0)
    print(' palette', (blue_zen * 255).round(), (blue_top * 255).round(), (blue_low * 255).round(), (cloud_light * 255).round(),
          (cloud_shade * 255).round(), (horizon * 255).round())
    # The painting projected onto the dome.
    px, py, zc = to_pixel(D.astype(np.float64))
    inside = (zc > 0.05) & (px >= 0) & (px < PW) & (py >= 0) & (py < PH)
    wts = blur(mask, 4)
    col = blur(p * mask[..., None], 4) / np.maximum(wts, 1e-4)[..., None]
    w = np.where(inside, bilinear(mask, px, py), 0).astype(np.float32)
    w = np.where(w > 0.5, 1.0, w * 0.4).astype(np.float32)
    gcol = np.where(inside[..., None], bilinear(col, px, py), 0).astype(np.float32)
    ext = pushpull(gcol, w)
    near = np.maximum(np.clip(blur(w, 60) * 2.2, 0, 1), w)
    # A summer sky carried on all round: fair-weather clouds on a deck overhead, smaller and flatter down to the
    # horizon, open blue between them.
    s = np.maximum(D[..., 1], math.sin(math.radians(6.0)))
    Px = D[..., 0] / s; Pz = D[..., 2] / s
    warp = 0.45 * fbm(Px * 1.3, Pz * 1.3, seed=41, octaves=3)
    dens = fbm(Px * 0.9 + warp, Pz * 0.9 - warp, seed=43, octaves=5)
    up = 0.12
    rr = np.hypot(Px, Pz) + 1e-6
    dens_up = fbm((Px - up * Px / rr) * 0.9 + warp, (Pz - up * Pz / rr) * 0.9 - warp, seed=43, octaves=5)
    # Clear low down, where the tree line stands against a haze, as in the painting.
    cover = smoothstep(-0.04, 0.2, dens + 0.08 * smoothstep(25, 0, el)) * smoothstep(2.0, 7.0, el)
    lit = np.clip(0.6 + 4.0 * (dens - dens_up), 0, 1)
    t = np.clip(el / 75, 0, 1)[..., None]
    bluec = np.where(t < 0.25, blue_low[None, None] * (1 - t / 0.25) + blue_top[None, None] * (t / 0.25),
                     blue_top[None, None] * (1 - (t - 0.25) / 0.75) + blue_zen[None, None] * ((t - 0.25) / 0.75))
    hz = smoothstep(10, 0, el)[..., None]
    bluec = bluec * (1 - 0.7 * hz) + horizon[None, None] * 0.7 * hz
    cloudc = cloud_shade[None, None] * (1 - lit[..., None]) + cloud_light[None, None] * lit[..., None]
    cloudc = cloudc * 0.75 + cloud_mid[None, None] * 0.25
    proc = bluec * (1 - cover[..., None]) + cloudc * cover[..., None]
    proc = proc * (1 - 0.4 * hz) + horizon[None, None] * 0.4 * hz
    below = smoothstep(0.5, -2, el)[..., None]
    proc = proc * (1 - below) + horizon[None, None] * below
    n = near[..., None]
    field = (ext * n + proc * (1 - n)).astype(np.float32)
    return field, pal, near


def paint(canvas, D, el, az, field, pal, rng):
    t0 = time.time()
    fb = blur(field, 3)
    passes = [  # (length deg, width deg, coverage, spread)
        (6.0, 2.2, 1.2, 0.5),
        (3.2, 1.1, 1.6, 0.7),
        (1.5, 0.5, 0.5, 0.9),
    ]
    total_sr = 2 * math.pi * (1 + math.sin(math.radians(-EL_MIN)))
    count = 0
    for L, W, cov, spread in passes:
        la0, lb0 = math.radians(L) / 2, math.radians(W) / 2
        n = int(cov * total_sr / (math.pi * la0 * lb0))
        se = rng.uniform(math.sin(math.radians(EL_MIN)), 1, n)
        els = np.degrees(np.arcsin(se)); azs = rng.uniform(-math.pi, math.pi, n)
        order = np.argsort(-np.abs(els - 30) + rng.uniform(0, 5, n))
        for i in order:
            e = float(els[i]); a_ = float(azs[i])
            k = (1 + 1.3 * (max(e, 0) / 90) ** 1.5) * (0.55 + 0.45 * smoothstep(0, 10, e))
            la = la0 * k * rng.uniform(0.75, 1.3) * (1 - 0.45 * float(smoothstep(40, 75, e))); lb = lb0 * k * rng.uniform(0.75, 1.3)
            c = dir_of_el_az(e, a_)
            eh = np.array([math.cos(a_), 0.0, math.sin(a_)])
            er = math.radians(e)
            ev = np.array([-math.sin(er) * math.sin(a_), math.cos(er), math.sin(er) * math.cos(a_)])
            th_h = rng.normal(0, 0.18)
            # Homer's clouds lean up to the right; overhead the strokes follow the wind.
            wv = np.array([0.8, 0.0, 0.6]); wv = wv - c * (wv @ c)
            th_w = math.atan2(wv @ ev, wv @ eh) + rng.normal(0, 0.18)
            bl = float(smoothstep(80, 88, e))
            dth = (th_w - th_h + math.pi / 2) % math.pi - math.pi / 2
            th = th_h + bl * dth + 0.12 * (1 - bl)
            ax = math.cos(th) * eh + math.sin(th) * ev
            bx = -math.sin(th) * eh + math.cos(th) * ev
            pts = []
            for sa in (-1, 0, 1):
                for sb in (-1, 0, 1):
                    q = c + sa * la * ax + sb * lb * bx
                    pts.append(q / np.linalg.norm(q))
            tx, ty = tex_of_dir(np.array(pts))
            x0 = max(int(tx.min()) - 1, 0); x1 = min(int(tx.max()) + 2, NS - 1)
            y0 = max(int(ty.min()) - 1, 0); y1 = min(int(ty.max()) + 2, NS - 1)
            if x1 <= x0 or y1 <= y0: continue
            rows = np.arange(y0, y1 + 1); cols = np.arange(x0, x1 + 1)
            Dd = D[y0:y1 + 1, x0:x1 + 1]
            dc = Dd @ c.astype(np.float32)
            q = Dd / np.maximum(dc, 1e-3)[..., None] - c[None, None].astype(np.float32)
            s = (q @ ax.astype(np.float32)) / la; t = (q @ bx.astype(np.float32)) / lb
            s = np.where(dc > 0.2, s, 9)
            cx, cy = tex_of_dir(c[None])
            want = fb[min(int(cy[0]), NS - 1), min(int(cx[0]), NS - 1)]
            col = pal.near(want, k=10, spread=spread)
            brush(canvas, rows, cols, s, t, col, rng.uniform(0.45, 0.7), rng, bristles=rng.integers(2, 5))
            count += 1
        print(f'  pass {L}°: {n} strokes, {time.time() - t0:.0f}s', flush=True)
    print(' painted', count)


def mesh():
    els = [EL_MIN, -3, 0, 1.5, 3, 4.5, 6, 8, 10, 12.5, 15, 18, 21, 24.5, 28, 32, 36, 40, 44, 48, 52, 56, 60, 64, 68, 72, 76, 80, 84, 87.5]
    NAZ = 160
    azs = -math.pi + 2 * math.pi * np.arange(NAZ + 1) / NAZ
    E, A = np.meshgrid(np.array(els), azs, indexing='ij')
    pos = dir_of_el_az(E, A).reshape(-1, 3)
    pos = np.vstack([pos, [[0, 1, 0]]])
    tx, ty = tex_of_dir(pos)
    uv = np.stack([tx, ty], -1)
    top = len(pos) - 1
    vid = lambda k, j: k * (NAZ + 1) + j
    idx = []
    for k in range(len(els) - 1):
        for j in range(NAZ):
            idx += [vid(k, j), vid(k + 1, j), vid(k + 1, j + 1), vid(k, j), vid(k + 1, j + 1), vid(k, j + 1)]
    k = len(els) - 1
    for j in range(NAZ):
        idx += [vid(k, j), top, vid(k, j + 1)]
    return pos.astype(np.float32), uv.astype(np.float32), np.array(idx, np.int32).reshape(-1, 3)


if __name__ == '__main__':
    rng = np.random.default_rng(11)
    t0 = time.time()
    D, el, az = texel_dirs()
    field, pal, near = sky_fields(D, el)
    print(' fields', time.time() - t0)
    canvas = np.ascontiguousarray(field.copy())
    Image.fromarray((np.clip(canvas, 0, 1) * 255).astype(np.uint8)).resize((1024, 1024)).save(f'{WORK}/sky_fill.png')
    paint(canvas, D, el, az, field, pal, rng)
    Image.fromarray((np.clip(canvas, 0, 1) * 255).astype(np.uint8)).save(f'{WORK}/out_sky.png')
    pos, uv, idx = mesh()
    np.savez(f'{WORK}/mesh_sky.npz', pos=pos, uv=uv, idx=idx)
    print('done', time.time() - t0)
