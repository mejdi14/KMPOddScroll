# Boys in a Pasture, as a place

Builds the scene the gallery demo walks into from Winslow Homer's *Boys in a
Pasture* (1874): the painting as it hangs, `museum_pasture.jpg`, its scene
file `files/museum_pasture_scene.bin` and the five `museum_pasture_*` textures
it draws with (`far`, `ground`, `paint`, `band`, `cards`).

```
python3 scripts/boys-in-a-pasture/make.py                   # every step, then into the app's resources
python3 scripts/boys-in-a-pasture/make.py band pack install # one part again
```

Needs Python 3.9 or later with numpy and Pillow (built with WebP). The whole
run takes about three minutes, most of it the pasture. Everything it makes
goes in `work/`, which git ignores; `PASTURE_WORK` puts it somewhere else.

It starts from [`art/boys-in-a-pasture.jpg`](art/boys-in-a-pasture.jpg), the
Wikimedia Commons scan of the painting (public domain) with its dark scan
border trimmed, cut 3:2 like every painting in the gallery.

## Where it was painted from

Homer sat low in the grass. The horizon lies 191.6 px down the 1200 x 800
painting, and the seated older boy's hat comes up just over it, so the eye is
about 0.84 m up. With a 50° field of view the boys sit 3.47 m off, and the rail
fence along the far side of the field, its top at eye level, is about 70 m
away. The pasture is flat. The walk in goes forward and a little left, past
the boys' shoulders, getting up off the grass as it goes.

## What it makes

- **Which of the painting is what** (`regions.py`). The boys are cut out from
  an outline traced round them, then each pixel along its edge is fitted to
  whichever side's paint it is nearer. The sky is found by its light against
  the trees, joined down from the top. Below the foot of the fence everything
  else is pasture.
- **The painting itself**, laid back over the sky and the pasture from where
  it was painted (`paint`), so from the doorway the place is the painting to
  the pixel. Where the walk would see it blown up, it gives way to the
  pasture's own paint: each vertex carries how much.
- **The pasture** (`ground`), one log-polar texture round the walk, painted
  stroke by stroke in colours lifted from the painting: broad olive dabs,
  upright grass flicks, rust-red sorrel, long dark blades, a few daisies,
  clover and specks of red and violet close to. It goes from green near the
  eye to olive, then to rust and olive swathes towards the fence, as the
  painting does. Right under the walk the flicks are no taller than they are
  wide, so they never fan out round the texture's centre.
- **The sky** (`far`), one polar texture with the zenith in its middle: the
  painting's teal sky (its blue under old varnish) and cream clouds carried
  on overhead, clear and hazy low down where the tree line stands against it.
- **The far side of the field** (`band`), a ring 70 m out: the painting's own
  stretch, with its big tree, its sapling and its fence; elsewhere a
  post-and-rail fence about every three and a half metres, the strip of field
  beyond it, low hedges and trees dabbed in the painting's tree colours,
  smaller ones hazed back as the painting's are on its right. Where the older
  boy's hat hides the painting's tree line, the hedge and fence carry on
  behind it.
- **The boys and the shade trees** (`cards`). The two boys are cut from the
  full-size scan, so close to they are as sharp as the scan allows, and stood
  upright where they sit. Two pasture elms and a pair of bushes stand out of
  the painting's sight, behind and to the left. The elms are painted with a
  trunk forking into limbs and a crown of separate leaf clusters lit from the
  left.

## Looking at it

The app can't be run from here, so `render.py` draws a scene the way
`EnterPainting` does: textured triangles with screen-space texture
coordinates, back to front. `views.py` renders it at phone size, each view a
`route,yaw,pitch`:

```
python3 scripts/boys-in-a-pasture/views.py look 0,0,0 1,0,0 1,-70,0 1,180,0
```

The picture lands in `work/look.png`.
