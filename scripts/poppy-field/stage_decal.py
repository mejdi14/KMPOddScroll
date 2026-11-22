"""The painting itself, laid back over the sky and the ground from where it was painted, so from there the
place is the painting to the pixel: one texture of its sky above its ground, each faded at its edges."""
import math
import numpy as np
from PIL import Image
from common import *

SKY_ROWS = 480
G_FROM = 320
GUTTER = 4
G_OFF = SKY_ROWS + GUTTER - G_FROM     # atlas row = painting row + G_OFF in the ground part
BAND_R = 193.8                         # nothing of it past the tree line


def atlas():
    p = painting(); ids = region_ids()
    ys, xs = np.mgrid[0:PH, 0:PW].astype(np.float32) + 0.5
    def edge(px, n): return smoothstep(0, n, px)
    sky = (ids == ID_SKY).astype(np.float32)
    sky_core = (blur(sky, 1.8) > 0.985).astype(np.float32)
    a_sky = blur(sky_core, 1.0) * edge(xs, 36) * edge(PW - xs, 36) * edge(ys, 44)
    ground = (ids == ID_GROUND).astype(np.float32)
    things = ((ids == ID_FIG) | (ids == ID_HILLFIG)).astype(np.float32)
    trees = ((ids == ID_HILLTREES) | (ids == ID_BAND)).astype(np.float32)
    g_core = ground * (blur(ground, 1.5) > 0.985) * (blur(things, 5) < 0.01) * (blur(trees, 1.5) < 0.02)
    a_ground = blur(g_core.astype(np.float32), 1.2) * edge(xs, 30) * edge(PW - xs, 30) * edge(PH - ys, 40)
    out = np.zeros((SKY_ROWS + GUTTER + PH - G_FROM, PW, 4), np.float32)
    out[:SKY_ROWS, :, :3] = p[:SKY_ROWS]; out[:SKY_ROWS, :, 3] = a_sky[:SKY_ROWS]
    out[SKY_ROWS + GUTTER:, :, :3] = p[G_FROM:]; out[SKY_ROWS + GUTTER:, :, 3] = a_ground[G_FROM:]
    return out


def sky_mesh():
    xs = np.linspace(0, PW, 51); ys = np.linspace(0, SKY_ROWS - 4, 21)
    X, Y = np.meshgrid(xs, ys)
    d = pixel_ray(X, Y).reshape(-1, 3)
    uv = np.stack([X.ravel(), Y.ravel()], -1)
    nx = len(xs)
    idx = []
    for j in range(len(ys) - 1):
        for i in range(nx - 1):
            a0 = j * nx + i; a1 = a0 + 1; b0 = a0 + nx; b1 = b0 + 1
            idx += [a0, b0, b1, a0, b1, a1]
    return d.astype(np.float32), uv.astype(np.float32), np.array(idx, np.int32).reshape(-1, 3)


def ground_mesh():
    xs = np.linspace(0, PW, 76); ys = np.linspace(G_FROM + 10, PH, 60)
    X, Y = np.meshgrid(xs, ys)
    d = pixel_ray(X, Y).reshape(-1, 3)
    t = raycast(E0, d)
    pos = E0[None] + d * np.where(np.isfinite(t), t, 0)[:, None]
    ok = np.isfinite(t) & (np.hypot(pos[:, 0], pos[:, 2]) < BAND_R)
    uv = np.stack([X.ravel(), Y.ravel() + G_OFF], -1)
    nx = len(xs)
    idx = []
    tri = []
    for j in range(len(ys) - 1):
        for i in range(nx - 1):
            a0 = j * nx + i; a1 = a0 + 1; b0 = a0 + nx; b1 = b0 + 1
            tri += [(a0, b0, b1), (a0, b1, a1)]
    tri = np.array(tri)
    good = ok[tri].all(1)
    # Leave out any that bridge a near slope and the ground far behind it.
    cpx = X.ravel()[tri].mean(1); cpy = Y.ravel()[tri].mean(1)
    dc = pixel_ray(cpx, cpy)
    tc = raycast(E0, dc)
    tm = np.where(np.isfinite(t), t, 0)[tri].mean(1)
    cont = np.isfinite(tc) & (np.abs(tc - tm) < 0.12 * tc + 0.3)
    keep = good & cont
    print(' ground decal: kept', keep.sum(), 'of', len(tri))
    tri = tri[keep]
    used = np.unique(tri)
    remap = -np.ones(len(pos), np.int64); remap[used] = np.arange(len(used))
    pos = pos[used]
    # How much more of the eye each bit of it fills from the end of the walk than from where it was painted:
    # where that is a lot, its pixels would be seen blown up, so it gives way to the ground's own paint.
    e = 0.05
    dx = (height(pos[:, 0] + e, pos[:, 2]) - height(pos[:, 0] - e, pos[:, 2])) / (2 * e)
    dz = (height(pos[:, 0], pos[:, 2] + e) - height(pos[:, 0], pos[:, 2] - e)) / (2 * e)
    n = np.stack([-dx, np.ones_like(dx), -dz], -1); n /= np.linalg.norm(n, axis=1, keepdims=True)
    def solid(eye):
        v = pos - eye[None]; d = np.linalg.norm(v, axis=1)
        return np.abs((v / d[:, None] * n).sum(1)) / (d * d)
    grow = solid(ROUTE[-1]) / solid(E0)
    weight = smoothstep(2.2, 5.0, grow).astype(np.float32)
    print(' fade weight: mean', weight.mean().round(2), 'grow range', grow.min().round(2), grow.max().round(2))
    return pos.astype(np.float32), uv[used].astype(np.float32), remap[tri].astype(np.int32), weight


if __name__ == '__main__':
    a = atlas()
    np.save(f'{WORK}/out_paint.npy', a)
    pv = Image.new('RGBA', (a.shape[1], a.shape[0]), (255, 0, 255, 255))
    pv.alpha_composite(Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8)))
    pv.convert('RGB').resize((600, a.shape[0] // 2)).save(f'{WORK}/paint_preview.png')
    p, u, i = sky_mesh()
    np.savez(f'{WORK}/mesh_skydecal.npz', pos=p, uv=u, idx=i)
    p, u, i, w = ground_mesh()
    np.savez(f'{WORK}/mesh_grounddecal.npz', pos=p, uv=u, idx=i, w=w)
