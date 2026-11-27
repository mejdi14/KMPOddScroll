"""Extract the painted chessmen and restore the board behind their silhouettes.

Usage: python3 scripts/vermeer/chess_layers.py path/to/painting.jpg
Requires Pillow and NumPy. The source must have the same framing as the traced
508 × 604 reproduction. Coordinates are in that reproduction's pixel space.
"""

import argparse
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter


REFERENCE = (508, 604)
# Fit to the light/dark transitions in the exposed board, going from a1 to a8,
# h8 and h1. The renderer uses the same corners in algebraic grid order.
BOARD = ((259.8, 341.3), (361.6, 352.9), (322.8, 410.3), (197.0, 387.5))
# (foot, traced outline). Piece boundaries follow the paint, including its
# soft edges and the shared contours where two chessmen partly cover each other.
PIECES = {
    "light_left_back": ((246, 351), (
        (240, 333), (243, 332), (244, 329.5), (248, 329.5), (250, 332),
        (252, 332), (252.5, 335), (250.5, 339), (249, 342), (247.5, 343),
        (249, 345), (251.5, 346), (251, 348), (252.5, 350), (252, 351.5),
        (248, 353), (242, 352.5), (240, 350.5), (241.5, 348), (243, 347),
        (241.5, 345), (243.5, 343), (242, 339.5), (239.5, 337),
    )),
    "light_left_front": ((248, 369), (
        (244, 352.8), (248, 353), (252, 351.5), (253.5, 354), (254.5, 358),
        (252, 361.5), (252.5, 364), (255, 365.5), (255, 368), (252, 370),
        (247, 371), (242, 370), (240, 368.5), (241.5, 365), (242.5, 362),
        (241.5, 359.5), (241, 356), (242, 354),
    )),
    "dark_left_back": ((258, 350), (
        (255, 312), (256, 309), (258, 309), (258.5, 311), (257, 313),
        (262, 313.5), (262, 315), (260, 318), (258, 319), (259, 321),
        (259, 324), (261.5, 326), (260, 327), (261.5, 329), (261, 330.5),
        (264, 331), (265, 335), (263, 341), (262, 346), (263, 350),
        (260, 352), (252.5, 351.5), (251.5, 349), (252, 346), (251.5, 343),
        (252, 339), (253, 337), (251.5, 334), (253.5, 332), (255.5, 331),
        (253.5, 329), (254, 327), (255.5, 326), (253, 324), (254.5, 322),
        (253, 320), (254.5, 318), (251, 317.5), (252, 315),
    )),
    "dark_middle_left": ((270, 353), (
        (268, 333), (269, 332), (271, 333), (271.5, 335), (270.5, 336.5),
        (272.5, 337), (273.5, 339), (272.5, 341.5), (272.5, 346),
        (275, 350), (274.5, 352.5), (271, 354), (266.5, 353.5),
        (265, 352.5), (266.5, 350), (267, 346), (266, 343.5), (267, 341),
        (266, 338), (264.5, 337), (265, 335.5), (268, 335.5),
    )),
    "dark_middle_right": ((280, 353), (
        (277, 334), (279.5, 334), (281, 336), (280, 338), (283, 338.5),
        (284, 341), (281.5, 344), (282, 348.5), (285, 351), (284.5, 354),
        (281, 355), (276, 354.5), (274.5, 353), (275, 350.5), (277.5, 349),
        (278, 345), (276.5, 343), (277.5, 340), (276, 338), (276, 336),
    )),
    "light_middle_back": ((290, 342), (
        (286, 323), (287, 324.5), (290, 325), (292, 327.5), (295, 328.5),
        (295.5, 333.5), (294, 337), (295, 339.5), (294, 342),
        (290, 343), (286, 342.5), (285, 340.5), (287, 338), (289, 335.5),
        (287, 332.5), (284, 332), (283.5, 329), (285, 327.5),
    )),
    "light_middle_center": ((289, 358), (
        (286, 342.5), (290, 343), (294, 342), (295, 344), (297, 345.5),
        (297, 348.5), (295, 350.5), (294, 352.5), (296, 354), (296, 356),
        (292, 358), (286, 357.5), (283.5, 355.5), (285, 353),
        (286, 351), (284.5, 348.5), (285, 345),
    )),
    "light_middle_front": ((287, 373), (
        (286, 362), (289, 362), (291.5, 364.5), (292, 367), (290.5, 369),
        (291.5, 371), (292, 373), (289.5, 375), (284.5, 374.5),
        (283, 373), (284, 371), (285.5, 370), (283.5, 367), (284, 364),
    )),
    "dark_right_back": ((324, 351), (
        (320.5, 333), (322.5, 332), (325, 332.5), (327.5, 335),
        (327, 337.5), (326.5, 339.5), (327.5, 342), (327, 345),
        (328.5, 348), (329, 351), (326.5, 352.5), (321.5, 352),
        (320, 350.5), (321.5, 347), (322, 344), (320.5, 341), (321.5, 339),
        (319.5, 336.5),
    )),
    "light_right_back": ((334, 360), (
        (331, 343), (334, 342.5), (336, 344), (336.5, 348), (336, 351),
        (337.5, 353), (337, 355), (339.5, 357), (340, 359.5),
        (337, 361), (332, 362), (329, 360), (329, 357.5), (331, 355.5),
        (330, 353), (331, 351), (329.5, 348), (329.5, 345.5),
    )),
    "light_right_front": ((334, 383), (
        (332, 362), (337, 361), (339, 363), (341, 365.5), (340.5, 369),
        (339, 372), (339, 375), (342, 378), (342, 381), (339, 383.5),
        (334, 384), (329, 383), (327.5, 381), (328.5, 378), (331, 375),
        (331.5, 372), (329.5, 370), (328.5, 367), (330, 363.5),
    )),
}


# Parts of a single painted piece are traced separately where the finial joins
# its lower body. The published puzzle identifies eight men on the board.
SPRITES = {
    "e8": ("light_right_front", "light_right_back"),
    "a6": ("dark_right_back",),
    "e5": ("light_middle_front",),
    "b4": ("light_middle_center", "light_middle_back"),
    "b3": ("dark_middle_right",),
    "b2": ("dark_middle_left",),
    "e2": ("light_left_front", "light_left_back"),
    "b1": ("dark_left_back",),
}


def homography(source, target):
    rows, values = [], []
    for (u, v), (x, y) in zip(source, target):
        rows.extend(((u, v, 1, 0, 0, 0, -x*u, -x*v),
                     (0, 0, 0, u, v, 1, -y*u, -y*v)))
        values.extend((x, y))
    return np.append(np.linalg.solve(rows, values), 1).reshape((3, 3))


def transform(matrix, points):
    homogeneous = np.einsum("ij,kj->ik", np.column_stack((points, np.ones(len(points)))), matrix)
    return homogeneous[:, :2] / homogeneous[:, 2:]


def texture_restoration(rgb, holes, scale):
    """Sample the same local point in nearby unobscured, same-color squares."""
    y, x = np.nonzero(holes)
    image_to_board = homography(BOARD, ((0, 0), (8, 0), (8, 8), (0, 8)))
    board_to_image = np.linalg.inv(image_to_board)
    board = transform(image_to_board, np.column_stack((x + .5, y + .5)) / scale)
    valid = ((board >= 0) & (board < 8)).all(axis=1)
    positions = board[valid]
    samples = np.zeros((len(positions), 3), dtype=float)
    weights = np.zeros(len(positions), dtype=float)
    # These cells are unobscured by the cup, pieces, or board edge.
    donors = ((7, 0), (4, 1), (7, 1), (4, 2), (5, 2), (7, 2), (2, 3),
              (4, 3), (5, 3), (2, 4), (4, 4), (5, 4), (3, 5), (4, 5),
              (5, 5), (6, 5), (1, 6), (2, 6), (3, 6), (4, 6), (5, 6),
              (0, 7), (2, 7), (3, 7), (4, 7))
    cells = np.floor(positions).astype(int)
    for column, row in donors:
        target_cell = np.array([column, row])
        sample = transform(board_to_image, positions % 1 + target_cell) * scale
        sx, sy = sample.astype(int).T
        distance = np.sum((cells - target_cell) ** 2, axis=1)
        same_color = (cells.sum(axis=1) - column - row) % 2 == 0
        weight = (same_color & ~holes[sy, sx]) / (1 + distance)**2
        samples += rgb[sy, sx] * weight[:, None]
        weights += weight
    available = weights > 0
    selected_y, selected_x = y[valid][available], x[valid][available]
    texture = samples[available] / weights[available, None]

    # Continue the near left wooden trim through the masked span beside it.
    trim = (board[:, 0] >= -.42) & (board[:, 0] < 0) & (board[:, 1] >= 0) & (board[:, 1] < 8)
    trim_points = board[trim].copy()
    trim_points[:, 1] = np.clip(trim_points[:, 1], 5, 7.5)
    tx, ty = (transform(board_to_image, trim_points) * scale).astype(int).T
    return (np.append(selected_y, y[trim]), np.append(selected_x, x[trim]),
            np.vstack((texture, rgb[ty, tx])))


def restore(source, masks, scale):
    """Diffuse surrounding paint into off-board gaps and patch board pixels."""
    merged = Image.fromarray(np.max(masks, axis=0))
    # Dilate by one reference pixel to remove color spill from the cut edges.
    hole_mask = merged.point(lambda opacity: 255 if opacity > 20 else 0)
    radius = max(1, round(scale.min()))
    hole_mask = hole_mask.filter(ImageFilter.MaxFilter(2 * radius + 1))
    # Fill narrow gaps between contours where individual edge fringes meet.
    closing = 4 * radius + 1
    hole_mask = hole_mask.filter(ImageFilter.MaxFilter(closing)).filter(ImageFilter.MinFilter(closing))
    box = hole_mask.getbbox()
    margin = round(8 * scale.max())
    x0, y0, x1, y1 = box
    x0, y0, x1, y1 = x0 - margin, y0 - margin, x1 + margin, y1 + margin
    box = (x0, y0, x1, y1)
    original = np.asarray(source)
    holes = np.asarray(hole_mask) > 0
    patch = original[y0:y1, x0:x1].astype(float)
    local_holes = holes[y0:y1, x0:x1]
    patch[local_holes] = np.mean(patch[~local_holes], axis=0)
    sy, sx, texture = texture_restoration(original, holes, scale)
    diffusion = local_holes.copy()
    diffusion[sy - y0, sx - x0] = False
    for _ in range(round(160 * scale.max())):
        north = np.roll(patch, 1, axis=0)
        south = np.roll(patch, -1, axis=0)
        east = np.roll(patch, 1, axis=1)
        west = np.roll(patch, -1, axis=1)
        patch[diffusion] = ((north + south + east + west) / 4)[diffusion]
    patch[sy - y0, sx - x0] = texture
    replacement = Image.fromarray(np.rint(patch).clip(0, 255).astype(np.uint8))
    plate = source.copy()
    plate.paste(replacement, box, hole_mask.crop(box))
    return plate


def extract(source):
    scale = np.array(source.size) / REFERENCE
    outlines = {}
    for name, (_, outline) in PIECES.items():
        # Supersample the outlines so the original edge pixels survive with
        # fractional alpha and the cut follows the brushwork at any resolution.
        supersampling = 4
        mask = Image.new("L", tuple(length * supersampling for length in source.size))
        polygon = [tuple(point) for point in np.array(outline) * scale * supersampling]
        ImageDraw.Draw(mask).polygon(polygon, fill=255)
        mask = mask.resize(source.size, Image.Resampling.BOX)
        outlines[name] = np.array(mask)

    masks = [np.minimum(sum(outlines[part].astype(np.uint16) for part in parts), 255)
             .astype(np.uint8) for parts in SPRITES.values()]

    plate = restore(source, masks, scale)
    # Remove the reconstructed background's contribution from translucent
    # pixels. Replacing the sprites at rest reconstructs the supplied artwork.
    original = np.asarray(source).astype(float)
    background = np.asarray(plate).astype(float)
    atlas = Image.new("RGBA", source.size)
    cursor_x, cursor_y, shelf_height = 0, 0, 0
    for (name, parts), mask in zip(SPRITES.items(), masks):
        foot, _ = PIECES[parts[0]]
        alpha_mask = Image.fromarray(mask)
        box = alpha_mask.getbbox()
        x0, y0, x1, y1 = box
        opacity = mask[y0:y1, x0:x1, None] / 255
        foreground = background[y0:y1, x0:x1] + (
            original[y0:y1, x0:x1] - background[y0:y1, x0:x1]
        ) / np.maximum(opacity, 1 / 255)
        sprite = Image.fromarray(np.rint(foreground).clip(0, 255).astype(np.uint8))
        sprite.putalpha(alpha_mask.crop(box))
        width, height = sprite.size
        if cursor_x + width > atlas.width:
            cursor_x, cursor_y, shelf_height = 0, cursor_y + shelf_height + 2, 0
        atlas.paste(sprite, (cursor_x, cursor_y))
        print(f"{name:22} crop={(cursor_x, cursor_y, width, height)!s:22} "
              f"source={(x0, y0)!s:13} foot={tuple((np.array(foot) * scale).tolist())}")
        cursor_x += width + 2
        shelf_height = max(shelf_height, height)
    return plate, atlas.crop(atlas.getbbox())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("painting", type=Path)
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[2] /
                        "shared/src/commonMain/composeResources/drawable")
    args = parser.parse_args()

    with Image.open(args.painting) as image:
        plate, atlas = extract(image.convert("RGB"))
    plate.save(args.output / "museum_chess_plate.webp", lossless=True)
    atlas.save(args.output / "museum_chess_pieces.png", optimize=True)


if __name__ == "__main__":
    main()
