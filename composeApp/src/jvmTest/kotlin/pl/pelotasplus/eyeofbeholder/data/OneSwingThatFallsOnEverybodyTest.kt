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
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterProperty
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.MonstersTurn
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.PortraitId
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A monster whose one swing comes down on everybody it reaches rather than on
 * the champion in front of it.
 *
 * Nearly everything in the game picks the nearest champion still standing and
 * stops there, which is what makes a party's order worth arranging: the
 * armoured stand in front and the casters behind them are safe. Two things in
 * the whole dungeon ignore that — the frost giants of the fourteenth floor and
 * the dragon at the end — and against those two the back rank is in exactly
 * the same danger as the front, each champion rolled against and hurt
 * separately.
 *
 * Which of them do it is the file's own bit and not a judgement made here.
 */
@Category(NeedsGameData::class)
class OneSwingThatFallsOnEverybodyTest {

    private val resources = ResourceRepositoryImpl()

    private fun load(name: String): Inf = runBlocking {
        InfRepositoryImpl(
            resourceRepository = resources,
            mazRepository = MazRepositoryImpl(resources),
            vmpRepository = VmpRepositoryImpl(resources),
            vcnRepository = VcnRepositoryImpl(resources),
            palRepository = PalRepositoryImpl(resources),
            cpsRepository = CpsRepositoryImpl(resources),
            decRepository = DecRepositoryImpl(resources),
        ).loadInf(name).getOrThrow()
    }

    /** The fourteenth floor, whose one kind is the frost giant. */
    private val giants: List<MonsterProperty> = load("LEVEL14.INF").subLevels[0].monsters

    /** The last floor's inner half, which holds both halves of the rule. */
    private val theEnd: List<MonsterProperty> = load("LEVEL16.INF").subLevels[1].monsters

    /** Every twenty-sided roll comes up [pips], so only the sums decide. */
    private fun everyRollA(pips: Int) = Dice { times, sides, modifier ->
        if (times == 1 && sides == 20 && modifier == 0) pips else times * sides + modifier
    }

    private fun championWith(armour: Int) = champion(STOUT).copy(armorClass = ArmorClass(armour))

    private fun champion(hitPoints: Int) = Champion(
        name = "Anselm",
        portrait = PortraitId(0),
        abilities = Abilities(strength = Ability(10, 10), dexterity = Ability(10, 10)),
        hitPoints = HitPoints(hitPoints, STOUT),
        armorClass = ArmorClass(UNARMOURED),
        food = Food(100),
        characterClass = CharacterClass.FIGHTER,
        levels = listOf(ClassLevel(1, XpPoints(0))),
        carrying = CarrySlot.NOTHING_IN_ANY,
        flags = ChampionFlags(1),
    )

    /** The party at 13x10 with one monster of [kind] on 13x9 facing them. */
    private fun world(kind: Int) = GameState(
        party = PartyState(Location(13, 10), Direction.NORTH),
        champions = List(6) { champion(STOUT) },
        monsters = listOf(
            MonsterInstance(
                index = THE_ONE_IN_FRONT,
                unit = 0,
                location = Location(13, 9),
                place = SquarePlace.MIDDLE,
                direction = Direction.SOUTH,
                type = MonsterTypeId(kind),
                gfxIndex = 0,
                mode = 0,
                pause = 0,
                weapon = 0,
                pocketItem = 0,
                hitPoints = HitPoints(100, 100),
                provoked = true,
            ),
        ),
    )

    /** Every swing connects, so what is counted is whom it fell on. */
    private val everySwingLands = Dice { times, pips, modifier ->
        if (times == 1 && pips == 20 && modifier == 0) 20 else times * pips + modifier
    }

    private fun swing(
        kinds: List<MonsterProperty>,
        world: GameState,
        dice: Dice = everySwingLands,
    ): MonstersTurn.Taken = MonstersTurn(kinds = kinds, dice = dice)
        .landed(world, listOf(THE_ONE_IN_FRONT))

    // --- which kinds do it -----------------------------------------------------

    @Test
    fun `the frost giant strikes everyone it reaches and the dragon does too`() {
        assertTrue(giants[FROST_GIANT].strikesEveryoneItReaches, "the giant hits one only")
        assertTrue(theEnd[THE_DRAGON].strikesEveryoneItReaches, "the dragon hits one only")
    }

    @Test
    fun `and the thing standing beside the dragon does not`() {
        assertFalse(theEnd[ITS_MASTER].strikesEveryoneItReaches)
    }

    // --- and what that comes to ------------------------------------------------

    /**
     * All six, each with a wound of its own. The party's order buys nothing
     * here, which is the whole of why these two are dangerous.
     */
    @Test
    fun `one swing of a frost giant falls on the whole party`() {
        val taken = swing(giants, world(FROST_GIANT))

        assertEquals(
            (0..5).map(::PartySlot).toSet(),
            taken.struck.map { it.at }.toSet(),
            "it did not reach all six",
        )
        assertTrue(taken.struck.all { it.damage.landed }, "it reached somebody for nothing")
    }

    /** And an ordinary monster's swing falls on one of them. */
    @Test
    fun `an ordinary monster's swing falls on one champion`() {
        val taken = swing(theEnd, world(ITS_MASTER))

        assertEquals(1, taken.struck.size, "it hit ${taken.struck.map { it.at }}")
    }

    /**
     * How many of the six connect is the hit roll and nothing else, and what
     * decides that roll is armour: a blow lands on `1d20 >= 3 - armour class`.
     *
     * Three is the second best number in the game — only the dragon's four
     * comes near it — so a champion has to be armoured past anything the
     * fourteenth floor is reached with before the giant starts missing. A
     * party in cloth is hit six times out of six, and that is the game rather
     * than a fault in it.
     */
    @Test
    fun `armour is the only thing that thins the swing`() {
        val inCloth = swing(giants, world(FROST_GIANT), everyRollA(1))
        assertEquals(6, inCloth.struck.size, "the worst roll there is missed somebody")

        val armoured = MonstersTurn(kinds = giants, dice = everyRollA(1))
            .landed(
                GameState(
                    party = PartyState(Location(13, 10), Direction.NORTH),
                    champions = List(6) { championWith(armour = PLATE_AND_MORE) },
                    monsters = world(FROST_GIANT).monsters,
                ),
                listOf(THE_ONE_IN_FRONT),
            )
        assertEquals(emptyList(), armoured.struck, "armour bought nothing")
    }

    /**
     * One swing is one sound, however many champions it lands on. Without this
     * a giant's turn plays the same roar six times over the top of itself.
     */
    @Test
    fun `six blows are one swing and are heard once`() {
        val taken = swing(giants, world(FROST_GIANT))

        assertEquals(1, taken.struck.count { it.heard != null }, "the swing was heard more than once")
    }

    private companion object {
        val THE_ONE_IN_FRONT = MonsterSlot(0)

        const val FROST_GIANT = 0
        const val ITS_MASTER = 0
        const val THE_DRAGON = 1

        const val STOUT = 200
        const val UNARMOURED = 10

        /**
         * Armoured past anything the fourteenth floor is reached with: enough
         * that a one on the die no longer reaches, which unarmoured cannot be.
         */
        const val PLATE_AND_MORE = -3
    }
}
