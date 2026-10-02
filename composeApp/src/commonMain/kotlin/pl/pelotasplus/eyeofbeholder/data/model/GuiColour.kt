package pl.pelotasplus.eyeofbeholder.data.model

/**
 * The twelve colours the interface is drawn in, named as the game names them.
 *
 * These are not the panel's own art, which comes out of whatever palette the
 * floor is using — they are the dozen the game keeps a name for and draws
 * text, frames and bars in, and they are the same twelve on every floor.
 *
 * The numbers are indices into the palette and mean nothing else: 4 is not
 * greener than 3, and nothing about 6 says red. So they are written once here
 * and referred to by name everywhere, because a frame drawn in `PaletteIndex(6)`
 * says only that somebody once knew what 6 was.
 */
enum class GuiColour(val index: PaletteIndex) {
    WHITE(PaletteIndex(15)),
    LIGHT_RED(PaletteIndex(6)),
    DARK_RED(PaletteIndex(8)),
    LIGHT_BLUE(PaletteIndex(9)),
    BLUE(PaletteIndex(2)),
    DARK_BLUE(PaletteIndex(11)),
    YELLOW(PaletteIndex(5)),
    LIGHT_GREEN(PaletteIndex(4)),
    DARK_GREEN(PaletteIndex(3)),
    PURPLE(PaletteIndex(1)),
    BROWN(PaletteIndex(7)),
    BLACK(PaletteIndex(12)),
}
