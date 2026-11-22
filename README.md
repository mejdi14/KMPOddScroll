<div align="center">

# KMP Odd Scroll

Odd scrolling styles for Compose Multiplatform.<br>
Carousels, 3D racks and liquid-glass lists — each one a single composable that takes a list and owns its layout, projection and gestures.

[![Stars](https://img.shields.io/github/stars/mejdi14/KMPOddScroll?style=flat-square&logo=github)](https://github.com/mejdi14/KMPOddScroll/stargazers)
[![Kotlin Multiplatform](https://img.shields.io/badge/Kotlin-Multiplatform-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org/docs/multiplatform.html)
[![Compose Multiplatform](https://img.shields.io/badge/Compose-Multiplatform-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://www.jetbrains.com/compose-multiplatform/)

**Android · iOS · Desktop · Wasm · JS**

</div>

<br>

## Flex Carousel

An infinite row of paintings that flows through liquid glass at its edges. Flick,
scroll or tap a card to move it. (Opening the centred card beside its text, as
in the video, is the demo's own, built on `onItemClick`.)

https://github.com/user-attachments/assets/d67ff742-685a-44b4-9f56-c003ecf8b3d9

```kotlin
FlexCarouselScroll(
    items = listOf(FlexCarouselItem(image, "Forest Path"), /* ... */),
    modifier = Modifier.fillMaxSize(),
)
```

| Parameter | Default | |
| --- | --- | --- |
| `background`, `contentColor` | near black, off white | page and caption colours |
| `lens` | `Liquid` | glass shape: `Liquid`, `Ribbon`, `Vortex`, `Arch`, or your own `FlexLens` |
| `cardHeight`, `maxCardWidth` | `0.5`, `0.62` | card size, as shares of the frame |
| `gap`, `radius` | `12.dp`, `0.dp` | space between cards, corner rounding |
| `fit` | `Natural` | card proportions, or `Portrait`, `Square`, `Landscape` |
| `squeeze` | `0.2` | how far cards narrow at full speed |
| `parallax` | `0.08` | how far each picture overscans its card |
| `captions` | `true` | title of the centred card |
| `autoplay`, `interval` | `false`, `4f` | step along on a timer, seconds apart |
| `stiffness`, `flingReach` | `55`, `0.32` | the spring the row follows, how far a flick carries it |

The glass is an AGSL shader and needs Android 13+; everywhere else the row runs
undistorted.

<br>

## CD Rack

A row of jewel cases running across the frame, the one in the slot standing
proud of the rest. Drag along or across the row, flick it, or scroll a case at
a time. (Taking a case out, opening it and leafing through its manual, as in
the video, is the demo's own, built on `onItemClick`.)

https://github.com/user-attachments/assets/0446d7bf-080d-4278-a885-98c3e965e1a2

```kotlin
CdRackScroll(
    items = listOf(CdRackItem(disc, "Tekken 3", "Namco", cover = front), /* ... */),
    modifier = Modifier.fillMaxSize(),
)
```

| Parameter | Default | |
| --- | --- | --- |
| `background` | white | the backdrop; the plastic is shaded for a pale one |
| `contentColor` | near black | caption text |
| `caseSize` | `0.4` | a case face's share of the frame width |
| `captions` | `true` | title and artist of the case in the slot |

Shaded plastic and the disc's rainbow run on desktop, iOS, the web, and Android
13+; on older Android the same case is drawn from a baked look.

The rack draws every case once before it can be used; `state.loaded` runs from
0 to 1 while it does, for a loader of your own over it (the demo has one).

<br>

## Every style

Each style is one composable, and every one of them shares the same shape:

```kotlin
val state = rememberTerraceScrollState(initialIndex = 2)

TerraceScroll(
    items = items,
    modifier = Modifier.fillMaxSize(),
    userScrollEnabled = true,          // false ignores drag, fling and wheel
    onItemClick = { index -> state.animateScrollTo(index) },
    state = state,
)

// state.currentIndex       the item at the front, observable
// state.isScrollInProgress dragging or still moving
// state.scrollTo(i)        jump there
// state.animateScrollTo(i) travel there with the style's own motion
// state.step(by)           a whole item on or back, where the style has an order
```

With `onItemClick` left null, a tap does the style's own thing, usually
bringing the tapped item to the front. Indices wrap, and a run goes the short
way round. Every size and feel constant is a parameter, with the look shown
in the demo as its default; each is documented in KDoc.

| Style | Items | Notes |
| --- | --- | --- |
| `InfiniteSpiralScroll` | `InfiniteSpiralItem` | cards wound onto a turning spiral; `motion`, `speed`, `direction`, `radius`, `cardsPerTurn`, `edgeFade`, `edgeBlur`, `grayscale` |
| `EdgeDistortionScroll` | `ImageBitmap` | an endless 2D lattice bending away at its borders; no current item, so its state has `offset`, `scrollBy`, `scrollTo(Offset)`; `distortion` and `aberration` need Android 13+ |
| `FlexCarouselScroll` | `FlexCarouselItem` | see above |
| `InfiniteMenuScroll` | `InfiniteMenuItem` | a sphere of tiles that turns the nearest to face you; `tileCount`, `tileSize`, `zoom`, `inertia`, `autoplay`, `grayscale`, `dim`; no `step` |
| `CartwheelScroll` | `CartwheelItem` | cards riding the rim of a wheel; `cardSize`, `rimRadius`, `spacing`, `hub`, `dampingRatio`, `stiffness` |
| `CardSphereScroll` | `ImageBitmap` | a field of cards that coasts and settles one at the front; `billboard`, `cardPerspective`, `coastDecay`, `snapSpeed`; no `step` |
| `LiftStackScroll` | `LiftStackItem` | a deck that runs up through itself; tap stands the top card up (`state.toggleLift()`); `markerColor`, `matColor`, `liftPace` |
| `CdRackScroll` | `CdRackItem` | see above |
| `GalleryWallScroll` | `GalleryWallItem` | paintings round the inside of a room; `frame` (`GalleryWallFrame`, `GalleryWallOrnament`), `labels`, `roomDepth`, `roomHalfWidth`, `gap`, `eyeLevel`, `stiffness` |
| `TerraceScroll` | `TerraceItem` | a deck stacked back both ways from the front card; `depth`, `shrink`, `levelStep`, `aspect`, `shadowColor` |
| `RunwayScroll` | `RunwayItem` | card plinths on a colour-banded runway with a margin index; `sections`, `contentColor`, `shadowColor`, settle spring; state adds `autoplay` and `animateScrollToSection` |

The library only scrolls. Everything the demos do on top of that — taking a CD
case out, walking into a painting, opening a card beside its text, intro runs,
the runway's paper pagodas — is demo code in `shared/`, built on the public
API or kept as the demo's own copy under `shared/.../showcase/`.

<br>

## Install

Not on Maven Central yet — include the module as a project dependency:

```kotlin
// settings.gradle.kts
include(":oddScroll")

// build.gradle.kts
commonMain.dependencies { implementation(project(":oddScroll")) }
```

Depends on `compose.foundation` only, no Material. Android needs API 24+.

## Run the demo

```bash
./gradlew :desktopApp:run                         # desktop
./gradlew :androidApp:assembleDebug               # android
./gradlew :webApp:wasmJsBrowserDevelopmentRun     # web
```

For iOS, open [`iosApp`](./iosApp) in Xcode.

## Credits

Flex Carousel's paintings are museum scans from [Unsplash](https://unsplash.com)
([licence](https://unsplash.com/license)) by the
[National Gallery of Art](https://unsplash.com/@ngadc),
[Birmingham Museums Trust](https://unsplash.com/@birminghammuseumstrust),
[The Metropolitan Museum of Art](https://unsplash.com/@themet) and
[Tofan Teodor](https://unsplash.com/@tofanteodor).

The elm and windmill tower in the gallery's walk through Monet's *Poppy Field*
are new artwork generated with ImageGen. The sails, cart track and shade are
painted by the [scene generator](scripts/poppy-field/README.md).

CD Rack's disc labels come from the
[LaunchBox Games Database](https://gamesdb.launchbox-app.com) and its manuals
from [Emu-Land](https://www.emu-land.net/library/manuals/psx). The artwork
belongs to each game's publisher and is shown only to illustrate the demo — swap
it for your own before using this anywhere real.

---

<div align="center">

If this is useful to you, a ⭐ helps other people find it.

Made by [@mejdi141](https://x.com/mejdi141) — follow along on [X](https://x.com/mejdi141).

</div>
