package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.CarrySlot
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemIconId
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemNameId
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypeId
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Somebody put out of the party to make room for somebody else.
 *
 * Everything they carried lands on the square underfoot rather than going
 * with them, so a party who drop somebody for a stranger can pick their things
 * back up. Two parts of that are easy to get wrong and neither shows on a
 * screen: a quiver is a ring of arrows and not one thing, and the corners it
 * all scatters between are read off which way the party face.
 */
class DroppingAChampionTest {

    // --- the quiver ----------------------------------------------------------

    /**
     * A quiver holds its arrows strung to one another, and the slot names only
     * the first. Letting go of that one alone leaves the rest of the ring on
     * no floor and in nobody's hands — gone from the game without being
     * destroyed, which is worse than either.
     */
    @Test
    fun `every arrow in the quiver reaches the floor`() {
        val world = partyOfOne(quiver = threeArrows()).championDropped(
            whose = THE_ONE,
            level = LEVEL,
            at = HERE,
            facing = Direction.NORTH,
            dice = rolling(0),
        )

        ARROWS.forEach { arrow ->
            val landed = world.item(arrow)
            assertEquals(HERE, landed?.location, "arrow $arrow did not land underfoot")
            assertEquals(LEVEL, landed?.level, "arrow $arrow landed on the wrong floor")
        }
    }

    @Test
    fun `and the quiver slot is left empty`() {
        val world = partyOfOne(quiver = threeArrows()).championDropped(
            whose = THE_ONE,
            level = LEVEL,
            at = HERE,
            facing = Direction.NORTH,
            dice = rolling(0),
        )

        assertTrue(world.champions[0].carrying.none { it.isSomething })
    }

    /** Draining it must not leave an arrow in the party's hand on the way out. */
    @Test
    fun `nothing is left in the hand`() {
        val world = partyOfOne(quiver = threeArrows()).championDropped(
            whose = THE_ONE,
            level = LEVEL,
            at = HERE,
            facing = Direction.NORTH,
            dice = rolling(0),
        )

        assertEquals(ItemIndex.NOTHING, world.inHand.value)
    }

    // --- which corner --------------------------------------------------------

    /**
     * The two corners a dropped thing may land in, per facing. These are
     * transcribed: they are the first two of each row of the table the game
     * rolls a die against, and a thrown thing starts from the same pair.
     */
    @Test
    fun `it scatters between the two corners ahead of the party`() {
        val expected = mapOf(
            Direction.NORTH to listOf(SquarePlace.NORTH_WEST, SquarePlace.NORTH_EAST),
            Direction.EAST to listOf(SquarePlace.NORTH_EAST, SquarePlace.SOUTH_EAST),
            Direction.SOUTH to listOf(SquarePlace.SOUTH_EAST, SquarePlace.SOUTH_WEST),
            Direction.WEST to listOf(SquarePlace.SOUTH_WEST, SquarePlace.NORTH_WEST),
        )

        expected.forEach { (facing, corners) ->
            corners.forEachIndexed { roll, corner ->
                val world = partyOfOne(quiver = oneArrow()).championDropped(
                    whose = THE_ONE,
                    level = LEVEL,
                    at = HERE,
                    facing = facing,
                    // the game rolls one die of two; fixing it picks a corner
                    dice = rolling(roll),
                )

                assertEquals(
                    corner,
                    world.item(ARROWS.first())?.place,
                    "facing $facing, roll ${roll + 1}",
                )
            }
        }
    }

    // --- what a drop leaves behind -------------------------------------------

    @Test
    fun `the one dropped is nobody afterwards`() {
        val world = partyOfOne(quiver = oneArrow()).championDropped(
            whose = THE_ONE,
            level = LEVEL,
            at = HERE,
            facing = Direction.NORTH,
            dice = rolling(0),
        )

        assertTrue(!world.champions[0].inTheParty)
    }

    @Test
    fun `dropping nobody changes nothing`() {
        val before = partyOfOne(quiver = oneArrow())

        assertEquals(
            before,
            before.championDropped(
                whose = PartySlot(5),
                level = LEVEL,
                at = HERE,
                facing = Direction.NORTH,
                dice = rolling(0),
            ),
        )
    }

    // --- where the gap ends up -----------------------------------------------

    /**
     * The party close up behind whoever is left rather than fighting around a
     * hole where somebody used to stand.
     */
    @Test
    fun `the gap goes to the back of a full party`() {
        val after = sixOf().dropping(PartySlot(0))

        assertEquals(
            listOf("Six", "Two", "Three", "Four", "Five", null),
            after.champions.map { it.name.takeIf { _ -> it.inTheParty } },
            "the gap was left where the dropped champion stood",
        )
    }

    /** With the last slot already empty the gap goes to the one before it. */
    @Test
    fun `and to the second from the back where the back is empty`() {
        val five = sixOf().let { it.copy(champions = it.champions.dropLast(1) + Champion.NOBODY) }

        val after = five.dropping(PartySlot(1))

        assertEquals(
            listOf("One", "Five", "Three", "Four", null, null),
            after.champions.map { it.name.takeIf { _ -> it.inTheParty } },
            "the gap was not put behind the living",
        )
    }

    @Test
    fun `dropping the one at the back moves nobody`() {
        val after = sixOf().dropping(PartySlot(5))

        assertEquals(
            listOf("One", "Two", "Three", "Four", "Five", null),
            after.champions.map { it.name.takeIf { _ -> it.inTheParty } },
            "the party were shuffled to no purpose",
        )
    }

    private fun GameState.dropping(whose: PartySlot) = championDropped(
        whose = whose,
        level = LEVEL,
        at = HERE,
        facing = Direction.NORTH,
        dice = rolling(0),
    )

    /** Six named champions carrying nothing, so only the order is in play. */
    private fun sixOf() = GameState(
        party = PartyState(HERE, Direction.NORTH),
        champions = listOf("One", "Two", "Three", "Four", "Five", "Six").map {
            Champion.NOBODY.copy(
                name = it,
                flags = ChampionFlags(1),
                carrying = CarrySlot.NOTHING_IN_ANY,
            )
        },
    )

    // --- the world these run in ----------------------------------------------

    /** Three arrows strung into a ring, the way a stack is kept. */
    private fun threeArrows() = ARROWS.mapIndexed { which, _ ->
        anArrow(
            next = ARROWS[(which + 1) % ARROWS.size].value,
            prev = ARROWS[(which + ARROWS.size - 1) % ARROWS.size].value,
        )
    }

    /** One on its own, which is a ring pointing at itself. */
    private fun oneArrow() =
        listOf(anArrow(next = ARROWS.first().value, prev = ARROWS.first().value))

    private fun anArrow(next: Int, prev: Int) = Item(
        nameUnidentified = ItemNameId(0),
        nameIdentified = ItemNameId(0),
        flags = 0,
        icon = ItemIconId(1),
        type = ItemTypeId(0),
        place = SquarePlace.NORTH_WEST,
        location = Item.ON_A_STACK,
        next = next,
        prev = prev,
        level = Item.NO_LEVEL,
        value = 0,
    )

    /** The one die of two the game rolls for a corner, landing on [pips]. */
    private fun rolling(pips: Int) = Dice { _, _, _ -> pips }

    private fun partyOfOne(quiver: List<Item>): GameState {
        val nothing = anArrow(next = 0, prev = 0)
            .copy(location = Item.NOWHERE, level = 0)

        val holder = Champion.NOBODY.copy(
            name = "Holder",
            flags = ChampionFlags(1),
            carrying = CarrySlot.NOTHING_IN_ANY.toMutableList().also {
                it[CarrySlot.QUIVER.index] = ARROWS.first()
            },
        )

        return GameState(
            party = PartyState(HERE, Direction.NORTH),
            champions = listOf(holder),
            items = List(ARROWS.first().value) { nothing } + quiver,
        )
    }

    private companion object {
        const val LEVEL = 7
        val HERE = Location(4, 4)
        val THE_ONE = PartySlot(0)

        /** Where in the item table the arrows sit, so a ring can be written. */
        val ARROWS = listOf(ItemIndex(1), ItemIndex(2), ItemIndex(3))
    }
}
