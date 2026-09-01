package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSpell
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.WhatABlowLeaves
import pl.pelotasplus.eyeofbeholder.data.model.WhereASpellLands
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What each of a beholder's four rays, and a cleric's hold person, does to the
 * party where it comes down.
 *
 * The numbers are the game's own: three hundred for the two that kill, three
 * dice of eight and three over for the one that wounds, a die of four for how
 * many a spell takes, and the eighth level for what the death spell will not
 * touch. None of them is worked out from anything.
 *
 * Two of them do nothing anybody would guess. See the tests for those.
 */
class WhatASpellDoesOnArrivalTest {

    /** Every die at its most, which also passes every saving throw. */
    private val everyDieAtItsMost = Dice { times, pips, modifier -> times * pips + modifier }

    /** And at its least, which fails every saving throw. */
    private val everyDieAtItsLeast = Dice { times, _, modifier -> times + modifier }

    private fun aChampion(level: Int = 5, hp: Int = 60) = Champion.NOBODY.copy(
        name = "One",
        flags = ChampionFlags(IN_THE_PARTY),
        hitPoints = HitPoints(hp, hp),
        levels = listOf(ClassLevel(level, XpPoints(0))),
    )

    private fun world(party: List<Champion> = List(6) { aChampion() }) = GameState(
        party = PartyState(Location(3, 11), Direction.NORTH),
        champions = party,
    )

    private fun landing(dice: Dice) = WhereASpellLands(dice)

    private fun GameState.standing() = champions.count { !it.dead }

    private fun GameState.hurt(before: GameState) =
        champions.indices.filter { champions[it].hitPoints.current < before.champions[it].hitPoints.current }

    // --- the ones that kill ---------------------------------------------------

    /**
     * Disintegrate has a saving throw in the original which is never rolled:
     * what it asks for is halved as a whole number before anything is thrown,
     * and one halved is none, so it always reads as a failure. It kills every
     * time, and the best roll a champion could make does not change it.
     */
    @Test
    fun `disintegrate always kills, however well the dice fall`() {
        val before = world()

        val after = landing(everyDieAtItsMost).of(MonsterSpell.MONSTER_DISINTEGRATE, before)

        assertEquals(1, after.hurt.size, "it took more than one of them, or none")
        assertEquals(5, after.world.standing(), "the one it reached is still standing")
    }

    /**
     * And flesh to stone, asking for the same throw with a two, is always read
     * as having been saved — so it walks the whole party, finds every one of
     * them saved, and stops. It has never turned anybody to stone.
     */
    @Test
    fun `flesh to stone does nothing at all, ever`() {
        val before = world()

        val after = landing(everyDieAtItsLeast).of(MonsterSpell.MONSTER_FLESH_TO_STONE, before)

        assertEquals(emptyList(), after.left, "somebody was turned to stone")
        assertEquals(emptyList(), after.hurt, "somebody was hurt by it")
        assertTrue(
            after.world.champions.none { it.petrified },
            "the spell that never works worked",
        )
    }

    /** The death spell passes over anybody of the eighth level or better. */
    @Test
    fun `the death spell will not touch a champion who has come far enough`() {
        val seasoned = world(List(6) { aChampion(level = 8) })

        val after = landing(everyDieAtItsMost).of(MonsterSpell.MONSTER_DEATH_SPELL, seasoned)

        assertEquals(emptyList(), after.hurt, "it killed somebody it should have passed over")
    }

    /** And takes up to a die of four of those it will. */
    @Test
    fun `and takes as many of the rest as its die says`() {
        val before = world(List(6) { aChampion(level = 7) })

        val most = landing(everyDieAtItsMost).of(MonsterSpell.MONSTER_DEATH_SPELL, before)
        val least = landing(everyDieAtItsLeast).of(MonsterSpell.MONSTER_DEATH_SPELL, before)

        assertEquals(4, most.hurt.size, "a die of four at its most is four of them")
        assertEquals(1, least.hurt.size, "and at its least is one")
    }

    // --- the one that only wounds ---------------------------------------------

    /** Three dice of eight and three over, thrown against nothing. */
    @Test
    fun `cause critical wounds takes three of eight and three over from one of them`() {
        val before = world()

        val after = landing(everyDieAtItsMost)
            .of(MonsterSpell.MONSTER_CAUSE_CRITICAL_WOUNDS, before)

        val whose = after.hurt.single()
        assertEquals(
            3 * 8 + 3,
            before.champions[whose.index].hitPoints.current -
                after.world.champions[whose.index].hitPoints.current,
        )
    }

    // --- and the one that holds ------------------------------------------------

    /**
     * Hold person leaves the same paralysis a monster's grip does, and is
     * thrown against a spell rather than against petrification — so a party
     * who shrug off a ghoul are not thereby safe from a cleric.
     */
    @Test
    fun `hold person paralyses as many as its die says, where they fail the throw`() {
        val before = world()

        // The same die decides how many it reaches and whether each of them
        // shrugs it off, so the lowest roll is one champion who fails.
        val after = landing(everyDieAtItsLeast).of(MonsterSpell.HOLD_PERSON, before)

        assertEquals(1, after.left.size, "a die of four at its least is one of them")
        assertTrue(after.left.all { it.what == WhatABlowLeaves.PARALYSIS })
        assertEquals(
            1,
            after.world.champions.count { it.paralysed },
            "it was announced without taking hold",
        )
    }

    /** A throw made is a spell shrugged off, and nothing is left behind. */
    @Test
    fun `and leaves nothing on anybody who makes the throw`() {
        val before = world()

        val after = landing(everyDieAtItsMost).of(MonsterSpell.HOLD_PERSON, before)

        assertEquals(emptyList(), after.left, "a champion who saved was held anyway")
        assertFalse(after.world.champions.any { it.paralysed })
    }

    // --- and the ones not written yet -----------------------------------------

    /**
     * The ones that only deal damage want the game's damage table, saving
     * throws and all, and none of it is written. They must do nothing rather
     * than something invented.
     */
    @Test
    fun `a spell with no effect written takes nothing off anybody`() {
        val before = world()

        listOf(
            MonsterSpell.FLAME_STRIKE,
            MonsterSpell.MONSTER_FIREBALL,
            MonsterSpell.MONSTER_LESSER_FIREBALL,
            MonsterSpell.MAGIC_MISSILE,
            MonsterSpell.FIREBALL,
            MonsterSpell.LIGHTNING_BOLT,
        ).forEach { spell ->
            val after = landing(everyDieAtItsMost).of(spell, before)
            assertEquals(emptyList(), after.hurt, "$spell hurt somebody by a number from nowhere")
        }
    }

    private companion object {
        const val IN_THE_PARTY = 0x01
    }
}
