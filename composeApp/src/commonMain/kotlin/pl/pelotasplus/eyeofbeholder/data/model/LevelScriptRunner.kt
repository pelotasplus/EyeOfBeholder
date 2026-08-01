package pl.pelotasplus.eyeofbeholder.data.model

import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.model.script.Conditional
import pl.pelotasplus.eyeofbeholder.data.model.script.End
import pl.pelotasplus.eyeofbeholder.data.model.script.Eval
import pl.pelotasplus.eyeofbeholder.data.model.script.Goto
import pl.pelotasplus.eyeofbeholder.data.model.script.NewLevelOrMonster
import pl.pelotasplus.eyeofbeholder.data.model.script.Return
import pl.pelotasplus.eyeofbeholder.data.model.script.Script
import pl.pelotasplus.eyeofbeholder.data.model.script.SetFlag
import pl.pelotasplus.eyeofbeholder.data.model.script.Teleport
import kotlin.jvm.JvmInline

/** What running a trigger script asks the game to do. */
sealed interface ScriptOutcome {
    data object Nothing : ScriptOutcome

    data class ChangeLevel(
        val level: Int,
        val subLevel: Int,
        val location: Location,
        val direction: Direction?,
    ) : ScriptOutcome

    data class MoveParty(val destination: Location) : ScriptOutcome
}

/** A value on [LevelScriptRunner]'s condition stack. Zero is false. */
@JvmInline
private value class ConditionValue(val raw: Int) {
    val isTrue: Boolean get() = raw != 0

    companion object {
        val TRUE = ConditionValue(1)
        val FALSE = ConditionValue(0)

        fun of(condition: Boolean) = if (condition) TRUE else FALSE
    }
}

/**
 * Runs a square's trigger script far enough to move the party around and
 * between levels.
 *
 * This is not the full interpreter: it handles control flow, the two movement
 * instructions and level flags, and ignores everything else — monsters,
 * messages, damage, items.
 *
 * Conditions needing state this project does not model yet (party classes,
 * items in hand, dice rolls) evaluate to true, which keeps stairs reachable.
 * Each one is logged, so what a level actually depends on stays visible.
 */
class LevelScriptRunner(
    private val script: List<Script>,
    private val levelFlags: MutableSet<Int> = mutableSetOf(),
) {

    /** Runs the script of the square the party is on, if it reacts to [event]. */
    fun onEvent(
        triggers: List<Trigger>,
        event: ScriptEvent,
        party: PartyState,
    ): ScriptOutcome {
        val trigger = triggers
            .firstOrNull { it.location == party.position && it.flags.reactsTo(event) }
            ?: return ScriptOutcome.Nothing

        Logger.d(TAG) {
            "Running trigger at ${party.position} for $event from offset ${trigger.script.offset}"
        }
        return run(trigger.script.offset, party)
    }

    private fun run(fromOffset: Int, party: PartyState): ScriptOutcome {
        var index = script.indexOfFirst { it.offset == fromOffset }
        if (index < 0) {
            Logger.w(TAG) { "No script at offset $fromOffset" }
            return ScriptOutcome.Nothing
        }

        // A move does not end the script — the stairs scripts step the party
        // onto the staircase and only then change level, and
        // oeob_movePartyOrObject restores the abort flag to allow exactly that.
        var moved: Location? = null
        var steps = 0

        while (index in script.indices) {
            if (steps++ > MAX_STEPS) {
                Logger.w(TAG) { "Script from $fromOffset did not terminate after $MAX_STEPS steps" }
                return moved.asOutcome()
            }

            when (val token = script[index].token) {
                End, Return -> return moved.asOutcome()

                is Goto -> {
                    index = script.indexOfFirst { it.offset == token.offset }
                    if (index < 0) return moved.asOutcome()
                    continue
                }

                is Eval -> {
                    // a true condition falls through, a false one jumps
                    if (!evaluate(token.tokens, party).isTrue) {
                        index = script.indexOfFirst { it.offset == token.goto }
                        if (index < 0) return moved.asOutcome()
                        continue
                    }
                }

                is SetFlag.LevelFlag -> levelFlags.add(token.flag)

                is NewLevelOrMonster.ChangeLevel -> return ScriptOutcome.ChangeLevel(
                    level = token.level,
                    subLevel = token.subLevel,
                    location = token.location,
                    direction = token.direction,
                )

                is Teleport.MoveParty -> moved = token.destination

                else -> Unit
            }
            index++
        }
        return moved.asOutcome()
    }

    private fun Location?.asOutcome(): ScriptOutcome =
        if (this == null) ScriptOutcome.Nothing else ScriptOutcome.MoveParty(this)

    /** Postfix stack machine over a condition's tokens. */
    private fun evaluate(tokens: List<Conditional>, party: PartyState): ConditionValue {
        val stack = ArrayDeque<ConditionValue>()
        fun pop() = stack.removeLastOrNull() ?: ConditionValue.FALSE
        fun push(value: ConditionValue) = stack.addLast(value)
        fun push(condition: Boolean) = stack.addLast(ConditionValue.of(condition))

        tokens.forEach { token ->
            when (token) {
                is Conditional.ImmediateShort -> push(ConditionValue(token.value))
                is Conditional.GetLevelFlag -> push(levelFlags.isNotEmpty())
                is Conditional.GetPartyDirection -> push(ConditionValue(party.facing.ordinal))
                is Conditional.Equals -> push(pop().raw == pop().raw)
                is Conditional.NotEquals -> push(pop().raw != pop().raw)
                is Conditional.MoreThan -> push(pop().raw < pop().raw)
                is Conditional.LessThan -> push(pop().raw > pop().raw)
                is Conditional.And -> push(pop().isTrue && pop().isTrue)
                is Conditional.Or -> push(pop().isTrue || pop().isTrue)
                else -> {
                    Logger.d(TAG) { "Condition $token not modelled yet, assuming true" }
                    push(ConditionValue.TRUE)
                }
            }
        }
        return pop()
    }

    private companion object {
        const val TAG = "LevelScriptRunner"
        const val MAX_STEPS = 200
    }
}
