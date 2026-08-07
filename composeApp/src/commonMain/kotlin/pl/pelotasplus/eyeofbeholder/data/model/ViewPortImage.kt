package pl.pelotasplus.eyeofbeholder.data.model

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint

/**
 * One icon on its own, for drawing outside the play field — what is being held
 * follows the pointer rather than sitting anywhere on the 320×200 screen, so
 * it cannot be painted into it.
 */
fun Cps.ItemIcon.toImageBitmap(palette: Palette): ImageBitmap {
    val bitmap = ImageBitmap(w, h)
    val canvas = Canvas(bitmap)
    val paint = Paint().apply { isAntiAlias = false }

    for (y in 0 until h) {
        for (x in 0 until w) {
            val index = pixels[y * w + x]
            if (index.isTransparent) continue
            val rgb = palette.colors[index.value]
            paint.color = Color(rgb.red, rgb.green, rgb.blue)
            canvas.drawRect(x.toFloat(), y.toFloat(), x + 1f, y + 1f, paint)
        }
    }
    return bitmap
}

/**
 * Rasterizes the rendered frame into a 176×120 [ImageBitmap], once. Display it
 * scaled with `FilterQuality.None` to keep the crisp retro pixels.
 */
fun ViewPort.toImageBitmap(): ImageBitmap =
    rasterize(getRows(), ViewPort.COLS, ViewPort.ROWS)
