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
import pl.pelotasplus.eyeofbeholder.data.model.Spell
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * That a blur reaches the dice, which is the one thing the store's own tests
 * cannot say.
 *
 * Everything else about the spell is arithmetic in a model; this is whether a
 * monster swinging at a blurred champion actually misses where it would have
 * hit. The roll is pinned rather than random, and the same swing is played
 * twice — once blurred and once not — so the only difference between the two
 * runs is the spell.
 */
@Category(NeedsGameData::class)
class WhatABlurTurnsAsideTest {

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

    private fun GameState.blurredAll(): GameState =
        (0 until 6).fold(this) { world, slot ->
            assertNotNull(
                world.spellBegunWhereItSettles(Spell.BLUR, PartySlot(slot), casterLevel = 9)
            )
        }

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
}
