package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.HandUse
import pl.pelotasplus.eyeofbeholder.data.model.Horn
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemKind
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypes
import pl.pelotasplus.eyeofbeholder.data.repository.ItemTypesRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a hand does with what it holds, over the game's own table of item kinds.
 *
 * A hand does one thing. Deciding each use separately and then swinging anyway
 * is what drank a healing potion and hit the thing in front with the flask, and
 * what said a shield could not be swung and swung it.
 *
 * The kinds come from ITEMTYPE.DAT rather than from a table written here, so
 * this says what the game's own items do and not what somebody expected.
 */
class WhatAHandDoesTest {

    private val resources = ResourceRepositoryImpl()

    private val types: ItemTypes by lazy {
        runBlocking { ItemTypesRepositoryImpl(resources).loadItemTypes().getOrThrow() }
    }

    private val dungeonItems: List<Item> by lazy {
        runBlocking { ItemsRepositoryImpl(resources).loadItems().getOrThrow().items }
    }

    /** The first item in the file of each kind, or nothing if there is none. */
    private fun somethingThatIs(kind: ItemKind): Item? =
        dungeonItems.firstOrNull { types.kindOf(it) == kind }

    private fun doneWith(kind: ItemKind): HandUse? =
        somethingThatIs(kind)?.let { types.whatAHandDoesWith(it) }

    @Test
    fun `an empty hand is a fist and swings`() {
        assertEquals(HandUse.Swing, types.whatAHandDoesWith(null))
    }

    @Test
    fun `a weapon is swung`() {
        assertEquals(HandUse.Swing, doneWith(ItemKind.SWUNG_BY_HAND))
    }

    /**
     * The one that bit: a potion in hand was drunk and then swung, so drinking
     * hit whatever stood in front of the party with the flask.
     */
    @Test
    fun `a potion is drunk and not swung`() {
        assertEquals(HandUse.Drink, doneWith(ItemKind.POTION))
    }

    /** The same bug wearing a shield: it said so, and swung it anyway. */
    @Test
    fun `something worn is not swung`() {
        assertEquals(HandUse.WorksByBeingWorn, doneWith(ItemKind.ARMOUR))
        assertEquals(HandUse.WorksByBeingWorn, doneWith(ItemKind.RING))
    }

    @Test
    fun `a key is not used this way`() {
        assertEquals(HandUse.NotUsedThisWay, doneWith(ItemKind.KEY))
        assertEquals(HandUse.NotUsedThisWay, doneWith(ItemKind.GEM))
    }

    /**
     * Which of the four a horn is comes from its value, counting from one, and
     * the file holds one with no value yet for a script to make into one. So
     * the four are asked for by name rather than whichever turns up first.
     */
    @Test
    fun `a horn is blown`() {
        val horn = somethingThatIs(ItemKind.A_HORN)
        assertTrue(horn != null, "the file holds no horn")

        Horn.entries.forEachIndexed { index, which ->
            assertEquals(
                HandUse.Blow(which),
                types.whatAHandDoesWith(horn.copy(value = index + 1)),
            )
        }
    }

    @Test
    fun `a parchment is read`() {
        assertTrue(doneWith(ItemKind.SOMETHING_TO_READ) is HandUse.Read)
    }

    /**
     * Every kind in the game's table has an answer, so adding one means adding
     * it here rather than finding out later that it was quietly swung.
     */
    @Test
    fun `every kind in the file has an answer`() {
        val kinds = dungeonItems.mapNotNull { types.kindOf(it) }.distinct()

        assertTrue(kinds.size > 5, "the file gave up too few kinds to be testing anything")

        kinds.forEach { kind ->
            val doing = doneWith(kind)
            assertTrue(doing != null, "nothing in the file is $kind after all")
        }
    }

    /**
     * Nothing that is not a weapon is swung. This is the rule the bug broke,
     * asked of every kind at once rather than of the two that were noticed.
     */
    @Test
    fun `only a weapon is swung`() {
        val swung = dungeonItems
            .filter { types.whatAHandDoesWith(it) == HandUse.Swing }
            .mapNotNull { types.kindOf(it) }
            .distinct()

        assertEquals(
            emptyList(),
            swung - ItemKind.WIELDED,
            "these are swung and are not weapons",
        )
    }
}
