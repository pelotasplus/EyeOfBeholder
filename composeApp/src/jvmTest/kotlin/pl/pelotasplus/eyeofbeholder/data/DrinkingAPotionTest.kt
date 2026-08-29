package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.Potion
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What is in each of the eight bottles, and what drinking one comes to.
 *
 * The order of them, the words a champion is said to feel, and the two sets of
 * dice are all the game's. Healing is 2d4+2 and extra healing 3d8+3, so the
 * one can mend between four and ten and the other between six and twenty-seven.
 */
class DrinkingAPotionTest {

    /** Every die comes up its highest, and every die its lowest. */
    private val best = Dice { times, pips, modifier -> times * pips + modifier }
    private val worst = Dice { times, _, modifier -> times + modifier }

    private fun world(hurt: Int, of: Int = 20, fed: Int = 50) = GameState(
        party = PartyState(Location(1, 1), Direction.NORTH),
        champions = listOf(
            Champion.NOBODY.copy(
                flags = ChampionFlags(1),
                hitPoints = HitPoints(of - hurt, of),
                food = Food(fed),
            ),
        ),
    )

    private val first = PartySlot(0)

    // --- what is in the bottles ----------------------------------------------

    @Test
    fun `a potion is what its value says it is`() {
        assertEquals(Potion.GIANT_STRENGTH, Potion.of(0))
        assertEquals(Potion.HEALING, Potion.of(1))
        assertEquals(Potion.EXTRA_HEALING, Potion.of(2))
        assertEquals(Potion.POISON, Potion.of(3))
        assertEquals(Potion.VITALITY, Potion.of(4))
        assertEquals(Potion.SPEED, Potion.of(5))
        assertEquals(Potion.INVISIBILITY, Potion.of(6))
        assertEquals(Potion.CURE_POISON, Potion.of(7))
        assertEquals(null, Potion.of(8), "there is no eighth")
    }

    /** Two of them say the same word for different reasons, which is the game's. */
    @Test
    fun `cure poison and healing both leave a champion feeling better`() {
        assertEquals("better", Potion.HEALING.feels)
        assertEquals("better", Potion.CURE_POISON.feels)
        assertEquals("much better", Potion.EXTRA_HEALING.feels)
    }

    @Test
    fun `the line names the champion and what they feel`() {
        assertEquals(
            "\"Stumpy\" feels much better!",
            Potion.said("\"Stumpy\"", Potion.EXTRA_HEALING.feels),
        )
    }

    // --- the dice ------------------------------------------------------------

    @Test
    fun `healing mends between four and ten`() {
        assertEquals(4, Potion.HEALING.mends(worst))
        assertEquals(10, Potion.HEALING.mends(best))
    }

    @Test
    fun `extra healing mends between six and twenty-seven`() {
        assertEquals(6, Potion.EXTRA_HEALING.mends(worst))
        assertEquals(27, Potion.EXTRA_HEALING.mends(best))
    }

    /** The other six mend nothing, whatever they do instead. */
    @Test
    fun `nothing else in the eight mends`() {
        val mending = listOf(Potion.HEALING, Potion.EXTRA_HEALING)

        Potion.entries.filterNot { it in mending }.forEach {
            assertEquals(0, it.mends(best), "$it should mend nothing")
        }
    }

    // --- what mending does to a champion --------------------------------------

    @Test
    fun `mending stops at what a champion can take`() {
        val mended = world(hurt = 3).championMended(first, 10)

        assertEquals(20, mended.champions[0].hitPoints.current, "it does not go past the top")
    }

    @Test
    fun `mending somebody barely hurt mends only what is missing`() {
        val mended = world(hurt = 12).championMended(first, 5)

        assertEquals(13, mended.champions[0].hitPoints.current)
    }

    /** Vitality fills the stomach outright rather than adding a mouthful. */
    @Test
    fun `vitality fills a stomach however empty it was`() {
        assertEquals(100, world(hurt = 0, fed = 1).championSated(first).champions[0].food.value)
        assertEquals(100, world(hurt = 0, fed = 99).championSated(first).champions[0].food.value)
    }

    // --- being poisoned -------------------------------------------------------

    /**
     * The bit is the game's, and one of the three troubles the flag word
     * already carried: two is poisoned, four paralysed, eight turned to stone.
     */
    @Test
    fun `poison is the second bit of the flag word`() {
        val inTheParty = ChampionFlags(0x01)
        val poisoned = inTheParty.poisoned(true)

        assertEquals(0x03, poisoned.value, "the bit the game writes, and nothing else touched")
        assertEquals(true, poisoned.poisoned)
        assertEquals(true, poisoned.inTheParty)
        assertEquals(0x01, poisoned.poisoned(false).value, "and clearing puts the word back")
    }

    /** It is one of the troubles, which is what makes a champion worth curing. */
    @Test
    fun `poison counts as being in trouble`() {
        assertEquals(true, ChampionFlags(0x01).poisoned(true).inTrouble)
        assertEquals(false, ChampionFlags(0x01).inTrouble)
    }

    /** A poisoned champion fights on: it is not one of the troubles that stops them. */
    @Test
    fun `poison does not stop a champion acting`() {
        assertEquals(true, world(hurt = 0).championPoisoned(first).champions[0].canAct)
    }

    /**
     * A second bite does not start it again. The game refuses to refresh it,
     * so being bitten twice is no worse than being bitten once.
     */
    @Test
    fun `poisoning somebody already poisoned changes nothing`() {
        val once = world(hurt = 0).championPoisoned(first)
        val twice = once.championPoisoned(first)

        assertEquals(once, twice)
    }

    @Test
    fun `curing takes it away and leaves them in the party`() {
        val cured = world(hurt = 0).championPoisoned(first).championPoisoned(first, yes = false)

        assertEquals(false, cured.champions[0].poisoned)
        assertEquals(true, cured.champions[0].inTheParty)
    }

    @Test
    fun `who is poisoned is asked of the whole party`() {
        val world = world(hurt = 0)

        assertEquals(emptyList(), world.poisoned)
        assertEquals(listOf(first), world.championPoisoned(first).poisoned)
    }

    /**
     * It takes nothing more from somebody who is already down. The hold does
     * not let go — nothing washes it out of them — but a clock that kept
     * biting a body would never stop, and never says it had stopped either.
     */
    @Test
    fun `the poison stops taking from the dead`() {
        val down = world(hurt = 20).championPoisoned(first)

        assertEquals(true, down.champions[0].poisoned, "it still has hold of them")
        assertEquals(emptyList(), down.poisoned, "but there is nothing more to take")
    }
}
