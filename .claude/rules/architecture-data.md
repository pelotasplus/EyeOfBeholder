# Architecture: data layer and domain models

## Data Layer Architecture

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

## Key Domain Models

**Inf (Level Information)**
- Contains sublevel definitions, game scripts, messages, and items
- Scripts use 29 opcodes (SetWall, OpenDoor, CreateMonster, NewItem, ConsumeItem, etc.)
- Compressed with LCW algorithm

**Maz (Maze Layout)**
- 32x32 grid of squares
- Each square has 4 walls (north, east, south, west)
- WallType is a sealed class: `NoWall`, `FixedWall`, `Door(doorIndex, hasButton, state)`, `StairUp/Down`, `Decoration`
- Supports 2D indexing: `maz[x, y]`

**Item & ItemType**
- `Item`: Individual item instance with location, level, linked-list pointers (next/prev), icon, flags
- `ItemType`: Category definition with RPG stats (damage dice, armor class, allowed classes, required hands)
- Items are parsed from `ITEM.DAT` and integrated into level data via `InfRepository`

**SubLevel**
- Composite of maz, vmp, vcn, palette, scripts, monsters, doors, decorations, and sound
- Represents a single floor/area within a level

**Location**
- Simple x,y coordinate wrapper with parsing from packed byte format

**Script Tokens (29 types)**
- Control flow: Goto, GoSub, Return, End, Conditional, Eval
- World manipulation: SetWall, ToggleWall, OpenDoor, CloseDoor, Teleport
- Items: NewItem, ConsumeItem (DeleteHandItem/DeleteBlockItem)
- Entities: CreateMonster, Encounter, NewLevelOrMonster
- Effects: Message, Dialog, Sound, Damage, Launcher, SpecialEvent
- Other: SetFlag, ClearFlag, Wait, Turn, UpdateScreen

The rendering-side models (`Vcn`, `Vmp`, `ViewPort`, `ViewSlot`, `Direction`,
`WallSide`) live in `.claude/rules/rendering.md`.

## Important Patterns

**Sealed Classes for Type Safety**
```kotlin
sealed class WallType {
    data object NoWall : WallType()
    data class FixedWall(val wallType: Int) : WallType()
    data class Door(val doorIndex: Int, val hasButton: Boolean, val state: Int) : WallType()
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
