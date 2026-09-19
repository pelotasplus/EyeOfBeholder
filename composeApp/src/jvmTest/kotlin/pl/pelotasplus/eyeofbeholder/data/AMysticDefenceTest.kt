package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSpell
import pl.pelotasplus.eyeofbeholder.data.model.RunningSpell
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.SparksOverTheParty
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.SpellMessages
import pl.pelotasplus.eyeofbeholder.data.model.Ticks
import pl.pelotasplus.eyeofbeholder.data.model.Wand
import pl.pelotasplus.eyeofbeholder.data.model.WhereASpellLands
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
import pl.pelotasplus.eyeofbeholder.data.model.spellsRunThroughARest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Starfire wand's mystic defence: the party shielded against the last
 * floor's dragon for 546 ticks, the first blast of its fire doing four dice
 * of ten and six over instead of twelve, and that blast using the shield up.
 */
class AMysticDefenceTest {

    /** Every die at its most, and every saving throw failed. */
    private val atItsWorst = Dice { times, pips, modifier ->
        if (times == 1 && pips == 20) 1 else times * pips + modifier
    }

    private val caster = PartySlot(2)

    private fun aChampion() = Champion.NOBODY.copy(
        name = "Anselm",
        flags = ChampionFlags(1),
        hitPoints = HitPoints(300, 300),
        levels = listOf(ClassLevel(5, XpPoints(0))),
    )

    private fun world() = GameState(
        party = PartyState(Location(3, 11), Direction.NORTH),
        champions = List(6) { aChampion() },
    )

    private fun GameState.shielded() = assertNotNull(mysticDefenceCast(caster))

    private fun GameState.fireLandsOn() =
        WhereASpellLands(atItsWorst).of(MonsterSpell.MONSTER_FIREBALL, this)

    // --- casting -----------------------------------------------------------

    @Test
    fun `the Starfire wand casts it`() {
        assertEquals(Spell.MYSTIC_DEFENCE, Wand.of(5)?.casts)
    }

    @Test
    fun `casting it shields the party`() {
        val after = world().shielded()

        assertTrue(after.partyShielded)
        assertEquals(
            RunningSpell(Spell.MYSTIC_DEFENCE, castBy = caster, ticksLeft = 546),
            after.mysticDefence,
        )
    }

    @Test
    fun `a shield already up refuses another`() {
        assertNull(world().shielded().mysticDefenceCast(PartySlot(0)))
    }

    @Test
    fun `once the shield is used it can be put up again, afresh`() {
        val spent = world().shielded()
            .spellsRunDown(Ticks(100)).first
            .mysticDefenceSpent()

        val again = assertNotNull(spent.mysticDefenceCast(PartySlot(0)))

        assertEquals(
            RunningSpell(Spell.MYSTIC_DEFENCE, castBy = PartySlot(0), ticksLeft = 546),
            again.mysticDefence,
        )
    }

    @Test
    fun `it runs out after 546 ticks and not before`() {
        val shielded = world().shielded()

        assertNotNull(shielded.spellsRunDown(Ticks(545)).first.mysticDefence)
        assertNull(shielded.spellsRunDown(Ticks(546)).first.mysticDefence)
    }

    /** An hour of rest counts as 32,760 ticks, and the spell lasts 546. */
    @Test
    fun `a rest of any length ends it`() {
        assertNull(world().shielded().spellsRunThroughARest(hours = 1).mysticDefence)
        assertNull(world().shielded().mysticDefenceSpent().spellsRunThroughARest(hours = 8).mysticDefence)
    }

    @Test
    fun `a rest cut short before an hour has passed leaves it up`() {
        assertTrue(world().shielded().spellsRunThroughARest(hours = 0).partyShielded)
    }

    // --- the fire ----------------------------------------------------------

    @Test
    fun `unshielded, the fire does twelve dice of ten and six over to each of them`() {
        val after = world().fireLandsOn().world

        after.champions.forEach { assertEquals(300 - (12 * 10 + 6), it.hitPoints.current) }
    }

    @Test
    fun `shielded, it does four dice of ten and six over`() {
        val after = world().shielded().fireLandsOn().world

        after.champions.forEach { assertEquals(300 - (4 * 10 + 6), it.hitPoints.current) }
    }

    @Test
    fun `the blast uses the shield up, and the spell runs on`() {
        val after = world().shielded().fireLandsOn().world

        assertFalse(after.partyShielded)
        assertEquals(true, after.mysticDefence?.spent)
    }

    @Test
    fun `the next blast is the full fire again`() {
        val after = world().shielded().fireLandsOn().world.fireLandsOn().world

        val bothBlasts = (4 * 10 + 6) + (12 * 10 + 6)
        after.champions.forEach { assertEquals(300 - bothBlasts, it.hitPoints.current) }
    }

    @Test
    fun `a lesser fireball is not turned and does not use the shield`() {
        val after = WhereASpellLands(atItsWorst).of(MonsterSpell.MONSTER_LESSER_FIREBALL, world().shielded())

        assertTrue(after.world.partyShielded)
    }

    // --- what is said and shown --------------------------------------------

    @Test
    fun `the lines are the game's`() {
        assertEquals(
            "The party is already under the effect of a mystic defense spell.",
            SpellMessages.alreadyOnTheParty(Spell.MYSTIC_DEFENCE.calledIt),
        )
        assertEquals(
            "Anselm's mystic defense spell expires.",
            SpellMessages.expires("Anselm", Spell.MYSTIC_DEFENCE.calledIt),
        )
    }

    /** The spells whose casting on the whole party sparkles over the portraits. */
    @Test
    fun `it sparkles over the party as every spell on the whole party does`() {
        assertEquals(
            listOf(3, 8, 14, 16, 29, 34, 37, 43, 46, 47, 51, 57, 64),
            Spell.entries.filter { it.sparksOverTheParty }.map { it.asWritten },
        )
    }

    @Test
    fun `the sparks run thirty-two frames and go out`() {
        var sparks: SparksOverTheParty? = SparksOverTheParty()
        var frames = 0
        while (sparks != null) {
            frames++
            sparks = sparks.next()
        }

        assertEquals(32, frames)
    }

    @Test
    fun `the first frame lights only the first spark of each box`() {
        val first = SparksOverTheParty()

        assertEquals(listOf(1, 0, 0, 0), (0 until 4).map { first.showing(it) })
    }

    @Test
    fun `each spark sits where the game puts it over its box`() {
        assertEquals(184 to 8, SparksOverTheParty.x(PartySlot(0), 0) to SparksOverTheParty.y(PartySlot(0), 0))
        assertEquals(276 to 122, SparksOverTheParty.x(PartySlot(5), 1) to SparksOverTheParty.y(PartySlot(5), 1))
    }
}
