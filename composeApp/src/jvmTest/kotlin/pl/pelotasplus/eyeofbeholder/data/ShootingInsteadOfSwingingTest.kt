package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterMode
import pl.pelotasplus.eyeofbeholder.data.model.MonsterPathing
import pl.pelotasplus.eyeofbeholder.data.model.MonsterProperty
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSize
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterStepping
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.MonstersTurn
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.TakingAShot
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Where a shot sits in a monster's turn, which is first and instead of
 * everything else.
 *
 * The engine tries the three in a fixed order and returns on the first that
 * takes: shoot, else swing, else walk. So a monster that looses one does
 * neither of the others, and — the part that is easy to get backwards — one
 * that is merely *waiting* for its next shot has not spent anything, and
 * fights or walks that turn as it always would.
 *
 * The second floor's corridor is the ground: it runs north from the party at
 * 3x11 up to 3x8, with a wall past that.
 */
@Category(NeedsGameData::class)
class ShootingInsteadOfSwingingTest {

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
        ).loadInf("LEVEL2.INF").getOrThrow()
    }

    private val sub get() = level.subLevels[0]

    private val partyAt = Location(3, 11)

    /** Always the lowest roll, so the wait is over and the shot is taken. */
    private val theLowestRoll = Dice { times, _, modifier -> times + modifier }

    /** Always the highest, so the wait never runs out. */
    private val theHighestRoll = Dice { times, pips, modifier -> times * pips + modifier }

    /** One that carries hold person, as the clerics of this floor do. */
    private val kind = MonsterProperty(
        id = 0,
        armorClass = 5, hitChance = 13, level = 5,
        hpDcTimes = 1, hpDcPips = 1, hpDcBase = 0,
        attacksPerRound = 1, dmgDc = emptyList(),
        immunityFlags = 0, capsFlags = 0, typeFlags = 0, experience = 100,
        size = MonsterSize.FOUR_TO_A_SQUARE,
        sound1 = 0, sound2 = 0,
        numRemoteAttacks = 4,
        remoteWeaponChangeMode = 1,
        numRemoteWeapons = 1,
        remoteWeapons = listOf(3),
        tuResist = -1, dmgModifierEvade = 0,
        decorations = emptyList(),
    )

    private val kinds = listOf(kind)

    private fun shooter(
        at: Location,
        mode: MonsterMode = MonsterMode.HUNTING,
        waited: Int = 3,
    ) = MonsterInstance(
        index = SHOOTER,
        unit = 0,
        location = at,
        place = SquarePlace.MIDDLE,
        direction = Direction.SOUTH,
        type = MonsterTypeId(0),
        gfxIndex = 0,
        mode = mode.asWritten,
        pause = 0,
        weapon = 0,
        pocketItem = 0,
        hitPoints = HitPoints(10, 10),
        shotsLeft = 4,
        stepsTillItShoots = waited,
    )

    private fun world(monster: MonsterInstance, roused: Boolean = true) = GameState(
        party = PartyState(partyAt, Direction.NORTH),
        monsters = listOf(monster),
    ).let { if (roused) it.rousedBy(SHOOTER) else it }

    private val walking = MonsterPathing(
        stepping = MonsterStepping(level = 2, subLevel = sub, kinds = kinds),
        kinds = kinds,
    )

    private fun turn(dice: Dice) = MonstersTurn(
        kinds = kinds,
        dice = dice,
        shooting = TakingAShot(sublevel = sub, level = 2, kinds = kinds, dice = dice),
    )

    private fun GameState.theShooter() = monsters.first { it.index == SHOOTER }

    private fun GameState.whereItIs() = theShooter().let { Location(it.x, it.y) }

    // --- a shot is the whole turn ---------------------------------------------

    /**
     * Standing next to the party and facing them, it would otherwise start a
     * swing. Shooting is tried first, so it does not.
     */
    @Test
    fun `one that shoots does not also swing`() {
        val after = turn(theLowestRoll).begun(world(shooter(at = Location(3, 10))))

        assertNull(after.theShooter().striking, "it swung on the turn it shot")
        assertEquals(3, after.theShooter().shotsLeft, "the shot was not spent")
    }

    /** And out of reach, with somewhere to walk to, it holds its ground. */
    @Test
    fun `one that shoots does not also walk`() {
        val after = turn(theLowestRoll).begun(world(shooter(at = Location(3, 9))), walking)

        assertEquals(Location(3, 9), after.whereItIs(), "it walked on the turn it shot")
        assertEquals(3, after.theShooter().shotsLeft, "the shot was not spent")
    }

    // --- but waiting for one is not -------------------------------------------

    /**
     * The wait is one turn longer and nothing else: the monster goes on to do
     * what it would have done. Without this a cleric stands rooted in the
     * corridor for as long as its wait runs.
     */
    @Test
    fun `one still waiting for its shot walks anyway, and waits one less`() {
        val waiting = shooter(at = Location(3, 9), waited = 0)

        val after = turn(theHighestRoll).begun(world(waiting), walking)

        assertEquals(Location(3, 10), after.whereItIs(), "waiting for a shot ate its step")
        assertEquals(4, after.theShooter().shotsLeft, "a shot it never took was counted")
        assertEquals(
            1,
            after.theShooter().stepsTillItShoots,
            "the waiting it did was thrown away, so the shot never comes",
        )
    }

    /** The same next to the party: it swings, as it would with no bow at all. */
    @Test
    fun `and one still waiting swings anyway`() {
        val waiting = shooter(at = Location(3, 10), waited = 0)

        val after = turn(theHighestRoll).begun(world(waiting))

        assertNotNull(after.theShooter().striking, "waiting for a shot ate its swing")
    }

    // --- and only a hunting monster shoots at all ------------------------------

    /**
     * A shot is tried on the hunting turn and on no other. One waiting to see
     * what the party do is deaf until somebody hits it, and being lined up
     * squarely down a corridor at them does not change that.
     */
    @Test
    fun `one waiting to see what the party do does not shoot them`() {
        val talking = shooter(at = Location(3, 9), mode = MonsterMode.WAITING_TO_SEE)

        val after = turn(theLowestRoll).begun(world(talking, roused = false), walking)

        assertEquals(4, after.theShooter().shotsLeft, "it shot before anybody had provoked it")
    }

    private companion object {
        val SHOOTER = MonsterSlot(0)
    }
}
