package pl.pelotasplus.eyeofbeholder.data.model

import androidx.compose.ui.graphics.ImageBitmap

/** Rasterizes the composed game screen into a 320×200 [ImageBitmap], once. */
fun PlayField.toImageBitmap(): ImageBitmap =
    rasterize(getRows(), PlayField.WIDTH, PlayField.HEIGHT)
