package pl.pelotasplus.eyeofbeholder.data.model

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint

/** Rasterizes the composed game screen into a 320×200 [ImageBitmap], once. */
fun PlayField.toImageBitmap(): ImageBitmap {
    val bitmap = ImageBitmap(PlayField.WIDTH, PlayField.HEIGHT)
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
