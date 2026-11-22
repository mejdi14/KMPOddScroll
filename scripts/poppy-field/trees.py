"""Trees dabbed in the painting's own tree colours: a crown of a few lobes, its shade low and to the right,
its light up and to the left, ragged at the edge the way the brush leaves it."""
import math
import numpy as np
from common import brush, smoothstep


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
