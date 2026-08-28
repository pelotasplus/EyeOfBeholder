package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
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
import pl.pelotasplus.eyeofbeholder.data.model.Straying
import pl.pelotasplus.eyeofbeholder.data.model.SubLevel
import pl.pelotasplus.eyeofbeholder.data.model.canBeWalkedOnto
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
import kotlin.test.assertTrue

/**
 * A monster going about its own business, on the levels that place one.
 *
 * The placements are the game's own — level 3 puts two wall-followers in its
 * south-east corner and level 4 a strayer in each half — and so is the maze
 * they walk. What each ought to do comes from the rule and not from watching
 * it: straight on where the way is open, and a fixed turn where it is not.
 */
class MonsterWanderingTest {

    private val resources = ResourceRepositoryImpl()

    private fun inf(name: String): Inf = runBlocking {
        InfRepositoryImpl(
            resourceRepository = resources,
            mazRepository = MazRepositoryImpl(resources),
            vmpRepository = VmpRepositoryImpl(resources),
            vcnRepository = VcnRepositoryImpl(resources),
            palRepository = PalRepositoryImpl(resources),
            cpsRepository = CpsRepositoryImpl(resources),
            decRepository = DecRepositoryImpl(resources),
        ).loadInf(name).getOrThrow()
    }

    /** The party parked out of the way, so nothing notices them. */
    private fun world(inf: Inf, level: Int): GameState =
        GameState(party = PartyState(Location(0, 0), Direction.NORTH)).arrivingAt(
            level = level,
            places = inf.monsterInstances,
            maz = inf.subLevels[0].maz,
            kinds = inf.subLevels[0].monsters,
        )

    private fun walking(inf: Inf, level: Int) = MonsterPathing(
        stepping = MonsterStepping(level, inf.subLevels[0], inf.subLevels[0].monsters),
        kinds = inf.subLevels[0].monsters,
    )

    private fun GameState.at(slot: MonsterSlot) = monsters.first { it.index == slot }

    private fun SubLevel.openAhead(world: GameState, level: Int, of: MonsterInstance): Boolean {
        val ahead = of.direction.oneStepFrom(Location(of.x, of.y))
        return canBeWalkedOnto(world.wall(level, ahead, of.direction.wallSideFacingBack))
    }

    /**
     * A patrol is one rule and no route: straight on when it can, and a fixed
     * turn where it cannot. The maze says which of the two applies; the mode
     * says how far it turns.
     */
    @Test
    fun `a wall follower goes straight on or turns by its own amount`() {
        val inf = inf("LEVEL3.INF")
        val world = world(inf, 3)
        val sub = inf.subLevels[0]

        listOf(
            MonsterSlot(23) to MonsterMode.FOLLOWING_RIGHT,
            MonsterSlot(24) to MonsterMode.FOLLOWING_LEFT,
        )
            .forEach { (slot, expected) ->
                val before = world.at(slot)
                assertEquals(
                    expected,
                    before.whatItDoes,
                    "level 3's slot ${slot.value} is not a patrol",
                )

                val after = MonstersTurn(sub.monsters)
                    .begun(world, walking(inf, 3))
                    .at(slot)

                if (sub.openAhead(world, 3, before)) {
                    assertEquals(
                        before.direction.oneStepFrom(Location(before.x, before.y)),
                        Location(after.x, after.y),
                        "the way was open and it did not take it",
                    )
                    assertEquals(before.direction, after.direction, "it turned as well as stepped")
                } else {
                    assertEquals(
                        Location(before.x, before.y),
                        Location(after.x, after.y),
                        "the way was shut and it walked anyway",
                    )
                    assertEquals(
                        before.direction.turnedBy(expected.turnsBy),
                        after.direction,
                        "it turned by the wrong amount",
                    )
                }
            }
    }

    /**
     * Pacing is the same rule with the turn set to a half, so a corridor is
     * walked up and down for ever. No level places one, which is why the mode
     * is proved from the rule rather than from a placement.
     */
    @Test
    fun `pacing turns right round when the way shuts`() {
        assertEquals(2, MonsterMode.PACING.turnsBy)
        assertEquals(-1, MonsterMode.FOLLOWING_LEFT.turnsBy)
        assertEquals(1, MonsterMode.FOLLOWING_RIGHT.turnsBy)

        assertTrue(MonsterMode.PACING.wanders)
        assertTrue(MonsterMode.PACING.noticesTheParty)
    }

    /**
     * Something asleep is not scenery: the party coming near sets it hunting,
     * the same as a patrol drops its round. It is the commonest mode in the
     * game after hunting, so treating it as inert would leave most of what is
     * placed standing still for ever.
     */
    @Test
    fun `something asleep wakes when the party come near`() {
        val inf = inf("LEVEL1.INF")
        val asleep = inf.monsterInstances.filter { it.whatItDoes == MonsterMode.ASLEEP }

        assertTrue(asleep.isNotEmpty(), "level 1 places nothing asleep")
        assertTrue(asleep.none { it.whatItDoes.wanders }, "asleep is not standing still")
        assertTrue(asleep.all { it.whatItDoes.noticesTheParty }, "asleep never wakes")

        val one = asleep.first()
        val world = GameState(
            party = PartyState(Location(one.x, one.y + 1), Direction.NORTH),
        ).arrivingAt(
            level = 1,
            places = listOf(one),
            maz = inf.subLevels[0].maz,
            kinds = inf.subLevels[0].monsters,
        )

        val after = MonstersTurn(inf.subLevels[0].monsters)
            .begun(world, walking(inf, 1))
            .at(one.index)

        assertEquals(MonsterMode.HUNTING, after.whatItDoes)
        assertTrue(after.provoked)
    }

    /**
     * What is waiting to see, on the other hand, does not wake for anybody
     * walking past — level 5's clerics let a party right up to them.
     */
    @Test
    fun `something waiting to see does not wake for the party`() {
        assertTrue(MonsterMode.WAITING_TO_SEE.noticesTheParty.not())
        assertTrue(MonsterMode.HELD.noticesTheParty.not())
    }

    /**
     * Straying is wall-following plus the one thing that makes it different:
     * having stepped forward, an opening to the side is taken rather than
     * walked past. Without it the two are the same mode.
     */
    @Test
    fun `a strayer turns into an opening it has just walked past`() {
        val inf = inf("LEVEL4.INF")
        val world = world(inf, 4)
        val sub = inf.subLevels[0]

        val strayers = world.monsters.filter { it.whatItDoes.straysTowards != null }
        assertEquals(2, strayers.size, "level 4 does not place the pair of strayers")

        strayers.forEach { before ->
            val towards = before.whatItDoes.straysTowards!!

            // Having just moved, it is looking to that side this turn.
            val looking = world.copy(
                monsters = world.monsters.map {
                    if (it.index == before.index) {
                        it.copy(straying = Straying.WENT_FORWARD)
                    } else {
                        it
                    }
                },
            )

            val aside = before.direction.turnedBy(towards)
            val open = sub.canBeWalkedOnto(
                looking.wall(4, aside.oneStepFrom(Location(before.x, before.y)), aside.wallSideFacingBack),
            )

            val after = MonstersTurn(sub.monsters).begun(looking, walking(inf, 4)).at(before.index)

            if (open) {
                assertEquals(aside, after.direction, "it walked past an opening")
                assertEquals(
                    Location(before.x, before.y),
                    Location(after.x, after.y),
                    "it turned and stepped in one turn",
                )
            } else {
                assertEquals(before.direction, after.direction, "it turned towards a wall")
            }
        }
    }
}
