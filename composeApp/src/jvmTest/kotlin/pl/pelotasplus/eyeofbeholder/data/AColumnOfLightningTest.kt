package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Abilities
import pl.pelotasplus.eyeofbeholder.data.model.Ability
import pl.pelotasplus.eyeofbeholder.data.model.ArmorClass
import pl.pelotasplus.eyeofbeholder.data.model.CarrySlot
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.PortraitId
import pl.pelotasplus.eyeofbeholder.data.model.Race
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.ScriptRun
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

/**
 * The columns of lightning on the seventh floor, which are the only blows in
 * the game that allow a saving throw.
 *
 * Twenty-seven of them, all the same shape: 6d6 or thereabouts to everybody,
 * a throw against spells, and half damage to whoever makes it. They did
 * nothing at all until there was a throw to roll.
 */
@Category(NeedsGameData::class)
class AColumnOfLightningTest {

    private val resources = ResourceRepositoryImpl()

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

    private fun champion() = Champion(
        name = "Anselm",
        portrait = PortraitId(0),
        abilities = Abilities(
            strength = Ability(10, 10),
            dexterity = Ability(10, 10),
            constitution = Ability(10, 10),
        ),
        hitPoints = HitPoints(60, 60),
        armorClass = ArmorClass(10),
        food = Food(100),
        race = Race.HUMAN,
        characterClass = CharacterClass.FIGHTER,
        levels = listOf(ClassLevel(1, XpPoints(0))),
        carrying = List(CarrySlot.ALL_OF_THEM) { ItemIndex(ItemIndex.NOTHING) },
        flags = ChampionFlags(1),
    )

    private fun struckBy(dice: Dice): ScriptRun = runBlocking {
        LevelScriptRunner(script = level.script, level = LEVEL, dice = dice).onEvent(
            triggers = level.triggers,
            event = ScriptEvent.PARTY_ENTERED,
            state = GameState(
                party = PartyState(UNDER_THE_COLUMN, Direction.NORTH),
                champions = List(6) { champion() },
            ).arrivingAt(level = LEVEL, places = emptyList(), maz = level.subLevels[0].maz),
            at = UNDER_THE_COLUMN,
        )
    }

    /**
     * Every die its highest: 6d6 is thirty-six, and a twenty-sided die shows
     * twenty, so every throw is made and every champion takes half.
     */
    private val everyDieHighest = Dice { times, pips, modifier -> times * pips + modifier }

    /** And its lowest: 6d6 is six, and a one on a d20 makes no throw at all. */
    private val everyDieLowest = Dice { times, _, modifier -> times + modifier }

    @Test
    fun `a throw that is made costs half`() {
        val struck = struckBy(everyDieHighest)

        assertEquals(6, struck.hurt.size)
        assertEquals(setOf(36 / 2), struck.hurt.values.map { it.points }.toSet())
    }

    @Test
    fun `and one that is missed costs the whole of it`() {
        val struck = struckBy(everyDieLowest)

        assertEquals(setOf(6), struck.hurt.values.map { it.points }.toSet())
    }

    /** The blow is real either way, which it was not before. */
    @Test
    fun `the column takes hit points off`() {
        assertEquals(60 - 18, struckBy(everyDieHighest).state.champions.first().hitPoints.current)
    }

    private companion object {
        const val LEVEL = 7

        /** The first of the twenty-seven, reached by walking onto its square. */
        val UNDER_THE_COLUMN = Location(17, 5)
    }
}
