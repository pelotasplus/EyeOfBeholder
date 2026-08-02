package pl.pelotasplus.eyeofbeholder.data.model

/**
 * The overlays that go on top of a monster's poses, parsed from the .DCR file
 * sitting next to its sprite sheet.
 *
 * A sheet is painted once, so every monster cut from it has the same body. An
 * overlay is what tells two of them apart in a way a recolor cannot: the long
 * hair one cleric wears and another does not is a shape, not a color. The
 * shapes are cut from the monster's own sheet, from the space left over
 * around the poses, and a monster type names up to three sets of them in
 * [MonsterProperty.decorations].
 */
data class Dcr(
    val name: String,
    val sets: List<Map<MonsterPose, Placement>>,
) {
    /**
     * Where one overlay is cut from the sheet, and where it goes once the
     * pose it belongs to has been drawn.
     *
     * @property offsetX From the left edge of the monster sprite, before the
     *   sprite is mirrored — a mirrored pose measures the same offset from its
     *   right edge instead
     * @property offsetY From the top edge of the monster sprite
     */
    data class Placement(
        val rect: MonsterFrameRect,
        val offsetX: Int,
        val offsetY: Int,
    )
}
