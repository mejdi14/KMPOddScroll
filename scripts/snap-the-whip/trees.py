"""Trees dabbed in the painting's own tree colours: a crown of a few lobes, its shade low and to the right,
its light up and to the left, ragged at the edge the way the brush leaves it."""
import math
import numpy as np
from common import brush, smoothstep, fbm


class TreePalette:
    def __init__(self, pixels, rng):
        p = np.asarray(pixels, np.float32).reshape(-1, 3)
        lum = p @ np.array([0.3, 0.55, 0.15], np.float32)
        q = np.percentile(lum, [20, 45, 70, 90])
        self.dark = p[lum < q[0]]; self.mid = p[(lum >= q[0]) & (lum < q[2])]
        self.light = p[(lum >= q[2]) & (lum < q[3])]; self.top = p[lum >= q[3]]
        self.rng = rng

    def pick(self, light):
        """light 0 deep shade .. 1 lit top."""
        r = self.rng.uniform()
        if light < 0.25: s = self.dark if r < 0.8 else self.mid
        elif light < 0.55: s = self.mid if r < 0.7 else (self.dark if r < 0.85 else self.light)
        elif light < 0.8: s = self.light if r < 0.6 else self.mid
        else: s = self.top if r < 0.6 else self.light
        return s[self.rng.integers(len(s))]


def dab(canvas, cx, cy, la, lb, th, col, op, rng, bristles=None):
    h, w = canvas.shape[:2]
    reach = max(la, lb) + 2
    y0 = max(int(cy - reach), 0); y1 = min(int(cy + reach) + 1, h - 1)
    if y1 < y0: return
    xs = np.arange(int(cx - reach), int(cx + reach) + 1)
    rows = np.arange(y0, y1 + 1); cols = xs % w
    yy, xx = np.meshgrid(rows + 0.5, xs + 0.5, indexing='ij')
    dx, dy = xx - cx, yy - cy
    c, s_ = math.cos(th), math.sin(th)
    s = (dx * c + dy * s_) / la; t = (-dx * s_ + dy * c) / lb
    brush(canvas, rows, cols, s, t, col, op, rng, bristles=bristles, alpha=True)


def tree(canvas, cx, base, width, height, pal, rng, kind='round', dab_px=3.0):
    """A tree standing on row `base` round column cx, `width` and `height` in pixels."""
    if kind == 'poplar':
        lobes = [(0.0, 0.5, 0.42, 0.52)]
        lobes += [(rng.uniform(-0.12, 0.12), rng.uniform(0.25, 0.8), 0.3, rng.uniform(0.2, 0.32)) for _ in range(3)]
    elif kind == 'bush':
        lobes = [(rng.uniform(-0.3, 0.3), rng.uniform(0.3, 0.55), rng.uniform(0.25, 0.45), rng.uniform(0.35, 0.5)) for _ in range(rng.integers(2, 4))]
    else:
        n = rng.integers(3, 7)
        lobes = [(0.0, 0.55, rng.uniform(0.32, 0.45), rng.uniform(0.3, 0.42))]
        for _ in range(n):
            lobes.append((rng.uniform(-0.32, 0.32), rng.uniform(0.35, 0.78), rng.uniform(0.18, 0.3), rng.uniform(0.16, 0.28)))
    def inside(px, py):
        """How far inside the crown a point is, >0 inside, by its lobes; px, py in crown units, y up from base."""
        best = -9
        for lx, ly, rx, ry in lobes:
            d = 1 - math.hypot((px - lx) / rx, (py - ly) / ry)
            best = max(best, d)
        return best
    # A trunk first, if any shows.
    if kind == 'round' and rng.uniform() < 0.6:
        tc = pal.dark[rng.integers(len(pal.dark))] * 0.85
        dab(canvas, cx + rng.uniform(-0.05, 0.05) * width, base - 0.12 * height, 0.14 * height, max(0.8, 0.025 * width), math.pi / 2, tc, 0.85, rng, bristles=3)
    n = int(width * height / (dab_px * dab_px) * 2.6) + 12
    tries = 0; laid = 0
    while laid < n and tries < n * 6:
        tries += 1
        px = rng.uniform(-0.6, 0.6); py = rng.uniform(0.0, 1.05)
        d = inside(px, py)
        if d < -0.08: continue
        if d < 0 and rng.uniform() < 0.6: continue
        # Light from up and to the left; shade deepest low and to the right and inside the crown's mass.
        light = np.clip(0.3 + 0.7 * (py - 0.55) - 0.55 * px + 0.2 * (0.3 - d) + rng.normal(0, 0.12), 0, 1)
        if kind == 'poplar': light *= 0.75
        col = pal.pick(light)
        la = dab_px * rng.uniform(0.7, 1.4); lb = dab_px * rng.uniform(0.45, 0.8)
        th = rng.uniform(-0.9, 0.9) + (math.pi / 2 if kind == 'poplar' and rng.uniform() < 0.6 else 0)
        dab(canvas, cx + px * width, base - py * height, la, lb, th, col, rng.uniform(0.7, 0.98), rng, bristles=rng.integers(3, 6))
        laid += 1


def crown(canvas, cx, base, width, height, pal, rng, dab_px=5.0, haze=None, haze_k=0.0, trunk=True):
    """A broadleaf tree of the woods, as the painting's behind the schoolhouse: a crown of many rounded clusters,
    each lit on its upper left and in shade low on its right, the whole crown darker below and to the right,
    laid solid so no sky shows through but at its ragged edge, brushed over in dabs, a short trunk under it."""
    H, W = canvas.shape[:2]
    ccx, ccy = cx, base - 0.6 * height
    ex, ey = 0.47 * width, 0.4 * height
    ramp = [pal.dark.mean(0), pal.mid.mean(0), pal.light.mean(0), pal.top.mean(0)]
    def shade(light):
        l = np.clip(light, 0, 1) * 3
        i = np.clip(np.floor(l).astype(int), 0, 2)
        f = (l - i)[..., None]
        r = np.stack(ramp)
        return r[i] * (1 - f) + r[i + 1] * f
    if trunk:
        tw = max(1.5, 0.05 * width)
        bark = np.clip(pal.dark.mean(0) * 0.6, 0, 1)
        dab(canvas, cx + rng.uniform(-0.05, 0.05) * width, (base + ccy) / 2 + 0.1 * height, (base - ccy) / 2, tw, math.pi / 2, bark, 0.95, rng, bristles=3)
    clusters = [(ccx, ccy + 0.05 * ey, ex * 0.7, ey * 0.7)]
    for _ in range(rng.integers(9, 15)):
        a = rng.uniform(0, 2 * math.pi); r = math.sqrt(rng.uniform(0.2, 1.0))
        rx = ex * rng.uniform(0.24, 0.4); ry = ey * rng.uniform(0.26, 0.42)
        clusters.append((ccx + math.cos(a) * r * (ex - rx * 0.7), ccy + math.sin(a) * r * (ey - ry * 0.7), rx, ry))
    # shaded ones first, so the lit clusters up on the left sit over them
    clusters.sort(key=lambda c: (c[0] - ccx) / ex * 0.6 + (c[1] - ccy) / ey * 0.8, reverse=True)
    x0 = int(ccx - ex * 1.2) - 2; x1 = int(ccx + ex * 1.2) + 3
    y0 = max(int(ccy - ey * 1.2) - 2, 0); y1 = min(int(base - 0.12 * height) + 2, H)
    if y1 <= y0: return
    rows = np.arange(y0, y1); xs = np.arange(x0, x1); cols = xs % W
    Y, X = np.meshgrid(rows + 0.5, xs + 0.5, indexing='ij')
    seed = int(rng.integers(0, 10 ** 6))
    rag = 0.16 * fbm(X / max(dab_px * 1.6, 2), Y / max(dab_px * 1.6, 2), seed=seed % 997, octaves=3)
    best = np.full(X.shape, -9.0); light = np.zeros(X.shape)
    gx = (X - ccx) / ex; gy = (Y - ccy) / ey
    for cxi, cyi, rxi, ryi in clusters:
        dx = (X - cxi) / rxi; dy = (Y - cyi) / ryi
        d = 1 - np.hypot(dx, dy) + rag
        own = 0.5 - 0.42 * (0.6 * dx + 0.8 * dy) / np.maximum(np.hypot(dx, dy), 0.6)
        li = np.clip(0.55 * own + 0.45 * (0.55 - 0.35 * gx - 0.45 * gy), 0, 1)
        take = d > 0
        best = np.where(take, np.maximum(best, d), best)
        light = np.where(take, li, light)
    a = smoothstep(0.0, 0.07, best).astype(np.float32)
    col = shade(light + 0.06 * fbm(X / 3, Y / 3, seed=(seed + 5) % 997, octaves=2))
    if haze is not None and haze_k > 0:
        col = col * (1 - haze_k) + np.asarray(haze) * haze_k
    reg = canvas[np.ix_(rows, cols)]
    reg[..., :3] = reg[..., :3] * (1 - a[..., None]) + col * a[..., None]
    reg[..., 3] = np.maximum(reg[..., 3], a)
    canvas[np.ix_(rows, cols)] = reg
    # Dabs over it, in the colours its light asks for.
    inside = np.argwhere(a > 0.6)
    if len(inside) == 0: return
    n = int(len(inside) / (dab_px * dab_px) * 1.3) + 4
    pick = inside[rng.integers(0, len(inside), n)]
    for j, i in pick:
        lt = float(light[j, i]) + rng.normal(0, 0.1)
        c = pal.pick(np.clip(lt, 0, 1))
        if haze is not None and haze_k > 0:
            c = c * (1 - haze_k) + np.asarray(haze) * haze_k
        dab(canvas, xs[i] + 0.5, rows[j] + 0.5, dab_px * rng.uniform(0.8, 1.4), dab_px * rng.uniform(0.5, 0.8),
            rng.uniform(-0.9, 0.9), c, rng.uniform(0.6, 0.9), rng, bristles=rng.integers(3, 6))
