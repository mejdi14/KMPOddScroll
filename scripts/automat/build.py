"""Bake the compact Automat room into the museum's textured PSC1 mesh format."""

from pathlib import Path
import sys

import numpy as np
from PIL import Image, ImageEnhance, ImageFilter

from foreground import left_chair_mask, mask
from seating import masks as seating_masks

SCRIPT_DIR = Path(__file__).resolve().parent
DRAWABLES = SCRIPT_DIR.parents[1] / "shared/src/commonMain/composeResources/drawable"

# The landscape scenes share this PSC1 writer.
sys.path.insert(0, str(SCRIPT_DIR.parent / "snap-the-whip"))
from scene import save

ART = SCRIPT_DIR / "art"
PREFIX = "museum_automat_"
LEFT, RIGHT = -2.08, 2.10
FRONT, REAR = -2.52, 1.90
HEIGHT, EYE = 2.64, 1.37
HFOV, TILT = 64.5, -4.0
COS, SIN = np.cos(np.radians(TILT)), np.sin(np.radians(TILT))
# Original canvas registered in the 1426 x 1103 extension, in its pixel units.
ORIGIN = (189.5, 134.0)
SCALE = .475
CX, CY = ORIGIN[0] + 1000 * SCALE, ORIGIN[1] + 777 * SCALE
FOCAL = 1000 * SCALE / np.tan(np.radians(HFOV / 2))
TABLE_BOX = (518, 452, 1207, 1066)
SEAT_BOXES = [(76, 510, 601, 878), (977, 498, 1348, 696)]
SEAT_ROWS = [620, 996]
LEFT_FOOT = (755, 807, 862, 920, 949, 890)
REAR_FOOT = (919, 912)
RIGHT_FOOT = (726, 702, 678, 653, 650)


def grade_wall(artwork):
    # Lift the midtones, tapering the adjustment to zero at black and white.
    curve = []
    for level in range(256):
        tone = level / 255
        lift = 64 * tone**2 * (1 - tone)
        curve.append(round(level + lift))
    lifted = artwork.point(curve * 3)
    return ImageEnhance.Color(lifted).enhance(1.10)


def texture(name, image):
    image.save(DRAWABLES / f"{PREFIX}{name}.webp", quality=93, method=6)


def mesh(tex, points, uv):
    """A regular patch of two-sided triangles; UVs are in texture pixels."""
    rows, cols, _ = points.shape
    a = np.arange((rows - 1) * cols).reshape(rows - 1, cols)[:, :-1]
    indices = np.stack((a, a + cols, a + 1, a + 1, a + cols, a + cols + 1), axis=-1)
    return dict(kind=2, tex=tex, pos=points.reshape(-1, 3), uv=uv.reshape(-1, 2),
                w=np.ones(rows * cols), idx=indices.reshape(-1, 3))


def grid(nx, ny):
    return np.meshgrid(np.linspace(0, 1, nx + 1), np.linspace(0, 1, ny + 1))


def xyz(x, y, z):
    return np.stack(np.broadcast_arrays(x, y, z), axis=-1)


def front_uv(points):
    """Project the room into the outpaint from the precise museum entry pose."""
    x, y, z = np.moveaxis(points, -1, 0)
    y = y - EYE
    depth = y * SIN - z * COS
    return np.stack((CX + FOCAL * x / depth, CY - FOCAL * (y * COS + z * SIN) / depth), axis=-1)


def smooth(low, high, values):
    t = np.clip((values - low) / (high - low), 0, 1)
    return t * t * (3 - 2 * t)


def bake_front(furniture):
    extended = Image.open(ART / "extended-front.webp").convert("RGB")
    empty = Image.open(ART / "empty-front.webp").convert("RGB").resize(extended.size, Image.Resampling.LANCZOS)
    original = Image.open(DRAWABLES / "museum_automat.jpg").convert("RGB")
    inset = original.resize((950, 738), Image.Resampling.LANCZOS)
    # Keep Hopper's paint inside the frame and feather only its outermost strokes.
    y, x = np.indices((inset.height, inset.width))
    edge = np.minimum(np.minimum(x, inset.width - 1 - x), np.minimum(y, inset.height - 1 - y))
    extended.paste(inset, (190, 134), Image.fromarray((255 * smooth(1, 15, edge)).astype("uint8")))
    silhouette = mask()
    table = extended.copy()
    table.putalpha(silhouette)
    furniture.paste(table.crop(TABLE_BOX), (0, 0))

    # Where parallax uncovers the sitter, draw the repainted sill, skirting and floor.
    under = silhouette.filter(ImageFilter.MaxFilter(11)).filter(ImageFilter.GaussianBlur(2))
    front = Image.composite(empty, extended, under)
    # Dissolve the floor extension into the common slab at the end of its UVs.
    y = np.arange(front.height)[:, None]
    alpha = np.broadcast_to(1 - smooth(964, 1061, y), (front.height, front.width))
    front.putalpha(Image.fromarray((255 * alpha).astype("uint8")))
    texture("front", front)


def bake_surfaces():
    # Sample real painted surfaces into a continuous, non-repeating patch.
    # Remove the large shadows belonging to the donor view before relighting it.
    right = Image.open(ART / "window-tables.webp").convert("RGB")
    rear = grade_wall(Image.open(ART / "coffee-counter.webp").convert("RGB"))
    atlas = Image.new("RGB", (768, 1674))
    rng = np.random.default_rng(1927)
    y, x = np.indices((768, 768))
    illumination = np.full((768, 768), .95)
    for ly in (170, 576):
        for lx in (200, 557):
            illumination += .07 * np.exp(-((x - lx)**2 + (y - ly)**2) / 23000)
    for row, patch, base_color in [
        (0, right.crop((641, 741, 951, 1051)), [194, 204, 201]),
        (768, rear.crop((890, 3, 1050, 166)), [181, 177, 153]),
    ]:
        pigment = np.asarray(patch, dtype=float) - np.asarray(patch.filter(ImageFilter.GaussianBlur(9)))
        surface = np.zeros((896, 896, 3))
        weights = np.zeros((896, 896))
        taper = np.hanning(128)
        blend = np.outer(taper, taper)
        for top in range(0, 769, 64):
            for left in range(0, 769, 64):
                py = rng.integers(0, patch.height - 127)
                px = rng.integers(0, patch.width - 127)
                sample = pigment[py:py + 128, px:px + 128]
                surface[top:top + 128, left:left + 128] += sample * blend[:, :, None]
                weights[top:top + 128, left:left + 128] += blend
        strokes = surface[64:832, 64:832] / weights[64:832, 64:832, None]
        lit = (base_color + .75 * strokes) * illumination[:, :, None]
        atlas.paste(Image.fromarray(lit.clip(0, 255).astype("uint8")), (0, row))
    atlas.paste(rear.crop((482, 0, 685, 138)), (0, 1536))
    texture("surfaces", atlas)


def right_floor_line(px):
    return np.interp(px, np.linspace(76, 1374, len(RIGHT_FOOT)), RIGHT_FOOT)


def wall_texture(name, image, crop, bottom, apron):
    # Only the foreground edge of the painted floor apron dissolves. The wall
    # and its junction with the floor stay fully opaque, including at corners.
    x = np.arange(image.width)
    base = np.interp(x, np.linspace(*crop, len(bottom)), bottom)
    y = np.arange(image.height)[:, None]
    alpha = 1 - smooth(base + .16 * apron, base + .95 * apron, y)
    image.putalpha(Image.fromarray((255 * alpha).astype("uint8")))
    texture(name, image)


def prepare_seating(furniture):
    # Pack both seating groups beside the front table in the same transparent
    # atlas. Behind them, continue the adjacent sill, panels and skirting.
    right = grade_wall(Image.open(ART / "window-tables.webp").convert("RGB"))
    y, x = np.indices((right.height, right.width))
    rgb = np.asarray(right)
    # Sample the clear middle bay. Bend the sample with the baseboard so its
    # wall/floor joint remains continuous across both empty patches.
    donor_x = 656 + np.abs((x + 47) % 520 - 260)
    delta = right_floor_line(donor_x) - right_floor_line(x)
    donor_y = np.rint(y + delta * smooth(506, 630, y)).astype(int).clip(0, right.height - 1)
    empty = Image.fromarray(rgb[donor_y, donor_x])
    clean = right.copy()
    for alpha, box, row in zip(seating_masks(), SEAT_BOXES, SEAT_ROWS):
        cutout = right.copy()
        cutout.putalpha(alpha)
        furniture.paste(cutout.crop(box), (0, row))
        under = alpha.filter(ImageFilter.MaxFilter(13)).filter(ImageFilter.GaussianBlur(3))
        clean.paste(empty, mask=under)
    wall_texture("right", clean, (76, 1374), RIGHT_FOOT, 68)
    # An unrelated foreground chair enters the edge of the rear study.
    # Carry the adjoining stretch of lower counter behind that crop.
    rear = grade_wall(Image.open(ART / "coffee-counter.webp").convert("RGB"))
    strip = rear.crop((610, 744, 901, 950))
    rear.paste(strip, (0, 744))
    wall_texture("rear", rear, (0, 1447), REAR_FOOT, 30)


def room_face(tex, corners, uv_corners, nx, ny):
    u, v = grid(nx, ny)
    def interpolate(c):
        a, b, d, e = np.array(c)
        top = a + (b - a) * u[..., None]
        bottom = d + (e - d) * u[..., None]
        return top + (bottom - top) * v[..., None]
    return mesh(tex, interpolate(corners), interpolate(uv_corners))


def horizontal(y, v0, v1):
    corners = [(LEFT, y, FRONT), (RIGHT, y, FRONT), (LEFT, y, REAR), (RIGHT, y, REAR)]
    return room_face(4, corners, [(1, v0), (767, v0), (1, v1), (767, v1)], 21, 21)


def front_wall():
    u, v = grid(32, 20)
    points = xyz(LEFT + (RIGHT - LEFT) * u, HEIGHT * (1 - v), FRONT)
    return mesh(0, points, front_uv(points))


def front_floor():
    u, v = grid(28, 14)
    # This section has the radiator's and table's painted contact shadows.
    points = xyz(LEFT + (RIGHT - LEFT) * u, 0, FRONT + .95 * v)
    return mesh(0, points, front_uv(points))


def wall_points(start, end, u, wall_y, stand_off=1):
    # Below the painted baseboard the same ray meets the floor first.
    depth = np.minimum(stand_off, EYE / np.maximum(EYE - wall_y, EYE))
    x = start[0] + (end[0] - start[0]) * u
    z = start[1] + (end[1] - start[1]) * u
    return xyz(x * depth, EYE + (wall_y - EYE) * depth, z * depth)


def side_wall(tex, start, end, crop, top, bottom, apron):
    # Include an exact mesh row along the wall/floor intersection.
    cols = np.linspace(0, 1, 37)
    rows = np.concatenate([np.linspace(0, 1, 24), np.linspace(1, 2, 7)[1:]])
    u, v = np.meshgrid(cols, rows)
    px = crop[0] + (crop[1] - crop[0]) * u
    ceiling_px = np.interp(u, [0, 1], top)
    foot_px = np.interp(u, np.linspace(0, 1, len(bottom)), bottom)
    py = ceiling_px + np.minimum(1, v) * (foot_px - ceiling_px) + np.maximum(0, v - 1) * apron
    wall_y = HEIGHT * (foot_px - py) / (foot_px - ceiling_px)
    return mesh(tex, wall_points(start, end, u, wall_y), np.stack((px, py), axis=-1))


def contact_shadows(furniture):
    # Small pools of cool shadow under the window seats tie the painted feet
    # to the linoleum. Draw them together, after the wall-floor aprons.
    y, x = np.indices((128, 128))
    falloff = np.maximum(0, 1 - ((x - 63.5) / 63)**2 - ((y - 63.5) / 63)**2)
    stain = Image.new("RGB", (128, 128), (52, 68, 84))
    stain.putalpha(Image.fromarray((83 * falloff**2).astype("uint8")))
    furniture.paste(stain, (552, 628))
    uv = [(553, 629), (679, 629), (553, 755), (679, 755)]
    patches = []
    for box, foot, centers in [(SEAT_BOXES[0], 857, (175, 350, 513)),
                                (SEAT_BOXES[1], 683, (1026, 1150, 1286))]:
        for px in centers:
            t = (px - 76) / (1374 - 76)
            wall_foot = right_floor_line(px)
            scale = EYE / (EYE + (foot - wall_foot) * HEIGHT / (wall_foot - (145 + 5 * t)))
            cx, cz = RIGHT * scale, (FRONT + (REAR - FRONT) * t) * scale
            rx, rz = .28, (box[2] - box[0]) / 1450
            corners = [(cx - rx, .001, cz - rz), (cx + rx, .001, cz - rz),
                       (cx - rx, .001, cz + rz), (cx + rx, .001, cz + rz)]
            patches.append(room_face(5, corners, uv, 4, 4))
    return dict(kind=2, tex=5,
                pos=np.concatenate([p["pos"] for p in patches]),
                uv=np.concatenate([p["uv"] for p in patches]),
                w=np.concatenate([p["w"] for p in patches]),
                idx=np.concatenate([p["idx"] + i * len(p["pos"]) for i, p in enumerate(patches)]))


def window_seats(box, row, feet):
    u, v = grid(31, 23)
    x0, y0, x1, y1 = box
    px, py = x0 + (x1 - x0) * u, y0 + (y1 - y0) * v
    t = (px - 76) / (1374 - 76)
    foot = right_floor_line(px)
    ceiling = 145 + 5 * t
    height_per_px = HEIGHT / (foot - ceiling)
    stand_off = EYE / (EYE + (feet - foot) * height_per_px)
    points = wall_points((RIGHT, FRONT), (RIGHT, REAR), t, (foot - py) * height_per_px, stand_off)
    return mesh(5, points, np.stack((px - x0, py - y0 + row), axis=-1))


def foreground():
    x0, y0, x1, y1 = TABLE_BOX
    u, v = grid(56, 49)
    px = x0 + (x1 - x0) * u
    py = y0 + (y1 - y0) * v
    # The woman is seated behind the table. The marble slopes towards the
    # camera in projection, with its near lip in front of the central pedestal.
    metres = np.interp(py, [450, 647, 660, 733, 755, 792, 990, 1032, 1066],
                       [1.99, 1.99, 1.99, 1.58, 1.58, 1.80, 1.80, 1.69, 1.62])
    chair = np.asarray(left_chair_mask().filter(ImageFilter.GaussianBlur(8))) / 255
    left_weight = chair[np.rint(py).astype(int), np.rint(px).astype(int)]
    metres += (1.66 - metres) * left_weight
    right_weight = smooth(995, 1040, px)
    metres += (1.65 - metres) * right_weight
    # Let all of the feet land on the shared linoleum.
    ry = (CY - py) / FOCAL
    dy, dz = SIN + ry * COS, -COS + ry * SIN
    scale = np.minimum(-metres / dz, EYE / np.maximum(-dy, .01))
    points = xyz((px - CX) / FOCAL * scale, EYE + scale * dy, scale * dz)
    return mesh(5, points, np.stack((px - x0, py - y0), axis=-1))


def pendant(x, z):
    # Revolve the painted milk-glass bowl and hanging rod from the rear study.
    angle = np.linspace(0, 2 * np.pi, 25)
    radius = np.array([.012, .012, .048, .126, .157, .15, .112, .048, .002])[:, None]
    y = HEIGHT - np.array([0, .14, .16, .174, .187, .212, .251, .272, .274])[:, None]
    points = xyz(x + radius * np.cos(angle), y, z + radius * np.sin(angle))
    # Every meridian samples the hand-painted edge-to-center shading of the same bowl.
    vx = 100 + 440 * radius * np.cos(angle)
    vy = 1536 + np.array([2, 47, 57, 70, 80, 94, 113, 125, 129])[:, None]
    return mesh(4, points, np.stack(np.broadcast_arrays(vx, vy), axis=-1))


def build():
    left = grade_wall(Image.open(ART / "food-compartments.webp").convert("RGB"))
    wall_texture("left", left, (25, 974), LEFT_FOOT, 100)
    furniture = Image.new("RGBA", (696, 1196))
    bake_front(furniture)
    prepare_seating(furniture)
    shadow_mesh = contact_shadows(furniture)
    texture("table", furniture)
    bake_surfaces()
    layers = [
        horizontal(0, 1, 766), horizontal(HEIGHT, 769, 1534),
        front_floor(), front_wall(),
        # The door and radiator are already present on the original front.
        side_wall(1, (LEFT, REAR), (LEFT, FRONT), (25, 974), (182, 176),
                  LEFT_FOOT, 100),
        side_wall(2, (RIGHT, REAR), (LEFT, REAR), (0, 1447), (153, 153), REAR_FOOT, 30),
        side_wall(3, (RIGHT, FRONT), (RIGHT, REAR), (76, 1374), (145, 150),
                  RIGHT_FOOT, 68),
        shadow_mesh,
        window_seats(SEAT_BOXES[1], SEAT_ROWS[1], 683),
        window_seats(SEAT_BOXES[0], SEAT_ROWS[0], 857),
    ]
    for z in (1.02, -1.02):
        for x in (-.88, .88):
            layers.append(pendant(x, z))
    layers.append(foreground())
    route = [(0, EYE, 0), (.03, EYE, -.21), (.08, EYE, -.44)]
    save(DRAWABLES.parent / "files" / f"{PREFIX}scene.bin",
         dict(hFov=HFOV, tilt=TILT, yawMax=180, pitchDown=83, pitchUp=89), route, layers)


if __name__ == "__main__":
    build()
