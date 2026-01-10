package pl.pelotasplus.eyeofbeholder

import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.model.Cps
import pl.pelotasplus.eyeofbeholder.data.model.Palette
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

private const val TAG = "saveCpsImage"

actual fun saveCpsImage(cps: Cps, palette: Palette, outputPath: String) {
    runCatching {
        val image = BufferedImage(cps.width, cps.height, BufferedImage.TYPE_INT_RGB)

        for (y in 0 until cps.height) {
            for (x in 0 until cps.width) {
                val pixel = cps.pixels[y * cps.width + x]
                val color = palette.colors[pixel]

                // Convert RGB to AWT color int
                // Ensure alpha is 0xFF for opaque
                val colorInt =
                    (0xFF shl 24) or (color.red shl 16) or (color.green shl 8) or color.blue
                image.setRGB(x, y, colorInt)
            }
        }

        val outputFile = File(outputPath)
        Logger.d(TAG) { "Output path $outputPath" }
        ImageIO.write(image, "jpg", outputFile)
    }.onFailure {
        Logger.e(TAG, it) { "Error while writing file" }
    }
}
