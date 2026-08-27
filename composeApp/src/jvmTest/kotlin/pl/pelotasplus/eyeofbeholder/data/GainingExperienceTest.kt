package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Abilities
import pl.pelotasplus.eyeofbeholder.data.model.Ability
import pl.pelotasplus.eyeofbeholder.data.model.ArmorClass
import pl.pelotasplus.eyeofbeholder.data.model.CarrySlot
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.ClassProgression
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.PortraitId
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
import pl.pelotasplus.eyeofbeholder.data.model.earning
import pl.pelotasplus.eyeofbeholder.data.model.partyEarns
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Earning experience and levelling on it.
 *
 * The thresholds and the hit dice are a fixed table, so the expected values
 * come from that table rather than from what the code happens to produce: a
 * fighter reaches its second level at two thousand and rolls a ten-sided die
 * for the hit points that come with it, a mage at two thousand five hundred on
 * a four-sided one, and a multi-class splits both the experience and the roll.
 */
class GainingExperienceTest {

    /** Every die comes up its highest, so the hit die roll is the class die. */
    private val bestRoll = Dice { times, pips, modifier -> times * pips + modifier }

    /** And its lowest, so a roll is one. */
    private val worstRoll = Dice { times, _, modifier -> times + modifier }

    private val nowhere = PartyState(Location(0, 0), Direction.NORTH)

    private fun xp(count: Long) = XpPoints(count)

    private fun champion(
        cClass: CharacterClass,
        levels: List<ClassLevel>,
        constitution: Int = 10,
        hitPoints: HitPoints = HitPoints(20, 20),
    ) = Champion.NOBODY.copy(
        name = "Test",
        portrait = PortraitId(0),
        abilities = Abilities(constitution = Ability(constitution, constitution)),
        hitPoints = hitPoints,
        armorClass = ArmorClass(10),
        food = Food(100),
        characterClass = cClass,
        levels = levels,
        carrying = CarrySlot.NOTHING_IN_ANY,
        flags = ChampionFlags(1),
    )

    // --- the tables ----------------------------------------------------------

    @Test
    fun `each class needs what the rules say for a level`() {
        assertEquals(xp(2_000), ClassProgression.AS_A_FIGHTER.neededFor(2))
        assertEquals(xp(250_000), ClassProgression.AS_A_FIGHTER.neededFor(9))
        assertEquals(xp(2_500), ClassProgression.AS_A_MAGE.neededFor(2))
        assertEquals(xp(1_500), ClassProgression.AS_A_CLERIC.neededFor(2))
        assertEquals(xp(1_250), ClassProgression.AS_A_THIEF.neededFor(2))
        assertEquals(xp(2_250), ClassProgression.AS_A_RANGER.neededFor(2))
        assertEquals(
            ClassProgression.AS_A_RANGER.neededFor(9),
            ClassProgression.AS_A_PALADIN.neededFor(9),
            "a paladin advances on the ranger's table",
        )
    }

    @Test
    fun `the last level is a ceiling and nothing is asked past it`() {
        assertEquals(13, ClassProgression.AS_A_FIGHTER.levelFor(xp(Long.MAX_VALUE)))
        assertEquals(null, ClassProgression.AS_A_FIGHTER.neededFor(14))
    }

    @Test
    fun `a level is whatever the experience has earned`() {
        assertEquals(1, ClassProgression.AS_A_FIGHTER.levelFor(xp(0)))
        assertEquals(1, ClassProgression.AS_A_FIGHTER.levelFor(xp(1_999)))
        assertEquals(2, ClassProgression.AS_A_FIGHTER.levelFor(xp(2_000)))
        assertEquals(7, ClassProgression.AS_A_FIGHTER.levelFor(xp(64_000)))
    }

    // --- one class -----------------------------------------------------------

    @Test
    fun `a fighter reaching the threshold gains a level and hit points`() {
        val before = champion(CharacterClass.FIGHTER, listOf(ClassLevel(1, xp(0))), constitution = 16)

        val after = before.earning(points = xp(2_000), dice = bestRoll)

        assertEquals(2, after.levels.first().level)
        assertEquals(xp(2_000), after.levels.first().experience)
        // a ten-sided die at its best plus what a warrior's constitution of 16 is worth
        assertEquals(HitPoints(32, 32), after.hitPoints)
    }

    @Test
    fun `short of the threshold it only banks the experience`() {
        val before = champion(CharacterClass.FIGHTER, listOf(ClassLevel(1, xp(0))))

        val after = before.earning(points = xp(1_999), dice = bestRoll)

        assertEquals(1, after.levels.first().level)
        assertEquals(xp(1_999), after.levels.first().experience)
        assertEquals(HitPoints(20, 20), after.hitPoints)
    }

    @Test
    fun `one kill is worth at most one level however far it reaches`() {
        val before = champion(CharacterClass.FIGHTER, listOf(ClassLevel(1, xp(0))))

        val after = before.earning(points = xp(100_000), dice = worstRoll)

        assertEquals(2, after.levels.first().level, "it jumped more than a level on one kill")
        assertEquals(xp(100_000), after.levels.first().experience)
    }

    @Test
    fun `past the die's last level the gain is a flat one and not a roll`() {
        val before = champion(CharacterClass.FIGHTER, listOf(ClassLevel(9, xp(499_000))))

        val after = before.earning(points = xp(2_000), dice = bestRoll)

        assertEquals(10, after.levels.first().level)
        // a fighter past level nine takes three rather than rolling ten
        assertEquals(HitPoints(23, 23), after.hitPoints)
    }

    // --- constitution --------------------------------------------------------

    @Test
    fun `a hardy constitution helps a warrior more than a mage`() {
        val fighter = champion(CharacterClass.FIGHTER, listOf(ClassLevel(1, xp(0))), constitution = 18)
        val mage = champion(CharacterClass.MAGE, listOf(ClassLevel(1, xp(0))), constitution = 18)

        val grownFighter = fighter.earning(points = xp(2_000), dice = bestRoll)
        val grownMage = mage.earning(points = xp(2_500), dice = bestRoll)

        // a warrior's eighteen is worth four; a d10 and four
        assertEquals(14, grownFighter.hitPoints.max - 20)
        // a mage's is held to two; a d4 and two
        assertEquals(6, grownMage.hitPoints.max - 20)
    }

    // --- several classes -----------------------------------------------------

    @Test
    fun `a multi-class splits the experience among its classes`() {
        val before = champion(
            CharacterClass.FIGHTER_MAGE_THIEF,
            listOf(ClassLevel(1, xp(0)), ClassLevel(1, xp(0)), ClassLevel(1, xp(0))),
        )

        val after = before.earning(points = xp(9_000), dice = bestRoll)

        assertEquals(listOf(xp(3_000), xp(3_000), xp(3_000)), after.levels.map { it.experience })
        assertEquals(listOf(2, 2, 2), after.levels.map { it.level }, "not every class levelled")
        // each class's roll is shared three ways: (d10)/3 + (d4)/3 + (d6)/3
        assertEquals(6, after.hitPoints.max - 20)
    }

    @Test
    fun `a class already at its ceiling only banks the share`() {
        val before = champion(
            CharacterClass.FIGHTER_MAGE,
            listOf(ClassLevel(13, xp(2_000_000)), ClassLevel(1, xp(0))),
        )

        val after = before.earning(points = xp(5_000), dice = bestRoll)

        assertEquals(13, after.levels[0].level, "the capped class climbed")
        assertEquals(xp(2_002_500), after.levels[0].experience)
        assertEquals(2, after.levels[1].level)
    }

    // --- the party -----------------------------------------------------------

    @Test
    fun `the party share a kill evenly`() {
        val world = GameState(
            party = nowhere,
            champions = List(4) { champion(CharacterClass.FIGHTER, listOf(ClassLevel(1, xp(0)))) },
        )

        val after = world.partyEarns(points = xp(8_000), dice = worstRoll)

        after.champions.forEach {
            assertEquals(xp(2_000), it.levels.first().experience, "${it.name} got the wrong share")
        }
    }

    @Test
    fun `a champion past raising takes no share and enlarges everyone else's`() {
        val gone = champion(
            CharacterClass.FIGHTER,
            listOf(ClassLevel(1, xp(0))),
            hitPoints = HitPoints(Champion.BEYOND_RAISING, 20),
        )
        val world = GameState(
            party = nowhere,
            champions = listOf(
                champion(CharacterClass.FIGHTER, listOf(ClassLevel(1, xp(0)))),
                champion(CharacterClass.FIGHTER, listOf(ClassLevel(1, xp(0)))),
                gone,
            ),
        )

        val after = world.partyEarns(points = xp(9_000), dice = worstRoll)

        // two share nine thousand rather than three, so each gets more
        assertEquals(xp(4_500), after.champions[0].levels.first().experience)
        assertEquals(xp(4_500), after.champions[1].levels.first().experience)
        assertEquals(xp(0), after.champions[2].levels.first().experience, "the fallen one still earned")
    }
}
