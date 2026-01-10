package pl.pelotasplus.eyeofbeholder

import pl.pelotasplus.eyeofbeholder.data.model.Cps
import pl.pelotasplus.eyeofbeholder.data.model.Palette

expect fun saveCpsImage(cps: Cps, palette: Palette, outputPath: String)
