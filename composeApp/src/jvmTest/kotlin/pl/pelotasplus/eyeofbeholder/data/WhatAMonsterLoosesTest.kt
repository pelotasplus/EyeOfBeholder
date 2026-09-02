package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Burst
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
import pl.pelotasplus.eyeofbeholder.data.model.ConjuredBolt
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemIconId
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemNameId
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypeId
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterProperty
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSize
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.Projectile
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
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a monster puts in the air, which the number in its weapon list decides
 * and the sign of that number splits in two.
 *
 * The expected shapes, reaches and colours are the game's own flight table
 * read as four bytes an entry, not anything this code worked out: a spell that
 * crosses six squares does so because the table says six.
 *
 * The corridor is the second floor's, running north from the party at 3x11.
 */
@Category(NeedsGameData::class)
class WhatAMonsterLoosesTest {

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
    private val shooterAt = Location(3, 9)

    /** Always the lowest roll, so the wait is over and the first choice is made. */
    private val theLowestRoll = Dice { times, _, modifier -> times + modifier }

    private fun kind(weapons: List<Int>) = MonsterProperty(
        id = 0,
        armorClass = 5, hitChance = 13, level = 5,
        hpDcTimes = 1, hpDcPips = 1, hpDcBase = 0,
        attacksPerRound = 1, dmgDc = emptyList(),
        immunityFlags = 0, capsFlags = 0, typeFlags = 0, experience = 100,
        size = MonsterSize.FOUR_TO_A_SQUARE,
        sound1 = 0, sound2 = 0,
        numRemoteAttacks = 4,
        remoteWeaponChangeMode = 1,
        numRemoteWeapons = weapons.size,
        remoteWeapons = weapons,
        tuResist = -1, dmgModifierEvade = 0,
        decorations = emptyList(),
    )

    private val shooter = MonsterInstance(
        index = SHOOTER,
        unit = 0,
        location = shooterAt,
        place = SquarePlace.SOUTH_WEST,
        direction = Direction.SOUTH,
        type = MonsterTypeId(0),
        gfxIndex = 0,
        mode = 0,
        pause = 0,
        weapon = 0,
        pocketItem = 0,
        hitPoints = HitPoints(10, 10),
        shotsLeft = 4,
        stepsTillItShoots = 3,
    )

    /**
     * A table with a nothing at the head of it, one real thing to be copied,
     * and a free slot for the copy to go into. Slot zero is never a thing in
     * this game — it is what an empty hand holds.
     */
    private fun world() = GameState(
        party = PartyState(partyAt, Direction.NORTH),
        monsters = listOf(shooter),
        items = listOf(nothing, aRock(Location(1, 1)), nothing),
    )

    private val nothing = anItem(Item.NOWHERE)

    private fun aRock(at: Location) = anItem(at)

    private fun anItem(at: Location) = Item(
        nameUnidentified = ItemNameId(0),
        nameIdentified = ItemNameId(0),
        flags = 0,
        icon = ItemIconId(1),
        type = ItemTypeId(0),
        place = SquarePlace.NORTH_WEST,
        location = at,
        next = 0,
        prev = 0,
        level = 2,
        value = 0,
    )

    /** The one thing in the table that is really there. */
    private val somethingReal = ItemIndex(1)

    /** What one carrying [weapons] leaves in the air, having shot. */
    private fun loosed(weapons: List<Int>): List<Projectile> {
        val kinds = listOf(kind(weapons))
        val shooting = TakingAShot(sublevel = sub, level = 2, kinds = kinds, dice = theLowestRoll)
        val world = world()

        val shot = shooting.taken(world, shooter)
        check(shot is TakingAShot.Shot.Looses) { "it did not shoot at all: $shot" }

        return shooting.loosed(world, shot).world.inFlight
    }

    private fun theOneLoosed(weapon: Int) = loosed(listOf(weapon)).single()

    // --- a spell --------------------------------------------------------------

    /**
     * Hold person is the second floor's own, and the flight table gives it a
     * reach of 255 — as far as the corridor goes.
     */
    @Test
    fun `a spell goes into the air as a bolt with nothing behind it`() {
        val flying = theOneLoosed(HOLD_PERSON)

        assertNull(flying.what, "a conjured thing was given something to be picked up")
        assertEquals(ConjuredBolt.LIKE_MOTES, flying.looksLike)
        assertEquals(Projectile.UNTIL_IT_HITS, flying.squaresLeft)
    }

    /** It starts where the monster stands, on its quarter, going its way. */
    @Test
    fun `and it starts from the monster, facing the way the monster does`() {
        val flying = theOneLoosed(HOLD_PERSON)

        assertEquals(shooterAt, flying.at)
        assertEquals(SquarePlace.SOUTH_WEST, flying.place, "it was not loosed from its own corner")
        assertEquals(Direction.SOUTH, flying.going)
        assertEquals(Projectile.Thrower.AMonster(SHOOTER), flying.thrownBy)
    }

    /**
     * Six squares and a burst, both from the table, and the burst is a bolt of
     * lightning's rather than a fire's — the one place the two are told apart
     * is where they go off.
     */
    @Test
    fun `a lightning bolt reaches six squares and goes off in its own colours`() {
        val flying = theOneLoosed(LIGHTNING_BOLT)

        assertEquals(ConjuredBolt.LIKE_LIGHTNING, flying.looksLike)
        assertEquals(6, flying.squaresLeft)
        assertTrue(flying.harm.everybody, "it stopped instead of going off")
        assertEquals(Burst.LIKE_LIGHTNING, flying.burstsLike)
    }

    /** Hold person is not one of them, and must not burst on arrival. */
    @Test
    fun `and one that does not burst simply stops`() {
        assertTrue(!theOneLoosed(HOLD_PERSON).harm.everybody, "it went off where it should stop")
    }

    /**
     * The four a beholder fires are four different spells drawn as one shape,
     * so nothing on screen says which of them is coming. Surprising enough to
     * look like a bug, and it is not.
     */
    @Test
    fun `every one of a beholder's four rays looks exactly like the others`() {
        val rays = BEHOLDER_RAYS.map { theOneLoosed(it) }

        assertEquals(
            listOf(ConjuredBolt.LIKE_MOTES),
            rays.map { it.looksLike }.distinct(),
            "the rays were told apart by sight, which the game never does",
        )
    }

    /** But they are still four spells, and the fireballs are not motes. */
    @Test
    fun `a fireball does not look like a ray`() {
        assertNotEquals(theOneLoosed(FIREBALL).looksLike, theOneLoosed(HOLD_PERSON).looksLike)
    }

    // --- a thrown thing -------------------------------------------------------

    /**
     * A negative weapon names one of the level's own items, and what flies is
     * a copy: the guard keeps the one it was pointed at, so it can throw
     * another next turn.
     */
    @Test
    fun `a thrown weapon is a real thing, and a copy of the one named`() {
        val kinds = listOf(kind(listOf(-somethingReal.value)))
        val shooting = TakingAShot(sublevel = sub, level = 2, kinds = kinds, dice = theLowestRoll)
        val before = world()

        val shot = shooting.taken(before, shooter)
        check(shot is TakingAShot.Shot.Looses)
        val after = shooting.loosed(before, shot).world

        val flying = after.inFlight.single()
        val what = assertNotNull(flying.what, "a thrown thing had nothing to be picked up")

        assertNotEquals(somethingReal, what, "it threw the original instead of a copy")
        assertEquals(
            before.item(somethingReal),
            after.item(somethingReal),
            "the thing it was copied from was disturbed",
        )
        assertEquals(shooterAt, after.item(what)?.location, "the copy was not put where it flies")
    }

    // --- and one that is no projectile at all ---------------------------------

    /**
     * The mind blast is a number in the list with nothing behind it: it
     * reaches the whole party where they stand rather than crossing the room.
     * Nothing may be put in the air for it — least of all a fireball, which is
     * what reading it as a spell number would give.
     */
    @Test
    fun `the mind blast puts nothing in the air`() {
        assertTrue(loosed(listOf(MIND_BLAST)).isEmpty(), "the mind blast threw something")
    }

    /** It reaches all six at once, each thrown against on their own. */
    @Test
    fun `and takes hold of whoever fails the throw`() {
        val shot = blasting(everyDieAtItsLeast)

        assertEquals(6, shot.blast?.held?.size, "it did not reach the whole party")
        assertEquals(6, shot.world.champions.count { it.paralysed })
    }

    /** And a party who all make it are only startled. */
    @Test
    fun `and leaves alone whoever makes it`() {
        val shot = blasting(everyDieAtItsMost)

        assertEquals(emptyList(), shot.blast?.held, "somebody was held who saved")
        assertTrue(shot.world.champions.none { it.paralysed })
    }

    /** Always the highest roll, so every saving throw is made. */
    private val everyDieAtItsMost = Dice { times, pips, modifier -> times * pips + modifier }

    private val everyDieAtItsLeast = theLowestRoll

    /** A mindflayer's shot, with the party standing in front of it. */
    private fun blasting(dice: Dice): TakingAShot.Loosed {
        val kinds = listOf(kind(listOf(MIND_BLAST)))
        val shooting = TakingAShot(sublevel = sub, level = 2, kinds = kinds, dice = dice)
        val world = world().copy(champions = List(6) { aChampion() })

        val shot = shooting.taken(world, shooter)
        check(shot is TakingAShot.Shot.Looses) { "it did not shoot at all: $shot" }

        return shooting.loosed(world, shot)
    }

    private fun aChampion() = Champion.NOBODY.copy(
        name = "One",
        flags = ChampionFlags(IN_THE_PARTY),
        hitPoints = HitPoints(40, 40),
        levels = listOf(ClassLevel(5, XpPoints(0))),
    )

    private companion object {
        val SHOOTER = MonsterSlot(0)

        const val IN_THE_PARTY = 0x01

        const val FIREBALL = 2
        const val HOLD_PERSON = 3
        const val LIGHTNING_BOLT = 4
        const val MIND_BLAST = 20

        val BEHOLDER_RAYS = listOf(10, 11, 12, 13)
    }
}
