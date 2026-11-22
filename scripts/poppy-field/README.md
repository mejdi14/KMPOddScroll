# Poppy Field, as a place

Builds the scene the gallery demo walks into from Monet's *Poppy Field*:
`files/museum_poppies_scene.bin` and the five `museum_poppies_*` textures it
draws with (`far`, `ground`, `paint`, `band`, `cards`).

```
python3 scripts/poppy-field/make.py                  # every step, then into the app's resources
python3 scripts/poppy-field/make.py sky pack install # one part again
```

Needs Python 3.9 or later with numpy and Pillow (built with WebP). The whole
run takes about four minutes, most of it the ground. Everything it makes goes
in `work/`, which git ignores; `POPPY_WORK` puts it somewhere else.

It starts from the first walk-in version of the scene and the painting before
its edges were worn, both taken out of git history at `80f1ea1`. Those give it
the hill the painting was fitted to, which part of the painting is sky,
ground, trees or figures, and the cut-out trees and figures.

## What it makes

- **The painting itself**, laid back over the sky and the ground from where it
  was painted (`paint`), so from the doorway the place is the painting to the
  pixel. Where it would be seen blown up from further in, it gives way to the
  ground's own paint over the walk: each vertex carries how much.
- **The field** (`ground`), one log-polar texture round the walk, so it is as
  sharp underfoot as it needs to be and never runs out. It is painted stroke
  by stroke in colours lifted from the painting's own pixels: grass flicks,
  poppy dabs, yellow and white flowers. Each stroke has a size and slant as
  the eye sees it from the walk, the way the painting's get smaller into the
  distance.
- **The sky** (`far`), one polar texture with the zenith in its middle: the
  painting's clouds carried on into cloud masses on a deck overhead, in broad
  strokes from its sky.
- **The tree line** (`band`), a ring 194 m out: the painting's own stretch
  with its house, and trees dabbed in its tree colours the rest of the way
  round.
- **The hill's trees and the figures** (`cards`): the painting's stretch of
  the hill as it was, its cypress, bushes and tall tree standing again along
  the crest where it bends round, and the two pairs of figures.

The hill is the one the painting was fitted to wherever the painting sees it.
Out of its sight the crest bends round on the left, and there is a low swell
off behind.

## Looking at it

The app can't be run from here, so `render.py` draws a scene the way
`EnterPainting` does: textured triangles with screen-space texture
coordinates, back to front. `views.py` renders the new scene at phone size,
each view a `route,yaw,pitch`:

```
python3 scripts/poppy-field/views.py look 1,0,0 1,-45,0 1,180,0 1,0,35
```

The picture lands in `work/look.png`.
