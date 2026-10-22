This is a Kotlin Multiplatform project targeting Android, iOS, Web, Desktop (JVM).

* [/iosApp](./iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/shared](./shared/src) is for code that will be shared across your Compose Multiplatform applications.
  It contains several subfolders:
  - [commonMain](./shared/src/commonMain/kotlin) is for code that’s common for all targets.
  - Other folders are for Kotlin code that will be compiled for only the platform indicated in the folder name.
    For example, if you want to use Apple’s CoreCrypto for the iOS part of your Kotlin app,
    the [iosMain](./shared/src/iosMain/kotlin) folder would be the right place for such calls.
    Similarly, if you want to edit the Desktop (JVM) specific part, the [jvmMain](./shared/src/jvmMain/kotlin)
    folder is the appropriate location.

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :androidApp:assembleDebug`
- Desktop app:
  - Hot reload: `./gradlew :desktopApp:hotRun --auto`
  - Standard run: `./gradlew :desktopApp:run`
- Web app:
  - Wasm target (faster, modern browsers): `./gradlew :webApp:wasmJsBrowserDevelopmentRun`
  - JS target (slower, supports older browsers): `./gradlew :webApp:jsBrowserDevelopmentRun`
- iOS app: open the [/iosApp](./iosApp) directory in Xcode and run it from there.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :shared:testAndroidHostTest`
- Desktop tests: `./gradlew :shared:jvmTest`
- Web tests:
  - Wasm target: `./gradlew :shared:wasmJsTest`
  - JS target: `./gradlew :shared:jsTest`
- iOS tests: `./gradlew :shared:iosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html),
[Compose Multiplatform](https://kotlinlang.org/compose-multiplatform/),
[Kotlin/Wasm](https://kotl.in/wasm/)…

We would appreciate your feedback on Compose/Web and Kotlin/Wasm in the public Slack channel [#compose-web](https://slack-chats.kotlinlang.org/c/compose-web).
If you face any issues, please report them on [YouTrack](https://youtrack.jetbrains.com/newIssue?project=CMP).

## Scroll styles

### Infinite Spiral

A column of cards wound onto a spiral, turning endlessly. Each card sits a fixed
step further down than the one before and a fixed turn further round. Nothing is
actually in depth — the cards stay on the plane, and the roundness comes from
where the turn puts them across the frame and how large the near side draws
them. The card at the middle is the largest and the sharpest; away from it they
shrink, soften and fade out.

```kotlin
InfiniteSpiralScroll(
    items = listOf(InfiniteSpiralItem(image, "Forest Path"), /* ... */),
    modifier = Modifier.fillMaxSize(),
    motion = SpiralMotion.Both,
)
```

| Parameter | Default | |
| --- | --- | --- |
| `motion` | `Both` | `Drift`, `Hand`, or both |
| `speed` | `0.55` | cards a second while drifting |
| `direction` | `Up` | which way the drift runs |
| `radius` | `170.dp` | how far the spiral swings across the frame |
| `cardWidth` / `cardHeight` | `100.dp` | card size |
| `verticalSpacing` | `60.dp` | step down between one card and the next |
| `perspective` | `1000.dp` | depth the near side is enlarged against |
| `cardsPerTurn` | `7` | cards in one full turn |
| `rotation` / `cardTilt` | `0` / `0` | turn of the spiral, lie of each card |
| `cardRadius` | `10.dp` | corner rounding |
| `centerScale` | `1.2` | how much larger the middle card draws |
| `edgeFade` / `edgeBlur` | `0.3` / `6.dp` | how the ends fade and soften |
| `grayscale` | `0` | drain the colour |

Compose has no blur in common code, so the softening at the ends is a blurred
copy of each card made once — the picture laid down twenty-five times on a small
offset lattice and added together — cross-faded against the sharp one.

### Edge Distortion

An endless lattice of cards panned freely on both axes, bending away at the
borders of the frame with a fisheye and a colour fringe. It drifts diagonally on
its own and takes a drag, resuming the drift on release. There is no extent and
no edge — pan far enough and the pictures come round again.

The border treatment is a single AGSL shader pass over the frame, so it needs
**Android 13 (API 33) or newer**. Everywhere else — older Android, desktop, web,
iOS — the lattice draws flat and behaves identically otherwise.

```kotlin
EdgeDistortionScroll(
    images = listOf(image, /* ... */),
    modifier = Modifier.fillMaxSize(),
)
```

| Parameter | Default | |
| --- | --- | --- |
| `background` | white | page colour behind the cards |
| `cardHeight` | `0.375` | card height, share of the viewport |
| `cardRatio` | `0.8` | card width / height |
| `gapX` | `0.333` | space between columns, share of card width |
| `gapY` | `0.267` | space between rows, share of card height |
| `corner` | `0.053` | rounding, share of the shorter side |
| `distortion` | `0.3` | how hard the borders bend; `0` is flat |
| `aberration` | `0.008` | fringe offset in uv; `0` drops it |
| `panSpeed` | `1.5` | lattice pixels per pixel of drag |
| `drift` | `(0.03, 0.015)` | idle movement per second |

### Flex Carousel

An infinite row of pictures that flows through invisible liquid glass at its
edges. With `fit = Natural` cards keep their own proportions and the row is a
run of unequal widths; any other `fit` draws them all at one size. Either way a
step along is half of this card plus the gap plus half of the next.
Position is a spring: flick, wheel and tap all arrive as a new goal. The faster
it runs, the narrower every card draws. Tap the centred card to open it.

```kotlin
FlexCarouselScroll(
    items = listOf(FlexCarouselItem(image, "Forest Path"), /* ... */),
    modifier = Modifier.fillMaxSize(),
    lens = FlexLens.Liquid,
)
```

| Parameter | Default | |
| --- | --- | --- |
| `lens` | `FlexLens.Liquid` | glass shape; also `Ribbon`, `Vortex`, `Arch` |
| `cardHeight` | `0.5` | card height, share of the viewport height |
| `maxCardWidth` | `0.62` | widest a card may draw, share of viewport width |
| `gap` | `12.dp` | space between cards |
| `radius` | `0.dp` | corner rounding |
| `fit` | `Natural` | or `Portrait`, `Square`, `Landscape` |
| `squeeze` | `0.2` | how far cards narrow at full speed |
| `focusOnClick` | `true` | tap the centred card to open it |
| `focus` | `Auto` | where an opened card goes — `Beside`, `Centred`, or `Auto` |
| `autoplay` / `interval` | `false` / `4f` | step along on a timer |
| `captions` | `true` | title and counter |
| `intro` | `true` | the row rises into place |

Opening a card on a landscape frame grows it into the middle first, and only
once it is there does it move over into the left half and give the right half to
its title and description. Closing retraces that: over to the middle, then down
into the row. The text lets itself in a piece at a time as the card arrives —
the title by the letter, the description by the word, each piece rising into
place behind the one before it. Closing takes it away at once, without a reverse
reveal to sit through. `FlexFocus.Centred` keeps it in the middle throughout; On a portrait frame `Auto` stays centred,
since there is no room to put text next to a card worth looking at.

Every size is a share of the bounds the row is given, including the lens, so
handing it a band rather than the whole screen is the way to make its cards
smaller — the demo gives it 62% of the height on a portrait screen and all of a
landscape one. `maxCardWidth` is the backstop underneath that: it holds the
widest card to a share of the width and brings `cardHeight` down with it, since
on a tall frame half the height is wider than the frame itself. A landscape
frame never reaches the cap.

Like Edge Distortion, the glass is a single AGSL pass and needs **Android 13
(API 33) or newer**; elsewhere the row runs undistorted and everything else is
unchanged.

### Infinite Menu

A sphere of tiles, dragged from any direction, that brings whichever tile ends
up nearest the front round to face you. Pictures repeat around the sphere, so it
reads as a surface rather than a list bent into a ball. Tiles turning away
shrink, desaturate and fade; the camera backs off while you handle it and draws
in again as it settles.

```kotlin
InfiniteMenuScroll(
    items = listOf(InfiniteMenuItem(image, "Forest Path", "Autumn woodland"), /* ... */),
    modifier = Modifier.fillMaxSize(),
)
```

| Parameter | Default | |
| --- | --- | --- |
| `tileCount` | `160` | tiles on the sphere, 6 to 400 |
| `tileSize` | `0.63` | tile size, share of its own patch of sphere |
| `roundness` | `0.2` | corner rounding, 0 square to 1 circular |
| `zoom` | `1.65` | how much of the frame the front tile fills |
| `pullBack` | `1.0` | how far the camera backs off while moving |
| `stretch` | `0.6` | smear along travel while spinning |
| `inertia` | `0.6` | how long a throw runs |
| `autoplay` | `3f` | seconds at rest before stepping on; `0` never |
| `grayscale` / `dim` | `true` / `0.5` | how the tiles behind are drained |
| `captions` | `true` | title and description of the front tile |

Unlike the other two optical styles this one needs no shader anywhere — it is
all geometry, one image per tile, so it runs the same on every target.

### Card Sphere

A field of flat graphic cards on a transparent sphere. Drag, flick or scroll to
turn it; it coasts, then glides one of eight hero cards to the front. Double tap
flips the page between white and `#111`.

Each card carries one watercolour flower, dealt by a stride so every flower gets
an equal share of the field and no two neighbours draw the same one.

```kotlin
CardSphereScroll(
    flowers = listOf(image, /* ... */),
    modifier = Modifier.fillMaxSize(),
    theme = CardSphereTheme.Light,
    onSettle = { hero -> },
)
```

| Parameter | Default | |
| --- | --- | --- |
| `flowers` | — | plates dealt round the field; the heroes take the first eight |
| `theme` | `Light` | starting page colour |
| `allowThemeToggle` | `true` | double tap flips the page |
| `onSettle` | `null` | hero index, each time the field rests |
| `onThemeChange` | `null` | called when the page flips |

The field itself is generated from a fixed seed, so the arrangement is the same
every run. Like Infinite Menu it needs no shader anywhere.

### Lift Stack

A deck of pictures laid flat in a stack and seen from above at a quarter turn.
It runs up through itself a card at a time — each one comes in at the bottom,
rides up four layers and fades off the top. Tap the top card and it lifts off
the deck and turns upright to face you, with its title let in underneath; tap
again and it plays back down into its slot. Drag it, flick it, or scroll to step
it on — except while a card is up, when the deck stays put.

The geometry and timing were measured off a recording of the reference rather
than eyeballed: a pinhole camera fitted to the resting stack (yaw 45°, looking
down 27°, fifteen card sides away), a layer gap of 0.274 card sides, a lift of
0.73 sides, and the reference's own curves for the run, the settle, the rise
and the turn.

```kotlin
LiftStackScroll(
    items = listOf(LiftStackItem(image, "Green Wheat Fields", "Vincent van Gogh"), /* ... */),
    modifier = Modifier.fillMaxSize(),
)
```

| Parameter | Default | |
| --- | --- | --- |
| `items` | — | cards in order; the deck repeats, so it has no end |
| `background` | `#F6F6F1` | page colour |
| `contentColor` | `#141414` | caption text |
| `markerColor` | `#D9443F` | the square that leads the caption |
| `captions` | `true` | title and credit of the standing card |
| `state` | intro of `7` | `rememberLiftStackScrollState(intro)` — cards run on first show; `0` opens at rest |

`LiftStackScrollState.step(by)` runs the deck on from a host's own controls, and
`locked` says whether a card is up and the deck is holding still.
Every card is one `drawImage` through its own exact projection — Compose's
`Matrix` carries the perspective terms on every target — so it needs no shader
and no strips anywhere.

### CD Rack

A row of clear CD jewel cases standing on edge, seen from above and to the
left, running up and to the right across the frame. It slides along its own
length, and the case that comes to the slot in the middle rises out of the row,
with its title let in top left. Drag along the row (or across it — only the
part along it counts), flick to throw it, or scroll to step it a case.

Tap a case to take it out: it comes to the slot, the rest of the row clears
off the screen either way, and it turns to face you and comes forward — the
single case below, to drag round and tap open. `CdRackScrollState.leave()`
(the demo wires it to the system's back) shuts it if it is open, turns it back
into the rack and brings the row in again.

Every case is the 3D case of `CdCase`, drawn after Ann Nguyen's WebGL
[CD case](https://github.com/annnguyentt/vibe-coding/tree/main/cd-case). The
rack's three measured angles turn out to be one turn of it, exactly — tipped
back 0.310 rad and turned 0.534 rad left, the cases standing face to face along
their own axis, 71.9 mm apart — seen through a camera dollied 24 times out
with its lens lengthened to match, which flattens the row to the reference's
orthographic look. So each case is drawn once at that turn, shaders and all,
and then only moved; the one taken out is drawn live, while the camera comes
back in.

The rack was measured off a recording of a row of cassettes, which is why the
numbers below talk in tapes.

Measured off a recording of the reference rather than eyeballed. The top-left
corner of every tape, pooled over 270 frames, falls on one line with one pitch
and one number per frame to 1.1 px, so the rack is a single scalar:

- the row runs at 27.3°, a tape every 0.322 tape widths, with no perspective
  to speak of (0.05% a tape), so every tape is the same shape on screen;
- the slotted tape stands 0.342 widths proud, straight up, as a fixed function
  of its distance from the slot — flat within 0.08 tapes, gone by 1.0, a
  smoothstep between (1.9 px residual on a 50 px lift; a timed per-tape lift
  fits no better than 7 px);
- nothing turns (top edges agree to 0.6°), grows or fades; each tape sits a
  fixed 0.018 widths off the line, the same every time it comes round, and a
  light over the slot lifts its shell 18%;
- a throw leaves at the finger's speed on `cubic-bezier(0.283, 0.501, 0.698,
  0.955)`, a run from rest is `cubic-bezier(0.280, 0.027, 0.248, 0.935)` for
  0.664 s × √tapes — each within 0.6 px of the reference's own runs.

```kotlin
CdRackScroll(
    items = listOf(CdRackItem(image, "Funky Drummer", "James Brown"), /* ... */),
    modifier = Modifier.fillMaxSize(),
)
```

| Parameter | Default | |
| --- | --- | --- |
| `items` | — | cases in order; the rack repeats, so it has no end |
| `background` | white | the backdrop; the plastic is shaded for a pale one |
| `contentColor` | `#111114` | caption text |
| `captions` | `true` | title and artist of the case in the slot |
| `state` | intro of `4` | `rememberCdRackScrollState(intro)` — cases slid on first show; `0` opens at rest |

`CdRackScrollState.scrollTo(index)` and `step(by)` drive it from a host's own
controls, `inspect(index)` takes a case out, `leave()` puts it back, `slotted`
is the case in the slot and `inspecting` whether one is out.

#### One case: `CdCase`

The case the rack takes out, on its own: on its white studio wall, idling with
a slow sway, the disc turning inside. Drag it to turn
it (it springs back when let go); tap it to open it — it turns right round as
the lid swings out to its left on the reference's own hand-keyed curves — and
tap again to shut it.

It is the reference's geometry and its studio, carried over:

- the camera is a 28° lens far enough back for the case to fill 60% of the
  height, and every piece of the case — floor, rails and ribs, disc, hub, tab,
  lid — is flat in its own frame, so each is baked once and drawn through its
  own exact projection, back to front as the case faces. The walls round its
  edge and the hinge's knuckles are drawn as the camera sees them each frame,
  and its shadow falls on the wall behind along the reference's light;
- on Android 13 and up the plastic and the disc are shaded a pixel at a time in
  AGSL, after the reference's own shaders: its analytic studio of softboxes and
  flags, Fresnel at every surface, rods that refract the view through a round
  section and bend the page and the dark room into it, sheets that throw back
  the softboxes' glare, and the disc's diffraction rainbow, three orders of the
  spectrum along its tracks. Elsewhere the same pieces are drawn from a baked
  look, with the rainbow and the lid's glare worked out per frame for the case's
  turn.

```kotlin
CdCase(
    art = image,
    modifier = Modifier.fillMaxSize(),
)
```

| Parameter | Default | |
| --- | --- | --- |
| `art` | — | printed across the disc, its middle square to the disc's width |
| `background` | white | the backdrop; the plastic is shaded for a pale one |
| `spin` | `true` | turn the disc inside the case |
| `state` | — | `rememberCdCaseState()`; `open()`, `close()`, `toggle()` and `isOpen` |

### Gallery Wall

Paintings hung round the inside of a room — a back wall facing you, side walls
running towards you, rounded corners between — that slide along the walls past
you, each with its label on the wall underneath. A painting comes in down the
right-hand wall, bends round the corner, crosses the back and goes out down the
left. Drag along it or scroll, and the hang springs after and comes to rest
with a painting in the middle; tap a painting to bring it to the middle. The
room opens with a walk in from the doorway. Two seconds after it stops, an
Inspect button walks you up to the painting in the
middle — two seconds of a visitor's approach, steps and all — and a tap steps
back. As the walk starts the painting comes off the wall's curve and hangs
flat, square on; nearly there, the paintings either side slide away down the
walls and out of the frame. On Android 13 and up the painting also comes out of its
frame and loses its label on the way, showing the edges of the liquid glass
over it, and a drag slides the glass about so its edges bend the painting
under them.

Measured off a recording of the reference rather than eyeballed. How tall a
painting stands at each column of the screen is one fixed curve for every
painting in every frame, bar its own height — 475,000 columns over 640 frames
to 0.44 px — so the wall stays put and the whole hang is a single scalar
(every painting placed to 1.8 px). In heights of the tallest painting:

- the eye is 6.95 from the back wall, level with the paintings' middles, with a
  focal length of 1.40 frame widths;
- the side walls stand 1.10 either side of the middle, joined to the back wall
  by corners of radius 0.64 (the wall's profile to 0.37 px; the focal length is
  pinned by the paintings holding their shape round the corner, to 1%);
- paintings hang 0.56 apart edge to edge, the same on every stretch of wall to
  2.6%, at heights from 0.72 to 1;
- nothing turns, grows, fades or dims on the way round — size holds to 0.1%,
  brightness to 1.4% — and the corners are square;
- after a swipe the hang settles on a critically damped spring, ωn = 6.74 a
  second (`spring(dampingRatio = 1f, stiffness = 45.5f)`), to 1.3 px over seven
  let-gos, and coasts no further than a spring trailing the input would.

```kotlin
GalleryWallScroll(
    items = listOf(
        GalleryWallItem(image, "Wheat Field with Cypresses", "Vincent van Gogh", "1889", "Oil on canvas"),
        /* ... */
    ),
    modifier = Modifier.fillMaxSize(),
)
```

| Parameter | Default | |
| --- | --- | --- |
| `items` | — | paintings in order; the hang repeats, so it has no end |
| `background` | `#FFFFFF` | the wall |
| `contentColor` | `#1A1A1A` | the labels; all but the title are set in it at 45% |
| `frame` | `null` | `GalleryWallFrame()` hangs each painting in a walnut frame — four mitred sticks, the grain running along each — that stands 4.5cm off the wall (against a metre-tall painting), and with `carving = 1f` hand-carved: a rope twist round the outer edge, egg and dart along the face, bead and reel at the sight edge and a rosette over each mitre, every piece a little off true: the edge shows as the wall turns away, and a soft shadow falls behind. The painting sits 1.5cm back inside it, so the inside of the moulding shows round it at an angle, behind glass that throws back more of the wall the further the wall turns away (Fresnel), with the doorway behind you as a soft glow that stays put as the paintings slide under it; `glass = false` leaves it open. On Android 13 and up the glass is liquid glass, an AGSL shader drawn in the painting's own coordinates: its square-cornered edges run under the moulding, where they bend the painting under them — pulled out towards the edge with a ripple, split a little into red, green and blue, and lit along the rim — and show once the painting comes out of its frame up close. `null` hangs them flat and bare |
| `inspectLabel` | `"Inspect"` | the button that walks up to a painting; `null` for none |
| `state` | — | `rememberGalleryWallScrollState()`; `walkIn = false` skips the walk in from the doorway |

`GalleryWallItem.scale` is a painting's height against the tallest (1).
`GalleryWallScrollState.scrollTo(index)` brings a painting to the middle from a
host's own controls, and `centred` is the one there. `canInspect` turns true two
seconds after the hang comes to rest; `inspect()` walks up to the painting and
`leave()` steps back, with `inspecting` true in between — a host can wire the
system back to it with `BackHandler(enabled = state.inspecting) { state.leave() }`.
Each painting is drawn
once, label and all, into a bitmap and then through its own exact projection —
one `drawImage` for each flat stretch of wall it is on and a dozen chords to a
corner — so it needs no shader.

### Terrace

A deck stacked in depth both ways from the card at the front: the cards before
it step back above, the ones after it below, each smaller than the one in
front, so all that shows of them is a band along the edge — a terrace either
side of the front card, with its index to the left and its title to the right.
Run it on and the front card shrinks back into the bands above while the first
band below grows out to take its place; the two pass in front of each other
halfway. Drag up or down, flick to throw it, scroll a card a notch, or tap a
band to bring that card to the front.

Measured off a recording of the reference rather than eyeballed. The width of
every card and the outer edge of every band, pooled over 175 frames, collapse
onto one number per frame and five constants to 0.20 px (max 0.99 px over
2,409 edges), so the stack is a single scalar. In card widths, a card `d`
levels from the front:

- is scaled by 0.801^|d| (± 0.0005) and centred 0.207·d below the middle; the
  card is 1.384 tall. Interpolating between fixed slots instead fits only to
  0.51 px with six more numbers, and its slots come out at 0.8, 0.8² and 0.8³;
- holds its x-centre to 0.06 px, turns by no more than 0.18°, keeps its size to
  0.13% at the front, and has square corners (any radius is under 0.01);
- is never dimmed — a band reads darker one level back only because the card in
  front throws a shadow onto it: a gaussian 0.070 soft, dropped 0.065 and 20%
  dark, to 2/255 of the page;
- settles on a first-order approach to the nearest card, 5.47 a second (5.03
  to 5.97 over five settles, 0.3 px each). It is not a spring: a free spring
  fit comes back critically damped with no swing, and one from rest would have
  to start eight frames before anything moves.

```kotlin
TerraceScroll(
    items = listOf(TerraceItem(image, "Sounds", "Marlborough Sounds"), /* ... */),
    modifier = Modifier.fillMaxSize(),
)
```

| Parameter | Default | |
| --- | --- | --- |
| `items` | — | cards in order; the stack repeats, so it has no end |
| `background` | `#FFFFFF` | page colour |
| `contentColor` | `#141414` | captions; the index and subtitle are set in it at reduced strength |
| `shadowColor` | `#000000` | the shadow each card throws onto those behind it |
| `captions` | `true` | index to the left of the front card, title and subtitle to its right |
| `depth` | `3` | levels shown either side of the front card |
| `state` | — | `rememberTerraceScrollState()` |

`TerraceScrollState.scrollTo(index)` and `step(by)` drive it from a host's own
controls, and `front` is the card at the front. A card and one shared, baked
shadow are each a single `drawImage` placed by a transform, so the stack is
fourteen draws a frame and needs no shader.

## Image credits

### Photographs

Infinite Spiral and Edge Distortion use photographs from
[Unsplash](https://unsplash.com), under the
[Unsplash License](https://unsplash.com/license). Cut to 1200x600 and bundled as
Compose resources.

| File | Photographer |
| --- | --- |
| `wide_sounds.jpg` | [Samuel Harrington](https://unsplash.com/photos/1791681403982-fe65ea53288f) |
| `wide_falls.jpg` | [Ingrid Eich](https://unsplash.com/photos/1767930704291-a12b91e5a261) |
| `wide_peaks.jpg` | [Maciej Marko](https://unsplash.com/photos/1790019852739-349585111186) |
| `wide_forest.jpg` | [Wolfgang Hasselmann](https://unsplash.com/photos/1788695968738-3c2818047036) |
| `wide_basalt.jpg` | [Adrien Olichon](https://unsplash.com/photos/1762763955129-0ad8692095ce) |
| `wide_towers.jpg` | [Heye Jensen](https://unsplash.com/photos/1790313157313-cbc2c66a61ab) |

The `grid_*.jpg` set is the same six photographs, cut 800x1000 for the portrait
cards of the Edge Distortion gallery; Terrace stacks the same cuts.

Flex Carousel uses museum scans of paintings, also from Unsplash and under the
same licence, cut 800x600 for its 4:3 cards. Lift Stack uses the same six, cut
square. CD Rack prints both the scans and the wide photographs on its discs.

| File | Scanned by |
| --- | --- |
| `art_wheatfield.jpg` | [National Gallery of Art](https://unsplash.com/@ngadc) |
| `art_farmhouse.jpg` | [National Gallery of Art](https://unsplash.com/@ngadc) |
| `art_night.jpg` | [Birmingham Museums Trust](https://unsplash.com/@birminghammuseumstrust) |
| `art_cowherd.jpg` | [The Metropolitan Museum of Art](https://unsplash.com/@themet) |
| `art_village.jpg` | [Birmingham Museums Trust](https://unsplash.com/@birminghammuseumstrust) |
| `art_cypresses.jpg` | [Tofan Teodor](https://unsplash.com/@tofanteodor) |

Gallery Wall hangs fourteen `museum_*.jpg` paintings instead, each cut 1200x800
(3:2, so every one hangs the same height and one and a half times as wide) from
a 1,800 px or larger scan on Wikipedia / Wikimedia Commons: *Sunflowers*,
*Boating*, *Flaming June*, *Water Lilies and Japanese Bridge*, *The Roses of
Heliogabalus*, *Little Girl in a Blue Armchair*, *The Kiss*, *Almond Blossoms*,
*Luncheon of the Boating Party*, *The Cliff Walk at Pourville*, *The Birth of
Venus*, *Poppy Field*, *Lady with a Fan* and *Strolling along the Seashore*. The
reuse terms of each scan are on its Commons page.

### Illustrations

The `flower_*.jpg` set used by Card Sphere was cut out of two watercolour flower
collection sheets from [Freepik](https://www.freepik.com) — each flower found as
its own run of ink on the paper, its neighbours painted back out, and the result
framed to the card's 4:5.

The `movie_*.jpg` set used by Infinite Menu is thirty silent-era film posters,
high-resolution scans from Wikipedia/Wikimedia, cut square from the top and
scaled to 640x640 for the sphere's tiles. Posters are copyrighted by their studios and shown here only to
illustrate the demo, so swap them for your own artwork before using this
anywhere real.

**Neither set is covered by the licences above.** Freepik's free licence wants
attribution and does not allow the flowers to be redistributed on their own, so
swap them for your own artwork before using this anywhere real, or hold a
Freepik licence that covers it.
