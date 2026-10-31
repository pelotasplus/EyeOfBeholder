package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Abilities
import pl.pelotasplus.eyeofbeholder.data.model.Ability
import pl.pelotasplus.eyeofbeholder.data.model.ArmorClass
import pl.pelotasplus.eyeofbeholder.data.model.CarrySlot
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
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WhatARemoveParalysisFreesTest {

    private fun champion(held: Boolean) = Champion(
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
    ).paralysed(held)

    private fun partyWith(vararg held: Boolean) = GameState(
        party = PartyState(Location(13, 9), Direction.NORTH),
        champions = held.map(::champion),
    ).gripsFoundForTheHeld()

    private fun GameState.whoIsHeld() = champions.map { it.paralysed }

    @Test
    fun `it is the spell that does it`() {
        assertEquals(listOf(Spell.REMOVE_PARALYSIS), Spell.entries.filter { it.freesTheHeld })
    }

    @Test
    fun `whoever is held is let go and their grip goes with them`() {
        val freed = partyWith(false, true, false, true).heldFreed()

        assertEquals(listOf(false, false, false, false), freed.whoIsHeld())
        assertEquals(emptyList(), freed.holding)
    }

    @Test
    fun `only four are let go, the front of the party first`() {
        val freed = partyWith(true, true, true, true, true, true).heldFreed()

        assertEquals(listOf(false, false, false, false, true, true), freed.whoIsHeld())
        assertEquals(listOf(PartySlot(4), PartySlot(5)), freed.holding.map { it.whose })
    }

    @Test
    fun `those not held do not use up the four`() {
        val freed = partyWith(false, false, true, true, true, true).heldFreed()

        assertTrue(freed.whoIsHeld().none { it })
    }

    @Test
    fun `with nobody held nothing changes`() {
        val party = partyWith(false, false)

        assertEquals(party, party.heldFreed())
        assertFalse(party.heldFreed().whoIsHeld().any { it })
    }
}
