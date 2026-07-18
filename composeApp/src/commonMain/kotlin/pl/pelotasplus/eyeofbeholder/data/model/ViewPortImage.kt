package pl.pelotasplus.eyeofbeholder.data.model

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint

/**
 * Rasterizes the rendered frame into a 176×120 [ImageBitmap], once.
 *
 * Transparent pixels are left unset so whatever is behind the image shows
 * through, matching the previous per-pixel Canvas drawing. Display it scaled
 * with `FilterQuality.None` to keep the crisp retro pixels.
 */
fun ViewPort.toImageBitmap(): ImageBitmap {
    val bitmap = ImageBitmap(ViewPort.COLS, ViewPort.ROWS)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply { isAntiAlias = false }

    getRows().forEachIndexed { y, row ->
        row.forEachIndexed { x, rgb ->
            if (rgb.transparent) return@forEachIndexed
            paint.color = Color(rgb.red, rgb.green, rgb.blue)
            canvas.drawRect(
                left = x.toFloat(),
                top = y.toFloat(),
                right = x + 1f,
                bottom = y + 1f,
                paint = paint
            )
        }
    }
    return bitmap
}
