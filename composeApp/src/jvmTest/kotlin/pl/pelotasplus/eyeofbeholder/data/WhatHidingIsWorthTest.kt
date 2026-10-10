package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Abilities
import pl.pelotasplus.eyeofbeholder.data.model.Ability
import pl.pelotasplus.eyeofbeholder.data.model.ArmorClass
import pl.pelotasplus.eyeofbeholder.data.model.CarrySlot
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HOURS_A_MENDED_POINT
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.HowLongASpellRuns
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonstersTurn
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.PortraitId
import pl.pelotasplus.eyeofbeholder.data.model.SettlesOn
import pl.pelotasplus.eyeofbeholder.data.model.Spell
import pl.pelotasplus.eyeofbeholder.data.model.SpellLasts
import pl.pelotasplus.eyeofbeholder.data.model.Ticks
import pl.pelotasplus.eyeofbeholder.data.model.WhatTheBlowCameTo
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
import pl.pelotasplus.eyeofbeholder.data.model.spellsRunThroughARest
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
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Invisibility, and the 10' radius one that is the same spell over six
 * champions instead of one.
 *
 * The two of them are the first spells in the game with no clock: nothing
 * counts them down, and what finishes one is the champion under it doing
 * something. So there are two halves to test — that being hidden is worth
 * something to a monster choosing whom to hit, and that it is spent the
 * moment its champion acts.
 *
 * It is not armour, and the difference matters: a blow that does find a
 * hidden champion costs exactly what it would have cost anyone.
 */
@Category(NeedsGameData::class)
class WhatHidingIsWorthTest {

    private val resources = ResourceRepositoryImpl()

    private val level = runBlocking {
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

    private val kinds get() = level.subLevels[0].monsters

    private fun champion() = Champion(
        name = "Anselm",
        portrait = PortraitId(0),
        abilities = Abilities(strength = Ability(10, 10), dexterity = Ability(10, 10)),
        hitPoints = HitPoints(400, 400),
        armorClass = ArmorClass(10),
        food = Food(100),
        characterClass = CharacterClass.FIGHTER,
        levels = listOf(ClassLevel(1, XpPoints(0))),
        carrying = List(CarrySlot.ALL_OF_THEM) { ItemIndex(ItemIndex.NOTHING) },
        flags = ChampionFlags(IN_THE_PARTY),
    )

    private val everyDieHighest = Dice { times, pips, modifier -> times * pips + modifier }

    /** Every twenty comes up [face]; everything else rolls its highest. */
    private fun rolling(face: Int) = Dice { times, pips, modifier ->
        if (times == 1 && pips == 20) face else times * pips + modifier
    }

    private fun world() = GameState(
        party = PartyState(Location(13, 9), Direction.NORTH),
        champions = List(6) { champion() },
    ).arrivingAt(
        level = 5,
        places = level.monsterInstances,
        maz = level.subLevels[0].maz,
        kinds = kinds,
        dice = everyDieHighest,
    ).rousedBy(MonsterSlot(16))

    private fun swungThrough(from: GameState, dice: Dice): MonstersTurn.Taken {
        val turn = MonstersTurn(kinds, dice)
        var world = turn.begun(from).world
        world = world.swingsCarriedOn()
        return turn.landed(world, world.landingThisFrame)
    }

    /** One champion hidden by a plain invisibility read off a scroll. */
    private fun GameState.hiding(whom: PartySlot): GameState = assertNotNull(
        spellBegunOn(Spell.INVISIBILITY, whom, by = CASTER, casterLevel = OFF_A_SCROLL),
        "an invisibility would not settle on $whom",
    )

    /** And all six, by the 10' radius one, which is six hidings rather than one. */
    private fun GameState.allHiding(): GameState = assertNotNull(
        spellBegunWhereItSettles(Spell.INVISIBILITY_TEN_FEET, CASTER, OFF_A_SCROLL),
    )

    // --- no clock ------------------------------------------------------------

    /**
     * The table gives invisibility no duration at all, where every other
     * lasting spell gets one — a blur gets three half-minutes and one more
     * per level, an improved invisibility five and one more. This one gets
     * nothing, and that absence is the spell.
     */
    @Test
    fun `neither of them is given a clock`() {
        assertEquals(HowLongASpellRuns.UntilSomethingBreaksIt, Spell.INVISIBILITY.lasts)
        assertEquals(HowLongASpellRuns.UntilSomethingBreaksIt, Spell.INVISIBILITY_TEN_FEET.lasts)
    }

    @Test
    fun `a hiding that is running has nothing left to count`() {
        val hidden = world().hiding(PartySlot(0))

        assertNull(hidden.running.all.single().ticksLeft)
        assertFalse(hidden.running.anyOnAClock, "a hiding should give the clock nothing to turn")
    }

    /**
     * No length of time ends one. An hour of rest is longer than every
     * counted spell in the game put together, and this one walks out of it.
     */
    @Test
    fun `no amount of time ends it, a night of rest included`() {
        val hidden = world().hiding(PartySlot(0))

        val (later, ended) = hidden.spellsRunDown(Ticks(AN_HOUR))
        assertEquals(emptyList(), ended, "time should not have ended a hiding")
        assertTrue(later.running.unseen(PartySlot(0)))

        val rested = hidden.spellsRunThroughARest(HOURS_A_MENDED_POINT)
        assertTrue(rested.running.unseen(PartySlot(0)), "a night's sleep ended a hiding")
    }

    // --- whom it settles on --------------------------------------------------

    /** Asked which champion, the way an aid is, and spent on nobody if nobody is named. */
    @Test
    fun `the plain one asks which champion`() {
        assertEquals(SettlesOn.WHOEVER_IS_POINTED_AT, Spell.INVISIBILITY.settlesOn)
    }

    /**
     * And the 10' one is six hidings rather than one over the party, which is
     * what lets one champion step out of cover and leave the other five in it.
     */
    @Test
    fun `the ten foot one settles on each of them separately`() {
        assertEquals(SettlesOn.EVERY_CHAMPION, Spell.INVISIBILITY_TEN_FEET.settlesOn)

        val all = world().allHiding()

        assertEquals(6, all.running.all.size)
        assertEquals(
            (0 until 6).map { PartySlot(it) },
            all.running.all.map { it.on },
            "each champion should carry their own",
        )
    }

    /** It hides; it does not shield. A blow that lands lands for its full worth. */
    @Test
    fun `it takes nothing off a blow that finds them anyway`() {
        val hidden = world().hiding(PartySlot(0))

        assertEquals(0, hidden.running.hindranceStriking(PartySlot(0)))
    }

    // --- being passed over ---------------------------------------------------

    /**
     * Four faces of twenty find them and sixteen do not, so the boundary is
     * where the whole worth of the spell sits: a step either way turns it into
     * a different spell. The two faces on the boundary are named rather than
     * the proportion, because a proportion is what an off-by-one still passes.
     */
    @Test
    fun `a four finds a hidden champion and a five does not`() {
        val hidden = world().hiding(PartySlot(0))

        assertTrue(
            hidden.whoAMonsterCanSee(PartySlot(0), rolling(4)),
            "a four should have found them",
        )
        assertFalse(
            hidden.whoAMonsterCanSee(PartySlot(0), rolling(5)),
            "a five should have passed over them",
        )
    }

    /** And nobody who is not hiding is ever passed over, on any face of the die. */
    @Test
    fun `a champion nobody hid is seen on every face`() {
        val bare = world()

        (1..20).forEach { face ->
            assertTrue(
                bare.whoAMonsterCanSee(PartySlot(0), rolling(face)),
                "a $face passed over somebody who was not hiding",
            )
        }
    }

    /**
     * The one that says the spell reaches the dice rather than only the model:
     * the same monster, the same swing, the same roll, and the only difference
     * between the two runs is who is hiding.
     *
     * A twenty is used because it passes over a hidden champion and lands on
     * a bare one, so one roll shows both halves at once.
     */
    @Test
    fun `a monster swings at the party and finds nobody once they are all hidden`() {
        val bare = swungThrough(world(), rolling(20))
        val hidden = swungThrough(world().allHiding(), rolling(20))

        assertTrue(bare.struck.isNotEmpty(), "the monster should have reached somebody")
        assertEquals(
            emptyList(),
            hidden.struck,
            "the monster reached somebody who was hiding from it",
        )
    }

    /**
     * A chance rather than a wall: on a face that sees through it, a hidden
     * party are reached exactly as a bare one is.
     *
     * The face is searched for rather than named, because a rigged die rigs
     * every roll of that size — the monster's own choice of what to do with
     * its turn included — so the only faces this can be asked at are the ones
     * where a bare monster does come out swinging.
     */
    @Test
    fun `a face that sees through it reaches them as if they were not hiding`() {
        val seenThrough = assertNotNull(
            (1 until SEEN_ANYWAY).firstOrNull { face ->
                swungThrough(world(), rolling(face)).struck.isNotEmpty()
            },
            "no face under $SEEN_ANYWAY has the monster swinging at all",
        )

        val bare = swungThrough(world(), rolling(seenThrough)).struck.map { it.at }
        val hidden = swungThrough(world().allHiding(), rolling(seenThrough)).struck.map { it.at }

        assertEquals(bare, hidden, "hiding changed who was reached on a face that sees through it")
    }

    /**
     * Hiding the front rank moves the beating rather than stopping it: the
     * monster passes over whoever it reaches first and swings at the next
     * champion along.
     *
     * Which champion it reaches first is read off the bare run rather than
     * assumed, because that depends on the monster's own facing and where it
     * stands on its square.
     */
    @Test
    fun `the blow goes on to whoever stands behind the hidden`() {
        val firstReached = assertNotNull(
            swungThrough(world(), rolling(20)).struck.firstOrNull()?.at,
            "the monster reached nobody to begin with",
        )

        val behind = swungThrough(world().hiding(firstReached), rolling(20))

        assertTrue(behind.struck.isNotEmpty(), "hiding one champion stopped the swing entirely")
        assertFalse(
            behind.struck.any { it.at == firstReached },
            "the monster swung at the one champion who was hiding",
        )
    }

    // --- giving yourself away ------------------------------------------------

    @Test
    fun `a swing gives away whoever swung`() {
        val hidden = world().hiding(PartySlot(0))

        val swung = hidden.handSwung(PartySlot(0), CarrySlot(0), WhatTheBlowCameTo.Missed)

        assertFalse(swung.running.unseen(PartySlot(0)), "a swing left them hidden")
    }

    /**
     * Including a swing that found nothing. What gives a champion away is
     * having done something, not having done something that worked, so an
     * empty bow raised at an empty corridor costs the hiding too.
     */
    @Test
    fun `a throw or a shot gives them away as surely as a swing`() {
        val hidden = world().hiding(PartySlot(0))

        assertFalse(hidden.handLoosed(PartySlot(0), CarrySlot(0)).running.unseen(PartySlot(0)))
    }

    /** An aimed spell does it. The words carry, and whoever said them is there. */
    @Test
    fun `casting something aimed gives them away`() {
        val hidden = world().hiding(PartySlot(0))

        val cast = hidden.handCast(PartySlot(0), CarrySlot(0), Spell.CONE_OF_COLD)

        assertFalse(cast.running.unseen(PartySlot(0)))
    }

    /**
     * And one cast over the party does not, which is what makes hiding worth
     * anything: a party can be hidden and still be hastened, aided and shown
     * what is magical.
     */
    @Test
    fun `casting something over the party leaves them hidden`() {
        val hidden = world().hiding(PartySlot(0))

        listOf(Spell.HASTE, Spell.DETECT_MAGIC, Spell.AID, Spell.INVISIBILITY).forEach { spell ->
            val cast = hidden.handCast(PartySlot(0), CarrySlot(0), spell)
            assertTrue(
                cast.running.unseen(PartySlot(0)),
                "${spell.calledIt} should not have given them away",
            )
        }
    }

    /**
     * The whole point of the 10' one. One champion drawing a sword steps out
     * of cover alone, and the other five are still in it — which is what lets
     * a party put one of their number up front and keep the rest hidden.
     */
    @Test
    fun `one champion swinging leaves the other five hidden`() {
        val all = world().allHiding()

        val swung = all.handSwung(PartySlot(2), CarrySlot(0), WhatTheBlowCameTo.Missed)

        assertFalse(swung.running.unseen(PartySlot(2)), "the one who swung stayed hidden")
        (0 until 6).filter { it != 2 }.forEach { slot ->
            assertTrue(
                swung.running.unseen(PartySlot(slot)),
                "champion $slot lost a hiding somebody else spent",
            )
        }
    }

    /** Nothing happens to somebody who was not hiding, so acting is free. */
    @Test
    fun `acting costs nothing to a champion with nothing to lose`() {
        val bare = world()

        assertEquals(
            bare.running,
            bare.handSwung(PartySlot(0), CarrySlot(0), WhatTheBlowCameTo.Missed).running,
        )
    }

    /**
     * What the loss can be told by. The spell has gone from the world that
     * comes back, so the two worlds together are the only place its name
     * still is.
     */
    @Test
    fun `the hiding that was lost can still be named afterwards`() {
        val hidden = world().hiding(PartySlot(0))
        val swung = hidden.handSwung(PartySlot(0), CarrySlot(0), WhatTheBlowCameTo.Missed)

        val gone = swung.hidingsGoneSince(hidden)

        assertEquals(listOf(Spell.INVISIBILITY), gone.map { it.spell })
        assertEquals(listOf(PartySlot(0)), gone.map { it.on }, "it should name who lost it")
        assertEquals(emptyList(), hidden.hidingsGoneSince(hidden), "nothing was lost")
    }

    // --- the improved one ----------------------------------------------------

    /**
     * The one spell of the three that is given a clock, and the numbers are
     * data: five half-minutes flat and another for every level of its caster,
     * so three minutes at first level and seven and a half off a scroll.
     */
    @Test
    fun `the improved one is given a clock where the other two are not`() {
        val lasts = assertIs<SpellLasts>(Spell.IMPROVED_INVISIBILITY.lasts)

        assertEquals(SpellLasts(base = 5, perLevel = 1), lasts)
        assertEquals(Ticks(3276), lasts.castBySomeoneOfLevel(1))
        assertEquals(Ticks(7644), lasts.castBySomeoneOfLevel(OFF_A_SCROLL))
    }

    /**
     * And the trade that pays for the clock: it is not spent by acting. A
     * champion swings out of one and is still hidden for the next round,
     * which is the whole reason to prefer it to the plain one.
     */
    @Test
    fun `swinging does not spend an improved invisibility`() {
        val hidden = assertNotNull(
            world().spellBegunOn(
                Spell.IMPROVED_INVISIBILITY, PartySlot(0), CASTER, OFF_A_SCROLL,
            )
        )

        val swung = hidden.handSwung(PartySlot(0), CarrySlot(0), WhatTheBlowCameTo.Missed)

        assertTrue(swung.running.unseen(PartySlot(0)), "a swing spent the improved one")
        assertEquals(emptyList(), swung.hidingsGoneSince(hidden), "it reported a loss")
    }

    /** Nor does an aimed spell, which spends the plain one. */
    @Test
    fun `casting something aimed does not spend it either`() {
        val hidden = assertNotNull(
            world().spellBegunOn(
                Spell.IMPROVED_INVISIBILITY, PartySlot(0), CASTER, OFF_A_SCROLL,
            )
        )

        val cast = hidden.handCast(PartySlot(0), CarrySlot(0), Spell.CONE_OF_COLD)

        assertTrue(cast.running.unseen(PartySlot(0)))
    }

    /** What does end it is its clock, which the plain one has nothing of. */
    @Test
    fun `its clock is what ends it`() {
        val hidden = assertNotNull(
            world().spellBegunOn(
                Spell.IMPROVED_INVISIBILITY, PartySlot(0), CASTER, OFF_A_SCROLL,
            )
        )

        val (left, ended) = hidden.spellsRunDown(Ticks(7644))

        assertEquals(listOf(Spell.IMPROVED_INVISIBILITY), ended.map { it.spell })
        assertFalse(left.running.unseen(PartySlot(0)))
    }

    /**
     * A monster cannot tell the two apart. Both are read off the same pair of
     * bits wherever being hidden is worth anything, so the improved one buys
     * exactly the same chance of being passed over — it buys it for longer,
     * not harder.
     */
    @Test
    fun `it hides exactly as well as the plain one and no better`() {
        val improved = assertNotNull(
            world().spellBegunOn(
                Spell.IMPROVED_INVISIBILITY, PartySlot(0), CASTER, OFF_A_SCROLL,
            )
        )

        assertTrue(improved.whoAMonsterCanSee(PartySlot(0), rolling(4)))
        assertFalse(improved.whoAMonsterCanSee(PartySlot(0), rolling(5)))
        assertEquals(0, improved.running.hindranceStriking(PartySlot(0)))
    }

    /**
     * The two stack, being different conditions rather than two strengths of
     * one. The game refuses a spell already in force by comparing the bit it
     * sets against the ones the champion carries, and these two set different
     * bits — so a champion may hold both, and holding one is no reason to
     * refuse the other.
     */
    @Test
    fun `the plain one and the improved one sit on the same champion at once`() {
        val both = assertNotNull(
            world().hiding(PartySlot(0)).spellBegunOn(
                Spell.IMPROVED_INVISIBILITY, PartySlot(0), CASTER, OFF_A_SCROLL,
            ),
            "the improved one was refused to somebody already hidden",
        )

        assertEquals(2, both.running.over(PartySlot(0)).size)

        // And a swing spends the plain one and leaves the improved one, so
        // the champion is still hidden afterwards by the half that survives.
        val swung = both.handSwung(PartySlot(0), CarrySlot(0), WhatTheBlowCameTo.Missed)

        assertEquals(listOf(Spell.INVISIBILITY), swung.hidingsGoneSince(both).map { it.spell })
        assertTrue(swung.running.unseen(PartySlot(0)), "the improved one should have held")
    }

    /** But the same one twice over is refused, which is what keeps a scroll. */
    @Test
    fun `a second improved invisibility on the same champion is refused`() {
        val once = assertNotNull(
            world().spellBegunOn(
                Spell.IMPROVED_INVISIBILITY, PartySlot(0), CASTER, OFF_A_SCROLL,
            )
        )

        assertNull(
            once.spellBegunOn(Spell.IMPROVED_INVISIBILITY, PartySlot(0), CASTER, OFF_A_SCROLL),
        )
    }

    // --- is the whole party hidden -------------------------------------------

    /** All of them or none: one champion who can be seen is the party seen. */
    @Test
    fun `the party count as hidden only once every one of them is`() {
        assertFalse(world().wholePartyIsHidden, "a bare party counted as hidden")

        val allButOne = (0 until 5).fold(world()) { party, slot ->
            party.hiding(PartySlot(slot))
        }
        assertFalse(allButOne.wholePartyIsHidden, "five of six counted as the party")

        assertTrue(allButOne.hiding(PartySlot(5)).wholePartyIsHidden)
    }

    /** And the 10' one answers it in a single casting, which is its point. */
    @Test
    fun `the ten foot one hides the whole party at once`() {
        assertTrue(world().allHiding().wholePartyIsHidden)
    }

    /**
     * The departure, and the reason for it: a party under the better of the
     * two spells are hidden, and a question about whether the party are
     * hidden should say so.
     *
     * The game's own test reads the plain spell's bit alone and would answer
     * no here, which is taken as an oversight — everywhere else that asks
     * whether a champion can be seen counts both. Pinned so that nobody
     * quietly restores it.
     */
    @Test
    fun `a party hidden by the improved one count just the same`() {
        val improved = (0 until 6).fold(world()) { party, slot ->
            assertNotNull(
                party.spellBegunOn(
                    Spell.IMPROVED_INVISIBILITY, PartySlot(slot), CASTER, OFF_A_SCROLL,
                )
            )
        }

        assertTrue(
            improved.wholePartyIsHidden,
            "the better of the two hidings failed a question about being hidden",
        )
    }

    /** A mixture of the two counts as well, there being nobody left in sight. */
    @Test
    fun `one of each hides the party between them`() {
        val mixed = (0 until 6).fold(world()) { party, slot ->
            if (slot % 2 == 0) party.hiding(PartySlot(slot))
            else assertNotNull(
                party.spellBegunOn(
                    Spell.IMPROVED_INVISIBILITY, PartySlot(slot), CASTER, OFF_A_SCROLL,
                )
            )
        }

        assertTrue(mixed.wholePartyIsHidden)
    }

    /** And one of them swinging puts the party back in sight. */
    @Test
    fun `one champion acting ends it for the question`() {
        val all = world().allHiding()

        val swung = all.handSwung(PartySlot(3), CarrySlot(0), WhatTheBlowCameTo.Missed)

        assertFalse(swung.wholePartyIsHidden, "one champion gave the whole party away")
    }

    // --- which spells carry the caster's voice -------------------------------

    /**
     * The nineteen the table marks, named so that a dropped one has something
     * to fail against: a missing name would only show as a champion who
     * stayed hidden through a fireball, which looks like the spell working.
     */
    @Test
    fun `the spells that give a caster away are the ones the table marks`() {
        assertEquals(
            setOf(
                Spell.BURNING_HANDS,
                Spell.MAGIC_MISSILE,
                Spell.MELFS_ACID_ARROW,
                Spell.FIREBALL,
                Spell.HOLD_PERSON,
                Spell.LIGHTNING_BOLT,
                Spell.FEAR,
                Spell.ICE_STORM,
                Spell.CONE_OF_COLD,
                Spell.HOLD_MONSTER,
                Spell.DISINTEGRATE,
                Spell.FLESH_TO_STONE,
                Spell.CAUSE_LIGHT_WOUNDS,
                Spell.A_CLERICS_HOLD_PERSON,
                Spell.CAUSE_SERIOUS_WOUNDS,
                Spell.CAUSE_CRITICAL_WOUNDS,
                Spell.FLAME_STRIKE,
                Spell.SLAY_LIVING,
                Spell.HARM,
            ),
            Spell.entries.filter { it.givesTheCasterAway }.toSet(),
        )
    }

    /**
     * The three that look as though they belong on that list and do not.
     * Each of them charges a hand rather than reaching anything, and the blow
     * that hand then strikes gives the caster away by itself — so marking
     * them as well would spend the hiding twice.
     */
    @Test
    fun `the ones that charge a hand are left off it`() {
        listOf(Spell.SHOCKING_GRASP, Spell.VAMPIRIC_TOUCH, Spell.FLAME_BLADE).forEach { spell ->
            assertFalse(
                spell.givesTheCasterAway,
                "${spell.calledIt} should leave the hiding for its own blow to spend",
            )
        }
    }

    private companion object {
        const val IN_THE_PARTY = 1

        /** Whoever read the scroll, in a party where everybody is a stand-in. */
        val CASTER = PartySlot(0)

        /** A scroll is always read at ninth-level strength. */
        const val OFF_A_SCROLL = 9

        /** Longer than every counted spell in the game, and this one shrugs it off. */
        const val AN_HOUR = 32760

        /** The face a hidden champion is found on, from [GameState.SEEN_ANYWAY]. */
        const val SEEN_ANYWAY = GameState.SEEN_ANYWAY
    }
}
