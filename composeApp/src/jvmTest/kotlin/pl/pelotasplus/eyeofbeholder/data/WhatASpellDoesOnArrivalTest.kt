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
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
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
     * Both of these are save-or-lose-a-champion, and the throw is the one a
     * spell asks for rather than the one paralysis uses — so hardiness against
     * a rod is no help, and the levels a champion has earned are.
     */
    @Test
    fun `disintegrate kills the one it finds, where the throw is failed`() {
        val before = world()

        val after = landing(everyDieAtItsLeast).of(MonsterSpell.MONSTER_DISINTEGRATE, before)

        assertEquals(1, after.hurt.size, "it took more than one of them, or none")
        assertEquals(5, after.world.standing(), "the one it reached is still standing")
    }

    /** And a champion who makes the throw walks away from it. */
    @Test
    fun `and takes nobody who makes it`() {
        val before = world()

        val after = landing(everyDieAtItsMost).of(MonsterSpell.MONSTER_DISINTEGRATE, before)

        assertEquals(emptyList(), after.hurt, "the throw was made and they died anyway")
        assertEquals(6, after.world.standing())
    }

    /** Flesh to stone takes the first who fails, and stone is on no clock. */
    @Test
    fun `flesh to stone turns the first one who fails the throw`() {
        val before = world()

        val after = landing(everyDieAtItsLeast).of(MonsterSpell.MONSTER_FLESH_TO_STONE, before)

        assertEquals(1, after.left.size, "it took more than one of them, or none")
        assertEquals(WhatABlowLeaves.PETRIFICATION, after.left.single().what)
        assertEquals(1, after.world.champions.count { it.petrified })
    }

    /** And leaves a party who all make it standing. */
    @Test
    fun `and leaves a party who all make the throw alone`() {
        val before = world()

        val after = landing(everyDieAtItsMost).of(MonsterSpell.MONSTER_FLESH_TO_STONE, before)

        assertEquals(emptyList(), after.left, "somebody was turned to stone anyway")
        assertTrue(after.world.champions.none { it.petrified })
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

    /**
     * The number on the portrait is the whole of what the blow cost, however
     * big. It is the only thing on screen that explains a champion dropping
     * to one hit, so a number too large to draw is worth drawing anyway.
     */
    @Test
    fun `a killing three hundred shows all three hundred`() {
        val before = world()

        val after = landing(everyDieAtItsLeast)
            .of(MonsterSpell.MONSTER_DISINTEGRATE, before).world

        val whose = PartySlot(after.champions.indexOfFirst { it.dead })
        assertEquals(300, after.damageShownOn(whose)?.points, "it drew the byte, which is 44")
        assertTrue(after.champions[whose.index].deadForGood, "three hundred did not kill")
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

    // --- the ones that burn the lot of you ------------------------------------

    /**
     * These take the whole party rather than picking somebody, and each of
     * them is rolled for and thrown for separately.
     */
    @Test
    fun `flame strike takes six of eight off everybody`() {
        val before = world()

        val after = landing(everyDieAtItsLeast).of(MonsterSpell.FLAME_STRIKE, before)

        assertEquals(6, after.hurt.size, "it did not reach the whole party")
        assertEquals(
            6,
            before.champions[0].hitPoints.current - after.world.champions[0].hitPoints.current,
            "six dice at their least is six",
        )
    }

    /** A throw made halves it. There is nowhere to stand that is out of it. */
    @Test
    fun `and a champion who makes the throw takes half, not none`() {
        val before = world()

        val after = landing(everyDieAtItsMost).of(MonsterSpell.FLAME_STRIKE, before)

        assertEquals(6, after.hurt.size, "somebody dodged it altogether")
        assertEquals(
            6 * 8 / 2,
            before.champions[0].hitPoints.current - after.world.champions[0].hitPoints.current,
        )
    }

    /** The hell hounds' one rolls nothing at all: eighteen, every time. */
    @Test
    fun `the lesser fireball is a flat eighteen`() {
        val before = world()

        val most = landing(everyDieAtItsMost)
            .of(MonsterSpell.MONSTER_LESSER_FIREBALL, before).world
        val least = landing(everyDieAtItsLeast)
            .of(MonsterSpell.MONSTER_LESSER_FIREBALL, before).world

        val was = before.champions[0].hitPoints.current
        assertEquals(18 / 2, was - most.champions[0].hitPoints.current, "the throw was made")
        assertEquals(18, was - least.champions[0].hitPoints.current, "and here it was not")
    }

    // --- the three a mage throws ----------------------------------------------

    /**
     * These three scale with how practised the caster is, and no monster says
     * how practised it is — the floor answers, and answers what it answers for
     * a trap: five above the seventh, nine from there down. So a mage on the
     * fifteenth throws at nine.
     */
    @Test
    fun `a fireball rolls a die of six for every level of whatever threw it`() {
        val before = world()

        val after = deepLanding(everyDieAtItsLeast).of(MonsterSpell.FIREBALL, before)

        assertEquals(6, after.hurt.size, "it did not reach the whole party")
        assertEquals(
            9,
            before.champions[0].hitPoints.current - after.world.champions[0].hitPoints.current,
            "a one rolled nine times over is nine",
        )
    }

    /**
     * A lightning bolt does the same but picks one champion, chosen by the
     * quarter it came down on rather than by who stands in front.
     */
    @Test
    fun `a lightning bolt takes one of them, not the party`() {
        val before = world()

        val after = deepLanding(everyDieAtItsLeast)
            .of(MonsterSpell.LIGHTNING_BOLT, before, SquarePlace.NORTH_WEST)

        assertEquals(1, after.hurt.size, "it took the whole party")
    }

    /**
     * And a missile cannot be thrown off at all — the only thing in the game
     * that allows nothing against it. One missile for every two levels, and
     * the same at the best roll a champion can make as at the worst.
     */
    @Test
    fun `nothing is thrown against a magic missile`() {
        val before = world()

        val saved = deepLanding(everyDieAtItsMost)
            .of(MonsterSpell.MAGIC_MISSILE, before, SquarePlace.NORTH_WEST)

        assertEquals(1, saved.hurt.size)
        assertEquals(
            (4 + 1) * 4,
            before.champions[0].hitPoints.current -
                saved.world.champions[saved.hurt.single().index].hitPoints.current,
            "the best throw in the world took something off it",
        )
    }

    /** Deep enough that whatever throws these is at its most practised. */
    private fun deepLanding(dice: Dice) = WhereASpellLands(dice, level = 15)

    private companion object {
        const val IN_THE_PARTY = 0x01
    }
}
