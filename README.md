# Eye of the Beholder II - Compose Multiplatform Remake

A work-in-progress recreation of the legendary **Eye of the Beholder II: The Legend of Darkmoon** (1991, VGA DOS version) built with Kotlin and Compose Multiplatform.

> **Status:** Work in Progress - Currently focusing on asset parsing and 3D viewport rendering.

## About

This project aims to bring the classic first-person dungeon crawler to modern platforms while staying faithful to the original VGA DOS experience. Built entirely in Kotlin, it runs on Android, iOS, Desktop, and Web.

## Features (In Progress)

- Binary file parsing for original game assets (MAZ, VCN, VMP, PAL, INF, CPS)
- LCW decompression algorithm implementation
- 3D viewport rendering with wall depth and perspective
- Level script interpreter (doors, triggers, monsters, etc.)
- Debug screens for exploring game data

## Platforms

| Platform | Status |
|----------|--------|
| Desktop (JVM) | Primary development target |
| Android | Supported |
| iOS | Supported |
| Web (Wasm/JS) | Supported |

## Quick Start

```bash
# Run desktop application
./gradlew :composeApp:run

# Run tests
./gradlew allTests
```

## Legal

This is a fan project for educational purposes. Original Eye of the Beholder II assets are not included - you'll need the original game files.

## License

MIT
