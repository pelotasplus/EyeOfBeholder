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

    /**
     * Something in flight crossed onto the square.
     *
     * It fires once per square entered, for the square entered, and it is how
     * a trap does its work further down the corridor than it stands: a
     * fireball loosed at one end sets off whatever it passes over on the way.
     */
    SOMETHING_FLEW_IN(0x10),

    /** The wall facing the party was clicked, wherever the party stand. */
    WALL_CLICKED(0x40),

    /**
     * Something in a hand was used, and the wall the party face is being asked
     * what it makes of that.
     *
     * It fires for whatever was used and leaves the script to care what it
     * was: a window is broken by a weapon and not by a torch, and the script
     * is what asks. Distinct from [WALL_CLICKED], which is the same wall
     * merely pointed at — one reads a carving, the other takes a sword to it.
     */
    ITEM_USED_ON_WALL(0x100),
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
