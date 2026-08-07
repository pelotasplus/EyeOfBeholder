package pl.pelotasplus.eyeofbeholder.data.model

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Hands [rows] of pixels to the platform as a finished image.
 *
 * Drawing a frame rectangle by rectangle is what this replaces, and the count
 * is the reason: a 320×200 screen is 64,000 of them, or 21,609 once runs of
 * one colour are joined, and each one is a call out of Kotlin into the drawing
 * library. Copying the pixels and handing them over is one.
 *
 * A pixel is opaque or it is nothing: [RGB.transparent] becomes a fully
 * transparent black, so whatever is behind the image shows through and it does
 * not matter whether the platform wants its colours premultiplied.
 */
internal expect fun imageOf(rows: List<List<RGB>>, width: Int, height: Int): ImageBitmap
