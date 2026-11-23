"""The painting itself, laid back over the sky and the meadow from where it was painted, so from there the
place is the painting to the pixel: one texture of its sky above its meadow, each faded at its edges."""
import numpy as np
from common import *

SKY_ROWS = 352
G_FROM = 386
GUTTER = 4
G_OFF = SKY_ROWS + GUTTER - G_FROM     # atlas row = painting row + G_OFF in the ground part
BAND_R = RB - 0.3                      # nothing of it past the ring


def atlas():
    p = painting(); reg = regions()
    ys, xs = np.mgrid[0:PH, 0:PW].astype(np.float32) + 0.5
    def edge(px, n): return smoothstep(0, n, px)
    sky = reg['sky']
    sky_core = (blur((sky > 0.5).astype(np.float32), 1.5) > 0.985).astype(np.float32) * sky
    # Its top and sides fade over a long way: the painting's upper right is darkened under old varnish, and a
    # short fade would leave that a dark patch against the clouds carried on above it.
    a_sky = blur(sky_core, 0.8) * edge(xs, 60) * edge(PW - xs, 60) * edge(ys, 110)
    fig = reg['fig']
    # Keep clear of the boys, and of the dark paint of their outlines just outside them, which laid flat on the
    # meadow would be seen from further in as dark sticks in the grass.
    clear = (blur((fig > 0.02).astype(np.float32), 2.5) < 0.02).astype(np.float32)
    gr = reg['ground_row'][None, :]
    below = smoothstep(gr + 0.5, gr + 2.0, ys)
    a_ground = blur(below * clear, 0.8) * edge(xs, 30) * edge(PW - xs, 30) * edge(PH - ys, 80)
    out = np.zeros((SKY_ROWS + GUTTER + PH - G_FROM, PW, 4), np.float32)
    out[:SKY_ROWS, :, :3] = p[:SKY_ROWS]; out[:SKY_ROWS, :, 3] = a_sky[:SKY_ROWS]
    out[SKY_ROWS + GUTTER:, :, :3] = p[G_FROM:]; out[SKY_ROWS + GUTTER:, :, 3] = a_ground[G_FROM:]
    return out


def sky_mesh():
    xs = np.linspace(0, PW, 51); ys = np.linspace(0, SKY_ROWS - 4, 17)
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
    from regions import ground_row
    xs = np.linspace(0, PW, 61)
    # Rows close together by the ring, where a pixel covers metres of meadow, and further apart close to.
    s = np.linspace(0, 1, 58)
    gr = ground_row(xs) + 0.3
    X = np.broadcast_to(xs[None], (len(s), len(xs)))
    Y = gr[None] + (PH - gr[None]) * s[:, None] ** 2.2
    d = pixel_ray(X, Y).reshape(-1, 3)
    t = raycast(E0, d)
    pos = E0[None] + d * np.where(np.isfinite(t), t, 0)[:, None]
    ok = np.isfinite(t) & (np.hypot(pos[:, 0], pos[:, 2]) < BAND_R)
    pos[~ok] = (E0[None] + d * (BAND_R / np.maximum(np.hypot(d[:, 0], d[:, 2]), 1e-6))[:, None])[~ok] * np.array([1, 0, 1])
    uv = np.stack([X.ravel(), Y.ravel() + G_OFF], -1)
    nx = len(xs)
    tri = []
    for j in range(len(s) - 1):
        for i in range(nx - 1):
            a0 = j * nx + i; a1 = a0 + 1; b0 = a0 + nx; b1 = b0 + 1
            tri += [(a0, b0, b1), (a0, b1, a1)]
    tri = np.array(tri)
    used = np.unique(tri)
    remap = -np.ones(len(pos), np.int64); remap[used] = np.arange(len(used))
    pos = pos[used]
    # How much more of the eye each bit of it fills from the end of the walk than from where it was painted:
    # where that is a lot, its pixels would be seen blown up, so it gives way to the meadow's own paint.
    n = np.array([0.0, 1.0, 0.0])
    def solid(eye):
        v = pos - eye[None]; dd = np.linalg.norm(v, axis=1)
        return np.abs((v / dd[:, None]) @ n) / (dd * dd)
    grow = solid(ROUTE[-1]) / solid(E0)
    weight = smoothstep(1.8, 4.0, grow).astype(np.float32)
    print(' fade weight: mean', weight.mean().round(2), 'grow range', grow.min().round(2), grow.max().round(2))
    return pos.astype(np.float32), uv[used].astype(np.float32), remap[tri].astype(np.int32), weight


if __name__ == '__main__':
    a = atlas()
    np.save(f'{WORK}/out_paint.npy', a)
    save_rgba_preview(a, f'{WORK}/paint_preview.png', 0.5)
    p, u, i = sky_mesh()
    np.savez(f'{WORK}/mesh_skydecal.npz', pos=p, uv=u, idx=i)
    p, u, i, w = ground_mesh()
    np.savez(f'{WORK}/mesh_grounddecal.npz', pos=p, uv=u, idx=i, w=w)
