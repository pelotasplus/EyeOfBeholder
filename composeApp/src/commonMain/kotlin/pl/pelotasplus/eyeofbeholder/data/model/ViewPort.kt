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

    private fun draw(x: Int, y: Int, rgb: RGB) {
        if (x !in 0..<COLS) return
        if (y !in 0..<ROWS) return
        if (rgb.transparent) return
        pixels[y * COLS + x] = rgb
    }

    private fun drawBlock(x: Int, y: Int, tilePixels: List<RGB>, flipX: Boolean = false) {
        for (py in 0 until TILE_SIZE) {
            for (px in 0 until TILE_SIZE) {
                val pixelX = if (flipX) {
                    x + (TILE_SIZE - 1 - px)
                } else {
                    x + px
                }
                val pixelY = y + py
                if (pixelX in 0 until COLS && pixelY in 0 until ROWS) {
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

    fun drawWall(
        wallSetIndex: Int,
        wallPosition: Int,
    ) {
        Logger.d(TAG) { "drawWall wallPosition: $wallPosition wallSetIndex: $wallSetIndex" }

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

                val tilePixels = vcn.getTileAsWall(tile.tileIndex).pixels.map { pixel ->
                    if (pixel == 0) {
                        RGB(0, 0, 0, transparent = true)
                    } else {
                        palette.colors[pixel]
                    }
                }

                drawBlock(xpos * TILE_SIZE, ypos * TILE_SIZE, tilePixels, flipX = blockFlip)

                offset++
            }
            offset += renderData.skipValue
        }
    }

    fun drawBackdrop() {
        for (y in 0 until TILES_PER_COL) {
            for (x in 0 until TILES_PER_ROW) {
                val tile = vmp.backdrop[y * TILES_PER_ROW + x]
                val tilePixels = vcn.getTileAsBackdrop(tile.tileIndex).pixels.map { pixel ->
                    if (pixel == 0) {
                        RGB(0, 0, 0, transparent = true)
                    } else {
                        palette.colors[pixel]
                    }
                }

                val xpos = x * TILE_SIZE
                val ypos = y * TILE_SIZE

                drawBlock(xpos, ypos, tilePixels, flipX = tile.mirrorX)
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
                val color = palette.colors[pixel]

                val targetX = srcX - rectangle.x + renderData.offsetInViewPortX
                val targetY = srcY - rectangle.y + renderData.offsetInViewPortY - deltaY
                draw(targetX, targetY, color)
            }
        }

        if (showButton && renderData.buttonIndex != null) {
            val button = door.buttons[renderData.buttonIndex]

            for (srcX in button.x until button.x + button.w) {
                for (srcY in button.y until button.y + button.h) {
                    val pixel = cps.pixels[srcY * cps.width + srcX]
                    val color = palette.colors[pixel]

                    val targetX = srcX - button.x + button.posX
                    val targetY = srcY - button.y + button.posY
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

        Logger.d(TAG) { "drawDecoration wallPosition: $wallPosition isAtWall: $isAtWall decoration ${decoration.decorationID}"}

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

//        Logger.d(TAG) { "drawDecorationPart:" }
//        Logger.d(TAG) { "  wallPosition=$wallPosition -> decPos(xFlip=${decPos.xFlip}, wall=${decPos.wall}, xDelta=${decPos.xDelta})" }
//        Logger.d(TAG) { "  pos=$pos, rectIndex=$rectIndex, rect=(${rect.x},${rect.y},${rect.w},${rect.h})" }
//        Logger.d(TAG) { "  screenX=$screenX, screenY=$screenY, dx=$dx, mirrored=$mirrored" }
//        Logger.d(TAG) { "  srcX=$srcX, srcY=$srcY, srcWidth=$srcWidth, srcHeight=$srcHeight" }
//        Logger.d(TAG) { "  cps.width=${cps.width}, cps.height=${cps.height}" }

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

                val pixelIndex = cps.pixels[cpsIndex]

                // Skip transparent pixels (index 0)
                if (pixelIndex == 0) {
                    if (mirrored) targetX-- else targetX++
                    continue
                }

                val color = palette.colors[pixelIndex]

                // Calculate final screen position
                val finalX = if (mirrored) {
                    targetX + dx
                } else if (decPos.xFlip == 1) {
                    // Right side walls - mirror horizontally
                    22 * 8 - (targetX + dx)
                } else {
                    targetX + dx
                }

                // Draw pixel if within bounds
                if (finalX in 0 until COLS && targetY in 0 until ROWS) {
                    draw(finalX, targetY, color)
                }

                if (mirrored) targetX-- else targetX++
            }
            targetY++
        }
    }

    fun getRows(): List<List<RGB>> {
        return pixels.chunked(COLS)
    }

    fun drawItem(
        smallIcons: Cps,
        largeIcons: Cps,
        iconIdx: Int,
        iconPosition: Int,
        wallPosition: Int
    ) {
        Logger.d(TAG) { "Draw item $iconIdx pos=$iconPosition wallPosition=$wallPosition"}

        val itemIcon = if (iconPosition == 8) {
            val origItemIcon = smallIcons.getItemIcon(iconIdx) ?: return

            when (wallPosition) {
                21 -> {
                    scaleDown(origItemIcon)
                }

                15, 16, 17 -> {
                    scaleDown(scaleDown(origItemIcon))
                }

                8 -> {
                    // too far even though shelf/niche is visible in-game
                    return
                }

                else -> {
                    // not showing at position
                    return
                }
            }
        } else {
            val origItemIcon = largeIcons.getItemIcon(iconIdx) ?: return

            when (wallPosition) {
                21 -> {
                    scaleDown(origItemIcon)
                }

                15, 16, 17 -> {
                    scaleDown(scaleDown(origItemIcon))
                }

                8 -> {
                    // too far even though shelf/niche is visible in-game
                    return
                }

                else -> {
                    // not showing at position
                    return
                }
            }
        }

        val startY = if (iconPosition == 8) {
            when (wallPosition) {
                21 -> 40
                15, 16, 17 -> 39
                else -> 0
            }
        } else {
            when (wallPosition) {
                21 -> 72
                15, 16, 17 -> 60
                else -> 0
            }
        }

        val startX = if (iconPosition == 8) {
            when (wallPosition) {
                21 -> (COLS - itemIcon.w) / 2
                17 -> (COLS - itemIcon.w) / 2 + 80
                16 -> (COLS - itemIcon.w) / 2
                15 -> 0
                8 -> (COLS - itemIcon.w) / 2
                else -> 0
            }
        } else {
            when (wallPosition) {
                21 -> (COLS - itemIcon.w) / 2 + 22
                17 -> (COLS - itemIcon.w) / 2 + 80
                16 -> (COLS - itemIcon.w) / 2 + 14
                15 -> 0
                8 -> (COLS - itemIcon.w) / 2
                else -> 0
            }
        }

        for (y in 0 until itemIcon.h) {
            for (x in 0 until itemIcon.w) {
                val pixelIndex = itemIcon.pixels[y * itemIcon.w + x]
                if (pixelIndex == 0) continue
                val color = palette.colors[pixelIndex]
                draw(startX + x, startY + y, color)
            }
        }
    }

    private fun scaleDown(
        itemIcon: Cps.ItemIcon
    ): Cps.ItemIcon {
        val output = mutableListOf<Int>()

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
    private fun scaleRow(row: List<Int>): List<Int> {
        val out = mutableListOf<Int>()
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
        const val ROWS = 120
        const val COLS = 176
        const val TILE_SIZE = 8
        const val TILES_PER_ROW = 22
        const val TILES_PER_COL = 15
    }
}
