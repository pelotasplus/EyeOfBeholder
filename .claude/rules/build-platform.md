# Build and platform notes

## Platform Targets
- **Android**: Min SDK 24, Target SDK 36, Compile SDK 36
- **iOS**: arm64 + simulatorArm64 with static framework
- **Desktop (JVM)**: Primary development target, JVM 11
- **Web**: JS + Wasm support

## Important Build Settings
- Kotlin code style: official
- Gradle configuration cache: enabled
- Gradle build cache: enabled
- Opt-in to `kotlin.ExperimentalUnsignedTypes` (required for binary parsing)

## Logging
Uses **Kermit** for multiplatform logging:
```kotlin
Logger.d { "Debug message" }
Logger.e { "Error message" }
```

## Resource Files
Game assets are loaded via Compose Multiplatform resources (`Res.readBytes()`). The `ResourceRepository` maintains a manifest of 250+ asset files (CPS, MAZ, VCN, VMP, PAL, INF, etc.).

## The sounds are rendered, and are not in the repository

`files/audio/` holds what the game plays, and none of it is in the repository:
it sits inside `composeResources/files`, which `.gitignore` keeps out whole,
along with the game data it is made from. So a fresh checkout has no sounds at
all, and `SoundBanksTest` fails with `MissingResourceException:
files/audio/clips.json` until they are made. That failure means the clips have
not been rendered, not that anything is broken.

The `.ADL` files are scores for a sound chip rather than samples, so they are
rendered ahead of time:

```bash
brew install adplay
scripts/render-adlib.py ../EOB2 composeApp/src/commonMain/composeResources/files/audio
```

Ten banks give 745 tracks, which fold down to about 181 files — a door sounds
the same on every floor — named by the hash of their samples, with
`clips.json` beside them putting bank and track back together. Rendering takes
a few minutes. Tracks that come out silent are left out rather than written as
silence, so a missing number is not a missing clip.

## Web development loop
`./gradlew :composeApp:wasmJsBrowserDevelopmentRun --continuous` recompiles on
save and reloads the page (full reload — app state is lost). Without
`--continuous` the dev server keeps serving the build it started with.
Desktop hot reload (`./gradlew :composeApp:hotRunJvm`) preserves state and is
the faster loop for UI work.
