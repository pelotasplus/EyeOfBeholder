package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Abilities
import pl.pelotasplus.eyeofbeholder.data.model.Ability
import pl.pelotasplus.eyeofbeholder.data.model.ArmorClass
import pl.pelotasplus.eyeofbeholder.data.model.CarrySlot
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.PortraitId
import pl.pelotasplus.eyeofbeholder.data.model.Race
import pl.pelotasplus.eyeofbeholder.data.model.SavingThrow
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
import pl.pelotasplus.eyeofbeholder.data.model.saves
import pl.pelotasplus.eyeofbeholder.data.model.toHitProgression
import pl.pelotasplus.eyeofbeholder.data.model.ToHitProgression
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The roll a champion is given to shrug something off.
 *
 * The tables are AD&D's and are transcribed, so what is asserted here is read
 * off those rules rather than off what the code happens to produce: a
 * levelless fighter wants 16 against paralysis and 20 against a breath
 * weapon, a first-level cleric wants 10 against paralysis, and both get
 * better in bands rather than every level.
 *
 * There is no way to ask what number is wanted — only whether the throw was
 * made — so the number is pinned by rolling exactly it and exactly one less.
 */
class SavingThrowTest {

    private fun champion(
        isA: CharacterClass = CharacterClass.FIGHTER,
        level: Int = 1,
        race: Race = Race.HUMAN,
        constitution: Int = 10,
    ) = Champion(
        name = "Anselm",
        portrait = PortraitId(0),
        abilities = Abilities(
            strength = Ability(10, 10),
            dexterity = Ability(10, 10),
            constitution = Ability(constitution, constitution),
        ),
        hitPoints = HitPoints(20, 20),
        armorClass = ArmorClass(10),
        food = Food(100),
        race = race,
        characterClass = isA,
        levels = listOf(ClassLevel(level, XpPoints(0))),
        carrying = List(CarrySlot.ALL_OF_THEM) { ItemIndex(ItemIndex.NOTHING) },
        flags = ChampionFlags(1),
    )

    /** A die that always shows [pips], whatever it is asked to roll. */
    private fun alwaysRolling(pips: Int) = Dice { _, _, _ -> pips }

    /**
     * The lowest roll that makes the throw, found by trying each in turn.
     * A throw nothing makes comes back as 21.
     */
    private fun Champion.needsAgainst(off: SavingThrow): Int =
        (1..21).first { it == 21 || saves(off, alwaysRolling(it)) }

    // --- what each class wants, off the AD&D tables ---------------------------

    @Test
    fun `a first level fighter wants the worst row there is`() {
        val fighter = champion(level = 1)

        assertEquals(16, fighter.needsAgainst(SavingThrow.PARALYSIS_POISON_OR_DEATH))
        assertEquals(18, fighter.needsAgainst(SavingThrow.A_ROD_STAFF_OR_WAND))
        assertEquals(17, fighter.needsAgainst(SavingThrow.PETRIFICATION_OR_POLYMORPH))
        assertEquals(20, fighter.needsAgainst(SavingThrow.A_BREATH_WEAPON))
        assertEquals(19, fighter.needsAgainst(SavingThrow.A_SPELL))
    }

    /**
     * A fighter improves every two levels, so the second and third want the
     * same and the fourth is better again.
     */
    @Test
    fun `a fighter improves in bands of two`() {
        val wanted = (1..7).map {
            champion(level = it).needsAgainst(SavingThrow.PARALYSIS_POISON_OR_DEATH)
        }

        assertEquals(listOf(16, 14, 14, 13, 13, 11, 11), wanted)
    }

    @Test
    fun `a cleric saves better against paralysis than a fighter does`() {
        assertEquals(
            10,
            champion(isA = CharacterClass.CLERIC).needsAgainst(SavingThrow.PARALYSIS_POISON_OR_DEATH),
        )
    }

    @Test
    fun `and a mage better than either against a wand`() {
        assertEquals(
            11,
            champion(isA = CharacterClass.MAGE).needsAgainst(SavingThrow.A_ROD_STAFF_OR_WAND),
        )
    }

    @Test
    fun `a thief has a table of its own`() {
        assertEquals(
            13,
            champion(isA = CharacterClass.THIEF).needsAgainst(SavingThrow.PARALYSIS_POISON_OR_DEATH),
        )
    }

    /**
     * Past a level of its own a class gains nothing more, and for a fighter
     * that is level seventeen.
     *
     * Four is as good as it gets, not the three the last column of the table
     * holds: the cap lands on the band before it, so the best row a fighter
     * has is one nobody can ever reach. It is written that way and left that
     * way — the numbers are the game's, cap included.
     */
    @Test
    fun `improvement stops one band short of the table`() {
        val best = champion(level = 17).needsAgainst(SavingThrow.PARALYSIS_POISON_OR_DEATH)

        assertEquals(4, best)
        assertEquals(best, champion(level = 40).needsAgainst(SavingThrow.PARALYSIS_POISON_OR_DEATH))
    }

    // --- who is hardy --------------------------------------------------------

    /**
     * A dwarf's constitution is worth something against poison and against
     * magic, and nothing against a breath weapon.
     */
    @Test
    fun `a dwarf shrugs off poison and magic`() {
        val dwarf = champion(race = Race.DWARF, constitution = 18)

        assertEquals(16 - 5, dwarf.needsAgainst(SavingThrow.PARALYSIS_POISON_OR_DEATH))
        assertEquals(18 - 5, dwarf.needsAgainst(SavingThrow.A_ROD_STAFF_OR_WAND))
        assertEquals(19 - 5, dwarf.needsAgainst(SavingThrow.A_SPELL))
        assertEquals(20, dwarf.needsAgainst(SavingThrow.A_BREATH_WEAPON))
    }

    /** A gnome is hardy against magic alone, and not against poison. */
    @Test
    fun `a gnome is helped against magic only`() {
        val gnome = champion(race = Race.GNOME, constitution = 18)

        assertEquals(19 - 5, gnome.needsAgainst(SavingThrow.A_SPELL))
        assertEquals(16, gnome.needsAgainst(SavingThrow.PARALYSIS_POISON_OR_DEATH))
    }

    @Test
    fun `a halfling is helped like a dwarf`() {
        assertEquals(
            16 - 5,
            champion(race = Race.HALFLING, constitution = 18)
                .needsAgainst(SavingThrow.PARALYSIS_POISON_OR_DEATH),
        )
    }

    /** And nobody else is helped, however hardy they look. */
    @Test
    fun `a human of the same constitution gets nothing`() {
        assertEquals(
            16,
            champion(race = Race.HUMAN, constitution = 18)
                .needsAgainst(SavingThrow.PARALYSIS_POISON_OR_DEATH),
        )
    }

    /** The bonus climbs in steps of its own rather than with the score. */
    @Test
    fun `how much a constitution is worth`() {
        val worth = listOf(3, 4, 7, 11, 14, 18).map {
            16 - champion(race = Race.DWARF, constitution = it)
                .needsAgainst(SavingThrow.PARALYSIS_POISON_OR_DEATH)
        }

        assertEquals(listOf(0, 1, 2, 3, 4, 5), worth)
    }

    // --- the roll itself -----------------------------------------------------

    @Test
    fun `the number wanted is made and one less is not`() {
        val fighter = champion()

        assertTrue(fighter.saves(SavingThrow.PARALYSIS_POISON_OR_DEATH, alwaysRolling(16)))
        assertFalse(fighter.saves(SavingThrow.PARALYSIS_POISON_OR_DEATH, alwaysRolling(15)))
    }

    /**
     * A blow that allows no throw names a kind there is none of, so nothing
     * has to remember which number means that.
     */
    @Test
    fun `the number that is no throw at all names nothing`() {
        assertEquals(null, SavingThrow.of(NO_THROW))
        SavingThrow.entries.forEach { assertEquals(it, SavingThrow.of(it.ordinal)) }
    }

    /**
     * The game keeps who saves like what and who hits like what in separate
     * tables, and they agree. Nothing forces them to, so this says so.
     */
    @Test
    fun `saving and hitting group the classes the same way`() {
        val savesAs = mapOf(
            CharacterClass.FIGHTER to ToHitProgression.AS_A_FIGHTER,
            CharacterClass.RANGER to ToHitProgression.AS_A_FIGHTER,
            CharacterClass.PALADIN to ToHitProgression.AS_A_FIGHTER,
            CharacterClass.MAGE to ToHitProgression.AS_A_MAGE,
            CharacterClass.CLERIC to ToHitProgression.AS_A_CLERIC,
            CharacterClass.THIEF to ToHitProgression.AS_A_THIEF,
            CharacterClass.FIGHTER_CLERIC to ToHitProgression.AS_A_FIGHTER,
            CharacterClass.FIGHTER_THIEF to ToHitProgression.AS_A_FIGHTER,
            CharacterClass.FIGHTER_MAGE to ToHitProgression.AS_A_FIGHTER,
            CharacterClass.FIGHTER_MAGE_THIEF to ToHitProgression.AS_A_FIGHTER,
            CharacterClass.THIEF_MAGE to ToHitProgression.AS_A_THIEF,
            CharacterClass.CLERIC_THIEF to ToHitProgression.AS_A_CLERIC,
            CharacterClass.FIGHTER_CLERIC_MAGE to ToHitProgression.AS_A_FIGHTER,
            CharacterClass.RANGER_CLERIC to ToHitProgression.AS_A_FIGHTER,
            CharacterClass.CLERIC_MAGE to ToHitProgression.AS_A_CLERIC,
        )

        assertEquals(CharacterClass.entries.size, savesAs.size)
        savesAs.forEach { (isA, groupedAs) ->
            assertEquals(groupedAs, isA.toHitProgression, "$isA")
        }
    }

    /**
     * And the grouping is the one the throws use: two classes in the same
     * group want the same number, and two in different groups do not.
     */
    @Test
    fun `classes grouped together want the same number`() {
        val against = SavingThrow.PARALYSIS_POISON_OR_DEATH

        assertEquals(
            champion(isA = CharacterClass.CLERIC).needsAgainst(against),
            champion(isA = CharacterClass.CLERIC_MAGE).needsAgainst(against),
        )
        assertEquals(
            champion(isA = CharacterClass.FIGHTER).needsAgainst(against),
            champion(isA = CharacterClass.RANGER_CLERIC).needsAgainst(against),
        )
    }

    /**
     * Nobody gets worse at shrugging things off as they go on — save in the
     * one place the game says otherwise.
     *
     * The tables are transcribed by hand and most of what they hold is pinned
     * nowhere else, so this asks the one thing the numbers must satisfy
     * whatever they are. A digit typed wrongly nearly always shows up as a
     * champion who saved better before they gained a level.
     *
     * The exception is real and is the game's: a thief of the twentieth level
     * wants a seven against a wand where one of the sixteenth wanted a six.
     */
    @Test
    fun `saving throws only ever improve, bar the one that does not`() {
        val slipsBack = mutableListOf<String>()

        CharacterClass.entries.forEach { isA ->
            SavingThrow.entries.forEach { against ->
                (1..25).map { champion(isA = isA, level = it).needsAgainst(against) }
                    .zipWithNext()
                    .filter { (earlier, later) -> later > earlier }
                    .forEach { (earlier, later) ->
                        slipsBack += "$isA against $against went $earlier -> $later"
                    }
            }
        }

        assertEquals(
            listOf(
                "THIEF against A_ROD_STAFF_OR_WAND went 6 -> 7",
                "THIEF_MAGE against A_ROD_STAFF_OR_WAND went 6 -> 7",
            ),
            slipsBack,
        )
    }

    private companion object {
        /** What a blow that allows no throw names. */
        const val NO_THROW = 5
    }
}
