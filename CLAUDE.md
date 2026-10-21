# KMPOddScroll

A Compose Multiplatform library of odd scrolling styles — helixes, ribbons,
conveyors — plus a demo app that showcases them. Targets Android, iOS, Desktop
(JVM), Wasm and JS.

- `oddScroll/` — the published library (`io.github.mejdi14:kmp-odd-scroll`).
  Each style is a single composable that takes a list of items and owns its own
  layout, projection and gesture handling. Package root is
  `io.github.mejdi14.oddscroll`.
- `shared/` — the demo app UI. `ScrollCatalog.kt` is the single source of truth:
  one `ScrollDemo` entry drives both the home gallery and the navigation state.
- `shared/.../home/ScrollArtwork.kt` — procedural cover art for each demo tile.
- `shared/src/commonMain/composeResources/drawable/` — demo art bundled as
  Compose resources, so no demo needs a network call or an image-loading
  dependency. `wide_*` are 1200x600 Unsplash photographs, `grid_*` the same six
  cut 800x1000 for portrait cards, `flower_*` watercolour illustrations cut out
  of two collection sheets. Credit new ones in `README.md`; the flowers are
  under a stock licence that the Unsplash ones are not, so keep the two apart.
- `androidApp/`, `desktopApp/`, `webApp/`, `iosApp/` — thin entry points; they
  all call `App()` from `shared`.

A demo fills the frame and nothing is drawn over it. The way back to the gallery
is `BackHandler` in `App.kt` — the system's own back — so don't add chrome on
top of a style that is the thing being looked at.

## Committing

After finishing a change, `git add` the relevant files and commit it.

Commit messages are short, lowercase, and plain — describe the change the way a
developer would in a hurry:

```
add infinite spiral scroll
fix strip seams on wide cards
upgrade compose multiplatform
```

Rules:
- One line. No body unless the change genuinely needs explaining.
- No "feat:"/"chore:" prefixes, no emoji, no trailing punctuation.
- **No AI attribution or co-author trailers.** Commits should read as if written
  by the repo owner.
- Don't push unless asked.

## Adding a new scroll style

1. Add the composable in `oddScroll/src/commonMain/.../<name>/<Name>Scroll.kt`,
   with its parameters and state class beside it.
2. Add a demo screen in `shared/.../demos/<Name>DemoScreen.kt`.
3. Add a `ScrollDemo` entry in `ScrollCatalog.kt` (route, category, title,
   subtitle).
4. Add a cover-art motif branch in `ScrollArtwork.kt`.
5. Document it in `README.md` (feature bullet, usage snippet, API table).

## Rendering constraints

Prefer **common code**: a style that can be drawn with Compose's own primitives
should be. The house techniques for that are below.

A per-pixel treatment over a finished frame — a warp, a blur, a colour split —
is the exception. Do **not** try to fake one by subdividing geometry and drawing
the pieces: a few hundred `drawImage` calls a frame will not hold 60fps on a
phone, which is how `EdgeDistortionScroll` was first written and why it was
rewritten. Put it behind an `internal expect fun Modifier.<effect>(...)` instead:
`androidMain` supplies an AGSL `RuntimeShader` through
`graphicsLayer { renderEffect = ... }`, and the other targets return `this`, so
the style still runs everywhere with that one treatment missing. Keep the actual
in every source set (`jvmMain`, `jsMain`, `wasmJsMain`, `iosMain`) — the module
has no shared non-Android set, and adding one is not worth the build config.

House techniques for the common part:

- **Perspective via strips.** A card is sliced into vertical strips and each
  strip is drawn with its own affine transform (`withTransform { transform(m) }`
  around `drawImage`). Narrow strips approximate a perspective quad as a
  parallelogram, which is enough for cylinder curvature, per-strip depth scaling
  and shear. Strips overlap slightly (`STRIP_OVERLAP`) or hairline seams show.
- **Compose `Matrix` is row-vector × matrix**: `m[inputAxis, outputAxis]`, so
  `m[0,0]`/`m[0,1]` is the screen vector per unit of source x, `m[1,0]`/`m[1,1]`
  per unit of source y, and `m[3,0]`/`m[3,1]` is the translation.
- **Frame-wide treatments go down once.** Anything driven only by screen y —
  the border fade, for instance — is one `Brush.verticalGradient` over the
  composited column with `BlendMode.SrcAtop`, which affects only where content
  already exists. That needs
  `Modifier.graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)`.
- **Count the draws per frame.** A visible-cell sweep plus one `drawImage` per
  card is the target. `withTransform { translate(...); scale(...) }` around
  `drawImage(image, topLeft)` keeps motion sub-pixel smooth where an integer
  `dstOffset` would step.
- Animation runs off a `withFrameNanos` loop, so easing constants are converted
  from per-frame to per-second: `1 - (1 - k).pow(dt / 16.67)`.
- **Per-frame values are read inside `graphicsLayer { }`**, not in composition.
  The block re-runs on the layer without recomposing the node, which is what
  makes a staggered reveal over dozens of pieces (`internal/SplitText.kt`)
  affordable. A spring value read in composition rebuilds the whole style —
  canvas, gestures and all — sixty times a second for the length of its
  animation; that is what made the flex carousel's focus stutter. Pass such a
  value as `() -> Float` and read it in the layer, and tell composition only
  about booleans, via `derivedStateOf`.
- **Compose early, animate late.** Building dozens of nodes in the frame an
  animation is supposed to land is a dropped frame. Compose them when the
  gesture starts and hold the clock with `snapshotFlow(gate).first { it }`.

## Testing

Don't run tests, and don't try the app on a simulator, emulator or any device.
Compiling to check a change builds is fine; looking at it running is the
owner's job.

## Notes

- Build with the Android Studio JDK:
  `JAVA_HOME="$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home"`
- Desktop is the fast feedback loop: `./gradlew :desktopApp:run`.
- The library pulls in `compose.foundation` and no Material — HUD text uses
  `BasicText`, so consumers aren't forced onto Material 3.
- Credentials live in `~/.gradle/gradle.properties`, never in the repo.
