"""Bake the three wall elevations and their projecting architectural details."""

import numpy as np
from PIL import Image, ImageDraw, ImageFilter


REFERENCE_WIDTH = 780  # Coordinates of the elevation tracings below.
WALL_WIDTH = 1500
ATLAS_LIMIT = 2048
RESAMPLE = Image.Resampling.LANCZOS


class _Wall:
    def __init__(self, start, end, inward, height, bottom, texture, mesh):
        self.start = np.asarray(start)
        self.end = np.asarray(end)
        self.inward = np.asarray(inward)
        self.height = height
        self.bottom = bottom
        self.texture = texture
        self.mesh = mesh

    def quad(self, xs, ys, depth, texture=None):
        x, y = np.meshgrid(xs, ys)
        u, v = x / REFERENCE_WIDTH, y / self.bottom
        return self.surface(x, y, depth, u, v, texture or self.texture)

    def surface(self, x, y, depth, u, v, texture):
        along = x[..., None] / REFERENCE_WIDTH
        depth = np.broadcast_to(depth, x.shape)[..., None]
        plan = self.start + along * (self.end - self.start) + depth * self.inward
        points = np.stack((plan[..., 0], self.height * (1 - y / self.bottom),
                           plan[..., 1]), axis=-1)
        tx, ty, width, height = texture
        uv = np.stack((tx + .5 + (width - 1) * u, ty + .5 + (height - 1) * v), axis=-1)
        return self.mesh(1, points, uv)

    def extrusion(self, p, q, back, front, texture):
        """Join a traced edge to the surface behind it."""
        u, v = np.meshgrid(np.linspace(0, 1, 12), np.linspace(0, 1, 4))
        x = p[0] + u * (q[0] - p[0])
        y = p[1] + u * (q[1] - p[1])
        return self.surface(x, y, back + v * (front - back), u, v, texture)


def _study(source, width=WALL_WIDTH):
    return source.resize((width, round(source.height * width / source.width)), RESAMPLE)


def _crop(source, box):
    scale = source.width / REFERENCE_WIDTH
    return source.crop(tuple(round(coordinate * scale) for coordinate in box))


def _cutout(source, box, outlines):
    cut = _crop(source, box).convert("RGBA")
    scale = cut.width / (box[2] - box[0])
    alpha = Image.new("L", (cut.width * 2, cut.height * 2))
    draw = ImageDraw.Draw(alpha)
    for outline in outlines:
        draw.polygon([(round((x - box[0]) * scale * 2), round((y - box[1]) * scale * 2))
                      for x, y in outline], fill=255)
    cut.putalpha(alpha.resize(cut.size, RESAMPLE))
    return cut


def _underpaint(source, box, donor_x, tiled=False):
    """Continue the plaster and baseboard behind an extracted furnishing."""
    a = np.asarray(source).copy()
    scale = source.width / REFERENCE_WIDTH
    x0, y0, x1, y1 = (round(coordinate * scale) for coordinate in box)
    patch = a[y0:y1, x0:x1].astype(float)
    x = np.arange(x0, x1) / scale
    y = np.arange(y0, y1)[:, None] / scale
    donor = np.rint(donor_x(x) * scale).astype(int)
    fill = a[y0:y1, donor].astype(float)
    # Keep the donor's brush texture while matching the light on this wall.
    blurred = np.asarray(source.filter(ImageFilter.GaussianBlur(12)), dtype=float)
    right_tone = blurred[y0:y1, x1 - 4]
    left_tone = blurred[y0:y1, x0 + 4] if x0 else right_tone * .93
    across = np.linspace(0, 1, x.size)[None, :, None]
    tone = left_tone[:, None, :] * (1 - across) + right_tone[:, None, :] * across
    start_of_trim = 333 if tiled else 298
    plaster = np.clip(fill - blurred[y0:y1, donor] + tone, 0, 255)
    fill = np.where((y < start_of_trim)[..., None], plaster, fill)
    if tiled:
        # Repeat three blue-and-white tiles from the area beside the chest.
        tile_x = np.rint((252 + (x - 252) % 54) * scale).astype(int)
        fill = np.where((y >= start_of_trim)[..., None], a[y0:y1, tile_x], fill)

    left_edge = np.clip((x - box[0]) / 22, 0, 1) if x0 else np.ones_like(x)
    right_edge = np.clip((box[2] - x) / 31, 0, 1)
    top_edge = np.clip((y - box[1]) / 28, 0, 1)
    opacity = np.minimum(np.minimum(left_edge, right_edge), top_edge)[..., None]
    opacity = opacity**2 * (3 - 2 * opacity)
    a[y0:y1, x0:x1] = np.round(patch * (1 - opacity) + fill * opacity).astype("uint8")
    return Image.fromarray(a)


def _window_wall(wall):
    xs, ys = np.meshgrid(
        np.unique(np.r_[np.linspace(0, 780, 38), 49, 60, 72, 88, 220, 226, 239, 264,
                        523, 537, 547, 558, 690, 698, 720, 737]),
        np.unique(np.r_[np.linspace(0, wall.bottom, 18), 24, 39, 232, 240, 246, 251, 256, 260]),
    )
    depth = np.zeros(xs.shape)
    for x1, x2, x3, x4 in ((60, 88, 226, 239), (537, 558, 698, 720)):
        opening = np.interp(xs, (x1, x2, x3, x4), (0, 1, 1, 0), left=0, right=0)
        recess = np.interp(ys, (24, 39, 232, 246), (0, .27, .27, 0), left=0, right=0)
        depth -= opening * recess
    for x1, x2, x3, x4 in ((49, 60, 250, 264), (523, 537, 724, 737)):
        ledge = np.interp(xs, (x1, x2, x3, x4), (0, 1, 1, 0), left=0, right=0)
        profile = np.interp(ys, (240, 246, 251, 256, 260), (0, .09, .17, .13, 0), left=0, right=0)
        depth += ledge * profile
    return wall.surface(xs, ys, depth, xs / REFERENCE_WIDTH, ys / wall.bottom, wall.texture)


def _chest(wall, source, pack):
    box = (53, 260, 250, 377)
    chest = _cutout(source, box, [
        [(55, 281), (61, 277), (68, 277), (240, 276), (247, 281),
         (246, 288), (242, 289), (242, 360), (238, 365), (242, 371),
         (242, 376), (226, 377), (222, 372), (224, 363), (75, 363),
         (73, 374), (70, 377), (57, 377), (55, 369), (58, 361), (57, 288)],
        [(60, 277), (62, 271), (68, 268), (83, 264), (102, 264),
         (119, 265), (125, 270), (122, 276), (111, 280), (76, 281)],
    ])
    panel = pack(chest)
    side = pack(_crop(source, (79, 299, 131, 343)))
    top = pack(_crop(source, (126, 278, 237, 283)))
    foot = pack(_crop(source, (57, 364, 70, 376)))
    offset = .45
    ground = wall.bottom

    def level(y):
        return box[1] + (y - box[1]) * (ground - box[1]) / (box[3] - box[1])

    return [
        wall.extrusion((57, level(287)), (57, level(362)), .01, offset, side),
        wall.extrusion((241, level(287)), (241, level(362)), .01, offset, side),
        wall.extrusion((57, level(283)), (244, level(283)), .01, offset, top),
        wall.extrusion((57, level(363)), (57, ground), .04, offset, foot),
        wall.extrusion((241, level(363)), (241, ground), .04, offset, foot),
        _sprite(wall, (box[0], box[1], box[2], ground), offset, panel),
    ]


def _sprite(wall, box, depth, texture):
    u, v = np.meshgrid(np.linspace(0, 1, 15), np.linspace(0, 1, 10))
    x = box[0] + u * (box[2] - box[0])
    y = box[1] + v * (box[3] - box[1])
    return wall.surface(x, y, depth, u, v, texture)


# Position of the approved frame in the window elevation. The width preserves
# the proportions of the supplied chess picture once the wall is in 3D.
CHESS_FRAME = (317, 54, 463, 232.5)


def _frame_shadow(windows):
    x0, y0, x1, y1 = CHESS_FRAME
    scale = windows.width / REFERENCE_WIDTH
    shadow = Image.new("L", windows.size)
    ImageDraw.Draw(shadow).rectangle(
        [(round((x0 + 3)*scale), round((y0 + 3)*scale)),
         (round((x1 + 3)*scale), round((y1 + 3)*scale))], fill=80,
    )
    return Image.composite(Image.new("RGB", windows.size, (24, 21, 17)), windows,
                           shadow.filter(ImageFilter.GaussianBlur(3*scale)))


def _chess_frame(wall, art, pack):
    frame = Image.open(art / "chess-players.webp")
    face = pack(frame)
    edge = pack(frame.crop((1, 20, 4, 52)))
    x0, y0, x1, y1 = CHESS_FRAME
    corners = [(x0, y0), (x1, y0), (x1, y1), (x0, y1)]
    sides = [wall.extrusion(a, b, 0, .047, edge)
             for a, b in zip(corners, corners[1:] + corners[:1])]
    return sides + [_sprite(wall, CHESS_FRAME, .047, face)]


def build_walls(art, left, right, front, rear, height, grade, mesh):
    """Return the packed wall texture and its PSC1 texture-1 layers."""
    windows, door, workbench = [
        _study(grade(Image.open(art / f"{name}.webp")))
        for name in ("windows", "door", "workbench")
    ]
    atlas = Image.new("RGBA", (ATLAS_LIMIT, ATLAS_LIMIT))
    wall_y = prop_y = 2

    def paste_wall(study, bottom):
        nonlocal wall_y
        study = _crop(study, (0, 0, 780, bottom)).convert("RGBA")
        atlas.paste(study, (2, wall_y))
        region = (2, wall_y, study.width, study.height)
        wall_y += study.height + 4
        return region

    def paste_detail(study):
        nonlocal prop_y
        x = WALL_WIDTH + 6
        atlas.paste(study, (x, prop_y))
        region = (x, prop_y, study.width, study.height)
        prop_y += study.height + 4
        assert x + study.width + 2 <= ATLAS_LIMIT and prop_y <= ATLAS_LIMIT
        return region

    left_wall = _Wall((left, rear), (left, front), (1, 0), height, 311,
                      paste_wall(_frame_shadow(windows), 311), mesh)
    door_wall = _underpaint(door, (0, 237, 277, 389),
                            lambda x: 250 + (x / 4) % 55, tiled=True)
    rear_wall = _Wall((right, rear), (left, rear), (0, -1), height, 372,
                      paste_wall(door_wall, 372), mesh)
    bench_wall = _underpaint(workbench, (363, 135, 635, 312), lambda x: x - 254)
    right_wall = _Wall((right, front), (right, rear), (-1, 0), height, 307,
                        paste_wall(bench_wall, 307), mesh)

    walls = [
        _window_wall(left_wall),
        rear_wall.quad(np.linspace(0, 780, 41), np.linspace(0, rear_wall.bottom, 23), 0),
        right_wall.quad(np.linspace(0, 780, 41), np.linspace(0, right_wall.bottom, 23), 0),
    ]
    walls.extend(_chest(rear_wall, door, paste_detail))
    walls.extend(_chess_frame(left_wall, art, paste_detail))
    assert wall_y <= ATLAS_LIMIT
    return atlas.crop((0, 0, ATLAS_LIMIT, max(wall_y, prop_y))), walls
