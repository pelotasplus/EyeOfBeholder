# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Eye of Beholder is a Kotlin Multiplatform project (Android, iOS, Web, Desktop) built with Compose Multiplatform.
It's a game asset viewer/debugger for parsing and rendering classic Eye of the Beholder game files including level data (INF), mazes (MAZ), tiles (VCN), viewport mappings (VMP), palettes (PAL), graphics (CPS), decorations (DEC), items (ITEM.DAT), and more.
Eventually it will be a full Eye of Beholder recreation.

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

# Run web app (Wasm - faster, modern browsers)
./gradlew :composeApp:wasmJsBrowserDevelopmentRun

# Run web app (JS - slower, older browser support)
./gradlew :composeApp:jsBrowserDevelopmentRun
```

### Test Commands
```bash
# Run all tests
./gradlew allTests

# Run JVM tests only
./gradlew jvmTest

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

## Architecture

### Pattern: MVVM (Model-View-ViewModel)

The codebase follows clean MVVM architecture with clear separation:

- **View Layer**: Composable screens using Jetpack Compose
- **ViewModel Layer**: State management with `StateFlow` and lifecycle awareness
- **Repository Layer**: Data access abstraction with interface/implementation pattern
- **Model Layer**: Domain models representing game data structures

### Package Structure

```
composeApp/src/commonMain/kotlin/pl/pelotasplus/eyeofbeholder/
├── di/                    # Dependency Injection (Koin)
├── data/
│   ├── model/            # Domain models (Inf, Maz, Vcn, Vmp, Item, etc.)
│   │   └── script/       # Script token types (29 opcodes)
│   ├── repository/       # Data access layer (10 repositories)
│   ├── ByteReader.kt     # Binary file parsing utility
│   └── LCWHelper.kt      # LCW compression/decompression
├── features/             # Feature modules (each self-contained)
│   ├── main_debug/      # Main menu / navigation hub
│   ├── inf_debug/       # Level information debugging
│   ├── maz_debug/       # Maze layout debugging
│   ├── vcn_debug/       # Tile set debugging
│   ├── pal_debug/       # Palette debugging
│   ├── cps_debug/       # Graphics debugging
│   ├── dec_debug/       # Decoration debugging
│   └── view_cone_debug/ # 3D viewport rendering
└── navigation/          # Navigation routing (8 routes)
```

### Feature Module Pattern

Each feature follows a consistent structure:

1. **ViewModel** (`*ViewModel.kt`): Manages state with `MutableStateFlow`, handles events
2. **Screen** (`*Screen.kt`): Composable UI with `collectAsStateWithLifecycle()`
3. **DI Module** (`di/Modules.kt`): Feature-specific dependency injection

```kotlin
// Standard feature pattern
@Stable
class MyViewModel(private val repository: MyRepository) : ViewModel() {
    val state = MutableStateFlow(State())
    fun onEvent(event: Event) { /* handle events */ }
}

@Composable
fun MyScreen(viewModel: MyViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // UI implementation
}

// DI registration
val sharedFeaturesMyModule = module {
    viewModelOf(::MyViewModel)
}
```

### Data Layer Architecture

**Three-tier system:**

1. **ResourceRepository**: Foundation layer
   - Reads raw binary files from Compose resources
   - Handles LCW compression/decompression
   - Maintains manifest of 250+ game asset files
   - All operations are `suspend` functions for async I/O

2. **Specialized Repositories**: Domain-specific parsing
   - **PalRepository**: `.PAL` → `Palette` (256 RGB colors)
   - **MazRepository**: `.MAZ` → `Maz` (32x32 dungeon grid)
   - **VcnRepository**: `.VCN` → `Vcn` (8x8 pixel tiles)
   - **VmpRepository**: `.VMP` → `Vmp` (viewport mapping data)
   - **InfRepository**: `.INF` → `Inf` (level data, scripts, items)
   - **CpsRepository**: `.CPS` → `Cps` (graphics/images)
   - **DecRepository**: `.DEC` → `Dec` (wall decorations)
   - **ItemsRepository**: `ITEM.DAT` → `List<Item>` (game items with types and names)
   - **ViewConeRepository**: Orchestrates multiple repos to render 3D viewport

3. **Result-Oriented Error Handling**: All repository methods return `Result<T>`
   ```kotlin
   repository.loadInf(name)
       .onSuccess { /* handle data */ }
       .onFailure { /* log and gracefully degrade */ }
   ```

### Key Domain Models

**Inf (Level Information)**
- Contains sublevel definitions, game scripts, messages, and items
- Scripts use 29 opcodes (SetWall, OpenDoor, CreateMonster, NewItem, ConsumeItem, etc.)
- Compressed with LCW algorithm

**Maz (Maze Layout)**
- 32x32 grid of squares
- Each square has 4 walls (north, east, south, west)
- WallType is a sealed class: `NoWall`, `FixedWall`, `DoorType*`, `StairUp/Down`, `Teleport`, etc.
- Supports 2D indexing: `maz[x, y]`

**Vcn (Tile Set)**
- Collection of 8x8 pixel tiles
- Dual palette support: backdrop vs wall rendering
- Used for rendering 3D views

**Vmp (Viewport Mapping)**
- Defines 176x120 pixel viewport (22x15 tiles)
- Backdrop layer (330 tiles) + 25 wall positions for 3D depth
- Tile indices encode: z-mask (bit 15), mirror_x (bit 14), tile_index (bits 0-13)

**Item & ItemType**
- `Item`: Individual item instance with location, level, linked-list pointers (next/prev), icon, flags
- `ItemType`: Category definition with RPG stats (damage dice, armor class, allowed classes, required hands)
- Items are parsed from `ITEM.DAT` and integrated into level data via `InfRepository`

**SubLevel**
- Composite of maz, vmp, vcn, palette, scripts, monsters, doors, decorations, and sound
- Represents a single floor/area within a level

**ViewPort (3D Rendering)**
- Composite view combining backdrop + wall layers
- 25 wall positions create 3D depth illusion across 4 rendering layers
- Supports wall flipping, transparency, doors (with stuck variants), stairs, and decorations
- Draws decorations via linked-list parts with position mapping

**WallPositionMapping & Direction**
- 25-element mapping from viewport wall positions to maze-relative coordinates
- `Direction` enum with `transformCoordinates()` and `transformWallSide()` for rotating coordinates based on player facing direction
- `WallSide` enum for cardinal directions in absolute maze coordinates

**WallRenderData & DoorRenderData**
- Pixel-level rendering configuration for each of the 25 wall positions
- Door-specific rendering offsets and dimensions

**Location**
- Simple x,y coordinate wrapper with parsing from packed byte format

**Script Tokens (29 types)**
- Control flow: Goto, GoSub, Return, End, Conditional, Eval
- World manipulation: SetWall, ToggleWall, OpenDoor, CloseDoor, Teleport
- Items: NewItem, ConsumeItem (DeleteHandItem/DeleteBlockItem)
- Entities: CreateMonster, Encounter, NewLevelOrMonster
- Effects: Message, Dialog, Sound, Damage, Launcher, SpecialEvent
- Other: SetFlag, ClearFlag, Wait, Turn, UpdateScreen

### Important Patterns

**Sealed Classes for Type Safety**
```kotlin
sealed class WallType {
    data object NoWall : WallType()
    data class FixedWall(val wallType: Int) : WallType()
    data class DoorTypeOneWithButton(val state: Int) : WallType()
    // ... exhaustive when expressions required
}
```

**Immutable Collections**
```kotlin
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class State(
    val items: ImmutableList<String> = persistentListOf()
)
```
Used throughout for state safety and efficient Compose recomposition.

**Binary Data Parsing (ByteReader)**
```kotlin
class ByteReader {
    fun readU8(): Int           // unsigned byte
    fun readI8(): Int           // signed byte
    fun readU16LE(): Int        // unsigned 16-bit little-endian
    fun readString(len: Int): String
    // ... safe binary parsing utilities
}
```

**LCW Compression**
- Custom decompression algorithm in `LCWHelper.kt`
- Five command types: copy-as-is, copy, large copy, fill, very large copy
- Supports both relative and absolute addressing modes

**Coroutine-Based Async**
```kotlin
viewModelScope.launch {
    repository.loadData(name)
        .onSuccess { data -> _state.update { /* ... */ } }
}
// Automatic cancellation on ViewModel destruction
```

## Dependency Injection (Koin)

The project uses Koin for DI with a modular structure:

1. **Root setup** in `di/InitKoin.kt`
2. **Data module** in `data/di/Modules.kt` (repositories)
3. **Feature modules** in `features/*/di/Modules.kt` (ViewModels)

Currently registered modules:
- `sharedDataModule` — all repositories
- `sharedFeaturesPalDebugModule`
- `sharedFeaturesCpsDebugModule`
- `sharedFeaturesDecDebugModule`
- `sharedFeaturesInfDebugModule`
- `sharedFeaturesMazDebugModule`
- `sharedFeaturesVcnDebugModule`
- `sharedFeaturesViewConeDebugModule`

To add a new feature:
```kotlin
// 1. Create feature DI module
val sharedFeaturesMyModule = module {
    viewModelOf(::MyViewModel)
}

// 2. Register in InitKoin.kt
modules(
    sharedDataModule,
    sharedFeaturesMyModule,
    // ... other modules
)
```

## Development Notes

### Platform Targets
- **Android**: Min SDK 24, Target SDK 36, Compile SDK 36
- **iOS**: arm64 + simulatorArm64 with static framework
- **Desktop (JVM)**: Primary development target, JVM 11
- **Web**: JS + Wasm support

### Important Build Settings
- Kotlin code style: official
- Gradle configuration cache: enabled
- Gradle build cache: enabled
- Opt-in to `kotlin.ExperimentalUnsignedTypes` (required for binary parsing)

### Logging
Uses **Kermit** for multiplatform logging:
```kotlin
Logger.d { "Debug message" }
Logger.e { "Error message" }
```

### Resource Files
Game assets are loaded via Compose Multiplatform resources (`Res.readBytes()`). The `ResourceRepository` maintains a manifest of 250+ asset files (CPS, MAZ, VCN, VMP, PAL, INF, etc.).

### Navigation
Type-safe navigation using `androidx.navigation.compose` with `@Serializable` sealed interface routes defined in `navigation/Route.kt`. Routes: `DebugGraph`, `MainDebug`, `DecDebug`, `PalDebug`, `CpsDebug`, `InfDebug`, `MazDebug`, `VcnDebug`, `ViewConeDebug`.

## Adding a New Feature

1. **Create domain model** in `data/model/MyData.kt`
2. **Create repository interface + impl** in `data/repository/MyRepository.kt`
3. **Add DI binding** in `data/di/Modules.kt`:
   ```kotlin
   factory<MyRepository> { MyRepositoryImpl(get()) }
   ```
4. **Create feature ViewModel** in `features/my_feature/MyViewModel.kt`
5. **Create feature Screen** in `features/my_feature/MyScreen.kt`
6. **Create feature DI module** in `features/my_feature/di/Modules.kt`
7. **Register in root DI** in `di/InitKoin.kt`
8. **Add navigation route** in `navigation/Route.kt` and wire in `App.kt`
