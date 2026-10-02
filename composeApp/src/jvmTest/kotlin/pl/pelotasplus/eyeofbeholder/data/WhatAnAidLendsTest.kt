package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Abilities
import pl.pelotasplus.eyeofbeholder.data.model.Ability
import pl.pelotasplus.eyeofbeholder.data.model.ArmorClass
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.DamageDice
import pl.pelotasplus.eyeofbeholder.data.model.Dice
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
import pl.pelotasplus.eyeofbeholder.data.model.spellsRunThroughARest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Aid: hit points lent rather than given, and a point of help striking.
 *
 * The lending is the half worth testing. They go onto what a champion is
 * standing on without raising what they can hold, and the same number comes
 * off at the end — so an aided champion stands above their own maximum, and
 * one who spent the loan is worse off when it runs out than before it was
 * cast. That is the game's own behaviour and it is sharp enough to want
 * pinning.
 */
class WhatAnAidLendsTest {

    private val caster = PartySlot(0)
    private val aided = PartySlot(2)

    private fun aChampion(current: Int = 20, max: Int = 20) = Champion(
        name = "Anselm",
        portrait = PortraitId(0),
        abilities = Abilities(strength = Ability(10, 10), dexterity = Ability(10, 10)),
        hitPoints = HitPoints(current, max),
        armorClass = ArmorClass(10),
        food = Food(100),
        characterClass = CharacterClass.FIGHTER,
        levels = listOf(ClassLevel(1, XpPoints(0))),
        carrying = List(27) { ItemIndex(ItemIndex.NOTHING) },
        flags = ChampionFlags(1),
    )

    private fun world(party: List<Champion> = List(6) { aChampion() }) = GameState(
        party = PartyState(Location(3, 11), Direction.NORTH),
        champions = party,
    )

    /** Every die its highest, so an aid lends its whole eight. */
    private val eight = Dice { times, pips, modifier -> times * pips + modifier }

    /** And its lowest, so it lends one. */
    private val one = Dice { times, _, modifier -> times + modifier }

    private fun GameState.aid(dice: Dice = eight, whom: PartySlot = aided) =
        spellBegunOn(Spell.AID, whom, by = caster, casterLevel = 9, dice = dice)

    // ---- what it is ------------------------------------------------------

    /** A half-minute flat and another for every level: five minutes off a scroll. */
    @Test
    fun `it lasts a half-minute and one more per level`() {
        assertEquals(SpellLasts(base = 1, perLevel = 1), Spell.AID.lasts)
        assertEquals(Ticks(5460), assertNotNull(Spell.AID.lasts).castBySomeoneOfLevel(9))
    }

    @Test
    fun `it is asked for by pointing at somebody`() {
        assertEquals(SettlesOn.WHOEVER_IS_POINTED_AT, Spell.AID.settlesOn)
    }

    @Test
    fun `it lends a die of eight and helps striking by one`() {
        assertEquals(DamageDice(times = 1, pips = 8, base = 0), Spell.AID.lends)
        assertEquals(1, Spell.AID.helpsStriking)
    }

    // ---- the lending -----------------------------------------------------

    /**
     * Above their own maximum, which is the point: the maximum is not raised
     * with it, so twenty-eight of twenty is a spell running and not a bug.
     */
    @Test
    fun `the points go on above what the champion can hold`() {
        val after = assertNotNull(world().aid())
        val champion = assertNotNull(after.championIn(aided))

        assertEquals(28, champion.hitPoints.current)
        assertEquals(20, champion.hitPoints.max)
    }

    @Test
    fun `what it lent is kept with the spell`() {
        assertEquals(8, assertNotNull(world().aid(eight)).running.over(aided).single().lent)
        assertEquals(1, assertNotNull(world().aid(one)).running.over(aided).single().lent)
    }

    /** Only the champion pointed at: the other five are untouched. */
    @Test
    fun `nobody else gains anything`() {
        val after = assertNotNull(world().aid())

        assertEquals(20, assertNotNull(after.championIn(PartySlot(1))).hitPoints.current)
        assertFalse(after.running.aided(PartySlot(1)))
        assertEquals(0, after.running.helpStriking(PartySlot(1)))
    }

    @Test
    fun `it helps the one it is on strike`() {
        val after = assertNotNull(world().aid())

        assertEquals(1, after.running.helpStriking(aided))
        assertTrue(after.running.aided(aided))
    }

    // ---- taking it back --------------------------------------------------

    /** Exactly what was lent, not a fresh roll of the same dice. */
    @Test
    fun `it takes back the number it gave`() {
        val lentOne = assertNotNull(world().aid(one))
        assertEquals(21, assertNotNull(lentOne.championIn(aided)).hitPoints.current)

        val after = lentOne.spellsRunDown(Ticks(5460)).first
        assertEquals(20, assertNotNull(after.championIn(aided)).hitPoints.current)
    }

    @Test
    fun `it stands until its last tick`() {
        val cast = assertNotNull(world().aid())

        assertTrue(cast.spellsRunDown(Ticks(5459)).first.running.aided(aided))
        assertFalse(cast.spellsRunDown(Ticks(5460)).first.running.aided(aided))
    }

    /** And the help striking goes with it. */
    @Test
    fun `the help striking ends with it`() {
        val cast = assertNotNull(world().aid())

        assertEquals(0, cast.spellsRunDown(Ticks(5460)).first.running.helpStriking(aided))
    }

    /**
     * A champion who spent the loan is worse off than before it was cast.
     *
     * The points were never theirs: aided to twenty-eight, beaten down to
     * four, and the eight still comes off — which leaves them on four below
     * nothing. Harsh, and the game's own arithmetic; guarding it would be
     * inventing a rule rather than keeping one.
     */
    @Test
    fun `spending the loan leaves a champion worse off when it ends`() {
        val cast = assertNotNull(world().aid())
        val beaten = cast.championHurt(aided, pl.pelotasplus.eyeofbeholder.data.model.Damage(24))

        assertEquals(4, assertNotNull(beaten.championIn(aided)).hitPoints.current)

        val after = beaten.spellsRunDown(Ticks(5460)).first
        assertEquals(-4, assertNotNull(after.championIn(aided)).hitPoints.current)
    }

    /** A rest ends it, and ends it by taking the points back rather than keeping them. */
    @Test
    fun `resting takes the lent points back too`() {
        val cast = assertNotNull(world().aid())

        val after = cast.spellsRunThroughARest(hours = 1)

        assertFalse(after.running.aided(aided))
        assertEquals(20, assertNotNull(after.championIn(aided)).hitPoints.current)
    }

    // ---- being refused --------------------------------------------------

    /** The same champion cannot carry two, so a second is refused. */
    @Test
    fun `a champion already aided is refused another`() {
        val once = assertNotNull(world().aid())

        assertNull(once.aid())
    }

    /** But the champion beside them may have their own. */
    @Test
    fun `another champion may be aided as well`() {
        val once = assertNotNull(world().aid())
        val twice = assertNotNull(once.aid(whom = PartySlot(3)))

        assertTrue(twice.running.aided(aided))
        assertTrue(twice.running.aided(PartySlot(3)))
    }
}
