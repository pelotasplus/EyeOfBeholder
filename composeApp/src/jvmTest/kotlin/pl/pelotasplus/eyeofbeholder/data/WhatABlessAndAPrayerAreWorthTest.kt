package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Abilities
import pl.pelotasplus.eyeofbeholder.data.model.Ability
import pl.pelotasplus.eyeofbeholder.data.model.ArmorClass
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.PortraitId
import pl.pelotasplus.eyeofbeholder.data.model.SettlesOn
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.SpellLasts
import pl.pelotasplus.eyeofbeholder.data.model.Ticks
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * A bless and a prayer: a point on the party's own rolls, and for the prayer
 * a point off the rolls of whatever strikes at them.
 *
 * Neither settles on anybody. They sit over the party, so all six have them
 * at once and none of them carries one — which is what makes the pair worth
 * testing together, since a spell on six champions and a spell over the party
 * are told apart by nothing else on screen.
 *
 * What separates them is the second half. A prayer is the only thing in the
 * game that works on both sides of a fight at once; a bless only helps the
 * swinging.
 */
class WhatABlessAndAPrayerAreWorthTest {

    private val caster = PartySlot(0)
    private val someoneElse = PartySlot(4)

    private fun aChampion() = Champion(
        name = "Anselm",
        portrait = PortraitId(0),
        abilities = Abilities(strength = Ability(10, 10), dexterity = Ability(10, 10)),
        hitPoints = HitPoints(20, 20),
        armorClass = ArmorClass(10),
        food = Food(100),
        characterClass = CharacterClass.CLERIC,
        levels = listOf(ClassLevel(1, XpPoints(0))),
        carrying = List(27) { ItemIndex(ItemIndex.NOTHING) },
        flags = ChampionFlags(1),
    )

    private fun world() = GameState(
        party = PartyState(Location(3, 11), Direction.NORTH),
        champions = List(6) { aChampion() },
    )

    private fun GameState.cast(spell: Spell, casterLevel: Int = 9) =
        spellBegunWhereItSettles(spell, by = caster, casterLevel = casterLevel)

    // ---- how long each runs ----------------------------------------------

    /**
     * Six half-minutes however good the cleric is. Every other counted spell
     * in the game grows with its caster, so a bless that did not would read
     * as a duration somebody forgot to multiply — it is the table's own
     * number, and this is what says so.
     */
    @Test
    fun `a bless runs three minutes whoever casts it`() {
        assertEquals(SpellLasts(base = 6, perLevel = 0), Spell.BLESS.lasts)

        val lasts = assertIs<SpellLasts>(Spell.BLESS.lasts)
        assertEquals(Ticks(3276), lasts.castBySomeoneOfLevel(1))
        assertEquals(Ticks(3276), lasts.castBySomeoneOfLevel(9))
    }

    /** And a prayer the other way about: nothing flat, a half-minute a level. */
    @Test
    fun `a prayer runs a half-minute for every level of its caster`() {
        assertEquals(SpellLasts(base = 0, perLevel = 1), Spell.PRAYER.lasts)

        val lasts = assertIs<SpellLasts>(Spell.PRAYER.lasts)
        assertEquals(Ticks(546), lasts.castBySomeoneOfLevel(1))
        assertEquals(Ticks(4914), lasts.castBySomeoneOfLevel(9))
    }

    // ---- whom they settle on ---------------------------------------------

    @Test
    fun `neither settles on anybody`() {
        assertEquals(SettlesOn.THE_PARTY, Spell.BLESS.settlesOn)
        assertEquals(SettlesOn.THE_PARTY, Spell.PRAYER.settlesOn)
    }

    /** Over the party means over the one who read it and the five who did not. */
    @Test
    fun `a bless helps all six strike, the caster no more than the rest`() {
        val after = assertNotNull(world().cast(Spell.BLESS))

        assertEquals(1, after.running.helpStriking(caster))
        assertEquals(1, after.running.helpStriking(someoneElse))
    }

    @Test
    fun `and so does a prayer`() {
        val after = assertNotNull(world().cast(Spell.PRAYER))

        assertEquals(1, after.running.helpStriking(caster))
        assertEquals(1, after.running.helpStriking(someoneElse))
    }

    // ---- the half only a prayer has --------------------------------------

    /**
     * The point of the spell. A prayer is read on both sides of a fight: the
     * party roll a point higher and whatever swings at them rolls a point
     * lower, which is two points of difference from one casting.
     */
    @Test
    fun `a prayer also makes the party harder to hit`() {
        val after = assertNotNull(world().cast(Spell.PRAYER))

        assertEquals(1, after.running.hindranceStriking(caster))
        assertEquals(1, after.running.hindranceStriking(someoneElse))
    }

    /** A bless does nothing for being hit, only for hitting. */
    @Test
    fun `a bless does not`() {
        val after = assertNotNull(world().cast(Spell.BLESS))

        assertEquals(0, after.running.hindranceStriking(caster))
        assertEquals(0, after.running.hindranceStriking(someoneElse))
    }

    /** Cast together they add, since each is taken off the same roll in turn. */
    @Test
    fun `the two together are worth two points of striking`() {
        val both = assertNotNull(assertNotNull(world().cast(Spell.BLESS)).cast(Spell.PRAYER))

        assertEquals(2, both.running.helpStriking(someoneElse))
        assertEquals(1, both.running.hindranceStriking(someoneElse))
    }

    // ---- what they look like ---------------------------------------------

    /** Yellow round every box, which is the only sign either of them shows. */
    @Test
    fun `both frame all six boxes yellow`() {
        val blessed = assertNotNull(world().cast(Spell.BLESS))
        val prayed = assertNotNull(world().cast(Spell.PRAYER))

        assertEquals(true, blessed.running.framedYellow(someoneElse))
        assertEquals(true, prayed.running.framedYellow(someoneElse))
        assertEquals(false, world().running.framedYellow(someoneElse))
    }

    /** And the frame goes when the spell does. */
    @Test
    fun `the frame ends with the spell`() {
        val cast = assertNotNull(world().cast(Spell.BLESS))

        assertEquals(true, cast.spellsRunDown(Ticks(3275)).first.running.framedYellow(caster))
        assertEquals(false, cast.spellsRunDown(Ticks(3276)).first.running.framedYellow(caster))
    }

    // ---- casting one twice -----------------------------------------------

    /**
     * Refused rather than restarted, which is what lets the caster be told
     * and keeps a scroll from being spent on something already in force.
     */
    @Test
    fun `neither can be cast again while it stands`() {
        assertNull(assertNotNull(world().cast(Spell.BLESS)).cast(Spell.BLESS))
        assertNull(assertNotNull(world().cast(Spell.PRAYER)).cast(Spell.PRAYER))
    }

    /** And can be cast again once it has run out. */
    @Test
    fun `a prayer can be cast again after it ends`() {
        val cast = assertNotNull(world().cast(Spell.PRAYER))
        val ended = cast.spellsRunDown(Ticks(4914)).first

        assertEquals(0, ended.running.helpStriking(caster))
        assertNotNull(ended.cast(Spell.PRAYER))
    }

    // ---- running out -----------------------------------------------------

    @Test
    fun `a bless stands until its last tick`() {
        val cast = assertNotNull(world().cast(Spell.BLESS))

        assertEquals(1, cast.spellsRunDown(Ticks(3275)).first.running.helpStriking(caster))
        assertEquals(0, cast.spellsRunDown(Ticks(3276)).first.running.helpStriking(caster))
    }

    /** And a prayer takes both of its halves with it. */
    @Test
    fun `a prayer ends on both sides at once`() {
        val cast = assertNotNull(world().cast(Spell.PRAYER))
        val ended = cast.spellsRunDown(Ticks(4914)).first

        assertEquals(0, ended.running.helpStriking(caster))
        assertEquals(0, ended.running.hindranceStriking(caster))
    }
}
