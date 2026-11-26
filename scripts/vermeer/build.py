"""Bake The Art of Painting into the museum's PSC1 scene and WebP textures."""

import sys
from itertools import groupby
from pathlib import Path

import numpy as np
from PIL import Image, ImageChops, ImageFilter

from atlas import pack_sprites
from chandelier import chandelier_mask
from extensions import chair_mask, curtain_mask, dark_cloth_mask, front_chair_mask
from foreground import PHOTO_SIZE, masks
from studio import build_studio
from walls import build_walls

HERE = Path(__file__).resolve().parent
ART = HERE / "art"
RES = HERE.parents[1] / "shared/src/commonMain/composeResources"
DRAW = RES / "drawable"
sys.path.insert(0, str(HERE.parent / "snap-the-whip"))
from scene import save

PW, PH = PHOTO_SIZE
CX, CY = PW / 2, PH / 2
# The two diagonals of the marble pavement give the horizon and focal length.
FOCAL = 2930
TILT = np.arctan((1096 - CY) / FOCAL)
COS, SIN = np.cos(TILT), np.sin(TILT)
EYE = 1.37
FRONT, REAR = -7.32, 2.15
LEFT, RIGHT, HEIGHT = -2.64, 3.89, 3.62
ROUTE = [(0, EYE, 0), (.035, EYE, -.47), (.09, EYE, -1.12)]
RESAMPLE = Image.Resampling.LANCZOS


def smooth(low, high, value):
    t = np.clip((value - low) / (high - low), 0, 1)
    return t * t * (3 - 2 * t)


def image8(values):
    return Image.fromarray(np.uint8(np.clip(values, 0, 255)))


def grade(im):
    """Bring the oil studies into the cooler daylight of the supplied photograph."""
    red, green, blue = im.convert("RGB").split()
    return Image.merge("RGB", (red.point(lambda x: .97*x + 6),
                               green.point(lambda x: 1.035*x + 7),
                               blue.point(lambda x: 1.07*x + 9)))


def texture(name, im):
    im.save(DRAW / f"museum_vermeer_{name}.webp", quality=92, method=6)


def xyz(x, y, z):
    return np.stack(np.broadcast_arrays(x, y, z), axis=-1)


def grid(cols, rows):
    return np.meshgrid(np.linspace(0, 1, cols + 1), np.linspace(0, 1, rows + 1))


def mesh(tex, points, uv):
    rows, cols = points.shape[:2]
    a = np.arange((rows - 1)*cols).reshape(rows - 1, cols)[:, :-1]
    triangles = np.stack((a, a+cols, a+1, a+1, a+cols, a+cols+1), axis=-1)
    return dict(kind=2, tex=tex, pos=points.reshape(-1, 3), uv=uv.reshape(-1, 2),
                w=np.ones(rows*cols), idx=triangles.reshape(-1, 3))


def project(x, y, z):
    """World point to the original photograph's pixel coordinates."""
    depth = np.maximum((y-EYE)*SIN - z*COS, .02)
    return CX + FOCAL*x/depth, CY - FOCAL*((y-EYE)*COS + z*SIN)/depth


def rays(px, py):
    ry = (CY-py)/FOCAL
    return (px-CX)/FOCAL, SIN + ry*COS, -COS + ry*SIN


def wide_xy(px, py):
    return 296 + .52*px, -3 + .503*py


def sample(im, x, y):
    """Bilinear sampling of a baked study, in pixel coordinates."""
    a = np.asarray(im)
    x = np.clip(x, 0, im.width-1)
    y = np.clip(y, 0, im.height-1)
    i, j = x.astype(int), y.astype(int)
    ii, jj = np.minimum(i+1, im.width-1), np.minimum(j+1, im.height-1)
    u, v = x-i, y-j
    if a.ndim == 3:
        u, v = u[..., None], v[..., None]
    return ((1-v)*((1-u)*a[j, i] + u*a[j, ii])
            + v*((1-u)*a[jj, i] + u*a[jj, ii]))


def clean_front(photo, covered):
    # Only the occluded paint comes from the empty-room study. The visible
    # map and plaster retain Vermeer's marks. The wider study finishes the wall.
    empty = grade(Image.open(ART / "empty-portrait.webp")).resize(PHOTO_SIZE, RESAMPLE)
    clean = Image.composite(empty, photo, covered)
    wide = grade(Image.open(ART / "empty-studio.webp"))
    # Register the map's two columns of city views across the crop boundary.
    wide_x = np.arange(wide.width)
    source_x = np.interp(wide_x, [0,170,620,1084,1162,1535], [0,170,615,1114,1196,1535])
    wide = image8(sample(wide, source_x[None,:], np.arange(wide.height)[:,None]))
    y, x = np.ogrid[:PH, :PW]
    edge = np.minimum(np.minimum(x, PW-1-x), np.minimum(y, PH-1-y))
    mask = image8(255*smooth(4, 54, edge))
    box = (854, 1011)
    inset = clean.resize(box, RESAMPLE)
    base = np.asarray(wide).astype(float)
    # Match the side lighting across both joins, retaining the new brushwork.
    for edge in (15,box[0]-16):
        study = np.asarray(inset)[3:, edge-8:edge+8].mean(axis=1)
        outside = base[:len(study), 296+edge-8:296+edge+8].mean(axis=1)
        delta = image8(128 + np.clip(study-outside,-50,50)[:,None,:])
        delta = np.asarray(delta.filter(ImageFilter.GaussianBlur(12))).astype(float)-128
        weight = np.exp(-((wide_x-296-edge)/173)**2)
        base[:len(delta)] += delta*weight[None,:,None]
    wide = image8(base)
    wide.paste(inset, (296, -3), mask.resize(box, RESAMPLE))
    return wide


def tile_coordinates():
    # Successive real intersections around the left foot of the painter's
    # stool. Fit the tile lattice on the floor itself, then carry it all round.
    ties = np.array([(773,1735,0,0), (906,1764,1,0), (791,1794,1,1),
                     (945,1828,2,1), (811,1864,2,2), (978,1903,3,2),
                     (838,1948,3,3)])
    dx, dy, dz = rays(ties[:,0], ties[:,1])
    t = -EYE/dy
    return np.linalg.lstsq(np.column_stack((dx*t, dz*t, np.ones(len(t)))),
                           ties[:,2:], rcond=None)[0]


def surfaces(photo, covered, studio, sitter_feet):
    # Bake both horizontal surfaces in plan. The floor uses the measured tile
    # lattice even under the furniture, so turning never changes its perspective.
    width, height = 1024, 1536
    materials = grade(Image.open(ART / "materials.webp"))
    split = materials.width//2
    marble = materials.crop((0, 0, split, materials.height))
    wood = materials.crop((split, 0, materials.width, materials.height))
    mapping = tile_coordinates()
    atlas = Image.new("RGB", (width*2, height))
    x = LEFT + (np.arange(width)[None, :] + .5)/width*(RIGHT-LEFT)
    # Broad window-light shadow under the dress, tight contact at each sole
    # and chair post. Both follow the same floor intersections as the figure.
    cx, _, cz = sitter_feet.mean(axis=0)
    sitter_shadows = [(cx+.23, cz+.08, .81, .30, .35)]
    for fx, _, fz in sitter_feet:
        sitter_shadows.append((fx+.07, fz+.02, .16, .06, .19))
        sitter_shadows.append((fx, fz, .08, .04, .31))
    for row in range(0, height, 64):
        v = (np.arange(row, min(row+64, height))[:, None]+.5)/height
        z = FRONT + v*(REAR-FRONT)
        a = x*mapping[0,0] + z*mapping[1,0] + mapping[2,0]
        b = x*mapping[0,1] + z*mapping[1,1] + mapping[2,1]
        # (0,0) is the crossing with a pale tile immediately downscreen.
        floor = sample(marble, ((a % 8)/8)*marble.width, ((b % 8)/8)*marble.height)
        light = 1.03 - .15*(x-LEFT)/(RIGHT-LEFT) - .07*v
        for cx, cz, rx, rz, strength in [
            (.61,-5.07,.57,.48,.35), (-.82,-5.34,.90,.50,.25),
            (-1.31,-4.47,.62,.39,.32), (1.50,-6.33,.45,.35,.20),
            (-.41,-3.34,.45,.31,.39),
            (3.19,-7.13,.29,.14,.34),        # Delft vase foot
            (3.73,-5.35,.22,.29,.25),        # standing girl's hem and shoes
            (3.52,-3.43,.32,.40,.24),        # silver ewer's table
            (LEFT+.29,(FRONT+REAR)/2,.29,.88,.22),  # window bench
            *sitter_shadows,
            (3.51,-1.41,.44,1.17,.35),       # artist's desk
        ]:
            light = light - strength*np.exp(-((x-cx)/rx)**2-((z-cz)/rz)**2)
        floor *= light[...,None]
        ceiling = sample(wood, (x-LEFT)/(RIGHT-LEFT)*(wood.width-1), v*(wood.height-1))
        for index, (level, base) in enumerate(((0, floor), (HEIGHT, ceiling))):
            px, py = project(x, level, z)
            if index == 0:
                edge = np.minimum(np.minimum(px, PW-1-px), np.minimum(py, PH-10-py))
                blend = smooth(0, 65, edge)*(1-sample(covered, px, py)/255)
                src = sample(photo, px, py)
            else:
                # Carry each painted beam right across the studio at the seam
                # with the map wall before it gives way to the overhead study.
                wx, wy = wide_xy(px, py)
                blend = smooth(0, 50, wy)*smooth(0, 60, np.minimum(wx, studio.width-wx))
                src = sample(studio, wx, np.minimum(wy, 97))
            atlas.paste(image8(base+(src-base)*blend[...,None]), (index*width, row))
    texture("surfaces", atlas)
    return width, height


def architecture(surface_size):
    layers = []
    width, height = surface_size
    u, v = grid(40, 50)
    for i, h in enumerate((0, HEIGHT)):
        points = xyz(LEFT+(RIGHT-LEFT)*u, h, FRONT+(REAR-FRONT)*v)
        uv = np.stack((i*width+.5+(width-1)*u, .5+(height-1)*v), axis=-1)
        layers.append(mesh(2, points, uv))

    u, v = grid(36, 22)
    front = xyz(LEFT+(RIGHT-LEFT)*u, HEIGHT*(1-v), FRONT)
    wx, wy = wide_xy(*project(*np.moveaxis(front, -1, 0)))
    layers.append(mesh(0, front, np.stack((wx, wy), axis=-1)))

    walls, wall_layers = build_walls(ART, LEFT, RIGHT, FRONT, REAR, HEIGHT, grade, mesh)
    texture("walls", walls)
    layers.extend(wall_layers)
    return layers


def rgba(im, alpha):
    result = im.copy()
    result.putalpha(alpha)
    return result


def donor(im, box, transform):
    """Sample a study in the photograph's coordinates, including outside its crop."""
    x0, y0, x1, y1 = box
    a, b, c, d, e, f = transform
    return im.transform((x1-x0, y1-y0), Image.Transform.AFFINE,
                        (a,b,a*x0+b*y0+c,d,e,d*x0+e*y0+f), Image.Resampling.BICUBIC)


def hidden_skirt(photo, mattes, box):
    # Fit each pleat to the visible hips and the space beneath the desktop.
    # Carry the adjacent blue pigment across the newly uncovered seam.
    cloth = Image.open(ART / "skirt.webp").convert("RGBA")
    alpha = np.asarray(cloth.getchannel("A")) > 140
    left = alpha.argmax(axis=1)
    right = cloth.width-1-alpha[:,::-1].argmax(axis=1)
    y, x = np.mgrid[box[1]:box[3], box[0]:box[2]]
    waist, floor = 1210, 1668
    low = np.interp(y, [waist,1280,1390,floor], [633,593,556,491])
    high = np.interp(y, [waist,1242,1294,1366,1510,floor], [883,898,898,889,889,883])
    row = np.clip((y-waist)/(floor-waist), 0, 1)*(cloth.height-1)
    col = left[row.astype(int)] + (x-low)/(high-low)*(right-left)[row.astype(int)]
    pixels = sample(cloth, col, row)
    pixels[:,:,:3] = pixels[:,:,:3] * [.92,.90,.79] + [14,12,7]
    bottom = PH-1-(np.asarray(mattes["model"])[::-1] > 200).argmax(axis=0)
    edge = bottom[x]-6
    near = np.asarray(photo)[edge,x]
    blend = (1-smooth(0,85,y-edge)) * (edge>waist)
    pixels[:,:,:3] += (near-pixels[:,:,:3])*blend[...,None]
    hidden = ImageChops.lighter(mattes["table"], mattes["curtain"]).crop(box)
    pixels[:,:,3] *= ((y>=waist) & (y<floor)) * (np.asarray(hidden)/255)
    return image8(pixels)


def foreground(photo, mattes):
    extended = grade(Image.open(ART / "extended.webp"))
    chair_front = front_chair_mask()
    cloth = dark_cloth_mask()
    separate = dict(mattes)
    separate["chair"] = chair_front
    separate["table"] = ImageChops.lighter(mattes["table"], cloth)
    separate["curtain"] = ImageChops.subtract(mattes["curtain"], ImageChops.lighter(chair_front, cloth))
    pieces = {}
    for name, mask in separate.items():
        bounds = mask.getbbox()
        pieces[name] = (bounds, rgba(photo.crop(bounds), mask.crop(bounds)))

    def patch(name, box, under):
        old_box, original = pieces[name]
        under.alpha_composite(original, (old_box[0]-box[0], old_box[1]-box[1]))
        pieces[name] = (box, under)

    # The hem shows in the slit between the desk and model after the camera
    # moves. Keep the photographed upper dress, face, hands, book and trumpet.
    box = (420, 795, 955, 1680)
    patch("model", box, hidden_skirt(photo, mattes, box))

    # Restore the draped end of the desk behind the tapestry. Outside the
    # photograph the study completes the left edge and its turned legs.
    box = (-185, 1085, 945, 1865)
    desk = Image.new("RGBA", (1130, 780))
    donor_desk = Image.open(ART / "table.webp").convert("RGBA").resize((802, 750), RESAMPLE)
    desk.paste(donor_desk, (0, 20))
    hidden = Image.new("L", desk.size, 255)
    hidden.paste(mattes["curtain"], (-box[0], -box[1]))
    desk.putalpha(ImageChops.multiply(desk.getchannel("A"), hidden))
    patch("table", box, desk)

    # The photograph ends at the fringe on the near leather chair's seat.
    # Restore its legs below that crop, with the feet planted on the marble.
    box = (35, 1110, 837, 2348)
    support = Image.new("RGBA", (box[2]-box[0], box[3]-box[1]))
    legs = Image.open(ART / "chair-legs.webp").convert("RGBA").resize((647,377), RESAMPLE)
    support.paste(legs, (181-box[0],1965-box[1]))
    bounds, chair = pieces["chair"]
    pieces["chair"] = (bounds, chair.crop((0,0,chair.width,2000-bounds[1])))
    patch("chair", box, support)

    # Finish the canvas behind the brush and sleeve with its actual bare linen.
    # The right chair's stile continues just beyond the photograph.
    box = (1180, 745, 1760, 1760)
    chair = donor(rgba(extended, chair_mask()), box, (.50,0,570.5,0,.515,-47))
    x = np.arange(box[0], box[2])
    outside = image8(np.broadcast_to(255*smooth(1622,1642,x), (chair.height, chair.width)))
    chair.putalpha(ImageChops.multiply(chair.getchannel("A"), outside))
    canvas = photo.crop((1481,1054,1504,1422)).resize((246,435), RESAMPLE)
    chair.paste(canvas, (1262-box[0],1009-box[1]))
    bounds, _ = pieces["easel"]
    linen = ImageChops.subtract(mattes["easel"], mattes["painter"].filter(ImageFilter.MaxFilter(13)))
    pieces["easel"] = (bounds, rgba(photo.crop(bounds), linen.crop(bounds)))
    patch("easel", box, chair)

    # The camera can see around the left edge of the original curtain. Its
    # missing outer folds, tie and foot come from the complete tapestry study.
    box = (-550, -180, 825, 2038)
    whole = donor(rgba(extended, curtain_mask()), box, (.516,.003,295,0,.503,-3))
    y, x = np.ogrid[box[1]:box[3], box[0]:box[2]]
    edge = np.maximum.reduce(np.broadcast_arrays(1-smooth(-2,24,x),
                                                  1-smooth(0,22,y), smooth(1980,2000,y)))
    edge *= 1 - smooth(178,198,x)*smooth(1960,1980,y)
    whole.putalpha(ImageChops.multiply(whole.getchannel("A"), image8(255*edge)))
    # The thin photographed frame edge beneath the curtain is outside the paint.
    cbox, curtain = pieces["curtain"]
    pieces["curtain"] = (cbox, curtain.crop((0,0,curtain.width,2000-cbox[1])))
    patch("curtain", box, whole)
    # The top edge of the photograph crops the hanging folds. Continue the
    # assembled cloth to the ceiling with matching paint at the cut.
    fabric = pieces["curtain"][1]
    top = fabric.crop((0,180,fabric.width,360)).transpose(Image.Transpose.FLIP_TOP_BOTTOM)
    fabric.paste(top, (0,0))
    return pieces


def object_mesh(name, box, atlas_bounds):
    x0, y0, x1, y1 = box
    u, v = grid(max(8, (x1-x0)//44), max(8, (y1-y0)//42))
    px, py = x0+(x1-x0)*u, y0+(y1-y0)*v
    dx, dy, dz = rays(px, py)
    # Distances are constrained by the feet along the measured floor. Give
    # the desktop and the painter's legs the depth visible in the photograph.
    if name == "curtain":
        depth = np.interp(py, [-180,0,1200,1780,2038], [5.27,5.12,4.73,4.61,4.27])
        # The brass tieback runs out to its hook on the wall behind the cloth.
        wx, wy = .516*px+.003*py+295, .503*py-3
        cord = (1-smooth(207,255,wx))*smooth(315,325,wy)*(1-smooth(431,451,wy))
        depth += (-FRONT-depth)*cord
    elif name == "chair":
        depth = np.interp(py, [1180,1785,1925,2000,2348], [3.6,3.60,3.27,3.27,3.26])
    elif name == "table":
        depth = np.interp(py, [1100,1275,1390,1550,1852], [6.2,6.02,5.30,5.31,5.30])
    elif name == "model":
        depth = 6.89
    elif name == "painter":
        depth = np.interp(py, [840,1510,1575,1735,1800,1926], [5.24,5.2,5.03,5.16,5.39,4.84])
    elif name == "easel":
        depth = 5.83 + .38*smooth(1498,1555,px)
    else:  # The chandelier hangs from the ceiling at its suspension ring.
        depth = 6.39
    t = depth/-dz
    t = np.minimum(t, EYE/np.maximum(-dy,.01))
    t = np.minimum(t, (HEIGHT-EYE)/np.maximum(dy,.01))
    positions = xyz(dx*t, EYE+dy*t, dz*t)
    ax, ay, w, h = atlas_bounds
    uv = np.stack((ax+u*w, ay+v*h), axis=-1)
    return mesh(3, positions, uv)


def figures(pieces):
    sprites = {name: sprite.resize((round(sprite.width*.66), round(sprite.height*.66)), RESAMPLE)
               for name, (_, sprite) in pieces.items()}
    atlas, layout = pack_sprites(sprites)
    texture("figures", atlas)
    return [object_mesh(key, pieces[key][0], layout[key])
            for key in ("model","easel","chandelier","painter","table","chair","curtain")]


def texture_batches(layers):
    # All the still meshes are two-sided. Keep their painter's order but
    # submit adjoining meshes on the same atlas in a single draw.
    batches = []
    for tex, group in groupby(layers, key=lambda layer: layer["tex"]):
        meshes = list(group)
        counts = [len(m["pos"]) for m in meshes]
        assert sum(counts) <= 32767  # EnterPainting reads signed short indices.
        offsets = np.cumsum([0] + counts[:-1])
        batch = {key: np.concatenate([m[key] for m in meshes]) for key in ("pos","uv","w")}
        indices = np.concatenate([m["idx"]+offset for m, offset in zip(meshes, offsets)])
        batches.append(dict(kind=2, tex=tex, idx=indices, **batch))
    return batches


def build():
    photo = Image.open(ART / "the-art-of-painting.jpg").convert("RGB")
    mattes = masks()
    mattes["chandelier"] = chandelier_mask()
    covered = Image.new("L", PHOTO_SIZE)
    for matte in mattes.values():
        covered = ImageChops.lighter(covered, matte)
    covered = covered.filter(ImageFilter.MaxFilter(11)).filter(ImageFilter.GaussianBlur(2))
    studio = clean_front(photo, covered)
    texture("front", studio)
    view_focal = .66 * 525 * FOCAL / PH
    fittings, fitting_layers, sitter_feet = build_studio(
        ART, ROUTE[-1], TILT, view_focal, FRONT, REAR, LEFT, RIGHT, mesh)
    layers = architecture(surfaces(photo, covered, studio, sitter_feet))
    texture("studio", fittings)
    layers.extend(fitting_layers)
    layers.extend(figures(foreground(photo, mattes)))
    camera = dict(hFov=np.degrees(2*np.arctan(CX/FOCAL)), tilt=np.degrees(TILT),
                  yawMax=180, pitchDown=89, pitchUp=87)
    save(RES / "files/museum_vermeer_scene.bin", camera, ROUTE, texture_batches(layers))


if __name__ == "__main__":
    build()
