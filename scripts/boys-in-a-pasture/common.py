"""Shared pieces for rebuilding Homer's Boys in a Pasture as a place: the entry camera, the painting and its
regions, the flat pasture, noise, blurs and the brush."""
import math
import os
import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(ROOT, '..', '..'))
RES = os.path.join(REPO, 'shared', 'src', 'commonMain', 'composeResources')
ART = os.path.join(ROOT, 'art')
# Everything the steps make, kept out of git.
WORK = os.environ.get('PASTURE_WORK', os.path.join(ROOT, 'work'))
os.makedirs(WORK, exist_ok=True)
NAME = 'museum_pasture'

# The painting as it hangs, and the scan it is cut from at its full size.
PW, PH = 1200, 800
FULL_SCALE = 1908 / PW

# The entry camera: where the painting was painted from, sitting in the grass. The horizon lies 191.6 px down
# the painting, and the rail fence along the far side of the field stands on the ring the tree line is drawn on.
EYE = 0.84
E0 = np.array([0.0, EYE, 0.0])
HFOV = 50.0
FOCAL = PW / 2 / math.tan(math.radians(HFOV) / 2)
HORIZON = 191.6
TILT = -math.degrees(math.atan((PH / 2 - HORIZON) / FOCAL))
_t = math.radians(TILT)
CF = np.array([0.0, math.sin(_t), -math.cos(_t)])
CR = np.array([1.0, 0.0, 0.0])
CU = np.array([0.0, math.cos(_t), math.sin(_t)])
# The walk in: forward and a little left, past the boys' shoulders, getting up off the grass as it goes.
ROUTE = np.array([[0.0, EYE, 0.0], [-0.14, 0.87, -0.6], [-0.3, 0.91, -1.2], [-0.45, 0.95, -1.75]])

# The ring the far side of the field is drawn on: the fence, the strip beyond it, the hedges and the trees.
RB = 70.0

# Region ids.
ID_SKY, ID_BAND, ID_GROUND, ID_FIG = 0, 1, 2, 3


def painting():
    return np.asarray(Image.open(f'{WORK}/painting.png').convert('RGB')).astype(np.float32) / 255


def painting_full():
    return np.asarray(Image.open(f'{ART}/boys-in-a-pasture.jpg').convert('RGB')).astype(np.float32) / 255


def regions():
    """Soft masks over the painting: sky, band (the far side of the field), ground and figures, summing to 1."""
    r = np.load(f'{WORK}/regions.npz')
    return {k: r[k] for k in r.files}


def pixel_ray(px, py):
    """Unit rays through painting pixel coordinates (texture pixel units, centres at +0.5)."""
    px = np.asarray(px, np.float64); py = np.asarray(py, np.float64)
    d = CF[None] + ((px.ravel() - PW / 2) / FOCAL)[:, None] * CR[None] + ((PH / 2 - py.ravel()) / FOCAL)[:, None] * CU[None]
    d /= np.linalg.norm(d, axis=1, keepdims=True)
    return d.reshape(px.shape + (3,))


def to_pixel(d):
    """Painting pixel coordinates of directions d (..., 3) from the entry eye; z ahead."""
    z = d @ CF
    zz = np.where(z > 1e-6, z, 1e-6)
    return PW / 2 + FOCAL * (d @ CR) / zz, PH / 2 - FOCAL * (d @ CU) / zz, z


# ---------------------------------------------------------------- the ground: a flat pasture

def height(x, z):
    return np.zeros(np.broadcast(np.asarray(x), np.asarray(z)).shape, np.float32)


def raycast(o, d, tmax=400.0):
    """Distance along unit rays d (N, 3) from o to the ground, or inf."""
    d = np.asarray(d, np.float64).reshape(-1, 3)
    down = d[:, 1] < -1e-9
    t = np.where(down, -o[1] / np.where(down, d[:, 1], -1), np.inf)
    return np.where(t < tmax, t, np.inf)


# ---------------------------------------------------------------- noise

def _hash(ix, iy, iz, seed):
    h = (ix.astype(np.int64) * 73856093) ^ (iy.astype(np.int64) * 19349663) ^ (iz.astype(np.int64) * 83492791) ^ (seed * 2654435761)
    h = h & 0xFFFFFFFF
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    h = h ^ (h >> 16)
    return (h & 0xFFFFFF).astype(np.float32) / float(0xFFFFFF)


def vnoise(x, y, z=None, seed=0):
    x = np.asarray(x, np.float64); y = np.asarray(y, np.float64)
    z = np.zeros_like(x) if z is None else np.asarray(z, np.float64)
    ix, iy, iz = np.floor(x), np.floor(y), np.floor(z)
    fx, fy, fz = x - ix, y - iy, z - iz
    fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy); fz = fz * fz * (3 - 2 * fz)
    ix = ix.astype(np.int64); iy = iy.astype(np.int64); iz = iz.astype(np.int64)
    out = 0
    for dx in (0, 1):
        wx = fx if dx else 1 - fx
        for dy in (0, 1):
            wy = fy if dy else 1 - fy
            for dz in (0, 1):
                wz = fz if dz else 1 - fz
                out = out + wx * wy * wz * _hash(ix + dx, iy + dy, iz + dz, seed)
    return (out * 2 - 1).astype(np.float32)


def fbm(x, y, z=None, octaves=5, seed=0, lac=2.03, gain=0.5):
    total = 0; amp = 1; norm = 0
    x = np.asarray(x, np.float64); y = np.asarray(y, np.float64)
    z = None if z is None else np.asarray(z, np.float64)
    for o in range(octaves):
        total = total + amp * vnoise(x, y, z, seed + 17 * o)
        norm += amp; amp *= gain
        x = x * lac + 3.1; y = y * lac + 1.7
        if z is not None: z = z * lac + 5.3
    return total / norm


def smoothstep(a, b, x):
    t = np.clip((np.asarray(x, np.float64) - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


# ---------------------------------------------------------------- blurs and fills

def blur(img, sigma, wrap_x=False):
    """Gaussian blur along both image axes (H, W[, C]); x wraps if asked, else edges reflect."""
    if sigma <= 0: return np.asarray(img, np.float32).copy()
    a = np.asarray(img, np.float32)
    pad = int(3 * sigma) + 1
    def along(arr, axis, wrap):
        n = arr.shape[axis]
        p = min(pad, n - 1) if not wrap else pad
        if wrap:
            arr2 = np.concatenate([np.take(arr, range(n - p, n), axis=axis), arr, np.take(arr, range(0, p), axis=axis)], axis=axis)
        else:
            idx = np.concatenate([np.arange(p, 0, -1), np.arange(n), np.arange(n - 2, n - 2 - p, -1)])
            idx = np.clip(idx, 0, n - 1)
            arr2 = np.take(arr, idx, axis=axis)
        m = arr2.shape[axis]
        k = np.fft.rfftfreq(m)
        g = np.exp(-2 * (math.pi * k * sigma) ** 2).astype(np.float32)
        shape = [1] * arr2.ndim; shape[axis] = len(g)
        out = np.fft.irfft(np.fft.rfft(arr2, axis=axis) * g.reshape(shape), n=m, axis=axis)
        return np.take(out, range(p, p + n), axis=axis).astype(np.float32)
    a = along(a, 0, False)
    a = along(a, 1, wrap_x)
    return a


def pushpull(img, w):
    """Fills where weight w (H, W) is low from the weighted colour round it."""
    img = np.asarray(img, np.float32); w = np.clip(np.asarray(w, np.float32), 0, 1)
    if img.ndim == 2: img = img[..., None]; squeeze = True
    else: squeeze = False
    levels = [(img * w[..., None], w)]
    while min(levels[-1][1].shape) > 1:
        ci, cw = levels[-1]
        h, wd = cw.shape
        h2, w2 = (h + 1) // 2, (wd + 1) // 2
        pi = np.zeros((h2 * 2, w2 * 2, ci.shape[2]), np.float32); pi[:h, :wd] = ci
        pw = np.zeros((h2 * 2, w2 * 2), np.float32); pw[:h, :wd] = cw
        si = pi.reshape(h2, 2, w2, 2, -1).sum((1, 3)); sw = pw.reshape(h2, 2, w2, 2).sum((1, 3))
        levels.append((si / np.maximum(sw, 1e-8)[..., None] * np.minimum(sw, 1)[..., None], np.minimum(sw, 1)))
    ci, cw = levels[-1]
    col = ci / np.maximum(cw, 1e-8)[..., None]
    for ci, cw in reversed(levels[:-1]):
        h, wd = cw.shape
        up = np.repeat(np.repeat(col, 2, 0), 2, 1)[:h, :wd]
        up = blur(up, 0.8)
        here = ci / np.maximum(cw, 1e-8)[..., None]
        col = here * cw[..., None] + up * (1 - cw[..., None])
    return col[..., 0] if squeeze else col


def bilinear(img, x, y, wrap_x=False):
    h, w = img.shape[:2]
    x = np.asarray(x, np.float64) - 0.5; y = np.asarray(y, np.float64) - 0.5
    y = np.clip(y, 0, h - 1)
    if not wrap_x: x = np.clip(x, 0, w - 1)
    x0 = np.floor(x).astype(np.int64); y0 = np.floor(y).astype(np.int64)
    fx = (x - x0).astype(np.float32); fy = (y - y0).astype(np.float32)
    if wrap_x:
        x1 = (x0 + 1) % w; x0 = x0 % w
    else:
        x1 = np.minimum(x0 + 1, w - 1)
    y1 = np.minimum(y0 + 1, h - 1)
    if img.ndim == 3: fx = fx[..., None]; fy = fy[..., None]
    return img[y0, x0] * (1 - fx) * (1 - fy) + img[y0, x1] * fx * (1 - fy) + img[y1, x0] * (1 - fx) * fy + img[y1, x1] * fx * fy


def save_rgba_preview(a, path, scale=1.0):
    pv = Image.new('RGBA', (a.shape[1], a.shape[0]), (255, 0, 255, 255))
    pv.alpha_composite(Image.fromarray((np.clip(a, 0, 1) * 255 + 0.5).astype(np.uint8), 'RGBA'))
    pv = pv.convert('RGB')
    if scale != 1.0: pv = pv.resize((int(pv.width * scale), int(pv.height * scale)), Image.LANCZOS)
    pv.save(path)


# ---------------------------------------------------------------- the brush

def brush(canvas, rows, cols, s, t, col, op, rng, bristles=None, alpha=None, blob=False):
    """Lays one stroke over canvas[rows][:, cols] whose texels sit at stroke coordinates s (along, -1..1) and t
    (across, -1..1): a loaded brush, its bristles streaking along it, thinning towards its tail."""
    inside = (np.abs(s) < 1.3) & (np.abs(t) < 1.3)
    if not inside.any(): return
    nb = bristles if bristles is not None else rng.integers(5, 11)
    ph = rng.uniform(0, 6.283, 3)
    fr = rng.uniform(0.7, 1.3, 3) * nb
    streak = (np.sin(t * fr[0] + ph[0]) + 0.6 * np.sin(t * fr[1] * 1.7 + ph[1]) + 0.35 * np.sin(t * fr[2] * 2.9 + ph[2])) / 1.95
    if blob:
        # A dab: round, its edge ragged where the paint broke off the brush.
        ang = np.arctan2(t, s)
        rad = np.hypot(s, t) / (1 + 0.16 * np.sin(3 * ang + ph[0]) + 0.1 * np.sin(5 * ang + ph[1]))
        edge = 1 - smoothstep(0.7, 1.0, rad)
        a = edge * (0.88 + 0.12 * streak) * op
    else:
        # The stroke's outline: blunt where the brush went down, tapered where it lifted.
        taper = np.sqrt(np.clip(1 - s * s, 0, 1)) ** 0.6 * (1 - 0.25 * np.clip(s, 0, 1))
        edge = 1 - smoothstep(0.72, 1.0, np.abs(t) / np.maximum(taper, 1e-3))
        a = edge * (0.78 + 0.22 * streak) * (1 - 0.3 * (s + 1) / 2) * op
    a = np.clip(a, 0, 1).astype(np.float32)
    shade = (1 + 0.07 * streak).astype(np.float32)
    region = canvas[np.ix_(rows, cols)]
    c = np.asarray(col, np.float32)[None, None, :] * shade[..., None]
    region[..., :3] = region[..., :3] * (1 - a[..., None]) + c * a[..., None]
    if alpha is not None and region.shape[-1] == 4:
        region[..., 3] = region[..., 3] * (1 - a) + a
    canvas[np.ix_(rows, cols)] = region


class Palette:
    """Colours lifted from the painting's own pixels, picked near a wanted colour."""

    def __init__(self, pixels, rng):
        self.p = np.asarray(pixels, np.float32).reshape(-1, 3)
        self.rng = rng

    def near(self, want, k=8, spread=1.0):
        pick = self.p[self.rng.integers(0, len(self.p), k)]
        d = ((pick - np.asarray(want, np.float32)[None]) ** 2).sum(1)
        c = pick[np.argmin(d)]
        return np.clip(np.asarray(want) + (c - np.asarray(want)) * spread, 0, 1)

    def any(self):
        return self.p[self.rng.integers(len(self.p))]
