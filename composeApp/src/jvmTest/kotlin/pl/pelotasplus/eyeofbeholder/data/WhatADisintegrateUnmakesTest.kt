package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Disintegration
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterImmunities
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterProperty
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSize
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.WallByte
import pl.pelotasplus.eyeofbeholder.data.model.WallSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Disintegrate, which unmakes the square ahead rather than hurting what is on
 * it — and takes the wall with it either way.
 *
 * The wall is the half that surprises: it goes on every casting, whether
 * anything was standing there, whether that thing survived, and whether there
 * was a creature at all. Cast down an empty corridor it is a way through a
 * wall, which is as much what the spell is for as killing is.
 */
class WhatADisintegrateUnmakesTest {

    private val here = Location(10, 10)
    private val ahead = Location(10, 9)

    private fun aMonster(at: Location, level: Int = 1) = MonsterInstance(
        index = MonsterSlot(0),
        unit = 1,
        location = at,
        place = SquarePlace.MIDDLE,
        direction = Direction.SOUTH,
        type = MonsterTypeId(0),
        gfxIndex = 0,
        mode = 0,
        pause = 0,
        weapon = 0,
        pocketItem = 0,
        hitPoints = HitPoints(400, 400),
    ).copy(level = level)

    private fun world(vararg monsters: MonsterInstance) = GameState(
        party = PartyState(here, Direction.NORTH),
        monsters = monsters.toList(),
    )

    private fun aKind(
        immunities: MonsterImmunities = MonsterImmunities(0),
        evade: Int = 0,
    ) = MonsterProperty(
        id = 0,
        armorClass = 9, hitChance = 13, level = 1,
        hpDcTimes = 0, hpDcPips = 0, hpDcBase = 1,
        attacksPerRound = 1, dmgDc = emptyList(),
        immunities = immunities, capsFlags = 0, typeFlags = 2, experience = 120,
        size = MonsterSize.FILLS_THE_SQUARE,
        sound1 = 0, sound2 = 0,
        numRemoteAttacks = 0, remoteWeaponChangeMode = null, numRemoteWeapons = null,
        remoteWeapons = emptyList(), tuResist = -1, dmgModifierEvade = evade,
        decorations = emptyList(),
    )

    /** Every throw failed and no dodge made, so the spell always lands. */
    private val nothingGetsAway = Dice { times, pips, _ ->
        if (times == 1 && pips == 20) 1 else if (times == 1 && pips == 100) 100 else times * pips
    }

    /** Every throw made, so nothing is ever unmade. */
    private val everythingHolds = Dice { times, pips, _ ->
        if (times == 1 && pips == 20) 20 else if (times == 1 && pips == 100) 100 else times * pips
    }

    private fun castOn(world: GameState, dice: Dice, reachable: Boolean = true) =
        Disintegration(kinds = listOf(aKind()), dice = dice)
            .castAheadOf(world, level = 1, reachable = reachable)

    // ---- the wall --------------------------------------------------------

    @Test
    fun `cast at nothing it still unmakes the wall`() {
        val after = castOn(world(), nothingGetsAway)

        assertNull(after.gone)
        WallSide.entries.forEach { side ->
            assertEquals(0, after.world.wallByte(1, ahead, side).value, "the $side side stood")
        }
    }

    /** A wall that was there goes, which is how a passage is opened. */
    @Test
    fun `a standing wall is unmade`() {
        val walled = world().wallChanged(1, ahead, WallSide.SOUTH, WallByte(12))
        val after = castOn(walled, nothingGetsAway)

        assertEquals(0, after.world.wallByte(1, ahead, WallSide.SOUTH).value)
    }

    @Test
    fun `the wall goes even when the creature survives`() {
        val walled = world(aMonster(ahead)).wallChanged(1, ahead, WallSide.SOUTH, WallByte(12))
        val after = castOn(walled, everythingHolds)

        assertNull(after.gone)
        assertEquals(0, after.world.wallByte(1, ahead, WallSide.SOUTH).value)
    }

    // ---- the creature ----------------------------------------------------

    /** Four hundred hit points and none of them counted for anything. */
    @Test
    fun `what it takes is unmade rather than wounded`() {
        val after = castOn(world(aMonster(ahead)), nothingGetsAway)

        assertEquals(MonsterSlot(0), after.gone)
        assertEquals(emptyList(), after.world.monsters)
    }

    /** Untouched rather than half unmade — there is no partial version. */
    @Test
    fun `a creature that makes its throw is untouched`() {
        val after = castOn(world(aMonster(ahead)), everythingHolds)

        assertNull(after.gone)
        assertEquals(400, after.world.monsters.single().hitPoints?.current)
    }

    /** What this magic does not reach at all is never even given a throw. */
    @Test
    fun `a creature beyond this magic is untouched`() {
        val immune = MonsterImmunities(0x10)
        val after = Disintegration(kinds = listOf(aKind(immune)), dice = nothingGetsAway)
            .castAheadOf(world(aMonster(ahead)), level = 1, reachable = true)

        assertNull(after.gone)
        assertEquals(400, after.world.monsters.single().hitPoints?.current)
    }

    /** And one quick enough is out of the way before anything is rolled. */
    @Test
    fun `a creature quick enough gets out of the way`() {
        val quick = Dice { times, pips, _ ->
            // Its dodge succeeds; every throw after it would have failed.
            if (times == 1 && pips == 100) 0 else if (times == 1 && pips == 20) 1 else times * pips
        }

        val after = Disintegration(kinds = listOf(aKind(evade = 50)), dice = quick)
            .castAheadOf(world(aMonster(ahead)), level = 1, reachable = true)

        assertNull(after.gone)
    }

    // ---- what it does not reach -----------------------------------------

    /**
     * Behind a closed door nothing is touched — but the door is a wall of
     * that square, and goes. So the first casting opens the way and the
     * second is the one that reaches what was behind it.
     */
    @Test
    fun `what stands behind a closed door is untouched, though the door goes`() {
        val shut = world(aMonster(ahead)).wallChanged(1, ahead, WallSide.SOUTH, WallByte(12))
        val after = castOn(shut, nothingGetsAway, reachable = false)

        assertNull(after.gone)
        assertEquals(400, after.world.monsters.single().hitPoints?.current)
        assertEquals(0, after.world.wallByte(1, ahead, WallSide.SOUTH).value)
    }

    @Test
    fun `nothing off the square ahead is touched`() {
        val around = world(
            aMonster(Location(10, 11)),
            aMonster(Location(11, 10)),
        )
        val after = castOn(around, nothingGetsAway)

        assertNull(after.gone)
        assertEquals(2, after.world.monsters.size)
    }

    /** A creature on another floor is not on the square ahead at all. */
    @Test
    fun `something on another floor is not touched`() {
        val after = castOn(world(aMonster(ahead, level = 2)), nothingGetsAway)

        assertNull(after.gone)
        assertEquals(1, after.world.monsters.size)
    }

    @Test
    fun `it follows whichever way the party face`() {
        val behind = Location(10, 11)
        val facingSouth = world(aMonster(behind))
            .copy(party = PartyState(here, Direction.SOUTH))

        val after = castOn(facingSouth, nothingGetsAway)

        assertEquals(MonsterSlot(0), after.gone)
        assertTrue(after.world.monsters.isEmpty())
    }
}
