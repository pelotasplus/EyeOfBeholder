package pl.pelotasplus.eyeofbeholder.data

import kotlinx.serialization.json.Json
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
import pl.pelotasplus.eyeofbeholder.data.model.SavedWorld
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.AWallOfForce
import pl.pelotasplus.eyeofbeholder.data.model.Ticks
import pl.pelotasplus.eyeofbeholder.data.model.WallByte
import pl.pelotasplus.eyeofbeholder.data.model.WallSide
import pl.pelotasplus.eyeofbeholder.data.model.WallsOfForce
import pl.pelotasplus.eyeofbeholder.data.model.WhatABlowLeaves
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** A running spell belongs to a person and not to a place in the party. */
class WhatASpellFollowsItsChampionTest {

    private val one = PartySlot(0)
    private val other = PartySlot(1)

    private fun champion(name: String) = Champion(
        name = name,
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
        champions = listOf(champion("Anselm"), champion("Bree")),
    ).championPoisoned(one).championPoisoned(other)

    private fun GameState.slowed() = assertNotNull(
        spellBegunOn(Spell.SLOW_POISON, whom = one, by = other, casterLevel = 9),
    )

    @Test
    fun `changing places takes the spell along`() {
        val swapped = bothPoisoned().slowed().championsSwapped(one, other)

        assertEquals("Anselm", swapped.championIn(other)!!.name)
        assertTrue(swapped.running.isOn(Spell.SLOW_POISON, other))
        assertFalse(swapped.running.isOn(Spell.SLOW_POISON, one))
        assertEquals(listOf(one), swapped.bittenByVenom)
        assertEquals(one, swapped.running.all.single().castBy, "the caster moved too")
    }

    @Test
    fun `changing places takes a grip, a resting hand and a splat along`() {
        val noThrow = Dice { _, _, _ -> 1 }
        val world = assertNotNull(
            bothPoisoned().championLeftWith(one, WhatABlowLeaves.PARALYSIS, noThrow),
        ).championHurt(one, Damage(5)).handSwungForTest(one)

        val swapped = world.championsSwapped(one, other)

        assertEquals(listOf(other), swapped.holding.map { it.whose })
        assertEquals(listOf(other), swapped.showingDamage.map { it.whose })
        assertTrue(swapped.isRecovering(other, CarrySlot(0)))
        assertFalse(swapped.isRecovering(one, CarrySlot(0)))
    }

    private fun GameState.handSwungForTest(whose: PartySlot) =
        copy(recovering = listOf(pl.pelotasplus.eyeofbeholder.data.model.HandRecovering(whose, CarrySlot(0), 10, null)))

    /** Whoever fills the place afterwards starts with nothing on them. */
    @Test
    fun `being dropped takes the spell away, wherever the gap goes`() {
        val dropped = bothPoisoned().slowed().championDropped(
            whose = one,
            level = 1,
            at = Location(13, 9),
            facing = Direction.NORTH,
            dice = Dice { _, _, _ -> 1 },
        )

        assertEquals(emptyList(), dropped.running.all)
    }

    @Test
    fun `it comes back from a save with what is left of it`() {
        val slowed = bothPoisoned().slowed()
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        val back = json.decodeFromString<SavedWorld>(json.encodeToString(SavedWorld.serializer(), slowed.saved()))
        val loaded = GameState.restoredFrom(back, on = 1).copy(champions = slowed.champions)

        assertEquals(slowed.running, loaded.running)
        assertEquals(listOf(other), loaded.bittenByVenom)
    }

    /** A charged hand has the most to lose, being a spell and a hand and a picture. */
    @Test
    fun `a charged hand comes back from a save`() {
        val charged = assertNotNull(
            bothPoisoned().handCharged(one, CarrySlot(1), Spell.SHOCKING_GRASP, casterLevel = 9),
        )
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

        val back = json.decodeFromString<SavedWorld>(json.encodeToString(SavedWorld.serializer(), charged.saved()))

        assertEquals(charged.running, back.running)
    }

    @Test
    fun `a save from before spells were kept reads as none running`() {
        val slowed = bothPoisoned().slowed()
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }

        val written = json.encodeToString(SavedWorld.serializer(), slowed.saved().copy(running = GameState(party = slowed.party).running))

        assertEquals(emptyList(), json.decodeFromString<SavedWorld>(written).running.all)
        assertFalse(written.contains("\"running\""))
    }
}
