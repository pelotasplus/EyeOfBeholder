package pl.pelotasplus.eyeofbeholder.data.model

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint

/**
 * Paints [rows] of pixels into a [width]×[height] bitmap, a run of equal
 * colours at a time. Transparent pixels are left unset, so whatever is behind
 * the image shows through.
 *
 * A rect per pixel is 64,000 draw calls for a 320×200 screen and was most of
 * what a frame cost; runs of one colour are long enough in this artwork to cut
 * that to a third.
 */
internal fun rasterize(rows: List<List<RGB>>, width: Int, height: Int): ImageBitmap {
    val bitmap = ImageBitmap(width, height)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply { isAntiAlias = false }

    rows.forEachIndexed { y, row ->
        var x = 0
        while (x < row.size) {
            val rgb = row[x]

            var past = x + 1
            while (past < row.size && row[past] == rgb) past++

            if (!rgb.transparent) {
                paint.color = Color(rgb.red, rgb.green, rgb.blue)
                canvas.drawRect(
                    left = x.toFloat(),
                    top = y.toFloat(),
                    right = past.toFloat(),
                    bottom = y + 1f,
                    paint = paint,
                )
            }

            x = past
        }
    }
    return bitmap
}
