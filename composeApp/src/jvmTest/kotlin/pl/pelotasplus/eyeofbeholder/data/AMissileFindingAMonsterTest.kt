package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Abilities
import pl.pelotasplus.eyeofbeholder.data.model.Ability
import pl.pelotasplus.eyeofbeholder.data.model.ArmorClass
import pl.pelotasplus.eyeofbeholder.data.model.CarrySlot
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.CountedBy
import pl.pelotasplus.eyeofbeholder.data.model.DamageDice
import pl.pelotasplus.eyeofbeholder.data.model.HarmKind
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Flight
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterImmunities
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.PortraitId
import pl.pelotasplus.eyeofbeholder.data.model.Projectile
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.WhereASpellLands
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
 * A magic missile crossing the room and coming down on a monster.
 *
 * The numbers are the spell's own: one die of four and one over, counted once
 * for the caster and once more for every two levels past the first, and the
 * whole roll multiplied rather than rolled again for each count — so a mage
 * throwing three missiles throws three of one size.
 *
 * A missile is not aimed. Nothing in the game misses less: there is no roll to
 * land it and no throw against it, and the only thing that turns it aside is a
 * creature that shrugs off magic itself.
 */
@Category(NeedsGameData::class)
class AMissileFindingAMonsterTest {

    private val resources = ResourceRepositoryImpl()

    private val level: Inf = runBlocking {
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

    private val target = MonsterSlot(0)
    private val onThem = Location(13, 8)

    /** Every die comes up its highest, so 1d4+1 is five. */
    private val alwaysTheMost = Dice { times, pips, modifier -> times * pips + modifier }

    /** And its lowest, so 1d4+1 is two. */
    private val alwaysTheLeast = Dice { times, _, modifier -> times + modifier }

    // --- what the spell says it is -------------------------------------------

    @Test
    fun `the spell throws something, and it is the missile bolt`() {
        val thrown = assertNotNull(Spell.MAGIC_MISSILE.throws, "it throws nothing")

        assertEquals(DamageDice(times = 1, pips = 4, base = 1), thrown.dealing)
        assertEquals(setOf(HarmKind.MAGIC), thrown.hurting)
    }

    /**
     * One missile up to the third level, two from the fifth, and one more
     * every second level after — with a floor of one, so the newest mage still
     * throws something.
     */
    @Test
    fun `how many missiles a caster throws`() {
        val counted = CountedBy.EVERY_SECOND_LEVEL

        assertEquals(1, counted.forACasterOf(1))
        assertEquals(1, counted.forACasterOf(2))
        assertEquals(1, counted.forACasterOf(3))
        assertEquals(2, counted.forACasterOf(5))
        assertEquals(3, counted.forACasterOf(7))
        assertEquals(4, counted.forACasterOf(9))
    }

    // --- and what it does where it comes down --------------------------------

    @Test
    fun `a missile takes hit points off what it comes down on`() {
        val hurt = struck(casterLevel = 1, dice = alwaysTheMost)

        assertEquals(listOf(target), hurt.map { it.slot })
        assertEquals(5, hurt.single().by.points, "one die of four and one over")
    }

    /** The roll is counted once per pair of levels, not rolled again for each. */
    @Test
    fun `a practised caster throws the same missile more times`() {
        assertEquals(10, struck(casterLevel = 5, dice = alwaysTheMost).single().by.points)
        assertEquals(15, struck(casterLevel = 7, dice = alwaysTheMost).single().by.points)
    }

    /** And the whole of a low roll is counted too, so the multiplying is real. */
    @Test
    fun `a low roll is counted just as many times`() {
        assertEquals(2, struck(casterLevel = 1, dice = alwaysTheLeast).single().by.points)
        assertEquals(6, struck(casterLevel = 7, dice = alwaysTheLeast).single().by.points)
    }

    /**
     * Nothing is rolled to land it. A first-level caster would need eighteen
     * to reach these clerics with a weapon and cannot fail to reach them with
     * this, however badly the dice are running.
     */
    @Test
    fun `a missile never misses`() {
        assertTrue(struck(casterLevel = 1, dice = alwaysTheLeast).isNotEmpty())
    }

    // --- unless the thing is not troubled by magic ----------------------------

    /**
     * A creature that shrugs off magic itself takes nothing, and stops the
     * missile all the same: it is spent on what it could not hurt rather than
     * carrying through to whatever stands behind.
     */
    @Test
    fun `something that shrugs off magic takes nothing from it`() {
        val hurt = struck(
            casterLevel = 7,
            dice = alwaysTheMost,
            immunities = MonsterImmunities(SHRUGS_OFF_MAGIC),
        )

        assertEquals(listOf(target), hurt.map { it.slot }, "the missile went past it")
        assertEquals(0, hurt.single().by.points)
    }

    /** An immunity to something else does not help against this one. */
    @Test
    fun `shrugging off cold is no help against a missile`() {
        val hurt = struck(
            casterLevel = 1,
            dice = alwaysTheMost,
            immunities = MonsterImmunities(SHRUGS_OFF_COLD),
        )

        assertEquals(5, hurt.single().by.points)
    }

    // --- and it knows whose it is ---------------------------------------------

    /**
     * A missile passes through the party who cast it. Walking into your own is
     * walking into your own: it costs nothing and it does not even stop.
     *
     * Two of the party's spells are not like this — a fireball and a bolt of
     * lightning take either side — so the rule is the projectile's rather than
     * every spell's.
     */
    @Test
    fun `the party are not hurt by their own missile`() {
        val moved = walkedInto(takesEitherSide = false)

        assertEquals(emptyList(), moved.hurt, "their own missile turned on them")
    }

    /** And one that does take either side finds them where they stand. */
    @Test
    fun `a spell that takes either side does hurt them`() {
        val moved = walkedInto(takesEitherSide = true)

        assertTrue(
            moved.hurt.any { it is Flight.Hurt.AChampion },
            "a spell that spares nobody spared them",
        )
    }

    // --- the fixture ---------------------------------------------------------

    /** The party standing on the square their own spell is crossing. */
    private fun walkedInto(takesEitherSide: Boolean): Flight.Moved {
        val standing = Location(13, 9)
        val thrown = Spell.MAGIC_MISSILE.throws ?: error("magic missile throws nothing")

        val world = GameState(
            party = PartyState(standing, Direction.NORTH),
            champions = listOf(fighter(), fighter()),
            inFlight = listOf(
                Projectile(
                    what = null,
                    at = standing,
                    place = PartySlot(0).standsIn.onASquareFacing(Direction.NORTH),
                    going = Direction.NORTH,
                    squaresLeft = thrown.flies.reach,
                    thrownBy = Projectile.Thrower.AChampion(PartySlot(0)),
                    harm = thrown.dealtBy(9).copy(takesEitherSide = takesEitherSide),
                    spell = thrown.flies,
                    leaving = false,
                ),
            ),
        )

        return Flight(
            sublevel = level.subLevels[0],
            level = 5,
            kinds = level.subLevels[0].monsters,
            dice = alwaysTheMost,
            landing = WhereASpellLands(dice = alwaysTheMost, level = 5),
        ).onward(world)
    }

    private fun fighter() = Champion(
        name = "Anselm",
        portrait = PortraitId(0),
        abilities = Abilities(
            strength = Ability(10, 10),
            dexterity = Ability(10, 10),
        ),
        hitPoints = HitPoints(90, 90),
        armorClass = ArmorClass(10),
        food = Food(100),
        characterClass = CharacterClass.FIGHTER,
        levels = listOf(ClassLevel(9, XpPoints(0))),
        carrying = List(27) { ItemIndex(ItemIndex.NOTHING) },
        flags = ChampionFlags(1),
    )


    private fun struck(
        casterLevel: Int,
        dice: Dice,
        immunities: MonsterImmunities = MonsterImmunities(0),
    ): List<Flight.Hurt.AMonster> {
        val thrown = Spell.MAGIC_MISSILE.throws ?: error("magic missile throws nothing")

        val standing = MonsterInstance(
            index = target,
            unit = 0,
            location = onThem,
            place = SquarePlace.MIDDLE,
            direction = Direction.SOUTH,
            type = MonsterTypeId(0),
            gfxIndex = 0,
            mode = 0,
            pause = 0,
            weapon = 0,
            pocketItem = 0,
            hitPoints = HitPoints(90, 90),
        )

        val world = GameState(
            party = PartyState(Location(13, 9), Direction.NORTH),
            monsters = listOf(standing),
            inFlight = listOf(
                // Already over their square rather than still leaving the
                // caster's, which is the beat this test is about.
                Projectile(
                    what = null,
                    at = onThem,
                    place = SquarePlace.MIDDLE,
                    going = Direction.NORTH,
                    squaresLeft = thrown.flies.reach,
                    thrownBy = Projectile.Thrower.AChampion(PartySlot(0)),
                    harm = thrown.dealtBy(casterLevel),
                    spell = thrown.flies,
                    leaving = false,
                ),
            ),
        )

        return Flight(
            sublevel = level.subLevels[0],
            level = 5,
            kinds = listOf(level.subLevels[0].monsters.first().copy(immunities = immunities)),
            dice = dice,
        ).onward(world)
            .hurt
            .filterIsInstance<Flight.Hurt.AMonster>()
    }

    private companion object {
        /** The bit that turns aside anything magical, which is every spell. */
        const val SHRUGS_OFF_MAGIC = 0x10

        const val SHRUGS_OFF_COLD = 0x80
    }
}
