package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.CountedBy
import pl.pelotasplus.eyeofbeholder.data.model.DamageDice
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Flight
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HarmKind
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.Projectile
import pl.pelotasplus.eyeofbeholder.data.model.SavingThrow
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.ThrownSpell
import pl.pelotasplus.eyeofbeholder.data.model.WhatAMadeThrowIsWorth
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A storm of ice, which is the one spell that does not stop where it arrives.
 *
 * Everything else in the air is spent on the square it comes down on. This
 * settles: that square takes it and so do the four beside it, which is what
 * makes it a spell for a room rather than for a corridor. It is the shortest-
 * ranged of the party's three burning spells to make up for it.
 */
@Category(NeedsGameData::class)
class AStormSettlingTest {

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
        ).loadInf("LEVEL5.INF").getOrThrow()
    }

    /** Open floor with open floor on all four sides, and a wall to the west. */
    private val comesDownOn = Location(12, 9)
    private val behindTheWall = Location(11, 9)

    /** Every die its lowest, so nothing is ever thrown off and 1d6 is one. */
    private val alwaysTheLeast = Dice { times, _, modifier -> times + modifier }

    // --- what the spell says it is -------------------------------------------

    @Test
    fun `it freezes, and it is magical as everything is`() {
        val thrown = assertNotNull(Spell.ICE_STORM.throws, "the storm throws nothing")

        assertEquals(setOf(HarmKind.MAGIC, HarmKind.COLD), thrown.hurting)
        assertEquals(DamageDice(times = 1, pips = 6, base = 0), thrown.dealing)
        assertEquals(CountedBy.EVERY_LEVEL, thrown.counted)
        assertTrue(thrown.takesTheWholeSquare, "it picked one of them off the square")
        assertTrue(thrown.takesEitherSide, "the party who cast it walked through it")
        assertTrue(thrown.spreads, "it stopped where it came down")
        assertEquals(SavingThrow.A_SPELL, thrown.thrownOff)
        assertEquals(WhatAMadeThrowIsWorth.HALF_OF_IT, thrown.aMadeThrowIsWorth)
    }

    /**
     * And it is short. A fireball and a bolt of lightning go until something
     * stops them; this one is given six squares and gives out, which is the
     * price of taking five squares where it lands.
     */
    @Test
    fun `it carries six squares and no further`() {
        val thrown = assertNotNull(Spell.ICE_STORM.throws)

        assertEquals(6, thrown.flies.reach)
        assertTrue(thrown.flies.reach < assertNotNull(Spell.FIREBALL.throws).flies.reach)
    }

    // --- and what it does where it settles -----------------------------------

    @Test
    fun `it takes the square it came down on and the four beside it`() {
        val hurt = struck(standingOn = beside(comesDownOn) + comesDownOn)

        assertEquals(5, hurt.size, "some of them were left out in the cold")
    }

    /**
     * A wall between is no help. The four are taken as if it had come down on
     * each of them in turn, and nothing asks whether it could have got there
     * — which is how a storm cast at a doorway reaches the room behind it.
     */
    @Test
    fun `a wall beside it does not keep it out`() {
        val hurt = struck(standingOn = listOf(comesDownOn, behindTheWall))

        assertEquals(
            setOf(MonsterSlot(0), MonsterSlot(1)),
            hurt.map { it.slot }.toSet(),
            "the wall kept it out",
        )
    }

    /**
     * But it spreads only from something. One that comes down on an empty
     * square is spent there, and the monsters on the next square over never
     * know it was cast — which is what stops it being a spell with a reach of
     * two squares in every direction.
     */
    @Test
    fun `one that comes down on nothing spreads nothing`() {
        val hurt = struck(standingOn = beside(comesDownOn))

        assertEquals(emptyList(), hurt, "it spread from an empty square")
    }

    // --- the fixture ---------------------------------------------------------

    private fun beside(at: Location) = Direction.entries.map { it.oneStepFrom(at) }

    private fun struck(standingOn: List<Location>): List<Flight.Hurt.AMonster> {
        val world = GameState(
            party = PartyState(Location(12, 12), Direction.NORTH),
            monsters = standingOn.mapIndexed { slot, where ->
                MonsterInstance(
                    index = MonsterSlot(slot),
                    unit = 0,
                    location = where,
                    place = SquarePlace.MIDDLE,
                    direction = Direction.SOUTH,
                    type = MonsterTypeId(0),
                    gfxIndex = 0,
                    mode = 0,
                    pause = 0,
                    weapon = 0,
                    pocketItem = 0,
                    hitPoints = HitPoints(90, 90),
                )
            },
            inFlight = listOf(aStorm(comesDownOn)),
        )

        return Flight(
            sublevel = level.subLevels[0],
            level = 5,
            kinds = listOf(level.subLevels[0].monsters.first()),
            dice = alwaysTheLeast,
        ).onward(world).hurt.filterIsInstance<Flight.Hurt.AMonster>()
    }

    private fun aStorm(at: Location) = Spell.ICE_STORM.throws!!.let { thrown ->
        Projectile(
            what = null,
            at = at,
            place = SquarePlace.MIDDLE,
            going = Direction.NORTH,
            squaresLeft = thrown.flies.reach,
            thrownBy = Projectile.Thrower.AChampion(PartySlot(0)),
            harm = thrown.dealtBy(ThrownSpell.AS_READ_FROM_A_SCROLL),
            spell = thrown.flies,
            leaving = false,
        )
    }
}
