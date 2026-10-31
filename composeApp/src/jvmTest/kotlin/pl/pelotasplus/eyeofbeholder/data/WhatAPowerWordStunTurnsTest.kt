package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.FleshToStone
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterImmunities
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterProperty
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.TurningToStone
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Category(NeedsGameData::class)
class WhatAPowerWordStunTurnsTest {

    private val kind: MonsterProperty = runBlocking {
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
    }.subLevels[0].monsters.first().copy(immunities = MonsterImmunities(0), dmgModifierEvade = 0)

    private val party = Location(13, 9)
    private val oneAhead = Location(13, 8)
    private val twoAhead = Location(13, 7)
    private val threeAhead = Location(13, 6)

    /** No dodging, and no throw made. */
    private val nothingEscapes = Dice { _, pips, _ -> if (pips == 100) 100 else 1 }

    private fun standing(at: Location, hitPoints: Int = 50, slot: Int = 0) = MonsterInstance(
        index = MonsterSlot(slot),
        unit = 0,
        location = at,
        place = SquarePlace.MIDDLE,
        direction = Direction.SOUTH,
        type = MonsterTypeId(kind.id),
        gfxIndex = 0,
        mode = 0,
        pause = 0,
        weapon = 0,
        pocketItem = 0,
        hitPoints = HitPoints(hitPoints, 200),
    )

    private fun spoken(
        vararg monsters: MonsterInstance,
        kinds: List<MonsterProperty> = listOf(kind),
        dice: Dice = nothingEscapes,
        reachable: (Location) -> Boolean = { true },
    ) = FleshToStone(kinds, dice).spokenBy(
        GameState(party = PartyState(party, Direction.NORTH), monsters = monsters.toList()),
        PartySlot(0),
        reachable,
    )

    private fun FleshToStone.Cast.stone(slot: Int = 0) =
        world.monsters.first { it.index == MonsterSlot(slot) }.stone

    /** All of this row of the spell table is empty. */
    @Test
    fun `it has no sound, no spark and no clock, and gives nobody away`() {
        val word = Spell.POWER_WORD_STUN

        assertEquals(TurningToStone.BY_A_WORD, word.turnsToStone)
        assertNull(word.heardAs)
        assertNull(word.lasts)
        assertFalse(word.throwsSparks || word.sparksOverTheParty || word.sparksOverTheCaster)
        assertFalse(word.givesTheCasterAway)
    }

    /** Hit points as they stand now, not the most it could have. */
    @Test
    fun `eighty nine hit points left is stone and ninety is not`() {
        assertTrue(spoken(standing(oneAhead, hitPoints = 89)).stone())
        assertFalse(spoken(standing(oneAhead, hitPoints = 90)).stone())
    }

    @Test
    fun `no die is thrown for a creature with too much left`() {
        val noDice = Dice { _, _, _ -> error("a die was thrown") }

        val cast = spoken(standing(oneAhead, hitPoints = 90), dice = noDice)

        assertEquals(FleshToStone.CameTo.RESISTED, cast.came)
    }

    @Test
    fun `it reaches a second square and not a third`() {
        assertTrue(spoken(standing(twoAhead)).stone())
        assertFalse(spoken(standing(threeAhead)).stone())
    }

    @Test
    fun `it takes the nearer of two and leaves the other`() {
        val cast = spoken(standing(twoAhead, slot = 1), standing(oneAhead, slot = 0))

        assertTrue(cast.stone(slot = 0))
        assertFalse(cast.stone(slot = 1))
    }

    /** The nearer one being too healthy does not pass the word on to the one behind. */
    @Test
    fun `a healthy creature in front shelters the one behind it`() {
        val cast = spoken(standing(oneAhead, hitPoints = 90, slot = 0), standing(twoAhead, slot = 1))

        assertFalse(cast.stone(slot = 0))
        assertFalse(cast.stone(slot = 1))
    }

    @Test
    fun `it does not reach past what the party cannot see onto`() {
        val cast = spoken(standing(twoAhead), reachable = { it != oneAhead })

        assertEquals(FleshToStone.CameTo.NOTHING_IN_REACH, cast.came)
        assertFalse(cast.stone())
    }

    @Test
    fun `a creature can dodge it, be passed by it, or throw it off`() {
        val dodging = Dice { _, _, _ -> 1 }
        val quick = listOf(kind.copy(dmgModifierEvade = 99))
        assertFalse(spoken(standing(oneAhead), kinds = quick, dice = dodging).stone())

        val warded = listOf(kind.copy(immunities = MonsterImmunities(SHRUGS_OFF_MAGIC)))
        assertFalse(spoken(standing(oneAhead), kinds = warded).stone())

        val makesItsThrow = Dice { _, pips, _ -> if (pips == 100) 100 else 20 }
        assertFalse(spoken(standing(oneAhead), dice = makesItsThrow).stone())
    }

    private companion object {
        const val SHRUGS_OFF_MAGIC = 0x10
    }
}
