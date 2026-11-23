"""The painting at the gallery's size, and which of it is sky, the far side of the field, the pasture and the two
boys: the boys from an outline traced round them and then fitted to the paint along its edge, the sky by its
light against the trees."""
import numpy as np
from PIL import Image, ImageDraw
from common import *

# The boys, traced clockwise from the top of the older boy's hat, in painting pixels. The bottom runs through
# the grass under them, which is cut softly.
BOYS = [
    (437, 148), (460, 150), (477, 157), (487, 167), (492, 180), (493, 197), (503, 205), (508, 208), (503, 215),
    (492, 220), (490, 230), (492, 237),
    # the younger boy's straw hat
    (500, 236), (513, 234), (533, 237), (547, 243), (557, 255), (570, 267), (587, 272), (598, 275), (601, 278),
    (590, 284), (573, 287), (560, 290),
    # his face and shirt
    (568, 298), (573, 310), (575, 320), (572, 328), (566, 336),
    (580, 337), (600, 336), (620, 340), (640, 350), (660, 360), (680, 372), (700, 385), (712, 394),
    # his trousers, shins and the far foot
    (725, 401), (740, 409), (760, 410), (777, 412), (783, 418), (800, 427), (817, 430), (833, 435), (843, 443),
    (850, 445), (860, 443), (870, 450), (873, 455),
    (890, 460), (913, 465), (930, 460), (937, 455), (947, 448), (953, 437), (960, 427), (970, 423), (977, 427),
    (983, 437), (985, 450), (983, 467), (980, 483), (973, 493), (967, 498), (953, 500), (940, 497),
    # through the grass under them
    (913, 493), (890, 489), (867, 490), (840, 492), (820, 492), (800, 492), (783, 494), (760, 497), (740, 500),
    (720, 499), (693, 501), (673, 503), (647, 503), (620, 504), (600, 503), (570, 503), (540, 503), (500, 502),
    (470, 503), (440, 504), (400, 504), (365, 502), (345, 500), (337, 497),
    # the older boy's back and shoulder
    (332, 487), (328, 473), (330, 457), (327, 440), (325, 427), (318, 423), (310, 420), (313, 407), (318, 395),
    (315, 387), (317, 373), (318, 357), (323, 340), (328, 327), (337, 313), (347, 300), (360, 290), (365, 283),
    (372, 273), (382, 267), (390, 262),
    # his hair under the brim, and the hat's left side
    (391, 250), (395, 240), (393, 231), (378, 227), (375, 220), (380, 207), (392, 190), (393, 173), (400, 162),
    (417, 152),
]
GRASS_LINE = 484      # below this the outline runs through grass and is cut softly, not fitted


def polygon_mask(points, ss=4):
    im = Image.new('L', (PW * ss, PH * ss), 0)
    ImageDraw.Draw(im).polygon([(x * ss, y * ss) for x, y in points], fill=255)
    return np.asarray(im.resize((PW, PH), Image.BOX)).astype(np.float32) / 255


def boys_alpha(p):
    """The outline, then each pixel within a few of its edge given to whichever side's paint it is nearer:
    the colour on either side taken from just inside and just outside, close by."""
    m = polygon_mask(BOYS)
    ys = np.mgrid[0:PH, 0:PW][0]
    inner = blur(m, 3.0) > 0.97       # well inside
    outer = blur(m, 3.0) < 0.03       # well outside
    def local_mean(core, s):
        w = blur(core.astype(np.float32), s)
        return blur(p * core[..., None], s) / np.maximum(w, 1e-4)[..., None], w
    fg, wf = local_mean(inner, 4.0)
    bg, wb = local_mean(outer, 4.0)
    df = ((p - fg) ** 2).sum(-1); db = ((p - bg) ** 2).sum(-1)
    side = db / np.maximum(df + db, 1e-6)
    side = smoothstep(0.3, 0.7, side)
    band = (blur(m, 2.0) > 0.02) & (blur(m, 2.0) < 0.98) & (wf > 0.05) & (wb > 0.05)
    a = np.where(band, side, m)
    # Never far from the outline either way.
    a = np.minimum(a, (blur(m, 2.5) > 0.01).astype(np.float32))
    a = np.maximum(a, (blur(m, 2.5) > 0.99).astype(np.float32))
    a = blur(a, 0.6)
    # Through the grass, a soft cut on the outline itself.
    soft = blur(m, 2.5)
    k = smoothstep(GRASS_LINE - 8, GRASS_LINE, ys)
    return (a * (1 - k) + soft * k).astype(np.float32)


def sky_alpha(p):
    """The sky: light, and over the tree line, connected down from the top."""
    ys = np.mgrid[0:PH, 0:PW][0].astype(np.float32)
    R, G, B = p[..., 0], p[..., 1], p[..., 2]
    lum = 0.3 * R + 0.55 * G + 0.15 * B
    warm_green = np.clip((G - B) - 0.18, 0, 1) * 4         # foliage is yellow-green; sky is not
    s = smoothstep(0.40, 0.52, lum) * (1 - np.clip(warm_green, 0, 1))
    s = s * (1 - smoothstep(186, 192, ys))
    s = np.where(ys < 90, 1.0, s)
    # Keep only what is joined to the open sky above, so a light leaf never lets the sky through.
    hard = s > 0.5
    reach = np.zeros_like(hard); reach[0] = hard[0]
    for y in range(1, PH):
        row = hard[y] & (reach[y - 1] | np.roll(reach[y - 1], 1) | np.roll(reach[y - 1], -1))
        # spread along the row within the sky
        for _ in range(3):
            row = row | (hard[y] & (np.roll(row, 1) | np.roll(row, -1)))
        reach[y] = row
        if y > 200: break
    s = s * blur(reach.astype(np.float32), 1.0).clip(0, 1) ** 0.5
    return s.astype(np.float32)


# Where the far side of the field ends and the pasture begins: the foot of the rail fence.
FENCE_FOOT = 207.0


if __name__ == '__main__':
    full = Image.open(f'{ART}/boys-in-a-pasture.jpg').convert('RGB')
    full.resize((PW, PH), Image.LANCZOS).save(f'{WORK}/painting.png')
    p = painting()
    ys = np.mgrid[0:PH, 0:PW][0].astype(np.float32)
    fig = boys_alpha(p)
    sky = sky_alpha(p) * (1 - fig)
    below = smoothstep(FENCE_FOOT - 0.5, FENCE_FOOT + 0.5, ys)
    ground = below * (1 - fig)
    band = np.clip(1 - fig - sky - ground, 0, 1)
    np.savez(f'{WORK}/regions.npz', sky=sky, band=band, ground=ground, fig=fig)
    col = np.array([[60, 120, 230], [230, 200, 40], [90, 160, 60], [240, 60, 40]], np.float32) / 255
    view = sky[..., None] * col[0] + band[..., None] * col[1] + ground[..., None] * col[2] + fig[..., None] * col[3]
    Image.fromarray((np.clip(view * 0.6 + p * 0.4, 0, 1) * 255).astype(np.uint8)).save(f'{WORK}/regions.png')
    cut = np.concatenate([p, fig[..., None]], -1)
    save_rgba_preview(cut[130:520, 290:1000], f'{WORK}/boys_cut.png', 2.0)
    print('sky', sky.mean().round(3), 'band', band.mean().round(3), 'ground', ground.mean().round(3), 'fig', fig.mean().round(3))
