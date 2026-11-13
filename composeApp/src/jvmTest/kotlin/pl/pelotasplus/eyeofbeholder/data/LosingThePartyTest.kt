package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Damage
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterMode
import pl.pelotasplus.eyeofbeholder.data.model.MonsterPathing
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterStepping
import pl.pelotasplus.eyeofbeholder.data.model.MonstersTurn
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A monster that has only seen the party makes for the square it saw them on,
 * and gives up there if they have gone; one the party have hit goes on looking.
 *
 * Level 5's temple: the open squares are 12x8 and 13x8, 12x9 through 15x9,
 * and 12x10 and 13x10. The cleric is left in the mode the level gives it,
 * waiting to see, so that noticing never re-aims it and the square it was
 * given is the only one it knows.
 */
@Category(NeedsGameData::class)
class LosingThePartyTest {

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
        ).loadInf("LEVEL5.INF").getOrThrow()
    }

    private val sub get() = level.subLevels[0]
    private val kinds get() = sub.monsters

    private val walking get() = MonsterPathing(
        stepping = MonsterStepping(level = 5, subLevel = sub, kinds = kinds),
        kinds = kinds,
    )

    private fun world(
        party: Location,
        cleric: (MonsterInstance) -> MonsterInstance,
    ): GameState {
        val placed = GameState(party = PartyState(party, Direction.NORTH)).arrivingAt(
            level = 5,
            places = level.monsterInstances,
            maz = sub.maz,
            kinds = kinds,
        )
        return placed.copy(monsters = placed.monsters.filter { it.index == CLERIC }.map(cleric))
    }

    private fun GameState.theCleric() = monsters.first { it.index == CLERIC }

    @Test
    fun `it walks to where it saw the party rather than to where they are`() {
        val world = world(party = Location(15, 9)) {
            it.copy(location = Location(14, 9), direction = Direction.WEST, provoked = true, heading = Location(12, 9))
        }

        val after = MonstersTurn(kinds).begun(world, walking).world.theCleric()

        assertEquals(Location(13, 9), after.location, "it turned on the party instead of going on")
    }

    @Test
    fun `finding nobody there it gives up and strays`() {
        val world = world(party = Location(15, 9)) {
            it.copy(location = Location(13, 9), direction = Direction.WEST, provoked = true, heading = Location(12, 9))
        }

        val after = MonstersTurn(kinds).begun(world, walking).world.theCleric()

        assertFalse(after.provoked, "it is still hunting")
        assertTrue(
            after.whatItDoes in setOf(MonsterMode.STRAYING_LEFT, MonsterMode.STRAYING_RIGHT),
            "it gave up into ${after.whatItDoes}",
        )
        assertNull(after.heading)
        assertEquals(Location(13, 9), after.location, "giving up is the whole of the turn")
    }

    /**
     * A hunter, not one pinned waiting to see: the party two squares behind
     * it are not noticed, so nothing re-aims it and it gives up facing the
     * square it was making for.
     */
    @Test
    fun `a hunter with the party behind it gives up all the same`() {
        val world = world(party = Location(15, 9)) {
            it.copy(location = Location(13, 9), direction = Direction.WEST).sawTheParty(Location(12, 9))
        }

        val after = MonstersTurn(kinds).begun(world, walking).world.theCleric()

        assertFalse(after.provoked, "it noticed the party behind its back")
        assertEquals(Location(13, 9), after.location)
    }

    @Test
    fun `a hunter that still sees the party is re-aimed at them`() {
        val world = world(party = Location(13, 10)) {
            it.copy(location = Location(13, 8), direction = Direction.SOUTH).sawTheParty(Location(12, 8))
        }

        val after = MonstersTurn(kinds).begun(world, walking).world.theCleric()

        assertEquals(Location(13, 10), after.heading, "it kept the square it saw them on first")
        assertEquals(Location(13, 9), after.location)
    }

    @Test
    fun `one the party have hit goes after them rather than a square`() {
        val world = world(party = Location(15, 9)) {
            it.copy(location = Location(13, 9), direction = Direction.WEST)
                .sawTheParty(Location(12, 9))
                .hurt(Damage(1), byTheParty = false)
                .copy(hitByTheParty = true)
        }

        val after = MonstersTurn(kinds).begun(world, walking).world.theCleric()

        assertTrue(after.provoked, "it gave up on the party that hit it")
    }

    @Test
    fun `a wound from anything else does not keep it hunting`() {
        val seen = world(party = Location(13, 10)) { it.sawTheParty(Location(13, 10)) }.theCleric()

        val trapped = seen.hurt(Damage(1), byTheParty = false)

        assertFalse(trapped.huntsForGood)
        assertEquals(Location(13, 10), trapped.heading)
    }

    @Test
    fun `a wound out of an older save counts as the party's`() {
        val loaded = world(party = Location(13, 10)) { it }.theCleric().let {
            it.copy(hitPoints = it.hitPoints.copy(current = it.hitPoints.max - 1), hitByTheParty = null)
        }

        assertTrue(loaded.huntsForGood)
    }

    @Test
    fun `a trap does not wipe out a wound from an older save`() {
        val loaded = world(party = Location(13, 10)) { it }.theCleric().let {
            it.copy(hitPoints = it.hitPoints.copy(current = it.hitPoints.max - 1), hitByTheParty = null)
        }

        assertTrue(loaded.hurt(Damage(1), byTheParty = false).huntsForGood)
    }

    @Test
    fun `the square it makes for is kept in a save`() {
        val seen = world(party = Location(13, 10)) { it.sawTheParty(Location(12, 9)) }.theCleric()

        val back = Json.decodeFromString<MonsterInstance>(Json.encodeToString(seen))

        assertEquals(Location(12, 9), back.heading)
    }

    @Test
    fun `a talking group struck is set hunting, and so can lose the party`() {
        val world = world(party = Location(13, 10)) { it }

        assertTrue(world.theCleric().standingBy, "the cleric is not placed waiting to see")

        val roused = world.rousedBy(CLERIC).theCleric()

        assertEquals(MonsterMode.HUNTING, roused.whatItDoes)
        assertTrue(roused.whatItDoes.noticesTheParty)
    }

    @Test
    fun `a hold running out forgets where it was making for`() {
        val held = world(party = Location(13, 10)) { it.sawTheParty(Location(12, 9)).heldFor(1) }.theCleric()

        val let = held.theHoldRunningDown()

        assertEquals(held.location, let.heading, "it is still making for the square from before the hold")
    }

    @Test
    fun `noticing the party sets the square it makes for`() {
        val world = world(party = Location(13, 10)) {
            it.copy(location = Location(13, 8), direction = Direction.SOUTH, mode = MonsterMode.ASLEEP.asWritten)
        }

        val after = MonstersTurn(kinds).begun(world, walking).world.theCleric()

        assertTrue(after.provoked, "it did not notice them")
        assertEquals(Location(13, 10), after.heading)
    }

    @Test
    fun `a blow keeps it after the party wherever they go`() {
        val seen = world(party = Location(13, 10)) { it.sawTheParty(Location(13, 10)) }.theCleric()

        val hit = seen.hurt(Damage(1))

        assertTrue(hit.huntsForGood)
        assertNull(hit.heading)
    }

    private companion object {
        val CLERIC = MonsterSlot(16)
    }
}
