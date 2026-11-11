package pl.pelotasplus.eyeofbeholder.agentserver

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File

/**
 * Several of a player's actions as one call, each still done through `act`
 * and `wait` exactly as an agent would do them one by one, so nothing here
 * can do what a player could not. They exist so that a corridor costs one
 * call rather than ten, and they stop the moment anything happens that a
 * player would stop for.
 */
class Playing(private val game: Games.Game, private val map: TheMap, private val home: AgentHome) {

    data class Outcome(val done: String, val observation: JsonObject?)

    /**
     * The moves in order, until one is refused or something turns up. What
     * came into sight on the floor along the way is listed, since a walk past
     * a thing is how it gets missed.
     */
    suspend fun walk(moves: List<String>): Outcome {
        var made = 0
        val noticed = linkedSetOf<String>()
        fun said(why: String) = if (noticed.isEmpty()) why else "$why Seen lying about: ${noticed.joinToString("; ")}."

        for (move in moves) {
            val action = MOVES[move.lowercase()] ?: return Outcome("No such move \"$move\"; moves are ${MOVES.keys}.", observe())

            untilFree()
            val before = observe()
            act(action)?.let { refused -> return Outcome(said("Stopped after $made of ${moves.size}: \"$move\" refused: $refused."), observe()) }
            untilFree()
            val after = observe() ?: return Outcome("The game gave no observation after \"$move\".", null)
            noticed += itemsIn(after)

            made++
            if (move.lowercase() in STEPS && before?.get("at") == after["at"]) {
                return Outcome(said("Stopped after $made of ${moves.size}: \"$move\" went nowhere."), after)
            }
            stopFor(after)?.let { why -> return Outcome(said("Stopped after $made of ${moves.size}: $why."), after) }
        }
        return Outcome(said("Walked all ${moves.size}."), observe())
    }

    private fun itemsIn(seen: JsonObject): List<String> {
        val at = seen["at"]?.jsonObject ?: return emptyList()
        val party = TheMap.Square(at["x"]!!.jsonPrimitive.int, at["y"]!!.jsonPrimitive.int)
        val facing = TheMap.Facing.valueOf(seen["facing"]!!.jsonPrimitive.content)
        return squares(seen).flatMap { square ->
            val where = party.seenAt(facing, square["ahead"]!!.jsonPrimitive.int, square["across"]!!.jsonPrimitive.int)
            (square["items"] as? JsonArray).orEmpty().map { "${it.jsonObject["name"]!!.jsonPrimitive.content} at (${where.key})" }
        }
    }

    /**
     * The front rank striking at what stands in front of the party until
     * nothing does, someone is badly hurt, or [rounds] are up. What each
     * hand showed after its swing is collected, which is how a player knows
     * a fight is going their way.
     *
     * Each round is strike, step aside while the hands recover, step back in:
     * standing in front of a monster through its swing is how parties die in
     * this game. With no open side it stops rather than trade blows, unless
     * [stand] says to stay put and take them.
     */
    suspend fun fight(rounds: Int, stand: Boolean = false): Outcome {
        val shown = mutableListOf<String>()
        repeat(rounds) { round ->
            val now = observe() ?: return Outcome("The game gave no observation.", null)
            if (!somethingInFront(now)) return Outcome(summary("Nothing stands in front after $round rounds", shown), now)
            now["dialogue"]?.let { return Outcome(summary("A dialogue is up", shown), now) }

            // Checked before the blow: a dance that cannot step aside is a
            // trade of blows, which is the one thing it exists to avoid.
            val aside = if (stand) null else sideStepFrom(now)
                ?: return Outcome(summary("No side square is open to step aside to; fight with stand to trade blows here", shown), now)

            act(STRIKE)?.let { refused -> return Outcome(summary("The strike was refused: $refused", shown), now) }
            game.ask("wait", buildJsonObject { put("ms", REPORT_SHOWS) }, within = REPORT_SHOWS + Games.ANSWER_WITHIN)
            observe()?.let { shown += "round ${round + 1}: " + handReports(it).ifEmpty { listOf("nothing shown") }.joinToString(", ") }

            if (aside == null) {
                game.ask("wait", buildJsonObject { put("ms", HANDS_RECOVER) }, within = HANDS_RECOVER + Games.ANSWER_WITHIN)
            } else {
                stepWhenFree(aside)?.let { refused -> return Outcome(summary("The step aside was refused: $refused", shown), observe()) }
                game.ask("wait", buildJsonObject { put("ms", HANDS_RECOVER) }, within = HANDS_RECOVER + Games.ANSWER_WITHIN)
                stepWhenFree(OTHER_SIDE.getValue(aside))?.let { refused ->
                    return Outcome(summary("The step back in was refused: $refused", shown), observe())
                }
                untilFree()
            }

            val after = observe() ?: return Outcome(summary("The game gave no observation", shown), null)
            badlyHurt(after)?.let { who -> return Outcome(summary("$who is badly hurt", shown), after) }
        }
        return Outcome(summary("$rounds rounds fought", shown), observe())
    }

    /**
     * [spell] cast by [champion] through the book or holy symbol in their
     * hand, the pages turned until it is found; [on] picks who a laid-on
     * spell goes to. A page is read from the open book after turning to it,
     * so this knows no more than the clicks would show.
     */
    suspend fun cast(champion: Int, spell: String, on: Int?): Outcome {
        untilFree()
        val now = observe() ?: return Outcome("The game gave no observation.", null)
        val caster = party(now).getOrNull(champion) ?: return Outcome("No champion $champion.", now)
        val name = caster["name"]!!.jsonPrimitive.content
        val ready = (caster["spells"] as? JsonArray).orEmpty().map { it.jsonPrimitive.content }
        if (ready.none { it.equals(spell, ignoreCase = true) }) {
            return Outcome("$name has no \"$spell\" ready; ready: $ready.", now)
        }
        val hand = (caster["hands"] as? JsonArray).orEmpty().map { it.jsonPrimitive.content.lowercase() }
            .indexOfFirst { BOOKS.any(it::contains) }
            .takeIf { it >= 0 } ?: return Outcome("$name holds no spellbook or holy symbol.", now)

        actWhenFree(useHand(champion, hand))?.let { return Outcome("Opening the book was refused: $it", observe()) }
        for (page in 0 until PAGES) {
            actWhenFree(bookTab(page))?.let { return Outcome("Turning to page ${page + 1} was refused: $it", observe()) }
            wait(PAGE_TURNS)
            val lines = (observe()?.get("book")?.jsonObject?.get("lines") as? JsonArray).orEmpty()
                .map { (it as? JsonPrimitive)?.contentOrNull }
            val line = lines.indexOfFirst { it.equals(spell, ignoreCase = true) }
            if (line < 0) continue

            actWhenFree(bookLine(line))?.let { return Outcome("Casting was refused: $it", observe()) }
            if (on != null) {
                wait(PAGE_TURNS)
                actWhenFree(castOn(on))?.let { return Outcome("Laying it on champion $on was refused: $it", observe()) }
            }
            wait(SPELL_LEAVES)
            val after = observe()
            if (after?.get("book") != null) act(BOOK_ABORT)
            val said = (after?.get("messages") as? JsonArray).orEmpty().map { it.jsonPrimitive.content }.takeLast(2)
            return Outcome("Cast. Last said: ${said.joinToString(" / ")}", observe())
        }
        act(BOOK_ABORT)
        return Outcome("\"$spell\" is ready but was not found on any page of $name's book.", observe())
    }

    private suspend fun wait(ms: Long) {
        game.ask("wait", buildJsonObject { put("ms", ms) }, within = ms + Games.ANSWER_WITHIN)
    }

    /** [action], done as soon as a swing or the last step lets it be. */
    private suspend fun actWhenFree(action: JsonObject): String? {
        var waited = 0L
        while (true) {
            val refused = act(action) ?: return null
            if (waited >= MOST_A_STEP_TAKES || (A_SWING_COMING !in refused && STEP_STILL_GOING !in refused)) return refused
            wait(POLL)
            waited += POLL
        }
    }

    /** A step to [side], taken as soon as a swing or the last step lets it be. */
    private suspend fun stepWhenFree(side: String): String? {
        var waited = 0L
        while (true) {
            val refused = act(step(side)) ?: return null
            if (waited >= MOST_A_STEP_TAKES || (A_SWING_COMING !in refused && STEP_STILL_GOING !in refused)) return refused
            game.ask("wait", buildJsonObject { put("ms", POLL) }, within = POLL + Games.ANSWER_WITHIN)
            waited += POLL
        }
    }

    /**
     * Lets the game run until [condition] holds or [mostMs] go by: `free`
     * for the party able to step again, `dialogue` for a conversation to
     * come up, `no_dialogue` for it to go.
     */
    suspend fun waitUntil(condition: String, mostMs: Long): Outcome {
        val test: (JsonObject) -> Boolean = when (condition) {
            "free" -> { o -> o["busy"] == null && o["dialogue"] == null && o["menu"] == null }
            "dialogue" -> { o -> o["dialogue"] != null }
            "no_dialogue" -> { o -> o["dialogue"] == null }
            else -> return Outcome("No such condition \"$condition\"; they are free, dialogue, no_dialogue.", observe())
        }
        var waited = 0L
        while (true) {
            val now = observe() ?: return Outcome("The game gave no observation.", null)
            if (test(now)) return Outcome("Waited $waited ms.", now)
            if (condition == "free") {
                // waiting does not answer a question or shut a menu
                if (now["dialogue"] != null) return Outcome("A dialogue is up; it wants an answer, not a wait.", now)
                if (now["menu"] != null) return Outcome("A menu is open; it wants a choice, not a wait.", now)
            }
            if (waited >= mostMs) return Outcome("Gave up after $waited ms.", now)
            game.ask("wait", buildJsonObject { put("ms", POLL) }, within = POLL + Games.ANSWER_WITHIN)
            waited += POLL
        }
    }

    /**
     * Saves through the camp menu under [name], then keeps a copy of the
     * save outside the game's six slots, so any checkpoint can be gone back
     * to however many there are. A slot already named [name] is saved over,
     * then an empty one, then [over] if given, then the oldest save — whose
     * kept copy still lets it be gone back to.
     */
    suspend fun checkpoint(name: String, over: String?): Outcome {
        openTheSlots(saving = true)?.let { return Outcome(it, observe()) }

        val slots = menuChoices()
        fun slotNamed(wanted: String?) = wanted?.let { slots.firstOrNull { it.equals(wanted, ignoreCase = true) } }
        val slot = slotNamed(name)
            ?: slotNamed(EMPTY_SLOT)
            ?: slotNamed(over)
            ?: slotNamed(home.oldestSave())
        if (slot == null) {
            act(choose("Cancel"))
            leaveTheCamp()
            return Outcome("Every slot is taken and none could be picked to save over; give over as one of $slots.", observe())
        }

        act(choose(slot))?.let { return Outcome("Could not open \"$slot\": $it", observe()) }
        act(buildJsonObject { put("type", "press"); put("key", "RUBOUT"); put("times", LONGEST_NAME) })
        act(buildJsonObject { put("type", "type"); put("text", name) })?.let { return Outcome("Could not type the name: $it", observe()) }
        act(buildJsonObject { put("type", "press"); put("key", "ACCEPT") })?.let { return Outcome("Could not accept the name: $it", observe()) }
        untilFree()

        val written = home.saveNamed(name) ?: return Outcome("Saved as \"$name\", but no save file of that name was found to keep.", observe())
        val kept = home.keep(name, written)
        val replaced = if (slot.equals(name, ignoreCase = true) || slot.equals(EMPTY_SLOT, ignoreCase = true)) "" else " over \"$slot\""
        return Outcome("Saved as \"$name\"$replaced and kept at ${kept.path}.", observe())
    }

    /**
     * Goes back to a checkpoint kept by [checkpoint], putting its save back
     * into a slot first if the slot has since been saved over.
     */
    suspend fun restore(name: String, over: String?): Outcome {
        if (home.saveNamed(name) == null) {
            val keptCopy = home.kept(name)
                ?: return Outcome("No save or checkpoint is called \"$name\"; the kept ones are ${home.checkpoints()}.", observe())
            home.putBack(keptCopy, over)?.let { return Outcome(it, observe()) }
        }

        openTheSlots(saving = false)?.let { return Outcome(it, observe()) }
        act(choose(name))?.let { return Outcome("Could not load \"$name\": $it", observe()) }
        untilFree()
        return Outcome("Back at \"$name\".", observe())
    }

    private suspend fun openTheSlots(saving: Boolean): String? {
        val now = observe() ?: return "The game gave no observation."
        if (now["dialogue"] != null) return "A dialogue is up; answer it first."
        if (now["menu"] == null) act(CAMP)?.let { return "The camp would not open: $it" }
        act(choose("Game Options"))?.let { return "Game Options would not open: $it" }
        act(choose(if (saving) "Save Game" else "Load Game"))?.let { return "The slots would not open: $it" }
        return null
    }

    private suspend fun leaveTheCamp() {
        repeat(3) { if (observe()?.get("menu") != null) act(CAMP) }
    }

    private suspend fun menuChoices(): List<String> =
        (observe()?.get("menu")?.jsonObject?.get("choices") as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty()

    /** Lets the game run in short steps until a step would be taken again, for as long as a step can take. */
    private suspend fun untilFree() {
        var waited = 0L
        while (waited < MOST_A_STEP_TAKES) {
            val busy = observe()?.get("busy")?.jsonPrimitive?.content ?: return
            if (busy != STEP_STILL_GOING && busy != A_SWING_COMING) return
            game.ask("wait", buildJsonObject { put("ms", POLL) }, within = POLL + Games.ANSWER_WITHIN)
            waited += POLL
        }
    }

    private suspend fun observe(): JsonObject? =
        (game.ask("observe")["result"] as? JsonObject)?.also(map::saw)

    /** Nothing when done, or why not. */
    private suspend fun act(action: JsonObject): String? {
        val answer = game.ask("act", buildJsonObject { put("action", action) })
        answer["error"]?.let { return it.jsonPrimitive.content }
        return (answer["result"] as? JsonObject)?.get("refused")?.jsonPrimitive?.content
    }

    private fun stopFor(seen: JsonObject): String? {
        if (seen["dialogue"] != null) return "a dialogue is up"
        if (seen["menu"] != null) return "a menu is open"
        if (somethingWithin(seen, NEAR_ENOUGH)) return "a monster is in sight"
        badlyHurt(seen)?.let { return "$it is badly hurt" }
        val struck = party(seen).filter { it["struckFor"] != null }.map { it["name"]!!.jsonPrimitive.content }
        if (struck.isNotEmpty()) return "${struck.joinToString()} struck"
        return null
    }

    private fun squares(seen: JsonObject) = (seen["inSight"] as? JsonArray)?.map { it.jsonObject }.orEmpty()

    private fun party(seen: JsonObject) = (seen["party"] as? JsonArray)?.map { it.jsonObject }.orEmpty()

    private fun somethingInFront(seen: JsonObject) = squares(seen).any {
        it["ahead"]!!.jsonPrimitive.int == 1 && it["across"]!!.jsonPrimitive.int == 0 &&
            (it["monsters"] as? JsonArray)?.isNotEmpty() == true
    }

    private fun somethingWithin(seen: JsonObject, ahead: Int) = squares(seen).any {
        it["ahead"]!!.jsonPrimitive.int <= ahead && (it["monsters"] as? JsonArray)?.isNotEmpty() == true
    }

    // A hand at rest saying `last:` is telling an older swing, not this round's.
    private fun handReports(seen: JsonObject): List<String> = party(seen).flatMap { who ->
        (who["hands"] as? JsonArray).orEmpty().map { it.jsonPrimitive.content }
            .filter { it.endsWith(")") }
            .map { it.substringAfterLast('(').dropLast(1) }
            .mapNotNull { said ->
                when {
                    said.startsWith("recovering, last: ") -> said.removePrefix("recovering, last: ")
                    said == "recovering" || said.startsWith("last: ") -> null
                    else -> said
                }
            }
            .map { "${who["name"]!!.jsonPrimitive.content} $it" }
    }

    private fun summary(why: String, shown: List<String>) =
        if (shown.isEmpty()) "$why." else "$why. ${shown.joinToString("; ")}."

    private fun choose(label: String) = buildJsonObject { put("type", "choose"); put("label", label) }

    private companion object {
        val MOVES = mapOf(
            "forward" to step("FORWARD"),
            "back" to step("BACK"),
            "strafe_left" to step("LEFT"),
            "strafe_right" to step("RIGHT"),
            "left" to turn("LEFT"),
            "right" to turn("RIGHT"),
        )
        val STEPS = setOf("forward", "back", "strafe_left", "strafe_right")
        val OTHER_SIDE = mapOf("LEFT" to "RIGHT", "RIGHT" to "LEFT")

        val STRIKE = buildJsonObject { put("type", "front_rank_strikes") }
        val CAMP = buildJsonObject { put("type", "camp") }

        val BOOK_ABORT = buildJsonObject { put("type", "book_abort") }

        fun step(way: String) = buildJsonObject { put("type", "step"); put("way", way) }
        fun turn(side: String) = buildJsonObject { put("type", "turn"); put("toThe", side) }
        fun useHand(champion: Int, hand: Int) = buildJsonObject { put("type", "use_hand"); put("champion", champion); put("hand", hand) }
        fun bookTab(page: Int) = buildJsonObject { put("type", "book_tab"); put("page", page) }
        fun bookLine(line: Int) = buildJsonObject { put("type", "book_line"); put("line", line) }
        fun castOn(champion: Int) = buildJsonObject { put("type", "cast_on"); put("champion", champion) }

        val BOOKS = listOf("spellbook", "holy symbol")
        const val PAGES = 6

        /** Long enough for a page turned or a line clicked to take; sent back to back, the second click is sometimes lost. */
        const val PAGE_TURNS = 200L
        const val SPELL_LEAVES = 400L

        /** The two reasons a step is turned away that time alone clears, as `busy` says them. */
        const val STEP_STILL_GOING = "the last step is still being taken"
        const val A_SWING_COMING = "the party are being swung at"

        const val EMPTY_SLOT = "Empty Slot"
        const val LONGEST_NAME = 19

        const val POLL = 100L
        const val MOST_A_STEP_TAKES = 2_000L
        const val REPORT_SHOWS = 400L
        const val HANDS_RECOVER = 2_600L
        const val NEAR_ENOUGH = 2
    }
}

/**
 * Whoever is below a third of their hit points, which is when a player
 * stops to heal. The dead are past healing, and stopping for them would
 * stop every step until they are raised.
 */
internal fun badlyHurt(seen: JsonObject): String? =
    (seen["party"] as? JsonArray).orEmpty().map { it.jsonObject }.firstOrNull { who ->
        val down = (who["conditions"] as? JsonArray).orEmpty().any { it.jsonPrimitive.content == "down" }
        val (now, most) = who["hitPoints"]!!.jsonPrimitive.content.split('/').map { it.toInt() }
        !down && now * BADLY_HURT_DIVISOR < most
    }?.let { "${it["name"]!!.jsonPrimitive.content} (${it["hitPoints"]!!.jsonPrimitive.content})" }

private const val BADLY_HURT_DIVISOR = 3

/**
 * Where the agent keeps what outlives a session: its map, and every
 * checkpoint it has saved, beside where the game writes its own saves.
 */
class AgentHome(val directory: File, private val saves: File) {

    val mapFile: File get() = File(directory, "map.json")
    private val checkpointsDirectory: File get() = File(directory, "checkpoints")

    /** The game's own save file whose name is [name], if any slot has it. */
    fun saveNamed(name: String): File? = saves.listFiles { f -> f.extension == "json" }
        ?.firstOrNull { descriptionOf(it).equals(name, ignoreCase = true) }

    fun keep(name: String, save: File): File {
        checkpointsDirectory.mkdirs()
        return File(checkpointsDirectory, "$name.json").also { save.copyTo(it, overwrite = true) }
    }

    /** The name of the save least recently written, which is the one to give up when all six are taken. */
    fun oldestSave(): String? = saves.listFiles { f -> f.extension == "json" }
        ?.minByOrNull { it.lastModified() }
        ?.let(::descriptionOf)

    fun kept(name: String): File? = File(checkpointsDirectory, "$name.json").takeIf { it.exists() }

    fun checkpoints(): List<String> = checkpointsDirectory.listFiles()?.map { it.nameWithoutExtension }?.sorted().orEmpty()

    /** Into an empty slot, or over the slot named [over]. Says why not, or nothing. */
    fun putBack(kept: File, over: String?): String? {
        val target = (1..SLOTS).map { File(saves, "slot$it.json") }.firstOrNull { !it.exists() }
            ?: over?.let(::saveNamed)
            ?: return "Every slot is taken; give over as the name of a save to put \"${kept.nameWithoutExtension}\" back over."
        saves.mkdirs()
        kept.copyTo(target, overwrite = true)
        return null
    }

    private fun descriptionOf(save: File): String? = runCatching {
        Games.json.parseToJsonElement(save.readText()).jsonObject["description"]?.jsonPrimitive?.content
    }.getOrNull()

    companion object {
        private const val SLOTS = 6

        /** EOB_AGENT_HOME, or a folder in the home directory; and the desktop game's own save folder. */
        fun here(): AgentHome {
            val home = System.getProperty("user.home")
            val os = System.getProperty("os.name").orEmpty().lowercase()
            val saves = when {
                os.contains("mac") -> "$home/Library/Application Support/EyeOfBeholder/saves"
                os.contains("win") -> "${System.getenv("APPDATA") ?: "$home/AppData/Roaming"}/EyeOfBeholder/saves"
                else -> "${System.getenv("XDG_DATA_HOME") ?: "$home/.local/share"}/EyeOfBeholder/saves"
            }
            return AgentHome(File(System.getenv("EOB_AGENT_HOME") ?: "$home/.eob-agent"), File(saves))
        }
    }
}

/**
 * Which way the party can step aside out of a monster's reach, as `step`
 * names it, or null when both sides are shut. A side is open when the
 * party's own square has no face there, or an open door, and nothing is
 * seen standing on the square beyond it. Left is tried first.
 */
internal fun sideStepFrom(seen: JsonObject): String? {
    val squares = (seen["inSight"] as? JsonArray)?.map { it.jsonObject }.orEmpty()
    val own = squares.firstOrNull { it.at(ahead = 0, across = 0) } ?: return null

    fun open(face: String, across: Int): Boolean {
        val wall = own[face]?.jsonPrimitive?.content
        if (wall != null && wall != "open door") return false
        return squares.none { it.at(ahead = 0, across = across) && (it["monsters"] as? JsonArray)?.isNotEmpty() == true }
    }

    return when {
        open("left", -1) -> "LEFT"
        open("right", 1) -> "RIGHT"
        else -> null
    }
}

private fun JsonObject.at(ahead: Int, across: Int) =
    this["ahead"]?.jsonPrimitive?.int == ahead && this["across"]?.jsonPrimitive?.int == across
