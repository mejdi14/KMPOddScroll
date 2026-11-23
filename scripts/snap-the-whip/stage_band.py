"""The far side of the meadow: a ring 40 m out all the way round. Where the painting looks, its own stretch: the
red schoolhouse with the woods behind it and, on the left, the fields running off to the far village; where the
boys stand in front of it, what they hide is rebuilt, the schoolhouse's boards carried on row by row and its
door column by column, the trees and fields mirrored in from beside them. Everywhere else the painting's
distant tree line and hazy hills carried on round the left, closing in to woods in the painting's tree colours
behind and round to the trees behind the schoolhouse."""
import math, time
import numpy as np
from PIL import Image
from common import *
from regions import LONG_WALL, DOOR, GABLE, WINDOW, polygon_mask
from trees import TreePalette, tree, crown

PXD = 24.0                      # texture pixels to a degree round the ring, the painting's own at its middle
YTOP, YBOT = 9.5, -0.6
BH = 348                        # rows, top to bottom
HALF = int(round(180 * PXD))    # one half of the ring
FULL = 2 * HALF
GUTTER = 4
BOARD = 6                       # the schoolhouse's clapboards, in painting rows


def v_of_y(y): return (YTOP - np.asarray(y, np.float64)) / (YTOP - YBOT) * BH
def y_of_v(v): return YTOP - np.asarray(v, np.float64) / BH * (YTOP - YBOT)
def u_of_az(az_deg): return (np.asarray(az_deg, np.float64) + 180.0) * PXD
def az_of_u(u): return np.asarray(u, np.float64) / PXD - 180.0
def px_per_m(): return BH / (YTOP - YBOT)


# ---------------------------------------------------------------- what the boys hide, rebuilt

def mirror_rows(img, face, hidden, reach=120):
    """Fills hidden pixels of a face from the same row, mirrored in from the nearest visible pixels either side
    and blended across the gap, so anything running along the rows (boards, a tree line, a field's edge) runs
    on through it. Returns the fill and where it found something."""
    H, W = face.shape
    out = img.copy(); got = np.zeros((H, W), bool)
    vis = face & ~hidden
    xs = np.arange(W)
    for y in range(H):
        hrow = np.where(hidden[y] & face[y])[0]
        if len(hrow) == 0: continue
        v = vis[y]
        if not v.any(): continue
        vx = xs[v]
        # nearest visible to the left and right of each hidden pixel
        li = np.searchsorted(vx, hrow) - 1
        ri = li + 1
        has_l = li >= 0; has_r = ri < len(vx)
        xl = np.where(has_l, vx[np.clip(li, 0, len(vx) - 1)], -10 ** 6)
        xr = np.where(has_r, vx[np.clip(ri, 0, len(vx) - 1)], 10 ** 6)
        dl = hrow - xl; dr = xr - hrow
        ok_l = has_l & (dl < reach); ok_r = has_r & (dr < reach)
        # The visible run beside each edge, and a source mirrored back and forth within it, so a gap wider than
        # what shows beside it is filled with that paint again and again rather than one pixel smeared out.
        runs = np.zeros(W, np.int64)
        c = 0
        for x in range(W):
            c = c + 1 if v[x] else 0; runs[x] = c
        runs_r = np.zeros(W, np.int64); c = 0
        for x in range(W - 1, -1, -1):
            c = c + 1 if v[x] else 0; runs_r[x] = c
        Ll = np.clip(np.where(has_l, runs[np.clip(xl, 0, W - 1)], 1), 1, 90)
        Lr = np.clip(np.where(has_r, runs_r[np.clip(xr, 0, W - 1)], 1), 1, 90)
        # a sliver of paint (a corner board, a gap between two heads) is no source to fill a wide gap from
        ok_l &= (Ll >= 6) | (dl <= 2 * Ll); ok_r &= (Lr >= 6) | (dr <= 2 * Lr)
        def pingpong(d, L):
            i = d % (2 * L)
            return np.where(i < L, i, 2 * L - 1 - i)
        sl = np.clip(xl - pingpong(dl - 1, Ll), 0, W - 1); sr = np.clip(xr + pingpong(dr - 1, Lr), 0, W - 1)
        sl = np.where(vis[y, sl], sl, np.clip(xl, 0, W - 1)); sr = np.where(vis[y, sr], sr, np.clip(xr, 0, W - 1))
        cl = img[y, sl]; cr = img[y, sr]
        wl = np.where(ok_l, 1.0 / np.maximum(dl, 1) ** 2, 0); wr = np.where(ok_r, 1.0 / np.maximum(dr, 1) ** 2, 0)
        tot = wl + wr
        sel = tot > 0
        c = (cl * wl[:, None] + cr * wr[:, None]) / np.maximum(tot, 1e-12)[:, None]
        out[y, hrow[sel]] = c[sel]
        got[y, hrow[sel]] = True
    return out, got


def mirror_columns(img, face, hidden, reach=200):
    """The same down the columns: from what shows above and below."""
    f, got = mirror_rows(img.transpose(1, 0, 2).copy(), face.T.copy(), hidden.T.copy(), reach)
    return f.transpose(1, 0, 2), got.T


def interp_columns(img, face, hidden):
    """Fills hidden pixels of a face along its columns, between the visible pixels above and below."""
    H, W = face.shape
    out = img.copy()
    ys = np.arange(H)
    for x in range(W):
        h = hidden[:, x] & face[:, x]
        if not h.any(): continue
        v = face[:, x] & ~hidden[:, x]
        if v.sum() < 2: continue
        for c in range(img.shape[2]):
            out[h, x, c] = np.interp(ys[h], ys[v], img[v, x, c])
    return out


def clone_boards(img, face, hidden, done):
    """What a row-wise fill could not reach on a wall: copied from the same column a whole number of boards up
    or down, so the clapboards stay in step, its light matched to the wall's light where it goes."""
    H, W = face.shape
    out = img.copy()
    vis = face & ~hidden
    todo = face & hidden & ~done
    ys, xs = np.where(todo)
    src_y = np.full(len(ys), -1)
    for k in range(1, 40):
        for sgn in (-1, 1):
            cand = ys + sgn * k * BOARD
            ok = (src_y < 0) & (cand >= 0) & (cand < H)
            ok[ok] = vis[cand[ok], xs[ok]]
            src_y = np.where(ok, cand, src_y)
    good = src_y >= 0
    # the wall's light, smooth: a push-pull of what shows, so a board copied from far off is lit as here
    low = blur(pushpull(img, vis.astype(np.float32)), 5)
    ys, xs, sy = ys[good], xs[good], src_y[good]
    out[ys, xs] = img[sy, xs] + (low[ys, xs] - low[sy, xs])
    return out


def clean_plate():
    """The painting with the boys taken out of the band: colour and how solid (not sky) each pixel is."""
    p = painting(); reg = regions()
    fig = reg['fig']
    hidden = blur((fig > 0.02).astype(np.float32), 2.5) > 0.02
    solid = 1 - reg['sky']
    rgbs = np.concatenate([p, solid[..., None]], -1)
    ys = np.mgrid[0:PH, 0:PW][0]
    gr = reg['ground_row'][None, :]
    band_rows = ys < gr + 6
    window = polygon_mask(WINDOW) > 0.5
    walls = [(polygon_mask(LONG_WALL) > 0.5) & ~window, polygon_mask(GABLE) > 0.5]
    door = polygon_mask(DOOR) > 0.5
    school = reg['school'] > 0.5
    roof = school & ~walls[0] & ~walls[1] & ~door & ~window
    xs = np.mgrid[0:PH, 0:PW][1]
    left = band_rows & ~school & (xs < 780)        # the far fields and tree line, left of the schoolhouse
    right = band_rows & ~school & (xs >= 780)      # the woods behind it and right of it
    # The woods stand higher than any of the boys' heads there, so nothing behind them is sky: the woods fill
    # down from their own leaves only.
    right &= (solid > 0.5) | hidden
    out = rgbs.copy()
    # The walls: their boards run on along the rows, and where a whole row is hidden, from boards above or below.
    for wall in walls:
        f, got = mirror_rows(rgbs, wall, hidden, reach=140)
        f = clone_boards(f, wall, hidden, got)
        out = np.where((wall & hidden)[..., None], f, out)
    # The door and the corner beside it, and the window and its shutters, run down the columns.
    for face in (door, window):
        f = interp_columns(rgbs, face, hidden)
        out = np.where((face & hidden)[..., None], f, out)
    # The roof and the far fields mirrored in along the rows; the woods on the right, which the head of the chain
    # hides from the edge of the painting in, down their columns from the trees above.
    for face, by_rows in ((roof, True), (left, True), (right, False)):
        f, got = (mirror_rows(rgbs, face, hidden, reach=600) if by_rows else mirror_columns(rgbs, face, hidden, reach=300))
        rest = face & hidden & ~got
        if rest.any():
            w = (face & ~hidden).astype(np.float32)
            f = np.where(rest[..., None], pushpull(rgbs, w), f)
        if not by_rows:
            f[..., 3] = 1.0
        out = np.where((face & hidden)[..., None], f, out)
    # Soften the seams a touch inside what was filled.
    soft = blur(out, 0.7)
    k = (blur(hidden.astype(np.float32), 1.0) * hidden)[..., None]
    out = out * (1 - 0.5 * k) + soft * 0.5 * k
    out[..., 3] = np.clip(out[..., 3], 0, 1)
    return out


# ---------------------------------------------------------------- the ring

def painting_strip(plate):
    """The clean plate laid on the ring where the painting sees it: colour, how solid it is, and where it can be
    used at all (inside the painting)."""
    v, u = np.mgrid[0:BH, 0:FULL].astype(np.float64) + 0.5
    az = np.radians(az_of_u(u)); y = y_of_v(v)
    P = np.stack([RB * np.sin(az), y, -RB * np.cos(az)], -1) - E0[None, None]
    px, py, zc = to_pixel(P / np.linalg.norm(P, axis=-1, keepdims=True))
    inside = (zc > 0.1) & (px >= 0.5) & (px < PW - 0.5) & (py >= 0) & (py < PH)
    s = np.where(inside[..., None], bilinear(plate, px, py), 0).astype(np.float32)
    return s[..., :3], s[..., 3], inside


def far_ground():
    """The meadow's colour just in front of the ring, round it, from the painted ground."""
    from stage_ground import NU, v_of_r
    g = np.asarray(Image.open(f'{WORK}/out_ground.png').convert('RGB')).astype(np.float32) / 255
    rows = g[int(v_of_r(34)):int(v_of_r(40))].mean(0)
    rows = blur(rows[None], 6, wrap_x=True)[0]
    az = np.arange(FULL) / PXD - 180.0
    cols = ((np.radians(az) + math.pi) / (2 * math.pi) * NU).astype(int) % NU
    return rows[cols]


def colour_sets():
    p = painting(); reg = regions()
    ys, xs = np.mgrid[0:PH, 0:PW]
    lum = p @ np.array([0.3, 0.55, 0.15], np.float32)
    core = (blur((reg['band'] > 0.9).astype(np.float32), 1.2) > 0.97) & (reg['fig'] < 0.01) & (reg['school'] < 0.01)
    # the woods' greens, their few autumn browns left out so the woods carried on read as the painting's
    near_trees = p[core & (xs > 560) & (ys < 330) & (lum < 0.4) & (p[..., 1] >= p[..., 0] - 0.01)]
    far_trees = p[core & (xs < 300) & (ys > 318) & (ys < 360) & (lum < 0.4)]
    far_field = p[core & (xs < 280) & (ys >= 366) & (ys < 392)]
    hills = p[core & (xs < 90) & (ys > 322) & (ys < 345)]
    return near_trees, far_trees, far_field, hills


def woods(az):
    """How much the woods close in, by azimuth: open to the far fields on the painting's left, woods from behind
    round to the trees behind the schoolhouse."""
    a = (np.asarray(az, np.float64) + 180) % 360 - 180
    return np.clip(smoothstep(-28, -95, a) + smoothstep(14, 26, a), 0, 1)


def build(rng):
    t0 = time.time()
    plate = clean_plate()
    np.save(f'{WORK}/plate.npy', plate)
    save_rgba_preview(np.concatenate([plate[..., :3], np.clip(plate[..., 3:], 0, 1)], -1)[100:420], f'{WORK}/plate.png')
    print(' clean plate', time.time() - t0)
    col, solid, inside = painting_strip(plate)
    ntp, ftp, ffp, hlp = colour_sets()
    print(' painting laid on the ring', time.time() - t0, len(ntp), len(ftp), len(ffp), len(hlp))
    # Where the painting's own stretch goes: cut where its tree line is lowest near each edge.
    tops = (solid[:int(v_of_y(1.6))] > 0.5).sum(0) * inside.all(0)
    cols_in = np.where(inside.all(0))[0]
    lo, hi = cols_in.min(), cols_in.max()
    def gap_near(c, toward):
        win = np.arange(c, c + toward * 60, toward)
        return int(win[np.argmin(tops[win] + 0.01 * np.abs(win - c))])
    cut0, cut1 = gap_near(lo + 2, 1), gap_near(hi - 2, -1)
    ring = np.zeros((BH, FULL, 4), np.float32)
    rows = np.arange(BH)
    yv = y_of_v(rows + 0.5)
    azs = az_of_u(np.arange(FULL) + 0.5)
    wd = woods(azs)
    horizon_y = EYE                      # eye level on the ring: the far fields run up to it
    # The far fields from the foot of the ring up to the horizon: the meadow's own colour at the foot going over
    # to the painting's far field, hazier as it goes.
    fg = far_ground()
    ff = ffp.mean(0)
    haze = np.array([0.5, 0.5, 0.42], np.float32)
    k = smoothstep(0.0, horizon_y, yv)[:, None, None]
    base = fg[None] * (1 - k) + (ff * 0.85 + haze * 0.15)[None, None] * k
    base = base * (1 + 0.07 * fbm(np.arange(FULL)[None] / 14.0, rows[:, None] / 2.5, seed=61, octaves=3))[..., None]
    low = yv < horizon_y + 0.05
    ring[low, :, :3] = base[low]
    ring[low, :, 3] = 1
    # Field boundaries far off: thin dark hedgerow lines across the far fields.
    hp = Palette(ftp, rng)
    for _ in range(int(FULL / 9)):
        cx = rng.uniform(0, FULL)
        yy = rng.uniform(0.35, horizon_y - 0.05)
        la = rng.uniform(15, 70); lb = rng.uniform(0.8, 1.6)
        cy = v_of_y(yy)
        rr = np.arange(max(int(cy - lb - 2), 0), min(int(cy + lb + 3), BH))
        xs = np.arange(int(cx - la - 2), int(cx + la + 3))
        Y, X = np.meshgrid(rr + 0.5, xs + 0.5, indexing='ij')
        brush(ring, rr, xs % FULL, (X - cx) / la, (Y - cy) / lb, np.clip(hp.any() * 0.95 + haze * 0.1, 0, 1), rng.uniform(0.5, 0.8), rng, alpha=True)
    # Hazy hills along the horizon, the painting's far hills, low and blue-green.
    hill_col = np.clip(hlp.mean(0) * 0.8 + haze * 0.2, 0, 1)
    u = np.arange(FULL)
    ridge = horizon_y + 0.45 + 0.55 * (0.5 + 0.5 * fbm(u / 260.0, np.zeros(FULL), seed=65, octaves=4)) + 0.25 * (0.5 + 0.5 * fbm(u / 60.0, np.ones(FULL), seed=66, octaves=3))
    hill_a = (yv[:, None] < ridge[None]) & (yv[:, None] >= horizon_y - 0.02)
    hk = np.clip((ridge[None] - yv[:, None]) * 6, 0, 1) * hill_a
    tex = (1 + 0.05 * fbm(u[None] / 7.0, rows[:, None] / 3.0, seed=67, octaves=2))[..., None]
    ring[..., :3] = ring[..., :3] * (1 - hk[..., None]) + hill_col * tex * hk[..., None]
    ring[..., 3] = np.maximum(ring[..., 3], hk)
    print(' far fields and hills', time.time() - t0)
    # The far tree line along the horizon, low and hazed, in clumps with gaps: as the painting's on its left.
    fpal = TreePalette(ftp, rng)
    u_ = 0.0
    while u_ < FULL:
        hgt = rng.uniform(10, 30) * (1 + 0.6 * rng.uniform() ** 3)
        wid = hgt * rng.uniform(1.2, 2.6)
        n = rng.integers(1, 4)
        for j in range(n):
            x = u_ + wid * (0.5 + j * 0.7)
            tree(ring, x % FULL, v_of_y(horizon_y + rng.uniform(-0.05, 0.15)), wid, hgt, fpal, rng, kind='bush' if rng.uniform() < 0.5 else 'round', dab_px=2.6)
        u_ += wid * (0.7 * n + 0.3) * (1 + rng.choice([0.0, rng.uniform(0.2, 1.2)], p=[0.6, 0.4]))
    print(' far tree line', time.time() - t0)
    # The woods: a back row of tall trees, hazed a little, a front row of smaller ones over them, and brush along
    # their foot, standing on the foot of the ring where the woods close in, lit from the left as the painting's.
    tpal = TreePalette(ntp, rng)
    m = px_per_m()
    ground_v = v_of_y(0.0)
    start_u, end_u = float(cut1 - 40), float(cut0 + FULL + 40)
    placed = 0
    for row, (hlo, hhi, step, hz_k) in enumerate(((6.5, 9.0, 0.55, 0.12), (3.5, 6.5, 0.7, 0.0))):
        u_ = start_u + rng.uniform(0, 40)
        while u_ < end_u:
            w = float(woods(az_of_u(u_ % FULL)))
            hgt = rng.uniform(hlo, hhi) * (0.55 + 0.45 * w)
            wid = hgt * rng.uniform(0.85, 1.25)
            if rng.uniform() < w * (1.05 if row == 0 else 0.8):
                kind_poplar = row == 0 and rng.uniform() < 0.08
                if kind_poplar:
                    tree(ring, u_ % FULL, ground_v + rng.uniform(0, 3), wid * m * 0.3, hgt * m, tpal, rng, kind='poplar', dab_px=5.0)
                else:
                    crown(ring, u_ % FULL, ground_v + rng.uniform(0, 4), wid * m, hgt * m, tpal, rng, dab_px=5.5,
                          haze=haze, haze_k=hz_k, trunk=rng.uniform() < (0.15 if row == 0 else 0.35))
                placed += 1
            u_ += wid * m * step * rng.uniform(0.8, 1.2)
        print(f'  row {row}: {placed} trees', time.time() - t0, flush=True)
    bpal = TreePalette(np.clip(ntp * 0.9, 0, 1), rng)
    u_ = start_u
    while u_ < end_u:
        w = float(woods(az_of_u(u_ % FULL)))
        hgt = rng.uniform(0.9, 2.2); wid = hgt * rng.uniform(1.6, 2.8)
        if rng.uniform() < w:
            crown(ring, u_ % FULL, ground_v + rng.uniform(1, 4), wid * m, hgt * m * 1.6, bpal, rng, dab_px=4.5, trunk=False)
        u_ += wid * m * rng.uniform(0.5, 0.9)
    print(' woods', placed, 'trees', time.time() - t0)
    # The painting's own stretch over it, cut where its tree line is lowest near each edge and feathered into
    # what goes on round it.
    f = np.clip(np.minimum(np.arange(FULL) - cut0, cut1 - np.arange(FULL)) / 30.0, 0, 1)[None, :, None]
    f = f * inside[..., None]
    pa = np.clip(solid[..., None], 0, 1); ga = ring[..., 3:4]
    out_a = ga * (1 - f) + pa * f
    ring[..., :3] = (ring[..., :3] * ga * (1 - f) + col * pa * f) / np.maximum(out_a, 1e-4)
    ring[..., 3:4] = out_a
    print(' composed', time.time() - t0, 'painting from', round(float(az_of_u(cut0)), 1), 'to', round(float(az_of_u(cut1)), 1))
    return ring


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
    ys = [YBOT, 0.0, 0.6, EYE, 2.0, 3.5, 5.5, YTOP]
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
    save_rgba_preview(a, f'{WORK}/band_preview.png', 0.5)
    pos, uv, idx = mesh()
    np.savez(f'{WORK}/mesh_band.npz', pos=pos, uv=uv, idx=idx)
    print('done')
