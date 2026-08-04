package pl.pelotasplus.eyeofbeholder.data.model

import pl.pelotasplus.eyeofbeholder.data.model.script.Script
import kotlin.jvm.JvmInline

/**
 * A square that reacts to something happening on it — the party stepping on,
 * or off, it — by running a script. One entry of the trigger map in Block C of
 * an .INF.
 */
data class Trigger(
    val location: Location,
    val flags: TriggerFlags,
    val script: Script,
)

/** What has happened to a square, which its one script asks about. */
enum class ScriptEvent(val mask: Int) {
    PARTY_ENTERED(1),
    PARTY_LEFT(2),

    /** Something was put down on the square, which is how a plate is pressed. */
    ITEM_PUT_DOWN(4),

    /** Something was taken off it again. */
    ITEM_TAKEN(8),

    /** The wall facing the party was clicked, wherever the party stand. */
    WALL_CLICKED(0x40),
}

/**
 * The flag word of a [Trigger], deciding which events the square reacts to.
 *
 * The packing stays as the game stores it: shift down three bits, force the
 * top three on, overlap with the event mask.
 */
@JvmInline
value class TriggerFlags(val raw: Int) {
    fun reactsTo(event: ScriptEvent): Boolean {
        val accepted = ((raw and 0xFFF8) shr 3) or 0xE0
        return (event.mask and accepted) != 0
    }
}
