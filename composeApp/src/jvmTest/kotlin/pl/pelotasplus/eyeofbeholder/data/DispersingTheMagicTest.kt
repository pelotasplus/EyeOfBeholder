package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.WallSide
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.MazRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VcnRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VmpRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The eleventh floor's carvings, which open a way by being dispelled.
 *
 * Two of them, at 11x25 and 15x25, and each answers a spell cast on the square
 * it stands on rather than being pointed at. What they open is six squares in
 * a fixed order — down 13x25, 13x24, 13x23 and then west along 12x23, 11x23,
 * 10x23 — one for each casting, so the way is walked open rather than thrown
 * open.
 *
 * The party must face the carving: west at the western one, and the eastern
 * one wants them facing the other way.
 */
@Category(NeedsGameData::class)
class DispersingTheMagicTest {

    private val resources = ResourceRepositoryImpl()

    private val level: Inf = runBlocking {
        InfRepositoryImpl(
            resourceRepository = resources,
            mazRepository = MazRepositoryImpl(resources),
            vmpRepository = VmpRepositoryImpl(resources),
            vcnRepository = VcnRepositoryImpl(resources),
            palRepository = PalRepositoryImpl(resources),
            cpsRepository = CpsRepositoryImpl(resources),
            decRepository = DecRepositoryImpl(resources),
        ).loadInf("LEVEL11.INF").getOrThrow()
    }

    private val sub get() = level.subLevels[0]

    private fun world(facing: Direction = Direction.WEST) = GameState(
        party = PartyState(THE_WESTERN_CARVING, facing),
        mazes = mapOf(LEVEL to sub.maz),
    )

    /** One casting on the square the party stand on. */
    private fun GameState.dispelled(spell: Spell = Spell.DISPEL_MAGIC): GameState = runBlocking {
        LevelScriptRunner(level.script, level = LEVEL).onEvent(
            triggers = level.triggers,
            event = ScriptEvent.A_SPELL_WAS_CAST,
            state = this@dispelled,
            at = party.position,
            cast = spell,
        ).state
    }

    private fun GameState.wallAt(where: Location) =
        wallByte(LEVEL, where, WallSide.NORTH).value

    @Test
    fun `dispelling at the western carving opens the first square`() {
        val before = world()
        assertNotEquals(0, before.wallAt(FIRST_TO_GO), "the way is open before anything was cast")

        val after = before.dispelled()

        assertEquals(0, after.wallAt(FIRST_TO_GO), "the first square did not open")
    }

    /**
     * One square a casting, in the order the floor names them.
     *
     * Not all six start closed, so the ones that do are read off the floor
     * rather than assumed — writing the list out by hand is how a test comes
     * to agree with itself instead of with the game.
     */
    @Test
    fun `each casting opens the next one along`() {
        var world = world()
        val closed = THE_WAY_THROUGH.filter { world.wallAt(it) != 0 }

        assertTrue(closed.size > 1, "there is nothing to open in order")

        closed.forEachIndexed { cast, square ->
            world = world.dispelled()

            assertEquals(0, world.wallAt(square), "casting ${cast + 1} did not open $square")
            closed.drop(cast + 1).forEach {
                assertNotEquals(0, world.wallAt(it), "casting ${cast + 1} opened $it as well")
            }
        }
    }

    /** And once they are all open, another casting is simply spent. */
    @Test
    fun `and a casting with nothing left to open changes nothing`() {
        var world = world()
        repeat(THE_WAY_THROUGH.size) { world = world.dispelled() }

        val spent = world.dispelled()

        assertTrue(THE_WAY_THROUGH.all { spent.wallAt(it) == 0 })
    }

    // --- and what it refuses --------------------------------------------------

    /** A carving is dispelled by being faced. Cast at its back and nothing goes. */
    @Test
    fun `facing the wrong way opens nothing`() {
        val after = world(facing = Direction.EAST).dispelled()

        assertNotEquals(0, after.wallAt(FIRST_TO_GO), "it opened without being faced")
    }

    /**
     * And it wants that spell. Every other spell in the game is cast on the
     * same square by the same act, and the carving must ignore all of them.
     */
    @Test
    fun `and so does any other spell`() {
        listOf(
            Spell.ARMOUR,
            Spell.MAGIC_MISSILE,
            Spell.FIREBALL,
            Spell.LIGHTNING_BOLT,
            Spell.CREATE_FOOD,
            Spell.MAGICAL_VESTMENT,
            Spell.RAISE_DEAD,
        ).forEach { other ->
            val after = world().dispelled(other)

            assertNotEquals(0, after.wallAt(FIRST_TO_GO), "${other.calledIt} opened it")
        }
    }

    /** Either kind of caster opens it: the two spells are one puzzle. */
    @Test
    fun `and a cleric's dispel magic opens it too`() {
        val after = world().dispelled(Spell.A_CLERICS_DISPEL_MAGIC)

        assertEquals(0, after.wallAt(FIRST_TO_GO), "a cleric could not open it")
    }

    private companion object {
        const val LEVEL = 11

        val THE_WESTERN_CARVING = Location(11, 25)

        val THE_WAY_THROUGH = listOf(
            Location(13, 25),
            Location(13, 24),
            Location(13, 23),
            Location(12, 23),
            Location(11, 23),
            Location(10, 23),
        )

        val FIRST_TO_GO = THE_WAY_THROUGH.first()
    }
}
