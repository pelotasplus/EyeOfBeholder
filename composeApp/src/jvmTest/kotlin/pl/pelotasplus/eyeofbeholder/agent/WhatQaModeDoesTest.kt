package pl.pelotasplus.eyeofbeholder.agent

import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.data.model.CampSpellMessages
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.ThePlayField
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Discipline
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.SpellCapacity
import kotlin.test.Test
import kotlin.test.assertEquals
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
     * A prayer nothing has been written for yet, given to the cleric and
     * read out of her book: the casting is noted as reaching nothing.
     *
     * **This test is running out of subject matter, and that is the point of
     * the comment.** It wants a spell the saved party can actually cast that
     * does nothing, and there is no longer one: every spell of either
     * discipline at a level this party has reached now has an effect. The
     * nine that do not are all learnt above the mage's seventh level or
     * belong to the cleric, who carries no holy symbol in this save and so
     * has no book to open.
     *
     * So it stands down rather than passing on nothing. When the next thing
     * worth noting appears — a hand use that does nothing, an item with no
     * answer — this is the shape to write it in, and the assumption below is
     * what to delete.
     */
    @Test
    fun `a spell with no effect is noted when cast`() = runBlocking {
        val field = field("LEVEL12.INF", Location(19, 15), Direction.SOUTH)
        val before = Diagnostics.since(0).lastOrNull()?.number ?: 0
        val nothingYet = Spell.POWER_WORD_STUN

        assertNull(field.viewModel.setUp(QaSetup.GiveSpell(champion = THE_MAGE, spell = nothingYet.calledIt)))
        assertNull(field.viewModel.act(AgentAction.UseHand(champion = THE_MAGE, hand = 1)))
        field.until("the book") { field.viewModel.observation()?.book != null }

        val page = assertNotNull(SpellCapacity.pageOf(nothingYet, Discipline.MAGE))
        field.viewModel.act(AgentAction.BookTab(page))
        val written = CampSpellMessages.menuNameOf(nothingYet, Discipline.MAGE)
        val reached = field.viewModel.observation()?.book?.lines?.firstOrNull() == written

        Assume.assumeTrue(
            "${nothingYet.calledIt} is above this party's level, and nothing they can cast does nothing",
            reached,
        )

        assertNull(field.viewModel.act(AgentAction.BookLine(0)))
        field.until("the note") { Diagnostics.since(before).any { nothingYet.calledIt in it.said } }
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
    }
}
