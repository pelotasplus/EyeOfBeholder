package pl.pelotasplus.eyeofbeholder.data.model

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

internal actual fun imageOf(rows: List<List<RGB>>, width: Int, height: Int): ImageBitmap {
    val pixels = IntArray(width * height)

    rows.forEachIndexed { y, row ->
        var at = y * width
        row.forEach { rgb ->
            if (!rgb.transparent) {
                pixels[at] = (OPAQUE shl 24) or (rgb.red shl 16) or (rgb.green shl 8) or rgb.blue
            }
            at++
        }
    }

    return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()
}

private const val OPAQUE = 0xFF
