package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Abilities
import pl.pelotasplus.eyeofbeholder.data.model.Ability
import pl.pelotasplus.eyeofbeholder.data.model.ArmorClass
import pl.pelotasplus.eyeofbeholder.data.model.Blow
import pl.pelotasplus.eyeofbeholder.data.model.CarrySlot
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Fighting
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypes
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonstersTurn
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.PortraitId
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
import pl.pelotasplus.eyeofbeholder.data.model.needsToHit
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemTypesRepositoryImpl
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
 * That the point a bless and a prayer are worth reaches the dice.
 *
 * What a spell adds to a number in a model is not what a player feels; a blow
 * that would have missed landing is. So the same swing is played at every face
 * of the twenty, once bare and once under the spell, and the faces whose
 * answer changed are counted — one point is worth exactly one face, and a
 * table read as two would show up here as two.
 *
 * The party stand on 13x9 of level 5 facing the two clerics on 13x8, who are
 * armour class 2. A first-level fighter needs 18 against them, so the face
 * that a point of help is worth is 17.
 */
@Category(NeedsGameData::class)
class WhatABlessAndAPrayerReachTheDiceTest {

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

    private val itemTypes: ItemTypes = runBlocking {
        ItemTypesRepositoryImpl(resources).loadItemTypes().getOrThrow()
    }

    private val kinds get() = level.subLevels[0].monsters

    private val clericArmorClass = 2

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

    private fun GameState.cast(spell: Spell) =
        assertNotNull(spellBegunWhereItSettles(spell, by = PartySlot(0), casterLevel = 9))

    // ---- the point the party strike with ---------------------------------

    private fun swingLands(from: GameState, face: Int): Boolean =
        Fighting(itemTypes, kinds, rolling(face))
            .strike(from, PartySlot(0), CarrySlot(0))
            .blow is Blow.Hit

    /** The faces that miss bare and land under [spell], which is what it bought. */
    private fun facesWon(spell: Spell): List<Int> {
        val under = world().cast(spell)
        return (1..20).filter { face -> !swingLands(world(), face) && swingLands(under, face) }
    }

    @Test
    fun `a bless is worth one face of the die to the party's own swing`() {
        assertEquals(listOf(champion().needsToHit(clericArmorClass) - 1), facesWon(Spell.BLESS))
    }

    @Test
    fun `and so is a prayer`() {
        assertEquals(listOf(champion().needsToHit(clericArmorClass) - 1), facesWon(Spell.PRAYER))
    }

    /** Read together they are worth two, each taken off the same roll in turn. */
    @Test
    fun `the two together are worth two faces`() {
        val both = world().cast(Spell.BLESS).cast(Spell.PRAYER)
        val needed = champion().needsToHit(clericArmorClass)

        assertEquals(
            listOf(needed - 2, needed - 1),
            (1..20).filter { face -> !swingLands(world(), face) && swingLands(both, face) },
        )
    }

    /** The face below is still a miss, which is what says the help is only one. */
    @Test
    fun `a blessed swing two short still misses`() {
        val blessed = world().cast(Spell.BLESS)

        assertIs<Blow.Missed>(
            Fighting(itemTypes, kinds, rolling(champion().needsToHit(clericArmorClass) - 2))
                .strike(blessed, PartySlot(0), CarrySlot(0))
                .blow
        )
    }

    // ---- the point taken off what strikes at them ------------------------

    private fun swungThrough(from: GameState, face: Int): MonstersTurn.Taken {
        val turn = MonstersTurn(kinds, rolling(face))
        var world = turn.begun(from).world
        world = world.swingsCarriedOn()
        return turn.landed(world, world.landingThisFrame)
    }

    private fun blowLands(from: GameState, face: Int) =
        swungThrough(from, face).struck.any { it.damage.points > 0 }

    /** The faces that land bare and are turned aside under [spell]. */
    private fun facesTurnedAside(spell: Spell): List<Int> {
        val under = world().cast(spell)
        return (1..20).filter { face -> blowLands(world(), face) && !blowLands(under, face) }
    }

    /**
     * A prayer is read on both sides of the fight, and this is the half a
     * bless does not have: one face of the die that would have found the party
     * no longer does.
     */
    @Test
    fun `a prayer turns aside the blow that would just have landed`() {
        assertEquals(1, facesTurnedAside(Spell.PRAYER).size)
    }

    /** And a bless turns nothing aside, however many are swinging. */
    @Test
    fun `a bless turns nothing aside`() {
        assertEquals(emptyList(), facesTurnedAside(Spell.BLESS))
    }

    /**
     * A prayer is taken off the roll, and a natural twenty is not read as a
     * number at all — so it lands on a prayed-over party as it lands on
     * anybody, rather than being quietly turned into a nineteen.
     */
    @Test
    fun `a natural twenty still lands on a prayed-over party`() {
        assertTrue(blowLands(world().cast(Spell.PRAYER), 20))
    }
}
