package pl.pelotasplus.eyeofbeholder.data.model

/**
 * A wall decoration mapping — connects a maze wall index to visual and interactive content.
 *
 * When a maze square's wall has a type ≥ 25 (i.e. [Maz.WallType.Decoration]),
 * the byte value is looked up in the sublevel's decoration list by [wallIndex].
 * This determines:
 * - What base wall graphic to draw ([wallType] → VMP wall type index; 0 = no base wall)
 * - What overlay image to draw on top ([decorationID] → index into [Dec.decorations])
 * - What it does when clicked ([specialType], read as a [WallAction])
 *
 * ## Examples of decorations
 * - Lever on a stone wall: wallType=1 (stone wall backdrop), decorationID=lever graphic
 * - Alcove/niche: wallType=1, decorationID=alcove graphic, items can be placed inside
 * - Painting/carving: wallType=1, decorationID=painting graphic
 * - Pressure plate (floor): wallType=0 (no wall), decorationID=plate graphic
 *
 * @property wallIndex Index used by the .MAZ file to reference this decoration
 * @property wallType VMP wall type index for the base wall (0 = no base wall, decoration only)
 * @property decorationID Index into [Dec.decorations] for the overlay graphic
 * @property specialType What the wall does when it is clicked; read it through
 *   [doesWhenClicked] rather than by number
 * @property flags Additional rendering/behavior flags
 * @property dec Parsed DEC file with decoration rectangle and coordinate data
 * @property cps CPS image containing the decoration overlay pixels
 */
data class Decoration(
    val decorationWallIndex: Int, /* This is the index used by the .maz file. */
    val wallType: Int,  /* Index to what backdrop wall type that is being used. */
    val decorationID: Int, /* Index to and optional overlay decoration image in
                                  the DecorationData.decorations array in the
                                  [[eob.dat|.dat]] files. */
    val specialType: Int,
    val flags: WallFlags,
    val dec: Dec,
    val cps: Cps
) {
    override fun toString(): String {
        return "Decoration(decorationWallIndex=$decorationWallIndex, wallType=$wallType, decorationID=$decorationID, specialType=$specialType, flags=$flags, dec=..., cps=...)"
    }
}
