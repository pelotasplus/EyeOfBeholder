package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.CarrySlot
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.DialogueTextId
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemIconId
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemNameId
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypeId
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.NpcMeeting
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.TrackIndex
import pl.pelotasplus.eyeofbeholder.data.model.WhatTheGameItselfRemembers
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The person waiting on level 1 at 15x11, who steps up as the party arrive.
 *
 * The square's script sets the flag that says the meeting has happened, turns
 * the party to face west, and hands over to the meeting. What the meeting is
 * made of is the original's: a sound, a piece that ends in asking to come
 * along, and one of two answers to what the party say.
 */
class MeetingAnNpcTest {

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
        ).loadInf("LEVEL1.INF").getOrThrow()
    }

    private val met = Location(15, 11)

    private fun arriving(answers: List<Int>): RecordingStage {
        val stage = RecordingStage(answers)

        runBlocking { walkedIn(answers, stage) }

        return stage
    }

    /** The world the meeting leaves behind, party and all. */
    private fun walkedIn(
        answers: List<Int>,
        stage: RecordingStage = RecordingStage(answers),
        party: List<Champion> = aPartyOf(5),
        items: List<Item> = emptyList(),
    ): GameState = runBlocking {
        LevelScriptRunner(level.script, level = 1).onEvent(
            triggers = level.triggers,
            event = ScriptEvent.PARTY_ENTERED,
            state = GameState(
                party = PartyState(met, Direction.NORTH),
                champions = party,
                items = items,
            ),
            stage = stage,
            at = met,
        ).state
    }

    /** [many] champions and the rest of the six empty, as a party is kept. */
    private fun aPartyOf(many: Int) = List(PARTY_SLOTS) { slot ->
        if (slot < many) {
            Champion.NOBODY.copy(name = "One", flags = ChampionFlags(IN_THE_PARTY))
        } else {
            Champion.NOBODY
        }
    }

    @Test
    fun `arriving is heard, and the party are turned to face whoever it is`() {
        val stage = arriving(answers = listOf(NO))

        assertTrue(TrackIndex(57) in stage.played, "nothing was heard of them arriving")
        assertTrue(stage.tookTheParty, "the party were not turned to face them")
    }

    /**
     * The turn is on screen before they are: the party are faced round to
     * whoever it is, the view is drawn again, and only then do they step into
     * it and speak. Drawn the other way about, they stand in front of whatever
     * the party were looking at before.
     */
    @Test
    fun `the view is drawn again before they are in it`() {
        val beats = arriving(answers = listOf(NO)).beats

        val shown = beats.indexOfFirst { it is RecordingStage.Beat.Shown }
        val spoke = beats.indexOfFirst { it is RecordingStage.Beat.Asked }

        assertTrue(shown in 0 until spoke, "they spoke before the view was drawn again")
        assertEquals(
            Direction.WEST,
            (beats[shown] as RecordingStage.Beat.Shown).world.party.facing,
            "the view drawn for them is not the one the party were turned to",
        )
    }

    /** The piece they say is asked as a question, with two ways to answer it. */
    @Test
    fun `they ask to come along`() {
        val asked = arriving(answers = listOf(NO)).questions.first()

        assertEquals(DialogueTextId(1), asked.textId)
        assertEquals(listOf(NpcMeeting.YES, NpcMeeting.NO), asked.words)
    }

    @Test
    fun `saying yes is answered by what they say to being let along`() {
        val questions = arriving(answers = listOf(YES)).questions

        assertEquals(
            listOf(DialogueTextId(1), DialogueTextId(3)),
            questions.map { it.textId },
        )
    }

    @Test
    fun `and saying no by what they say to being turned down`() {
        val questions = arriving(answers = listOf(NO)).questions

        assertEquals(
            listOf(DialogueTextId(1), DialogueTextId(2)),
            questions.map { it.textId },
        )
    }

    /**
     * Both answers are read rather than answered: whatever they say to it is
     * the end of the meeting, and the word in the corner takes it down.
     */
    @Test
    fun `what they say to either is read and then done with`() {
        listOf(YES, NO).forEach { answer ->
            val said = arriving(answers = listOf(answer)).questions.last()

            assertTrue(said.waitsToBeRead, "answer $answer left something to answer")
        }
    }

    /**
     * Saying yes takes him into the first free place, as the party's sixth.
     * Who he is comes out of the original's own table: a halfling thief of the
     * sixth level, and three hit points of thirty-nine, which is what he is
     * asking to be got out of.
     */
    @Test
    fun `saying yes takes him into the party`() {
        val after = walkedIn(answers = listOf(YES))
        val joined = after.champions.last()

        assertEquals("Insal", joined.name)
        assertTrue(joined.inTheParty, "he is in a slot but not in the party")
        assertEquals(CharacterClass.THIEF, joined.characterClass)
        assertEquals(6, joined.levels.single().level)
        assertEquals(HitPoints(current = 3, max = 39), joined.hitPoints)
        assertEquals(
            5,
            after.champions.count { it.inTheParty && it.name != "Insal" },
            "somebody else's place was taken",
        )
    }

    /**
     * He comes with nothing, which is not the same as coming with nowhere to
     * put anything: somebody joining with no slots at all is somebody whose
     * hand cannot be given a thing, and handing them one threw.
     */
    @Test
    fun `what he comes with is empty slots rather than no slots`() {
        val after = walkedIn(answers = listOf(YES))
        val joined = after.champions.last()

        assertEquals(CarrySlot.ALL_OF_THEM, joined.carrying.size)
        assertTrue(joined.carrying.none { it.isSomething }, "he came carrying something")

        val given = after.carrying(
            champion = PartySlot(after.champions.lastIndex),
            slot = CarrySlot(0),
            item = ItemIndex(1),
        )

        assertEquals(
            ItemIndex(1),
            given.champions.last().holding(CarrySlot(0)),
            "the thing did not end up in his hand",
        )
    }

    @Test
    fun `saying no leaves the party as it was`() {
        val after = walkedIn(answers = listOf(NO))

        assertTrue(after.champions.none { it.name == "Insal" }, "he came along anyway")
    }

    /**
     * The game remembers the join itself: no script sets the bit, and the one
     * that reads it is on another floor entirely.
     */
    @Test
    fun `the game remembers whether he was let along`() {
        assertTrue(
            walkedIn(answers = listOf(YES)).flags
                .has(WhatTheGameItselfRemembers.SOMEBODY_WAS_LET_ALONG),
        )
        assertFalse(
            walkedIn(answers = listOf(NO)).flags
                .has(WhatTheGameItselfRemembers.SOMEBODY_WAS_LET_ALONG),
        )
    }

    /**
     * Somebody walking up cannot also be a pile of their own bones in the
     * pack, so joining takes those out. Anybody else's are left alone, and so
     * is anything the party are not carrying.
     */
    @Test
    fun `his bones are let go of as he joins`() {
        val bones = Item(
            nameUnidentified = ItemNameId(0),
            nameIdentified = ItemNameId(0),
            flags = 0,
            icon = ItemIconId(1),
            type = ItemTypeId(BONES),
            place = SquarePlace.MIDDLE,
            location = Item.CARRIED,
            next = 0,
            prev = 0,
            level = Item.CARRIED_LEVEL,
            value = 1,
        )
        val somebodyElses = bones.copy(value = 2)

        // slot 0 of the table is the nothing every empty hand and pack slot
        // names, so a real thing is never in it
        val carrier = Champion.NOBODY.copy(
            name = "One",
            flags = ChampionFlags(IN_THE_PARTY),
            carrying = listOf(HIS_BONES, SOMEBODY_ELSES_BONES),
        )

        val after = walkedIn(
            answers = listOf(YES),
            party = listOf(carrier) + aPartyOf(0).drop(1),
            items = listOf(bones.copy(location = Item.NOWHERE), bones, somebodyElses),
        )

        assertFalse(after.items[HIS_BONES.value].exists, "his own bones were kept")
        assertTrue(
            after.items[SOMEBODY_ELSES_BONES.value].exists,
            "somebody else's bones were thrown away",
        )
        assertFalse(
            after.champions.first().carrying.contains(HIS_BONES),
            "the slot still points at bones that are gone",
        )
    }

    private companion object {
        const val YES = 1
        const val NO = 2

        const val PARTY_SLOTS = 6
        const val IN_THE_PARTY = 0x01

        /** What a pile of bones is, as the item table counts kinds. */
        const val BONES = 33

        val HIS_BONES = ItemIndex(1)
        val SOMEBODY_ELSES_BONES = ItemIndex(2)
    }
}
