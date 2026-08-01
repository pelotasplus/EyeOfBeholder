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

**Direction & WallSide**
- `Direction` enum with `transformCoordinates()` and `transformWallSide()` for rotating coordinates based on player facing direction
- `WallSide` enum for cardinal directions in absolute maze coordinates

Changes here are guarded by the golden-image tests — see
`.claude/rules/golden-image-tests.md`.
