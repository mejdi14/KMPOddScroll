"""The far side of the field: a ring 70 m out all the way round, carrying the rail fence, the strip of field
beyond it, the hedges and the trees. The painting's own stretch where the painting shows it; everywhere else
the same fence carried on and trees dabbed in the painting's tree colours, in clumps and singly, the smaller
ones hazed back the way the painting's are on its right."""
import math, time
import numpy as np
from PIL import Image
from common import *
from trees import TreePalette, tree

PXD = 22.0                      # texture pixels to a degree round the ring
YTOP, YBOT = 8.6, -0.6
BH = 168                        # rows, top to bottom
HALF = int(round(180 * PXD))    # one half of the ring
FULL = 2 * HALF
GUTTER = 4


def v_of_y(y): return (YTOP - np.asarray(y, np.float64)) / (YTOP - YBOT) * BH
def y_of_v(v): return YTOP - np.asarray(v, np.float64) / BH * (YTOP - YBOT)
def u_of_az(az_deg): return (np.asarray(az_deg, np.float64) + 180.0) * PXD
def az_of_u(u): return np.asarray(u, np.float64) / PXD - 180.0
def px_per_m(): return BH / (YTOP - YBOT)


def painting_strip():
    """The painting laid on the ring where it sees it: colour, how much of it is the far side (not sky), and
    where it can be trusted (inside the painting, not behind the boys)."""
    p = painting(); reg = regions()
    v, u = np.mgrid[0:BH, 0:FULL].astype(np.float64) + 0.5
    az = np.radians(az_of_u(u)); y = y_of_v(v)
    P = np.stack([RB * np.sin(az), y, -RB * np.cos(az)], -1) - E0[None, None]
    px, py, zc = to_pixel(P / np.linalg.norm(P, axis=-1, keepdims=True))
    inside = (zc > 0.1) & (px >= 0.5) & (px < PW - 0.5) & (py >= 0) & (py < PH)
    col = np.where(inside[..., None], bilinear(p, px, py), 0).astype(np.float32)
    solid = np.where(inside, 1 - bilinear(reg['sky'], px, py), 0).astype(np.float32)
    fig = np.where(inside, bilinear(reg['fig'], px, py), 1).astype(np.float32)
    trust = (inside & (fig < 0.02)).astype(np.float32)
    return col, solid, trust, inside, px


def hedge_and_tree_pixels():
    p = painting(); reg = regions()
    ys = np.mgrid[0:PH, 0:PW][0]
    lum = p @ np.array([0.3, 0.55, 0.15], np.float32)
    core = (blur((reg['band'] > 0.9).astype(np.float32), 1.2) > 0.97) & (reg['fig'] < 0.01)
    trees = p[core & (ys < 186) & (lum < 0.55)]
    hedge = p[core & (ys >= 180) & (ys < 191) & (lum < 0.5)]
    # The strip of field between the fence and the hedges, and the fence's weathered rails and posts.
    strip = p[core & (ys >= 197) & (ys < 205) & (lum < 0.45)]
    fence = p[core & (ys >= 192) & (ys < 206) & (lum > np.percentile(lum[core & (ys >= 192) & (ys < 206)], 88))]
    return trees, hedge, strip, fence


def far_ground():
    """The pasture's colour just in front of the fence, round the ring, from the painted ground."""
    from stage_ground import NU, v_of_r
    g = np.asarray(Image.open(f'{WORK}/out_ground.png').convert('RGB')).astype(np.float32) / 255
    rows = g[int(v_of_r(55)):int(v_of_r(69))].mean(0)
    rows = blur(rows[None], 6, wrap_x=True)[0]
    # Ground texture columns run round the ground's own centre; near enough the same azimuth this far out.
    az = np.arange(FULL) / PXD - 180.0
    cols = ((np.radians(az) + math.pi) / (2 * math.pi) * NU).astype(int) % NU
    return rows[cols]


def build(rng):
    t0 = time.time()
    col, solid, trust, inside, px = painting_strip()
    tpx, hpx, spx, fpx = hedge_and_tree_pixels()
    print(' painting laid on the ring', time.time() - t0, len(tpx), len(hpx), len(spx), len(fpx))
    # Where the painting's own stretch goes: cut where its tree line is lowest near each edge.
    tops = (solid[:int(v_of_y(2.5))] > 0.5).sum(0) * inside.all(0)
    cols_in = np.where(inside.all(0))[0]
    lo, hi = cols_in.min(), cols_in.max()
    def gap_near(c, toward):
        win = np.arange(c, c + toward * 60, toward)
        return int(win[np.argmin(tops[win] + 0.01 * np.abs(win - c))])
    cut0, cut1 = gap_near(lo + 2, 1), gap_near(hi - 2, -1)
    ring = np.zeros((BH, FULL, 4), np.float32)
    rows = np.arange(BH)
    yv = y_of_v(rows + 0.5)
    # The strip of field from the fence back to the hedges: the pasture's own colour at its foot, going over to
    # the painting's greener strip by the hedges.
    fg = far_ground()
    strip = spx.mean(0)
    k = smoothstep(0.0, 0.8, yv)[:, None, None]
    base = fg[None] * (1 - k) + strip[None, None] * k
    base = base * (1 + 0.06 * fbm(np.arange(FULL)[None] / 9.0, rows[:, None] / 3.0, seed=61, octaves=3))[..., None]
    low = yv < 1.05
    ring[low, :, :3] = base[low]
    ring[low, :, 3] = 1
    # The hedges along the back of the strip, low and broken.
    hp = Palette(hpx, rng)
    hedge_mean = hpx.mean(0)
    for _ in range(int(FULL * 2.6)):
        cxp = rng.uniform(0, FULL)
        top = max(0.95, rng.uniform(0.9, 1.9) + 0.5 * float(fbm(np.array([cxp / 140.0]), np.array([0.0]), seed=63, octaves=2)[0]))
        cy = v_of_y(rng.uniform(0.75, top))
        la = rng.uniform(2.5, 6); lb = rng.uniform(1.4, 3.0)
        rr = np.arange(max(int(cy - lb - 2), 0), min(int(cy + lb + 3), BH))
        xs = np.arange(int(cxp - la - 2), int(cxp + la + 3))
        yy, xx = np.meshgrid(rr + 0.5, xs + 0.5, indexing='ij')
        brush(ring, rr, xs % FULL, (xx - cxp) / la, (yy - cy) / lb, hp.near(hedge_mean, k=4, spread=1), rng.uniform(0.6, 0.95), rng, alpha=True)
    print(' hedges', time.time() - t0)
    # Trees all round, in the painting's colours, standing on the hedge line.
    tpal = TreePalette(tpx, rng)
    haze = np.array([0.55, 0.6, 0.52], np.float32)
    hz_pal = TreePalette(np.clip(tpx * 0.72 + haze * 0.28, 0, 1), rng)
    ground_v = v_of_y(0.84)
    u = float(cut1 + 10)
    end = cut0 + FULL - 10
    while u < end - 20:
        r = rng.uniform()
        if r < 0.1:
            hgt = rng.uniform(80, 115); kind = 'round'; wid = hgt * rng.uniform(0.9, 1.2)
        elif r < 0.3:
            hgt = rng.uniform(14, 30); kind = 'bush'; wid = hgt * rng.uniform(1.4, 2.6)
        elif r < 0.36:
            hgt = rng.uniform(55, 80); kind = 'poplar'; wid = hgt * rng.uniform(0.22, 0.3)
        else:
            hgt = rng.uniform(30, 72); kind = 'round'; wid = hgt * rng.uniform(0.9, 1.4)
        n = rng.integers(2, 5) if kind == 'round' and (rng.uniform() < 0.35 or hgt > 80) else 1
        pal = hz_pal if hgt < 45 and rng.uniform() < 0.6 else tpal
        span = 0.0
        for j in range(n):
            h_k = hgt * (rng.uniform(0.7, 1.0) if j != n // 2 else 1.0) if n > 1 else hgt
            w_k = h_k * rng.uniform(0.9, 1.35) if n > 1 else wid
            x = u + span + w_k / 2
            if x + w_k / 2 > end: break
            tree(ring, x, ground_v + rng.uniform(0, 3), w_k, h_k, pal, rng, kind=kind, dab_px=3.4)
            span += w_k * (rng.uniform(0.5, 0.8) if n > 1 else 1.0)
        span = max(span, 10.0)
        gap = rng.choice([rng.uniform(-0.2, 0.0), rng.uniform(0.05, 0.6), rng.uniform(0.8, 2.0)], p=[0.35, 0.45, 0.2])
        u += span * (1 + gap)
    print(' trees', time.time() - t0)
    fence(ring, fpx, rng)
    print(' fence', time.time() - t0)
    # The painting's own stretch over it, cut where its tree line is lowest near each edge, the boys' hats
    # filled from round them, and feathered into what goes on round it.
    # Behind the older boy's hat the painting has nothing to give; what goes on round it shows there instead:
    # the strip of field, the hedge and the fence, no tree.
    f = np.clip(np.minimum(np.arange(FULL) - cut0, cut1 - np.arange(FULL)) / 30.0, 0, 1)[None, :, None]
    f = f * np.clip(blur(trust, 1.5) * 1.02, 0, 1)[..., None] ** 2
    solid_f = np.concatenate([col, solid[..., None]], -1)
    pa = np.clip(solid_f[..., 3:4], 0, 1); ga = ring[..., 3:4]
    out_a = ga * (1 - f) + pa * f
    ring[..., :3] = (ring[..., :3] * ga * (1 - f) + solid_f[..., :3] * pa * f) / np.maximum(out_a, 1e-4)
    ring[..., 3:4] = out_a
    print(' composed', time.time() - t0, 'painting from', round(float(az_of_u(cut0)), 1), 'to', round(float(az_of_u(cut1)), 1))
    return ring


def fence(ring, fpx, rng):
    """A post-and-rail fence along the foot of the ring, posts about three and a half metres apart, two rails
    sagging a little between them, weathered grey with the light on their tops."""
    m = px_per_m()
    pal = Palette(fpx, rng)
    light = np.percentile(fpx, 70, axis=0)
    dark = np.clip(fpx.mean(0) * 0.62, 0, 1)
    step = 3.4 / RB * 180 / math.pi * PXD
    posts = np.arange(0, FULL, step) + rng.normal(0, step * 0.06, int(math.ceil(FULL / step)))
    for k, x in enumerate(posts):
        top = 1.0 + rng.uniform(-0.12, 0.1)
        lean = rng.normal(0, 0.05)
        # the post: dark, lit down its left side
        yb, yt = v_of_y(-0.05), v_of_y(top)
        cy = (yb + yt) / 2; lb = max(0.9, 0.07 * m); la = (yb - yt) / 2
        rr = np.arange(int(yt) - 1, int(yb) + 2); xs = np.arange(int(x - 4), int(x + 5))
        yy, xx = np.meshgrid(rr + 0.5, xs + 0.5, indexing='ij')
        s = (yy - cy) / la; t = (xx - x - lean * (yy - cy)) / lb
        brush(ring, rr, xs % FULL, s, t, dark * rng.uniform(0.9, 1.1), 0.9, rng, bristles=2, alpha=True)
        brush(ring, rr, xs % FULL, s, (t + 0.5) / 0.5, pal.near(light, k=3), 0.6, rng, bristles=2, alpha=True)
        # the rails to the next post
        if k + 1 < len(posts) or True:
            x2 = posts[(k + 1) % len(posts)] + (FULL if k + 1 == len(posts) else 0)
            for h in (0.72, 0.32):
                if rng.uniform() < 0.06: continue          # a rail down here and there
                sag = rng.uniform(0.0, 0.06)
                h0 = h + rng.normal(0, 0.03); h1 = h + rng.normal(0, 0.03)
                xs = np.arange(int(x), int(x2) + 1)
                f = (xs + 0.5 - x) / max(x2 - x, 1)
                yline = v_of_y(h0 * (1 - f) + h1 * f - sag * 4 * f * (1 - f))
                rr = np.arange(int(yline.min()) - 2, int(yline.max()) + 3)
                yy = rr[:, None] + 0.5 - yline[None, :]
                thick = 0.065 * m
                s = np.broadcast_to((f * 2 - 1)[None, :] * 0.92, yy.shape)
                brush(ring, rr, xs % FULL, s, yy / thick, dark * rng.uniform(0.95, 1.15), 0.85, rng, bristles=3, alpha=True)
                brush(ring, rr, xs % FULL, s, (yy + 0.55 * thick) / (0.45 * thick), pal.near(light, k=3), 0.7, rng, bristles=3, alpha=True)


def atlas(ring):
    """Two halves, ahead (-90..90) over behind (90..270), one row each."""
    a = np.zeros((2 * BH + GUTTER, HALF + 3, 4), np.float32)
    for k, az0 in enumerate((-90.0, 90.0)):
        cols = (np.arange(HALF + 3) + start(az0)) % FULL
        a[k * (BH + GUTTER):k * (BH + GUTTER) + BH] = ring[:, cols]
    return a


def start(az0): return int(math.floor(u_of_az(az0)))


def mesh():
    STEP = 2.0
    pos, uv, idx = [], [], []
    ys = [YBOT, 0.0, 0.84, 2.0, 4.5, YTOP]
    for half in (0, 1):
        az0 = -90.0 if half == 0 else 90.0
        base = len(pos)
        n = int(180 / STEP)
        for k in range(n + 1):
            az = az0 + k * STEP
            a = math.radians(az)
            for y in ys:
                pos.append((RB * math.sin(a), y, -RB * math.cos(a)))
                uv.append((float(u_of_az(az)) - start(az0), float(v_of_y(y)) + (BH + GUTTER if half else 0)))
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
    save_rgba_preview(a, f'{WORK}/band_preview.png')
    pos, uv, idx = mesh()
    np.savez(f'{WORK}/mesh_band.npz', pos=pos, uv=uv, idx=idx)
    print('done')
