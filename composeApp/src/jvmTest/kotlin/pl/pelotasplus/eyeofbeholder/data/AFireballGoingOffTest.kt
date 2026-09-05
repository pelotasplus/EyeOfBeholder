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
import pl.pelotasplus.eyeofbeholder.data.model.CountedBy
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Flight
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HandUse
import pl.pelotasplus.eyeofbeholder.data.model.HarmKind
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemKind
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterImmunities
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.PortraitId
import pl.pelotasplus.eyeofbeholder.data.model.Projectile
import pl.pelotasplus.eyeofbeholder.data.model.SavingThrow
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.ThrownSpell
import pl.pelotasplus.eyeofbeholder.data.model.Wand
import pl.pelotasplus.eyeofbeholder.data.model.WhatAMadeThrowIsWorth
import pl.pelotasplus.eyeofbeholder.data.model.WhatCastingCosts
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemTypesRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepositoryImpl
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
 * A fireball coming down, which is not a missile in three ways.
 *
 * It takes the whole square rather than picking one thing off it. It takes
 * either side, so a party who cast one down a short corridor are in it too.
 * And it is the first thing anybody is given a throw against — half of it to
 * whoever makes theirs, each of them rolling separately.
 *
 * One die of six for every level of the caster, which from a scroll is nine.
 */
@Category(NeedsGameData::class)
class AFireballGoingOffTest {

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

    private val onThem = Location(13, 8)
    private val partyStand = Location(13, 9)

    /** Every die its highest, so 1d6 is six and a d20 saves. */
    private val alwaysTheMost = Dice { times, pips, modifier -> times * pips + modifier }

    /** And its lowest, so 1d6 is one and no throw is ever made. */
    private val alwaysTheLeast = Dice { times, _, modifier -> times + modifier }

    // --- what the spell says it is -------------------------------------------

    @Test
    fun `it burns, and it is magical as everything is`() {
        val thrown = assertNotNull(Spell.FIREBALL.throws, "fireball throws nothing")

        assertEquals(setOf(HarmKind.MAGIC, HarmKind.FIRE), thrown.hurting)
        assertEquals(CountedBy.EVERY_LEVEL, thrown.counted)
        assertTrue(thrown.takesTheWholeSquare)
        assertTrue(thrown.takesEitherSide)
        assertEquals(SavingThrow.A_SPELL, thrown.thrownOff)
        assertEquals(WhatAMadeThrowIsWorth.HALF_OF_IT, thrown.aMadeThrowIsWorth)
    }

    /** One die for every level, so nine of them out of a scroll. */
    @Test
    fun `a scroll throws nine dice of six`() {
        assertEquals(9, CountedBy.EVERY_LEVEL.forACasterOf(ThrownSpell.AS_READ_FROM_A_SCROLL))
    }

    // --- and where the party get one from ------------------------------------

    /**
     * Off a scroll, and only off a scroll. There is a wand of fire in the
     * game's list of eight, but no wand in the dungeon is one: the four that
     * are actually lying about are missiles, lightning, dispelling and
     * defence. So a scroll is the whole of the party's fire.
     */
    @Test
    fun `a scroll casts it, and no wand in the dungeon does`() = runBlocking {
        val itemTypes = ItemTypesRepositoryImpl(resources).loadItemTypes().getOrThrow()
        val dungeon = ItemsRepositoryImpl(resources).loadItems().getOrThrow()

        val scroll = dungeon.items.first {
            itemTypes.kindOf(it) in setOf(ItemKind.MAGE_SCROLL, ItemKind.CLERIC_SCROLL) &&
                Spell.of(it.value) == Spell.FIREBALL
        }

        assertEquals(
            HandUse.Cast(Spell.FIREBALL),
            itemTypes.whatAHandDoesWith(scroll),
            "a scroll of it does not cast it",
        )

        assertEquals(
            emptyList(),
            dungeon.items
                .filter { itemTypes.kindOf(it) == ItemKind.WAND && it.location != Item.CARRIED }
                .filter { Wand.of(it.value) == Wand.OF_FIRE },
            "a wand of fire turned up after all",
        )
    }

    /** A scroll goes up with the words, so each of the seven is one fireball. */
    @Test
    fun `reading the scroll costs the whole scroll`() = runBlocking {
        val itemTypes = ItemTypesRepositoryImpl(resources).loadItemTypes().getOrThrow()
        val dungeon = ItemsRepositoryImpl(resources).loadItems().getOrThrow()

        val scroll = dungeon.items.first {
            itemTypes.kindOf(it) in setOf(ItemKind.MAGE_SCROLL, ItemKind.CLERIC_SCROLL) &&
                Spell.of(it.value) == Spell.FIREBALL
        }

        assertEquals(WhatCastingCosts.AllOfIt, itemTypes.whatCastingCosts(scroll))
    }

    // --- what it does to what it lands on ------------------------------------

    /**
     * Nine dice of six, all coming up six, and the creature failing its throw:
     * fifty-four. Level 5's clerics are ninth level and save on the fighters'
     * table, which the lowest roll there is cannot make.
     */
    @Test
    fun `a creature that fails its throw takes all of it`() {
        val hurt = struckMonsters(dice = alwaysTheLeastButTheDamage)

        assertEquals(1, hurt.size)
        assertEquals(54, hurt.single().by.points)
    }

    /** And one that makes it takes half, which is the throw being worth half. */
    @Test
    fun `a creature that makes its throw takes half`() {
        val hurt = struckMonsters(dice = alwaysTheMost)

        assertEquals(27, hurt.single().by.points)
    }

    /**
     * Everything on the square, not the first thing found. A missile picks one
     * and a fireball does not choose.
     */
    @Test
    fun `it takes everything standing on the square`() {
        val hurt = struckMonsters(dice = alwaysTheLeastButTheDamage, howMany = 3)

        assertEquals(3, hurt.size, "it picked one of them out")
    }

    /** Something that shrugs off fire takes nothing, throw or no throw. */
    @Test
    fun `something that shrugs off fire is untouched`() {
        val hurt = struckMonsters(
            dice = alwaysTheLeastButTheDamage,
            immunities = MonsterImmunities(SHRUGS_OFF_FIRE),
        )

        assertEquals(0, hurt.single().by.points)
    }

    // --- and to the party who cast it ----------------------------------------

    /**
     * The party are in it too. A missile knows whose it is; this does not, and
     * a corridor short enough to reach a monster is short enough to reach back.
     */
    @Test
    fun `the party who cast it are burned by it`() {
        val hurt = struckParty(dice = alwaysTheLeastButTheDamage)

        assertEquals(6, hurt.size, "it should have taken the whole party")
        assertTrue(hurt.all { it.by.points == 54 })
    }

    /** Each of them rolls their own throw rather than sharing one. */
    @Test
    fun `each of the party is thrown against separately`() {
        val hurt = struckParty(dice = alwaysTheMost)

        assertEquals(6, hurt.size)
        assertTrue(hurt.all { it.by.points == 27 }, "they did not each get their throw")
    }

    // --- and to whoever walks into a trap's one ------------------------------

    /**
     * A trap looses the same fireball a champion casts — the same numbered row
     * of the flight table — so the same throw is allowed against it. Only how
     * hard it lands is the floor's rather than a caster's: five dice above the
     * seventh floor, nine from there down.
     */
    @Test
    fun `what a trap looses is the same fireball`() = runBlocking {
        val two = InfRepositoryImpl(
            resourceRepository = resources,
            mazRepository = MazRepositoryImpl(resources),
            vmpRepository = VmpRepositoryImpl(resources),
            vcnRepository = VcnRepositoryImpl(resources),
            palRepository = PalRepositoryImpl(resources),
            cpsRepository = CpsRepositoryImpl(resources),
            decRepository = DecRepositoryImpl(resources),
        ).loadInf("LEVEL2.INF").getOrThrow()

        val pulled = LevelScriptRunner(two.script, level = 2).onEvent(
            triggers = two.triggers,
            event = ScriptEvent.WALL_CLICKED,
            state = GameState(party = PartyState(Location(2, 11), Direction.WEST)),
            stage = RecordingStage(),
            at = Location(2, 11),
        ).state

        val loosed = assertNotNull(pulled.inFlight.singleOrNull(), "the lever loosed nothing")

        assertEquals(SavingThrow.A_SPELL, loosed.harm.thrownOff, "there was no throwing it off")
        assertEquals(WhatAMadeThrowIsWorth.HALF_OF_IT, loosed.harm.aMadeThrowIsWorth)
        assertEquals(setOf(HarmKind.MAGIC, HarmKind.FIRE), loosed.harm.hurting)
        assertTrue(loosed.harm.everybody, "it should take the whole square")
        assertEquals(5, loosed.harm.times, "above the seventh floor a trap throws five")
    }

    // --- the fixture ---------------------------------------------------------

    /**
     * A die that comes up its lowest for a throw and its highest for damage.
     *
     * The two are told apart by their sides: nothing but a saving throw rolls
     * a twenty here, so a one on a d20 fails every throw while the damage
     * still rolls full.
     */
    private val alwaysTheLeastButTheDamage = Dice { times, pips, modifier ->
        if (pips == 20) times + modifier else times * pips + modifier
    }

    private fun aFireball(at: Location) = Spell.FIREBALL.throws!!.let { thrown ->
        Projectile(
            what = null,
            at = at,
            place = PartySlot(0).standsIn.onASquareFacing(Direction.NORTH),
            going = Direction.NORTH,
            squaresLeft = thrown.flies.reach,
            thrownBy = Projectile.Thrower.AChampion(PartySlot(0)),
            harm = thrown.dealtBy(ThrownSpell.AS_READ_FROM_A_SCROLL),
            spell = thrown.flies,
            leaving = false,
        )
    }

    private fun struckMonsters(
        dice: Dice,
        howMany: Int = 1,
        immunities: MonsterImmunities = MonsterImmunities(0),
    ): List<Flight.Hurt.AMonster> {
        val standing = List(howMany) {
            MonsterInstance(
                index = MonsterSlot(it),
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
        }

        val world = GameState(
            party = PartyState(partyStand, Direction.NORTH),
            monsters = standing,
            inFlight = listOf(aFireball(onThem)),
        )

        return flying(dice, immunities).onward(world).hurt.filterIsInstance<Flight.Hurt.AMonster>()
    }

    private fun struckParty(dice: Dice): List<Flight.Hurt.AChampion> {
        val world = GameState(
            party = PartyState(partyStand, Direction.NORTH),
            champions = List(6) { fighter() },
            inFlight = listOf(aFireball(partyStand)),
        )

        return flying(dice).onward(world).hurt.filterIsInstance<Flight.Hurt.AChampion>()
    }

    private fun flying(dice: Dice, immunities: MonsterImmunities = MonsterImmunities(0)) = Flight(
        sublevel = level.subLevels[0],
        level = 5,
        kinds = listOf(level.subLevels[0].monsters.first().copy(immunities = immunities)),
        dice = dice,
    )

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

    private companion object {
        /** The bit that turns fire aside. */
        const val SHRUGS_OFF_FIRE = 0x800
    }
}
