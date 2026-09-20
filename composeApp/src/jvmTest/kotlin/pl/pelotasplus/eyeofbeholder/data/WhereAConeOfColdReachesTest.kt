package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.AConeInFront
import pl.pelotasplus.eyeofbeholder.data.model.AConeOfCold
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HarmKind
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterImmunities
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterProperty
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSize
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The cone of cold: seven squares in front of the caster, all frozen at once.
 *
 * The shape is the game's, kept as four tables of maze offsets — one per way
 * the party can face. They are the same seven squares turned four ways, which
 * is how they are written here; the tables themselves are transcribed into
 * this test so that the turning is checked against them rather than trusted.
 */
class WhereAConeOfColdReachesTest {

    /**
     * The four tables as the game keeps them: signed offsets into a maze
     * numbered thirty-two squares to the row, in facing order.
     *
     * Transcribed, so nothing here may be tidied or re-derived — a digit
     * changed by eye would agree with itself and with nothing else.
     */
    private val asTheGameKeepsThem = mapOf(
        Direction.NORTH to listOf(-32, -64, -63, -65, -96, -97, -95),
        Direction.EAST to listOf(1, 2, -30, 34, 3, -29, 35),
        Direction.SOUTH to listOf(32, 64, 63, 65, 96, 95, 97),
        Direction.WEST to listOf(-1, -2, 30, -34, -3, 29, -35),
    )

    /**
     * The turning is right if it lands on the squares the game's own tables
     * name, from every one of the four facings.
     *
     * Asked as a set: the tables list the same seven squares in a slightly
     * different order from one facing to the next, and everything on them is
     * frozen together, so the order carries nothing.
     */
    @Test
    fun `the cone covers the squares the game's tables name`() {
        val standing = Location(x = 10, y = 10)
        val block = standing.y * A_ROW + standing.x

        Direction.entries.forEach { facing ->
            val wanted = asTheGameKeepsThem.getValue(facing)
                .map { block + it }
                .toSet()

            val got = AConeInFront.spreadingFrom(standing, facing)
                .map { it.y * A_ROW + it.x }
                .toSet()

            assertEquals(wanted, got, "the cone facing $facing")
        }
    }

    /** Seven squares, and never the one the caster is standing on. */
    @Test
    fun `it covers seven squares and spares the caster's own`() {
        val standing = Location(10, 10)
        val covered = AConeInFront.spreadingFrom(standing, Direction.NORTH)

        assertEquals(7, covered.size)
        assertEquals(7, covered.toSet().size)
        assertTrue(standing !in covered)
    }

    /** One square wide where it leaves, three wide for the two rows after. */
    @Test
    fun `it opens out from one square to three`() {
        val covered = AConeInFront.spreadingFrom(Location(10, 10), Direction.NORTH)

        assertEquals(1, covered.count { it.y == 9 })
        assertEquals(3, covered.count { it.y == 8 })
        assertEquals(3, covered.count { it.y == 7 })
    }

    /** And stops at three: a creature four squares off is not in it. */
    @Test
    fun `it reaches three squares and no further`() {
        val covered = AConeInFront.spreadingFrom(Location(10, 10), Direction.NORTH)

        assertTrue(covered.none { it.y <= 6 })
    }

    // ---- what it does to what it reaches -------------------------------

    private fun aMonster(slot: Int, at: Location, type: Int = 0) = MonsterInstance(
        index = MonsterSlot(slot),
        unit = 1,
        location = at,
        place = SquarePlace.MIDDLE,
        direction = Direction.SOUTH,
        type = MonsterTypeId(type),
        gfxIndex = 0,
        mode = 0,
        pause = 0,
        weapon = 0,
        pocketItem = 0,
        hitPoints = HitPoints(200, 200),
    )

    private fun world(vararg monsters: MonsterInstance) = GameState(
        party = PartyState(Location(10, 10), Direction.NORTH),
        monsters = monsters.toList(),
    )

    /** Every die at its most, so what is dealt is the whole of what was rolled. */
    private val atItsWorst = Dice { times, pips, modifier -> times * pips + modifier }

    private fun aKind(immunities: MonsterImmunities = MonsterImmunities(0)) = MonsterProperty(
        id = 0,
        armorClass = 9, hitChance = 13, level = 1,
        hpDcTimes = 0, hpDcPips = 0, hpDcBase = 1,
        attacksPerRound = 1, dmgDc = emptyList(),
        immunities = immunities, capsFlags = 0, typeFlags = 2, experience = 120,
        size = MonsterSize.FILLS_THE_SQUARE,
        sound1 = 0, sound2 = 0,
        numRemoteAttacks = 0, remoteWeaponChangeMode = null, numRemoteWeapons = null,
        remoteWeapons = emptyList(), tuResist = -1, dmgModifierEvade = 0,
        decorations = emptyList(),
    )

    /**
     * A die of four and one over for each level of the caster: read off a
     * scroll, which is always ninth level, nine of each — so 45 at its most.
     */
    @Test
    fun `it freezes for a die of four and one over per level`() {
        val struck = AConeOfCold(kinds = listOf(aKind()), dice = atItsWorst)
            .castBy(world(aMonster(0, Location(10, 9))), casterLevel = 9)

        assertEquals(200 - (9 * 4 + 9), struck.world.monsters.single().hitPoints?.current)
    }

    /** And a less practised caster freezes for less, by the same rule. */
    @Test
    fun `a first-level caster freezes for one die and one over`() {
        val struck = AConeOfCold(kinds = listOf(aKind()), dice = atItsWorst)
            .castBy(world(aMonster(0, Location(10, 9))), casterLevel = 1)

        assertEquals(200 - (1 * 4 + 1), struck.world.monsters.single().hitPoints?.current)
    }

    /**
     * Everything in the cone at once, and nothing outside it. The one behind
     * the party is as safe as the one four squares ahead.
     */
    @Test
    fun `everything in the cone is frozen and nothing else`() {
        val struck = AConeOfCold(kinds = listOf(aKind()), dice = atItsWorst).castBy(
            world(
                aMonster(0, Location(10, 9)),
                aMonster(1, Location(11, 8)),
                aMonster(2, Location(10, 11)),
                aMonster(3, Location(10, 6)),
            ),
            casterLevel = 9,
        )

        assertEquals(listOf(MonsterSlot(0), MonsterSlot(1)), struck.frozen)
    }

    /**
     * Standing one behind another is no shelter: nothing is in flight, so
     * there is nothing for the first of them to stop.
     */
    @Test
    fun `one creature does not shield the one behind it`() {
        val struck = AConeOfCold(kinds = listOf(aKind()), dice = atItsWorst).castBy(
            world(aMonster(0, Location(10, 9)), aMonster(1, Location(10, 8))),
            casterLevel = 9,
        )

        assertEquals(2, struck.frozen.size)
        assertTrue(struck.world.monsters.all { it.hitPoints?.current == 200 - 45 })
    }

    /** What shrugs cold off stands in the cone and takes nothing from it. */
    @Test
    fun `a creature that shrugs cold off takes none of it`() {
        val immune = MonsterImmunities(HarmKind.COLD.asACreatureTurnsItAside ?: 0)

        val struck = AConeOfCold(kinds = listOf(aKind(immune)), dice = atItsWorst)
            .castBy(world(aMonster(0, Location(10, 9))), casterLevel = 9)

        assertEquals(200, struck.world.monsters.single().hitPoints?.current)
    }

    /**
     * Everything it freezes is marked to be drawn white for a moment.
     *
     * It is the only thing that says which of them the cold reached: the
     * swirl is over the whole view and points at nothing, and a creature that
     * survives looks exactly as it did. Taking the mark off again belongs to
     * whoever cast it — a silhouette nobody clears stays on until something
     * else happens to redraw, which in a still room is never.
     */
    @Test
    fun `everything frozen is marked to flash`() {
        val struck = AConeOfCold(kinds = listOf(aKind()), dice = atItsWorst).castBy(
            world(aMonster(0, Location(10, 9)), aMonster(1, Location(11, 8))),
            casterLevel = 9,
        )

        assertEquals(2, struck.frozen.size)
        assertTrue(struck.world.monsters.all { it.struck })
        assertTrue(struck.world.anythingFlashing)
    }

    /** And nothing outside the cone is marked, so the white says where it went. */
    @Test
    fun `nothing outside the cone is marked`() {
        val struck = AConeOfCold(kinds = listOf(aKind()), dice = atItsWorst).castBy(
            world(aMonster(0, Location(10, 9)), aMonster(1, Location(10, 11))),
            casterLevel = 9,
        )

        assertEquals(listOf(MonsterSlot(0)), struck.frozen)
        assertFalse(struck.world.monsters.single { it.index == MonsterSlot(1) }.struck)
    }

    /** It still rouses them, though: being frozen is a reason to come. */
    @Test
    fun `what it freezes is provoked`() {
        val struck = AConeOfCold(kinds = listOf(aKind()), dice = atItsWorst)
            .castBy(world(aMonster(0, Location(10, 9))), casterLevel = 9)

        assertTrue(struck.world.monsters.single().provoked)
    }

    /** Cast into an empty corridor it freezes nothing and says so. */
    @Test
    fun `a cone over nothing freezes nothing`() {
        val struck = AConeOfCold(dice = atItsWorst).castBy(world(), casterLevel = 9)

        assertEquals(emptyList(), struck.frozen)
    }

    private companion object {
        /** How many squares the maze numbers to a row, which its offsets step by. */
        const val A_ROW = 32
    }
}
