package pl.pelotasplus.eyeofbeholder.data.model

import co.touchlab.kermit.Logger
import kotlinx.collections.immutable.ImmutableList

/**
 * The 3D dungeon viewport renderer — the heart of the visual engine.
 *
 * Produces a 176×120 pixel image (22×15 tiles of 8×8) that simulates a
 * first-person 3D view of the dungeon, matching the original EoB DOS renderer.
 *
 * ## Rendering pipeline (called from ViewConeRepository.renderPosition)
 * 1. **Backdrop** — fill the viewport with floor/ceiling tiles from the VMP backdrop
 * 2. **Walls** — for each of the 25 wall positions (back-to-front, 4 layers):
 *    - Transform position from player-relative to maze-absolute coordinates
 *    - Determine wall type at that maze position
 *    - Draw the appropriate tiles: solid wall, door, stairs, or decoration
 * 3. **Items** — draw item icons at visible positions (scaled by distance)
 *
 * ## Wall position system
 * The viewport shows walls at 25 positions organized in 4 depth layers:
 * ```
 * Layer 4 (3 tiles ahead):  positions 0-10  (7 columns: A B C D E F G)
 * Layer 3 (2 tiles ahead):  positions 11-17 (5 columns: H I J K L)
 * Layer 2 (1 tile ahead):   positions 18-22 (3 columns: M N O)
 * Layer 1 (current row):    positions 23-24 (2 side walls: P Q)
 * ```
 * Each position is fully described by one [ViewSlot] row in [viewSlots]:
 * maze mapping, wall/door render config, and decoration slot. Positions are
 * rendered back-to-front so closer walls naturally occlude farther ones.
 *
 * ## Item rendering
 * Items in wall niches (pos=8) are drawn at positions 21, 15-17 with
 * distance-based scaling (closer = larger). Items on the floor (pos 0-3)
 * are not yet rendered.
 *
 * ## Decoration rendering
 * Decorations follow a linked list via [Dec.Decoration.linkToNextDecoration]
 * to draw multi-part overlays (e.g. an alcove frame + shelf + items).
 * Position mapping uses [ViewSlot.decoration] which maps each view position
 * to one of 10 "decoration wall slots" with mirroring and horizontal offset.
 */
@OptIn(ExperimentalUnsignedTypes::class)
class ViewPort(
    private val vmp: Vmp,
    private val vcn: Vcn,
    private val palette: Palette,
) {
    private val pixels = MutableList(ROWS * COLS) {
        RGB(0, 0, 0, true)
    }

    /** How far away whatever last painted each pixel was. */
    private val distances = MutableList(ROWS * COLS) { DistanceFromParty.BEYOND_EVERYTHING }

    private var painting = DistanceFromParty.BEYOND_EVERYTHING
    private var hiddenByCloserThings = false

    /**
     * Everything drawn inside [block] is [distance] away from the party.
     *
     * Walls paint regardless of what is already there, so their existing order
     * keeps deciding how they meet each other. Only what stands on a square —
     * items, monsters — sets [hiddenByCloserThings], and is then cut off
     * wherever something closer has already painted.
     */
    fun <T> at(
        distance: DistanceFromParty,
        hiddenByCloserThings: Boolean = false,
        block: () -> T,
    ): T {
        val previousDistance = painting
        val previouslyHidden = this.hiddenByCloserThings
        painting = distance
        this.hiddenByCloserThings = hiddenByCloserThings
        try {
            return block()
        } finally {
            painting = previousDistance
            this.hiddenByCloserThings = previouslyHidden
        }
    }

    private fun draw(x: ScreenX, y: ScreenY, rgb: RGB) {
        if (x.value !in 0..<COLS) return
        if (y.value !in 0..<ROWS) return
        if (rgb.transparent) return

        val offset = y.value * COLS + x.value
        // something closer already claimed this pixel
        if (hiddenByCloserThings && painting > distances[offset]) return

        pixels[offset] = rgb
        distances[offset] = painting
    }

    private fun drawBlock(x: ScreenX, y: ScreenY, tilePixels: List<RGB>, flipX: Boolean = false) {
        for (py in 0 until TILE_SIZE) {
            for (px in 0 until TILE_SIZE) {
                val pixelX = if (flipX) {
                    x + (TILE_SIZE - 1 - px)
                } else {
                    x + px
                }
                val pixelY = y + py
                if (pixelX.value in 0 until COLS && pixelY.value in 0 until ROWS) {
                    val rgb = tilePixels[py * TILE_SIZE + px]
                    if (rgb.transparent.not()) {
                        draw(pixelX, pixelY, rgb)
                    }
                }
            }
        }
    }

    fun drawWall(wallSet: WallSet, wallPosition: Int) {
        drawWall(wallSet.vmpIndex, wallPosition)
    }

    /**
     * Fills a wall position with flat red, to make something the renderer could
     * not draw impossible to miss instead of quietly leaving a hole.
     *
     * Whether it is drawn at all is [SHOW_UNDRAWABLE_WALLS].
     */
    fun drawUndrawableWall(wallPosition: Int) {
        if (!SHOW_UNDRAWABLE_WALLS) return

        val renderData = viewSlots[wallPosition].wall

        for (y in 0 until renderData.heightInTiles) {
            for (x in 0 until renderData.widthInTiles) {
                val blockIndex = x + y * TILES_PER_ROW + renderData.offsetInViewPort
                val left = (blockIndex % TILES_PER_ROW) * TILE_SIZE
                val top = (blockIndex / TILES_PER_ROW) * TILE_SIZE

                for (py in 0 until TILE_SIZE) {
                    for (px in 0 until TILE_SIZE) {
                        draw(ScreenX(left + px), ScreenY(top + py), UNDRAWABLE)
                    }
                }
            }
        }
    }

    fun drawWall(
        wallSetIndex: Int,
        wallPosition: Int,
    ) {
//        Logger.d(TAG) { "drawWall wallPosition: $wallPosition wallSetIndex: $wallSetIndex" }

        val renderData = viewSlots[wallPosition].wall

        val flipX = renderData.flipFlag == 1
        var offset = renderData.baseOffset

        val wallTiles = vmp.getWallType(wallSetIndex)

        for (y in 0 until renderData.heightInTiles) {
            for (x in 0 until renderData.widthInTiles) {
                val blockIndex = if (!flipX) {
                    x + y * TILES_PER_ROW + renderData.offsetInViewPort
                } else {
                    renderData.offsetInViewPort +
                            renderData.widthInTiles - 1 - x +
                            y * TILES_PER_ROW
                }

                val xpos = blockIndex % TILES_PER_ROW
                val ypos = blockIndex / TILES_PER_ROW

                val tile = wallTiles[offset]

                val blockFlip = tile.mirrorX xor flipX

                val tilePixels = vcn.getTileAsWall(tile.tileIndex).map {
                    palette.colorOrTransparent(it)
                }

                drawBlock(ScreenX(xpos * TILE_SIZE), ScreenY(ypos * TILE_SIZE), tilePixels, flipX = blockFlip)

                offset++
            }
            offset += renderData.skipValue
        }
    }

    fun drawBackdrop() {
        for (y in 0 until TILES_PER_COL) {
            for (x in 0 until TILES_PER_ROW) {
                val tile = vmp.backdrop[y * TILES_PER_ROW + x]
                val tilePixels = vcn.getTileAsBackdrop(tile.tileIndex).map {
                    palette.colorOrTransparent(it)
                }

                val xpos = x * TILE_SIZE
                val ypos = y * TILE_SIZE

                drawBlock(ScreenX(xpos), ScreenY(ypos), tilePixels, flipX = tile.mirrorX)
            }
        }
    }

    fun drawDoor(
        wallPosition: Int,
        door: Door,
        showButton: Boolean,
        stuckDoor: Boolean = false
    ) {
        drawWall(WallSet.DOOR_FRAME, wallPosition)

        val renderData = viewSlots[wallPosition].door

        if (renderData.offsetInViewPortX == null) {
            Logger.d(TAG) { "Skipping door rendering for wallPosition $wallPosition as offsetInViewPortX is -1" }
            return
        }

        val rectangle = door.rectangles[renderData.rectangleIndex]

        val cps = door.cps

        val deltaY = if (stuckDoor) {
            5
        } else {
            0
        }

        for (srcX in rectangle.x until rectangle.x + rectangle.w) {
            for (srcY in rectangle.y + deltaY until rectangle.y + rectangle.h) {
                val pixel = cps.pixels[srcY * cps.width + srcX]
                val color = palette.colors[pixel.value]

                val targetX = ScreenX(srcX - rectangle.x + renderData.offsetInViewPortX)
                val targetY = ScreenY(srcY - rectangle.y + renderData.offsetInViewPortY - deltaY)
                draw(targetX, targetY, color)
            }
        }

        if (showButton && renderData.buttonIndex != null) {
            val button = door.buttons[renderData.buttonIndex]

            for (srcX in button.x until button.x + button.w) {
                for (srcY in button.y until button.y + button.h) {
                    val pixel = cps.pixels[srcY * cps.width + srcX]
                    val color = palette.colors[pixel.value]

                    val targetX = ScreenX(srcX - button.x + button.posX)
                    val targetY = ScreenY(srcY - button.y + button.posY)
                    draw(targetX, targetY, color)
                }
            }
        }
    }

    /**
     * Draws a complete decoration, following the linked list of decoration parts.
     *
     * @param decoration The sublevel decoration containing Dec and Cps data
     * @param wallPosition View position (0-24) where to render the decoration
     */
    fun drawDecoration(
        decoration: Decoration,
        wallPosition: Int,
    ) {
        val dec = decoration.dec
        val cps = decoration.cps
        val isAtWall = decoration.wallType != 0

//        Logger.d(TAG) { "drawDecoration wallPosition: $wallPosition isAtWall: $isAtWall decoration ${decoration.decorationID}"}

        // Start with the decoration at decorationID
        var currentIndex = decoration.decorationID

        // Follow the linked list of decoration parts
        while (currentIndex in dec.decorations.indices) {
            val decDecoration = dec.decorations[currentIndex]

            drawDecorationPart(
                decoration = decDecoration,
                rectangles = dec.rectangles,
                cps = cps,
                wallPosition = wallPosition,
                isAtWall = isAtWall,
            )

            // Move to next linked decoration
            currentIndex = decDecoration.linkToNextDecoration
            // linkToNextDecoration of 0 means end of chain
            if (currentIndex == 0) break
        }
    }

    /**
     * Draws a single decoration part.
     */
    private fun drawDecorationPart(
        decoration: Dec.Decoration,
        rectangles: ImmutableList<Dec.DecorationRectangle>,
        cps: Cps,
        wallPosition: Int,
        isAtWall: Boolean,
    ) {
        if (wallPosition !in viewSlots.indices) {
            return
        }

        val slot = viewSlots[wallPosition]
        val decPos = slot.decoration

        // Calculate horizontal shift
        val dx = if (isAtWall) {
            8 * decPos.xDelta
        } else {
            slot.floorDecorationX
        }

        // Get decoration wall position (0-9)
        val pos = decPos.wall
        if (pos < 0 || pos >= decoration.rectangleIndices.size) {
            return
        }

        // Get rectangle index for this view position
        val rectIndex = decoration.rectangleIndices[pos]
        if (rectIndex == 0xFF || rectIndex >= rectangles.size) {
            return
        }

        val rect = rectangles[rectIndex]

        // Check if mirroring applies (bit 0 of flags, only for front walls)
        val mirrored = slot.isFrontWall && (decoration.flags and 0x01) != 0

        // Get screen coordinates from decoration data
        val screenY = decoration.yCoords[pos]
        val screenX = decoration.xCoords[pos]

        // Source rectangle in CPS (multiply by 8 for actual coords)
        val srcX = rect.x * 8
        val srcY = rect.y
        val srcWidth = rect.w * 8
        val srcHeight = rect.h

        // Draw pixels
        var targetY = screenY
        for (j in srcY until srcY + srcHeight) {
            var targetX = if (mirrored) {
                22 * 8 - screenX - 1
            } else {
                screenX
            }

            for (i in srcX until srcX + srcWidth) {
                // Get pixel from CPS
                val cpsIndex = j * cps.width + i
                if (cpsIndex < 0 || cpsIndex >= cps.pixels.size) continue

                // draw() skips transparent (index 0) and out-of-bounds pixels
                val color = palette.colorOrTransparent(cps.pixels[cpsIndex])

                // Calculate final screen position
                val finalX = if (mirrored) {
                    ScreenX(targetX + dx)
                } else if (decPos.xFlip == 1) {
                    // Right side walls - mirror horizontally
                    ScreenX(22 * 8 - (targetX + dx))
                } else {
                    ScreenX(targetX + dx)
                }

                draw(finalX, ScreenY(targetY), color)

                if (mirrored) targetX-- else targetX++
            }
            targetY++
        }
    }

    fun getRows(): List<List<RGB>> {
        return pixels.chunked(COLS)
    }

    /**
     * Draws a floor item (pos 0-3) lying in one quadrant of a visible block.
     *
     * Placement is quadrant-accurate via [blockScreenCoords] (baseline y 124),
     * so an item keeps its side of the square as the party approaches and
     * steps onto it. On the party's own square (block 16) only the two
     * quadrants ahead are visible — [scaleSteps] is -1 for the rear ones and
     * the caller skips them. The per-item screen jitter the original adds is
     * not implemented yet.
     *
     * @param largeIcons The floor-item icon sheet (ITEML1.CPS)
     * @param iconIdx Item icon index
     * @param blockIndex Visible-block index 0-17 into [blockScreenCoords]
     * @param viewQuadrant View-relative sub-position 0-3
     * @param scaleSteps 2/3 shrink steps from [itemScaleSteps]
     */
    fun drawFloorItem(
        largeIcons: Cps,
        iconIdx: ItemIconId,
        blockIndex: Int,
        viewQuadrant: Int,
        scaleSteps: ScaleSteps,
    ) {
        Logger.d(TAG) { "drawFloorItem $iconIdx block=$blockIndex quadrant=$viewQuadrant scale=$scaleSteps" }

        var icon = largeIcons.getItemIcon(iconIdx) ?: return
        repeat(scaleSteps.value) { icon = scaleDown(icon) }

        val coordIndex = (blockIndex * 5 + viewQuadrant) * 2
        val startX = ScreenX(blockScreenCoords[coordIndex] + 88 - icon.w / 2)
        val startY = ScreenY(blockScreenCoords[coordIndex + 1] + 124 - icon.h)

        drawIcon(icon, startX, startY, fadeSteps = scaleSteps)
    }

    /**
     * Draws a niche/shelf item (pos 8) centered in its alcove.
     *
     * @param smallIcons The niche-item icon sheet (ITEMS1.CPS)
     * @param iconIdx Item icon index
     * @param blockIndex Visible-block index 0-17 into [nicheItemX]
     * @param dim Depth row 0-3 (0 = three rows ahead); selects baseline and scale
     */
    fun drawNicheItem(
        smallIcons: Cps,
        iconIdx: ItemIconId,
        blockIndex: Int,
        dim: Int,
    ) {
        Logger.d(TAG) { "drawNicheItem $iconIdx block=$blockIndex dim=$dim" }

        val scaleSteps = itemScaleSteps[dim * 4]
        var icon = smallIcons.getItemIcon(iconIdx) ?: return
        repeat(scaleSteps.value) { icon = scaleDown(icon) }

        val startX = nicheItemX[blockIndex] - icon.w / 2
        val startY = nicheItemY[dim] - icon.h

        drawIcon(icon, startX, startY, fadeSteps = scaleSteps)
    }

    /**
     * Draws the sparks hanging over a teleporter square.
     *
     * Unlike everything else in the cone these are not scaled or faded with
     * distance: each depth row has its own blobs cut at the size it needs.
     *
     * @param decorations DECORATE.CPS, which the blobs are cut from
     * @param blockIndex Visible-block index 0-17 into [nicheItemX]
     * @param dim Depth row 0-3; the party's own row shows nothing
     * @param pulse Which half of the flicker to draw
     */
    fun drawTeleporter(
        decorations: Cps,
        blockIndex: Int,
        dim: Int,
        pulse: TeleporterPulse,
    ) {
        val haze = teleporterHazeAt(dim) ?: return
        val left = nicheItemX[blockIndex] - haze.leftOfNiche

        haze.clouds.forEachIndexed { index, cloud ->
            val blob = haze.blobFor(index, pulse)
            val icon = decorations.cut(blob.x, blob.y, blob.w, blob.h)

            for (spark in cloud.sparks) {
                drawIcon(
                    icon,
                    left + cloud.shiftedBy.dx + spark.dx,
                    haze.top + cloud.shiftedBy.dy + spark.dy,
                )
            }
        }
    }

    private fun drawIcon(
        icon: Cps.ItemIcon,
        startX: ScreenX,
        startY: ScreenY,
        fadeSteps: ScaleSteps = ScaleSteps(0),
    ) {
        for (y in 0 until icon.h) {
            for (x in 0 until icon.w) {
                val pixel = palette.fadedIndex(icon.pixels[y * icon.w + x], fadeSteps)
                draw(startX + x, startY + y, palette.colorOrTransparent(pixel))
            }
        }
    }

    /**
     * Draws a monster pose at one of the visible blocks.
     *
     * @param frame Near-size pose cut from the sprite sheet
     * @param decorations Overlays for this pose, drawn over the frame in order
     * @param blockIndex Visible-block index 0-17 into [blockScreenCoords]
     * @param subPosition View-relative sub-position: 0-3 (quadrant) or 4 (center)
     * @param mirrored Draw horizontally flipped (for right-facing side poses)
     * @param scaleSteps Number of 2/3 shrink steps for distance
     */
    fun drawMonster(
        frame: Cps.ItemIcon,
        decorations: List<MonsterDecoration>,
        blockIndex: Int,
        subPosition: Int,
        mirrored: Boolean,
        scaleSteps: ScaleSteps,
    ) {
        Logger.d(TAG) { "drawMonster block=$blockIndex subPos=$subPosition mirrored=$mirrored scale=$scaleSteps" }

        val icon = frame.shrunk(scaleSteps)

        val coordIndex = (blockIndex * 5 + subPosition) * 2
        val startX = ScreenX(blockScreenCoords[coordIndex] + 88 - icon.w / 2)
        val startY = ScreenY(blockScreenCoords[coordIndex + 1] + 127 - icon.h)

        blit(icon, startX, startY, mirrored, scaleSteps)

        for (decoration in decorations) {
            val overlay = decoration.frame.shrunk(scaleSteps)
            val offsetX = decoration.offsetX.shrunk(scaleSteps)
            val offsetY = decoration.offsetY.shrunk(scaleSteps)
            // the offset is measured from whichever edge the sprite starts at
            val left = if (mirrored) icon.w - offsetX - overlay.w else offsetX
            blit(overlay, startX + left, startY + offsetY, mirrored, scaleSteps)
        }
    }

    private fun blit(
        icon: Cps.ItemIcon,
        startX: ScreenX,
        startY: ScreenY,
        mirrored: Boolean,
        scaleSteps: ScaleSteps,
    ) {
        for (y in 0 until icon.h) {
            for (x in 0 until icon.w) {
                val srcX = if (mirrored) icon.w - 1 - x else x
                val pixel = palette.fadedIndex(icon.pixels[y * icon.w + srcX], scaleSteps)
                draw(startX + x, startY + y, palette.colorOrTransparent(pixel))
            }
        }
    }

    private fun Cps.ItemIcon.shrunk(scaleSteps: ScaleSteps): Cps.ItemIcon {
        var icon = this
        repeat(scaleSteps.value) { icon = scaleDown(icon) }
        return icon
    }

    /** An offset shrinks by the same 2/3 a step as the sprite it belongs to. */
    private fun Int.shrunk(scaleSteps: ScaleSteps): Int {
        var value = this
        repeat(scaleSteps.value) { value = value * 2 / 3 }
        return value
    }

    private fun scaleDown(
        itemIcon: Cps.ItemIcon
    ): Cps.ItemIcon {
        val output = mutableListOf<PaletteIndex>()

        // --- Row pass: keep row 0, keep row 1, skip row 2, repeat ---
        var row = 0
        while (row < itemIcon.h) {
            // keep row
            val rowStart = row * itemIcon.w
            output.addAll(scaleRow(itemIcon.pixels.subList(rowStart, rowStart + itemIcon.w)))
            row++

            if (row >= itemIcon.h) break

            // keep row
            val rowStart2 = row * itemIcon.w
            output.addAll(scaleRow(itemIcon.pixels.subList(rowStart2, rowStart2 + itemIcon.w)))
            row++

            if (row >= itemIcon.h) break

            // skip row
            row++
        }

        val outWidth = scaleRow(itemIcon.pixels.subList(0, itemIcon.w)).size
        val outHeight = output.size / outWidth
        return Cps.ItemIcon(w = outWidth, h = outHeight, pixels = output)
    }

    // Column pass: for every 6 pixels keep [0,1,3,4], drop [2,5]
    private fun scaleRow(row: List<PaletteIndex>): List<PaletteIndex> {
        val out = mutableListOf<PaletteIndex>()
        var col = 0
        while (col + 5 < row.size) {
            out.add(row[col])      // keep p0
            out.add(row[col + 1])  // keep p1
            // drop row[col + 2]   // drop p2
            out.add(row[col + 3])  // keep p3
            out.add(row[col + 4])  // keep p4
            // drop row[col + 5]   // drop p5
            col += 6
        }
        // remainder: keep whatever is left (1 or 2 pixels)
        while (col < row.size) {
            out.add(row[col++])
        }
        return out
    }


    companion object {
        private const val TAG = "ViewPort"

        /**
         * Paint walls the renderer could not draw bright red rather than
         * leaving a hole.
         *
         * Every level maps only the wall indices it uses, so the unreachable
         * corners of a maze refer to indices that have no appearance. Those are
         * expected and invisible. If red ever shows up on screen it means one
         * of them is reachable after all, which is a real gap worth chasing.
         */
        private const val SHOW_UNDRAWABLE_WALLS = true

        private val UNDRAWABLE = RGB(255, 0, 0, false)

        const val ROWS = 120
        const val COLS = 176
        const val TILE_SIZE = 8
        const val TILES_PER_ROW = 22
        const val TILES_PER_COL = 15

        /** Visible-block index of the party's own square in [blockScreenCoords]. */
        const val OWN_BLOCK_INDEX = 16
    }
}
