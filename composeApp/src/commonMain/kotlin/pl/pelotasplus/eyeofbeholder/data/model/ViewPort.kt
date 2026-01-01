package pl.pelotasplus.eyeofbeholder.data.model

class ViewPort {
    // 176px by 120px
    private val pixels = MutableList(ROWS * COLS) {
        RGB(0, 0, 0)
    }

    fun draw(x: Int, y: Int, rgb: RGB) {
        pixels[y * COLS + x] = rgb
    }

    fun drawBlock(x: Int, y: Int, tilePixels: List<RGB>, flipX: Boolean = false) {
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

//    fun drawBlockXFlip(x: Int, y: Int, tilePixels: List<RGB>) {
//        for (py in 0 until TILE_SIZE) {
//            for (px in 0 until TILE_SIZE) {
//                val pixelX = x + (TILE_SIZE - 1 - px)
//                val pixelY = y + py
//                if (pixelX in 0 until COLS && pixelY in 0 until ROWS) {
//                    val rgb = tilePixels[py * TILE_SIZE + px]
//                    if (rgb.transparent.not()) {
//                        draw(pixelX, pixelY, rgb)
//                    }
//                }
//            }
//        }
//    }

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

    fun getRow(y: Int): List<RGB> {
        return pixels.subList(y * COLS, (y + 1) * COLS)
    }

    fun getRows(): List<List<RGB>> {
        return pixels.chunked(COLS)
    }

    companion object {
        const val ROWS = 120
        const val COLS = 176
        const val TILE_SIZE = 8
        const val TILES_PER_ROW = 22
    }
}

