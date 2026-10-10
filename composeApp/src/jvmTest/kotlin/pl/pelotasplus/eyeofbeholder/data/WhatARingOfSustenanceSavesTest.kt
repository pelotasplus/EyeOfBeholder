package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
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
import pl.pelotasplus.eyeofbeholder.data.model.HOURS_A_MENDED_POINT
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemKind
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypes
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.PortraitId
import pl.pelotasplus.eyeofbeholder.data.model.Rest
import pl.pelotasplus.eyeofbeholder.data.model.Resting
import pl.pelotasplus.eyeofbeholder.data.model.Ring
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
import pl.pelotasplus.eyeofbeholder.data.model.sleptAnHour
import pl.pelotasplus.eyeofbeholder.data.repository.ItemTypesRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * A ring of sustenance, which answers hunger wherever hunger is charged: the
 * mouthful the clock takes as the party walk about, and the five a stretch of
 * sleep takes off each of them.
 *
 * It feeds nobody. What it does is stop the eating, so a stomach that is
 * already empty stays empty and whoever wears it starves alongside the rest.
 */
@Category(NeedsGameData::class)
class WhatARingOfSustenanceSavesTest {

    private val resources = ResourceRepositoryImpl()

    private val itemTypes: ItemTypes = runBlocking {
        ItemTypesRepositoryImpl(resources).loadItemTypes().getOrThrow()
    }

    private val dungeonItems: List<Item> = runBlocking {
        ItemsRepositoryImpl(resources).loadItems().getOrThrow().items
    }

    private fun aRingOf(effect: Ring) = ItemIndex(
        dungeonItems.withIndex().drop(1)
            .first { itemTypes.kindOf(it.value) == ItemKind.RING && itemTypes.ring(it.value) == effect }
            .index
    )

    private fun champion(
        food: Int = 100,
        hitPoints: Int = 40,
        wearing: ItemIndex = ItemIndex(ItemIndex.NOTHING),
        inThePack: ItemIndex = ItemIndex(ItemIndex.NOTHING),
    ) = Champion(
        name = "Anselm",
        portrait = PortraitId(0),
        abilities = Abilities(strength = Ability(10, 10), dexterity = Ability(10, 10)),
        hitPoints = HitPoints(hitPoints, 40),
        armorClass = ArmorClass(10),
        food = Food(food),
        characterClass = CharacterClass.FIGHTER,
        levels = listOf(ClassLevel(1, XpPoints(0))),
        carrying = List(CarrySlot.ALL_OF_THEM) {
            when (it) {
                CarrySlot.RINGS.first().index -> wearing
                IN_THE_PACK -> inThePack
                else -> ItemIndex(ItemIndex.NOTHING)
            }
        },
        flags = ChampionFlags(IN_THE_PARTY),
    )

    private fun world(vararg party: Champion) = GameState(
        party = PartyState(Location(13, 12), Direction.NORTH),
        champions = party.toList(),
        items = dungeonItems,
    )

    // --- walking about -------------------------------------------------------

    @Test
    fun `whoever wears one skips the mouthful the clock takes`() {
        val fed = world(champion(wearing = aRingOf(Ring.SUSTENANCE)), champion())

        val later = fed.hungrier(itemTypes)

        assertEquals(100, later.champions[0].food.value, "the ring let a mouthful through")
        assertEquals(99, later.champions[1].food.value)
    }

    /** Any other ring is only a ring. */
    @Test
    fun `another ring does not feed anybody`() {
        val fed = world(champion(wearing = aRingOf(Ring.ADORNMENT)))

        assertEquals(99, fed.hungrier(itemTypes).champions[0].food.value)
    }

    /** It has to be on a finger — one in the pack is luggage. */
    @Test
    fun `a ring in the pack feeds nobody`() {
        val fed = world(champion(inThePack = aRingOf(Ring.SUSTENANCE)))

        assertEquals(99, fed.hungrier(itemTypes).champions[0].food.value)
    }

    /**
     * Without the item types nothing can be told about what is worn, and the
     * ring cannot be read off an index alone. Hunger goes on being charged
     * rather than being quietly forgiven.
     */
    @Test
    fun `with nothing to read the ring by, everybody eats`() {
        val fed = world(champion(wearing = aRingOf(Ring.SUSTENANCE)))

        assertEquals(99, fed.hungrier().champions[0].food.value)
    }

    // --- sleeping ------------------------------------------------------------

    @Test
    fun `a stretch of sleep costs the wearer no food`() {
        val party = world(
            champion(hitPoints = 10, wearing = aRingOf(Ring.SUSTENANCE)),
            champion(hitPoints = 10),
        )

        val woken = party.sleptAnHour(HOURS_A_MENDED_POINT, itemTypes)

        assertEquals(100, woken.champions[0].food.value, "the ring paid for the sleep")
        assertEquals(98, woken.champions[1].food.value)
    }

    /** And the sleep still mends what it would have mended. */
    @Test
    fun `the wearer mends all the same`() {
        val party = world(champion(hitPoints = 10, wearing = aRingOf(Ring.SUSTENANCE)))

        val woken = party.sleptAnHour(HOURS_A_MENDED_POINT, itemTypes)

        assertEquals(40, woken.champions[0].hitPoints.current)
    }

    /**
     * The one thing it cannot do. Mending is paid for out of a stomach rather
     * than out of the ring, so somebody who wears it on an empty one neither
     * mends nor stops starving — a ring found after the food has run out is
     * worth nothing until the party find something to eat.
     */
    @Test
    fun `an empty stomach stays empty, ring or no ring`() {
        val starving = world(champion(food = 0, hitPoints = 10, wearing = aRingOf(Ring.SUSTENANCE)))

        val woken = starving.sleptAnHour(HOURS_A_MENDED_POINT, itemTypes)

        assertEquals(0, woken.champions[0].food.value)
        assertEquals(10, woken.champions[0].hitPoints.current, "an empty stomach mended something")
    }

    /**
     * A whole rest rather than one stretch of it. The party lie there until
     * nobody is hurt any more, which is several stretches for somebody badly
     * hurt, and the wearer pays for none of them.
     *
     * Worth asking separately: a rest is what the party actually do, and it
     * reaches the sleeping through a different route than a single stretch.
     */
    @Test
    fun `a rest long enough to mend somebody costs the wearer nothing`() {
        val party = world(
            champion(hitPoints = 1, wearing = aRingOf(Ring.SUSTENANCE)),
            champion(hitPoints = 1),
        )

        val slept = Resting(kinds = emptyList(), types = itemTypes).rest(party)

        val woken = assertIs<Rest.Slept>(slept).world
        assertEquals(40, woken.champions[0].hitPoints.current, "the rest did not run long enough")
        assertEquals(100, woken.champions[0].food.value, "the ring paid for the rest")
        assertEquals(96, woken.champions[1].food.value)
    }

    private companion object {
        const val IN_THE_PARTY = 1

        /** A slot that is neither hand nor finger. */
        const val IN_THE_PACK = 10
    }
}
