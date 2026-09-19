package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * How far a mending reaches, which is everything a potion or a cure has in
 * common.
 *
 * Being down is not being past mending: a champion at nothing left, or below
 * it, is mended like anybody else and gets up. Ten below is another matter —
 * that far down is past what a mending reaches at all, and bringing somebody
 * back from it is a spell of its own rather than a lucky roll on a cure or a
 * well-aimed potion.
 */
class WhatMendingReachesTest {

    @Test
    fun `somebody hurt is mended, and stops at whole`() {
        assertEquals(40, mended(from = 38, by = 99))
        assertEquals(20, mended(from = 12, by = 8))
    }

    /**
     * A champion at nothing left, or under it, gets up again. It is what a
     * cure read over somebody who has just dropped is for, and what a potion
     * poured down them does.
     */
    @Test
    fun `somebody down but not gone is mended and gets up`() {
        assertEquals(7, mended(from = -5, by = 12))
        assertEquals(2, mended(from = 0, by = 2))
    }

    /**
     * Ten below is past reach. A cure rolls as much as seventeen, so without
     * this the party's scroll of mending would be a resurrection whenever the
     * dice were kind — and raising the dead would stop being worth a spell.
     */
    @Test
    fun `somebody ten below is past mending`() {
        assertEquals(
            Champion.BEYOND_RAISING,
            mended(from = Champion.BEYOND_RAISING, by = 99),
            "a mending raised the dead",
        )
    }

    /** And the line is exactly there: one point above it still mends. */
    @Test
    fun `the line between down and gone is ten below`() {
        assertEquals(1, mended(from = Champion.BEYOND_RAISING + 1, by = 10))

        assertFalse(lying(at = Champion.BEYOND_RAISING + 1).deadForGood)
        assertTrue(lying(at = Champion.BEYOND_RAISING).deadForGood)
    }

    // --- the fixture -------------------------------------------------------

    private fun mended(from: Int, by: Int): Int {
        val world = GameState(
            party = PartyState(Location(3, 11), Direction.NORTH),
            champions = listOf(lying(at = from)),
        )
        return world.championMended(PartySlot(0), by).champions[0].hitPoints.current
    }

    private fun lying(at: Int) = Champion.NOBODY.copy(
        name = "Beorn",
        flags = ChampionFlags(1),
        hitPoints = HitPoints(at, 40),
    )
}
