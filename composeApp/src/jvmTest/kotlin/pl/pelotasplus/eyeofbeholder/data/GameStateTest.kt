package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.Maz
import pl.pelotasplus.eyeofbeholder.data.model.WallByte
import pl.pelotasplus.eyeofbeholder.data.model.WallSide
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A level outlives the party leaving it. Its flags do already; so must what
 * is standing on it, or a monster a script conjured is gone the moment the
 * party steps off the level and the encounter can be walked away from.
 */
class GameStateTest {

    private val world = GameState(PartyState(Location(1, 1), Direction.NORTH))

    @Test
    fun `a level never visited is peopled by its own file`() {
        val placed = listOf(monster(slot = 3), monster(slot = 4))

        assertEquals(placed, world.arrivingAt(6, placed).monsters)
    }

    @Test
    fun `a level already visited is as the party left it`() {
        val placed = listOf(monster(slot = 3))
        val conjured = monster(slot = 0)

        val left = world
            .arrivingAt(6, placed)
            .copy(monsters = placed + conjured)
            .leaving(6)

        val returned = left.arrivingAt(5, emptyList()).leaving(5).arrivingAt(6, placed)

        assertEquals(placed + conjured, returned.monsters, "the conjured one is still there")
    }

    @Test
    fun `each level is remembered on its own`() {
        val onSix = monster(slot = 0)
        val onFive = monster(slot = 1)

        val world = world
            .arrivingAt(6, listOf(onSix)).leaving(6)
            .arrivingAt(5, listOf(onFive)).leaving(5)

        assertEquals(listOf(onSix), world.arrivingAt(6, emptyList()).monsters)
        assertEquals(listOf(onFive), world.arrivingAt(5, emptyList()).monsters)
    }

    // --- walls a script has changed ------------------------------------------

    @Test
    fun `an unchanged wall is whatever the level file says`() {
        val world = world.arrivingAt(5, emptyList(), mazOf(9, 8, side = 44))

        assertEquals(Maz.WallType.Decoration(44), world.wall(5, Location(9, 8), WallSide.NORTH))
    }

    @Test
    fun `a changed wall answers as the script left it`() {
        val world = world
            .arrivingAt(5, emptyList(), mazOf(9, 8, side = 1))
            .wallsChanged(5, Location(9, 8), to = WallByte(44))

        WallSide.entries.forEach { side ->
            assertEquals(
                Maz.WallType.Decoration(44),
                world.wall(5, Location(9, 8), side),
                "every side of it changed",
            )
        }
        assertEquals(WallByte(44), world.wallByte(5, Location(9, 8), WallSide.NORTH))
    }

    @Test
    fun `changing one side leaves the others alone`() {
        val world = world
            .arrivingAt(5, emptyList(), mazOf(9, 8, side = 1))
            .wallChanged(5, Location(9, 8), WallSide.NORTH, to = WallByte(44))

        assertEquals(Maz.WallType.Decoration(44), world.wall(5, Location(9, 8), WallSide.NORTH))
        assertEquals(Maz.WallType.FixedWall(0), world.wall(5, Location(9, 8), WallSide.SOUTH))
    }

    @Test
    fun `the last change to a wall is the one that counts`() {
        val world = world
            .arrivingAt(5, emptyList(), mazOf(9, 8, side = 1))
            .wallsChanged(5, Location(9, 8), to = WallByte(44))
            .wallsChanged(5, Location(9, 8), to = WallByte(0))

        assertEquals(Maz.WallType.NoWall, world.wall(5, Location(9, 8), WallSide.NORTH))
    }

    @Test
    fun `a wall changed on one level says nothing about another`() {
        val world = world
            .arrivingAt(5, emptyList(), mazOf(9, 8, side = 1))
            .wallsChanged(5, Location(9, 8), to = WallByte(44))

        assertEquals(Maz.WallType.NoWall, world.wall(6, Location(9, 8), WallSide.NORTH))
    }

    /** A maze whose every side of every square is [side]. */
    private fun mazOf(x: Int, y: Int, side: Int) = Maz(
        name = "TEST.MAZ",
        width = x + 2,
        height = y + 2,
        squares = (0 until (y + 2)).flatMap { row ->
            (0 until (x + 2)).map { column ->
                Maz.Square(
                    x = column,
                    y = row,
                    north = Maz.WallType.fromInt(side),
                    south = Maz.WallType.fromInt(side),
                    west = Maz.WallType.fromInt(side),
                    east = Maz.WallType.fromInt(side),
                )
            }
        },
    )

    private fun monster(slot: Int) = MonsterInstance(
        index = slot,
        unit = 0,
        block = 0,
        place = SquarePlace.MIDDLE,
        direction = Direction.SOUTH,
        type = MonsterTypeId(0),
        gfxIndex = 0,
        mode = 0,
        pause = 0,
        weapon = 0,
        pocketItem = 0,
    )
}
