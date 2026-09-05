package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterImmunities
import pl.pelotasplus.eyeofbeholder.data.model.MonsterProperty
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSize
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.script.CreateMonster
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * What happens when a script asks for a monster and the floor is already
 * holding all thirty it can.
 *
 * It is not refused. The engine takes away whichever of them is furthest off
 * — quietly, with no experience for it — so that whatever a script conjures
 * in front of the party always arrives. Refusing instead is how a nest goes
 * dead and never says why: everything it asks for is dropped on the floor
 * because of thirty creatures scattered across rooms nobody is standing in.
 *
 * What the floor was shipped with is safe from this. Only what a script put
 * there can be taken back.
 */
class MakingRoomForAMonsterTest {

    private val partyAt = Location(10, 10)
    private val onThisFloor = 7
    private val here = 0

    private val kinds = listOf(
        MonsterProperty(
            id = 0,
            armorClass = 5, hitChance = 13, level = 5,
            hpDcTimes = 1, hpDcPips = 1, hpDcBase = 0,
            attacksPerRound = 1, dmgDc = emptyList(),
            immunities = MonsterImmunities(0), capsFlags = 0, typeFlags = 0, experience = 420,
            size = MonsterSize.FILLS_THE_SQUARE,
            sound1 = 0, sound2 = 0,
            numRemoteAttacks = 0, remoteWeaponChangeMode = null, numRemoteWeapons = null,
            remoteWeapons = emptyList(), tuResist = -1, dmgModifierEvade = 0,
            decorations = emptyList(),
        ),
    )

    /**
     * Thirty of them strung out along the top of the map, so that which one
     * is furthest from the party is a fact about the arrangement and not
     * about the order they happen to be in.
     */
    private fun aFullFloor(
        conjured: Boolean = true,
        at: (Int) -> Location = { Location(it, 0) },
    ) = GameState(
        party = PartyState(partyAt, Direction.NORTH),
        monsters = List(SLOTS) { standing(MonsterSlot(it), at(it), conjured) },
    )

    private fun standing(slot: MonsterSlot, where: Location, conjured: Boolean) =
        MonsterInstance(
            index = slot,
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
            subLevel = here,
            level = onThisFloor,
            conjured = conjured,
        )

    private fun GameState.asksForOneOn(where: Location) = monsterCreated(
        CreateMonster(
            unit = 0,
            location = where,
            place = SquarePlace.MIDDLE,
            direction = Direction.EAST,
            type = MonsterTypeId(0),
            gfxIndex = 0,
            mode = 0,
            pause = 0,
            weapon = 0,
            pocketItem = 0,
        ),
        subLevel = here,
        kinds = kinds,
        dice = Dice { times, pips, modifier -> times * pips + modifier },
        level = onThisFloor,
    )

    private val wanted = Location(11, 10)

    @Test
    fun `a full floor still answers a script`() {
        val after = aFullFloor().asksForOneOn(wanted)

        assertEquals(SLOTS, after.monsters.size, "the floor grew or shrank")
        assertNotNull(
            after.monsters.firstOrNull { it.location == wanted },
            "the script asked for one and got nothing",
        )
    }

    /**
     * Strung along the top row, (29,0) is the furthest from (10,10) — nineteen
     * across and ten down, which the engine counts as twenty-four.
     */
    @Test
    fun `the furthest one is the one that goes`() {
        val after = aFullFloor().asksForOneOn(wanted)

        assertNull(
            after.monsters.firstOrNull { it.location == Location(29, 0) },
            "something nearer was taken and the furthest left standing",
        )
        assertNotNull(
            after.monsters.firstOrNull { it.location == Location(11, 0) },
            "the nearest of them was taken instead of the furthest",
        )
    }

    @Test
    fun `and it leaves its slot to the newcomer`() {
        val after = aFullFloor().asksForOneOn(wanted)

        assertEquals(
            MonsterSlot(29),
            after.monsters.first { it.location == wanted }.index,
            "the newcomer did not take the slot that was freed for it",
        )
    }

    @Test
    fun `what the floor came with is never taken`() {
        val after = aFullFloor(conjured = false).asksForOneOn(wanted)

        assertEquals(
            SLOTS,
            after.monsters.size,
            "a monster the floor was shipped with was cleared away",
        )
        assertNull(
            after.monsters.firstOrNull { it.location == wanted },
            "room was made where there was none to make",
        )
    }

    /**
     * One conjured among twenty-nine that were shipped, and standing nearer
     * than any of them: it goes anyway, being the only one that may.
     */
    @Test
    fun `the only one that may go goes, near or far`() {
        val floor = aFullFloor(conjured = false)
        val nearest = floor.monsters.first().copy(
            location = Location(11, 11),
            conjured = true,
        )

        val after = floor
            .copy(monsters = listOf(nearest) + floor.monsters.drop(1))
            .asksForOneOn(wanted)

        assertNull(
            after.monsters.firstOrNull { it.location == Location(11, 11) },
            "the one that could be taken was passed over for ones that could not",
        )
        assertEquals(SLOTS, after.monsters.size, "the newcomer did not arrive")
    }

    /** Standing on the party is what keeps one of them, oddly enough. */
    @Test
    fun `nothing standing on the party is taken`() {
        val after = aFullFloor(at = { partyAt }).asksForOneOn(wanted)

        assertEquals(
            SLOTS,
            after.monsters.size,
            "one standing on the party's own square was cleared away",
        )
    }

    private companion object {
        const val SLOTS = 30
    }
}
