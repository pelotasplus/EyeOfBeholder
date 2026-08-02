package pl.pelotasplus.eyeofbeholder.data.model

import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.model.script.Conditional
import pl.pelotasplus.eyeofbeholder.data.model.script.CreateMonster
import pl.pelotasplus.eyeofbeholder.data.model.script.Dialog
import pl.pelotasplus.eyeofbeholder.data.model.script.End
import pl.pelotasplus.eyeofbeholder.data.model.script.Eval
import pl.pelotasplus.eyeofbeholder.data.model.script.Goto
import pl.pelotasplus.eyeofbeholder.data.model.script.Message
import pl.pelotasplus.eyeofbeholder.data.model.script.NewLevelOrMonster
import pl.pelotasplus.eyeofbeholder.data.model.script.Return
import pl.pelotasplus.eyeofbeholder.data.model.script.Script
import pl.pelotasplus.eyeofbeholder.data.model.script.ScriptOffset
import pl.pelotasplus.eyeofbeholder.data.model.script.SetFlag
import pl.pelotasplus.eyeofbeholder.data.model.script.SetWall
import pl.pelotasplus.eyeofbeholder.data.model.script.Teleport
import kotlin.jvm.JvmInline

/**
 * What came of running a trigger script: the world it left behind, and what it
 * stopped for, if it stopped at all.
 *
 * A script moves and turns the party as it goes and may stop part way through,
 * so the two cannot be reported separately — [state] is the world as of
 * wherever it got to, whether or not it reached the end.
 */
data class ScriptRun(
    val state: GameState,
    /** null when the script simply ran out. */
    val stoppedTo: ScriptStop? = null,
)

/** Why a trigger script stopped before its end, and what it wants doing. */
sealed interface ScriptStop {

    /**
     * The script has put [textId] on screen and is waiting to be clicked.
     *
     * [buttons] is what the player may click: three answers to a question, or
     * the single word — usually "ok" — that acknowledges a speech. Both stop
     * the script the same way, and the reply to an answer is itself a speech
     * waiting to be acknowledged.
     *
     * A script sets its scene one instruction at a time — clear the view, draw
     * who is speaking, draw the frame — and only then speaks. Those
     * instructions are collected in [scene], in the order the script ran them,
     * so the screen can put them up first.
     *
     * Feed the click back with [LevelScriptRunner.answer] and [resumeAt] to
     * carry on from where it stopped.
     */
    data class AskThePlayer(
        val textId: DialogueTextId,
        val buttons: List<MessageId>,
        val scene: List<Dialog>,
        val resumeAt: ScriptOffset,
        /**
         * What the script printed into the box before speaking — the party's
         * own line, usually. Drawing the box again wipes them, which is how
         * the clerics' reply arrives on a clean box.
         */
        val said: List<MessageId> = emptyList(),
        /**
         * True when the script is being read rather than answered, so the one
         * button belongs in the corner speeches are read on rather than in the
         * row of answers under the text.
         */
        val waitsToBeRead: Boolean = false,
    ) : ScriptStop

    data class ChangeLevel(
        val level: Int,
        val subLevel: Int,
        val location: Location,
        val direction: Direction?,
    ) : ScriptStop

}

/** A value on [LevelScriptRunner]'s condition stack. Zero is false. */
@JvmInline
private value class ConditionValue(private val raw: Int) : Comparable<ConditionValue> {
    val isTrue: Boolean get() = raw != 0

    override fun compareTo(other: ConditionValue) = raw.compareTo(other.raw)

    companion object {
        val TRUE = ConditionValue(1)
        val FALSE = ConditionValue(0)

        fun of(condition: Boolean) = if (condition) TRUE else FALSE
        fun of(number: Int) = ConditionValue(number)
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
    /** Which level's flags this script reads and writes. */
    private val level: Int = 0,
) {

    /** Runs the script of the square the party is on, if it reacts to [event]. */
    fun onEvent(
        triggers: List<Trigger>,
        event: ScriptEvent,
        state: GameState,
    ): ScriptRun {
        val position = state.party.position
        val here = triggers.filter { it.location == position }
        val trigger = here.firstOrNull { it.flags.reactsTo(event) }

        if (trigger == null) {
            if (here.isNotEmpty()) {
                Logger.d(TAG) {
                    "Trigger at $position ignores $event, " +
                        "flags ${here.map { it.flags.raw.toHexString() }}"
                }
            }
            return ScriptRun(state)
        }

        Logger.d(TAG) {
            "Running trigger at $position for $event from offset ${trigger.script.offset}"
        }
        return run(trigger.script.offset, state)
    }

    /**
     * Carries on a script that stopped at a dialogue, with [answer] being the
     * button the player pressed, numbered from one.
     */
    fun answer(resumeAt: ScriptOffset, state: GameState, answer: DialogAnswer): ScriptRun {
        Logger.d(TAG) { "Resuming at $resumeAt with answer $answer" }
        return run(resumeAt, state, answer)
    }

    private fun run(
        fromOffset: ScriptOffset,
        state: GameState,
        dialogAnswer: DialogAnswer? = null,
    ): ScriptRun = runScript(fromOffset, state, dialogAnswer).also { result ->
        Logger.d(TAG) { "Script from $fromOffset stopped to ${result.stoppedTo ?: "nothing"}" }
    }

    private fun runScript(
        fromOffset: ScriptOffset,
        initial: GameState,
        dialogAnswer: DialogAnswer?,
    ): ScriptRun {
        var state = initial

        var index = script.indexOfFirst { it.offset == fromOffset }
        if (index < 0) {
            Logger.w(TAG) { "No script at offset $fromOffset" }
            return ScriptRun(state)
        }

        var steps = 0

        // what the script has drawn so far for the question it is building up
        val scene = mutableListOf<Dialog>()

        // and what it has written into the box, which the box outlives
        val said = mutableListOf<MessageId>()

        fun stop(stoppedTo: ScriptStop? = null) = ScriptRun(state, stoppedTo)

        while (index in script.indices) {
            if (steps++ > MAX_STEPS) {
                Logger.w(TAG) { "Script from $fromOffset did not terminate after $MAX_STEPS steps" }
                return stop()
            }

            Logger.d(TAG) { "  ${script[index].offset} ${script[index].token}" }

            when (val token = script[index].token) {
                End, Return -> return stop()

                is Goto -> {
                    index = script.indexOfFirst { it.offset == token.offset }
                    if (index < 0) return stop()
                    continue
                }

                is Eval -> {
                    // a true condition falls through, a false one jumps
                    val condition = evaluate(token.tokens, state, dialogAnswer)
                    Logger.d(TAG) {
                        if (condition.isTrue) {
                            "    condition true, carrying on"
                        } else {
                            "    condition false, jumping to ${token.goto}"
                        }
                    }
                    if (!condition.isTrue) {
                        index = script.indexOfFirst { it.offset == token.goto }
                        if (index < 0) return stop()
                        continue
                    }
                }

                is CreateMonster -> state = state.monsterCreated(token)

                is SetFlag.LevelFlag -> state = state.levelFlagSet(level, token.bit)

                is SetFlag.GlobalFlag -> state = state.globalFlagSet(token.bit)

                is NewLevelOrMonster.ChangeLevel -> return stop(
                    ScriptStop.ChangeLevel(
                        level = token.level,
                        subLevel = token.subLevel,
                        location = token.location,
                        direction = token.direction,
                    )
                )

                // A move does not end the script — the stairs scripts step the
                // party onto the staircase and only then change level, so the
                // engine goes out of its way to keep running after one.
                is Teleport.MoveParty -> state = state.partyMovedTo(token.destination)

                is SetWall.ChangePartyDirection -> state = state.partyTurnedTo(token.direction)

                // Everything after this depends on the player's answer, so
                // guessing one would run a branch nobody chose — which is how
                // walking past the Darkmoon priest used to throw the party
                // down a level. Stop and ask.
                is Dialog.RunDialog -> return stop(
                    ScriptStop.AskThePlayer(
                        textId = token.textId,
                        buttons = listOf(token.button1, token.button2, token.button3),
                        scene = scene.toList(),
                        resumeAt = script.getOrNull(index + 1)?.offset ?: return stop(),
                        said = said.toList(),
                    )
                )

                // A speech waits to be read before the script goes on, and what
                // comes next can be the point: the clerics slam the door only
                // once their roar has been acknowledged.
                is Dialog.DialogText -> return stop(
                    ScriptStop.AskThePlayer(
                        textId = token.textId,
                        buttons = listOf(token.pageBreakLabel),
                        scene = scene.toList(),
                        resumeAt = script.getOrNull(index + 1)?.offset ?: return stop(),
                        said = said.toList(),
                        waitsToBeRead = true,
                    )
                )

                // the box is drawn empty, taking whatever was written in it
                Dialog.DrawDialogBox -> {
                    said.clear()
                    scene += Dialog.DrawDialogBox
                }

                Dialog.CloseDialog -> {
                    scene.clear()
                    said.clear()
                }

                is Message -> said += token.messageId

                // anything else the script draws while setting up its question
                is Dialog -> scene += token

                else -> Unit
            }
            index++
        }
        return stop()
    }

    /** Postfix stack machine over a condition's tokens. */
    private fun evaluate(
        tokens: List<Conditional>,
        state: GameState,
        dialogAnswer: DialogAnswer?,
    ): ConditionValue {
        val stack = ArrayDeque<ConditionValue>()
        fun pop() = stack.removeLastOrNull() ?: ConditionValue.FALSE
        fun push(value: ConditionValue) = stack.addLast(value)
        fun push(condition: Boolean) = stack.addLast(ConditionValue.of(condition))

        // The operand written last is the left-hand side: `X Y LessThan` asks
        // whether Y < X, not whether X < Y. Reading it the other way round made
        // "a monster stands here" come out as "fewer than none stand here".
        fun compare(holds: (left: ConditionValue, right: ConditionValue) -> Boolean) {
            val left = pop()
            val right = pop()
            push(holds(left, right))
        }

        tokens.forEach { token ->
            when (token) {
                is Conditional.ImmediateShort -> push(ConditionValue.of(token.value))
                is Conditional.GetLevelFlag -> push(state.isLevelFlagSet(level, token.bit))
                is Conditional.GetGlobalFlag -> push(state.isGlobalFlagSet(token.bit))
                is Conditional.GetPartyDirection ->
                    push(ConditionValue.of(state.party.facing.ordinal))

                is Conditional.DialogResult -> push(ConditionValue.of(dialogAnswer?.number ?: 0))

                is Conditional.IsMonsterAtLocation.BlockFlags ->
                    push(ConditionValue.of(state.monstersOn(token.location)))

                is Conditional.Equals -> compare { left, right -> left == right }
                is Conditional.NotEquals -> compare { left, right -> left != right }
                is Conditional.MoreThan -> compare { left, right -> left > right }
                is Conditional.MoreEqualsThan -> compare { left, right -> left >= right }
                is Conditional.LessThan -> compare { left, right -> left < right }
                is Conditional.LessEqualsThan -> compare { left, right -> left <= right }
                is Conditional.And -> push(pop().isTrue && pop().isTrue)
                is Conditional.Or -> push(pop().isTrue || pop().isTrue)
                else -> {
                    Logger.d(TAG) { "    condition $token not modelled yet, assuming true" }
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
