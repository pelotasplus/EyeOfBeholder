package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameFlags
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterProperty
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSize
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.SavedWorld
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Each floor keeps its own monsters, and arriving takes that floor's and no
 * other's.
 *
 * Getting this wrong is invisible on screen: the floor is simply empty of the
 * creatures that belong to it and full of ones that do not, and nothing says
 * why. A type another floor uses has no entry in this one's table, so no size
 * and no rules apply to it either, and it can stand where nothing should.
 */
class MonstersPerFloorTest {

    private fun standing(level: Int, howMany: Int) = List(howMany) {
        MonsterInstance(
            index = MonsterSlot(it),
            unit = 1,
            location = Location(level, it),
            place = SquarePlace.MIDDLE,
            direction = Direction.NORTH,
            type = MonsterTypeId(level),
            gfxIndex = 0,
            mode = 0,
            pause = 0,
            weapon = 0,
            pocketItem = 0,
        )
    }

    private val onTwo = standing(level = 2, howMany = 4)
    private val onThree = standing(level = 3, howMany = 26)

    private fun world() = GameState(party = PartyState(Location(1, 1), Direction.NORTH))

    @Test
    fun `arriving somewhere new takes the floor's own monsters`() {
        val there = world().arrivingAt(level = 3, places = onThree)

        assertEquals(26, there.monsters.size)
        assertEquals(listOf(3), there.monsters.map { it.type.value }.distinct())
    }

    /** Coming back finds them as they were left, not as the file lists them. */
    @Test
    fun `coming back finds a floor as it was left`() {
        val fought = world()
            .arrivingAt(level = 3, places = onThree)
            .let { it.copy(monsters = it.monsters.drop(20)) }

        val away = fought.leaving(3).arrivingAt(level = 2, places = onTwo)
        val back = away.leaving(2).arrivingAt(level = 3, places = onThree)

        assertEquals(6, back.monsters.size, "the ones killed came back")
        assertEquals(listOf(3), back.monsters.map { it.type.value }.distinct())
    }

    /**
     * The one that bit. A world carried in from somewhere else was filed under
     * whatever floor happened to be open, which handed one floor's monsters to
     * another; the floor they belonged to then never peopled itself from its
     * file again. Filing it wrongly no longer imposes it on the floor.
     */
    @Test
    fun `a floor is never peopled from another floor's list`() {
        val loaded = world().arrivingAt(level = 2, places = onTwo)

        // Filed as though the party had walked out of level 3, which is what
        // an open level left over from before a load used to do.
        val muddled = loaded.leaving(3)

        val arrived = muddled.arrivingAt(level = 3, places = onThree)

        assertEquals(26, arrived.monsters.size, "level 3 took level 2's list")
        assertEquals(listOf(3), arrived.monsters.map { it.level }.distinct())
    }

    /**
     * A save written before monsters named their floor cannot be checked, so
     * only the floor being stood on is vouched for — those monsters are the
     * live world whatever the map says. The rest are read from their files
     * again, which is what mends a save already carrying a wrong list.
     */
    @Test
    fun `an old save keeps the floor it is standing on and forgets the others`() {
        val old = SavedWorld(
            party = PartyState(Location(1, 1), Direction.NORTH),
            monsters = onTwo,
            flags = GameFlags(),
            leftBehind = mapOf(2 to onTwo, 3 to onTwo),
            changedWalls = emptyList(),
        )

        val restored = GameState.restoredFrom(old, on = 2)

        assertEquals(4, restored.remembersOn(2), "the floor underfoot was forgotten")
        assertNull(restored.remembersOn(3), "an unvouched-for floor was believed")
    }

    /**
     * The one that matters. A monster names the floor it belongs to, so a
     * memory holding another floor's is refused outright — however plausible
     * its creatures look by kind.
     *
     * Level 2 and level 3 both number their species from zero, so level 2's
     * type 1 passes every test but this one.
     */
    @Test
    fun `a floor refuses a memory of monsters that name another floor`() {
        val kindsOfThree = listOf(kind(0), kind(1))
        val elsewhere = standing(level = 2, howMany = 24)
            .map { it.copy(type = MonsterTypeId(1), level = 2) }

        val muddled = world().copy(monsters = elsewhere).leaving(3)

        val arrived = muddled.arrivingAt(
            level = 3,
            places = onThree,
            kinds = kindsOfThree,
        )

        assertEquals(26, arrived.monsters.size, "level 2's list was taken for level 3's")
        assertEquals(listOf(3), arrived.monsters.map { it.level }.distinct())
    }

    /** Arriving stamps the floor on, so leaving files something checkable. */
    @Test
    fun `a floor's own monsters name it`() {
        val there = world().arrivingAt(level = 3, places = onThree)

        assertEquals(listOf(3), there.monsters.map { it.level }.distinct())
    }

    /**
     * A floor's own table of species is what says which creatures can stand on
     * it, so a remembered one whose kind that floor has no row for is somebody
     * else's and is dropped. A memory of nothing but those is not a memory of
     * this floor, and the file is a truer account than it.
     *
     * This is the weaker of the two tests, kept for monsters out of a save
     * written before they named their floor.
     */
    @Test
    fun `a floor remembered as holding only creatures it has no kinds for is peopled afresh`() {
        val kindsOfThree = listOf(kind(3))
        val muddled = world()
            .arrivingAt(level = 2, places = onTwo)
            .leaving(3)

        val arrived = muddled.arrivingAt(level = 3, places = onThree, kinds = kindsOfThree)

        assertEquals(26, arrived.monsters.size, "the floor should have peopled itself")
        assertEquals(listOf(3), arrived.monsters.map { it.type.value }.distinct())
    }

    /** One of its own among strangers keeps the memory; the strangers go. */
    @Test
    fun `creatures a floor has no kinds for are dropped from what it remembers`() {
        val kindsOfThree = listOf(kind(3))
        val mixed = world()
            .copy(monsters = onTwo + standing(level = 3, howMany = 2))
            .leaving(3)

        val arrived = mixed.arrivingAt(level = 3, places = onThree, kinds = kindsOfThree)

        assertEquals(2, arrived.monsters.size, "the strangers were kept")
        assertEquals(listOf(3), arrived.monsters.map { it.type.value }.distinct())
    }

    /** A floor cleared by fighting is remembered as cleared, not refilled. */
    @Test
    fun `a floor emptied by fighting stays empty`() {
        val kindsOfThree = listOf(kind(3))
        val cleared = world()
            .arrivingAt(level = 3, places = onThree, kinds = kindsOfThree)
            .copy(monsters = emptyList())
            .leaving(3)

        val back = cleared.arrivingAt(level = 3, places = onThree, kinds = kindsOfThree)

        assertEquals(emptyList(), back.monsters, "the dead came back")
    }

    private fun kind(id: Int) = MonsterProperty(
        id = id,
        armorClass = 0, hitChance = 0, level = 1,
        hpDcTimes = 1, hpDcPips = 1, hpDcBase = 0,
        attacksPerRound = 1, dmgDc = emptyList(),
        immunityFlags = 0, capsFlags = 0, typeFlags = 0, experience = 0,
        size = MonsterSize.FOUR_TO_A_SQUARE,
        sound1 = 0, sound2 = 0,
        numRemoteAttacks = 0, remoteWeaponChangeMode = null, numRemoteWeapons = null,
        remoteWeapons = emptyList(), tuResist = 0, dmgModifierEvade = 0,
        decorations = emptyList(),
    )

    /**
     * Arriving remembers nothing: it is walking out of a floor that files it,
     * which is why a floor is only ever remembered once the party have been on
     * it and gone.
     */
    @Test
    fun `a floor is filed by leaving it, not by arriving`() {
        val arrived = world().arrivingAt(level = 2, places = onTwo)

        assertNull(arrived.remembersOn(2), "arriving filed the floor it arrived on")
        assertNull(arrived.remembersOn(3), "and one it has never seen")

        assertEquals(4, arrived.leaving(2).remembersOn(2), "leaving files it")
    }
}
