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
import pl.pelotasplus.eyeofbeholder.data.model.MonsterImmunities
import pl.pelotasplus.eyeofbeholder.data.model.MonsterProperty
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSize
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.SHOOTS_FOREVER
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Whether a monster shoots, which it is refused far more often than allowed.
 *
 * Five things have to hold at once: shots left, a wait that has run out, the
 * party within three squares, the party straight ahead, and nothing of its
 * own kind between. The corridor down the second floor is the one this uses,
 * running north from 3x11 up to 3x8 with a wall past it.
 */
@Category(NeedsGameData::class)
class TakingAShotTest {

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

    private val partyAt = Location(3, 11)

    /** Always the lowest roll, so the wait is over and any choice is the first. */
    private val theLowestRoll = Dice { times, _, modifier -> times + modifier }

    /** Always the highest, so the wait is never over. */
    private val theHighestRoll = Dice { times, pips, modifier -> times * pips + modifier }

    private fun kind(
        weapons: List<Int> = listOf(3),
        shots: Int = 4,
        changeMode: Int? = 1,
    ) = MonsterProperty(
        id = 0,
        armorClass = 5, hitChance = 13, level = 5,
        hpDcTimes = 1, hpDcPips = 1, hpDcBase = 0,
        attacksPerRound = 1, dmgDc = emptyList(),
        immunities = MonsterImmunities(0), capsFlags = 0, typeFlags = 0, experience = 100,
        size = MonsterSize.FOUR_TO_A_SQUARE,
        sound1 = 0, sound2 = 0,
        numRemoteAttacks = shots,
        remoteWeaponChangeMode = changeMode,
        numRemoteWeapons = weapons.size,
        remoteWeapons = weapons,
        tuResist = -1, dmgModifierEvade = 0,
        decorations = emptyList(),
    )

    private fun shooter(
        at: Location,
        facing: Direction = Direction.SOUTH,
        shots: Int? = 4,
        waited: Int = 3,
        slot: Int = 0,
    ) = MonsterInstance(
        index = MonsterSlot(slot),
        unit = 0,
        location = at,
        place = SquarePlace.MIDDLE,
        direction = facing,
        type = MonsterTypeId(0),
        gfxIndex = 0,
        mode = 0,
        pause = 0,
        weapon = 0,
        pocketItem = 0,
        hitPoints = HitPoints(10, 10),
        shotsLeft = shots,
        stepsTillItShoots = waited,
    )

    private fun world(vararg monsters: MonsterInstance) = GameState(
        party = PartyState(partyAt, Direction.NORTH),
        monsters = monsters.toList(),
    )

    private fun taking(
        monster: MonsterInstance,
        kind: MonsterProperty = kind(),
        dice: Dice = theLowestRoll,
        world: GameState = world(monster),
    ) = TakingAShot(
        sublevel = level.subLevels[0],
        level = 2,
        kinds = listOf(kind),
        dice = dice,
    ).taken(world, monster)

    // --- when it shoots -------------------------------------------------------

    @Test
    fun `it shoots down a clear corridor at the party ahead of it`() {
        val shot = taking(shooter(at = Location(3, 9)))

        assertIs<TakingAShot.Shot.Looses>(shot).also {
            assertEquals(3, it.weapon, "it reached for something its kind does not carry")
        }
    }

    @Test
    fun `and the shot is spent and the wait begun again`() {
        val shot = assertIs<TakingAShot.Shot.Looses>(taking(shooter(at = Location(3, 9))))

        assertEquals(3, shot.monster.shotsLeft, "the shot was not counted")
        assertEquals(0, shot.monster.stepsTillItShoots, "the wait did not start over")
    }

    /** Some kinds never run out, and their count must not run down. */
    @Test
    fun `what shoots forever is never any shorter of shots`() {
        val shot = assertIs<TakingAShot.Shot.Looses>(
            taking(
                shooter(at = Location(3, 9), shots = SHOOTS_FOREVER),
                kind = kind(shots = SHOOTS_FOREVER),
            ),
        )

        assertEquals(SHOOTS_FOREVER, shot.monster.shotsLeft, "the endless ran down")
    }

    // --- and when it does not --------------------------------------------------

    @Test
    fun `nothing without a weapon shoots`() {
        assertIs<TakingAShot.Shot.NotThisTurn>(
            taking(shooter(at = Location(3, 9)), kind = kind(weapons = emptyList())),
        )
    }

    @Test
    fun `nothing out of shots shoots`() {
        assertIs<TakingAShot.Shot.NotThisTurn>(taking(shooter(at = Location(3, 9), shots = 0)))
    }

    /** The wait is raced against a die, and a high roll means another turn of it. */
    @Test
    fun `a wait that has not run out is one longer instead`() {
        val shot = assertIs<TakingAShot.Shot.StillWaiting>(
            taking(shooter(at = Location(3, 9), waited = 0), dice = theHighestRoll),
        )

        assertEquals(1, shot.monster.stepsTillItShoots, "waiting did not bring the shot nearer")
    }

    @Test
    fun `it does not shoot what it is not facing`() {
        assertIs<TakingAShot.Shot.NotThisTurn>(
            taking(shooter(at = Location(3, 9), facing = Direction.NORTH)),
        )
    }

    /**
     * Three squares is as far as it reaches. The corridor runs to 3x8, which
     * is three from the party — and 3x7 is past the end of it anyway.
     */
    @Test
    fun `three squares off is near enough and four is not`() {
        assertIs<TakingAShot.Shot.Looses>(taking(shooter(at = Location(3, 8))))

        assertIs<TakingAShot.Shot.NotThisTurn>(
            taking(shooter(at = Location(3, 15))),
        )
    }

    /** It will not shoot over the head of its own kind. */
    @Test
    fun `one of its own in the way stops it`() {
        val shooting = shooter(at = Location(3, 9))
        val between = shooter(at = Location(3, 10), slot = 1)

        assertIs<TakingAShot.Shot.NotThisTurn>(
            taking(shooting, world = world(shooting, between)),
        )
    }

    // --- which of them it reaches for -----------------------------------------

    @Test
    fun `a kind that takes them in turn works down the list, and round again`() {
        val carrying = kind(weapons = listOf(3, 7, 11), changeMode = 1)

        var monster = shooter(at = Location(3, 9), shots = SHOOTS_FOREVER)

        val weapons = (1..4).map {
            val shot = assertIs<TakingAShot.Shot.Looses>(
                taking(monster, kind = carrying, world = world(monster)),
            )
            // a shot begins the wait again, and this is about the order it
            // reaches for them rather than about the waiting
            monster = shot.monster.copy(stepsTillItShoots = 3)
            shot.weapon
        }

        assertEquals(
            listOf(3, 7, 11, 3),
            weapons,
            "it did not take them in the order it carries them",
        )
    }

    @Test
    fun `and a kind with no way of choosing takes the first every time`() {
        val shot = assertIs<TakingAShot.Shot.Looses>(
            taking(
                shooter(at = Location(3, 9)),
                kind = kind(weapons = listOf(3, 7), changeMode = null),
            ),
        )

        assertEquals(3, shot.weapon)
    }

    @Test
    fun `a thing it throws comes through as a thing it throws`() {
        val shot = assertIs<TakingAShot.Shot.Looses>(
            taking(shooter(at = Location(3, 9)), kind = kind(weapons = listOf(-4))),
        )

        assertTrue(shot.weapon < 0, "a thrown weapon lost the sign that says it is one")
    }
}
