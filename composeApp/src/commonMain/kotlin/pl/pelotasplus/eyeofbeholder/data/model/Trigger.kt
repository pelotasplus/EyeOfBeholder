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

    /**
     * The party have walked far enough for the floor to be asked what it wants
     * doing about it — see [SubLevel.stepsUntilScriptCall].
     *
     * It reaches one square only, 0x0, which is a corner of the map nobody can
     * stand on or click. That square is the floor talking to itself, and what
     * it nearly always says is: put the monsters back. A floor cleared of what
     * it shipped with fills up again this way, and without it a level is
     * emptied for good the first time it is walked through.
     *
     * Steps rather than time, so a party who stand still are not restocked
     * around, and a party who pace a corridor are.
     */
    ENOUGH_STEPS_WALKED(0x20),

    /** The wall facing the party was clicked, wherever the party stand. */
    WALL_CLICKED(0x40),

    /**
     * The floor's own clock came round — see [ScriptTimer].
     *
     * Nobody has to be near the square: this is how a level does something on
     * its own, and it is the only event that reaches a square the party are
     * nowhere near. Every square answers it, the top three bits of the
     * accepted set being forced on, so a level can point its clock wherever it
     * likes without marking the square for it.
     */
    THE_CLOCK_CAME_ROUND(0x80),

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

    /**
     * A monster stepped onto the square, and [A_MONSTER_LEFT] for stepping off
     * it again. Both fire per step, the square left before the square reached.
     *
     * A monster weighs what the party weight: a plate set into the floor asks
     * how many are standing on it, and something wandering across one works it
     * exactly as a foot does. That is how a plate is held down with nobody
     * near it and nothing left lying on it — the eleventh floor's pit is
     * floored over that way.
     */
    A_MONSTER_ARRIVED(0x200),
    A_MONSTER_LEFT(0x400),

    /**
     * A spell was cast, and the square the party stand on is being asked what
     * it makes of that.
     *
     * The square, not the wall in front — casting is not aimed, so a carving
     * that answers a spell answers it from wherever the party happen to be
     * standing when they cast. Which spell it was is a question the script
     * asks separately, and which way they were facing another.
     */
    A_SPELL_WAS_CAST(0x800),
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
