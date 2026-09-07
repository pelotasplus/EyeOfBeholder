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
import kotlin.test.assertTrue

/**
 * Who a monster's blow can still fall on, once champions start going down.
 *
 * Dropping is not leaving the fight. A champion at nought or below lies where
 * they fell and is beaten on there — ten points further down, and then no
 * further, because ten below is as dead as the game goes. Which is why a party
 * that loses its front rank loses the rest: there is no fighting on from
 * behind a body, and the blows carry on falling on the one who is already out.
 *
 * Only two things put a champion out of a monster's reach: being past raising,
 * and being stone.
 */
@Category(NeedsGameData::class)
class ABlowOnAChampionAlreadyDownTest {

    private val resources = ResourceRepositoryImpl()

    private fun load(name: String): List<MonsterProperty> = runBlocking {
        InfRepositoryImpl(
            resourceRepository = resources,
            mazRepository = MazRepositoryImpl(resources),
            vmpRepository = VmpRepositoryImpl(resources),
            vcnRepository = VcnRepositoryImpl(resources),
            palRepository = PalRepositoryImpl(resources),
            cpsRepository = CpsRepositoryImpl(resources),
            decRepository = DecRepositoryImpl(resources),
        ).loadInf(name).getOrThrow().subLevels[0].monsters
    }

    /** The frost giants, whose one swing falls on everybody it reaches. */
    private val giants = load("LEVEL14.INF")

    /** And a bulette, which picks one champion the way nearly everything does. */
    private val ordinary = load("LEVEL11.INF")

    private fun champion(hitPoints: Int, stone: Boolean = false) = Champion(
        name = "Anselm",
        portrait = PortraitId(0),
        abilities = Abilities(strength = Ability(10, 10), dexterity = Ability(10, 10)),
        hitPoints = HitPoints(hitPoints, STOUT),
        armorClass = ArmorClass(UNARMOURED),
        food = Food(100),
        characterClass = CharacterClass.FIGHTER,
        levels = listOf(ClassLevel(1, XpPoints(0))),
        carrying = CarrySlot.NOTHING_IN_ANY,
        flags = ChampionFlags(1).let { if (stone) it.turnedToStone() else it },
    )

    private fun world(kind: Int, party: List<Champion>) = GameState(
        party = PartyState(Location(13, 10), Direction.NORTH),
        champions = party,
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

    private fun swing(kinds: List<MonsterProperty>, world: GameState) =
        MonstersTurn(kinds = kinds, dice = everySwingLands)
            .landed(world, listOf(THE_ONE_IN_FRONT))

    /**
     * Everybody down but conscious of nothing, and the swing still lands on
     * all six. Being at nought buys a champion nothing at all.
     */
    @Test
    fun `a champion at nought is beaten on where they lie`() {
        val taken = swing(giants, world(FROST_GIANT, List(6) { champion(UNCONSCIOUS) }))

        assertEquals(
            (0..5).map(::PartySlot).toSet(),
            taken.struck.map { it.at }.toSet(),
            "the fallen were stepped over",
        )
    }

    /** And it takes them further down, as far as ten below and no further. */
    @Test
    fun `and is carried down to ten below and no further`() {
        val taken = swing(giants, world(FROST_GIANT, List(6) { champion(UNCONSCIOUS) }))

        taken.world.champions.forEachIndexed { at, who ->
            assertTrue(who.hitPoints.current < UNCONSCIOUS, "champion $at was not hurt further")
            assertTrue(who.hitPoints.current >= PAST_RAISING, "champion $at went past ten below")
        }
    }

    /**
     * Past raising is where it stops. There is nothing left to hurt, and the
     * blow goes to whoever is still there instead.
     */
    @Test
    fun `one past raising is passed over`() {
        val party = List(6) { if (it == THE_LAST_ONE_UP) champion(STOUT) else champion(PAST_RAISING) }
        val taken = swing(giants, world(FROST_GIANT, party))

        assertEquals(listOf(PartySlot(THE_LAST_ONE_UP)), taken.struck.map { it.at })
    }

    /** So is one turned to stone, who is no longer flesh to hit. */
    @Test
    fun `and so is one turned to stone`() {
        val party = List(6) {
            if (it == THE_LAST_ONE_UP) champion(STOUT) else champion(STOUT, stone = true)
        }
        val taken = swing(giants, world(FROST_GIANT, party))

        assertEquals(listOf(PartySlot(THE_LAST_ONE_UP)), taken.struck.map { it.at })
    }

    /**
     * The same rule where it matters most: an ordinary monster picks one
     * champion, and a party whose front rank has dropped does not get a
     * fresh one picked for them.
     */
    @Test
    fun `an ordinary monster keeps hitting the one it knocked down`() {
        val allDown = swing(ordinary, world(A_BULETTE, List(6) { champion(UNCONSCIOUS) }))
        assertEquals(1, allDown.struck.size)

        val whoFell = allDown.struck.single().at
        assertTrue(
            allDown.world.championIn(whoFell)!!.hitPoints.current < UNCONSCIOUS,
            "it swung at somebody already down and took nothing off them",
        )
    }

    private companion object {
        val THE_ONE_IN_FRONT = MonsterSlot(0)

        const val FROST_GIANT = 0
        const val A_BULETTE = 0

        const val STOUT = 200

        /** Down, and out of the fight, and still in reach of a fist. */
        const val UNCONSCIOUS = 0

        /** As far down as the game goes. */
        const val PAST_RAISING = -10

        const val THE_LAST_ONE_UP = 2
        const val UNARMOURED = 10
    }
}
