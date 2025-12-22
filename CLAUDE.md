# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

EyeOfBeholder is a Kotlin Multiplatform project targeting Android, iOS, Web (JS and Wasm), and Desktop (JVM) using Compose Multiplatform for shared UI across all platforms.

## Build Commands

### Android
```bash
./gradlew :composeApp:assembleDebug
```

### Desktop (JVM)
```bash
./gradlew :composeApp:run
```

### Web (Wasm - faster, modern browsers)
```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun
```

### Web (JS - older browser support)
```bash
./gradlew :composeApp:jsBrowserDevelopmentRun
```

### iOS
Open `/iosApp` directory in Xcode and run from there, or use IDE run configurations.

### Build All Platforms
```bash
./gradlew build
```

## Testing

### Run All Tests
```bash
./gradlew allTests
```

### Run iOS Simulator Tests
```bash
./gradlew iosSimulatorArm64Test
```

### Run JVM Tests
```bash
./gradlew :composeApp:jvmTest
```

### Run Android Tests
```bash
./gradlew connectedAndroidTest
```

## Project Structure

### Source Sets
- `/composeApp/src/commonMain/kotlin` - Shared code for all platforms
- `/composeApp/src/androidMain/kotlin` - Android-specific code
- `/composeApp/src/iosMain/kotlin` - iOS-specific code (CoreCrypto, UIKit, etc.)
- `/composeApp/src/jvmMain/kotlin` - Desktop (JVM)-specific code
- `/composeApp/src/jsMain/kotlin` - JavaScript-specific code
- `/composeApp/src/wasmJsMain/kotlin` - WebAssembly-specific code
- `/composeApp/src/webMain/kotlin` - Shared web code (JS and Wasm)
- `/composeApp/src/commonTest/kotlin` - Shared tests

### iOS Application
- `/iosApp/iosApp` - iOS app entry point and SwiftUI code

## Architecture Notes

### Multiplatform Strategy
- Start with `commonMain` for code that works across all platforms
- Move to platform-specific source sets only when platform APIs are needed
- iOS targets: `iosArm64` (devices) and `iosSimulatorArm64` (simulator)
- The iOS framework is built as a static library named "ComposeApp"

### Dependencies
- Uses Gradle version catalogs (`gradle/libs.versions.toml`)
- Main dependencies: Compose Multiplatform, Lifecycle ViewModels, Coroutines
- Android min SDK: 24, target SDK: 36
- JVM target: Java 11
- Compose Hot Reload plugin enabled for faster development

### Main Entry Points
- Android: Activity Compose integration
- Desktop: `pl.pelotasplus.eyeofbeholder.MainKt`
- iOS: Xcode project in `/iosApp`
- Web: Browser executables for both JS and Wasm targets

## Package Structure
Root package: `pl.pelotasplus.eyeofbeholder`