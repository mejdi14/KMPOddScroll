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

### Helix

A spiral column of cards on the surface of a cylinder. Drag or scroll to turn
it; it settles on the nearest card, and tapping one brings it to the front.

```kotlin
HelixScroll(
    items = listOf(HelixItem(image, "Forest Path"), /* ... */),
    modifier = Modifier.fillMaxSize(),
    brand = "Studio",
)
```

| Parameter | Default | |
| --- | --- | --- |
| `brand` | `null` | wordmark in the top-left |
| `background` | `0xFF0A0A0B` | page colour the column fades into |
| `contentColor` | `0xFFF3F0E9` | wordmark, title and counter |
| `focusBand` | `0.25` | share of the height that stays lit |
| `twist` | `0.8` | turn between cards, radians |
| `rise` | `0.79` | rise between cards, card heights |
| `cardRatio` | `2.0` | card width / height |
| `entry` | `true` | glide the column in |
| `hud` | `true` | draw the wordmark, title and counter |

### Edge Distortion

An endless lattice of cards panned freely on both axes, bending away at the
borders of the frame with a fisheye and a colour fringe. It drifts on its own,
takes a drag, and throws when released. There is no extent and no edge — pan far
enough and the pictures come round again.

```kotlin
EdgeDistortionScroll(
    images = listOf(image, /* ... */),
    modifier = Modifier.fillMaxSize(),
)
```

| Parameter | Default | |
| --- | --- | --- |
| `background` | `0xFFF7F6F3` | page colour behind the cards |
| `cardHeight` | `0.375` | card height, share of the viewport |
| `cardRatio` | `0.8` | card width / height |
| `gap` | `0.27` | space between cards, share of card size |
| `corner` | `0.055` | rounding, share of the shorter side |
| `distortion` | `0.3` | how hard the borders bend; `0` is flat |
| `aberration` | `0.008` | colour fringe width; `0` drops it |
| `drift` | `(0.03, 0.015)` | idle movement per second |

## Photo credits

Demo card art is from [Unsplash](https://unsplash.com), used under the
[Unsplash License](https://unsplash.com/license). Cut to 1200x600 and bundled as
Compose resources.

| File | Photographer |
| --- | --- |
| `helix_sounds.jpg` | [Samuel Harrington](https://unsplash.com/photos/1791681403982-fe65ea53288f) |
| `helix_falls.jpg` | [Ingrid Eich](https://unsplash.com/photos/1767930704291-a12b91e5a261) |
| `helix_peaks.jpg` | [Maciej Marko](https://unsplash.com/photos/1790019852739-349585111186) |
| `helix_forest.jpg` | [Wolfgang Hasselmann](https://unsplash.com/photos/1788695968738-3c2818047036) |
| `helix_basalt.jpg` | [Adrien Olichon](https://unsplash.com/photos/1762763955129-0ad8692095ce) |
| `helix_towers.jpg` | [Heye Jensen](https://unsplash.com/photos/1790313157313-cbc2c66a61ab) |
