# Snap the Whip, as a place

Builds the scene the gallery demo walks into from Winslow Homer's *Snap the
Whip* (1872): the painting as it hangs, `museum_whip.jpg`, its scene file
`files/museum_whip_scene.bin` and the five `museum_whip_*` textures it draws
with (`far`, `ground`, `paint`, `band`, `cards`).

```
python3 scripts/snap-the-whip/make.py                   # every step, then into the app's resources
python3 scripts/snap-the-whip/make.py band pack install # one part again
```

Needs Python 3.9 or later with numpy and Pillow (built with WebP). The whole
run takes about four minutes, most of it the meadow. Everything it makes goes
in `work/`, which git ignores; `WHIP_WORK` puts it somewhere else.

It starts from [`art/snap-the-whip.jpg`](art/snap-the-whip.jpg), the
Wikimedia Commons scan of the Butler Institute's painting (public domain),
its frame edge trimmed and cut 3:2 like every painting in the gallery: 156 px
off the left, where there is only meadow, and 54 px off the right, short of
the head of the chain's cap.

## Where it was painted from

Homer stood low in the meadow. The horizon lies 355 px down the 1200 x 800
painting, at the far village, and it crosses the running boys at the chest, so
the eye is about 1.1 m up. With a 50° field of view the big boy in the blue
jacket, the nearest, is 5 m off, the tail of the chain 6.5 m, the boy fallen
on his hands and knees 10 m, and the schoolhouse, whose foot meets the meadow
about 390 px down, stands 40 m away. The meadow is flat. The walk in goes
forward and a little left, in among the boys, standing up as it goes.

## What it makes

- **Which of the painting is what** (`regions.py`). The seven boys of the
  chain and the fallen one are cut out from outlines traced round them, with
  the grass seen between their legs traced out again, then each pixel along
  the edge is fitted to whichever side's paint it is nearer. The sky is found
  by its light against the trees, joined down from the top, and never the
  schoolhouse's roof. Below the row where the meadow reaches the ring 40 m out
  everything else is meadow.
- **The painting itself**, laid back over the sky and the meadow from where it
  was painted (`paint`), so from the doorway the place is the painting to the
  pixel. Where the walk would see it blown up, it gives way to the meadow's own
  paint: each vertex carries how much.
- **The meadow** (`ground`), one log-polar texture round the walk, painted
  stroke by stroke in colours lifted from the painting: olive dabs, upright
  grass flicks, long dark blades and red-brown weed stalks, drifts of bramble
  leaves going red, heads of pale asters and Queen Anne's lace, and the
  painting's two grey stones where they lie. The schoolyard's beaten red earth
  lies in front of the schoolhouse and runs on past the painting's right edge.
  Under the painting's own foreground the grass's colour is laid first, not the
  painting's flowers blurred flat, so when the painting gives way the meadow's
  paint takes over cleanly.
- **The sky** (`far`), one polar texture with the zenith in its middle: the
  painting's grey teal sky (its blue under old varnish) and long cream clouds,
  drawn out east to west in rows, carried on overhead and thickening into a
  haze over the far hills.
- **The far side of the meadow** (`band`), a ring 40 m out. Where the painting
  looks, its own stretch, with what the boys hide rebuilt (`clean_plate`): the
  schoolhouse's clapboards carried on along their rows, and where a whole row
  is hidden copied from a whole number of boards above or below and relit, the
  door, its corner board, the window and shutters carried down their columns,
  the far fields and tree line mirrored in along their rows, and the woods
  behind the head of the chain down their columns from the leaves above.
  Elsewhere the far fields with their hedgerows, low hazy hills and the far
  tree line go on round the left, and the woods close in from behind round to
  the trees behind the schoolhouse: a back row of tall trees, a front row
  under it and brush along their foot, each crown of many clusters lit on its
  upper left.
- **The boys and the shade trees** (`cards`). The chain is cut from the
  full-size scan as one sheet, every point of it as far off as the feet of the
  boy it belongs to, blended across their joined hands, so walking in the big
  boy comes forward of the others and the tail falls back. The fallen boy
  kneels on a card of his own further off. Two elms stand out of the
  painting's sight, behind and to the left and right, and a pair of bushes on
  the left.

## Looking at it

The app can't be run from here, so `render.py` draws a scene the way
`EnterPainting` does: textured triangles with screen-space texture
coordinates, back to front. `views.py` renders it at phone size, each view a
`route,yaw,pitch`:

```
python3 scripts/snap-the-whip/views.py look 0,0,0 1,0,0 1,-70,0 1,180,0
```

The picture lands in `work/look.png`.
