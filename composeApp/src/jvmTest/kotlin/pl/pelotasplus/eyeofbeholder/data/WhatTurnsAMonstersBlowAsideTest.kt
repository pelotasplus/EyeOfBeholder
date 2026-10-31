package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Abilities
import pl.pelotasplus.eyeofbeholder.data.model.Ability
import pl.pelotasplus.eyeofbeholder.data.model.ArmorClass
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonstersTurn
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.PortraitId
import pl.pelotasplus.eyeofbeholder.data.model.SettlesOn
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.SpellLasts
import pl.pelotasplus.eyeofbeholder.data.model.Ticks
import pl.pelotasplus.eyeofbeholder.data.model.TrackIndex
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * That the spells which make a champion harder to hit reach the dice, which
 * is the one thing the store's own tests cannot say.
 *
 * Everything else about them is arithmetic in a model; this is whether a
 * monster swinging at a champion under one actually misses where it would
 * have hit. The roll is pinned rather than random, and the same swing is
 * played twice — once under the spell and once not — so the only difference
 * between the two runs is the spell.
 *
 * Two of them do the same thing by the same amount: a blur and a protection
 * from evil are each worth two off the roll, and nothing about either asks
 * what is swinging.
 */
@Category(NeedsGameData::class)
class WhatTurnsAMonstersBlowAsideTest {

    private val resources = ResourceRepositoryImpl()

    private val level = runBlocking {
        InfRepositoryImpl(
            resourceRepository = resources,
            mazRepository = MazRepositoryImpl(resources),
            vmpRepository = VmpRepositoryImpl(resources),
            vcnRepository = VcnRepositoryImpl(resources),
            palRepository = PalRepositoryImpl(resources),
            cpsRepository = CpsRepositoryImpl(resources),
            decRepository = DecRepositoryImpl(resources),
        ).loadInf("LEVEL5.INF").getOrThrow()
    }

    private val kinds get() = level.subLevels[0].monsters

    private fun champion() = Champion(
        name = "Anselm",
        portrait = PortraitId(0),
        abilities = Abilities(strength = Ability(10, 10), dexterity = Ability(10, 10)),
        hitPoints = HitPoints(400, 400),
        armorClass = ArmorClass(10),
        food = Food(100),
        characterClass = CharacterClass.FIGHTER,
        levels = listOf(ClassLevel(1, XpPoints(0))),
        carrying = List(27) { ItemIndex(ItemIndex.NOTHING) },
        flags = ChampionFlags(1),
    )

    private val everyDieHighest = Dice { times, pips, modifier -> times * pips + modifier }

    /** Every twenty comes up [toHit]; everything else rolls its highest. */
    private fun rolling(toHit: Int) = Dice { times, pips, modifier ->
        if (times == 1 && pips == 20) toHit else times * pips + modifier
    }

    private fun world() = GameState(
        party = PartyState(Location(13, 9), Direction.NORTH),
        champions = List(6) { champion() },
    ).arrivingAt(
        level = 5,
        places = level.monsterInstances,
        maz = level.subLevels[0].maz,
        kinds = kinds,
        dice = everyDieHighest,
    ).rousedBy(MonsterSlot(16))

    private fun swungThrough(from: GameState, dice: Dice): MonstersTurn.Taken {
        val turn = MonstersTurn(kinds, dice)
        var world = turn.begun(from).world
        world = world.swingsCarriedOn()
        return turn.landed(world, world.landingThisFrame)
    }

    private fun GameState.allUnder(spell: Spell): GameState =
        (0 until 6).fold(this) { world, slot ->
            assertNotNull(
                world.spellBegunWhereItSettles(spell, PartySlot(slot), casterLevel = 9)
            )
        }

    private fun GameState.blurredAll(): GameState = allUnder(Spell.BLUR)

    /**
     * The roll that is the whole point: one that lands by exactly two, so
     * taking two off it turns a hit into a miss and nothing else changes.
     *
     * Found by playing the same swing at every face of the die rather than
     * assumed, because what a monster needs depends on its own table.
     */
    private fun theRollThatLandsByTwo(): Int? = (2..19).firstOrNull { face ->
        val hitsBare = swungThrough(world(), rolling(face)).struck.any { it.damage.points > 0 }
        val hitsBlurred = swungThrough(world().blurredAll(), rolling(face)).struck
            .any { it.damage.points > 0 }

        hitsBare && !hitsBlurred
    }

    @Test
    fun `a blur turns aside a blow that would just have landed`() {
        val face = assertNotNull(
            theRollThatLandsByTwo(),
            "no face of the die both lands bare and misses blurred",
        )

        val bare = swungThrough(world(), rolling(face))
        val blurred = swungThrough(world().blurredAll(), rolling(face))

        assertTrue(bare.struck.any { it.damage.points > 0 }, "the bare swing should have landed")
        assertTrue(
            blurred.struck.none { it.damage.points > 0 },
            "the blurred swing should have been turned aside",
        )
    }

    /**
     * A natural twenty lands on a blurred champion as it lands on anybody.
     *
     * Blur is taken off the roll, and the roll of twenty is not read as a
     * number at all — which is why it has to be exempted rather than left to
     * arithmetic that would quietly turn it into an eighteen.
     */
    @Test
    fun `a natural twenty still lands on a blurred champion`() {
        val blurred = swungThrough(world().blurredAll(), rolling(20))

        assertTrue(blurred.struck.any { it.damage.points > 0 })
    }

    /** And a blur on one champion does nothing for the champion beside them. */
    @Test
    fun `it protects only whoever carries it`() {
        val one = assertNotNull(
            world().spellBegunWhereItSettles(Spell.BLUR, PartySlot(0), casterLevel = 9)
        )

        assertEquals(2, one.running.hindranceStriking(PartySlot(0)))
        assertEquals(0, one.running.hindranceStriking(PartySlot(1)))
    }

    // --- and the cleric's half of the same thing ------------------------------

    /**
     * A protection from evil is the same two off the roll, which is worth
     * proving at the dice rather than in the model: the blow that lands bare
     * misses under it, as it does under a blur.
     */
    @Test
    fun `a protection from evil turns aside the same blow a blur does`() {
        val face = assertNotNull(
            theRollThatLandsByTwo(),
            "no face of the die both lands bare and misses blurred",
        )

        val bare = swungThrough(world(), rolling(face))
        val protected = swungThrough(world().allUnder(Spell.PROTECTION_FROM_EVIL), rolling(face))

        assertTrue(bare.struck.any { it.damage.points > 0 }, "the bare swing should have landed")
        assertTrue(
            protected.struck.none { it.damage.points > 0 },
            "the protected swing should have been turned aside",
        )
    }

    /** And a twenty goes through it, as it goes through everything. */
    @Test
    fun `a natural twenty still lands on a protected champion`() {
        val protected = swungThrough(world().allUnder(Spell.PROTECTION_FROM_EVIL), rolling(20))

        assertTrue(protected.struck.any { it.damage.points > 0 })
    }

    /**
     * The two of them add rather than one winning: they are taken one after
     * another off the same roll, so a champion carrying both is four harder
     * to hit and not two.
     */
    @Test
    fun `a blur and a protection from evil add`() {
        val both = world()
            .allUnder(Spell.BLUR)
            .allUnder(Spell.PROTECTION_FROM_EVIL)

        assertEquals(4, both.running.hindranceStriking(PartySlot(0)))
    }

    /**
     * The wider one is the same spell over all six: the same two off the
     * roll, for the same length of time, on each of them separately.
     *
     * Separately and not over the party, so each can be dispelled on its own
     * — which is why one casting makes six running spells and not one.
     */
    @Test
    fun `the ten foot one reaches all six and the other reaches one`() {
        val wide = assertNotNull(
            world().spellBegunWhereItSettles(
                Spell.PROTECTION_FROM_EVIL_TEN_FEET,
                PartySlot(0),
                casterLevel = 9,
            )
        )

        (0 until 6).forEach { slot ->
            assertEquals(2, wide.running.hindranceStriking(PartySlot(slot)), "slot $slot")
        }
        assertEquals(6, wide.running.all.count { it.spell == Spell.PROTECTION_FROM_EVIL_TEN_FEET })

        assertEquals(Spell.PROTECTION_FROM_EVIL.lasts, Spell.PROTECTION_FROM_EVIL_TEN_FEET.lasts)
        assertEquals(TrackIndex(110), Spell.PROTECTION_FROM_EVIL_TEN_FEET.heardAs)
    }

    /**
     * Three half-minutes a level and laid on whoever is pointed at, where a
     * blur is three and one a level on whoever read it — so the cleric's is
     * the shorter of the two at first level and much the longer after that.
     */
    @Test
    fun `it lasts three half-minutes a level, laid on whoever is pointed at`() {
        val lasts = assertIs<SpellLasts>(Spell.PROTECTION_FROM_EVIL.lasts)

        assertEquals(SpellLasts(base = 0, perLevel = 3), lasts)
        assertEquals(Ticks(1638), lasts.castBySomeoneOfLevel(1))
        assertEquals(Ticks(14742), lasts.castBySomeoneOfLevel(9))

        assertEquals(SettlesOn.WHOEVER_IS_POINTED_AT, Spell.PROTECTION_FROM_EVIL.settlesOn)
        assertEquals(SettlesOn.WHOEVER_CAST_IT, Spell.BLUR.settlesOn)
        assertEquals(TrackIndex(110), Spell.PROTECTION_FROM_EVIL.heardAs)
    }
}
