package pl.pelotasplus.eyeofbeholder.data.model

import co.touchlab.kermit.Logger
import kotlinx.collections.immutable.ImmutableList

/**
 * Maps view positions (0-25) to decoration wall positions (0-9).
 * @param xFlip 0=left side wall, 1=right side wall (for mirroring)
 * @param wall decoration position 0-9, -1=none available for this position
 * @param xDelta horizontal shift (multiply by 8 for pixels)
 */
data class DecorationPosition(
    val xFlip: Int,
    val wall: Int,
    val xDelta: Int,
)

/**
 * Decoration wall positions grid (0-9) relative to party:
 * ```
 * 9 7 3 7 9
 * 8 6 2 6 8
 * 8 5 1 5 8
 *   4 0 4
 *     ^=party pos.
 * ```
 */
/*
						// 0 Center
						// 1 Front near
						// 2 Front middle
						// 3 Front far
						// 4 Side near
						// 5 Side middle
						// 6 Side far
						// 7 Side very far
						// 8 Side-Side far
						// 9 Side-Side very far
 */
val decorationPositions = listOf(
    DecorationPosition(xFlip = 0, wall = -1, xDelta = 0),   // 0
    DecorationPosition(xFlip = 0, wall = 9, xDelta = 0),    // 1
    DecorationPosition(xFlip = 0, wall = 7, xDelta = 0),    // 2

    DecorationPosition(xFlip = 1, wall = 7, xDelta = 0),    // 3
    DecorationPosition(xFlip = 1, wall = 9, xDelta = 0),    // 4
    DecorationPosition(xFlip = 0, wall = -1, xDelta = 0),   // 5

    DecorationPosition(xFlip = 0, wall = 3, xDelta = -12),  // 6
    DecorationPosition(xFlip = 0, wall = 3, xDelta = -6),   // 7
    DecorationPosition(xFlip = 0, wall = 3, xDelta = 0),    // 8 - middle front wall
    DecorationPosition(xFlip = 0, wall = 3, xDelta = 6),    // 9
    DecorationPosition(xFlip = 0, wall = 3, xDelta = 12),   // 10

    DecorationPosition(xFlip = 0, wall = 8, xDelta = 0),    // 11
    DecorationPosition(xFlip = 0, wall = 6, xDelta = 0),    // 12

    DecorationPosition(xFlip = 1, wall = 6, xDelta = 0),    // 13
    DecorationPosition(xFlip = 1, wall = 8, xDelta = 0),    // 14

    DecorationPosition(xFlip = 0, wall = 2, xDelta = -10),  // 15
    DecorationPosition(xFlip = 0, wall = 2, xDelta = 0),    // 16 - middle front wall
    DecorationPosition(xFlip = 0, wall = 2, xDelta = 10),   // 17

    DecorationPosition(xFlip = 0, wall = 5, xDelta = 0),    // 18
    DecorationPosition(xFlip = 1, wall = 5, xDelta = 0),    // 19

    DecorationPosition(xFlip = 0, wall = 1, xDelta = -16),  // 20
    DecorationPosition(xFlip = 0, wall = 1, xDelta = 0),    // 21 - middle front wall
    DecorationPosition(xFlip = 0, wall = 1, xDelta = 16),   // 22

    DecorationPosition(xFlip = 0, wall = 4, xDelta = 0),    // 23

    DecorationPosition(xFlip = 1, wall = 4, xDelta = 0),    // 24
    DecorationPosition(xFlip = 0, wall = 0, xDelta = 0),    // 25
)

/** Floor decoration offsets for non-wall decorations (pits, pressure plates, etc.) */
private val floorDecorationOffsets = mapOf(
    6 to -88,
    7 to -40,

    9 to 40,
    10 to 88,

    15 to -59,
    17 to 59,

    20 to -98,
    22 to 98,
)

/** Front wall positions where mirroring flag (bit 0) applies */
private val frontWallPositions = setOf(6, 7, 8, 9, 10, 15, 16, 17, 20, 21, 22, 25)

@OptIn(ExperimentalUnsignedTypes::class)
class ViewPort {
    private val pixels = MutableList(ROWS * COLS) {
        RGB(0, 0, 0)
    }

    private fun draw(x: Int, y: Int, rgb: RGB) {
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

    fun drawDoorFrame(
        wallPosition: Int,
        vmp: Vmp,
        vcn: Vcn,
        pal: Palette
    ) {
        drawWall(
            wallType = 2,
            wallPosition = wallPosition,
            vmp = vmp,
            vcn = vcn,
            pal = pal
        )
    }

    fun drawStairsDown(
        wallPosition: Int,
        vmp: Vmp,
        vcn: Vcn,
        pal: Palette
    ) {
        drawWall(
            wallType = 4,
            wallPosition = wallPosition,
            vmp = vmp,
            vcn = vcn,
            pal = pal
        )
    }

    fun drawStairsUp(
        wallPosition: Int,
        vmp: Vmp,
        vcn: Vcn,
        pal: Palette
    ) {
        drawWall(
            wallType = 3,
            wallPosition = wallPosition,
            vmp = vmp,
            vcn = vcn,
            pal = pal
        )
    }

    fun drawWall(
        wallType: Int,
        wallPosition: Int,
        vmp: Vmp,
        vcn: Vcn,
        pal: Palette
    ) {
        val renderData = wallRenderData[wallPosition]

        val flipX = renderData.flipFlag == 1
        var offset = renderData.baseOffset

        val wallTiles = vmp.getWallType(wallType)

        for (y in 0 until renderData.visibleWidthInBlocks) {
            for (x in 0 until renderData.visibleHeightInBlocks) {
                val blockIndex = if (!flipX) {
                    x + y * TILES_PER_ROW + renderData.offsetInViewPort
                } else {
                    renderData.offsetInViewPort +
                            renderData.visibleHeightInBlocks - 1 - x +
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
                        pal.colors[pixel]
                    }
                }

                drawBlock(xpos * TILE_SIZE, ypos * TILE_SIZE, tilePixels, flipX = blockFlip)

                offset++
            }
            offset += renderData.skipValue
        }
    }

    fun drawBackdrop(
        vmp: Vmp,
        vcn: Vcn,
        pal: Palette
    ) {
        for (y in 0 until TILES_PER_COL) {
            for (x in 0 until TILES_PER_ROW) {
                val tile = vmp.backdrop[y * TILES_PER_ROW + x]
                val tilePixels = vcn.getTileAsBackdrop(tile.tileIndex).pixels.map { pixel ->
                    if (pixel == 0) {
                        RGB(0, 0, 0, transparent = true)
                    } else {
                        pal.colors[pixel]
                    }
                }

                val xpos = x * TILE_SIZE
                val ypos = y * TILE_SIZE

                drawBlock(xpos, ypos, tilePixels, flipX = tile.mirrorX)
            }
        }
    }

    /**
     * Draws a complete decoration, following the linked list of decoration parts.
     *
     * @param decoration The sublevel decoration containing Dec and Cps data
     * @param palette Color palette for rendering
     * @param wallPosition View position (0-25) where to render the decoration
     */
    fun drawDecoration(
        decoration: Decoration,
        palette: Palette,
        wallPosition: Int,
    ) {
        val dec = decoration.dec
        val cps = decoration.cps
        val isAtWall = decoration.wallType != 0

        // Start with the decoration at decorationID
        var currentIndex = decoration.decorationID

        // Follow the linked list of decoration parts
        while (currentIndex in dec.decorations.indices) {
            val decDecoration = dec.decorations[currentIndex]

            drawDecorationPart(
                decoration = decDecoration,
                rectangles = dec.rectangles,
                cps = cps,
                palette = palette,
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
        palette: Palette,
        wallPosition: Int,
        isAtWall: Boolean,
    ) {
        if (wallPosition !in decorationPositions.indices) {
            return
        }

        val decPos = decorationPositions[wallPosition]

        // Calculate horizontal shift
        val dx = if (isAtWall) {
            8 * decPos.xDelta
        } else {
            floorDecorationOffsets[wallPosition] ?: 0
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
        val mirrored = wallPosition in frontWallPositions && (decoration.flags and 0x01) != 0

        // Get screen coordinates from decoration data
        val screenY = decoration.yCoords[pos]
        val screenX = decoration.xCoords[pos]

        // Source rectangle in CPS (multiply by 8 for actual coords)
        val srcX = rect.x * 8
        val srcY = rect.y
        val srcWidth = rect.w * 8
        val srcHeight = rect.h

        Logger.d(TAG) { "drawDecorationPart:" }
        Logger.d(TAG) { "  wallPosition=$wallPosition -> decPos(xFlip=${decPos.xFlip}, wall=${decPos.wall}, xDelta=${decPos.xDelta})" }
        Logger.d(TAG) { "  pos=$pos, rectIndex=$rectIndex, rect=(${rect.x},${rect.y},${rect.w},${rect.h})" }
        Logger.d(TAG) { "  screenX=$screenX, screenY=$screenY, dx=$dx, mirrored=$mirrored" }
        Logger.d(TAG) { "  srcX=$srcX, srcY=$srcY, srcWidth=$srcWidth, srcHeight=$srcHeight" }
        Logger.d(TAG) { "  cps.width=${cps.width}, cps.height=${cps.height}" }

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

                val pixelIndex = cps.pixels[cpsIndex].toInt()

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

    companion object {
        private const val TAG = "ViewPort"
        const val ROWS = 120
        const val COLS = 176
        const val TILE_SIZE = 8
        const val TILES_PER_ROW = 22
        const val TILES_PER_COL = 15
    }
}
