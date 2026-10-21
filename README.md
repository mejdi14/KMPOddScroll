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
rides up four layers and fades off the top — except the card it stops past,
which is held in the top slot, lifts off it and turns upright to face you, with
its title let in underneath. Drag it, flick it, or tap and scroll to step it on.

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

`LiftStackScrollState.step(by)` runs the deck on from a host's own controls.
Every card is one `drawImage` through its own exact projection — Compose's
`Matrix` carries the perspective terms on every target — so it needs no shader
and no strips anywhere.

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
cards of the Edge Distortion gallery.

Flex Carousel uses museum scans of paintings, also from Unsplash and under the
same licence, cut 800x600 for its 4:3 cards. Lift Stack uses the same six, cut
square.

| File | Scanned by |
| --- | --- |
| `art_wheatfield.jpg` | [National Gallery of Art](https://unsplash.com/@ngadc) |
| `art_farmhouse.jpg` | [National Gallery of Art](https://unsplash.com/@ngadc) |
| `art_night.jpg` | [Birmingham Museums Trust](https://unsplash.com/@birminghammuseumstrust) |
| `art_cowherd.jpg` | [The Metropolitan Museum of Art](https://unsplash.com/@themet) |
| `art_village.jpg` | [Birmingham Museums Trust](https://unsplash.com/@birminghammuseumstrust) |
| `art_cypresses.jpg` | [Tofan Teodor](https://unsplash.com/@tofanteodor) |

### Illustrations

The `flower_*.jpg` set used by Card Sphere was cut out of two watercolour flower
collection sheets from [Freepik](https://www.freepik.com) — each flower found as
its own run of ink on the paper, its neighbours painted back out, and the result
framed to the card's 4:5.

The `movie_*.jpg` set used by Infinite Menu is thirty film posters taken from
each film's English Wikipedia infobox, cut square from the top for the sphere's
tiles. Posters are copyrighted by their studios and shown here only to
illustrate the demo, so swap them for your own artwork before using this
anywhere real.

**Neither set is covered by the licences above.** Freepik's free licence wants
attribution and does not allow the flowers to be redistributed on their own, so
swap them for your own artwork before using this anywhere real, or hold a
Freepik licence that covers it.
