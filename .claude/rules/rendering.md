# Rendering models

**Vcn (Tile Set)**
- Collection of 8x8 pixel tiles
- Dual palette support: backdrop vs wall rendering
- Used for rendering 3D views

**Vmp (Viewport Mapping)**
- Defines 176x120 pixel viewport (22x15 tiles)
- Backdrop layer (330 tiles) + 25 wall positions for 3D depth
- Tile indices encode: z-mask (bit 15), mirror_x (bit 14), tile_index (bits 0-13)

**ViewPort (3D Rendering)**
- Renders one 176×120 frame; constructed fresh per frame with the sublevel's
  `vmp`/`vcn`/`palette`
- 25 wall positions create 3D depth illusion across 4 rendering layers
- Supports wall flipping, transparency, doors (with stuck variants), stairs,
  decorations, and item icons (placement driven by `ItemRenderSpec` tables)
- Item icons come in two sizes packed into different files: `Cps.locate()` says
  which, and the caller must read from the matching sheet — `ITEMS1.CPS` for
  small shapes, `ITEML1.CPS` for large. Cutting a small shape out of the large
  sheet yields whatever else sits at those coordinates
- `ViewPort.toImageBitmap()` (in `ViewPortImage.kt`) rasterizes the frame into
  a Compose `ImageBitmap`; screens display it with one `drawImage` blit using
  `FilterQuality.None`

**ViewSlot — the single source of truth for the 25 view positions**
- One row per position in `ViewSlot.kt`: label (e.g. "J-south"), maze-relative
  coordinates + wall side, `WallRenderData` (viewport tile geometry),
  `DoorRenderData` (door panel screen offsets), and `DecorationPosition`
  (mapping into the DEC file's 10-slot coordinate space — NOT screen pixels;
  decoration screen x/y comes from the DEC data itself)
- Editing how a position renders means editing one row in one file
- `WallSet` enum (in `Vmp.kt`) names the fixed VMP wall tile sets:
  `DOOR_FRAME(2)`, `STAIRS_UP(3)`, `STAIRS_DOWN(4)`

**SquarePlace & ViewPlace — where on a square a thing is**
- `SquarePlace` (in `SquarePlace.kt`) is the one name for it: the four corners
  of a square's floor named as the maze has them, the middle, and `IN_A_NICHE`.
  An item, a monster and a script's spawn all carry one as `place`
- `ViewPlace` is the same place as the party see it — `FAR_LEFT`, `FAR_RIGHT`,
  `NEAR_LEFT`, `NEAR_RIGHT`, `MIDDLE`, in the order the screen coordinates are
  kept in. `asSeenFacing()` and `onASquareFacing()` are inverses, and the test
  that they undo each other is what catches a rotation table written backwards
- A save writes the number the game writes, under the name a save already gives
  it (`pos`), so typing this did not break saves

**Direction & WallSide**
- `Direction` enum with `transformCoordinates()` and `transformWallSide()` for rotating coordinates based on player facing direction
- `WallSide` enum for cardinal directions in absolute maze coordinates

**DistanceFromParty — what hides what**
- Draw order cannot express depth on its own. A sprite must be drawn after the
  wall at the far end of its own square, but before the walls of the squares in
  front of it — and those walls must come first, because that is the order the
  walls need among themselves. Reordering the walls to suit sprites breaks
  wall-to-wall overlaps (it eats into the level 7 tapestry)
- So `ViewPort` keeps a distance per pixel alongside the colour. Walls always
  paint and record how far away they were; items and monsters pass
  `hiddenByCloserThings = true` and skip pixels a closer thing already claimed
- `DistanceFromParty` counts hundredths of a square: 100 straight ahead, 141
  diagonally ahead, 200 two rows back. Measuring to the square is the point —
  a tree straight ahead (100) hides what lies on the square beside it (141),
  which a row number alone cannot say
- Within one square its near face, contents and far face sit ±30 apart, well
  inside the 41 between the square ahead and the one diagonal to it, so a face
  never overtakes a neighbouring square
- Scope everything through `ViewPort.at(distance) { }`; nothing outside it
  needs to know the buffer exists

Changes here are guarded by the golden-image tests — see
`.claude/rules/golden-image-tests.md`.
