"""The elm and the mill beyond the painting's edges, and their marks in the field."""
import math
from pathlib import Path

import numpy as np
from PIL import Image

from common import fbm, height, smoothstep
from trees import dab

ELM = (-14.0, -9.0)
MILL = (28.0, -14.0)
ART = Path(__file__).with_name('art')


def paint_ground(canvas, x, z):
    # Broken light under the elm. Keep the paint marks and poppies visible in the shade.
    under = (abs(x - ELM[0]) < 7) & (abs(z - ELM[1]) < 9)
    dx, dz = x[under] - ELM[0], z[under] - ELM[1]
    flecks = fbm(dx * 2, dz * 2, seed=73, octaves=3)
    crown = np.hypot((dx + 0.6) / 3.3, (dz + 2.1) / 4.6)
    shade = (1 - smoothstep(0.58, 1.1, crown + 0.27 * flecks)) * (0.78 + 0.22 * flecks)
    canvas[under] *= 1 - shade[:, None] * np.array([0.30, 0.23, 0.18])

    # Two wheel ruts bend through the meadow and disappear at the mill door.
    lane = (x > 4) & (x < MILL[0] + 1.5) & (z > MILL[1] - 5) & (z < 2)
    lx, lz = x[lane], z[lane]
    bend = MILL[1] + 13.3 * (1 - smoothstep(5, 20, lx)) - 1.9 * np.exp(-((lx - 20) / 4.4) ** 2)
    broken = fbm(2.8 * lx, 4.0 * lz, seed=81, octaves=3)
    across = np.abs(lz - bend + 0.12 * broken)
    rut = 1 - smoothstep(0.14, 0.49, np.abs(across - 0.74) + 0.13 * broken)
    verge = 1 - smoothstep(0.84, 1.48, across)
    ends = smoothstep(4, 8, lx) * (1 - smoothstep(MILL[0] - 1, MILL[0] + 1.5, lx))
    wear = ends * (0.20 * verge + 0.76 * rut) * (0.86 + 0.14 * broken)
    grass = canvas[lane]
    # Let the existing field brushwork run through the warm, trampled grass.
    straw = np.array([0.76, 0.70, 0.57]) + (grass.mean(1) - 0.57)[:, None] * 0.45
    straw += 0.06 * broken[:, None]
    canvas[lane] = grass + (straw - grass) * wear[:, None]


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


def pack_landmarks(atlas):
    """Pack the cutouts beside the original hill and figures, in the atlas's spare space."""
    rects = {}
    for name, x, y in [('elm', 568, 8), ('mill', 420, 376), ('sails', 700, 382)]:
        if name == 'sails':
            tile = mill_sails()
        else:
            tile = np.asarray(Image.open(ART / f'{name}.webp').convert('RGBA'), np.float32) / 255
        h, w = tile.shape[:2]
        atlas[y:y + h, x:x + w] = tile
        rects[name] = (x, y, w, h)
    return rects


def side_cards(rects, walk):
    """Stand each cutout on its painted root or doorstep, facing the walk."""
    def upright(foot, width, rect, root):
        x, z = foot
        heading = np.array(foot) - walk
        distance = np.linalg.norm(heading)
        right = np.array([-heading[1], heading[0]]) / distance
        h = width * rect[3] / rect[2]
        cx, cz = foot + right * (0.5 - root[0]) * width
        base = float(height(x, z)) - (1 - root[1]) * h
        return distance, cx, cz, base, right, width, h, rect, False

    elm = upright(ELM, 10.5, rects['elm'], (0.41, 0.96))
    mill = upright(MILL, 9.4, rects['mill'], (0.46, 0.96))
    distance, cx, cz, base, right, w, h, _, _ = mill
    # The axle nub in the mill painting is 48.2% across and 24.3% down.
    axle = np.array([cx, cz]) + right * (0.482 - 0.5) * w
    diameter = 9.4
    sails = (distance, *axle, base + 0.757 * h - diameter / 2, right, diameter, diameter, rects['sails'], False)
    return [elm, mill], sails
