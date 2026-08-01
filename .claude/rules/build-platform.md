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

## Web development loop
`./gradlew :composeApp:wasmJsBrowserDevelopmentRun --continuous` recompiles on
save and reloads the page (full reload — app state is lost). Without
`--continuous` the dev server keeps serving the build it started with.
Desktop hot reload (`./gradlew :composeApp:hotRunJvm`) preserves state and is
the faster loop for UI work.
