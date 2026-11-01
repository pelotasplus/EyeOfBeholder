package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.CarrySlot
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemIconId
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemNameId
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypeId
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.NpcId
import pl.pelotasplus.eyeofbeholder.data.model.NpcMeeting
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.Race
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.ScriptStage
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.Trigger
import pl.pelotasplus.eyeofbeholder.data.model.TriggerFlags
import pl.pelotasplus.eyeofbeholder.data.model.script.Conditional
import pl.pelotasplus.eyeofbeholder.data.model.script.Eval
import pl.pelotasplus.eyeofbeholder.data.model.script.Script
import pl.pelotasplus.eyeofbeholder.data.model.script.ScriptOffset
import pl.pelotasplus.eyeofbeholder.data.model.script.Sound
import pl.pelotasplus.eyeofbeholder.data.model.script.SpecialEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The fifth floor's plaque, which grants three raisings. What each test expects
 * is the set piece as the game defines it: the dead, then whoever's bones are
 * carried, then a way out; a raised champion stands on one hit point; bones
 * become their owner at the numbers in the game's table of six.
 */
class AskingWhoToRaiseTest {

    private val here = Location(1, 2)

    private fun someone(name: String) = Champion.NOBODY.copy(
        name = name,
        flags = ChampionFlags(IN_THE_PARTY),
        hitPoints = HitPoints(30, 30),
        characterClass = CharacterClass.FIGHTER,
        carrying = CarrySlot.NOTHING_IN_ANY,
    )

    private val elf = someone("Elf").copy(
        race = Race.ELF,
        hitPoints = HitPoints(Champion.BEYOND_RAISING, 30),
    )
    private val fallen = someone("Fallen").copy(hitPoints = HitPoints(-3, 30))

    private val nothing = Item(
        nameUnidentified = ItemNameId(0),
        nameIdentified = ItemNameId(0),
        flags = 0,
        icon = ItemIconId(1),
        type = ItemTypeId(0),
        place = SquarePlace.MIDDLE,
        location = Item.NOWHERE,
        next = 0,
        prev = 0,
        level = 0,
        value = 0,
    )

    private fun bones(value: Int) = nothing.copy(
        type = ItemTypeId(BONES),
        location = Item.CARRIED,
        level = Item.CARRIED_LEVEL,
        value = value,
    )

    private fun carrying(vararg values: Int): List<ItemIndex> =
        CarrySlot.NOTHING_IN_ANY.toMutableList().also { pack ->
            values.indices.forEach { pack[it] = ItemIndex(it + 1) }
        }

    private fun world(champions: List<Champion>, vararg values: Int) = GameState(
        party = PartyState(here, Direction.SOUTH),
        champions = champions,
        items = listOf(nothing) + values.map { bones(it) },
    )

    /** The plaque's own shape: a sound only where the answer reads back as one. */
    private fun run(world: GameState, stage: ScriptStage): GameState = runBlocking {
        val script = listOf(
            0 to SpecialEvent.ResurrectionSelectDialogue,
            10 to Eval(
                listOf(Conditional.DialogResult, Conditional.ImmediateShort(1), Conditional.Equals),
                goto = ScriptOffset(30),
            ),
            20 to Sound(91, Location(0, 0)),
        ).map { (at, token) -> Script(ScriptOffset(at), token) }

        LevelScriptRunner(script, level = 5).onEvent(
            triggers = listOf(Trigger(here, TriggerFlags(0x08), script.first())),
            event = ScriptEvent.PARTY_ENTERED,
            state = world,
            stage = stage,
            at = here,
        ).state
    }

    @Test
    fun `it offers the dead, then the bones, then a way out`() {
        val picks = Picks(4)
        val champions = listOf(someone("Standing"), fallen, elf).let { (a, b, c) ->
            listOf(a.copy(carrying = carrying(SHORNS)), b, c)
        }

        run(world(champions, SHORNS), picks)

        assertEquals(listOf("Elf", "Shorn", NpcMeeting.ABORT), picks.asked.single().words.take(3))
    }

    @Test
    fun `an elf past raising comes back on one hit point, as the spell would not have it`() {
        val picks = Picks(1)
        val after = run(world(listOf(someone("Standing"), elf)), picks)

        assertEquals(1, after.champions[1].hitPoints.current)
        assertEquals(1, picks.played.size, "a raising did not read back as one")
        assertEquals(1, picks.shown.last().champions[1].hitPoints.current, "the panel still shows them dead")
    }

    @Test
    fun `bones of somebody already in the party are not offered`() {
        val picks = Picks(1)
        val carrier = someone("Carrier").copy(carrying = carrying(SHORNS))
        val shorn = requireNotNull(NpcMeeting.joiningAs(NpcId(SHORNS - 1)))

        val after = run(world(listOf(carrier, shorn), SHORNS), picks)

        assertEquals(listOf(NpcMeeting.ABORT), picks.asked.single().words)
        assertEquals(1, after.champions.count { it.name == "Shorn" })
    }

    @Test
    fun `the way out raises nobody and reads back as nothing`() {
        val picks = Picks(2)
        val after = run(world(listOf(someone("Standing"), elf)), picks)

        assertEquals(Champion.BEYOND_RAISING, after.champions[1].hitPoints.current)
        assertTrue(picks.played.isEmpty(), "the way out spent a raising")
    }

    @Test
    fun `bones become their owner, and are gone`() {
        val carrier = someone("Carrier").copy(carrying = carrying(SAN_RAALS))
        val after = run(world(listOf(carrier, Champion.NOBODY), SAN_RAALS), Picks(1))

        val him = after.champions[1]
        assertEquals("San-raal", him.name)
        assertEquals(HitPoints(28, 28), him.hitPoints)
        assertEquals(CharacterClass.MAGE, him.characterClass)
        assertEquals(Race.ELF, him.race)
        assertEquals(listOf(8), him.levels.map { it.level })
        assertTrue(after.bonesCarried.isEmpty(), "the party still carry the bones of someone standing with them")
    }

    /** Her book's word in the game's table, 0x00294566, has ten bits set. */
    @Test
    fun `amber comes as a thief and mage of the seventh level with her book`() {
        val amber = requireNotNull(NpcMeeting.joiningAs(NpcId(5)))

        assertEquals("Amber", amber.name)
        assertEquals(CharacterClass.THIEF_MAGE, amber.characterClass)
        assertEquals(listOf(7, 7), amber.levels.map { it.level })
        assertEquals(10, amber.knownMageSpells.size)
    }

    @Test
    fun `bones of nobody the game knows are not offered`() {
        val picks = Picks(1)
        val carrier = someone("Carrier").copy(carrying = carrying(99))

        run(world(listOf(carrier), 99), picks)

        assertEquals(listOf(NpcMeeting.ABORT), picks.asked.single().words)
    }

    /** Six in the party: somebody has to go, and they must not drop the bones as they do. */
    @Test
    fun `dropping somebody to make room leaves no second set of bones`() {
        val carrier = someone("Carrier").copy(carrying = carrying(SHORNS))
        val six = listOf(carrier) + (2..6).map { someone("P$it") }

        val after = run(world(six, SHORNS), Picks(1, 1))

        assertTrue(after.champions.any { it.name == "Shorn" }, "he did not join")
        assertFalse(after.items.any { it.exists && it.type == ItemTypeId(BONES) }, "the bones are still in the world")
    }

    /** Having said a name, backing out of dropping anyone is still a raising spent. */
    @Test
    fun `backing out of making room keeps the bones and still spends the use`() {
        val carrier = someone("Carrier").copy(carrying = carrying(SHORNS))
        val six = listOf(carrier) + (2..6).map { someone("P$it") }
        val picks = Picks(1, 7)

        val after = run(world(six, SHORNS), picks)

        assertEquals(listOf(NpcId(SHORNS - 1)), after.bonesCarried)
        assertEquals(1, picks.played.size)
    }

    private companion object {
        const val IN_THE_PARTY = 0x01
        const val BONES = 33
        const val SHORNS = 3
        const val SAN_RAALS = 4
    }
}
