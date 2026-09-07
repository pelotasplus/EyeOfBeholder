package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.AHold
import pl.pelotasplus.eyeofbeholder.data.model.Damage
import pl.pelotasplus.eyeofbeholder.data.model.DamageDice
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Flight
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterImmunities
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterMode
import pl.pelotasplus.eyeofbeholder.data.model.MonsterProperty
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSize
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.MonstersTurn
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.Projectile
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.ThrownSpell
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
import kotlin.test.assertTrue

/**
 * Hold person and hold monster, laid on something rather than on the party.
 *
 * They are the only two spells the party throw that do no damage at all: what
 * arrives is a mode the creature goes into and a count that runs it down. What
 * decides whether it takes is not the caster — a scroll read by a fighter is
 * as good as any mage — but the creature: whether it is the kind that spell
 * asks for, whether it can throw the spell off, and whether it can be held at
 * all.
 *
 * Five questions decide it and the order is the game's: quick enough to dodge,
 * beyond this magic entirely, the throw, the kind, and whether it can be held
 * at all. The order is worth keeping because it decides how many dice are
 * thrown — the throw is made even by a creature the spell was never going to
 * take hold of.
 *
 * A held creature is stopped, not spared. It goes on taking damage and can be
 * killed where it stands, and hurting it does not free it.
 */
@Category(NeedsGameData::class)
class AHoldOnAMonsterTest {

    // --- what takes hold and what does not -------------------------------------

    @Test
    fun `hold person takes a person`() {
        val after = held(Spell.HOLD_PERSON, aCreatureMarked = A_PERSON, throwing = FAILS)

        assertEquals(AHold.FOR_THIS_LONG, after.heldFor)
        assertTrue(after.isHeld)
        assertEquals(MonsterMode.HELD, after.whatItDoes)
    }

    @Test
    fun `and passes over what is not one`() {
        val after = held(Spell.HOLD_PERSON, aCreatureMarked = A_MONSTER, throwing = FAILS)

        assertFalse(after.isHeld, "hold person took hold of something that is not a person")
    }

    @Test
    fun `hold monster takes what it asks for`() {
        val after = held(Spell.HOLD_MONSTER, aCreatureMarked = A_MONSTER, throwing = FAILS)

        assertTrue(after.isHeld)
    }

    /**
     * And not everything. Hold monster asks for its own mark rather than for
     * anything alive, so a creature carrying neither is held by neither spell.
     */
    @Test
    fun `and nothing takes hold of a creature marked as neither`() {
        listOf(Spell.HOLD_PERSON, Spell.HOLD_MONSTER).forEach { spell ->
            val after = held(spell, aCreatureMarked = NEITHER, throwing = FAILS)
            assertFalse(after.isHeld, "${spell.calledIt} held something marked as neither")
        }
    }

    /** The cleric's is the mage's under another number. */
    @Test
    fun `a cleric's hold person is the same spell`() {
        val after = held(Spell.A_CLERICS_HOLD_PERSON, aCreatureMarked = A_PERSON, throwing = FAILS)

        assertTrue(after.isHeld)
    }

    // --- what shrugs it off ----------------------------------------------------

    @Test
    fun `a creature that makes its throw is not held`() {
        val after = held(Spell.HOLD_PERSON, aCreatureMarked = A_PERSON, throwing = MAKES_IT)

        assertFalse(after.isHeld, "it made its throw and was held anyway")
    }

    @Test
    fun `one that cannot be held is not, throw or no throw`() {
        val after = held(
            Spell.HOLD_PERSON,
            aCreatureMarked = A_PERSON,
            throwing = FAILS,
            immunities = MonsterImmunities(NEVER_HELD),
        )

        assertFalse(after.isHeld)
    }

    /**
     * And one quick enough gets out of the way before any of that is asked.
     * A quarter of the twelfth floor's guardians dodge this way.
     */
    @Test
    fun `a creature quick enough dodges it altogether`() {
        val after = held(
            Spell.HOLD_PERSON,
            aCreatureMarked = A_PERSON,
            throwing = FAILS,
            dodgingOneTimeIn = OFTEN_ENOUGH,
        )

        assertFalse(after.isHeld, "it was held despite dodging")
    }

    @Test
    fun `and one this magic does not reach at all is passed over`() {
        val after = held(
            Spell.HOLD_PERSON,
            aCreatureMarked = A_PERSON,
            throwing = FAILS,
            immunities = MonsterImmunities(NOTHING_OF_THE_SORT),
        )

        assertFalse(after.isHeld)
    }

    // --- and what being held comes to ------------------------------------------

    /**
     * A held creature spends its turn running the hold down and does nothing
     * else — it does not step, and it is not roused by the party standing over
     * it. Being provoked is what would otherwise have it swinging.
     */
    @Test
    fun `a held monster stays where it is`() {
        val world = worldWith(aCreature(marked = A_PERSON).copy(provoked = true).heldFor(3))

        val after = MonstersTurn(listOf(aKind(A_PERSON))).begun(world).world.only

        assertEquals(WHERE_IT_STANDS, after.location, "it moved while held")
        assertEquals(2, after.heldFor, "the hold did not run down")
    }

    /** And when the count runs out it is free, and hunting. */
    @Test
    fun `the hold runs out and it takes up the hunt`() {
        var world = worldWith(aCreature(marked = A_PERSON).heldFor(2))
        val turn = MonstersTurn(listOf(aKind(A_PERSON)))

        world = turn.begun(world).world
        assertTrue(world.only.isHeld, "it came free a turn early")

        world = turn.begun(world).world
        assertFalse(world.only.isHeld, "it never came free")
        assertEquals(MonsterMode.HUNTING, world.only.whatItDoes)
    }

    /**
     * Stopped is not spared. A held creature takes what it is dealt and can be
     * killed standing there, and being hurt does not shake the hold off — it
     * is what makes holding one worth doing at all.
     */
    @Test
    fun `a held monster still takes damage, and stays held`() {
        val world = worldWith(aCreature(marked = A_PERSON).heldFor(5))

        val hurt = world.monsterHurt(
            MonsterSlot(0),
            Damage(6),
            listOf(aKind(A_PERSON)),
            null,
            Dice { _, _, _ -> 1 },
        ).only

        assertEquals(HitPoints(14, 20), hurt.hitPoints, "it shrugged the blow off")
        assertTrue(hurt.isHeld, "hurting it broke the hold")
        assertEquals(5, hurt.heldFor, "hurting it shortened the hold")
    }

    /** A second casting sets the count afresh rather than adding to it. */
    @Test
    fun `holding one twice does not stack`() {
        val once = aCreature(marked = A_PERSON).heldFor(AHold.FOR_THIS_LONG)
        val twice = once.copy(heldFor = 4).heldFor(AHold.FOR_THIS_LONG)

        assertEquals(AHold.FOR_THIS_LONG, twice.heldFor)
    }

    // --- the fixture -----------------------------------------------------------

    /**
     * The creature after that spell is cast down the corridor at it.
     *
     * A real flight down a real corridor rather than a poke at the effect: the
     * spell is loosed the way a scroll looses it, crosses a square, and is
     * asked what it found — which is the only way the wiring from the spell to
     * the hold is covered at all.
     */
    private fun held(
        spell: Spell,
        aCreatureMarked: Int,
        throwing: Int,
        immunities: MonsterImmunities = MonsterImmunities(0),
        dodgingOneTimeIn: Int = NEVER_DODGES,
    ): MonsterInstance {
        val thrown = requireNotNull(spell.throws) { "${spell.calledIt} throws nothing" }
        var world = worldWith(aCreature(marked = aCreatureMarked)).copy(
            inFlight = listOf(
                Projectile(
                    what = null,
                    at = CAST_FROM,
                    place = SquarePlace.NORTH_WEST,
                    going = Direction.SOUTH,
                    squaresLeft = thrown.flies.reach,
                    thrownBy = Projectile.Thrower.AChampion(PartySlot(0)),
                    harm = thrown.dealtBy(ThrownSpell.AS_READ_FROM_A_SCROLL),
                    spell = thrown.flies,
                ),
            ),
        )

        val flying = Flight(
            sublevel = here,
            level = LEVEL,
            kinds = listOf(aKind(aCreatureMarked, immunities, dodgingOneTimeIn)),
            dice = { _, _, _ -> throwing },
        )

        repeat(UNTIL_IT_ARRIVES) { world = flying.onward(world).world }

        return world.only
    }

    private val GameState.only: MonsterInstance get() = monsters.single()

    // Arriving first and placing the creature after it: arriving is what puts
    // the floor's own monsters out, and it would sweep this one away with them.
    private fun worldWith(monster: MonsterInstance) =
        GameState(party = PartyState(CAST_FROM, Direction.SOUTH))
            .arrivingAt(level = LEVEL, places = emptyList(), maz = here.maz)
            .copy(monsters = listOf(monster))

    private fun aCreature(marked: Int) = MonsterInstance(
        index = MonsterSlot(0),
        unit = 0,
        location = WHERE_IT_STANDS,
        place = SquarePlace.MIDDLE,
        direction = Direction.SOUTH,
        type = MonsterTypeId(0),
        gfxIndex = 0,
        mode = MonsterMode.HUNTING.asWritten,
        pause = 0,
        weapon = 0,
        pocketItem = 0,
        hitPoints = HitPoints(20, 20),
    )

    private fun aKind(
        marked: Int,
        immunities: MonsterImmunities = MonsterImmunities(0),
        dodgingOneTimeIn: Int = NEVER_DODGES,
    ) =
        MonsterProperty(
            id = 0,
            armorClass = 5,
            hitChance = 15,
            level = 5,
            hpDcTimes = 2,
            hpDcPips = 8,
            hpDcBase = 0,
            attacksPerRound = 1,
            dmgDc = listOf(DamageDice(1, 4, 0)),
            immunities = immunities,
            capsFlags = 3,
            typeFlags = marked,
            experience = 100,
            size = MonsterSize.TWO_TO_A_SQUARE,
            sound1 = 0,
            sound2 = 0,
            numRemoteAttacks = 0,
            remoteWeaponChangeMode = null,
            numRemoteWeapons = null,
            remoteWeapons = emptyList(),
            tuResist = -1,
            dmgModifierEvade = dodgingOneTimeIn,
            decorations = emptyList(),
        )

    private val resources = ResourceRepositoryImpl()

    private val here = runBlocking {
        InfRepositoryImpl(
            resourceRepository = resources,
            mazRepository = MazRepositoryImpl(resources),
            vmpRepository = VmpRepositoryImpl(resources),
            vcnRepository = VcnRepositoryImpl(resources),
            palRepository = PalRepositoryImpl(resources),
            cpsRepository = CpsRepositoryImpl(resources),
            decRepository = DecRepositoryImpl(resources),
        ).loadInf("LEVEL12.INF").getOrThrow().subLevels[0]
    }

    private companion object {
        const val LEVEL = 12

        /** A clear stretch of the twelfth floor to cast down. */
        val CAST_FROM = Location(19, 15)
        val WHERE_IT_STANDS = Location(19, 16)

        /** Long enough for the bolt to leave its own square and arrive. */
        const val UNTIL_IT_ARRIVES = 30

        /** The two marks a species carries, which decide which spell reaches it. */
        const val A_PERSON = 0x1
        const val A_MONSTER = 0x2
        const val NEITHER = 0x0

        const val NEVER_HELD = 0x2
        const val NOTHING_OF_THE_SORT = 0x10

        /** A twenty makes any throw; a one makes none. */
        const val MAKES_IT = 20
        const val FAILS = 1

        /**
         * How often a kind gets out of the way, out of a hundred. Nothing at
         * all for most of these, so the throw is what decides them; often
         * enough that a die showing [FAILS] dodges, where dodging is the point.
         */
        const val NEVER_DODGES = 0
        const val OFTEN_ENOUGH = 50
    }
}
