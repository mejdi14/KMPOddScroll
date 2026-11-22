"""The grazing flock, trees, river and mill beyond the painting's edges."""
import math
from pathlib import Path

import numpy as np
from PIL import Image

from common import bilinear, fbm, height, painting, smoothstep
from trees import dab

# Feet in the meadow, painted width in metres, cutout and facing.
MEADOW_TREES = (
    ((-14.0, -9.0), 7.3, 'tree', False),
    ((-23.5, -5.7), 6.6, 'low_tree', False),
    ((-28.8, 2.9), 7.1, 'tree', True),
    ((-21.8, -21.0), 5.8, 'low_tree', True),
)
FLOCK = (
    ((-7.9, -7.3), 1.62, 'ewe_side', False),
    ((-11.3, -4.3), 1.51, 'ewe_step', False),
    ((-13.6, -6.0), 1.12, 'ewe_quarter', False),
    ((-11.9, -11.9), 1.42, 'ewe_step', True),
    ((-15.4, 1.0), 1.40, 'ewe_side', True),
)
MILL = (28.0, -14.0)
MILL_WIDTH = 9.4
MILL_FOOT = (0.46, 0.971)
ART = Path(__file__).with_name('art')


def paint_ground(canvas, x, z, walk):
    # Keep the grass strokes and poppies visible in the broken light under each crown.
    for (tx, tz), width, _, _ in MEADOW_TREES:
        scale = width / 7.3
        under = (abs(x - tx) < 7 * scale) & (abs(z - tz) < 9 * scale)
        dx, dz = (x[under] - tx) / scale, (z[under] - tz) / scale
        flecks = fbm(dx * 2, dz * 2, seed=73, octaves=3)
        crown = np.hypot((dx + 0.6) / 3.3, (dz + 2.1) / 4.6)
        shade = (1 - smoothstep(0.58, 1.1, crown + 0.27 * flecks)) * (0.78 + 0.22 * flecks)
        canvas[under] *= 1 - shade[:, None] * np.array([0.30, 0.23, 0.18])

    for (sx, sz), width, _, _ in FLOCK:
        under = (abs(x - sx) < width) & (abs(z - sz) < width)
        dx, dz = x[under] - sx, z[under] - sz
        facing = np.array([sx, sz]) - walk
        fx, fz = facing / np.linalg.norm(facing)
        across = (-fz * dx + fx * dz) / width
        depth = (fx * dx + fz * dz) / width
        shade = 1 - smoothstep(0.17, 1.15, np.hypot((across - 0.04) / 0.54, (depth - 0.06) / 0.28))
        canvas[under] *= 1 - shade[:, None] * np.array([0.26, 0.21, 0.17])

    # Two wheel ruts bend through the meadow and disappear at the mill door.
    lane = (x > 15) & (x < MILL[0] + 1.5) & (z > MILL[1] - 5) & (z < 2)
    lx, lz = x[lane], z[lane]
    bend = MILL[1] + 13.3 * (1 - smoothstep(5, 20, lx)) - 1.9 * np.exp(-((lx - 20) / 4.4) ** 2)
    broken = fbm(2.8 * lx, 4.0 * lz, seed=81, octaves=3)
    across = np.abs(lz - bend + 0.12 * broken)
    rut = 1 - smoothstep(0.14, 0.49, np.abs(across - 0.74) + 0.13 * broken)
    verge = 1 - smoothstep(0.84, 1.48, across)
    ends = smoothstep(15, 19, lx) * (1 - smoothstep(MILL[0] - 1, MILL[0] + 1.5, lx))
    wear = ends * (0.20 * verge + 0.76 * rut) * (0.86 + 0.14 * broken)
    grass = canvas[lane]
    # Let the existing field brushwork run through the warm, trampled grass.
    straw = np.array([0.76, 0.70, 0.57]) + (grass.mean(1) - 0.57)[:, None] * 0.45
    straw += 0.06 * broken[:, None]
    canvas[lane] = grass + (straw - grass) * wear[:, None]
    paint_river(canvas, x, z, walk)


def paint_river(canvas, x, z, walk):
    """Paint a bend between the walk and mill directly into the ground's log-polar texture."""
    along = z + 9
    mid = 10.1 - 0.42 * along + 0.033 * along**2 + 1.1 * np.sin((z + 13) / 5)
    slope = -0.42 + 0.066 * along + 0.22 * np.cos((z + 13) / 5)
    across = (x - mid) / np.sqrt(1 + slope**2)
    half_width = 3.15 + 0.55 * np.sin(z * 0.22)
    rows, cols = np.where((x > 2) & (np.abs(across) < half_width + 1.2))
    wx, wz = x[rows, cols], z[rows, cols]
    hollows = fbm(wx * 0.42, wz * 0.34, seed=91, octaves=3)
    broken_edge = fbm(wx * 2, wz * 2, seed=86)
    bank = half_width[rows, cols] - np.abs(across[rows, cols]) + 0.78 * hollows + 0.34 * broken_edge
    dx, dz = wx - walk[0], wz - walk[1]
    distance = np.hypot(dx, dz)
    azimuth = np.arctan2(dx, -dz)
    # Sample the painting's sky through irregular horizontal bands of reflected light.
    ripples = fbm(azimuth * 96, np.log(distance) * 160, seed=88, octaves=3)
    original = painting()
    sky = bilinear(original[:138], 80 + 405 * azimuth + 25 * ripples,
                   64 + 47 * np.sin(distance * 0.16) + 19 * ripples)
    foliage = original[285:325, 353:393].mean((0, 1))
    clumps = smoothstep(-0.27, 0.36, hollows)
    far_edge = (across[rows, cols] > 0) * (1 - smoothstep(0.05, 1.5 + 1.4 * clumps, bank + 0.24 * ripples))
    reflected_green = (0.20 + 0.34 * far_edge * clumps + 0.035 * ripples)[:, None]
    water = sky * (1 - reflected_green) + foliage * reflected_green
    water *= np.array([0.91, 0.96, 0.99])

    # Earth shows through in broken patches on the bank and under the shallows.
    grass = canvas[rows, cols]
    silt = original[455:482, 886:940].mean((0, 1)) + 0.05 * broken_edge[:, None]
    petals = smoothstep(0.06, 0.22, grass[:, 0] - np.maximum(grass[:, 1], grass[:, 2]))
    wet_edge = 1 - smoothstep(0.02, 0.48, -bank)
    grass += (silt - grass) * (petals * wet_edge)[:, None]
    earth = 0.58 * grass + 0.42 * silt
    patches = smoothstep(-0.30, 0.38, hollows + 0.55 * broken_edge)
    wear = patches * (1 - smoothstep(0.08, 0.28 + 0.48 * patches, -bank))
    grass += (earth - grass) * (0.64 * wear[:, None])
    shallows = (1 - smoothstep(0.02, 0.38 + 0.64 * patches, bank + 0.13 * ripples))
    water += (earth - water) * ((0.10 + 0.28 * patches) * shallows)[:, None]

    # Reflect the actual painted mill in this bend, breaking its outline into dabs.
    mill = painted_mill()
    h, w = mill.shape[:2]
    heading = np.asarray(MILL) - walk
    mill_distance = np.linalg.norm(heading)
    fx, fz = heading / mill_distance
    scale = mill_distance / np.maximum(0.01, dx * fx + dz * fz)
    u = w * (MILL_FOOT[0] + (-dx * fz + dz * fx) * scale / MILL_WIDTH) + 16 * ripples
    v = h * MILL_FOOT[1] - (scale - 1) * 2.7 * w / MILL_WIDTH + 5 * ripples
    reflection = bilinear(mill, u, v)
    ink = (reflection[:, 3] * ((u > 0) & (u < w) & (v > 0) & (v < h))
           * (0.42 + 0.26 * ripples))[:, None]
    water += (reflection[:, :3] - water) * ink

    canvas[rows, cols] = grass + (water - grass) * smoothstep(-0.13 - 0.08 * patches, 0.13, bank)[:, None]

    # In this atlas, a stroke across the ring reads horizontally from the walk.
    rng = np.random.default_rng(93)
    wet = np.flatnonzero(bank > 0.22)
    for k in rng.choice(wet, len(wet) // 37):
        light = original[rng.integers(12, 135), rng.integers(0, original.shape[1])]
        paint = light if rng.random() < 0.38 + 0.35 * clumps[k] else 0.64 * water[k] + 0.36 * foliage
        dab(canvas, cols[k], rows[k], rng.uniform(2, 10), rng.uniform(1.0, 3.1),
            rng.normal(0, 0.14), paint, rng.uniform(0.25, 0.60), rng)


def mill_sails():
    """Four timber spars carrying strips of linen, painted round the centre texel."""
    size = 176
    tile = np.zeros((size, size, 4), np.float32)
    rng = np.random.default_rng(41)
    timber = np.array([0.32, 0.29, 0.22])
    flax = np.array([0.81, 0.76, 0.64])
    for blade in range(4):
        angle = math.radians(-64 + 90 * blade)
        along = np.array([math.cos(angle), math.sin(angle)])
        across = np.array([-along[1], along[0]])

        def stroke(radius, offset, length, width, direction, colour):
            px, py = size / 2 + radius * along + offset * across
            dab(tile, px, py, length / 2, width / 2, direction, colour, 1, rng)

        # The cloth stops short of the hub. The ribs show through the flax.
        for radius in range(32, 77, 2):
            width = 8 + 4 * (radius - 32) / 44
            stroke(radius, width / 2, width + 2, 6, angle + math.pi / 2, flax * rng.uniform(0.87, 1.08))
        for radius in range(32, 78, 9):
            stroke(radius, 5, 14, 2.6, angle + math.pi / 2, timber * 1.35)
        stroke(40, 0, 81, 4.6, angle, timber)
        stroke(40, -0.8, 79, 1.6, angle, flax * 0.88)
    dab(tile, size / 2, size / 2, 4, 3.5, 0, timber, 1, rng)
    return tile


def painted_mill():
    """Put the sails and tower on the same piece of canvas, joined at the painted axle."""
    tower = Image.open(ART / 'mill.webp').convert('RGBA')
    w, h = tower.size
    sails = Image.fromarray((np.clip(mill_sails(), 0, 1) * 255).astype(np.uint8))
    rise = w // 2 - round(0.243 * h)
    mill = Image.new('RGBA', (w, h + rise))
    mill.alpha_composite(tower, (0, rise))
    mill.alpha_composite(sails.resize((w, w), Image.Resampling.LANCZOS), (round(0.482 * w) - w // 2, 0))
    return np.asarray(mill, np.float32) / 255


def field_trees(ridge_tree):
    """Fit boughs from the ridge tree into two different silhouettes."""
    tree = ridge_tree.copy()
    y, x = np.indices(tree.shape[:2])
    # Lift out the neighbouring bush on the lower right and fade the grass at the roots.
    tree[..., 3] *= 1 - smoothstep(149, 168, x) * smoothstep(192, 214, y)
    tree[..., 3] *= smoothstep(252, 233, y)
    tree[..., 3] *= 1 - smoothstep(0.45, 0.67, tree[..., :3].mean(2)) * smoothstep(163, 220, y)
    source = Image.fromarray((tree * 255).clip(0, 255).astype(np.uint8))
    # The outside of a lower bough becomes the new tree's left shoulder.
    crown = source.crop((107, 87, 218, 192)).transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    crown = crown.resize((98, 88), Image.Resampling.LANCZOS)
    result = Image.new('RGBA', (246, 246))
    result.alpha_composite(crown, (5, 78))
    result.alpha_composite(source.resize((214, 237), Image.Resampling.LANCZOS), (28, 4))
    tree = result.transpose(Image.Transpose.FLIP_LEFT_RIGHT)

    # Gather the lower foliage into a broader, shorter crown with its sunlit bough off centre.
    low = Image.new('RGBA', (228, 194))
    top = source.crop((38, 0, 167, 104)).resize((101, 70), Image.Resampling.LANCZOS)
    low.alpha_composite(top, (52, 7))
    low.alpha_composite(crown, (4, 46))
    low.alpha_composite(source.crop((0, 86, 218, 253)).resize((206, 149), Image.Resampling.LANCZOS), (19, 44))
    low = np.asarray(low, np.float32) / 255
    y, x = np.indices(low.shape[:2])
    low[..., 3] *= 1 - smoothstep(44, 18, x) * smoothstep(133, 177, y)
    return np.asarray(tree, np.float32) / 255, low


def pack_landmarks(atlas, ridge_tree):
    """Pack the meadow cutouts in the spare atlas space above the crest row."""
    rects = {}
    tree, low = field_trees(ridge_tree)
    for name, tile, x, y in [('tree', tree, 568, 8), ('low_tree', low, 414, 295), ('mill', painted_mill(), 848, 8)]:
        h, w = tile.shape[:2]
        atlas[y:y + h, x:x + w] = tile
        rects[name] = (x, y, w, h)

    sheet = Image.open(ART / 'grazing-sheep.webp').convert('RGBA')
    wool = np.asarray(sheet, np.float32) / 255
    # Keep the wool's brush marks, with cloud whites in the light and meadow grey in the shade.
    original = painting()
    cloud = original[65:110, 755:810].mean((0, 1))
    shade = original[306:327, 367:392].mean((0, 1))
    value = wool[..., :3] @ np.array([0.30, 0.55, 0.15])
    neutral = shade + (cloud - shade) * smoothstep(0.17, 0.85, value)[..., None]
    wool[..., :3] = 0.76 * neutral + 0.24 * wool[..., :3]
    x, y = 650, 300
    atlas[y:y + sheet.height, x:x + sheet.width] = wool
    cell = sheet.width // 3
    alpha = sheet.getchannel('A').point(lambda a: 255 if a > 12 else 0)
    for i, name in enumerate(('ewe_side', 'ewe_quarter', 'ewe_step')):
        left, top, right, bottom = alpha.crop((i * cell, 0, (i + 1) * cell, sheet.height)).getbbox()
        rects[name] = (x + i * cell + left - 2, y + top - 2, right - left + 4, bottom - top + 4)
    return rects


def side_cards(rects, walk):
    """Stand each cutout on its roots, hooves or doorstep, facing the walk."""
    def upright(foot, width, rect, root, flip=False):
        x, z = foot
        heading = np.array(foot) - walk
        distance = np.linalg.norm(heading)
        right = np.array([-heading[1], heading[0]]) / distance
        h = width * rect[3] / rect[2]
        root_x = 1 - root[0] if flip else root[0]
        cx, cz = foot + right * (0.5 - root_x) * width
        base = float(height(x, z)) - (1 - root[1]) * h
        return distance, cx, cz, base, right, width, h, rect, flip

    roots = {'tree': (0.59, 0.91), 'low_tree': (0.39, 0.94)}
    cards = [upright(foot, w, rects[name], roots[name], flip) for foot, w, name, flip in MEADOW_TREES]
    cards.extend(upright(foot, w, rects[name], (0.5, 0.95), flip) for foot, w, name, flip in FLOCK)
    cards.append(upright(MILL, MILL_WIDTH, rects['mill'], MILL_FOOT))
    return cards
