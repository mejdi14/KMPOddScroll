"""The two boys, cut from the full-size scan and stood up in the grass where they sit, and a few shade trees out
in the pasture where the painting does not look, each on a painted card facing the walk."""
import math
import numpy as np
from PIL import Image
from common import *
from trees import TreePalette, tree, dab

# The boys' box in painting pixels, and where their seat meets the grass.
BOX = (300, 140, 995, 512)
SEAT_ROW = 499.0
GUTTER = 6


def boys_sprite():
    full = painting_full()
    reg = regions()
    x0, y0, x1, y1 = BOX
    fx0, fy0 = int(math.floor(x0 * FULL_SCALE)), int(math.floor(y0 * FULL_SCALE))
    fx1, fy1 = int(math.ceil(x1 * FULL_SCALE)), int(math.ceil(y1 * FULL_SCALE))
    yy, xx = np.mgrid[fy0:fy1, fx0:fx1].astype(np.float64) + 0.5
    a = bilinear(reg['fig'], xx / FULL_SCALE, yy / FULL_SCALE)
    spr = np.concatenate([full[fy0:fy1, fx0:fx1], a[..., None]], -1).astype(np.float32)
    return spr, (fx0, fy0)


def limb(can, x0, y0, x1, y1, w0, w1, bark, lit, rng):
    """A tapering limb from (x0, y0) to (x1, y1), dark, its left side catching the light."""
    length = math.hypot(x1 - x0, y1 - y0)
    n = max(2, int(length / 16))
    th = math.atan2(y1 - y0, x1 - x0)
    for k in range(n):
        f = (k + 0.5) / n
        x = x0 + (x1 - x0) * f; y = y0 + (y1 - y0) * f
        w = w0 + (w1 - w0) * f
        seg = length / n * 0.95
        dab(can, x, y, seg, max(w / 2, 0.8), th, bark * rng.uniform(0.9, 1.05), 0.97, rng, bristles=4)
        if w > 2.5:
            nx, ny = -math.sin(th), math.cos(th)
            side = -1 if nx > 0 else 1
            dab(can, x + side * nx * w * 0.25, y + side * ny * w * 0.25, seg * 0.9, max(w / 5, 0.6), th, lit, 0.55, rng, bristles=2)


def trunk(can, x, y_base, y_fork, w, bark, lit, rng):
    """The trunk as one piece: a little flared at its foot, its bark streaked up it, lit down its left side."""
    H, W = can.shape[:2]
    rows = np.arange(int(y_fork) - 2, min(int(y_base) + 1, H))
    f = (rows + 0.5 - y_fork) / max(y_base - y_fork, 1)                  # 0 at the fork, 1 at the foot
    half = w * (0.38 + 0.12 * f + 0.25 * np.clip((f - 0.85) / 0.15, 0, 1) ** 2)
    lean = 3.0 * np.sin(f * 2.2 + rng.uniform(0, 3))
    cols = np.arange(int(x - w * 1.2), int(x + w * 1.2) + 1)
    yy, xx = np.meshgrid(rows + 0.5, cols + 0.5, indexing='ij')
    u = (xx - x - lean[:, None]) / half[:, None]                     # -1 left edge .. 1 right edge
    a = 1 - smoothstep(0.82, 1.0, np.abs(u))
    a *= smoothstep(y_fork - 2, y_fork + 6, yy)
    streak = 0.5 + 0.5 * np.sin(xx * 1.7 + 3 * np.sin(yy * 0.05)) * np.sin(xx * 0.6 + 1.3)
    shade = np.clip(0.5 - 0.5 * u, 0, 1)[..., None]
    col = bark[None, None] * (0.85 + 0.25 * streak[..., None]) * (1 - shade) + lit[None, None] * shade * 0.8 + bark * shade * 0.2
    reg = can[np.ix_(rows, cols)]
    reg[..., :3] = reg[..., :3] * (1 - a[..., None]) + col * a[..., None]
    reg[..., 3] = np.maximum(reg[..., 3], a)
    can[np.ix_(rows, cols)] = reg


def elm_sprite(rng, pal, W, H, spread=1.0):
    """A pasture elm: a short trunk forking into limbs that fan up and out, the crown in separate clusters of
    leaves at their ends with sky between them, lit from the left as the boys are, shade low and to the right."""
    can = np.zeros((H, W, 4), np.float32)
    base_x, base_y = W / 2 + rng.uniform(-0.03, 0.03) * W, H - 6.0
    fork_y = H * rng.uniform(0.64, 0.7)
    tw = W * 0.05
    bark = np.clip(pal.dark.mean(0) * 0.7 + np.array([0.04, 0.03, 0.02]), 0, 1)
    lit = np.clip(bark * 1.9 + 0.05, 0, 1)
    clusters = []
    nl = rng.integers(4, 6)
    angles = (np.linspace(-0.85, 0.85, nl) + rng.normal(0, 0.1, nl)) * spread
    for a in angles:
        L = H * rng.uniform(0.36, 0.44) * (1 - 0.25 * abs(a))
        ang = -math.pi / 2 + a
        x1 = base_x + math.cos(ang) * L * 0.95; y1 = fork_y + math.sin(ang) * L
        mx, my = (base_x + x1) / 2 + rng.normal(0, 4), (fork_y + y1) / 2 + rng.normal(0, 4)
        limb(can, base_x, fork_y, mx, my, tw * 0.7, tw * 0.45, bark, lit, rng)
        limb(can, mx, my, x1, y1, tw * 0.45, tw * 0.18, bark, lit, rng)
        # twigs off its end, and leaf clusters on them
        for _ in range(rng.integers(2, 4)):
            ta = ang + rng.normal(0, 0.5)
            tl = L * rng.uniform(0.18, 0.3)
            x2 = x1 + math.cos(ta) * tl; y2 = y1 + math.sin(ta) * tl
            limb(can, x1, y1, x2, y2, tw * 0.18, tw * 0.07, bark, lit, rng)
            clusters.append((x2, y2, W * rng.uniform(0.07, 0.12), H * rng.uniform(0.06, 0.1)))
        clusters.append((x1, y1, W * rng.uniform(0.09, 0.14), H * rng.uniform(0.07, 0.11)))
        for f in (0.45, 0.7):
            clusters.append((base_x + (x1 - base_x) * f + rng.normal(0, 8), fork_y + (y1 - fork_y) * f - rng.uniform(6, 16),
                             W * rng.uniform(0.07, 0.1), H * rng.uniform(0.055, 0.085)))
    trunk(can, base_x, base_y, fork_y, tw, bark, lit, rng)
    # keep every cluster inside the sprite, with room for its ragged edge
    clusters = [(min(max(cx, rx * 1.3 + 6), W - rx * 1.3 - 6), max(cy, ry * 1.3 + 6), rx, ry) for cx, cy, rx, ry in clusters]
    # the crown's bounds, for lighting it as one mass
    cxs = np.array([c[0] for c in clusters]); cys = np.array([c[1] for c in clusters])
    left, right, top, bottom = cxs.min() - 30, cxs.max() + 30, cys.min() - 30, cys.max() + 30
    dpx = max(3.6, W / 85)
    order = sorted(clusters, key=lambda c: -c[1])        # low ones first, so the upper ones sit over them
    for cx, cy, rx, ry in order:
        n = int(rx * ry / (dpx * dpx) * 3.2) + 8
        for _ in range(n):
            ang = rng.uniform(0, 2 * math.pi); rr = math.sqrt(rng.uniform(0, 1))
            wob = 1 + 0.22 * math.sin(3 * ang + cx) + 0.12 * math.sin(5 * ang + cy)
            px = cx + math.cos(ang) * rr * rx * wob; py = cy + math.sin(ang) * rr * ry * wob
            gx = (px - left) / max(right - left, 1); gy = (py - top) / max(bottom - top, 1)
            own = -math.cos(ang) * 0.35 - math.sin(ang) * 0.5         # each cluster lit up and to the left
            light = np.clip(0.7 - 0.85 * gy - 0.4 * (gx - 0.5) + own * rr * 0.7 + rng.normal(0, 0.12), 0, 1)
            col = pal.pick(light)
            la = dpx * rng.uniform(0.8, 1.5); lb = dpx * rng.uniform(0.5, 0.85)
            dab(can, px, py, la, lb, rng.uniform(-0.8, 0.8), col, rng.uniform(0.75, 0.98), rng, bristles=rng.integers(3, 6))
    # grass over its foot
    feet = smoothstep(H - 2, H - 16, np.arange(H))[:, None]
    can[..., 3] *= feet
    return can


def tree_sprite(rng, pal, w, h, kind='round'):
    can = np.zeros((h, w, 4), np.float32)
    tree(can, w / 2, h - 8, w * 0.82, h * 0.93, pal, rng, kind=kind, dab_px=max(4.0, w / 70))
    return can


def build(rng):
    boys, (fx0, fy0) = boys_sprite()
    p = painting(); reg = regions()
    ys, xs = np.mgrid[0:PH, 0:PW]
    lum = p @ np.array([0.3, 0.55, 0.15], np.float32)
    core = (blur((reg['band'] > 0.9).astype(np.float32), 1.2) > 0.97) & (reg['fig'] < 0.01)
    # The big tree's own colours, a touch nearer and so a touch less hazed.
    big = p[core & (xs > 480) & (xs < 730) & (ys < 186) & (lum < 0.55)]
    pal = TreePalette(np.clip(big * 1.18 + np.array([-0.01, 0.02, -0.01], np.float32), 0, 1), rng)
    trees = [elm_sprite(rng, pal, 520, 520), elm_sprite(rng, pal, 400, 430, 0.8), tree_sprite(rng, pal, 300, 250, 'bush')]
    # The atlas: the boys across the top, the trees under them.
    W = max(boys.shape[1], sum(t.shape[1] + GUTTER for t in trees))
    H = boys.shape[0] + GUTTER + max(t.shape[0] for t in trees)
    atlas = np.zeros((H, W, 4), np.float32)
    atlas[:boys.shape[0], :boys.shape[1]] = boys
    rects = []
    x = 0
    for t in trees:
        y = boys.shape[0] + GUTTER
        atlas[y:y + t.shape[0], x:x + t.shape[1]] = t
        rects.append((x, y, t.shape[1], t.shape[0]))
        x += t.shape[1] + GUTTER
    return atlas, rects, (fx0, fy0)


def boys_mesh(origin):
    """A grid over the boys' box, every vertex where its pixel's ray from the entry eye meets the upright plane
    through their seat, so from there it lies on the painting exactly."""
    x0, y0, x1, y1 = BOX
    d_seat = pixel_ray(np.array([(x0 + x1) / 2]), np.array([SEAT_ROW]))[0]
    t = -E0[1] / d_seat[1]
    depth = -(E0 + d_seat * t)[2]
    nx, ny = 16, 10
    X, Y = np.meshgrid(np.linspace(x0, x1, nx + 1), np.linspace(y0, y1, ny + 1))
    d = pixel_ray(X, Y).reshape(-1, 3)
    pos = E0[None] + d * (-depth / d[:, 2])[:, None]
    uv = np.stack([X.ravel() * FULL_SCALE - origin[0], Y.ravel() * FULL_SCALE - origin[1]], -1)
    idx = []
    for j in range(ny):
        for i in range(nx):
            a0 = j * (nx + 1) + i; a1 = a0 + 1; b0 = a0 + nx + 1; b1 = b0 + 1
            idx += [a0, b0, b1, a0, b1, a1]
    print(f' boys at {depth:.2f} m, {pos[:, 0].min():.2f} to {pos[:, 0].max():.2f} m across, seat at y {pos[:, 1].min():.2f}, hat at {pos[:, 1].max():.2f}')
    return pos.astype(np.float32), uv.astype(np.float32), np.array(idx, np.int32).reshape(-1, 3)


# Shade trees in the pasture, all well out of the painting's sight: x, z, height in metres, which sprite.
PLACES = [(-30.0, 16.0, 16.0, 0, False), (38.0, 30.0, 13.0, 1, True), (-46.0, -26.0, 4.6, 2, False), (-51.0, -19.0, 3.8, 2, True)]


def tree_mesh(rects):
    from stage_ground import C
    quads = []
    for x, z, h, k, flip in PLACES:
        rx, ry, rw, rh = rects[k]
        to = np.array([x, z]) - C; dist = np.linalg.norm(to); fwd = to / dist
        right = np.array([-fwd[1], fwd[0]])
        w = h * rw / rh
        quads.append((dist, x, z, -0.25, right, w, h, (rx, ry, rw, rh), flip))
    quads.sort(key=lambda q: -q[0])
    pos, uv, idx = [], [], []
    nx, ny = 4, 2
    for _, cx, cz, base, right, w, h, (rx, ry, rw, rh), flip in quads:
        b0 = len(pos)
        for j in range(ny + 1):
            for i in range(nx + 1):
                fx = i / nx; fy = j / ny
                pos.append((cx + right[0] * (fx - 0.5) * w, base + (1 - fy) * h, cz + right[1] * (fx - 0.5) * w))
                uv.append((rx + (1 - fx if flip else fx) * rw, ry + fy * rh))
        for j in range(ny):
            for i in range(nx):
                a0 = b0 + j * (nx + 1) + i; a1 = a0 + 1; c0 = a0 + nx + 1; c1 = c0 + 1
                idx += [a0, c0, c1, a0, c1, a1]
    return np.array(pos, np.float32), np.array(uv, np.float32), np.array(idx, np.int32).reshape(-1, 3)


if __name__ == '__main__':
    rng = np.random.default_rng(9)
    atlas, rects, origin = build(rng)
    np.save(f'{WORK}/out_cards.npy', atlas)
    save_rgba_preview(atlas, f'{WORK}/cards_preview.png', 0.5)
    p, u, i = boys_mesh(origin)
    np.savez(f'{WORK}/mesh_boys.npz', pos=p, uv=u, idx=i)
    p, u, i = tree_mesh(rects)
    np.savez(f'{WORK}/mesh_trees.npz', pos=p, uv=u, idx=i)
    print('atlas', atlas.shape, rects)
