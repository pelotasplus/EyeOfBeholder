package pl.pelotasplus.eyeofbeholder.data.model

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorInfo
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

/**
 * Skia is given the four bytes of each pixel in the order it was told to
 * expect them, rather than the machine's own 32-bit layout, which is not the
 * same one in a browser as on a desktop.
 */
internal actual fun imageOf(rows: List<List<RGB>>, width: Int, height: Int): ImageBitmap {
    val bytes = ByteArray(width * height * BYTES_PER_PIXEL)

    rows.forEachIndexed { y, row ->
        var at = y * width * BYTES_PER_PIXEL
        row.forEach { rgb ->
            if (!rgb.transparent) {
                bytes[at] = rgb.red.toByte()
                bytes[at + 1] = rgb.green.toByte()
                bytes[at + 2] = rgb.blue.toByte()
                bytes[at + 3] = OPAQUE
            }
            at += BYTES_PER_PIXEL
        }
    }

    val bitmap = Bitmap()
    bitmap.allocPixels(
        ImageInfo(
            colorInfo = ColorInfo(ColorType.RGBA_8888, ColorAlphaType.PREMUL, null),
            width = width,
            height = height,
        )
    )
    bitmap.installPixels(bytes)
    return bitmap.asComposeImageBitmap()
}

private const val BYTES_PER_PIXEL = 4
private const val OPAQUE = 0xFF.toByte()
