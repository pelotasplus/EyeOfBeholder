package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Alignment
import pl.pelotasplus.eyeofbeholder.data.model.CarrySlot
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemNames
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.NpcMeeting
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.Race
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.Sex
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.MazRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.OriginalSaveRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VcnRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VmpRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The one the seventh floor's script puts in the party itself.
 *
 * His numbers are transcribed from the game's table of the six it holds, so
 * what is asserted here is what that table says rather than what our code
 * happened to produce. What his three item slots hold is the exception worth
 * checking against the game: they are numbers into the table of items, and a
 * number pointing at the wrong thing would arm him with somebody's rations
 * without ever looking wrong.
 */
@Category(NeedsGameData::class)
class BringingTanglorAlongTest {

    private val him = NpcMeeting.TANGLOR

    @Test
    fun `he is a half-elf fighter cleric of the seventh level in both`() {
        assertEquals("Tanglor", him.name)
        assertEquals(Race.HALF_ELF, him.race)
        assertEquals(Sex.MALE, him.sex)
        assertEquals(CharacterClass.FIGHTER_CLERIC, him.characterClass)
        assertEquals(Alignment.NEUTRAL_GOOD, him.alignment)
        assertEquals(listOf(7, 7), him.levels.map { it.level })
        assertEquals(listOf(69570L, 69570L), him.levels.map { it.experience.count })
    }

    /**
     * Unlike the two found nearly dead, he is whole: the offer is his company
     * rather than his rescue.
     */
    @Test
    fun `he arrives in one piece`() {
        assertEquals(53, him.hitPoints.current)
        assertEquals(53, him.hitPoints.max)
    }

    @Test
    fun `and in the party rather than beside it`() {
        assertEquals(true, him.inTheParty)
    }

    /** Which of the six he is, which is the whole of what the opcode says. */
    @Test
    fun `he is the fifth of the six`() {
        assertEquals(4, NpcMeeting.TANGLOR_IS.value)
    }

    // --- what he carries -----------------------------------------------------

    /**
     * The three numbers are meaningless on their own, so they are read back
     * out of the game's own table and named. A dagger and a holy symbol in his
     * hands and plate mail on is a fighter/cleric's kit, and anything else
     * means the numbers point somewhere they should not.
     */
    @Test
    fun `he comes with a dagger, a holy symbol and plate mail`() {
        assertEquals("Dagger", nameOf(him.carrying[0]))
        assertEquals("Cleric Holy symbol", nameOf(him.carrying[1]))
        assertEquals("Plate Mail", nameOf(him.carrying[CarrySlot.WORN_ARMOUR.index]))
    }

    @Test
    fun `and with nothing in any other slot`() {
        val filled = him.carrying
            .mapIndexedNotNull { slot, what -> slot.takeIf { what.isSomething } }

        assertEquals(listOf(0, 1, CarrySlot.WORN_ARMOUR.index), filled)
    }

    private val resources = ResourceRepositoryImpl()

    private val names: ItemNames = runBlocking {
        ItemsRepositoryImpl(resources).loadItems().getOrThrow().names
    }

    private val shipped = runBlocking {
        OriginalSaveRepositoryImpl(resources)
            .loadOriginalSave(OriginalSaveRepositoryImpl.QUICK_START).getOrThrow()
    }

    private fun nameOf(what: ItemIndex) =
        names.of(shipped.items[what.value], types = null)

    // --- the scene the script plays ------------------------------------------

    /**
     * The answers one run of the scene gives, with the speeches in front.
     *
     * He makes two speeches before he asks anything, and a speech is read off
     * rather than answered — but it is put through the same door, so each one
     * takes an answer with it. They go in front of every real answer here.
     *
     * What the script does with the answers that follow, read off its own
     * branches:
     *
     * ```
     * 5962  if the answer given == 1     the join
     * 5971  say message 27
     * 5976  bring the npc into the party
     * 5979  go to 5992
     * 5982  take everything off 21x4     where a refusal lands
     * 5986  speak text 109
     * 5992  close the dialogue
     * ```
     *
     * So the square is swept by turning him down and by nothing else. His
     * shield and his short sword are lying on it, and they leave with him.
     */
    private fun theScene(vararg answers: Int) = listOf(READ_ON, READ_ON) + answers.toList()

    private fun asked(answers: List<Int>, party: List<Champion>): GameState = runBlocking {
        LevelScriptRunner(script = level.script, level = LEVEL).onEvent(
            triggers = level.triggers,
            event = ScriptEvent.PARTY_ENTERED,
            state = GameState(
                party = PartyState(HIS_SQUARE, Direction.NORTH),
                champions = party,
                items = shipped.items,
            ),
            stage = RecordingStage(answers),
            at = HIS_SQUARE,
        ).state
    }

    private fun GameState.stillLyingAtHisStash() = items
        .filter { it.exists && it.level == LEVEL && it.location == HIS_STASH }
        .map { names.of(it, types = null) }

    @Test
    fun `turning him down takes his shield and sword off the floor with him`() {
        assertEquals(emptyList(), asked(theScene(REFUSE), room()).stillLyingAtHisStash())
    }

    @Test
    fun `letting him along leaves them lying there`() {
        assertEquals(
            listOf("Shield", "Short sword"),
            asked(theScene(JOIN), room()).stillLyingAtHisStash(),
            "only a refusal sweeps the square",
        )
    }

    @Test
    fun `and he is in the party`() {
        assertEquals(
            true,
            asked(theScene(JOIN), room()).champions.any { it.name == "Tanglor" && it.inTheParty },
        )
    }

    /**
     * The interesting one. Saying yes and then backing out of dropping
     * somebody is not a refusal — the branch was taken at the question above,
     * so the square is not swept. The party end with neither him nor his
     * things, and his things are still on the floor to be picked up.
     */
    @Test
    fun `backing out of dropping somebody leaves the floor alone`() {
        val world = asked(theScene(JOIN, THE_WAY_OUT), full())

        assertEquals(
            listOf("Shield", "Short sword"),
            world.stillLyingAtHisStash(),
            "backing out swept the square, which only a refusal should do",
        )
        assertEquals(
            false,
            world.champions.any { it.name == "Tanglor" },
            "he came along after the way out was taken",
        )
        assertEquals(6, world.champions.count { it.inTheParty }, "somebody was dropped anyway")
    }

    private val level: Inf = runBlocking {
        InfRepositoryImpl(
            resourceRepository = resources,
            mazRepository = MazRepositoryImpl(resources),
            vmpRepository = VmpRepositoryImpl(resources),
            vcnRepository = VcnRepositoryImpl(resources),
            palRepository = PalRepositoryImpl(resources),
            cpsRepository = CpsRepositoryImpl(resources),
            decRepository = DecRepositoryImpl(resources),
        ).loadInf("LEVEL7.INF").getOrThrow()
    }

    /** A place free, so nobody is asked to leave. */
    private fun room() = List(6) { slot ->
        if (slot < 2) Champion.NOBODY.copy(name = "One$slot", flags = ChampionFlags(1))
        else Champion.NOBODY
    }

    private fun full() = List(6) { slot ->
        Champion.NOBODY.copy(name = "Name$slot", flags = ChampionFlags(1))
    }

    private companion object {
        const val LEVEL = 7

        val HIS_SQUARE = Location(20, 3)

        /** Where his shield and short sword lie. */
        val HIS_STASH = Location(21, 4)

        // Answers come back counted from one.
        const val JOIN = 1
        const val REFUSE = 2

        /** The one word under a speech, which only takes it down. */
        const val READ_ON = 1

        /** The last of the seven when a full party are asked who leaves. */
        const val THE_WAY_OUT = 7
    }
}
