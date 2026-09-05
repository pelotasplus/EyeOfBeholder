package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.CountedBy
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Flight
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HandUse
import pl.pelotasplus.eyeofbeholder.data.model.HarmKind
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.ItemKind
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterImmunities
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.Projectile
import pl.pelotasplus.eyeofbeholder.data.model.SavingThrow
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.ThrownSpell
import pl.pelotasplus.eyeofbeholder.data.model.Wand
import pl.pelotasplus.eyeofbeholder.data.model.WhatCastingCosts
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemTypesRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.MazRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VcnRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VmpRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A bolt of lightning, which is the one thing that is not spent on what it
 * finds.
 *
 * Everything else in the air stops at the first thing it touches. A bolt goes
 * the length of the corridor and takes whatever is standing in it, which is
 * the whole reason to loose one down a line of monsters rather than at the
 * front of it. The game says so by name rather than by any rule: it is the one
 * numbered flying thing written into the engine as an exception.
 *
 * A die of six for every level of the caster, magical and electrical both — so
 * a creature that shrugs off lightning stands in the middle of one untouched
 * while its neighbours are struck.
 */
@Category(NeedsGameData::class)
class ABoltDownACorridorTest {

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

    /**
     * The second floor's corridor, which runs north from the party at 3x11 up
     * to 3x8 with a wall past it — four squares of clear line.
     */
    private val partyStand = Location(3, 11)
    private val upTheCorridor = listOf(Location(3, 10), Location(3, 9), Location(3, 8))

    private val scrolls = setOf(ItemKind.MAGE_SCROLL, ItemKind.CLERIC_SCROLL)

    private val alwaysTheMost = Dice { times, pips, modifier -> times * pips + modifier }

    /** Lowest for a saving throw, highest for damage — told apart by the pips. */
    private val neverSaving = Dice { times, pips, modifier ->
        if (pips == 20) times + modifier else times * pips + modifier
    }

    // --- what the spell says it is -------------------------------------------

    @Test
    fun `it is electrical, and it carries on`() {
        val thrown = assertNotNull(Spell.LIGHTNING_BOLT.throws, "it throws nothing")

        assertEquals(setOf(HarmKind.MAGIC, HarmKind.ELECTRICITY), thrown.hurting)
        assertEquals(CountedBy.EVERY_LEVEL, thrown.counted)
        assertTrue(thrown.carriesOn, "a bolt is spent on the first thing it finds")
        assertEquals(SavingThrow.A_SPELL, thrown.thrownOff)
    }

    /**
     * It does not take the whole square the way a burning one does, and it
     * does not go off where it stops. Six squares is as far as it reaches,
     * where a fireball goes until it meets something.
     */
    @Test
    fun `it is neither a burst nor a thing that takes a whole square`() {
        val thrown = Spell.LIGHTNING_BOLT.throws!!

        assertTrue(!thrown.takesTheWholeSquare)
        assertEquals(6, thrown.flies.reach, "a bolt gives out rather than going on for ever")
    }

    // --- and where the party get one from ------------------------------------

    /**
     * Both ways of holding one lead to the same spell.
     *
     * A scroll writes the spell's own number on itself; a wand says which of
     * eight it is and is looked up. They arrive at the same place, which is
     * why setting the spell down once is enough to make both work.
     */
    @Test
    fun `a scroll of it and the wand of it both cast the same spell`() = runBlocking {
        val itemTypes = ItemTypesRepositoryImpl(resources).loadItemTypes().getOrThrow()
        val dungeon = ItemsRepositoryImpl(resources).loadItems().getOrThrow()

        val scroll = dungeon.items.first {
            itemTypes.kindOf(it) in scrolls && Spell.of(it.value) == Spell.LIGHTNING_BOLT
        }
        val wand = dungeon.items.first {
            itemTypes.kindOf(it) == ItemKind.WAND && Wand.of(it.value) == Wand.OF_LIGHTNING
        }

        assertEquals(
            HandUse.Cast(Spell.LIGHTNING_BOLT),
            itemTypes.whatAHandDoesWith(scroll),
            "a scroll of it does not cast it",
        )
        assertEquals(
            HandUse.Cast(Spell.LIGHTNING_BOLT),
            itemTypes.whatAHandDoesWith(wand),
            "the wand of it does not cast it",
        )
    }

    /** The wand carries two, and reading from it spends one of them. */
    @Test
    fun `the wand of it is spent by being read from`() = runBlocking {
        val itemTypes = ItemTypesRepositoryImpl(resources).loadItemTypes().getOrThrow()
        val dungeon = ItemsRepositoryImpl(resources).loadItems().getOrThrow()

        val wand = dungeon.items.first {
            itemTypes.kindOf(it) == ItemKind.WAND && Wand.of(it.value) == Wand.OF_LIGHTNING
        }

        assertEquals(WhatCastingCosts.OneCharge, itemTypes.whatCastingCosts(wand))
        assertEquals(2, wand.chargesLeft, "the wand of lightning carries two")
    }

    // --- and what that does down a corridor ----------------------------------

    /**
     * Three creatures standing one behind another, and one bolt: all three are
     * struck. Anything else would have stopped at the first.
     */
    @Test
    fun `a bolt takes everything standing in the corridor`() {
        val struck = struck(standingOn = upTheCorridor)

        assertEquals(
            upTheCorridor.size,
            struck.size,
            "it stopped at the first of them instead of going on",
        )
    }

    /** And each of them takes the whole of it, not a share. */
    @Test
    fun `each of them takes a bolt's worth`() {
        val struck = struck(standingOn = upTheCorridor)

        assertTrue(struck.all { it.by.points == 54 }, "they shared it out: ${struck.map { it.by.points }}")
    }

    /**
     * One creature is not struck twice for standing still. A thing in the air
     * asks its square on every turn of the clock, so without the list of what
     * it has already tried a bolt would roll again at the same monster.
     */
    @Test
    fun `nothing is struck twice by the same bolt`() {
        val one = listOf(upTheCorridor.first())
        val struck = struck(standingOn = one)

        assertEquals(1, struck.size)
    }

    /**
     * A creature that shrugs off lightning is passed over while the rest are
     * struck — the bolt does not stop on it either, since it is spent on
     * nothing.
     */
    @Test
    fun `one that shrugs off lightning stands in it untouched`() {
        val struck = struck(standingOn = upTheCorridor, immunities = MonsterImmunities(SHRUGS_OFF_LIGHTNING))

        assertEquals(upTheCorridor.size, struck.size, "it stopped on the one it could not hurt")
        assertTrue(struck.all { it.by.points == 0 }, "lightning got through to it")
    }

    // --- the fixture ---------------------------------------------------------

    private fun struck(
        standingOn: List<Location>,
        immunities: MonsterImmunities = MonsterImmunities(0),
    ): List<Flight.Hurt.AMonster> {
        val thrown = Spell.LIGHTNING_BOLT.throws!!

        val monsters = standingOn.mapIndexed { at, where ->
            MonsterInstance(
                index = MonsterSlot(at),
                unit = 0,
                location = where,
                place = SquarePlace.MIDDLE,
                direction = Direction.SOUTH,
                type = MonsterTypeId(0),
                gfxIndex = 0,
                mode = 0,
                pause = 0,
                weapon = 0,
                pocketItem = 0,
                hitPoints = HitPoints(400, 400),
            )
        }

        // The maze first — arriving peoples the floor from what it is handed,
        // so the monsters go on afterwards rather than being swept away.
        var world = GameState(party = PartyState(partyStand, Direction.NORTH))
            .arrivingAt(level = 2, places = emptyList(), maz = level.subLevels[0].maz)
            .copy(
                monsters = monsters,
                inFlight = listOf(
                    Projectile(
                        what = null,
                        at = partyStand,
                        place = PartySlot(0).standsIn.onASquareFacing(Direction.NORTH),
                        going = Direction.NORTH,
                        squaresLeft = thrown.flies.reach,
                        thrownBy = Projectile.Thrower.AChampion(PartySlot(0)),
                        harm = thrown.dealtBy(ThrownSpell.AS_READ_FROM_A_SCROLL),
                        spell = thrown.flies,
                    ),
                ),
            )

        val flying = Flight(
            sublevel = level.subLevels[0],
            level = 2,
            kinds = listOf(level.subLevels[0].monsters.first().copy(immunities = immunities)),
            dice = neverSaving,
        )

        // Long enough for it to cross the whole corridor and be stopped by the
        // wall at the end of it.
        val struck = mutableListOf<Flight.Hurt.AMonster>()
        repeat(A_LONG_WAY) {
            val moved = flying.onward(world)
            world = moved.world
            struck += moved.hurt.filterIsInstance<Flight.Hurt.AMonster>()
        }

        return struck
    }

    private companion object {
        /** Turns of the clock, comfortably more than six squares' worth. */
        const val A_LONG_WAY = 60

        /** The bit that turns lightning aside. */
        const val SHRUGS_OFF_LIGHTNING = 0x20
    }
}
