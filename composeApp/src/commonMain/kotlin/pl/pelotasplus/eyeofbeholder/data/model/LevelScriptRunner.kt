package pl.pelotasplus.eyeofbeholder.data.model

import co.touchlab.kermit.Logger
import kotlin.random.Random
import pl.pelotasplus.eyeofbeholder.data.model.script.ClearFlag
import pl.pelotasplus.eyeofbeholder.data.model.script.Conditional
import pl.pelotasplus.eyeofbeholder.data.model.script.ConsumeItem
import pl.pelotasplus.eyeofbeholder.data.model.script.CreateMonster
import pl.pelotasplus.eyeofbeholder.data.model.script.Damage as DamageDealt
import pl.pelotasplus.eyeofbeholder.data.model.script.Dialog
import pl.pelotasplus.eyeofbeholder.data.model.script.Encounter
import pl.pelotasplus.eyeofbeholder.data.model.script.End
import pl.pelotasplus.eyeofbeholder.data.model.script.Eval
import pl.pelotasplus.eyeofbeholder.data.model.script.GoSub
import pl.pelotasplus.eyeofbeholder.data.model.script.Goto
import pl.pelotasplus.eyeofbeholder.data.model.script.Launcher
import pl.pelotasplus.eyeofbeholder.data.model.script.CloseDoor
import pl.pelotasplus.eyeofbeholder.data.model.script.Message
import pl.pelotasplus.eyeofbeholder.data.model.script.OpenDoor
import pl.pelotasplus.eyeofbeholder.data.model.script.Sound
import pl.pelotasplus.eyeofbeholder.data.model.script.ItemDestination
import pl.pelotasplus.eyeofbeholder.data.model.script.NewItem
import pl.pelotasplus.eyeofbeholder.data.model.script.NewLevelOrMonster
import pl.pelotasplus.eyeofbeholder.data.model.script.Return
import pl.pelotasplus.eyeofbeholder.data.model.script.Script
import pl.pelotasplus.eyeofbeholder.data.model.script.ScriptOffset
import pl.pelotasplus.eyeofbeholder.data.model.script.ScriptToken
import pl.pelotasplus.eyeofbeholder.data.model.script.SetFlag
import pl.pelotasplus.eyeofbeholder.data.model.script.SetWall
import pl.pelotasplus.eyeofbeholder.data.model.script.SpecialEvent
import pl.pelotasplus.eyeofbeholder.data.model.script.Teleport
import pl.pelotasplus.eyeofbeholder.data.model.script.ToggleWall
import pl.pelotasplus.eyeofbeholder.data.model.script.Turn
import pl.pelotasplus.eyeofbeholder.data.model.script.UpdateScreen
import pl.pelotasplus.eyeofbeholder.data.model.script.Wait
import pl.pelotasplus.eyeofbeholder.data.model.script.inWords
import pl.pelotasplus.eyeofbeholder.data.model.script.xy
import kotlin.jvm.JvmInline

/**
 * What came of running a trigger script: the world it left behind, and the
 * one thing it can end by asking for.
 *
 * A script moves and turns the party as it goes, so [state] is the world as of
 * wherever it got to.
 */
data class ScriptRun(
    val state: GameState,
    /** Set when the script ended by sending the party to another level. */
    val changeLevel: ChangeLevel? = null,
    /**
     * What the script took off each champion, which is reported rather than
     * only done.
     *
     * The hit points are gone in [state]; the number showing on a portrait is
     * not, because that is on a clock the script does not own — see
     * [GameState.asAScriptLeaves]. So whoever applies the run has to put the
     * blow up itself, and this is what it needs to do it.
     */
    val hurt: Map<PartySlot, Damage> = emptyMap(),
)

/**
 * Whether a finished run is worth noticing: worth the frame that follows an
 * event, and worth the lines it wrote getting there. Those are one answer, not
 * two — what is not worth seeing is not worth reading about either.
 *
 * Drawing that frame belongs to whoever ran the script, because a caller that
 * sets a trigger off hands the drawing over. So an event somebody caused is
 * worth a frame whether or not its script did anything: a step onto a square
 * whose script declines is still a step, and the frame skipped there leaves
 * the party looking at the view from the square behind them.
 *
 * What a floor does by itself is the exception — see [ScriptTimer]. Nobody
 * asked for it, and it puts the same question to the same square for as long
 * as the party stay on the floor, hearing "not now" almost every time. A run
 * like that which left no mark is worth neither the frame nor the lines.
 *
 * @param before the world as the run found it
 * @param byItself whether the floor set the run off rather than anybody did
 * @param aBoxWasUp whether a dialogue stood when the run began, for this run
 *   to take down. That is a mark too, and one a [ScriptRun] cannot see: the
 *   box is on the screen rather than in the world
 */
fun ScriptRun.worthNoticing(
    before: GameState,
    byItself: Boolean,
    aBoxWasUp: Boolean = false,
): Boolean = !byItself || aBoxWasUp || leftAMarkOn(before)

private fun ScriptRun.leftAMarkOn(before: GameState) =
    state != before || hurt.isNotEmpty() || changeLevel != null

/** The party is to leave for another level, which ends the script. */
data class ChangeLevel(
    val level: Int,
    val subLevel: Int,
    val location: Location,
    val direction: Direction?,
)

/**
 * What a script has written into the dialogue box: what it drew getting there,
 * and the lines themselves. It is the box a question is asked in, without the
 * question — the speaker stays up and there is nothing to click.
 *
 * Both empty means the box is taken down.
 */
data class ScriptSpeech(
    val scene: List<Dialog> = emptyList(),
    val said: List<MessageId> = emptyList(),
    /**
     * What colour the last line was asked for in. A script packs two into the
     * instruction — the ink and the shade behind it — of which this is the ink.
     */
    val colour: PaletteIndex = DEFAULT_INK,
    /**
     * True when the box has this moment been drawn, which empties it. What was
     * written into it is gone; the speaker in the frame above it stays.
     */
    val boxJustDrawn: Boolean = false,
) {
    val isEmpty: Boolean get() = scene.isEmpty() && said.isEmpty()

    companion object {
        val DEFAULT_INK = PaletteIndex(15)

        /** The ink of a `Message`, which is the low byte of its colour word. */
        fun inkOf(colour: Int) = PaletteIndex(colour and 0xFF)
    }
}

/**
 * A question a script has put on screen and is waiting to have clicked.
 *
 * [buttons] is what may be clicked: three answers to a question, or the single
 * word — usually "ok" — that acknowledges a speech. A script sets its scene one
 * instruction at a time (clear the view, draw who is speaking, draw the frame)
 * and only then speaks, so [scene] holds those instructions in the order they
 * were run, for the screen to put up first.
 */
data class ScriptQuestion(
    /**
     * Which of the dungeon's speeches is being put, or null where the words
     * are [spoken] instead and belong to no level.
     */
    val textId: DialogueTextId?,
    val buttons: List<MessageId>,
    val scene: List<Dialog>,
    /**
     * What the buttons say, where the words are not the level's to give. A
     * level writes the answers to its own questions into its messages; the
     * answers to a meeting are the same two words everywhere in the dungeon,
     * and are written into the source like the two a speech is read with.
     */
    val words: List<String> = emptyList(),
    /**
     * Who is being spoken to, where the speaker is somebody met rather than a
     * picture a script named: they stand in the view while they say their
     * piece, and the level has nothing to say about them.
     */
    val met: NpcId? = null,
    /**
     * What the script printed into the box before speaking — the party's own
     * line, usually. Drawing the box again wipes them, so a reply arrives on a
     * clean box while an exchange builds up on one.
     */
    val said: List<MessageId> = emptyList(),
    /**
     * Whether the box is drawn again once this has been answered, taking what
     * stood in it with it.
     *
     * A question does that, so its answer arrives on a clean box. A speech
     * does not: it is written where the last one left off, and a script that
     * speaks, animates something and speaks again leaves both lines standing.
     * The blank speech after the mouth's riddle is the whole point of this —
     * it says nothing and only raises the button, and wiping the box for it
     * would take the riddle away unread.
     *
     * Only drawing the box empties it.
     */
    val boxDrawnAgainAfter: Boolean = true,
    /**
     * What is asked, where the asking is the game's rather than a level's —
     * the same reason [words] exists. A party of six being told to choose
     * somebody to drop is the game speaking, so no level holds the sentence.
     */
    val spoken: String? = null,
    /**
     * True when the script is being read rather than answered, so the one
     * button belongs in the corner speeches are read on rather than in the row
     * of answers under the text.
     */
    val waitsToBeRead: Boolean = false,
) {
    /**
     * Whether there is anything to click on, [labels] being what the buttons
     * say once the level's messages have been looked up.
     *
     * A speech whose button has no word on it is not clicked and not waited
     * for: the words go up and the script carries straight on, so a speaker
     * can say its piece and then move while what it said stays on screen. A
     * question is a different thing — its answers are what it is asking, and
     * one is always waited for.
     */
    fun hasSomethingToClick(labels: List<String>): Boolean =
        if (waitsToBeRead) labels.any { it.isNotBlank() } else labels.isNotEmpty()
}

/**
 * Where a script shows its work: the screen, from the interpreter's side.
 *
 * A script draws, waits and asks where it stands, and holds the world still
 * for as long as it runs — nothing else moves meanwhile. That is why these
 * suspend: a hold returns when the pause is over, a question when it has been
 * answered, and the script carries on from the same line either way.
 *
 * Since the interpreter never hands control back part way through, the calls a
 * script is inside, the scene it has built and what it has written into the box
 * are all plain locals of the run.
 */
interface ScriptStage {

    /** Draw the world as the script has left it, then carry on. */
    suspend fun show(world: GameState)

    /**
     * The script has moved the party, or turned them, and from here on they
     * are its to move.
     *
     * A script that only opens a door or writes a line leaves the party where
     * they are and free to walk off while it runs; one that walks them
     * somewhere cannot have them steered out from under it.
     */
    fun takesTheParty() = Unit

    /**
     * Put up what the script has written into the dialogue box, with nothing
     * to click and nothing to wait for.
     *
     * A script writes a line and then holds the screen so it can be read, so
     * a line that is not put up straight away is a pause with nothing in it.
     * An empty [speech] is the box taken down again.
     */
    suspend fun say(speech: ScriptSpeech)

    /** Hold what is on screen. Nothing else moves while a script waits. */
    suspend fun hold(ticks: Ticks)

    /**
     * Start a sound and carry on without waiting for it. A script that means
     * a sound to be over before the next thing happens says so itself, with a
     * wait of its own.
     *
     * [volume] is how loud it is from where the party stand. Something made
     * too far off arrives [Volume.SILENT] and is not worth starting.
     */
    suspend fun play(track: TrackIndex, volume: Volume = Volume.FULL)

    /** Put a question up, and wait for it to be answered. */
    suspend fun ask(question: ScriptQuestion): DialogAnswer

    /**
     * A set piece is about to take the screen, so whatever the player had
     * standing over the play field comes down first: a champion's page, the
     * camp menu.
     *
     * Only a set piece does this. A script that writes a line or opens a door
     * leaves the screen as it found it, and a page can be read through the
     * whole of one.
     */
    suspend fun takesTheScreen() = Unit

    /**
     * Open the archway over the view and hold until it has finished opening.
     *
     * The script puts the party down somewhere else the moment this returns,
     * so a stage that hurries it lands them before they have seen where they
     * were going.
     */
    suspend fun opensThePortal()

    /**
     * Something the script asked for that nothing here does yet, said in the
     * words a player could repeat back.
     *
     * It does not suspend and it changes nothing: a gap is reported beside the
     * run rather than being a beat of it, so a stage that has nowhere to put
     * the notice can ignore it and the script reads the same either way.
     */
    fun notImplemented(what: String) = Unit

    companion object {
        /**
         * Runs a script straight through: nothing is drawn, no wait takes any
         * time, and every question is answered with [answer].
         */
        fun silent(answer: DialogAnswer = DialogAnswer(1)) = object : ScriptStage {
            override suspend fun show(world: GameState) = Unit
            override suspend fun say(speech: ScriptSpeech) = Unit
            override suspend fun hold(ticks: Ticks) = Unit
            override suspend fun play(track: TrackIndex, volume: Volume) = Unit
            override suspend fun ask(question: ScriptQuestion) = answer
            override suspend fun opensThePortal() = Unit
        }
    }
}

/**
 * How loud a sound made on [where] is from where the party are standing.
 *
 * Distance is all that quietens it — walls in this dungeon do not — and past
 * fifteen squares it is not heard at all, which is why a lever thrown at the
 * far end of the floor stays over there. The party's own square is read at the
 * moment the sound is made, so a script that walks them somewhere first is
 * heard from where it left them.
 *
 * A script that names no square at all means a sound the party carry with
 * them rather than one coming from somewhere, and that is heard in full
 * wherever they are.
 */
private fun GameState.hears(where: Location): Volume =
    if (where == NO_SQUARE) Volume.FULL
    else Volume.asFarOffAs(where.blocksFrom(party.position))

private val NO_SQUARE = Location.ofBlock(0)

/**
 * Where a script's luck comes from, so that a test can decide how it falls.
 *
 * The game rolls for more than damage: which corner a thing it makes lands
 * in, and whether searching a wall turns anything up.
 */
fun interface Dice {
    /** [times] dice of [pips] sides each, plus [modifier]. */
    fun roll(times: Int, pips: Int, modifier: Int): Int

    companion object {
        val random = Dice { times, pips, modifier ->
            if (times <= 0 || pips <= 0) modifier
            else (1..times).sumOf { Random.nextInt(1, pips + 1) } + modifier
        }
    }
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
 * Runs a square's trigger script: what the game does when the party steps
 * somewhere that has something to say about it.
 *
 * A run goes through to the end of the script rather than in pieces, however
 * long that takes — a script may hold the screen between steps or wait on an
 * answer, and it does both through the [ScriptStage] it is given.
 *
 * This is not the full interpreter, and the instructions it does not run say
 * so as it goes past them, naming the level and the offset they sit at. That
 * log is the list of what is left to write, so nothing is skipped in silence.
 *
 * The `when` over the instructions has no `else`: a token added to the
 * language has to be given a branch here, even if all that branch does is
 * report itself.
 *
 * Conditions needing state this project does not model yet evaluate to true,
 * which keeps stairs reachable, and are reported the same way.
 */
class LevelScriptRunner(
    private val script: List<Script>,
    /** Which level's flags this script reads and writes. */
    private val level: Int = 0,
    /** Which sublevel the party are in, which a monster it conjures joins. */
    private val subLevel: Int = 0,
    /** That sublevel's species, which say what a conjured monster can take. */
    private val kinds: List<MonsterProperty> = emptyList(),
    /** What each kind of item is, for the questions a script asks about one. */
    private val itemTypes: ItemTypes? = null,
    private val dice: Dice = Dice.random,
) {

    /**
     * Runs the script of a square, if it reacts to [event].
     *
     * [at] is where the party stand, save for a wall being clicked: that is
     * the square in front of them, which they are not on.
     */
    suspend fun onEvent(
        triggers: List<Trigger>,
        event: ScriptEvent,
        state: GameState,
        stage: ScriptStage = ScriptStage.silent(),
        at: Location = state.party.position,
        /** What was used, for the questions a script asks about it. */
        used: ItemIndex? = null,
        /**
         * Which spell was just cast, where one was. A square that answers a
         * spell nearly always cares which, so this is the difference between
         * dispelling a carving and reading anything else aloud at it.
         */
        cast: Spell? = null,
        /**
         * Whether the floor set this off rather than anybody did, which is
         * what lets a run that left no mark go unsaid — see [worthNoticing].
         */
        byItself: Boolean = false,
    ): ScriptRun {
        writingDown = if (byItself) mutableListOf() else null

        val run = onEvent(triggers, event, state, stage, at, depth = 0, used = used, cast = cast)

        writingDown?.also { written ->
            writingDown = null
            if (run.worthNoticing(before = state, byItself = true)) {
                written.forEach { line -> Logger.d(TAG) { line } }
            }
        }
        return run
    }

    /**
     * Where a trace goes while it is being weighed — see [byItself]. Null is
     * the ordinary case of saying each line as it happens.
     */
    private var writingDown: MutableList<String>? = null

    private inline fun say(crossinline line: () -> String) {
        val written = writingDown
        if (written == null) Logger.d(TAG) { line() } else written += line()
    }

    private suspend fun onEvent(
        triggers: List<Trigger>,
        event: ScriptEvent,
        state: GameState,
        stage: ScriptStage,
        position: Location,
        depth: Int,
        used: ItemIndex? = null,
        cast: Spell? = null,
    ): ScriptRun {
        val here = triggers.filter { it.location == position }
        val trigger = here.firstOrNull { it.flags.reactsTo(event) }

        if (trigger == null) {
            if (here.isNotEmpty()) {
                say {
                    "The trigger on ${position.xy} ignores $event, " +
                        "flags ${here.map { it.flags.raw.toHexString() }}"
                }
            }
            return ScriptRun(state)
        }

        say {
            "$event on ${position.xy} runs the script at ${trigger.script.offset.value}"
        }
        return runScript(
            trigger.script.offset, state, stage, triggers, depth, event, used, cast, position,
        ).also { result ->
            say {
                val party = result.state.party
                "The script at ${trigger.script.offset.value} left the party " +
                    "on ${party.position.xy} facing ${party.facing}"
            }
        }
    }

    private suspend fun runScript(
        from: ScriptOffset,
        initial: GameState,
        stage: ScriptStage,
        triggers: List<Trigger>,
        depth: Int,
        event: ScriptEvent,
        used: ItemIndex?,
        cast: Spell?,
        /** The square this script is being run for, which a few tokens ask about. */
        ranFor: Location,
    ): ScriptRun {
        var state = initial

        var index = script.indexOfFirst { it.offset == from }
        if (index < 0) {
            Logger.w(TAG) { "No script at offset $from" }
            return ScriptRun(state)
        }

        var steps = 0

        // what the script has drawn so far for the question it is building up
        val scene = mutableListOf<Dialog>()

        // and what it has written into the box, which the box outlives
        val said = mutableListOf<MessageId>()

        val returnTo = ArrayDeque<ScriptOffset>()

        // the last answer given, which the script may test more than once
        var dialogAnswer: DialogAnswer? = null

        // what the script has taken off each champion, added up over as many
        // blows as it deals: a corridor of traps is one script and several
        val hurt = mutableMapOf<PartySlot, Damage>()

        fun stop(changeLevel: ChangeLevel? = null) = ScriptRun(state, changeLevel, hurt.toMap())

        // An instruction nobody has written yet says so, and says what the
        // script goes on to do instead. Silence is the wrong answer here: a
        // script that quietly does nothing reads exactly like one that had
        // nothing to do, and every level is full of both.
        //
        // This goes to the log rather than to the player: the whole
        // instruction, where it sits, and where the party were standing when
        // it ran — which is what somebody going to write it needs, and is more
        // than a line on the bar could carry.
        fun notYet(token: ScriptToken, what: String, instead: String) {
            Logger.w(TAG) {
                gap(
                    where = "level $level ${state.party.position.xy}",
                    at = script[index].offset,
                    token = token.inWords(),
                    instead = instead,
                )
            }
            stage.notImplemented("$what is not written yet, $instead")
        }

        while (index in script.indices) {
            if (steps++ > MAX_STEPS) {
                Logger.w(TAG) { "Script from $from did not terminate after $MAX_STEPS steps" }
                return stop()
            }

            val step = script[index]

            // A condition is logged once it has an answer, so that the line
            // asking it is the line saying which way the script went.
            if (step.token !is Eval) say { "  ${step.offset.value}  ${step.token.inWords()}" }

            when (val token = step.token) {
                End -> return stop()

                // Nothing to return to ends the script, the way the engine
                // does when its stack is empty.
                Return -> {
                    val caller = returnTo.removeLastOrNull() ?: return stop()
                    index = script.indexOfFirst { it.offset == caller }
                    if (index < 0) return stop()
                    continue
                }

                is Goto -> {
                    index = script.indexOfFirst { it.offset == token.offset }
                    if (index < 0) return stop()
                    continue
                }

                is GoSub -> {
                    // a call the engine's ten-deep stack cannot hold is dropped
                    // and the script carries on past it
                    if (returnTo.size < MAX_SUBROUTINE_DEPTH) {
                        val target = script.indexOfFirst { it.offset == token.offset }
                        val caller = script.getOrNull(index + 1) ?: return stop()
                        if (target < 0) return stop()
                        returnTo.addLast(caller.offset)
                        index = target
                        continue
                    }
                }

                is Eval -> {
                    // a true condition falls through, a false one jumps
                    val condition =
                        evaluate(token.tokens, state, dialogAnswer, event, used, stage, cast)
                    say {
                        val went =
                            if (condition.isTrue) "yes, carrying on"
                            else "no, jumping to ${token.goto.value}"
                        "  ${step.offset.value}  ${token.inWords()}  ->  $went"
                    }
                    if (!condition.isTrue) {
                        index = script.indexOfFirst { it.offset == token.goto }
                        if (index < 0) return stop()
                        continue
                    }
                }

                // Asking for one is not getting one: the square may be full,
                // the party may be standing on it, or the floor may be holding
                // all thirty it can. So the trace says what came of the asking
                // as well as what was asked — a floor that quietly makes
                // nothing looks exactly like a floor that was never told to.
                is CreateMonster -> {
                    val before = state.monsters.map { it.index }.toSet()
                    state = state.monsterCreated(token, subLevel, kinds, dice, level)

                    say {
                        val made = state.monsters.firstOrNull { it.index !in before }
                        if (made == null) {
                            "        nothing was made: no room on ${token.location.xy}"
                        } else {
                            "        made m${made.index.value} on ${made.location.xy}, " +
                                "${state.monsters.size} standing on the floor"
                        }
                    }
                }

                // A script makes a thing by pointing at another like it. Where
                // it lands can be a square outright, or the hand, or the floor
                // in front of the party — and the last two pick a corner by
                // rolling for it, so two things made at once do not land in
                // the same one.
                is NewItem -> state = when (val goes = token.goes) {
                    is ItemDestination.OnASquare -> state.itemCopied(
                        copyOf = token.copyOf,
                        level = level,
                        at = goes.at,
                        place = goes.place,
                        overrides = token.overrides,
                    )

                    ItemDestination.IntoTheHand -> state.itemCopiedIntoTheHand(
                        copyOf = token.copyOf,
                        level = level,
                        place = cornerOfTwo(),
                        overrides = token.overrides,
                    )

                    ItemDestination.Underfoot -> state.itemCopied(
                        copyOf = token.copyOf,
                        level = level,
                        at = state.party.position,
                        place = cornerInFront(state.party.facing),
                        overrides = token.overrides,
                    )
                }

                is SetFlag.LevelFlag -> state = state.levelFlagSet(level, token.bit)

                is SetFlag.GlobalFlag -> state = state.globalFlagSet(token.bit)

                // The first bit is the one that means a monster has been
                // roused, and setting it is how a script starts a fight —
                // level 5's clerics are turned hostile by their own scene when
                // the party choose to attack, rather than by the first blow.
                // The other bits are still nobody's business, and say so.
                is SetFlag.MonsterFlag -> state =
                    if (token.bit == SetFlag.MonsterFlag.ROUSED) {
                        state.rousedBy(token.monsterId)
                    } else {
                        notYet(token, "this monster flag", "the monster is left as it was")
                        state
                    }

                // Clearing a flag is how a script takes something back. The
                // grave on level 4 sets the flag that says to dig, then clears
                // it again if the party's cleric talks them out of it, so a
                // script that cannot clear one cannot be argued with.
                is ClearFlag.LevelFlag ->
                    state = state.levelFlagCleared(level, FlagBit(token.flag))

                is ClearFlag.GlobalFlag -> state = state.globalFlagCleared(FlagBit(token.flag))

                // The two are the wrong way round, and it is the original's
                // doing: its "set" leaves the party free to sleep, and its
                // "remove" is what forbids it. So a dangerous stretch of floor
                // says the party cannot rest by removing the flag, and the safe
                // ground after says they can by setting it.
                is SetFlag.RestingAllowed -> state = state.copy(preventRest = false)
                is ClearFlag.RestingForbidden -> state = state.copy(preventRest = true)

                is NewLevelOrMonster.ChangeLevel -> return stop(
                    ChangeLevel(
                        level = token.level,
                        subLevel = token.subLevel,
                        location = token.location,
                        direction = token.direction,
                    )
                )

                // A move does not end the script. Scripts walk the party a
                // square at a time and carry on: onto a staircase and only then
                // to another level, or up a corridor with a pause between steps.
                //
                // Being put on a square is arriving on it, so whatever is there
                // has its say before this script goes on — a scripted walk that
                // ends on a door is how the door gets asked about.
                is Teleport.MoveParty -> {
                    stage.takesTheParty()
                    state = state.partyMovedTo(token.destination)

                    if (depth < MAX_NESTED_TRIGGERS) {
                        val arrival = onEvent(
                            triggers,
                            ScriptEvent.PARTY_ENTERED,
                            state,
                            stage,
                            state.party.position,
                            depth + 1,
                        )
                        state = arrival.state
                        arrival.changeLevel?.let { return stop(it) }
                    } else {
                        Logger.w(TAG) { "Not following a move onto ${token.destination}, $depth deep" }
                    }
                }

                // A plate set into the floor is weighed and then works a door
                // somewhere else, which is most of what the levels do with
                // doors that have no button on them.
                is ConsumeItem.DeleteHandItem -> state = state.handEmptied()

                is ConsumeItem.DeleteBlockItem -> state = state.itemsSweptFrom(
                    level = level,
                    at = token.location,
                    ofType = token.itemType.takeIf { it >= 0 }?.let(::ItemTypeId),
                )

                is OpenDoor -> state = doorSent(state, token.location, opening = true)

                is CloseDoor -> state = doorSent(state, token.location, opening = false)

                is SetWall.ChangePartyDirection -> {
                    stage.takesTheParty()
                    state = state.partyTurnedTo(token.direction)
                }

                // A switch on a wall, working something on another square.
                is ToggleWall.DoorSwitch -> state = doorSwitched(state, token.location)

                is ToggleWall.OneSide -> {
                    val was = state.wall(level, token.location, WallSide.entries[token.dir])
                    state = state.wallChanged(
                        level = level,
                        at = token.location,
                        side = WallSide.entries[token.dir],
                        to = flipped(was.asByte(), token.a, token.b),
                    )
                }

                is ToggleWall.AllSides -> {
                    // The north face is the one compared, whichever face the
                    // switch is on, and all four are set to the answer.
                    val was = state.wall(level, token.location, WallSide.NORTH)
                    state = state.wallsChanged(
                        level = level,
                        at = token.location,
                        to = flipped(was.asByte(), token.a, token.b),
                    )
                }

                is ToggleWall.Unknown ->
                    notYet(token, "this wall switch", "the wall is left as it was")

                is SetWall.OneSide ->
                    state = state.wallChanged(level, token.location, token.side, token.to)

                is SetWall.AllSides ->
                    state = state.wallsChanged(level, token.location, token.to)

                // The answer is kept rather than used and dropped: a script may
                // test it more than once, and well past the branch it chose.
                is Dialog.RunDialog -> dialogAnswer = stage.ask(
                    ScriptQuestion(
                        textId = token.textId,
                        buttons = listOf(token.button1, token.button2, token.button3),
                        scene = scene.toList(),
                        said = said.toList(),
                    )
                )

                // A speech is read rather than answered, and the script waits
                // for that: what follows a speech can be the point of it. The
                // click that dismisses one is not an answer, so it must not
                // replace the one the script is still testing.
                is Dialog.DialogText -> stage.ask(
                    ScriptQuestion(
                        textId = token.textId,
                        buttons = listOf(token.pageBreakLabel),
                        scene = scene.toList(),
                        said = said.toList(),
                        waitsToBeRead = true,
                        boxDrawnAgainAfter = false,
                    )
                )

                is Wait -> stage.hold(Ticks(token.delay))

                is Sound -> stage.play(TrackIndex(token.soundId), state.hears(token.location))

                UpdateScreen -> stage.show(state)

                // the box is drawn empty, taking whatever was written in it
                Dialog.DrawDialogBox -> {
                    said.clear()
                    scene += Dialog.DrawDialogBox
                    stage.say(ScriptSpeech(scene.toList(), said.toList(), boxJustDrawn = true))
                }

                Dialog.CloseDialog -> {
                    scene.clear()
                    said.clear()
                    stage.say(ScriptSpeech())
                }

                is Message -> {
                    said += token.messageId
                    stage.say(
                        ScriptSpeech(
                            scene = scene.toList(),
                            said = said.toList(),
                            colour = ScriptSpeech.inkOf(token.color),
                        )
                    )
                }

                // A picture goes up as it is asked for rather than whenever the
                // next thing is said. A script that draws several of them with
                // a pause between is animating one thing, not choosing between
                // several: a mouth that moves only once its speech is over is
                // not moving at all.
                is Dialog.DisplayPicture -> {
                    scene += token
                    stage.say(ScriptSpeech(scene.toList(), said.toList()))
                }

                // anything else the script draws while setting up its question
                is Dialog -> scene += token

                // These hand the script back an answer, and skipping one is
                // not the same as it answering nothing: the script goes on to
                // test an answer that was never given, and takes a branch
                // silently. Say so rather than let it read as a script that
                // did its work.
                SpecialEvent.InitNpc -> state = broughtAlong(state, stage)

                SpecialEvent.CharSelectDialogue -> dialogAnswer = whoWasChosen(state, stage)

                SpecialEvent.CharacterLevelGain -> state = aLevelGiven(state, dialogAnswer)

                // The two coins the fifteenth floor's giant asks for. Which
                // two is not in the script: the instruction says only "take
                // what you came for", and the pair is written into the engine.
                SpecialEvent.DeletePartyItems -> {
                    val before = state.championsCarrying(WHAT_A_GIANT_TAKES, ANYTHING)
                    state = COINS_A_GIANT_TAKES.fold(state) { world, worth ->
                        world.partyRelievedOf(WHAT_A_GIANT_TAKES, worth, itemTypes)
                    }
                    say {
                        "        took the coins off the party, who had them between " +
                            "$before of them"
                    }
                }

                is SpecialEvent -> notYet(
                    token,
                    "this set piece",
                    "the script will read its result as unanswered",
                )

                // A set piece takes the screen and the party with it: whatever
                // the player had open comes down before it starts, and they
                // cannot be steered out from under it while it plays.
                is Encounter -> {
                    stage.takesTheScreen()
                    stage.takesTheParty()

                    when (token) {
                        is Encounter.NpcSequence -> {
                            val meeting = NpcMeeting.called(token.npc)

                            if (meeting == null) {
                                notYet(token, "this meeting", "it counts as seen anyway")
                            } else {
                                state = met(meeting, state, stage)
                            }
                        }

                        // The archway a stone gem opens, which the script waits
                        // out before it puts the party down on another floor.
                        Encounter.PortalSequence -> stage.opensThePortal()

                        // The last set piece: the way the party are told they
                        // have died. Skipping it is quiet in a way that
                        // matters — the script has usually just set the flag
                        // that says it has happened, so nothing brings it round
                        // again and the scene is gone for that game.
                        else -> notYet(token, "this set piece", "it counts as seen anyway")
                    }
                }

                // A pit, a trap, a column of lightning. The dice are rolled
                // per champion rather than once for the party.
                is DamageDealt -> {
                    val blows = Harm.of(token, state, itemTypes, dice)
                    state = with(Harm) { state.hurtBy(blows) }
                    blows.forEach { (whose, amount) ->
                        hurt[whose] = Damage((hurt[whose]?.points ?: 0) + amount.points)
                    }
                    if (blows.values.any { it.landed }) stage.play(HURT)
                }

                // A dart from a wall, or the bolt a trap throws down a
                // corridor. The corner it starts in is the game's own table,
                // read by the way it is going and which of the two sides of
                // that way the trap sits on.
                is Launcher -> {
                    state = loosed(token, state)
                    stage.play(LOOSED)
                }

                // A quarter turn at a time, and by rather than to: a spinner
                // sends the party round from wherever they came in facing.
                is Turn.TurnParty -> {
                    stage.takesTheParty()
                    state = state.partyTurnedTo(state.party.facing.turnedBy(token.dir))
                }

                // The other kind turns what is in the air over this very
                // square, and by rather than to. It is how a room sends a
                // fireball round and round: four corners of it each give
                // whatever crosses them a quarter turn, and what was loosed
                // down one wall comes back along the next.
                is Turn.TurnFlyingObjects -> state = state.copy(
                    inFlight = state.inFlight.map {
                        if (it.at == ranFor) it.copy(going = it.going.turnedBy(token.dir))
                        else it
                    },
                )

                is Turn.Unknown -> notYet(token, "turning", "nothing turns")

                // The things on a square carried to another, on this level or
                // onto another. A null level in the token is this one.
                is Teleport.MoveItems -> state = state.itemsMoved(
                    ofType = token.ofType,
                    fromLevel = token.fromLevel ?: level,
                    from = token.from,
                    toLevel = token.toLevel ?: level,
                    to = token.to,
                    onLevel = level,
                )

                is Teleport.MoveMonster ->
                    state = state.monstersMovedFrom(token.source, token.destination)

                is Teleport.Unknown -> notYet(token, "moving a thing", "nothing is moved")

                // Graphics the level wants for a fight it is about to start.
                is NewLevelOrMonster.LoadMonsterShapes -> notYet(
                    token,
                    "loading monster shapes",
                    "the shapes already loaded are used",
                )

                // The flags that are not the level's or the game's: the answer
                // slot, and the one that stops the party camping.
                is SetFlag -> notYet(token, "this flag", "nothing is marked")

                is ClearFlag -> notYet(token, "this flag", "nothing is cleared")

                is SetWall -> notYet(token, "this wall", "the wall is left as it was")
            }
            index++
        }
        return stop()
    }

    /**
     * Somebody stepping up to the party, saying their piece and asking to come
     * along.
     *
     * The question is put the way a script's own questions are, so it is read,
     * answered and drawn by whatever is showing the script. Saying yes is
     * answered before anybody joins: adding them to the party is not written
     * yet, and the flag that would say they had joined is not set for a
     * meeting that ended with nobody joining.
     */
    private suspend fun met(
        meeting: NpcMeeting,
        state: GameState,
        stage: ScriptStage,
    ): GameState {
        // The view is drawn again before they step into it: the instruction
        // before this one usually turns the party to face whoever it is, and
        // they are drawn standing in the view that turn leaves rather than in
        // whatever the party were looking at a moment ago.
        stage.show(state)
        stage.play(meeting.heardAs)

        val freeing = meeting.freeing ?: return joining(meeting, state, stage)

        return freed(freeing, meeting, state, stage)
    }

    /**
     * Being let out of a cell, which is a question of its own and only
     * sometimes leads to the other one.
     *
     * Left where he is, he says nothing — but the party are turned to face
     * north, which is the only word the script gets back: it reads their
     * facing straight afterwards and walks them up the corridor unless it
     * finds them turned. Freed, a toss decides whether he comes along at all,
     * and either way the cell is remembered as dealt with.
     */
    private suspend fun freed(
        freeing: NpcMeeting.Freeing,
        meeting: NpcMeeting,
        state: GameState,
        stage: ScriptStage,
    ): GameState {
        val release = stage.ask(
            ScriptQuestion(
                textId = freeing.asks,
                buttons = emptyList(),
                words = listOf(freeing.doIt, freeing.leaveIt),
                scene = emptyList(),
                met = meeting.npc,
            ),
        ) == DialogAnswer.forButton(0)

        if (!release) {
            val walkedAway = meeting.remembering(state, NpcMeeting.Remembers.When.THEY_WERE_LEFT)
            return freeing.leftFacing?.let(walkedAway::partyTurnedTo) ?: walkedAway
        }

        val opened = meeting.remembering(state, NpcMeeting.Remembers.When.THEY_WERE_DEALT_WITH)

        if (freeing.goesInstead != null && dice.roll(1, 2, -1) == 1) {
            stage.ask(
                ScriptQuestion(
                    textId = freeing.goesInstead,
                    buttons = emptyList(),
                    words = listOf(DialogueScene.OK),
                    scene = emptyList(),
                    waitsToBeRead = true,
                    met = meeting.npc,
                ),
            )
            return opened
        }

        return joining(meeting, opened, stage)
    }

    /** Being asked along, and what follows from the answer. */
    private suspend fun joining(
        meeting: NpcMeeting,
        state: GameState,
        stage: ScriptStage,
    ): GameState {
        val letThemAlong = stage.ask(
            ScriptQuestion(
                textId = meeting.asks,
                buttons = emptyList(),
                words = listOf(NpcMeeting.YES, NpcMeeting.NO),
                scene = emptyList(),
                met = meeting.npc,
            ),
        ) == DialogAnswer.forButton(0)

        val answer = if (letThemAlong) meeting.agrees else meeting.refused

        answer?.let {
            stage.ask(
                ScriptQuestion(
                    textId = it,
                    buttons = emptyList(),
                    words = listOf(DialogueScene.OK),
                    scene = emptyList(),
                    waitsToBeRead = true,
                    met = meeting.npc,
                ),
            )
        }

        if (!letThemAlong) return state

        val room = madeRoomFor(state, stage) ?: return state

        return room
            .joinedBy(meeting.joiningAs, meeting.npc)
            .copy(flags = room.flags.setting(WhatTheGameItselfRemembers.SOMEBODY_WAS_LET_ALONG))
            .let { meeting.remembering(it, NpcMeeting.Remembers.When.THEY_JOINED) }
    }

    /**
     * Somebody put in the party by the script rather than by a meeting.
     *
     * The opcode carries nothing, because it does not need to: it always means
     * the same one of the six, and the script around it has already done the
     * talking. Which is why a party of six are asked here and not earlier —
     * the yes has been said by the time anybody counts them.
     *
     * That order leaves a seam, and it is the game's rather than ours: turning
     * him down at the door is a branch of the script, and it is that branch
     * which sweeps the things he leaves lying about. Backing out here is not,
     * so a party who say yes and then find no room for him keep both his
     * refusal and his belongings.
     *
     * Nor could a script do better with it. This is the one set piece that
     * leaves nothing behind for a script to read — its neighbours all answer
     * with something the next instruction can ask about, and this one answers
     * with nothing at all. So there is no branch to write, and the seam is not
     * a branch anybody forgot.
     *
     * His bones, if the party were carrying them, are let go of by the
     * joining: he cannot both walk along and be in somebody's pack.
     */
    private suspend fun broughtAlong(state: GameState, stage: ScriptStage): GameState {
        val room = madeRoomFor(state, stage) ?: return state

        return room.joinedBy(NpcMeeting.TANGLOR, NpcMeeting.TANGLOR_IS)
    }

    /**
     * The world with a place free in the party, or null where the party would
     * rather keep the six they have.
     *
     * Six is the whole of the party, so somebody has to go before anybody else
     * comes. The choice is put as their six names and a way out of the
     * question; taking the way out is what refuses the join, and it refuses it
     * after the person has been told they may come — which is the game's
     * order, and the reason a full party still hear the yes.
     *
     * Whoever is dropped leaves everything they carried on the floor
     * underfoot, in one of the two corners in front of the party.
     */
    private suspend fun madeRoomFor(state: GameState, stage: ScriptStage): GameState? {
        if (state.roomForOneMore) return state

        val standing = state.champions
            .mapIndexed { slot, who -> PartySlot(slot) to who }
            .filter { (_, who) -> who.inTheParty }

        val answer = stage.ask(
            ScriptQuestion(
                textId = null,
                buttons = emptyList(),
                words = standing.map { (_, who) -> who.name } + NpcMeeting.ABORT,
                scene = emptyList(),
                spoken = NpcMeeting.ONLY_SIX,
            ),
        )

        val chosen = standing.getOrNull(answer.number - 1) ?: return null

        return state.championDropped(
            whose = chosen.first,
            level = level,
            at = state.party.position,
            facing = state.party.facing,
            dice = dice,
        )
    }

    /**
     * The party asked which of them does a thing, and the answer in the form
     * the script reads it: which champion, or [NOBODY_CHOSE] where they
     * refused.
     *
     * Only those who could act are offered — nobody asks the stone or the
     * past-raising to lay a hand on anything — and refusing is always the
     * last of the choices. The question itself has been spoken already; this
     * only puts the names up.
     */
    private suspend fun whoWasChosen(state: GameState, stage: ScriptStage): DialogAnswer {
        val standing = state.champions
            .mapIndexed { slot, who -> slot to who }
            .filter { (_, who) -> who.canBeHurt }

        val answer = stage.ask(
            ScriptQuestion(
                textId = null,
                buttons = emptyList(),
                words = standing.map { (_, who) -> who.name } + NpcMeeting.ABORT,
                scene = emptyList(),
            ),
        )

        val chosen = standing.getOrNull(answer.number - 1) ?: return DialogAnswer(NOBODY_CHOSE)
        return DialogAnswer(chosen.first)
    }

    /**
     * A level handed to whoever was last chosen.
     *
     * Not by writing the number down: by giving them exactly the experience
     * the next level costs, one class at a time, so that what a level brings
     * with it arrives the way it always does. A champion of three classes
     * gets a third of each shortfall, the experience being split among their
     * classes as any other is — which is the arithmetic the game does and
     * leaves a multi-class champion short of the level a single-class one
     * gets outright.
     */
    private fun aLevelGiven(state: GameState, whose: DialogAnswer?): GameState {
        val slot = whose?.number?.takeIf { it in state.champions.indices } ?: return state
        val classes = state.champions[slot].characterClass?.levelledIn.orEmpty()

        val grown = classes.indices.fold(state.champions[slot]) { champion, which ->
            val standing = champion.levels.getOrNull(which) ?: return@fold champion
            val needed = classes[which].progression.neededFor(standing.level + 1)
                ?: return@fold champion

            champion.earning(XpPoints(needed.count - standing.experience.count + 1), dice)
        }

        return state.copy(
            champions = state.champions.mapIndexed { at, who -> if (at == slot) grown else who },
        )
    }

    /**
     * A wall that is one of two things and is being asked to be the other.
     * Anything but the first of the two becomes the first.
     */
    private fun flipped(was: WallByte, one: Int, other: Int): WallByte =
        WallByte(if (was.value == one) other else one)

    /**
     * A switch working the door on another square: whatever the door is
     * doing, it does the opposite — one on its way open turns round, one
     * standing shut opens, one standing open closes.
     *
     * Two squares are refused rather than worked, and refused whole rather
     * than half done: the party's own, and one with anything standing on it,
     * which would be a door coming down on a monster.
     */
    private fun doorSwitched(state: GameState, at: Location): GameState {
        if (!state.doorwayIsClear(at, subLevel)) return state

        val side = state.doorFacing(level, at) ?: run {
            Logger.w(TAG) { "No door at $at for a switch to work" }
            return state
        }
        val door = state.wall(level, at, side) as? Maz.WallType.Door ?: return state

        val onItsWay = state.swinging.firstOrNull { it.level == level && it.at == at }
        val opening = onItsWay?.let { !it.opening } ?: door.isShut

        return state.doorSetGoing(level, at, side, opening)
    }

    /**
     * A trap loosing something down a corridor.
     *
     * A thrown item is a real one and is copied out of the table, so it can be
     * picked up where it lands; a conjured bolt is not, and leaves nothing
     * behind. Either way what the script has done is put a thing in the air —
     * where it goes and what it meets is the flight's business, not the
     * script's, and the script carries straight on without waiting.
     */
    private fun loosed(token: Launcher, state: GameState): GameState {
        val place = SquarePlace.of(
            LOOSED_FROM.getOrElse(token.dir * 2 + token.dirOffset) { 0 },
        )

        val loosed = when (token) {
            // What a trap looses is one of the same numbered things a champion
            // casts, so it is the same spell in every way but one: nobody cast
            // it, and its strength is the one the game gives a trap.
            //
            // It carries no spell of its own. A spell on a projectile is what
            // the arrival table answers to, and that table is for what
            // monsters throw — it would hurt the party a second time, and by
            // the depth of the floor rather than by anything here.
            is Launcher.MagicObject -> Projectile(
                what = null,
                at = token.location,
                place = place,
                going = Direction.entries[token.dir % Direction.entries.size],
                squaresLeft = Projectile.UNTIL_IT_HITS,
                thrownBy = Projectile.Thrower.TheLevel,
                harm = looseningOf(token) ?: Projectile.Harm.ofABurst(strengthOfATrap),
            )

            is Launcher.PhysicalItem -> Projectile(
                what = ItemIndex(token.itemId),
                at = token.location,
                place = place,
                going = Direction.entries[token.dir % Direction.entries.size],
                thrownBy = Projectile.Thrower.TheLevel,
            )
        }

        return state.copy(inFlight = state.inFlight + loosed)
    }

    /**
     * How hard the floor throws, which is all a trap has instead of a caster:
     * five above the seventh floor and nine from there down.
     */
    private val strengthOfATrap get() = Projectile.trapStrength(level)

    /**
     * What [token] does where it comes down, for a number the flight table
     * knows and a spell somebody has written out.
     *
     * Null for the rest, which fall back to a plain burst — the numbers a trap
     * looses are the same numbers a champion casts, and the ones nobody has
     * written yet have nothing better to say than that they went off.
     */
    private fun looseningOf(token: Launcher.MagicObject): Projectile.Harm? =
        ConjuredObject.of(token.itemId)?.casts?.throws?.dealtBy(strengthOfATrap)

    /**
     * A door the script works, set going and then left to it.
     *
     * The script does not wait for it. A door takes about a second to travel
     * and the script that started it usually ends within an instruction or
     * two, so waiting would mean the party stood still through the one second
     * the door is worth watching — and a door shut behind them could never be
     * seen shutting at all.
     */
    private fun doorSent(state: GameState, at: Location, opening: Boolean): GameState {
        // Shutting one is refused with anything standing in the frame — see
        // [GameState.doorwayIsClear]. Opening one never is, which is how a
        // script lets a floor's guards out through a door they are stood in.
        if (!opening && !state.doorwayIsClear(at, subLevel)) {
            say { "        the doorway at ${at.xy} is not empty, so it stays as it is" }
            return state
        }

        val side = state.doorFacing(level, at) ?: run {
            Logger.w(TAG) { "No door at $at to ${if (opening) "open" else "close"}" }
            return state
        }

        return state.doorSetGoing(level, at, side, opening)
    }

    /**
     * One of the two corners nearest the party as they are looking, which is
     * where a thing dropped at their feet lands.
     */
    private fun cornerInFront(facing: Direction): SquarePlace =
        (if (dice.roll(1, 2, -1) == 0) FloorReach.OWN_LEFT else FloorReach.OWN_RIGHT)
            .placeFacing(facing)

    /**
     * The same choice made without regard to which way the party look, which
     * is what the game does when a thing meant for the hand has to go on the
     * floor instead.
     */
    private fun cornerOfTwo(): SquarePlace = SquarePlace.of(dice.roll(1, 2, -1))

    /** Postfix stack machine over a condition's tokens. */
    private fun evaluate(
        tokens: List<Conditional>,
        state: GameState,
        dialogAnswer: DialogAnswer?,
        event: ScriptEvent,
        used: ItemIndex?,
        stage: ScriptStage,
        cast: Spell?,
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

                // Which of the things a square reacts to has just happened. A
                // square has one script for all of them, so a script that does
                // different things for being walked onto and for being clicked
                // asks this first.
                is Conditional.GetTriggerFlag -> push(ConditionValue.of(event.mask))

                // What was used on the wall, which is how one that gives only
                // to a weapon tells a sword from a torch. Nothing used is
                // nothing on all four counts rather than a refusal: a script
                // asking is entitled to an answer, and the answer is that the
                // hand was empty.
                is Conditional.OnBash.ItemExtraProperties -> push(
                    ConditionValue.of(
                        state.item(used ?: ItemIndex(ItemIndex.NOTHING))
                            ?.let { itemTypes?.kindOf(it) }
                            ?.value
                            ?: 0,
                    ),
                )

                is Conditional.OnBash.ItemType -> push(
                    ConditionValue.of(
                        state.item(used ?: ItemIndex(ItemIndex.NOTHING))?.type?.value ?: 0,
                    ),
                )

                is Conditional.OnBash.ItemValue -> push(
                    ConditionValue.of(
                        state.item(used ?: ItemIndex(ItemIndex.NOTHING))?.value ?: 0,
                    ),
                )

                is Conditional.OnBash.LastUsedItem ->
                    push(ConditionValue.of(used?.value ?: 0))

                // What a wall is now, which is not what its file says once a
                // script has changed it: a script that opens a way through
                // asks this before deciding it has already been opened.
                is Conditional.GetWallSide -> push(
                    ConditionValue.of(
                        WallSide.entries.getOrNull(token.wallIndex)
                            ?.let { side -> state.wallByte(level, token.location, side).value }
                            ?: 0
                    )
                )
                // A square asked about without a face named is asked about its
                // north one, which is the face a square's own record starts
                // with. It is the same question as asking for side zero.
                is Conditional.GetWallNumber -> push(
                    ConditionValue.of(state.wallByte(level, token.location, WallSide.NORTH).value)
                )

                is Conditional.GetLevelFlag -> push(state.isLevelFlagSet(level, token.bit))
                is Conditional.GetGlobalFlag -> push(state.isGlobalFlagSet(token.bit))
                is Conditional.GetPartyDirection ->
                    push(ConditionValue.of(state.party.facing.ordinal))

                is Conditional.DialogResult -> push(ConditionValue.of(dialogAnswer?.number ?: 0))

                // Nothing cast is nothing, which no spell's number is — so a
                // square that answers a spell answers nothing else by
                // accident.
                is Conditional.OnSpell -> push(ConditionValue.of(cast?.asWritten ?: 0))

                // Whether the party hold anybody of a class, or of a race. A
                // script asks before putting a question that only such a
                // person would raise: the graves on level 4 ask whether there
                // is a cleric or a paladin to object to being dug.
                is Conditional.HasClass -> push(state.anybodyOfClass(token.classes))

                is Conditional.HasRace ->
                    push(token.race?.let(state::anybodyOfRace) == true)

                is Conditional.GetPointerItem.ItemType ->
                    push(ConditionValue.of(state.heldAsAScriptReadsIt?.type?.value ?: 0))

                is Conditional.GetPointerItem.ItemValue ->
                    push(ConditionValue.of(state.heldAsAScriptReadsIt?.value ?: 0))

                // the slot, not what is in it
                is Conditional.GetPointerItem.ItemInHand ->
                    push(ConditionValue.of(state.inHand.value))

                is Conditional.IsMonsterAtLocation.BlockFlags ->
                    push(ConditionValue.of(state.monstersOn(token.location)))

                // What is lying about on a square, which is how a plate set
                // into the floor knows it has been weighted down — and how the
                // script that opened something knows to close it again.
                is Conditional.ItemCountAtLocation -> push(
                    ConditionValue.of(
                        state.itemsLyingOn(
                            level = level,
                            at = token.location,
                            ofType = token.type,
                            countingWhatIsInTheAir = token.countingWhatIsInTheAir,
                        )
                    )
                )

                // Whether the party themselves are standing on a square. A
                // plate asks about the square it is set into: a thing left on
                // it only counts while nobody is stood there too.
                is Conditional.IsPartyAtLocation.CheckCurrentBlock ->
                    push(state.party.position == token.location)

                // How many of the party hold one of a thing. A puzzle that
                // wants one in every hand counts the champions rather than the
                // things, so a champion carrying two of them counts once.
                is Conditional.IsPartyAtLocation.CountCharactersWithItems -> push(
                    ConditionValue.of(
                        state.championsCarrying(ofType = token.ofType, worth = token.worth)
                    )
                )

                is Conditional.IsItemAtLocation -> push(
                    ConditionValue.of(
                        state.theOneLyingOn(level, token.location, token.item)?.value ?: 0
                    )
                )

                // What a script leaves to luck: whether searching a wall turns
                // anything up, which of two things a bed has to say. The roll
                // goes on the stack as a number like any other, and what is
                // made of it is the script's business.
                is Conditional.RollDice ->
                    push(ConditionValue.of(dice.roll(token.rolls, token.size, token.base)))

                is Conditional.Equals -> compare { left, right -> left == right }
                is Conditional.NotEquals -> compare { left, right -> left != right }
                is Conditional.MoreThan -> compare { left, right -> left > right }
                is Conditional.MoreEqualsThan -> compare { left, right -> left >= right }
                is Conditional.LessThan -> compare { left, right -> left < right }
                is Conditional.LessEqualsThan -> compare { left, right -> left <= right }
                // Both operands come off the stack whatever the answer is.
                // Written as one expression, Kotlin stops at the first false
                // and leaves the second where it was, and everything the
                // condition does after that reads one value along.
                is Conditional.And -> {
                    val left = pop().isTrue
                    val right = pop().isTrue
                    push(left && right)
                }

                is Conditional.Or -> {
                    val left = pop().isTrue
                    val right = pop().isTrue
                    push(left || right)
                }
                // Taken as true so the stairs stay reachable, which is a guess
                // and shows as one: the branch the script takes from here is
                // not the branch the game would have taken.
                else -> {
                    Logger.w(TAG) {
                        gap(
                            where = "level $level, a condition",
                            at = null,
                            token = token.inWords(),
                            instead = "taken as true, so the script may take the wrong branch",
                        )
                    }
                    stage.notImplemented("a question the script asked is not written yet")
                    push(ConditionValue.TRUE)
                }
            }
        }
        return pop()
    }

    private companion object {
        const val TAG = "LevelScriptRunner"
        const val MAX_STEPS = 200

        /**
         * Which corner of its square a trap looses from, by the way it is
         * firing and which side of that way it is built into. Transcribed from
         * the game rather than worked out: a trap fires from the side of the
         * corridor it is in, not from the middle of it.
         */
        val LOOSED_FROM = listOf(2, 3, 0, 2, 1, 0, 3, 1)

        /** What anything being loosed down a corridor sounds like. */
        val LOOSED = TrackIndex(11)

        /**
         * The kind of thing the fifteenth floor's giant searches the party
         * for, and the two of that kind it takes. The script names neither:
         * the instruction it runs says only that a search happens, and what is
         * looked for is written into the engine.
         */
        val WHAT_A_GIANT_TAKES = ItemTypeId(46)
        val COINS_A_GIANT_TAKES = listOf(5, 6)

        /** Either half of a search left open, which asks only about the other. */
        const val ANYTHING = -1

        /** And what a champion losing hit points sounds like. */
        val HURT = TrackIndex(21)

        /**
         * A gap in the interpreter, written so it can be found.
         *
         * The runner narrates every instruction it runs, so a warning among
         * them is a needle in a haystack — and a gap is the one line worth
         * stopping at. It is banged out on its own line and marked with a word
         * nothing else in the log uses, which is what makes it greppable and
         * what makes it catch the eye of somebody only scrolling.
         */
        fun gap(where: String, at: ScriptOffset?, token: String, instead: String): String {
            val place = if (at == null) where else "$where, ${at.value}"
            return "\n$BANNER\n$NOT_WRITTEN $token\n  at $place\n  meanwhile: $instead\n$BANNER"
        }

        const val NOT_WRITTEN = "!!! NOT WRITTEN:"
        const val BANNER = "!!! ============================================"

        /** How long a door rests at each of the positions it slides through. */
        val DOOR_STEP = Ticks(5)
        const val MAX_SUBROUTINE_DEPTH = 10

        /** A script moving the party onto a square that moves them back again. */
        const val MAX_NESTED_TRIGGERS = 4

        /**
         * The answer a script reads where the party were asked which of them
         * would do a thing and declined to name anybody. Transcribed.
         */
        const val NOBODY_CHOSE = 99
    }
}
