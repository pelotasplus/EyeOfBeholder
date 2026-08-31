package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.WhoTheMonsterReaches
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Which of the party a monster's arm comes down on.
 *
 * A monster in a corner of its square is on one side of the party or the
 * other, and reaches that side first every time. One standing in the middle
 * is on neither side, and which way it swings is decided afresh for every
 * blow.
 *
 * Getting that wrong is not subtle in play: a single large thing in a
 * doorway hits the same champion over and over until they fall, and then the
 * same next one, instead of spreading its blows across the front rank.
 */
class WhoAMonsterReachesTest {

    /** `rollDice(1, 2, -1)` comes out 0 on the low roll and 1 on the high. */
    private val swingsLeft = Dice { times, _, modifier -> times + modifier }
    private val swingsRight = Dice { times, pips, modifier -> times * pips + modifier }

    private fun reaches(place: SquarePlace, dice: Dice) = WhoTheMonsterReaches.inOrder(
        partyFacing = Direction.NORTH,
        monsterFacing = Direction.SOUTH,
        monsterPlace = place,
        dice = dice,
    )

    @Test
    fun `one in the middle swings either way`() {
        assertNotEquals(
            reaches(SquarePlace.MIDDLE, swingsLeft),
            reaches(SquarePlace.MIDDLE, swingsRight),
            "it reached the same champion whichever way the roll went",
        )
    }

    @Test
    fun `and reaches a different champion first each way`() {
        assertNotEquals(
            reaches(SquarePlace.MIDDLE, swingsLeft).first(),
            reaches(SquarePlace.MIDDLE, swingsRight).first(),
            "both rolls put the blow on the same champion",
        )
    }

    /** A monster in a corner is on that side and does not roll for it. */
    @Test
    fun `one in a corner reaches the same champion whatever it rolls`() {
        SquarePlace.entries.filter { it.onTheFloor }.forEach { corner ->
            assertEquals(
                reaches(corner, swingsLeft),
                reaches(corner, swingsRight),
                "a monster in $corner rolled for which side it was on",
            )
        }
    }

    /** Whichever way it swings, it can still reach all six of them. */
    @Test
    fun `every champion is reachable either way`() {
        listOf(swingsLeft, swingsRight).forEach { dice ->
            assertEquals(
                6,
                reaches(SquarePlace.MIDDLE, dice).distinct().size,
                "the order it reaches them in does not name all six",
            )
        }
    }
}
