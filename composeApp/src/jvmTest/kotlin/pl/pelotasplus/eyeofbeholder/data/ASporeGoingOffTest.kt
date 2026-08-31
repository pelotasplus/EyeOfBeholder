package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.Damage
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterProperty
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSize
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The gas spores on the eighth floor, which do not die so much as go off.
 *
 * They are drawn to look like the beholders two floors down and are nothing
 * of the kind: nine armour class, one hit point, and a blow of any size at
 * all sets them off rather than wounding them. What that costs is decided by
 * where the thing was standing when it went, not by what hit it.
 *
 * Beside the party it is six dice of six against every one of them, halved by
 * a throw, and the flash is drawn on the party's own square. Two squares off
 * or more it is a flash on its own square and nothing else — which is the
 * whole reason to shoot one from down a corridor.
 */
class ASporeGoingOffTest {

    private val partyAt = Location(10, 10)
    private val spore = MonsterSlot(0)

    /**
     * Every die showing its most, which is also every saving throw made: the
     * throw is a die like any other and this one comes up twenty.
     */
    private val everyDieAtItsMost = Dice { times, pips, modifier -> times * pips + modifier }

    /** And every die showing one, which is every throw failed. */
    private val everyDieAtItsLeast = Dice { times, _, modifier -> times + modifier }

    private val kinds = listOf(
        kind(id = 0, capsFlags = 0x2012),
        kind(id = 1, capsFlags = 0x0012),
    )

    private fun standing(at: Location, kind: Int = 0) = GameState(
        party = PartyState(partyAt, Direction.NORTH),
        champions = List(6) {
            Champion.NOBODY.copy(
                name = "One",
                flags = ChampionFlags(IN_THE_PARTY),
                hitPoints = HitPoints(HEARTY, HEARTY),
            )
        },
        monsters = listOf(
            MonsterInstance(
                index = spore,
                unit = 0,
                location = at,
                place = SquarePlace.MIDDLE,
                direction = Direction.SOUTH,
                type = MonsterTypeId(kind),
                gfxIndex = 0,
                mode = 0,
                pause = 0,
                weapon = 0,
                pocketItem = 0,
                hitPoints = HitPoints(20, 20),
            ),
        ),
    )

    private fun GameState.scratched(dice: Dice = everyDieAtItsLeast) =
        monsterHurt(spore, by = Damage(1), kinds = kinds, dice = dice)

    @Test
    fun `the lightest blow finishes one off`() {
        val after = standing(at = partyAt.copy(y = partyAt.y - 1)).scratched()

        assertEquals(
            emptyList<MonsterInstance>(),
            after.monsters,
            "twenty hit points survived a scratch, which is a wound and not a burst",
        )
    }

    @Test
    fun `and one beside the party takes the whole party with it`() {
        val before = standing(at = partyAt.copy(y = partyAt.y - 1))
        val after = before.scratched()

        val lost = (0 until 6).map { PartySlot(it) }.map { whose ->
            (before.championIn(whose)?.hitPoints?.current ?: 0) -
                (after.championIn(whose)?.hitPoints?.current ?: 0)
        }

        assertEquals(
            List(6) { SIX_DICE_OF_ONE },
            lost,
            "a spore beside the party did not roll its six dice against each of them",
        )
    }

    /** A throw halves it, which is what the burst allows and no more. */
    @Test
    fun `a throw is worth half of it`() {
        val before = standing(at = partyAt.copy(y = partyAt.y - 1))
        val after = before.scratched(dice = everyDieAtItsMost)

        val lost = (before.championIn(PartySlot(0))?.hitPoints?.current ?: 0) -
            (after.championIn(PartySlot(0))?.hitPoints?.current ?: 0)

        assertEquals(SIX_DICE_OF_SIX / 2, lost, "the throw did not halve it")
    }

    @Test
    fun `one going off on the party's own square catches them too`() {
        val after = standing(at = partyAt).scratched()

        assertTrue(
            after.bursting.single().inYourFace,
            "a burst on their own square was drawn as one at a distance",
        )
        assertTrue(
            (after.championIn(PartySlot(0))?.hitPoints?.current ?: 0) < HEARTY,
            "a spore going off underfoot left the party untouched",
        )
    }

    @Test
    fun `two squares off it is only a flash`() {
        val before = standing(at = partyAt.copy(y = partyAt.y - 2))
        val after = before.scratched()

        assertEquals(
            emptyList<MonsterInstance>(),
            after.monsters,
            "it should still have gone off, only harmlessly",
        )
        assertEquals(
            List(6) { HEARTY },
            (0 until 6).map { after.championIn(PartySlot(it))?.hitPoints?.current },
            "a spore two squares off reached the party",
        )
        assertEquals(
            before.monsters.single().location,
            after.bursting.single().at,
            "a distant burst was drawn anywhere but where it went off",
        )
    }

    /** Diagonally beside is still beside: the corners count as adjacent. */
    @Test
    fun `the square diagonally next to them is close enough`() {
        val after = standing(at = Location(partyAt.x - 1, partyAt.y - 1)).scratched()

        assertTrue(
            (after.championIn(PartySlot(0))?.hitPoints?.current ?: 0) < HEARTY,
            "a spore on the next corner was treated as out of reach",
        )
    }

    @Test
    fun `anything else is wounded rather than burst`() {
        val after = standing(at = partyAt.copy(y = partyAt.y - 1), kind = 1).scratched()

        assertEquals(
            19,
            after.monsters.single().hitPoints.current,
            "something with no burst in it went off anyway",
        )
        assertEquals(emptyList(), after.bursting, "it burst without the flag for it")
    }

    private fun kind(id: Int, capsFlags: Int) = MonsterProperty(
        id = id,
        armorClass = 9, hitChance = 13, level = 1,
        hpDcTimes = 0, hpDcPips = 0, hpDcBase = 1,
        attacksPerRound = 1, dmgDc = emptyList(),
        immunityFlags = 0, capsFlags = capsFlags, typeFlags = 2, experience = 120,
        size = MonsterSize.FILLS_THE_SQUARE,
        sound1 = 0, sound2 = 0,
        numRemoteAttacks = 0, remoteWeaponChangeMode = null, numRemoteWeapons = null,
        remoteWeapons = emptyList(), tuResist = -1, dmgModifierEvade = 0,
        decorations = emptyList(),
    )

    private companion object {
        const val IN_THE_PARTY = 0x01
        const val HEARTY = 200

        /** What the burst rolls, with every die showing its most. */
        const val SIX_DICE_OF_SIX = 36

        /** And with every die showing one. */
        const val SIX_DICE_OF_ONE = 6
    }
}
