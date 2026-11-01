package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterMode
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.TurningUndead
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Level 5's two clerics on 13x8, with the party on 13x9 facing them, made
 * undead here at whatever resistance a test wants. The only undead that really
 * exist are level 2's skeleton warriors, at resistance 8.
 */
@Category(NeedsGameData::class)
class WhatTurningUndeadDoesTest {

    private companion object {
        val level: Inf by lazy {
            runBlocking {
                val resources = ResourceRepositoryImpl()
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
        }
    }

    private val sub get() = level.subLevels[0]
    private val onTheLeft = MonsterSlot(17)
    private val onTheRight = MonsterSlot(16)

    private fun undead(resistance: Int) = sub.monsters.map { it.copy(typeFlags = 4, tuResist = resistance) }

    private fun world(party: Location = Location(13, 9)) = GameState(
        party = PartyState(party, Direction.NORTH),
    ).arrivingAt(
        level = 5,
        places = level.monsterInstances,
        maz = sub.maz,
        kinds = sub.monsters,
        dice = Dice { times, pips, modifier -> times * pips + modifier },
    )

    private fun throwing(this20: Int) = Dice { _, pips, modifier -> if (pips == 20) this20 else modifier }

    private fun cast(resistance: Int, casterLevel: Int, thrown: Int, on: GameState = world()) =
        TurningUndead(undead(resistance), level = 5, dice = throwing(thrown)).castAt(on, casterLevel)

    private fun GameState.monster(slot: MonsterSlot) = assertNotNull(monsters.firstOrNull { it.index == slot })

    @Test
    fun `only turn undead does it`() {
        assertEquals(listOf(Spell.TURN_UNDEAD), Spell.entries.filter { it.turnsUndead })
    }

    @Test
    fun `a throw that reaches the number turns them for forty walks with their backs to the party`() {
        // Resistance 8 at level 8 needs a 10.
        val after = cast(resistance = 8, casterLevel = 8, thrown = 10)

        assertTrue(after.reachedAny)
        listOf(onTheLeft, onTheRight).forEach {
            assertEquals(40, after.world.monster(it).afraidFor)
            assertEquals(Direction.NORTH, after.world.monster(it).direction)
        }
    }

    @Test
    fun `a turned creature is hunting once the fright has run out`() {
        val pacing = world().let { it.copy(monsters = it.monsters.map { m -> m.copy(mode = MonsterMode.PACING.asWritten) }) }

        var turned = cast(resistance = 8, casterLevel = 8, thrown = 10, on = pacing).world.monster(onTheLeft)
        repeat(40) { turned = turned.aWalkLessAfraid() }

        assertFalse(turned.afraid)
        assertEquals(MonsterMode.HUNTING, turned.whatItDoes)
    }

    @Test
    fun `one the destroying blow leaves standing does not flash`() {
        val tough = world().let { w ->
            w.copy(monsters = w.monsters.map { it.copy(hitPoints = it.hitPoints.copy(current = 1000)) })
        }

        val after = cast(resistance = 1, casterLevel = 6, thrown = 1, on = tough)

        assertFalse(after.world.monster(onTheLeft).struck)
    }

    @Test
    fun `a resistance past the table cannot be reached`() {
        assertFalse(cast(resistance = 10, casterLevel = 14, thrown = 20).reachedAny)
    }

    @Test
    fun `the throw is made for the living too`() {
        var throws = 0
        val counting = Dice { _, _, modifier -> throws++; modifier }

        TurningUndead(sub.monsters, level = 5, dice = counting).castAt(world(), casterLevel = 14)

        assertEquals(1, throws)
    }

    @Test
    fun `a throw one short leaves them where they were`() {
        val after = cast(resistance = 8, casterLevel = 8, thrown = 9)

        assertFalse(after.reachedAny)
        assertFalse(after.world.monster(onTheLeft).afraid)
    }

    @Test
    fun `a number of ninety nine is beyond any throw`() {
        // Resistance 8 at level 3.
        assertFalse(cast(resistance = 8, casterLevel = 3, thrown = 20).reachedAny)
    }

    @Test
    fun `where the table says so they are destroyed on any throw`() {
        // Resistance 1 at level 6.
        val after = cast(resistance = 1, casterLevel = 6, thrown = 1)

        assertTrue(after.reachedAny)
        assertTrue(after.world.monsters.none { it.index == onTheLeft || it.index == onTheRight })
    }

    @Test
    fun `a level past the table reads the next row's first column`() {
        // Resistance 8 at level 20 is as level 14, which falls on the 99 of resistance 9.
        assertFalse(cast(resistance = 8, casterLevel = 20, thrown = 20).reachedAny)
        // Resistance 8 at level 13 is the row's own last, a 0.
        assertTrue(cast(resistance = 8, casterLevel = 13, thrown = 1).reachedAny)
    }

    @Test
    fun `a creature that was asked once is never asked again`() {
        val refused = cast(resistance = 8, casterLevel = 8, thrown = 1)
        assertTrue(refused.world.monster(onTheLeft).askedToTurn)

        val again = cast(resistance = 8, casterLevel = 8, thrown = 20, on = refused.world)

        assertFalse(again.reachedAny)
        assertFalse(again.world.monster(onTheLeft).afraid)
    }

    @Test
    fun `the living are not asked at all`() {
        val after = TurningUndead(sub.monsters, level = 5, dice = throwing(20)).castAt(world(), casterLevel = 14)

        assertFalse(after.reachedAny)
        assertFalse(after.world.monster(onTheLeft).askedToTurn)
    }

    @Test
    fun `an empty square ahead does nothing`() {
        assertFalse(cast(resistance = 1, casterLevel = 14, thrown = 20, on = world(Location(2, 2))).reachedAny)
    }
}
