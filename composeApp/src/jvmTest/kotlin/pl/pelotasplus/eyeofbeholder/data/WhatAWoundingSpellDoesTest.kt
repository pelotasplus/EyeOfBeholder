package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.DamageDice
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
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
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.Wounding
import pl.pelotasplus.eyeofbeholder.data.model.Wounds
import pl.pelotasplus.eyeofbeholder.data.repository.OriginalSaveRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The cleric's five wounding spells, on the quick start party: Pericles stands
 * in the front rank, Wolfspirit behind him.
 *
 * The dice are told what to roll, so what each test expects is the spell's
 * own dice read at that roll: cause light wounds is 1d8, harm leaves the
 * creature 1d4 short of its whole, slay living kills or deals 2d8+1.
 */
@Category(NeedsGameData::class)
class WhatAWoundingSpellDoesTest {

    private val party = runBlocking {
        OriginalSaveRepositoryImpl(ResourceRepositoryImpl())
            .loadOriginalSave(OriginalSaveRepositoryImpl.QUICK_START)
            .getOrThrow()
            .party
    }

    private val pericles = PartySlot(0)
    private val wolfspirit = PartySlot(2)

    private val standing = Location(10, 10)
    private val ahead = Location(10, 9)

    /**
     * Every throw of a twenty-sided die comes up [d20] — the roll to hit and
     * the creature's throw alike; a hundred-sided one comes up a hundred, so
     * nothing dodges; anything else rolls its highest.
     */
    private fun rolling(d20: Int) = Dice { times, pips, modifier ->
        when (pips) {
            20 -> d20
            100 -> 100
            else -> times * pips + modifier
        }
    }

    private fun aCreature(at: Location = ahead, hitPoints: Int = 30) = MonsterInstance(
        index = MonsterSlot(0),
        unit = 0,
        location = at,
        place = SquarePlace.MIDDLE,
        direction = Direction.SOUTH,
        type = MonsterTypeId(0),
        gfxIndex = 0,
        mode = MonsterMode.HUNTING.asWritten,
        pause = 0,
        weapon = 0,
        pocketItem = 0,
        hitPoints = HitPoints(hitPoints, hitPoints),
    )

    private fun aKind(immunities: Int = 0) = MonsterProperty(
        id = 0,
        armorClass = 10,
        hitChance = 15,
        level = 5,
        hpDcTimes = 2,
        hpDcPips = 8,
        hpDcBase = 0,
        attacksPerRound = 1,
        dmgDc = listOf(DamageDice(1, 4, 0)),
        immunities = MonsterImmunities(immunities),
        capsFlags = 3,
        typeFlags = 0,
        experience = 0,
        size = MonsterSize.TWO_TO_A_SQUARE,
        sound1 = 0,
        sound2 = 0,
        numRemoteAttacks = 0,
        remoteWeaponChangeMode = null,
        numRemoteWeapons = null,
        remoteWeapons = emptyList(),
        tuResist = -1,
        dmgModifierEvade = 0,
        decorations = emptyList(),
    )

    private fun world(vararg monsters: MonsterInstance) = GameState(
        party = PartyState(standing, Direction.NORTH),
        champions = party,
        monsters = monsters.toList(),
    )

    private fun touch(
        spell: Spell,
        whose: PartySlot = pericles,
        d20: Int = HITS_BUT_FAILS_ITS_THROW,
        immunities: Int = 0,
        world: GameState = world(aCreature()),
    ) = Wounds(kinds = listOf(aKind(immunities)), dice = rolling(d20)).touchBy(world, whose, requireNotNull(spell.wounds))

    private val GameState.creatureHitPoints get() = monsters.single().hitPoints.current

    // --- the touch ----------------------------------------------------------

    @Test
    fun `cause light wounds lands for a die of eight`() {
        val dealt = touch(Spell.CAUSE_LIGHT_WOUNDS)

        assertEquals(Wounds.CameTo.LANDED, dealt.came)
        assertEquals(30 - 8, dealt.world.creatureHitPoints)
    }

    @Test
    fun `serious is two dice and one, critical three and three`() {
        assertEquals(30 - 17, touch(Spell.CAUSE_SERIOUS_WOUNDS).world.creatureHitPoints)
        assertEquals(30 - 27, touch(Spell.CAUSE_CRITICAL_WOUNDS).world.creatureHitPoints)
    }

    @Test
    fun `harm leaves a creature a few short of nothing`() {
        // its whole, less a d4 that rolls four
        assertEquals(4, touch(Spell.HARM).world.creatureHitPoints)
    }

    @Test
    fun `from the back rank it does not reach`() {
        val dealt = touch(Spell.CAUSE_LIGHT_WOUNDS, whose = wolfspirit)

        assertEquals(Wounds.CameTo.NOT_IN_THE_FRONT_RANK, dealt.came)
        assertEquals(30, dealt.world.creatureHitPoints)
    }

    @Test
    fun `with nothing ahead there is nothing to touch`() {
        assertEquals(Wounds.CameTo.NOTHING_THERE, touch(Spell.CAUSE_LIGHT_WOUNDS, world = world()).came)
    }

    /** It rolls to hit as a blow does, and a miss says nothing and does nothing. */
    @Test
    fun `a touch can miss`() {
        val dealt = touch(Spell.CAUSE_LIGHT_WOUNDS, d20 = 1)

        assertEquals(Wounds.CameTo.CAME_TO_NOTHING, dealt.came)
        assertEquals(30, dealt.world.creatureHitPoints)
    }

    @Test
    fun `a creature this magic passes by takes none of it`() {
        assertEquals(30, touch(Spell.CAUSE_CRITICAL_WOUNDS, immunities = UNTOUCHED_BY_MAGIC).world.creatureHitPoints)
    }

    @Test
    fun `one that shrugs off weak weapons takes half`() {
        assertEquals(30 - 27 / 2, touch(Spell.CAUSE_CRITICAL_WOUNDS, immunities = WEAK_WEAPONS_SHRUGGED_OFF).world.creatureHitPoints)
    }

    /** The creature's throw is made and changes nothing: the wound is whole either way. */
    @Test
    fun `the creature's throw makes no difference`() {
        // a twenty both hits and makes the throw
        assertEquals(30 - 8, touch(Spell.CAUSE_LIGHT_WOUNDS, d20 = 20).world.creatureHitPoints)
    }

    // --- slay living --------------------------------------------------------

    private fun slay(
        world: GameState,
        d20: Int = HITS_BUT_FAILS_ITS_THROW,
        immunities: Int = 0,
        reachable: (Location) -> Boolean = { true },
    ) = Wounds(kinds = listOf(aKind(immunities)), dice = rolling(d20)).slainBy(world, wolfspirit, reachable)

    /** From the back rank as well, and two squares off. */
    @Test
    fun `slay living kills a creature two squares off`() {
        val dealt = slay(world(aCreature(at = Location(10, 8))))

        assertEquals(Wounds.CameTo.LANDED, dealt.came)
        assertTrue(dealt.world.monsters.isEmpty(), "it lived")
    }

    @Test
    fun `one that makes its throw takes two dice and one instead`() {
        assertEquals(30 - 17, slay(world(aCreature()), d20 = 20).world.creatureHitPoints)
    }

    @Test
    fun `a kind no spell kills outright is untouched`() {
        val dealt = slay(world(aCreature()), immunities = NOT_SLAIN_BY_MAGIC)

        assertEquals(Wounds.CameTo.CAME_TO_NOTHING, dealt.came)
        assertEquals(30, dealt.world.creatureHitPoints)
    }

    /** A square the party could not see onto stops it there, whatever is behind. */
    @Test
    fun `a wall between stops it`() {
        val dealt = slay(world(aCreature(at = Location(10, 8))), reachable = { it != Location(10, 8) })

        assertEquals(Wounds.CameTo.NOTHING_THERE, dealt.came)
        assertEquals(30, dealt.world.creatureHitPoints)
    }

    private companion object {
        /** Enough to land a blow on armour class ten, and too little for a fifth-level throw. */
        const val HITS_BUT_FAILS_ITS_THROW = 10

        const val NOT_SLAIN_BY_MAGIC = 0x8
        const val UNTOUCHED_BY_MAGIC = 0x10
        const val WEAK_WEAPONS_SHRUGGED_OFF = 0x2000
    }
}
