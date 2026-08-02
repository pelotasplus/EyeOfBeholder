package pl.pelotasplus.eyeofbeholder.features.view_cone_debug

/**
 * Clickable regions of PLAYFLD.CPS, in the original 320×200 screen space.
 *
 * Taken verbatim from the original game's button table, where each entry is
 * `{ x, y, w, h }`.
 */
enum class PlayFieldControl(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
) {
    TURN_LEFT(3, 128, 21, 16),
    FORWARD(24, 128, 21, 16),
    TURN_RIGHT(45, 128, 21, 16),
    STRAFE_LEFT(3, 144, 21, 16),
    BACKWARD(24, 144, 21, 16),
    STRAFE_RIGHT(45, 144, 21, 16),
    CAMP(289, 177, 31, 21);

    fun contains(screenX: Int, screenY: Int): Boolean =
        screenX in x until x + width && screenY in y until y + height

    companion object {
        fun at(screenX: Int, screenY: Int): PlayFieldControl? =
            entries.firstOrNull { it.contains(screenX, screenY) }
    }
}
