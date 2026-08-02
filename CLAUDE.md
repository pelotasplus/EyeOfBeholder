# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Eye of Beholder is a Kotlin Multiplatform project (Android, iOS, Web, Desktop) built with Compose Multiplatform.
It's a game asset viewer/debugger for parsing and rendering classic Eye of the Beholder game files including level data (INF), mazes (MAZ), tiles (VCN), viewport mappings (VMP), palettes (PAL), graphics (CPS), decorations (DEC), items (ITEM.DAT), and more.
Eventually it will be a full Eye of Beholder recreation.

## Project Rules

The detail lives in `.claude/rules/`, loaded automatically every session:

| File | Covers |
| --- | --- |
| `code-style.md` | When to write a comment and when not to |
| `golden-image-tests.md` | The rendering test gate; run before every commit that touches rendering |
| `probes-and-tests.md` | Starting with a probe, and what may decide an assertion's value |
| `architecture-features.md` | MVVM pattern, package layout, feature modules, navigation, Koin DI, adding a feature |
| `architecture-data.md` | Repositories, domain models, binary parsing, LCW, common Kotlin patterns |
| `rendering.md` | Vcn/Vmp/ViewPort/ViewSlot, Direction and WallSide |
| `build-platform.md` | Platform targets, build settings, logging, resources, web dev loop |

## Common Commands

### Build Commands
```bash
# Build all targets
./gradlew build

# Build Android debug APK
./gradlew :composeApp:assembleDebug

# Build desktop application
./gradlew jvmJar
```

### Run Commands
```bash
# Run desktop (JVM) application - primary development target
./gradlew :composeApp:run

# Run desktop with Compose hot reload (preserves state across edits)
./gradlew :composeApp:hotRunJvm

# Run web app (Wasm - faster, modern browsers)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun

# Run web app (JS - slower, older browser support)
./gradlew :composeApp:jsBrowserDevelopmentRun
```

### Test Commands
```bash
# Run all tests
./gradlew allTests

# Run JVM tests only (includes the golden-image rendering tests)
./gradlew jvmTest

# Accept intentional rendering changes / bootstrap new golden scenes
UPDATE_GOLDENS=1 ./gradlew :composeApp:jvmTest

# Run JS browser tests
./gradlew jsBrowserTest

# Run iOS simulator tests
./gradlew iosSimulatorArm64Test
```

### Other Useful Commands
```bash
# Clean build artifacts
./gradlew clean

# Run Android lint
./gradlew lint

# Check for configuration errors
./gradlew check
```

## Reference Sources

The parent directory (`../`) holds an EoB research library. Most important:

- `../scummvm` — full ScummVM checkout. `engines/kyra/` is the authoritative
  reverse-engineered reimplementation of the EoB engines (this project targets
  EoB2/Darkmoon: `darkmoon.cpp`, `sprites_eob.cpp`, `scene_eob.cpp`,
  `screen_eob.cpp`, `eobcommon.cpp`). The original game's lookup tables live
  verbatim in `devtools/create_kyradat/resources/eob2_dos.h` (`kEoB2Dsc*`).
  Look tables up here rather than re-deriving them — but do not name ScummVM
  symbols in comments, see `.claude/rules/code-style.md`.
- `../eye-of-the-beholder-file-formats` — file format documentation.
- `../EOB1`, `../EOB2` — original game data.

ScummVM is GPL-2.0+; it is used as a reference for understanding formats and
algorithms, with data tables originating from the original game binaries.
