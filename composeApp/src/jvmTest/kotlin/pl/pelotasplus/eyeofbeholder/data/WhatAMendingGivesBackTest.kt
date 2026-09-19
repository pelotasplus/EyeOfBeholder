package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionBox
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.championBoxes
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.DamageDice
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.LaidOnAChampion
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.Mending
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.SparksOverTheParty
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a spell cast on one of the party gives back, and who it is asked of.
 *
 * The three cures are dice, a heal is however far short of whole somebody is,
 * and laying on hands is twice the level of whoever laid them — which is the
 * one of the five that a scroll cannot flatter, since it counts the caster
 * rather than the reading.
 */
class WhatAMendingGivesBackTest {

    private val alwaysTheHighest = Dice { times, pips, modifier -> times * pips + modifier }

    /** How a spell that mends gives it, unwrapped from what it is laid on for. */
    private val Spell.mends: Mending
        get() = assertIs<LaidOnAChampion.Mends>(laidOn, "$name does not mend anybody").by

    @Test
    fun `the three cures are their own dice`() {
        assertEquals(
            Mending.Rolled(DamageDice(times = 1, pips = 8, base = 0)),
            Spell.CURE_LIGHT_WOUNDS.mends,
        )
        assertEquals(
            Mending.Rolled(DamageDice(times = 2, pips = 8, base = 1)),
            Spell.CURE_SERIOUS_WOUNDS.mends,
        )
        assertEquals(
            Mending.Rolled(DamageDice(times = 3, pips = 8, base = 3)),
            Spell.CURE_CRITICAL_WOUNDS.mends,
        )
    }

    @Test
    fun `a cure gives what it rolls`() {
        val given = Spell.CURE_SERIOUS_WOUNDS.mends
            .given(caster(level = 9), hurt(current = 1, max = 40), alwaysTheHighest)

        assertEquals(2 * 8 + 1, given)
    }

    @Test
    fun `a heal gives back however far short of whole they are`() {
        val given = Spell.HEAL.mends
            .given(caster(level = 9), hurt(current = 12, max = 40), alwaysTheHighest)

        assertEquals(28, given)
    }

    /** And nothing at all to somebody who needed nothing. */
    @Test
    fun `a heal on somebody already whole gives nothing`() {
        val given = Spell.HEAL.mends
            .given(caster(level = 9), hurt(current = 40, max = 40), alwaysTheHighest)

        assertEquals(0, given)
    }

    /**
     * Laying on hands counts the caster and not the reading, so it is worth
     * twice what the one laying them on has learnt and no more.
     */
    @Test
    fun `laying on hands is twice the caster's own level`() {
        val mending = Spell.LAY_ON_HANDS.mends

        assertEquals(6, mending.given(caster(level = 3), hurt(1, 40), alwaysTheHighest))
        assertEquals(24, mending.given(caster(level = 12), hurt(1, 40), alwaysTheHighest))
    }

    /**
     * A cure rolls its dice at whoever it is pointed at, hurt or not — so
     * whether the casting is worth making cannot be read off what it rolled.
     *
     * This is the trap the refusal was written wrong for the first time: the
     * roll was asked whether anything was given back, and a cure on somebody
     * unhurt answers "sixteen" as readily as on somebody dying. Only the
     * champion can say, and [Champion.isWhole] is what says it.
     */
    @Test
    fun `a cure rolls the same on somebody who needs nothing`() {
        val given = Spell.CURE_SERIOUS_WOUNDS.mends
            .given(caster(level = 9), hurt(current = 40, max = 40), alwaysTheHighest)

        assertTrue(given > 0, "a cure that rolled nothing would hide the trap rather than show it")
        assertTrue(hurt(current = 40, max = 40).isWhole, "somebody unhurt is whole")
        assertFalse(hurt(current = 39, max = 40).isWhole, "a point short is not whole")
    }

    /** Nothing below whole is refused, however little is missing. */
    @Test
    fun `a champion a single point short is worth mending`() {
        assertFalse(hurt(current = 1, max = 2).isWhole)
        assertTrue(hurt(current = 2, max = 2).isWhole)
    }

    /**
     * How far a mending reaches at all — and that somebody ten below is past
     * it — is [WhatMendingReachesTest], since it is as true of a potion as of
     * a spell.
     */

    /**
     * Only the five are asked who they are for. A spell that flies is aimed by
     * pointing the party, and one laid on everybody asks nothing.
     */
    @Test
    fun `the mendings are the spells that ask which champion`() {
        val asking = Spell.entries
            .filter { it.laidOn is LaidOnAChampion.Mends }
            .toSet()

        assertEquals(
            setOf(
                Spell.CURE_LIGHT_WOUNDS,
                Spell.CURE_SERIOUS_WOUNDS,
                Spell.CURE_CRITICAL_WOUNDS,
                Spell.HEAL,
                Spell.LAY_ON_HANDS,
            ),
            asking,
        )

        assertNull(Spell.FIREBALL.laidOn, "a fireball is not laid on anybody")
        assertNull(Spell.BLESS.laidOn, "a blessing asks nothing")
    }

    /**
     * The sparks a mending shows are the party's own, held over one box.
     *
     * Naming nobody lights all six, which is what a spell laid on the whole
     * party wants and what the field drew before there was anything to name.
     */
    @Test
    fun `sparks light one box when one is named and all six when none is`() {
        val overOne = SparksOverTheParty(over = PartySlot(2))

        assertTrue(overOne.lighting(PartySlot(2)))
        assertFalse(overOne.lighting(PartySlot(0)))

        val overAll = SparksOverTheParty()
        repeat(6) { assertTrue(overAll.lighting(PartySlot(it)), "box $it went dark") }
    }

    /** And they stay over the same box as they burn down. */
    @Test
    fun `stepping the sparks keeps whose box they are over`() {
        var sparks: SparksOverTheParty? = SparksOverTheParty(over = PartySlot(4))

        repeat(SparksOverTheParty.FRAMES - 1) {
            sparks = assertNotNull(sparks).next()
            assertEquals(PartySlot(4), assertNotNull(sparks).over)
        }
        assertNull(assertNotNull(sparks).next(), "the sparks never went out")
    }

    /**
     * A box is pointed at whole while a mending is asking who it is for.
     *
     * The face opens a page and the slots hold things, and either would take a
     * click that was meant as an answer — so while the question stands the
     * whole box answers it: the name strip, the face, the two slots and the
     * bar along the bottom.
     */
    @Test
    fun `the whole of a box is that champion, not only the face`() {
        val box = ChampionBox(left = 184, top = 2)

        assertTrue(box.covers(box.portraitLeft + 1, box.portraitTop + 1), "the face")
        assertTrue(box.covers(box.handSlotLeft + 1, box.handTop(0) + 1), "a held thing")
        assertTrue(box.covers(184, 2), "the name strip")
        assertTrue(box.covers(184 + 63, 2 + 49), "the far corner")

        assertFalse(box.covers(183, 2), "a pixel left of it")
        assertFalse(box.covers(184 + 64, 2), "a pixel right of it")
        assertFalse(box.covers(184, 2 + 50), "a pixel below it")
    }

    /** And the six of them do not overlap, so a click answers for exactly one. */
    @Test
    fun `no two boxes answer for the same click`() {
        championBoxes.forEach { box ->
            listOf(
                box.left to box.top,
                box.left + ChampionBox.WIDTH - 1 to box.top + ChampionBox.HEIGHT - 1,
                box.portraitLeft + 1 to box.portraitTop + 1,
            ).forEach { (x, y) ->
                val found = championBoxes.count { it.covers(x, y) }
                assertEquals(1, found, "a click at ($x, $y) answered for $found boxes")
            }
        }
    }

    // --- the fixture -------------------------------------------------------

    private fun caster(level: Int) = Champion.NOBODY.copy(
        name = "Anselm",
        flags = ChampionFlags(1),
        levels = listOf(ClassLevel(level, XpPoints(0))),
        hitPoints = HitPoints(30, 30),
    )

    private fun hurt(current: Int, max: Int) = Champion.NOBODY.copy(
        name = "Beorn",
        flags = ChampionFlags(1),
        levels = listOf(ClassLevel(1, XpPoints(0))),
        hitPoints = HitPoints(current, max),
    )

    private fun worldWith(who: Champion) = GameState(
        party = PartyState(Location(3, 11), Direction.NORTH),
        champions = listOf(who),
    )
}
