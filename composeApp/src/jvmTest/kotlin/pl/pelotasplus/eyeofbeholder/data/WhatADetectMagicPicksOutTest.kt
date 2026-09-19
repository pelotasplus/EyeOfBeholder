package pl.pelotasplus.eyeofbeholder.data

import kotlinx.collections.immutable.toImmutableList
import pl.pelotasplus.eyeofbeholder.data.model.Cps
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemIconId
import pl.pelotasplus.eyeofbeholder.data.model.ItemNameId
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypeId
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.Palette
import pl.pelotasplus.eyeofbeholder.data.model.PaletteIndex
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.RGB
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.Ticks
import pl.pelotasplus.eyeofbeholder.data.model.tintedAsMagical
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * What a detect magic shows: the things with magic on them drawn blue, and
 * nothing else said about any of them.
 *
 * It does not identify. A blue icon says the thing is worth carrying to
 * somebody who can read it, and stops there — which is the whole of what the
 * spell is for, and why it is a first-level one.
 */
class WhatADetectMagicPicksOutTest {

    /**
     * A palette with one blue in it, at the entry the tint pulls toward, and
     * 255 distinct colours carrying no blue at all.
     *
     * Built out of six-bit channels and handed over as a .PAL's bytes are,
     * because that is the width the tint does its arithmetic in: an
     * eight-bit ramp would put several entries on the same six-bit colour,
     * and "the nearest entry" stops meaning anything among duplicates.
     */
    private val palette = Palette.fromVgaBytes(
        name = "TEST.PAL",
        bytes = UByteArray(Palette.BYTE_SIZE) { at ->
            val i = at / 3
            val channel = at % 3
            when {
                i == BLUE -> if (channel == 2) 63u else 0u
                channel == 0 -> (i % 64).toUByte()
                channel == 1 -> ((i / 64) * 16).toUByte()
                else -> 0u
            }
        },
    )

    private fun anItem(flags: Int) = Item(
        nameUnidentified = ItemNameId(0),
        nameIdentified = ItemNameId(0),
        flags = flags,
        icon = ItemIconId(3),
        type = ItemTypeId(1),
        place = SquarePlace.MIDDLE,
        location = Location(0, 0),
        next = 0,
        prev = 0,
        level = 1,
        value = 0,
    )

    // ---- what "magical" means on an item -------------------------------

    /** The mark is the item's own, and is not the same mark as being known. */
    @Test
    fun `an item says whether there is magic on it`() {
        assertTrue(anItem(flags = 0x80).magical)
        assertFalse(anItem(flags = 0x00).magical)
    }

    /** Magical and identified are different bits and neither implies the other. */
    @Test
    fun `magic on a thing is not the same as knowing what it is`() {
        val unknownButMagical = anItem(flags = 0x80)
        assertTrue(unknownButMagical.magical)
        assertFalse(unknownButMagical.identified)

        val knownAndPlain = anItem(flags = 0x40)
        assertFalse(knownAndPlain.magical)
        assertTrue(knownAndPlain.identified)
    }

    // ---- the tint ------------------------------------------------------

    /** Nothing is drawn for index 0, so it is left alone rather than tinted. */
    @Test
    fun `the transparent index stays transparent`() {
        assertEquals(0, palette.magicalTintTable[0])
    }

    /**
     * A colour is shifted toward the blue and then snapped to whatever entry
     * the palette actually has, so where it lands is the palette's answer as
     * much as the shift's: given only reds to choose between, a colour pulled
     * toward blue comes back red, just a different one.
     *
     * Which means there is no rule here about the direction a colour moves
     * that holds for every palette, and asserting one would only be a claim
     * about the palette it was written against. What the tint looks like is
     * asked of the rendered frame, where it can be seen.
     */
    @Test
    fun `a colour lands on an entry the palette has`() {
        (1 until 256).forEach { i ->
            val landed = palette.magicalTintTable[i]
            assertTrue(landed in 1 until 256, "colour $i landed outside the palette, on $landed")
        }
    }

    /**
     * And none of them is left where it was, because the snap that ends the
     * shift refuses the colour it started from.
     *
     * The blue itself is the exception the snap makes: it is what everything
     * is pulled toward, so it is allowed to stay.
     */
    @Test
    fun `no colour tints to itself`() {
        (1 until 256).filter { it != BLUE }.forEach { i ->
            assertNotEquals(i, palette.magicalTintTable[i], "colour $i tinted to itself")
        }

        assertEquals(BLUE, palette.magicalTintTable[BLUE])
    }

    /**
     * The blue is a different pull from the darkening one, so a thing picked
     * out by a detect magic cannot be confused with a thing far away.
     */
    @Test
    fun `the tint is not the distance darkening`() {
        assertNotEquals(palette.distanceFadeTable, palette.magicalTintTable)
    }

    private fun anIcon() = Cps.ItemIcon(
        w = 2,
        h = 1,
        pixels = listOf(PaletteIndex(0), PaletteIndex(200)),
    )

    @Test
    fun `tinting an icon leaves its transparent pixels alone`() {
        val tinted = anIcon().tintedAsMagical(palette)

        assertEquals(PaletteIndex(0), tinted.pixels[0])
    }

    @Test
    fun `tinting an icon moves its painted pixels`() {
        val tinted = anIcon().tintedAsMagical(palette)

        assertNotEquals(PaletteIndex(200), tinted.pixels[1])
        assertEquals(PaletteIndex(palette.magicalTintTable[200]), tinted.pixels[1])
    }

    /** The shape is untouched: it is the same icon in another colour. */
    @Test
    fun `tinting changes no pixel's place`() {
        val tinted = anIcon().tintedAsMagical(palette)

        assertEquals(2, tinted.w)
        assertEquals(1, tinted.h)
        assertEquals(2, tinted.pixels.size)
    }

    // ---- when it is showing --------------------------------------------

    private fun world() = GameState(party = PartyState(Location(3, 11), Direction.NORTH))

    @Test
    fun `nothing is showing before it is cast`() {
        assertFalse(world().magicIsShowing)
    }

    @Test
    fun `casting it shows the magic`() {
        val after = assertNotNull(
            world().spellBegunOverTheParty(Spell.DETECT_MAGIC, PartySlot(0), casterLevel = 9)
        )

        assertTrue(after.magicIsShowing)
    }

    /** The cleric's is the same spell and shows the same thing. */
    @Test
    fun `a cleric's detect magic shows it too`() {
        val after = assertNotNull(
            world().spellBegunOverTheParty(Spell.A_CLERICS_DETECT_MAGIC, PartySlot(0), 9)
        )

        assertTrue(after.magicIsShowing)
    }

    /**
     * Read off a scroll it runs for nine minutes — 1092 ticks for each of the
     * nine levels a scroll is always read at — and goes out at the end of them.
     */
    @Test
    fun `read from a scroll it shows for nine minutes and then stops`() {
        val cast = assertNotNull(
            world().spellBegunOverTheParty(Spell.DETECT_MAGIC, PartySlot(0), 9)
        )

        assertTrue(cast.spellsRunDown(Ticks(9827)).first.magicIsShowing)
        assertFalse(cast.spellsRunDown(Ticks(9828)).first.magicIsShowing)
    }

    /** Cast again while it is running it is refused, so no scroll is spent. */
    @Test
    fun `casting it twice is refused`() {
        val once = assertNotNull(
            world().spellBegunOverTheParty(Spell.DETECT_MAGIC, PartySlot(0), 9)
        )

        assertEquals(null, once.spellBegunOverTheParty(Spell.DETECT_MAGIC, PartySlot(1), 9))
    }

    /**
     * The mage's and the cleric's are written as two spells, and one running
     * is no reason to refuse the other — but either alone shows the magic.
     */
    @Test
    fun `the cleric's is not refused by the mage's`() {
        val mages = assertNotNull(
            world().spellBegunOverTheParty(Spell.DETECT_MAGIC, PartySlot(0), 9)
        )

        assertNotNull(mages.spellBegunOverTheParty(Spell.A_CLERICS_DETECT_MAGIC, PartySlot(1), 9))
    }

    private companion object {
        /** Where the one blue sits in the made-up palette above. */
        const val BLUE = 11
    }
}
