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
  distance. On the right a river curves through the meadow. Its water picks up
  the original sky colours and a broken reflection of the mill. Short strokes
  of light lie across the water. Patches of exposed earth show through the
  shallows where they meet the meadow.
- **The sky** (`far`), one polar texture with the zenith in its middle: the
  painting's clouds carried on into cloud masses on a deck overhead, in broad
  strokes from its sky.
- **The tree line** (`band`), a ring 194 m out: the painting's own stretch
  with its house, and trees dabbed in its tree colours the rest of the way
  round.
- **Trees, figures, sheep and the mill** (`cards`). The hill carries the
  painting's trees and both pairs of figures. Four trees on the left take
  their trunks and boughs from the ridge tree. Three sheep graze in the
  distance on the right, beyond the river. Their [painted poses](art/grazing-sheep.webp)
  share the light of the original sky and the grey-green shadows of its foliage.
  On the far bank, a cart track leads to the stone mill. Its four linen sails
  and the [tower supplied by ImageGen](art/mill.webp) share one static cutout.

The hill is the one the painting was fitted to wherever the painting sees it.
Out of its sight the crest bends round on the left, and there is a low swell
off behind.

## Looking at it

The app can't be run from here, so `render.py` draws a scene the way
`EnterPainting` does: textured triangles with screen-space texture
coordinates, back to front. `views.py` renders the new scene at phone size,
each view a `route,yaw,pitch`:

```
python3 scripts/poppy-field/views.py look 1,0,0 1,-70,0 1,72,-5
```

The picture lands in `work/look.png`.

<details>
<summary>ImageGen prompt for the grazing sheep</summary>

Three grazing ewes, one in each third of a wide horizontal transparent sprite
sheet. Paint them as small figures in Monet’s Poppies at Argenteuil, with broad
broken oil strokes, warm ivory and flax wool, muted grey-violet shadows, dull
umber faces and legs. Afternoon light from upper left. Left ewe in side profile
facing right with nose down in the grass, middle ewe grazing in three-quarter
rear view with lowered head visible at its left side, right ewe facing left with
one foreleg slightly advanced and muzzle down. Give each a different natural
silhouette. Paint the whole animals from hoof to back, seen at meadow level. Use
simple masses, irregular dry-brush edges and little detail, as if each animal
were painted with a dozen small strokes. Genuine transparent background
everywhere, also between the legs. Generous transparent gutters between the
three animals and margin around the sheet. Animals only, no ground, grass,
shadows outside the animals, text, outlines or scenery.

</details>

<details>
<summary>Historical source prompt for the sheet containing the mill tower</summary>

Use case: historical-scene. Make one two-piece transparent sprite sheet for the
landscape just outside the frame of Claude Monet’s 1873 Poppies at Argenteuil.
Wide horizontal composition with 2 completely disconnected painted objects,
separated by a broad fully transparent gutter at the exact vertical midline.
Each object fully in frame, each centered within its own half, with a little
transparent padding. Left half: one characterful mature field elm from the
meadow, broad irregular lopsided crown, a visible lightly crooked forked warm
grey trunk, large broken clumps of leaves with openings of air; a few wispy
grass strokes at its roots. Right half: one small rural French 19th-century
stone tower windmill BODY ONLY with a squat round tapering warm ivory plaster
tower, small dark door, one shutter, a muted dusty terracotta conical cap and a
tiny dark wooden axle nub near the roof eave facing the viewer. The mill is a
base for animating separately painted sails, so absolutely NO sails, spars or
blades on it. A few sage grass marks and low scrub at the mill's base. Both seen
from ground level on a gentle summer afternoon in the Seine valley. Paint each
in the same broad, loose thin scumbled oil strokes and broken drybrush as
Monet's Poppies, with irregular brush ends and barely visible canvas grain
within the paint. Quiet grey olives, muted sage, warm straw green and umber tree
shadows, hazy warm greys and dusty rose for the tower. Light from upper left.
Simplify into beautiful masses, just as thick rough brushmarks suggest forms on
Monet's distant hillside. No intricate leaf detailing or microscopic texture, no
realism, no outlines, no clipart rendering. Let edges dissolve into sparse dry
paint. Absolutely transparent real alpha throughout the negative space between
branches, under the tree and surrounding both subjects. No sky, no backdrop, no
horizon line, no landscape slab under either, no isolated island of dirt, no
labels, no drop shadow.

</details>
