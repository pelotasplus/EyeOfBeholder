package pl.pelotasplus.eyeofbeholder.data.model

import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.model.script.Conditional
import pl.pelotasplus.eyeofbeholder.data.model.script.Dialog
import pl.pelotasplus.eyeofbeholder.data.model.script.End
import pl.pelotasplus.eyeofbeholder.data.model.script.Eval
import pl.pelotasplus.eyeofbeholder.data.model.script.Goto
import pl.pelotasplus.eyeofbeholder.data.model.script.NewLevelOrMonster
import pl.pelotasplus.eyeofbeholder.data.model.script.Return
import pl.pelotasplus.eyeofbeholder.data.model.script.Script
import pl.pelotasplus.eyeofbeholder.data.model.script.ScriptOffset
import pl.pelotasplus.eyeofbeholder.data.model.script.SetFlag
import pl.pelotasplus.eyeofbeholder.data.model.script.Teleport
import kotlin.jvm.JvmInline

/** What running a trigger script asks the game to do. */
sealed interface ScriptOutcome {
    data object Nothing : ScriptOutcome

    /**
     * The script is waiting for the player to answer [dialog].
     *
     * A script sets its scene one instruction at a time — clear the view, draw
     * who is speaking, draw the frame — and only then asks. Those instructions
     * are collected in [scene], in the order the script ran them, so the
     * screen can put them up before the question.
     *
     * Feed the answer back with [LevelScriptRunner.answer] and [resumeAt] to
     * carry on from where it stopped.
     */
    data class AskThePlayer(
        val dialog: Dialog.RunDialog,
        val scene: List<Dialog>,
        val resumeAt: ScriptOffset,
    ) : ScriptOutcome

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

    /**
     * Carries on a script that stopped at a dialogue, with [answer] being the
     * button the player pressed, numbered from one.
     */
    fun answer(resumeAt: ScriptOffset, party: PartyState, answer: DialogAnswer): ScriptOutcome {
        Logger.d(TAG) { "Resuming at $resumeAt with answer $answer" }
        return run(resumeAt, party, answer)
    }

    private fun run(
        fromOffset: ScriptOffset,
        party: PartyState,
        dialogAnswer: DialogAnswer? = null,
    ): ScriptOutcome {
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

        // what the script has drawn so far for the question it is building up
        val scene = mutableListOf<Dialog>()

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
                    if (!evaluate(token.tokens, party, dialogAnswer).isTrue) {
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

                // Everything after this depends on the player's answer, so
                // guessing one would run a branch nobody chose — which is how
                // walking past the Darkmoon priest used to throw the party
                // down a level. Stop and ask.
                is Dialog.RunDialog -> return ScriptOutcome.AskThePlayer(
                    dialog = token,
                    scene = scene.toList(),
                    resumeAt = script.getOrNull(index + 1)?.offset ?: return moved.asOutcome(),
                )

                Dialog.CloseDialog -> scene.clear()

                // anything else the script draws while setting up its question
                is Dialog -> scene += token

                else -> Unit
            }
            index++
        }
        return moved.asOutcome()
    }

    private fun Location?.asOutcome(): ScriptOutcome =
        if (this == null) ScriptOutcome.Nothing else ScriptOutcome.MoveParty(this)

    /** Postfix stack machine over a condition's tokens. */
    private fun evaluate(
        tokens: List<Conditional>,
        party: PartyState,
        dialogAnswer: DialogAnswer?,
    ): ConditionValue {
        val stack = ArrayDeque<ConditionValue>()
        fun pop() = stack.removeLastOrNull() ?: ConditionValue.FALSE
        fun push(value: ConditionValue) = stack.addLast(value)
        fun push(condition: Boolean) = stack.addLast(ConditionValue.of(condition))

        tokens.forEach { token ->
            when (token) {
                is Conditional.ImmediateShort -> push(ConditionValue(token.value))
                is Conditional.GetLevelFlag -> push(levelFlags.isNotEmpty())
                is Conditional.GetPartyDirection -> push(ConditionValue(party.facing.ordinal))
                is Conditional.DialogResult -> push(ConditionValue(dialogAnswer?.number ?: 0))
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
