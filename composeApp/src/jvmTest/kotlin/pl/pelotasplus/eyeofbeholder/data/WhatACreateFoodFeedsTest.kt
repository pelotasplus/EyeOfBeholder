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
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Create food, which conjures a meal rather than handing anybody rations.
 *
 * Nothing is spent out of a pack and nothing is asked about how hungry each
 * of them was — the whole party end full. The only question it really has is
 * who counts as part of the party, and the answer is the one every spell that
 * reaches all six gives.
 */
class WhatACreateFoodFeedsTest {

    private fun aChampion(food: Int = 20, hitPoints: Int = 20) = Champion(
        name = "Anselm",
        portrait = PortraitId(0),
        abilities = Abilities(strength = Ability(10, 10), dexterity = Ability(10, 10)),
        hitPoints = HitPoints(hitPoints, hitPoints),
        armorClass = ArmorClass(10),
        food = Food(food),
        characterClass = CharacterClass.FIGHTER,
        levels = listOf(ClassLevel(1, XpPoints(0))),
        carrying = List(27) { ItemIndex(ItemIndex.NOTHING) },
        flags = ChampionFlags(1),
    )

    private fun world(party: List<Champion>) = GameState(
        party = PartyState(Location(3, 11), Direction.NORTH),
        champions = party,
    )

    @Test
    fun `the spell is the one that feeds them all`() {
        assertTrue(Spell.CREATE_FOOD.feedsThemAll)
    }

    /** Every one of the six, however little each had left. */
    @Test
    fun `all of them end full`() {
        val hungry = world(List(6) { aChampion(food = it * 10) })

        val fed = hungry.partyFedToTheBrim()

        (0 until 6).forEach { slot ->
            assertEquals(
                100,
                assertNotNull(fed.championIn(PartySlot(slot))).food.value,
                "slot $slot was not filled",
            )
        }
    }

    /** Starving and nearly full come out the same, there being one brim. */
    @Test
    fun `it does not matter how hungry anybody was`() {
        val fed = world(listOf(aChampion(food = 0), aChampion(food = 99)) + List(4) { aChampion() })
            .partyFedToTheBrim()

        assertEquals(100, assertNotNull(fed.championIn(PartySlot(0))).food.value)
        assertEquals(100, assertNotNull(fed.championIn(PartySlot(1))).food.value)
    }

    /** It feeds and nothing else: nobody is mended by a meal. */
    @Test
    fun `it mends nobody`() {
        val hurt = world(List(6) { aChampion(food = 0, hitPoints = 3) })

        val fed = hurt.partyFedToTheBrim()

        assertEquals(3, assertNotNull(fed.championIn(PartySlot(0))).hitPoints.current)
    }

    /**
     * Whoever is past raising is passed over — there is nothing there to
     * feed, and a full stomach on a corpse would read as a champion who could
     * still be got up.
     */
    @Test
    fun `it passes over whoever is past raising`() {
        val party = List(6) {
            if (it == 2) aChampion(food = 0).copy(hitPoints = HitPoints(-11, 20))
            else aChampion(food = 0)
        }

        val fed = world(party).partyFedToTheBrim()

        assertEquals(0, assertNotNull(fed.championIn(PartySlot(2))).food.value)
        assertEquals(100, assertNotNull(fed.championIn(PartySlot(1))).food.value)
    }

    /**
     * An empty slot is left as it was.
     *
     * Asked of the list rather than through [GameState.championIn], which
     * answers nothing for a slot nobody is in — so the only way to see that
     * the slot was passed over rather than quietly filled is to look at what
     * is sitting there.
     */
    @Test
    fun `it passes over an empty slot`() {
        val party = List(5) { aChampion(food = 0) } + Champion.NOBODY

        val fed = world(party).partyFedToTheBrim()

        assertEquals(Champion.NOBODY, fed.champions[5])
        assertEquals(100, assertNotNull(fed.championIn(PartySlot(0))).food.value)
    }
}
