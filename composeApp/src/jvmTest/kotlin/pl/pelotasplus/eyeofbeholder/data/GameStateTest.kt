package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
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

    private fun monster(slot: Int) = MonsterInstance(
        index = slot,
        unit = 0,
        block = 0,
        pos = 4,
        direction = Direction.SOUTH,
        type = MonsterTypeId(0),
        gfxIndex = 0,
        mode = 0,
        pause = 0,
        weapon = 0,
        pocketItem = 0,
    )
}
