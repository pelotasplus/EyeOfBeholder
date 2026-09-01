package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.Abilities
import pl.pelotasplus.eyeofbeholder.data.model.Ability
import pl.pelotasplus.eyeofbeholder.data.model.ArmorClass
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.PortraitId
import pl.pelotasplus.eyeofbeholder.data.model.Race
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.MazRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VcnRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VmpRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The remarks the second floor's squares draw out of the party, and the one
 * bit each of them is worth.
 *
 * A remark is worth testing because three things have to line up for it: the
 * square has to be stepped on, somebody of the right race or class has to be
 * in the party to make it, and it has to be the first time. Getting any of
 * them wrong is silent, which is why it goes unnoticed.
 *
 * See [pl.pelotasplus.eyeofbeholder.data.model.GameFlags] for what each of
 * the floor's bits is spent on.
 */
@Category(NeedsGameData::class)
class LevelTwoRemarksTest {

    private val resources = ResourceRepositoryImpl()

    private val level = runBlocking {
        InfRepositoryImpl(
            resourceRepository = resources,
            mazRepository = MazRepositoryImpl(resources),
            vmpRepository = VmpRepositoryImpl(resources),
            vcnRepository = VcnRepositoryImpl(resources),
            palRepository = PalRepositoryImpl(resources),
            cpsRepository = CpsRepositoryImpl(resources),
            decRepository = DecRepositoryImpl(resources),
        ).loadInf("LEVEL2.INF").getOrThrow()
    }

    private fun party(race: Race?) = List(6) {
        Champion(
            name = "Anselm",
            portrait = PortraitId(0),
            abilities = Abilities(strength = Ability(10, 10), dexterity = Ability(10, 10)),
            hitPoints = HitPoints(40, 40),
            armorClass = ArmorClass(10),
            food = Food(50),
            race = race,
            characterClass = CharacterClass.FIGHTER,
            levels = listOf(ClassLevel(1, XpPoints(0))),
            carrying = List(27) { ItemIndex(ItemIndex.NOTHING) },
            flags = ChampionFlags(1),
        )
    }

    private fun world(race: Race?, at: Location, facing: Direction = Direction.NORTH) =
        GameState(
            party = PartyState(at, facing),
            champions = party(race),
        ).arrivingAt(level = LEVEL, places = emptyList(), maz = level.subLevels[0].maz)

    private fun GameState.steppingOn(square: Location): RecordingStage {
        val stage = RecordingStage()
        runBlocking {
            LevelScriptRunner(level.script, level = LEVEL).onEvent(
                triggers = level.triggers,
                event = ScriptEvent.PARTY_ENTERED,
                state = this@steppingOn,
                stage = stage,
                at = square,
            )
        }
        return stage
    }

    private fun GameState.afterStandingOn(square: Location) = runBlocking {
        LevelScriptRunner(level.script, level = LEVEL).onEvent(
            triggers = level.triggers,
            event = ScriptEvent.PARTY_ENTERED,
            state = this@afterStandingOn,
            at = square,
        ).state
    }

    private fun RecordingStage.said() = beats
        .filterIsInstance<RecordingStage.Beat.Said>()
        .flatMap { it.speech.said }
        .mapNotNull { level.message(it) }

    @Test
    fun `a dwarf says how far underground they are`() {
        val said = world(Race.DWARF, UNDERGROUND).steppingOn(UNDERGROUND).said()

        assertEquals(1, said.size)
        assertTrue(said.single().contains("far underground"), said.single())
    }

    /** A human says the same line, which is the fallback and not a second one. */
    @Test
    fun `so does a human`() {
        val said = world(Race.HUMAN, UNDERGROUND).steppingOn(UNDERGROUND).said()

        assertEquals(1, said.size)
        assertTrue(said.single().contains("far underground"), said.single())
    }

    /** And a party of neither walks past, with the bit still there to spend. */
    @Test
    fun `an elf has nothing to say and leaves the bit unspent`() {
        val world = world(Race.ELF, UNDERGROUND)

        assertEquals(emptyList(), world.steppingOn(UNDERGROUND).said())
        assertEquals(world.flags, world.afterStandingOn(UNDERGROUND).flags)
    }

    @Test
    fun `the remark is made once and not again`() {
        val world = world(Race.DWARF, UNDERGROUND)

        assertEquals(
            emptyList(),
            world.afterStandingOn(UNDERGROUND).steppingOn(UNDERGROUND).said(),
        )
    }

    /**
     * The illusion by the cells is one remark on one bit across two squares,
     * so whichever is reached first silences the other.
     */
    @Test
    fun `two squares share the word about the illusory wall`() {
        val mage = world(Race.HUMAN, ILLUSION_NORTH).let {
            it.copy(champions = it.champions.map { who -> who.copy(characterClass = CharacterClass.MAGE) })
        }

        assertEquals(1, mage.steppingOn(ILLUSION_NORTH).said().size)
        assertEquals(
            emptyList(),
            mage.afterStandingOn(ILLUSION_NORTH).steppingOn(ILLUSION_SOUTH).said(),
        )
    }

    private companion object {
        const val LEVEL = 2

        val UNDERGROUND = Location(10, 13)
        val ILLUSION_NORTH = Location(4, 18)
        val ILLUSION_SOUTH = Location(4, 20)
    }
}
