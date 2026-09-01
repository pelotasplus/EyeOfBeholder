package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Flight
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.Projectile
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
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
import kotlin.test.assertTrue

/**
 * The pits on the seventh floor, which are opened and shut by throwing
 * something down the corridor at a button nobody can reach.
 *
 * The corridor runs south from 10x4. 10x5 and 10x6 are the pits; 10x7 is
 * solid on three sides and carries the button on the face turned back at the
 * party, so there is no standing in front of it and no clicking it. The only
 * square on the whole corridor that answers to something flying in is that
 * one, which is the puzzle stated in the level's own data.
 */
@Category(NeedsGameData::class)
class ThrowingAtAButtonTest {

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
        ).loadInf("LEVEL7.INF").getOrThrow()
    }

    private val here get() = level.subLevels[0]

    private fun world() = GameState(
        party = PartyState(THROWN_FROM, Direction.SOUTH),
    ).arrivingAt(level = LEVEL, places = emptyList(), maz = here.maz)

    /** Something let go of southward from where the party stand. */
    private fun GameState.thrown() = copy(
        inFlight = listOf(
            Projectile(
                what = ItemIndex(1),
                at = THROWN_FROM,
                place = SquarePlace.SOUTH_WEST,
                going = Direction.SOUTH,
                thrownBy = Projectile.Thrower.AChampion(PartySlot(0)),
            ),
        ),
    )

    /** Flies it until it stops, answering with everything that happened. */
    private fun GameState.untilItStops(): Flight.Moved {
        var world = this
        val flewOnto = mutableListOf<Location>()
        val struck = mutableListOf<Flight.StruckWall>()

        repeat(A_LONG_WAY) {
            val moved = Flight(sublevel = here, level = LEVEL).onward(world)
            world = moved.world
            flewOnto += moved.flewOnto
            struck += moved.struckWalls
        }

        return Flight.Moved(world, flewOnto, emptyList(), struck)
    }

    // --- the flight ----------------------------------------------------------

    @Test
    fun `a thing thrown south crosses the corridor and stops at the far wall`() {
        val flew = world().thrown().untilItStops()

        assertEquals(
            listOf(Location(10, 5), Location(10, 6)),
            flew.flewOnto,
            "it should cross both pits and stop against what is past them",
        )
    }

    /**
     * The face it could not pass, which is the one it hit — the north side of
     * 10x7, being the side turned back at the party.
     */
    @Test
    fun `and the wall it struck is the one carrying the button`() {
        val flew = world().thrown().untilItStops()

        assertEquals(listOf(Flight.StruckWall(THE_BUTTON, WallSide.NORTH)), flew.struckWalls)
    }

    @Test
    fun `it does not go on past the wall`() {
        assertTrue(world().thrown().untilItStops().world.inFlight.isEmpty())
    }

    // --- what the wall makes of it -------------------------------------------

    /**
     * That square answers to something flying in and to nothing else that can
     * reach it: the party cannot stand on it, so it is the throw or nothing.
     */
    @Test
    fun `the button answers a thing flying in`() {
        val trigger = level.triggers.single { it.location == THE_BUTTON }

        assertTrue(trigger.flags.reactsTo(ScriptEvent.SOMETHING_FLEW_IN))
        assertTrue(
            !trigger.flags.reactsTo(ScriptEvent.PARTY_ENTERED),
            "nobody can stand there, which is the whole of why it must be thrown at",
        )
    }

    /** And the squares it flew over do not, or the pits would work themselves. */
    @Test
    fun `the pits it crosses do not answer one`() {
        listOf(Location(10, 5), Location(10, 6)).forEach { pit ->
            level.triggers.firstOrNull { it.location == pit }?.let {
                assertTrue(
                    !it.flags.reactsTo(ScriptEvent.SOMETHING_FLEW_IN),
                    "$pit answers a thing flying over it",
                )
            }
        }
    }

    /**
     * The whole point: the script behind that wall clears 10x6 and 10x7 and
     * flips the pit at 10x5, which is what opens the way south.
     */
    @Test
    fun `working it opens the corridor`() {
        val before = world().thrown().untilItStops().world
        val after = runBlocking {
            LevelScriptRunner(script = level.script, level = LEVEL).onEvent(
                triggers = level.triggers,
                event = ScriptEvent.SOMETHING_FLEW_IN,
                state = before,
                at = THE_BUTTON,
            ).state
        }

        assertTrue(
            WallSide.entries.all { after.wallByte(LEVEL, THE_BUTTON, it).value == CLEARED },
            "the wall the button is on should have been taken away",
        )
        assertTrue(
            after.wallByte(LEVEL, Location(10, 5), WallSide.SOUTH) !=
                before.wallByte(LEVEL, Location(10, 5), WallSide.SOUTH),
            "the near pit should have been flipped",
        )
    }

    // --- walking into one ----------------------------------------------------

    /**
     * A thing in the air is asked what it is over on every turn of the clock,
     * not only when it crosses onto a new square — so somebody who steps into
     * the path of one that is already going steps into the thing.
     */
    /** Six of them, so which one is found is a real answer. */
    private fun sixOfThem() = List(6) { slot ->
        Champion.NOBODY.copy(name = "Name$slot", flags = ChampionFlags(1))
    }

    /** Something part way across the square the party are standing on. */
    private fun overThem(corner: SquarePlace) = world().copy(
        party = PartyState(Location(10, 5), Direction.SOUTH),
        champions = sixOfThem(),
        inFlight = listOf(
            Projectile(
                what = ItemIndex(1),
                at = Location(10, 5),
                place = corner,
                going = Direction.SOUTH,
                thrownBy = Projectile.Thrower.TheLevel,
                // far enough from its next step that this turn of the clock
                // moves it nowhere: it is asked where it stands, which is the
                // quarter these are about
                untilItSteps = Projectile.A_STEP,
                leaving = false,
            ),
        ),
    )

    private fun GameState.whoWasHit() = Flight(sublevel = here, level = LEVEL)
        .onward(this)
        .hurt
        .filterIsInstance<Flight.Hurt.AChampion>()
        .map { it.slot.index }

    @Test
    fun `somebody who walks into the path of one is hit by it`() {
        val moved = Flight(sublevel = here, level = LEVEL).onward(overThem(SquarePlace.SOUTH_EAST))

        assertTrue(
            moved.hurt.any { it is Flight.Hurt.AChampion },
            "standing under it cost nobody anything",
        )
        assertTrue(moved.world.inFlight.isEmpty(), "it should have stopped on them")
    }

    /**
     * Which of them takes it is where on the square it came through, and not
     * a toss: facing south, the far quarters are the south ones, and they
     * belong to the two standing at the front.
     */
    @Test
    fun `the quarter it comes through decides who takes it`() {
        assertEquals(
            listOf(0),
            overThem(SquarePlace.SOUTH_EAST).whoWasHit(),
            "ahead and to the left is the front left champion's",
        )
        assertEquals(
            listOf(1),
            overThem(SquarePlace.SOUTH_WEST).whoWasHit(),
            "and ahead and to the right is the front right champion's",
        )
    }

    /**
     * And where that champion is not standing, it carries on down the file
     * behind them rather than passing through the party untouched. This is
     * ours: the game names one champion for a front quarter and nobody after.
     */
    @Test
    fun `it carries on down the file when the one in front is not standing`() {
        val front = overThem(SquarePlace.SOUTH_EAST).let {
            it.copy(champions = it.champions.toMutableList().also { all -> all[0] = down() })
        }

        assertEquals(listOf(2), front.whoWasHit(), "the one behind them should take it")

        val twoDown = front.copy(
            champions = front.champions.toMutableList().also { it[2] = down() },
        )

        assertEquals(listOf(4), twoDown.whoWasHit(), "and the one behind them after that")
    }

    @Test
    fun `and nobody takes it when the whole file is down`() {
        val emptied = overThem(SquarePlace.SOUTH_EAST).let {
            it.copy(
                champions = it.champions.toMutableList().also { all ->
                    listOf(0, 2, 4).forEach { slot -> all[slot] = down() }
                },
            )
        }

        assertEquals(emptyList(), emptied.whoWasHit())
    }

    /** Somebody past raising, who is nobody for this purpose. */
    private fun down() = Champion.NOBODY.copy(name = "Down", flags = ChampionFlags(0))

    /**
     * Something crossing onto the party's square arrives at the near end of
     * it, which is the two behind — a thing coming at their backs reaches
     * those standing at the back first, and only then the front rank.
     */
    private fun steppingOnto(side: SquarePlace) = world().copy(
        party = PartyState(Location(10, 5), Direction.SOUTH),
        champions = sixOfThem(),
        inFlight = listOf(
            Projectile(
                what = ItemIndex(1),
                at = Location(10, 4),
                place = side,
                going = Direction.SOUTH,
                thrownBy = Projectile.Thrower.TheLevel,
                untilItSteps = 1,
                leaving = false,
            ),
        ),
    )

    @Test
    fun `something coming through behind them finds the ones standing back`() {
        assertEquals(
            listOf(2),
            steppingOnto(SquarePlace.SOUTH_EAST).whoWasHit(),
            "coming in down their left is the one at that shoulder's",
        )

        assertEquals(
            listOf(3, 5),
            steppingOnto(SquarePlace.SOUTH_WEST).whoWasHit().sorted(),
            "and the near right takes both, travelling the way they face",
        )
    }

    /**
     * The party are asked every turn of the clock and not only when the thing
     * moves. Somebody can arrive under it at any moment, and a thing part way
     * across a square that moves nowhere this turn is still over them.
     */
    @Test
    fun `walking under one costs them even between its steps`() {
        val betweenSteps = overThem(SquarePlace.SOUTH_EAST).let {
            it.copy(
                inFlight = it.inFlight.map { flying ->
                    // two steps' worth of waiting, so this turn of the clock
                    // moves it nowhere at all
                    flying.copy(untilItSteps = Projectile.ACROSS_A_SQUARE)
                },
            )
        }

        assertTrue(
            betweenSteps.whoWasHit().isNotEmpty(),
            "it went over them without touching them",
        )
    }

    /**
     * Which is not where it stops. A thing crossing a square passes the middle
     * and takes up the far end, so what is over the party a moment later is
     * the front rank's.
     */
    @Test
    fun `and reaches the front rank once it is half way across`() {
        val aboutToStep = overThem(SquarePlace.NORTH_EAST).let {
            it.copy(inFlight = it.inFlight.map { flying -> flying.copy(untilItSteps = 1) })
        }

        assertEquals(listOf(0), aboutToStep.whoWasHit())
    }

    /** But letting go of one is not walking into it. */
    @Test
    fun `the square it is thrown from is not walked into`() {
        val thrower = world().copy(
            champions = listOf(Champion.NOBODY.copy(name = "One", flags = ChampionFlags(1))),
        ).thrown()

        val moved = Flight(sublevel = here, level = LEVEL).onward(thrower)

        assertEquals(emptyList(), moved.hurt, "the thrower was hit by their own throw")
    }

    // --- aiming --------------------------------------------------------------

    /**
     * A thing nobody threw cannot be thrown badly: a trap's bolt has no
     * thrower to have aimed it, so it lands on whatever it comes to.
     */
    @Test
    fun `what the level looses always lands`() {
        val onThem = overThem(SquarePlace.SOUTH_EAST)

        assertEquals(
            listOf(0),
            Flight(sublevel = here, level = LEVEL, dice = alwaysRolling(1))
                .onward(onThem)
                .hurt
                .filterIsInstance<Flight.Hurt.AChampion>()
                .map { it.slot.index },
            "the worst roll there is should not have saved them",
        )
    }

    /** And what is aimed at the party is not rolled for either. */
    @Test
    fun `nothing rolls to hit the party`() {
        val thrown = overThem(SquarePlace.SOUTH_EAST).let {
            it.copy(
                inFlight = it.inFlight.map { flying ->
                    flying.copy(thrownBy = Projectile.Thrower.AChampion(PartySlot(1)))
                },
            )
        }

        assertTrue(
            Flight(sublevel = here, level = LEVEL, dice = alwaysRolling(1))
                .onward(thrown)
                .hurt
                .isNotEmpty(),
            "a throw that found the party was rolled for",
        )
    }

    private fun alwaysRolling(pips: Int) = Dice { times, _, base -> times * pips + base }

    private companion object {
        const val LEVEL = 7

        /** What the script leaves every side of a square it takes away. */
        const val CLEARED = 0

        /** Long enough for anything to have finished crossing the corridor. */
        const val A_LONG_WAY = 60

        val THROWN_FROM = Location(10, 4)
        val THE_BUTTON = Location(10, 7)
    }
}
