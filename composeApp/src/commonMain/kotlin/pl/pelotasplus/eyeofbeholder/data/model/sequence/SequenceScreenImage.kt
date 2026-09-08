package pl.pelotasplus.eyeofbeholder.data.model.sequence

import androidx.compose.ui.graphics.ImageBitmap
import pl.pelotasplus.eyeofbeholder.data.model.imageOf

/** Rasterizes a scene's screen into a 320×200 [ImageBitmap], once. */
fun SequenceScreen.toImageBitmap(): ImageBitmap =
    imageOf(getRows(), SequenceScreen.WIDTH, SequenceScreen.HEIGHT)
