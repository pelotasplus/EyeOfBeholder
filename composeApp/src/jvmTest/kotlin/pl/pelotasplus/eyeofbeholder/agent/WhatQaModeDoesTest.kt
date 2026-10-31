package pl.pelotasplus.eyeofbeholder.agent

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.ThePlayField
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Holding
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.Preferences
import pl.pelotasplus.eyeofbeholder.data.model.RunningSpell
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.SpellsRunning
import pl.pelotasplus.eyeofbeholder.data.model.AWallOfForce
import pl.pelotasplus.eyeofbeholder.data.model.WALL_OF_FORCE_BYTE
import pl.pelotasplus.eyeofbeholder.data.model.WallSide
import pl.pelotasplus.eyeofbeholder.data.model.WallsOfForce
import pl.pelotasplus.eyeofbeholder.data.model.WhatABlowLeaves
import pl.pelotasplus.eyeofbeholder.data.repository.SaveSlot
import pl.pelotasplus.eyeofbeholder.data.repository.SavedGameRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The shortcuts and the looking-behind that QA mode gives an agent, on the
 * real play field.
 */
@Category(NeedsGameData::class)
class WhatQaModeDoesTest {

    private fun field(level: String, at: Location, facing: Direction): ThePlayField = runBlocking {
        ThePlayField().apply {
            opened(level, at, facing)
            until("the party") { viewModel.state.value.game.champions.isNotEmpty() }
        }
    }

    @Test
    fun `a teleport puts the party where it says`() = runBlocking {
        val field = field("LEVEL12.INF", Location(19, 15), Direction.SOUTH)

        assertNull(field.viewModel.setUp(QaSetup.Teleport(level = 5, x = 13, y = 12, facing = Direction.NORTH)))
        field.until("level 5") { field.viewModel.observation()?.let { it.level == 5 && it.at == Location(13, 12) } == true }
    }

    /** On the fourth floor, which places wolves: one put two squares ahead stands there, facing the party. */
    @Test
    fun `a monster can be put in front of the party`() = runBlocking {
        val field = field("LEVEL4.INF", Location(18, 14), Direction.NORTH)

        assertNull(field.viewModel.setUp(QaSetup.SpawnMonster(looksLike = "wolf", ahead = 2)))

        val wolf = requireNotNull(field.viewModel.inspection()).monsters.single { it.at == Location(18, 12) }
        assertEquals("wolf", wolf.looksLike)
        assertEquals(Direction.SOUTH, wolf.facing)
    }

    @Test
    fun `a look the floor does not place is refused, naming those it does`() {
        val field = field("LEVEL4.INF", Location(18, 14), Direction.NORTH)

        val refused = requireNotNull(field.viewModel.setUp(QaSetup.SpawnMonster(looksLike = "dragon")))

        assertTrue("wolf" in refused, refused)
    }

    /**
     * A spell with no effect, read off a scroll: the casting is noted as
     * reaching nothing. A book has no tab for a spell this high.
     */
    @Test
    fun `a spell with no effect is noted when cast`() = runBlocking {
        val field = field("LEVEL12.INF", Location(19, 15), Direction.SOUTH)
        val before = Diagnostics.since(0).lastOrNull()?.number ?: 0
        val nothingYet = Spell.BIGBYS_CLENCHED_FIST

        // A casting straight after the floor opens can be lost to the opening.
        delay(THE_OPENING)
        field.read(nothingYet)
        field.until("the note") { Diagnostics.since(before).any { nothingYet.calledIt in it.said } }
    }

    /**
     * A power word read at a wolf two squares off, through the whole of a
     * casting: the wiring from the spell to the stone, on the floor's real
     * walls. Read until it takes, since the wolf gets a throw and real dice.
     */
    @Test
    fun `a power word turns a wolf two squares off to stone`() = runBlocking {
        val field = field("LEVEL4.INF", Location(18, 14), Direction.NORTH)
        assertNull(field.viewModel.setUp(QaSetup.SpawnMonster(looksLike = "wolf", ahead = 2)))

        fun aWolfIsStone() = requireNotNull(field.viewModel.inspection()).monsters
            .any { it.looksLike == "wolf" && it.stone }

        withTimeout(ALL_THE_READINGS) {
            while (!aWolfIsStone()) {
                field.read(Spell.POWER_WORD_STUN)
                delay(A_CASTING)
                assertNull(field.viewModel.act(AgentAction.TakeHand(champion = THE_MAGE, hand = 0)))
            }
        }
    }

    /**
     * A slow poison read through the whole of a casting: turned away from
     * somebody with no poison and the scroll kept for the next try, then
     * settled on the poisoned mage.
     */
    @Test
    fun `a slow poison is refused the healthy and settles on the poisoned`() = runBlocking {
        val field = field("LEVEL12.INF", Location(19, 15), Direction.SOUTH)
        delay(THE_OPENING)
        assertNull(field.viewModel.setUp(QaSetup.Poison(THE_MAGE)))

        fun slowed() = requireNotNull(field.viewModel.inspection()).running.toString().contains("slow poison", ignoreCase = true)

        field.read(Spell.SLOW_POISON, by = THE_CLERIC)
        assertNull(field.viewModel.act(AgentAction.CastOn(champion = 0)))
        field.until("the refusal") { field.viewModel.observation()?.messages?.last()?.contains("no effect") == true }
        assertFalse(slowed())

        assertNull(field.viewModel.act(AgentAction.UseHand(champion = THE_CLERIC, hand = 0)))
        assertNull(field.viewModel.act(AgentAction.CastOn(champion = THE_MAGE)))
        field.until("the spell to settle") { slowed() }
    }

    /**
     * The venom's live clock, held still until the spell is on: its first bite
     * takes the unslowed champion and passes over the slowed one.
     */
    @Test
    fun `the venom's clock bites past a slow poison`() = runBlocking {
        val field = field("LEVEL12.INF", Location(19, 15), Direction.SOUTH)
        delay(THE_OPENING)
        field.agentTime.takeOver()

        assertNull(field.viewModel.setUp(QaSetup.Poison(THE_MAGE)))
        assertNull(field.viewModel.setUp(QaSetup.Poison(ANOTHER)))
        field.read(Spell.SLOW_POISON, by = THE_CLERIC)
        assertNull(field.viewModel.act(AgentAction.CastOn(champion = THE_MAGE)))

        fun hitPointsOf(slot: Int) = requireNotNull(field.viewModel.observation()).party[slot].hitPoints
        val mage = hitPointsOf(THE_MAGE)
        val other = hitPointsOf(ANOTHER)

        withTimeout(ALL_THE_READINGS) {
            while (hitPointsOf(ANOTHER) == other) field.agentTime.advance(A_CASTING)
        }

        assertEquals(mage, hitPointsOf(THE_MAGE), "the venom bit through the slow poison")
    }

    @Test
    fun `a scroll given is of the discipline its spell belongs to`() {
        val field = field("LEVEL12.INF", Location(19, 15), Direction.SOUTH)

        assertNull(field.viewModel.setUp(QaSetup.GiveScroll(Spell.SLOW_POISON.calledIt)))
        assertEquals("Cleric Scroll of slow poison", field.viewModel.observation()?.holding)
    }

    /** A scroll of [spell] swapped into the mage's free hand and read. */
    private suspend fun ThePlayField.read(spell: Spell, by: Int = THE_MAGE) {
        until("the hand to come back to rest") {
            viewModel.observation()?.party?.get(by)?.hands?.first()?.contains("recovering") == false
        }
        assertNull(viewModel.setUp(QaSetup.GiveScroll(spell.calledIt)))
        assertNull(viewModel.act(AgentAction.TakeHand(champion = by, hand = 0)))
        assertNull(viewModel.act(AgentAction.UseHand(champion = by, hand = 0)))
    }

    @Test
    fun `the game's own warnings are noted, and its quieter lines are not`() {
        Diagnostics.listen()
        val before = Diagnostics.since(0).lastOrNull()?.number ?: 0

        co.touchlab.kermit.Logger.w("QaTest") { "a warning worth seeing" }
        co.touchlab.kermit.Logger.d("QaTest") { "a line nobody needs" }

        val noted = Diagnostics.since(before).map { it.said }
        assertTrue(noted.any { "a warning worth seeing" in it }, "$noted")
        assertTrue(noted.none { "a line nobody needs" in it }, "$noted")
    }

    @Test
    fun `an item can be put on the pointer`() {
        val field = field("LEVEL12.INF", Location(19, 15), Direction.SOUTH)

        assertNull(field.viewModel.setUp(QaSetup.GiveItem("dagger")))

        assertNotNull(field.viewModel.observation()?.holding)
    }

    @Test
    fun `healing mends everybody to the full`() {
        val field = field("LEVEL12.INF", Location(19, 15), Direction.SOUTH)

        assertNull(field.viewModel.setUp(QaSetup.Heal))

        requireNotNull(field.viewModel.inspection()).party.forEach { champion ->
            val (current, most) = champion.hitPoints.split("/")
            assertEquals(most, current, "${champion.name} was not mended")
        }
    }

    private companion object {
        /** Which of the saved party keeps a spellbook in hand. */
        const val THE_MAGE = 3
        const val THE_CLERIC = 2
        const val ANOTHER = 0
        const val A_FEW_TICKS = 40

        const val THE_OPENING = 1_000L
        const val A_CASTING = 300L
        const val ALL_THE_READINGS = 60_000L
    }
}
