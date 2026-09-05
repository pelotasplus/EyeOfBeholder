package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterImmunities
import pl.pelotasplus.eyeofbeholder.data.model.MonsterProperty
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSize
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.script.CreateMonster
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How many monsters a script may put on one square, which is not the seven
 * the square's flag byte can count.
 *
 * The engine asks two more questions before it places one: whether the square
 * is already occupied by something that fills it or stands in its middle, and
 * whether the newcomer is one of those itself. Only the smallest kind share a
 * square, and only by keeping to its corners.
 *
 * Which of the two a creature is decides how crowded a floor can get. The
 * small ones really do stand four to a square, one to a corner, and a script
 * may put three of them down at once; anything larger takes a square whole
 * and a second is refused for as long as the first is standing.
 *
 * Seven is not a limit anything reaches by itself. It is what three bits of
 * the square's flag byte can count, and the size rule refuses long before it
 * — but a script naming the same corner twice can climb towards it, which is
 * the one way a square ends up holding more than four.
 */
class RoomOnASquareTest {

    private val nest = Location(5, 24)

    private val kinds = listOf(
        kind(0, MonsterSize.FOUR_TO_A_SQUARE),
        kind(1, MonsterSize.TWO_TO_A_SQUARE),
        kind(2, MonsterSize.FILLS_THE_SQUARE),
    )

    private fun world() = GameState(
        party = PartyState(position = Location(1, 1), facing = Direction.NORTH),
    )

    private fun spawn(type: Int, place: SquarePlace, at: Location = nest) = CreateMonster(
        unit = 0,
        location = at,
        place = place,
        direction = Direction.EAST,
        type = MonsterTypeId(type),
        gfxIndex = 0,
        mode = 0,
        pause = 0,
        weapon = 0,
        pocketItem = 0,
    )

    private fun GameState.put(type: Int, place: SquarePlace, at: Location = nest) =
        monsterCreated(spawn(type, place, at), kinds = kinds)

    @Test
    fun `a square that fills up with one takes no second`() {
        val once = world().put(2, SquarePlace.NORTH_WEST)
        assertEquals(1, once.monstersOn(nest), "the first one was refused")

        val again = once
            .put(2, SquarePlace.SOUTH_WEST)
            .put(2, SquarePlace.NORTH_EAST)

        assertEquals(
            1,
            again.monstersOn(nest),
            "something that fills its square let two more stand on it",
        )
    }

    /**
     * The clock over a nest runs its script again and again, and every run
     * after the first has to come to nothing while the wasp it placed lives.
     */
    @Test
    fun `the same spawn refuses for as long as the first one lives`() {
        var world = world()
        repeat(5) {
            world = world
                .put(2, SquarePlace.NORTH_WEST)
                .put(2, SquarePlace.SOUTH_WEST)
                .put(2, SquarePlace.NORTH_EAST)
        }

        assertEquals(1, world.monstersOn(nest), "five turns of the clock filled the square")
    }

    /**
     * The one that matters in play: the nest asks for the same three corners
     * every time its clock comes round, and must be refused after the first.
     *
     * Allowing it stacks wasps at identical screen coordinates, so the square
     * looks no fuller while what it can do to the party doubles.
     */
    @Test
    fun `a corner already taken takes nothing more`() {
        var world = world()
        repeat(5) {
            world = world
                .put(0, SquarePlace.NORTH_WEST)
                .put(0, SquarePlace.SOUTH_WEST)
                .put(0, SquarePlace.NORTH_EAST)
        }

        assertEquals(
            3,
            world.monstersOn(nest),
            "five turns of the clock put more than one on a corner",
        )
    }

    @Test
    fun `and four is the most a square holds`() {
        var world = world()
        repeat(4) {
            SquarePlace.entries.filter { it.onTheFloor && it != SquarePlace.MIDDLE }
                .forEach { corner -> world = world.put(0, corner) }
        }

        assertEquals(4, world.monstersOn(nest), "a square held more than its four corners")
    }

    @Test
    fun `four of the smallest kind share a square`() {
        val world = world()
            .put(0, SquarePlace.NORTH_WEST)
            .put(0, SquarePlace.NORTH_EAST)
            .put(0, SquarePlace.SOUTH_WEST)
            .put(0, SquarePlace.SOUTH_EAST)

        assertEquals(4, world.monstersOn(nest), "the small kind stopped sharing")
    }

    /** Two of the middling kind, and no third. */
    @Test
    fun `the middling kind goes two to a square`() {
        val two = world()
            .put(1, SquarePlace.NORTH_WEST)
            .put(1, SquarePlace.SOUTH_EAST)

        assertEquals(2, two.monstersOn(nest), "a pair of wolves would not share a square")

        assertEquals(
            2,
            two.put(1, SquarePlace.NORTH_EAST).monstersOn(nest),
            "a third stood on a square that holds two",
        )
    }

    /** And a square emptied by killing takes as many again. */
    @Test
    fun `killing makes room`() {
        val full = world()
            .put(0, SquarePlace.NORTH_WEST)
            .put(0, SquarePlace.NORTH_EAST)
            .put(0, SquarePlace.SOUTH_WEST)
            .put(0, SquarePlace.SOUTH_EAST)

        val down = full.copy(monsters = full.monsters.take(2))

        assertEquals(
            4,
            down.put(0, SquarePlace.SOUTH_WEST).put(0, SquarePlace.SOUTH_EAST).monstersOn(nest),
            "two were killed and two could not take their places",
        )
    }

    @Test
    fun `one standing in the middle leaves no corner free`() {
        val world = world()
            .put(0, SquarePlace.MIDDLE)
            .put(0, SquarePlace.NORTH_WEST)

        assertEquals(1, world.monstersOn(nest), "a corner was taken beside one in the middle")
    }

    @Test
    fun `and nothing comes to stand in the middle of an occupied square`() {
        val world = world()
            .put(0, SquarePlace.NORTH_WEST)
            .put(0, SquarePlace.MIDDLE)

        assertEquals(1, world.monstersOn(nest), "one arrived in the middle of a taken square")
    }

    private fun kind(id: Int, size: MonsterSize) = MonsterProperty(
        id = id,
        armorClass = 0, hitChance = 0, level = 1,
        hpDcTimes = 1, hpDcPips = 1, hpDcBase = 0,
        attacksPerRound = 1, dmgDc = emptyList(),
        immunities = MonsterImmunities(0), capsFlags = 0, typeFlags = 0, experience = 0,
        size = size,
        sound1 = 0, sound2 = 0,
        numRemoteAttacks = 0, remoteWeaponChangeMode = null, numRemoteWeapons = null,
        remoteWeapons = emptyList(), tuResist = 0, dmgModifierEvade = 0,
        decorations = emptyList(),
    )
}
