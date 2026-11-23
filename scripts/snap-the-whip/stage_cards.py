"""The boys, cut from the full-size scan and stood up in the meadow where they run: the chain of seven on one
sheet bent to each boy's own distance, worked out from where his feet meet the grass, and the boy fallen on his
hands and knees further off on a card of his own; and a few shade trees in the meadow where the painting does
not look, each on a painted card facing the walk."""
import math
import numpy as np
from PIL import Image
from common import *
from trees import TreePalette, tree, dab, crown

# The chain's box and the fallen boy's in painting pixels.
CHAIN_BOX = (238, 240, 1200, 646)
CRAWLER_BOX = (4, 382, 256, 506)
CRAWLER_KNEES = 497.0
GUTTER = 6

# Where each boy's feet meet the grass, and points over his body that go back to that same distance: column,
# row and the row of the feet. The sheet's depth is blended between them, so the boys stand where they run and
# their joined hands bridge from one to the next.
BOYS = [
    # the tail and the dark boy just behind him
    ([(300, 300), (300, 420), (268, 565), (330, 556), (290, 480)], 574),
    ([(372, 300), (385, 420), (398, 492)], 570),
    # the third boy, his foot kicked up behind the big boy's leg too
    ([(478, 300), (470, 400), (440, 600), (552, 570)], 616),
    # the big boy in the blue jacket, his arm and his kicked-up foot
    ([(540, 262), (560, 380), (500, 625), (600, 470), (722, 485), (650, 350)], 638),
    # the boy with the braces
    ([(805, 300), (830, 420), (762, 588), (905, 578), (745, 360), (900, 345)], 590),
    # the feathered hat and the head of the chain
    ([(1060, 285), (1040, 420), (990, 585), (985, 352)], 591),
    ([(1172, 290), (1170, 400), (1155, 584), (1110, 480)], 588),
]


def ground_depth(row, col=600.0):
    """How far ahead (-z) the meadow is at a painting row."""
    d = pixel_ray(np.array([float(col)]), np.array([float(row)]))[0]
    t = -E0[1] / d[1]
    return float(-(E0 + d * t)[2])


def sheet_depth(X, Y):
    pts, deps = [], []
    for points, feet in BOYS:
        dep = ground_depth(feet, np.mean([p[0] for p in points]))
        for p in points:
            pts.append(p); deps.append(dep)
    pts = np.array(pts, np.float64); deps = np.array(deps)
    d2 = (X.ravel()[:, None] - pts[None, :, 0]) ** 2 + (Y.ravel()[:, None] - pts[None, :, 1]) ** 2
    w = np.exp(-d2 / (2 * 38.0 ** 2)) + 1e-12
    return ((w * deps[None]).sum(1) / w.sum(1)).reshape(X.shape)


def sprite(box, alpha):
    full = painting_full()
    x0, y0, x1, y1 = box
    fx0, fy0 = int(math.floor(x0 * FULL_SCALE)), int(math.floor(y0 * FULL_SCALE))
    fx1, fy1 = min(int(math.ceil(x1 * FULL_SCALE)), full.shape[1]), min(int(math.ceil(y1 * FULL_SCALE)), full.shape[0])
    yy, xx = np.mgrid[fy0:fy1, fx0:fx1].astype(np.float64) + 0.5
    a = bilinear(alpha, xx / FULL_SCALE, yy / FULL_SCALE)
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


def tree_sprite(rng, pal, w, h, kind='bush'):
    """A bush of the woods' edge: a low crown of clusters down to the grass."""
    can = np.zeros((h, w, 4), np.float32)
    crown(can, w / 2, h + 0.1 * h, w * 0.9, h * 1.05, pal, rng, dab_px=max(4.0, w / 60), trunk=False)
    feet = smoothstep(h - 2, h - 14, np.arange(h))[:, None]
    can[..., 3] *= feet
    return can


def build(rng):
    reg = regions()
    chain, corigin = sprite(CHAIN_BOX, reg['chain'])
    crawler, korigin = sprite(CRAWLER_BOX, reg['crawler'])
    p = painting()
    ys, xs = np.mgrid[0:PH, 0:PW]
    lum = p @ np.array([0.3, 0.55, 0.15], np.float32)
    core = (blur((reg['band'] > 0.9).astype(np.float32), 1.2) > 0.97) & (reg['fig'] < 0.01) & (reg['school'] < 0.01)
    # The woods' own colours behind the schoolhouse, a touch nearer and so a touch less hazed.
    near = p[core & (xs > 560) & (ys < 330) & (lum < 0.45) & (p[..., 1] >= p[..., 0] - 0.01)]
    pal = TreePalette(np.clip(near * 1.12 + np.array([-0.01, 0.02, -0.01], np.float32), 0, 1), rng)
    trees = [elm_sprite(rng, pal, 520, 520), elm_sprite(rng, pal, 400, 430, 0.8), tree_sprite(rng, pal, 300, 250, 'bush')]
    # The atlas: the chain across the top, the fallen boy and the trees under it.
    row2 = [crawler] + trees
    W = max(chain.shape[1], sum(t.shape[1] + GUTTER for t in row2))
    H = chain.shape[0] + GUTTER + max(t.shape[0] for t in row2)
    atlas = np.zeros((H, W, 4), np.float32)
    atlas[:chain.shape[0], :chain.shape[1]] = chain
    rects = []
    x = 0
    for t in row2:
        y = chain.shape[0] + GUTTER
        atlas[y:y + t.shape[0], x:x + t.shape[1]] = t
        rects.append((x, y, t.shape[1], t.shape[0]))
        x += t.shape[1] + GUTTER
    return atlas, rects, corigin, korigin


def chain_mesh(origin):
    """A grid over the chain's box, every vertex where its pixel's ray from the entry eye reaches the sheet's
    depth there, so from there it lies on the painting exactly."""
    x0, y0, x1, y1 = CHAIN_BOX
    nx, ny = 48, 20
    X, Y = np.meshgrid(np.linspace(x0, x1, nx + 1), np.linspace(y0, y1, ny + 1))
    depth = sheet_depth(X, Y).ravel()
    d = pixel_ray(X, Y).reshape(-1, 3)
    pos = E0[None] + d * (-depth / d[:, 2])[:, None]
    uv = np.stack([X.ravel() * FULL_SCALE - origin[0], Y.ravel() * FULL_SCALE - origin[1]], -1)
    idx = []
    for j in range(ny):
        for i in range(nx):
            a0 = j * (nx + 1) + i; a1 = a0 + 1; b0 = a0 + nx + 1; b1 = b0 + 1
            idx += [a0, b0, b1, a0, b1, a1]
    print(f' chain from {depth.min():.2f} to {depth.max():.2f} m, {pos[:, 0].min():.2f} to {pos[:, 0].max():.2f} m across')
    return pos, uv, np.array(idx, np.int32).reshape(-1, 3)


def crawler_mesh(origin, rect):
    """The fallen boy upright where his knees are on the grass."""
    x0, y0, x1, y1 = CRAWLER_BOX
    depth = ground_depth(CRAWLER_KNEES, 200)
    nx, ny = 8, 4
    X, Y = np.meshgrid(np.linspace(x0, x1, nx + 1), np.linspace(y0, y1, ny + 1))
    d = pixel_ray(X, Y).reshape(-1, 3)
    pos = E0[None] + d * (-depth / d[:, 2])[:, None]
    uv = np.stack([X.ravel() * FULL_SCALE - origin[0] + rect[0], Y.ravel() * FULL_SCALE - origin[1] + rect[1]], -1)
    idx = []
    for j in range(ny):
        for i in range(nx):
            a0 = j * (nx + 1) + i; a1 = a0 + 1; b0 = a0 + nx + 1; b1 = b0 + 1
            idx += [a0, b0, b1, a0, b1, a1]
    print(f' fallen boy at {depth:.2f} m')
    return pos, uv, np.array(idx, np.int32).reshape(-1, 3)


# Shade trees in the meadow, all well out of the painting's sight: x, z, height in metres, which sprite (the
# fallen boy is the first in the row under the chain).
PLACES = [(-17.0, 14.0, 14.0, 1, False), (21.0, 9.0, 12.0, 2, True), (-27.0, -9.0, 3.6, 3, False), (-29.0, -4.0, 3.0, 3, True)]


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
    atlas, rects, corigin, korigin = build(rng)
    np.save(f'{WORK}/out_cards.npy', atlas)
    save_rgba_preview(atlas, f'{WORK}/cards_preview.png', 0.5)
    p, u, i = chain_mesh(corigin)
    np.savez(f'{WORK}/mesh_boys.npz', pos=p.astype(np.float32), uv=u.astype(np.float32), idx=i)
    p, u, i = crawler_mesh(korigin, rects[0])
    np.savez(f'{WORK}/mesh_crawler.npz', pos=p.astype(np.float32), uv=u.astype(np.float32), idx=i)
    p, u, i = tree_mesh(rects)
    np.savez(f'{WORK}/mesh_trees.npz', pos=p, uv=u, idx=i)
    print('atlas', atlas.shape, rects)
