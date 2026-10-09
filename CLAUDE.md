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
- `shared/.../demos/DemoImages.kt` — procedural card art, so the demos need no
  network and no image-loading dependency.
- `androidApp/`, `desktopApp/`, `webApp/`, `iosApp/` — thin entry points; they
  all call `App()` from `shared`.

## Committing

After finishing a change, `git add` the relevant files and commit it.

Commit messages are short, lowercase, and plain — describe the change the way a
developer would in a hurry:

```
add helix scroll
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

The library is **common code only** — no `expect`/`actual`, no platform
graphics APIs. That rules out runtime shaders, so the house techniques are:

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
- Animation runs off a `withFrameNanos` loop, so easing constants are converted
  from per-frame to per-second: `1 - (1 - k).pow(dt / 16.67)`.

## Notes

- Build with the Android Studio JDK:
  `JAVA_HOME="$HOME/Applications/Android Studio.app/Contents/jbr/Contents/Home"`
- Desktop is the fast feedback loop: `./gradlew :desktopApp:run`.
- The library pulls in `compose.foundation` and no Material — HUD text uses
  `BasicText`, so consumers aren't forced onto Material 3.
- Credentials live in `~/.gradle/gradle.properties`, never in the repo.
