package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Abilities
import pl.pelotasplus.eyeofbeholder.data.model.Ability
import pl.pelotasplus.eyeofbeholder.data.model.ArmorClass
import pl.pelotasplus.eyeofbeholder.data.model.CarrySlot
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.Damage
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
import pl.pelotasplus.eyeofbeholder.data.model.TrackIndex
import pl.pelotasplus.eyeofbeholder.data.model.WhatABlowLeaves
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
import pl.pelotasplus.eyeofbeholder.data.model.anHourSlept
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WhatASlowPoisonHoldsBackTest {

    private val one = PartySlot(0)
    private val other = PartySlot(1)

    private fun champion() = Champion(
        name = "Anselm",
        portrait = PortraitId(0),
        abilities = Abilities(strength = Ability(10, 10), dexterity = Ability(10, 10)),
        hitPoints = HitPoints(100, 100),
        armorClass = ArmorClass(10),
        food = Food(100),
        characterClass = CharacterClass.CLERIC,
        levels = listOf(ClassLevel(9, XpPoints(0))),
        carrying = List(CarrySlot.ALL_OF_THEM) { ItemIndex(ItemIndex.NOTHING) },
        flags = ChampionFlags(1),
    )

    private fun bothPoisoned() = GameState(
        party = PartyState(Location(13, 9), Direction.NORTH),
        champions = listOf(champion(), champion()),
    ).championPoisoned(one).championPoisoned(other)

    private fun GameState.slowed() = assertNotNull(
        spellBegunOn(Spell.SLOW_POISON, whom = one, by = other, casterLevel = OFF_A_SCROLL),
    )

    @Test
    fun `it is laid on whoever is pointed at, and frames their box yellow`() {
        assertEquals(SettlesOn.WHOEVER_IS_POINTED_AT, Spell.SLOW_POISON.settlesOn)
        assertEquals(TrackIndex(111), Spell.SLOW_POISON.heardAs)
        assertTrue(Spell.SLOW_POISON.framesTheBoxYellow)
    }

    /** One unit and another a level, the unit being 32760 ticks. */
    @Test
    fun `it lasts hours rather than minutes`() {
        val lasts = assertIs<SpellLasts>(Spell.SLOW_POISON.lasts)

        assertEquals(Ticks(65520), lasts.castBySomeoneOfLevel(1))
        assertEquals(Ticks(327600), lasts.castBySomeoneOfLevel(OFF_A_SCROLL))
    }

    @Test
    fun `the venom passes over the one it is on and bites the other`() {
        val slowed = bothPoisoned().slowed()

        assertEquals(listOf(one, other), slowed.poisoned, "it is not a cure")
        assertEquals(listOf(other), slowed.bittenByVenom)
    }

    @Test
    fun `the bites start again when it ends`() {
        val slowed = bothPoisoned().slowed()

        val ended = slowed.copy(running = slowed.running.runDown(Ticks(327600)).first)

        assertEquals(listOf(one, other), ended.bittenByVenom)
    }

    /** Sleep is the one thing it does not hold back. */
    @Test
    fun `a night's sleep still costs them`() {
        val slept = bothPoisoned().slowed().anHourSlept()

        assertEquals(90, slept.championIn(one)!!.hitPoints.current)
    }

    @Test
    fun `curing the poison ends it`() {
        val cured = bothPoisoned().slowed().championPoisoned(one, yes = false)

        assertFalse(cured.running.isOn(Spell.SLOW_POISON, one))
    }

    @Test
    fun `a neutralize poison ends it`() {
        val lifted = bothPoisoned().slowed()
            .withTheSpellLaidOn(one, assertNotNull(Spell.NEUTRALIZE_POISON.laidOn), byWhom = champion())

        assertFalse(lifted.championIn(one)!!.poisoned)
        assertFalse(lifted.running.isOn(Spell.SLOW_POISON, one))
    }

    @Test
    fun `drinking poison ends it`() {
        val drunk = bothPoisoned().slowed().poisonDrunk(one)

        assertTrue(drunk.championIn(one)!!.poisoned)
        assertEquals(listOf(one, other), drunk.bittenByVenom)
    }

    /** Raised and bitten again, they are not protected by a spell from before. */
    @Test
    fun `going past raising ends it, and a blow short of that does not`() {
        val slowed = bothPoisoned().slowed()

        assertTrue(slowed.championHurt(one, Damage(109)).running.isOn(Spell.SLOW_POISON, one))
        assertFalse(slowed.championHurt(one, Damage(110)).running.isOn(Spell.SLOW_POISON, one))
    }

    @Test
    fun `turning to stone ends it`() {
        val makesNoThrow = Dice { _, _, _ -> 1 }

        val stone = assertNotNull(
            bothPoisoned().slowed()
                .championLeftWith(one, WhatABlowLeaves.PETRIFICATION, makesNoThrow),
        )

        assertFalse(stone.running.isOn(Spell.SLOW_POISON, one))
    }

    /** Every spell laid on them goes, and only theirs. */
    @Test
    fun `it goes for anything laid on them, and not for others or the party`() {
        val world = bothPoisoned().slowed()
            .let { assertNotNull(it.spellBegunWhereItSettles(Spell.BLUR, one, OFF_A_SCROLL)) }
            .let { assertNotNull(it.spellBegunWhereItSettles(Spell.BLUR, other, OFF_A_SCROLL)) }
            .let { assertNotNull(it.spellBegunWhereItSettles(Spell.BLESS, one, OFF_A_SCROLL)) }

        val after = world.championHurt(one, Damage(110)).running

        assertFalse(after.isOn(Spell.BLUR, one))
        assertTrue(after.isOn(Spell.BLUR, other))
        assertTrue(after.isRunning(Spell.BLESS))
    }

    private companion object {
        /** A scroll is always read at ninth-level strength. */
        const val OFF_A_SCROLL = 9
    }
}
