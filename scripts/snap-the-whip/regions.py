"""The painting at the gallery's size, and which of it is sky, the far side of the meadow, the meadow and the
boys: the boys from outlines traced round them and then fitted to the paint along their edges, the sky by its
light against the trees and the schoolhouse."""
import numpy as np
from PIL import Image, ImageDraw
from common import *

# The chain of seven boys, traced clockwise in painting pixels: along the tops of their caps, faces and arms
# from the tail on the left to the head of the chain on the right, down the right-hand boy, then back along
# their feet and legs, dipping up between them, to the tail again.
CHAIN = [
    # the tail: the boy in the white shirt, his cap, then the boy behind him in the dark jacket
    (281, 289), (300, 286), (315, 289), (322, 295), (325, 307), (327, 317), (326, 325), (322, 335), (325, 342),
    (333, 348), (340, 345), (355, 334), (352, 329), (350, 320), (350, 312), (339, 305), (345, 295), (360, 286),
    (375, 282), (385, 287), (397, 300), (400, 315), (405, 327), (415, 340), (420, 352), (424, 366),
    # the third boy's arm, face and cap
    (436, 361), (446, 356), (452, 350), (461, 343), (457, 336), (456, 327), (454, 316), (453, 307), (460, 293),
    (477, 284), (492, 286), (501, 290),
    # the big boy in the blue jacket: his face, his hat, his arm out to the right
    (504, 284), (506, 276), (501, 266), (510, 257), (520, 250), (536, 245), (552, 246), (564, 252), (572, 268),
    (579, 284), (570, 291), (573, 301), (579, 308), (590, 309), (607, 318), (630, 332), (647, 341), (670, 352),
    (683, 360), (696, 361), (707, 362),
    # the boy in the white shirt with red braces: his arm, face, cap and other arm
    (724, 356), (749, 350), (774, 343), (780, 342), (789, 338), (786, 329), (781, 320), (783, 314), (778, 304),
    (777, 299), (785, 289), (792, 284), (810, 283), (824, 288), (827, 301), (827, 314), (836, 320), (842, 328),
    (849, 328), (868, 328), (899, 334), (917, 340), (939, 345), (955, 346), (959, 344),
    # the boy with the feather in his hat
    (983, 342), (998, 335), (1018, 326), (1036, 318), (1046, 319), (1042, 312), (1037, 304), (1039, 297),
    (1029, 288), (1039, 274), (1056, 262), (1075, 258), (1092, 263), (1083, 274), (1095, 290), (1090, 300),
    (1098, 312),
    # the head of the chain, looking up, off the right edge
    (1110, 318), (1130, 316), (1145, 316), (1151, 305), (1154, 294), (1158, 287), (1161, 281), (1153, 276),
    (1165, 270), (1186, 271), (1203, 278), (1203, 331), (1197, 332), (1195, 347), (1187, 367), (1173, 383),
    (1167, 393), (1163, 400), (1160, 427), (1158, 450), (1157, 473), (1158, 500), (1163, 527), (1168, 538),
    (1162, 551), (1165, 575), (1162, 589),
    # back along the feet: the head's legs and the feathered boy's
    (1148, 589), (1145, 563), (1142, 545), (1136, 537), (1121, 538), (1112, 528), (1095, 507), (1083, 469),
    (1079, 456), (1078, 462), (1070, 480), (1058, 500), (1048, 516), (1036, 534), (1026, 550), (1015, 563), (1015, 584),
    (1004, 592), (968, 593), (962, 586), (965, 573), (968, 562), (985, 553), (998, 548), (1001, 534), (1006, 504),
    (1015, 472), (1027, 448), (1039, 419), (1048, 400), (1048, 382), (1048, 366),
    # under the joined arms to the boy with the braces, round his legs
    (1033, 365), (1018, 362), (1001, 362), (974, 368), (959, 368), (955, 367), (939, 364), (908, 361), (883, 361),
    (868, 373), (871, 389), (877, 401), (886, 417), (890, 433), (884, 453), (873, 481), (876, 501), (890, 524),
    (907, 547), (913, 559), (919, 573), (919, 582), (907, 586), (899, 582), (899, 573), (890, 559), (879, 547),
    (867, 530), (853, 507), (844, 481), (839, 461), (821, 473), (804, 496), (796, 519), (784, 556), (779, 570),
    (776, 588), (761, 595), (740, 596), (738, 590), (753, 576), (757, 556), (761, 524), (769, 493), (776, 467),
    (801, 436), (805, 414), (802, 401), (799, 386), (796, 379),
    # under his arm and the big boy's, down the big boy's side and round his kicked-up foot
    (774, 373), (752, 370), (727, 373), (708, 381), (692, 384), (676, 384), (653, 375), (630, 366), (616, 369),
    (613, 384), (624, 406), (630, 418), (628, 435), (631, 454), (646, 475), (660, 482), (696, 479), (703, 464),
    (710, 461), (724, 471), (744, 489), (747, 500), (739, 508), (714, 503), (696, 505), (660, 508), (639, 519),
    (632, 505), (628, 488), (615, 481), (594, 477), (570, 473), (545, 470), (525, 470), (516, 476), (512, 492),
    (514, 511), (519, 530), (523, 545),
    # the third boy's foot kicked up behind the big boy's leg, then the big boy's planted foot
    (546, 554), (564, 568), (575, 586), (564, 591), (546, 581), (527, 571), (524, 590), (518, 600), (517, 614),
    (525, 629), (518, 639), (481, 641), (478, 632), (495, 614), (502, 600), (492, 589), (487, 571), (481, 543),
    (481, 521), (482, 485), (476, 470), (458, 478),
    # the third boy's blue trousers and his foot
    (455, 495), (458, 515), (465, 540), (468, 560), (458, 572), (456, 593), (450, 605), (442, 613), (424, 620), (415, 616), (418, 607),
    (426, 597), (428, 580), (424, 568), (422, 543), (424, 510),
    # the dark boy's leg and the tail's two feet
    (400, 507), (371, 505), (356, 506), (354, 524), (356, 544), (357, 553), (337, 564), (314, 564), (311, 555),
    (323, 545), (326, 524), (326, 507), (314, 487), (301, 467), (297, 476), (289, 496), (277, 510), (274, 516),
    (277, 539), (286, 556), (284, 568), (263, 576), (246, 575), (245, 567), (256, 553), (260, 539), (259, 519),
    (251, 510), (250, 499), (247, 479), (249, 464), (249, 455), (253, 437), (265, 425), (276, 412), (276, 400),
    (275, 390), (277, 375), (280, 360), (282, 350), (287, 345), (290, 341), (284, 335), (280, 329), (277, 321),
    (277, 315), (275, 307), (275, 299),
]

# Grass seen between their legs, inside the outline: above the dark boy's calf, between him and the third boy, and
# between the two legs of the head of the chain.
HOLES = [
    [(360, 474), (372, 462), (390, 462), (412, 468), (428, 477), (432, 483), (428, 487), (420, 481), (405, 477),
     (388, 477), (372, 478)],
    [(412, 404), (448, 402), (452, 420), (453, 445), (450, 455), (442, 457), (433, 445), (425, 432), (418, 412)],
    [(1141, 470), (1150, 468), (1152, 500), (1150, 530), (1146, 545), (1141, 540), (1139, 505)],
]

# The boy fallen on his hands and knees on the left, traced clockwise from the top of his head.
CRAWLER = [
    (65, 389), (82, 391), (91, 400), (95, 405), (117, 405), (135, 407), (152, 409), (170, 411), (182, 417),
    (190, 427), (195, 440), (197, 452), (200, 442), (207, 427), (215, 420), (230, 424), (247, 430), (250, 435),
    (240, 440), (230, 445), (225, 452), (227, 465), (232, 480), (237, 492), (232, 497), (220, 500), (205, 499),
    (195, 495), (185, 500), (170, 500), (155, 495), (142, 487), (137, 475), (135, 470), (120, 475), (105, 470),
    (97, 462), (82, 460), (62, 470), (47, 480), (35, 489), (22, 490), (9, 487), (10, 481), (25, 479), (37, 475),
    (50, 465), (65, 452), (75, 442), (65, 431), (55, 425), (50, 415), (51, 400), (56, 392),
]

# The schoolhouse, roof and chimney, and its two walls: the long side with its window and door, and the gable end.
SCHOOL = [(572, 258), (660, 206), (846, 188), (846, 154), (872, 154), (872, 186), (893, 181), (1003, 249),
          (997, 397), (575, 392)]
LONG_WALL = [(582, 257), (725, 256), (725, 391), (582, 390)]
DOOR = [(725, 256), (776, 256), (776, 393), (725, 391)]          # the door and the corner beside it
GABLE = [(776, 256), (893, 184), (995, 250), (995, 397), (776, 393)]
WINDOW = [(606, 262), (714, 262), (714, 347), (606, 347)]   # the window and its two shutters


def polygon_mask(points, ss=4):
    im = Image.new('L', (PW * ss, PH * ss), 0)
    ImageDraw.Draw(im).polygon([(x * ss, y * ss) for x, y in points], fill=255)
    return np.asarray(im.resize((PW, PH), Image.BOX)).astype(np.float32) / 255


def fitted_alpha(p, m):
    """An outline's mask, then each pixel within a few of its edge given to whichever side's paint it is
    nearer: the colour on either side taken from just inside and just outside, close by."""
    inner = blur(m, 3.0) > 0.97
    outer = blur(m, 3.0) < 0.03
    def local_mean(core, s):
        w = blur(core.astype(np.float32), s)
        return blur(p * core[..., None], s) / np.maximum(w, 1e-4)[..., None], w
    fg, wf = local_mean(inner, 4.0)
    bg, wb = local_mean(outer, 4.0)
    df = ((p - fg) ** 2).sum(-1); db = ((p - bg) ** 2).sum(-1)
    side = smoothstep(0.3, 0.7, db / np.maximum(df + db, 1e-6))
    band = (blur(m, 2.0) > 0.02) & (blur(m, 2.0) < 0.98) & (wf > 0.05) & (wb > 0.05)
    a = np.where(band, side, m)
    # Never far from the outline either way.
    a = np.minimum(a, (blur(m, 2.5) > 0.01).astype(np.float32))
    a = np.maximum(a, (blur(m, 2.5) > 0.99).astype(np.float32))
    return blur(a, 0.6).astype(np.float32)


def chain_mask():
    m = polygon_mask(CHAIN)
    for h in HOLES:
        m = m * (1 - polygon_mask(h))
    return m


def figures_alpha(p):
    """The chain and the fallen boy, each fitted to the paint along its edge."""
    return fitted_alpha(p, chain_mask()), fitted_alpha(p, polygon_mask(CRAWLER))


def ground_row(x):
    """The painting row, at column x, where the meadow reaches the ring the far side is drawn on."""
    x = np.asarray(x, np.float64)
    ys = np.linspace(HORIZON + 1, HORIZON + 80, 1601)
    out = np.zeros_like(x)
    for i, xv in enumerate(x.ravel()):
        d = pixel_ray(np.full_like(ys, xv), ys)
        t = raycast(E0, d)
        r = np.hypot(d[:, 0] * t, d[:, 2] * t)
        out.ravel()[i] = ys[np.argmin(np.abs(np.where(np.isfinite(r), r, 1e9) - RB))]
    return out


def sky_alpha(p, school):
    """The sky: light, not the green of the trees, joined down from the top, and never the schoolhouse."""
    R, G, B = p[..., 0], p[..., 1], p[..., 2]
    # The sky darkens to a grey teal over the trees on the right, under old varnish; the trees are darker still.
    lum = blur(0.3 * R + 0.55 * G + 0.15 * B, 1.2)
    leafy = np.clip((blur(G - B, 1.2) - 0.07) * 8, 0, 1) * smoothstep(0.42, 0.3, lum)   # foliage is yellower
    s = smoothstep(0.26, 0.32, lum) * (1 - leafy) * (1 - school)
    ys = np.mgrid[0:PH, 0:PW][0]
    s = np.where(ys < 120, 1.0, s)
    s = s * (1 - smoothstep(HORIZON - 30, HORIZON - 22, ys))
    # Keep only what is joined to the open sky above, so a light leaf never lets the sky through.
    hard = s > 0.5
    reach = np.zeros_like(hard); reach[0] = hard[0]
    for y in range(1, PH):
        row = hard[y] & (reach[y - 1] | np.roll(reach[y - 1], 1) | np.roll(reach[y - 1], -1))
        for _ in range(3):
            row = row | (hard[y] & (np.roll(row, 1) | np.roll(row, -1)))
        reach[y] = row
        if y > HORIZON: break
    s = s * blur(reach.astype(np.float32), 1.0).clip(0, 1) ** 0.5
    return s.astype(np.float32)


if __name__ == '__main__':
    full = Image.open(f'{ART}/snap-the-whip.jpg').convert('RGB')
    full.resize((PW, PH), Image.LANCZOS).save(f'{WORK}/painting.png')
    p = painting()
    ys = np.mgrid[0:PH, 0:PW][0].astype(np.float32)
    chain, crawler = figures_alpha(p)
    fig = np.maximum(chain, crawler)
    school = polygon_mask(SCHOOL)
    sky = sky_alpha(p, school) * (1 - fig)
    gr = ground_row(np.arange(PW) + 0.5)
    below = smoothstep(gr[None, :] - 0.5, gr[None, :] + 0.5, ys)
    ground = below * (1 - fig)
    band = np.clip(1 - fig - sky - ground, 0, 1)
    np.savez(f'{WORK}/regions.npz', sky=sky, band=band, ground=ground, fig=fig, chain=chain, crawler=crawler, school=school, ground_row=gr.astype(np.float32),
             long_wall=polygon_mask(LONG_WALL), door=polygon_mask(DOOR), gable=polygon_mask(GABLE))
    col = np.array([[60, 120, 230], [230, 200, 40], [90, 160, 60], [240, 60, 40]], np.float32) / 255
    view = sky[..., None] * col[0] + band[..., None] * col[1] + ground[..., None] * col[2] + fig[..., None] * col[3]
    Image.fromarray((np.clip(view * 0.6 + p * 0.4, 0, 1) * 255).astype(np.uint8)).save(f'{WORK}/regions.png')
    cut = np.concatenate([p, fig[..., None]], -1)
    save_rgba_preview(cut[230:660, 0:1200], f'{WORK}/boys_cut.png', 1.0)
    print('sky', sky.mean().round(3), 'band', band.mean().round(3), 'ground', ground.mean().round(3), 'fig', fig.mean().round(3))
