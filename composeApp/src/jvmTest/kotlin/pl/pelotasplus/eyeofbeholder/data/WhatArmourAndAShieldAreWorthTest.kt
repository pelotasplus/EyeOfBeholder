package pl.pelotasplus.eyeofbeholder.data

import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Abilities
import pl.pelotasplus.eyeofbeholder.data.model.Ability
import pl.pelotasplus.eyeofbeholder.data.model.ArmorClass
import pl.pelotasplus.eyeofbeholder.data.model.ArmourHeldAt
import pl.pelotasplus.eyeofbeholder.data.model.praysAsSomeoneOfLevel
import pl.pelotasplus.eyeofbeholder.data.model.CarrySlot
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.Damage
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.HowLongASpellRuns
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSpell
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.PortraitId
import pl.pelotasplus.eyeofbeholder.data.model.SettlesOn
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.SpellLasts
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.Ticks
import pl.pelotasplus.eyeofbeholder.data.model.TrackIndex
import pl.pelotasplus.eyeofbeholder.data.model.WhereASpellLands
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The two spells that armour somebody, and the one rule they share: each is a
 * ceiling on armour class rather than a bonus to it.
 *
 * That is what makes them behave unlike everything else on the party. Neither
 * is worth anything to somebody already better armoured, the two together are
 * worth no more than the better of them, and taking plate off while one runs
 * costs its wearer nothing at all down to the ceiling.
 *
 * Where they differ is how they end. A shield runs on a clock. A suit of
 * conjured armour has none: it is worn through by its wearer being hit, and
 * it is over the moment it stops being worth anything.
 */
@Category(NeedsGameData::class)
class WhatArmourAndAShieldAreWorthTest {

    private val caster = PartySlot(0)
    private val other = PartySlot(1)

    // --- what the two spells are ----------------------------------------------

    @Test
    fun `a shield holds its reader at four for two and a half minutes a level`() {
        assertEquals(ArmourHeldAt.At(ArmorClass(4)), Spell.SHIELD.holdsArmourClassAt)
        assertEquals(SettlesOn.WHOEVER_CAST_IT, Spell.SHIELD.settlesOn)

        val lasts = assertIs<SpellLasts>(Spell.SHIELD.lasts)
        assertEquals(SpellLasts(base = 0, perLevel = 5), lasts)
        assertEquals(Ticks(2730), lasts.castBySomeoneOfLevel(1))
        assertEquals(Ticks(24570), lasts.castBySomeoneOfLevel(OFF_A_SCROLL))
    }

    /**
     * Six over whatever their own quickness is worth, on whoever is pointed
     * at, and no clock at all.
     */
    @Test
    fun `a suit of armour holds whoever it is laid on at six over their own quickness`() {
        assertEquals(ArmourHeldAt.OverTheirNimbleness(points = 6), Spell.ARMOUR.holdsArmourClassAt)
        assertEquals(SettlesOn.WHOEVER_IS_POINTED_AT, Spell.ARMOUR.settlesOn)

        val lasts = assertIs<HowLongASpellRuns.WornThroughByHarm>(Spell.ARMOUR.lasts)
        assertEquals(HowLongASpellRuns.WornThroughByHarm(base = 8, perLevel = 1), lasts)
        assertEquals(9, lasts.castBySomeoneOfLevel(1))
        assertEquals(17, lasts.castBySomeoneOfLevel(OFF_A_SCROLL))
    }

    /**
     * Both are heard as the same noise and both frame the box the defensive
     * colour — the one a blur draws, not the one a haste draws. Which spell
     * draws which is a list rather than a rule, so it is named here.
     */
    @Test
    fun `both are heard as the same noise, and both frame the box as a blur does`() {
        assertEquals(TrackIndex(92), Spell.ARMOUR.heardAs)
        assertEquals(TrackIndex(92), Spell.SHIELD.heardAs)

        assertTrue(Spell.ARMOUR.framesTheBoxRed)
        assertTrue(Spell.SHIELD.framesTheBoxRed)
        assertTrue(Spell.BLUR.framesTheBoxRed)

        assertFalse(Spell.ARMOUR.framesTheBoxYellow)
        assertFalse(Spell.SHIELD.framesTheBoxYellow)
        assertTrue(Spell.HASTE.framesTheBoxYellow, "the other frame is still somebody's")
    }

    // --- the ceiling ----------------------------------------------------------

    /** Ten bare and unhurried, held at four by the shield. */
    @Test
    fun `a shield takes an unarmoured reader to four`() {
        val world = world(unhurried())

        assertEquals(ArmorClass(10), world.armourClassOf(caster))
        assertEquals(ArmorClass(4), world.shielded().armourClassOf(caster))
    }

    /** And is worth nothing whatever to somebody already better than four. */
    @Test
    fun `a shield is worth nothing to somebody already better armoured`() {
        val world = world(unhurried().copy(armorClass = ArmorClass(2)))

        assertEquals(ArmorClass(2), world.shielded().armourClassOf(caster))
    }

    /**
     * A conjured suit is six over the wearer's own quickness, so it is worth
     * more to somebody nimble — which is the one way the two spells differ in
     * what they are worth rather than in how they end.
     */
    @Test
    fun `conjured armour counts the wearer's quickness and a shield does not`() {
        val quick = world(nimble()).armoured()
        val slow = world(unhurried()).armoured()

        assertEquals(ArmorClass(6 + A_NIMBLE_HAND), quick.armourClassOf(caster))
        assertEquals(ArmorClass(6), slow.armourClassOf(caster))

        assertEquals(ArmorClass(4), world(nimble()).shielded().armourClassOf(caster))
    }

    /** Two of them are two ceilings and not a sum: the better one wins. */
    @Test
    fun `a shield over conjured armour is worth the better of the two and no more`() {
        val both = world(unhurried()).armoured().shielded()

        assertEquals(ArmorClass(4), both.armourClassOf(caster))
    }

    /** And neither reaches anybody else. */
    @Test
    fun `a shield is on its reader alone`() {
        val world = world(unhurried(), unhurried()).shielded()

        assertEquals(ArmorClass(4), world.armourClassOf(caster))
        assertEquals(ArmorClass(10), world.armourClassOf(other))
    }

    /** A blow is rolled against the ceiling, not against what they are wearing. */
    @Test
    fun `the number a blow is rolled against is the one the spell holds them at`() {
        val world = world(unhurried()).shielded()

        assertEquals(ArmorClass(10), world.championIn(caster)!!.armorClass)
        assertEquals(ArmorClass(4), world.armourClassOf(caster))
    }

    // --- how each of them ends ------------------------------------------------

    /** A shield goes with its clock, and nothing else ends it. */
    @Test
    fun `a shield stands to its last tick`() {
        val shielded = world(unhurried()).shielded()

        assertEquals(ArmorClass(4), shielded.hurtFor(20).armourClassOf(caster))
        assertEquals(ArmorClass(4), shielded.after(Ticks(24569)).armourClassOf(caster))
        assertEquals(ArmorClass(10), shielded.after(Ticks(24570)).armourClassOf(caster))
    }

    /**
     * A suit of conjured armour has no clock at all, so no length of time
     * finishes it — a party who stand still keep it.
     */
    @Test
    fun `conjured armour is not counted down by time`() {
        val armoured = world(unhurried()).armoured()

        assertFalse(armoured.running.anyOnAClock)
        assertEquals(ArmorClass(6), armoured.after(Ticks(100000)).armourClassOf(caster))
    }

    /**
     * It is worn through by its wearer being hit instead: eight points and
     * another for every level of whoever read it, counted whole however the
     * blows fall.
     */
    @Test
    fun `conjured armour is worn through by its wearer being hit`() {
        val armoured = world(unhurried()).armoured()

        assertEquals(ArmorClass(6), armoured.hurtFor(THE_SUIT_HOLDS - 1).armourClassOf(caster))
        assertEquals(ArmorClass(10), armoured.hurtFor(THE_SUIT_HOLDS).armourClassOf(caster))
    }

    /** And by the blows added up rather than by any one of them. */
    @Test
    fun `the points that wear it through are counted together`() {
        var world = world(unhurried()).armoured()
        repeat(THE_SUIT_HOLDS) { world = world.hurtFor(1) }

        assertEquals(ArmorClass(10), world.armourClassOf(caster))
        assertNull(world.running.all.firstOrNull { it.spell == Spell.ARMOUR })
    }

    /** Somebody else being hit does not wear it at all. */
    @Test
    fun `another champion taking the blows leaves it whole`() {
        val armoured = world(unhurried(), unhurried()).armoured()
        val elsewhere = armoured.championHurt(other, Damage(THE_SUIT_HOLDS * 2))

        assertEquals(ArmorClass(6), elsewhere.armourClassOf(caster))
    }

    /**
     * And it is over the moment it stops being worth anything, which is not
     * the same as quietly doing nothing: the suit is what the spell is, so
     * better armour put on over it ends it.
     */
    @Test
    fun `conjured armour is over once its wearer is better armoured`() {
        val armoured = world(unhurried()).armoured()

        val plated = armoured.copy(
            champions = armoured.champions.toMutableList()
                .also { it[0] = it[0].copy(armorClass = ArmorClass(3)) },
        )
        val (after, dropped) = plated.armourThatStoppedHelpingDropped()

        assertEquals(listOf(Spell.ARMOUR), dropped.map { it.spell })
        assertNull(after.running.all.firstOrNull { it.spell == Spell.ARMOUR })
    }

    /** A shield is not armour and does not care what is worn under it. */
    @Test
    fun `a shield over better armour is not dropped`() {
        val shielded = world(unhurried().copy(armorClass = ArmorClass(2))).shielded()

        assertEquals(emptyList(), shielded.armourThatStoppedHelpingDropped().second)
    }

    // --- and the third of them ------------------------------------------------

    /**
     * A vestment is woven over whoever prayed for it, for two and a half
     * minutes a level — the longest of the three and the only one that asks
     * how practised its wearer is.
     */
    @Test
    fun `a vestment lasts two and a half minutes a level, on whoever prayed for it`() {
        assertEquals(ArmourHeldAt.AVestment, Spell.MAGICAL_VESTMENT.holdsArmourClassAt)
        assertEquals(SettlesOn.WHOEVER_CAST_IT, Spell.MAGICAL_VESTMENT.settlesOn)
        assertEquals(TrackIndex(113), Spell.MAGICAL_VESTMENT.heardAs)

        val lasts = assertIs<SpellLasts>(Spell.MAGICAL_VESTMENT.lasts)
        assertEquals(SpellLasts(base = 0, perLevel = 5), lasts)
        assertEquals(Ticks(24570), lasts.castBySomeoneOfLevel(OFF_A_SCROLL))
    }

    /**
     * Five, and a point worse for every three levels of cleric past the
     * fifth — so it holds at five up to the seventh level and loses ground
     * from the eighth on.
     *
     * Named level by level rather than as a formula, because a formula is
     * what is in doubt: this is the one rule in the three that reads like a
     * sign the wrong way round.
     */
    @Test
    fun `a vestment is worth less to a better cleric, not more`() {
        val vested = mapOf(1 to 5, 5 to 5, 6 to 5, 7 to 5, 8 to 6, 9 to 6, 10 to 6, 11 to 7, 14 to 8)

        vested.forEach { (level, held) ->
            assertEquals(
                ArmorClass(held),
                ArmourHeldAt.AVestment.forSomeone(cleric(level)),
                "a cleric of $level",
            )
        }
    }

    /** A paladin prays as somebody eight levels less practised, and anybody else as a novice. */
    @Test
    fun `a paladin is vested as a lesser cleric and a mage as none at all`() {
        assertEquals(1, cleric(1).praysAsSomeoneOfLevel)
        assertEquals(9, cleric(9).praysAsSomeoneOfLevel)
        assertEquals(1, paladin(8).praysAsSomeoneOfLevel, "a paladin below the ninth prays as a novice")
        assertEquals(3, paladin(11).praysAsSomeoneOfLevel)
        assertEquals(1, unhurried().praysAsSomeoneOfLevel, "a mage is no cleric")
    }

    /** And the ceiling is a ceiling here too, so plate under it is untouched. */
    @Test
    fun `a vestment over better armour is worth nothing`() {
        val plated = cleric(9).copy(armorClass = ArmorClass(2))
        val world = world(plated).let {
            assertNotNull(it.spellBegunWhereItSettles(Spell.MAGICAL_VESTMENT, caster, OFF_A_SCROLL))
        }

        assertEquals(ArmorClass(2), world.armourClassOf(caster))
    }

    // --- the one bolt a shield stops ------------------------------------------

    /**
     * A missile is the one thing in the game with no throw against it and
     * nothing to shrug it off with — and a shield is the one thing that stops
     * it, outright and with nothing taken.
     */
    @Test
    fun `a shield stops a magic missile dead`() {
        val landing = WhereASpellLands(dice = everyDieHighest, level = 9)
        val party = world(unhurried())

        val bare = landing.of(MonsterSpell.MAGIC_MISSILE, party, SquarePlace.NORTH_WEST)
        assertEquals(listOf(caster), bare.hurt, "the missile should have found the front left")

        val behindOne = landing.of(MonsterSpell.MAGIC_MISSILE, party.shielded(), SquarePlace.NORTH_WEST)
        assertEquals(emptyList(), behindOne.hurt)
        assertEquals(
            party.championIn(caster)!!.hitPoints.current,
            behindOne.world.championIn(caster)!!.hitPoints.current,
        )
    }

    /** One champion's shield is no shelter for the champion beside them. */
    @Test
    fun `a shield stops only the missiles that pick its own reader`() {
        val landing = WhereASpellLands(dice = everyDieHighest, level = 9)
        val party = world(unhurried(), unhurried()).shielded()

        val atTheOther = landing.of(MonsterSpell.MAGIC_MISSILE, party, SquarePlace.NORTH_EAST)

        assertEquals(listOf(other), atTheOther.hurt)
    }

    /** And it stops a missile only — a bolt of lightning goes through it. */
    @Test
    fun `a shield does not stop a bolt of lightning`() {
        val landing = WhereASpellLands(dice = everyDieHighest, level = 9)

        val struck = landing.of(
            MonsterSpell.LIGHTNING_BOLT,
            world(unhurried()).shielded(),
            SquarePlace.NORTH_WEST,
        )

        assertEquals(listOf(caster), struck.hurt)
    }

    // --- reading one again ----------------------------------------------------

    /**
     * A shield read again winds its clock back up rather than being refused,
     * which is written on the spell and not on the kind of spell it is.
     */
    @Test
    fun `a shield read again starts afresh`() {
        assertTrue(Spell.SHIELD.castAgainRatherThanRefused)

        val nearlyOut = world(unhurried()).shielded().after(Ticks(24000))
        val again = assertNotNull(
            nearlyOut.spellBegunWhereItSettles(Spell.SHIELD, by = caster, casterLevel = OFF_A_SCROLL),
        )

        assertEquals(1, again.running.all.count { it.spell == Spell.SHIELD }, "two shields are running")
        assertEquals(ArmorClass(4), again.after(Ticks(24000)).armourClassOf(caster))
    }

    /** Where most spells are refused, so that a scroll is not spent on nothing. */
    @Test
    fun `a blur read again is refused, as most of them are`() {
        assertFalse(Spell.BLUR.castAgainRatherThanRefused)

        val blurred = assertNotNull(
            world(unhurried()).spellBegunWhereItSettles(Spell.BLUR, by = caster, casterLevel = OFF_A_SCROLL),
        )

        assertNull(blurred.spellBegunWhereItSettles(Spell.BLUR, by = caster, casterLevel = OFF_A_SCROLL))
    }

    // --- the fixture ----------------------------------------------------------

    private fun GameState.shielded() = assertNotNull(
        spellBegunWhereItSettles(Spell.SHIELD, by = caster, casterLevel = OFF_A_SCROLL),
    )

    private fun GameState.armoured() = assertNotNull(
        spellBegunOn(Spell.ARMOUR, whom = caster, by = caster, casterLevel = OFF_A_SCROLL),
    )

    private fun GameState.hurtFor(points: Int) = championHurt(caster, Damage(points))

    private fun GameState.after(ticks: Ticks) = copy(running = running.runDown(ticks).first)

    private val everyDieHighest = Dice { times, pips, modifier -> times * pips + modifier }

    private fun world(vararg party: Champion) = GameState(
        party = PartyState(Location(13, 9), Direction.NORTH),
        champions = party.toList(),
    )

    private fun champion(dexterity: Int) = Champion(
        name = "Anselm",
        portrait = PortraitId(0),
        abilities = Abilities(strength = Ability(10, 10), dexterity = Ability(dexterity, dexterity)),
        hitPoints = HitPoints(200, 200),
        armorClass = ArmorClass(10),
        food = Food(100),
        characterClass = CharacterClass.MAGE,
        levels = listOf(ClassLevel(9, XpPoints(0))),
        carrying = List(CarrySlot.ALL_OF_THEM) { ItemIndex(ItemIndex.NOTHING) },
        flags = ChampionFlags(1),
    )

    /** Quick enough for it to be worth something, and clumsy enough that it is not. */
    private fun nimble() = champion(dexterity = 18)
    private fun unhurried() = champion(dexterity = 10)

    private fun cleric(level: Int) = champion(dexterity = 10).copy(
        characterClass = CharacterClass.CLERIC,
        levels = listOf(ClassLevel(level, XpPoints(0))),
    )

    private fun paladin(level: Int) = champion(dexterity = 10).copy(
        characterClass = CharacterClass.PALADIN,
        levels = listOf(ClassLevel(level, XpPoints(0))),
    )

    private companion object {
        /** A scroll is always read at ninth-level strength. */
        const val OFF_A_SCROLL = 9

        /** What eighteen dexterity is worth to an armour class. */
        const val A_NIMBLE_HAND = -4

        /** Eight points and another for every level of a ninth-level reader. */
        const val THE_SUIT_HOLDS = 8 + OFF_A_SCROLL
    }
}
