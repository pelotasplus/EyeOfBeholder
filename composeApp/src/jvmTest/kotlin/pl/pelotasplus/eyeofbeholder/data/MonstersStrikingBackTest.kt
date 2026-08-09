package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Abilities
import pl.pelotasplus.eyeofbeholder.data.model.Ability
import pl.pelotasplus.eyeofbeholder.data.model.ArmorClass
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.DamageShown
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterPose
import pl.pelotasplus.eyeofbeholder.data.model.MonstersTurn
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.PortraitId
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
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The original's monster turn, which the party's step is measured against. */
private val A_MONSTER_TURN = pl.pelotasplus.eyeofbeholder.data.model.Ticks(20)

/**
 * Level 5's clerics hitting back once they have been roused.
 *
 * They stand on 13x8 facing south, which is straight at a party coming up to
 * 13x9, and their kind strikes once a round for 2d8 at a to-hit number of 13 —
 * so a champion in plate needs to be lucky.
 */
class MonstersStrikingBackTest {

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
        ).loadInf("LEVEL5.INF").getOrThrow()
    }

    private val kinds get() = level.subLevels[0].monsters

    private fun champion(armour: Int = 10, hitPoints: Int = 40) = Champion(
        name = "Anselm",
        portrait = PortraitId(0),
        abilities = Abilities(strength = Ability(10, 10), dexterity = Ability(10, 10)),
        hitPoints = HitPoints(hitPoints, hitPoints),
        armorClass = ArmorClass(armour),
        food = Food(100),
        characterClass = CharacterClass.FIGHTER,
        levels = listOf(ClassLevel(1, 0)),
        carrying = List(27) { ItemIndex(ItemIndex.NOTHING) },
        flags = ChampionFlags(1),
    )

    /** The party met at 13x9, with the pair roused as a swing rouses them. */
    private fun world(party: List<Champion> = List(6) { champion() }) = GameState(
        party = PartyState(Location(13, 9), Direction.NORTH),
        champions = party,
    ).arrivingAt(
        level = 5,
        places = level.monsterInstances,
        maz = level.subLevels[0].maz,
        kinds = kinds,
        dice = everyDieHighest,
    ).rousedBy(16)

    private val everyDieHighest = Dice { times, pips, modifier -> times * pips + modifier }
    private val everyDieLowest = Dice { times, _, modifier -> times + modifier }

    private fun turn(dice: Dice) = MonstersTurn(kinds, dice)

    /**
     * A whole swing, as the clock plays one: the arms go back, come down, and
     * only then does anybody get hurt.
     */
    private fun swungThrough(from: GameState, dice: Dice = everyDieHighest): MonstersTurn.Taken {
        val turn = turn(dice)

        var world = turn.begun(from)
        world = world.swingsCarriedOn()

        val landing = world.monsters.filter { it.striking == MonsterPose.ATTACK_B }.map { it.index }
        return turn.landed(world.swingsCarriedOn(), landing)
    }

    @Test
    fun `nothing that has not been roused takes a turn`() {
        val talking = world().copy(
            monsters = world().monsters.map { it.copy(provoked = false) },
        )

        assertEquals(emptyList(), swungThrough(talking).struck)
    }

    /** Both of them reach, so both strike. */
    @Test
    fun `each roused monster in reach strikes once`() {
        val taken = swungThrough(world())

        assertEquals(listOf(16, 17), taken.struck.map { it.monster }.sorted())
    }

    /**
     * Their kind is 2d8, so the hardest they can hit is 16 and the softest is
     * 2 — and a champion who takes one from each is down that much.
     */
    @Test
    fun `a landed blow takes its damage off the champion it lands on`() {
        val before = world()
        val taken = swungThrough(before)

        taken.struck.forEach { assertEquals(16, it.damage, "2d8 at its worst is 16") }

        val hurt = taken.struck.groupBy { it.at }.mapValues { (_, blows) -> blows.sumOf { it.damage } }
        hurt.forEach { (whose, damage) ->
            assertEquals(
                before.championIn(whose)!!.hitPoints.current - damage,
                taken.world.championIn(whose)!!.hitPoints.current,
            )
        }
    }

    /**
     * A twenty lands on anything, and it is the only roll that does against
     * armour this good: their to-hit number is 13, so armour of −8 puts the
     * target at 21 and out of reach of everything else.
     */
    @Test
    fun `a natural twenty lands however good the armour`() {
        val armoured = world(List(6) { champion(armour = -8) })

        assertTrue(swungThrough(armoured).struck.all { it.damage > 0 })
        assertEquals(emptyList(), swungThrough(armoured, everyDieLowest).struck)
    }

    /**
     * A blow that misses is a turn taken and nothing else. It is kept apart
     * from one that lands rather than reported as a hit for no damage: a
     * champion is not hurt by it, and whatever comes to flash or bleed on
     * being hit must not fire for a swing that touched air.
     */
    @Test
    fun `a miss takes the turn but is not a hit`() {
        val armoured = world(List(6) { champion(armour = -8) })
        val taken = swungThrough(armoured, everyDieLowest)

        assertEquals(emptyList(), taken.struck)
        assertEquals(listOf(16, 17), taken.missed.sorted())

        assertTrue(
            taken.world.champions.all { it.hitPoints.current == it.hitPoints.max },
            "a miss took hit points off somebody",
        )
    }

    /**
     * The arm goes back, comes down, and comes to rest — and nobody is hurt
     * until it has. A blow settled the moment it began would make the wind-up
     * decoration; it is meant to be the warning a player reads.
     */
    @Test
    fun `the arm goes back before the blow lands`() {
        val before = world()
        var world = turn(everyDieHighest).begun(before)

        assertTrue(world.monsters.all { it.striking == MonsterPose.ATTACK_A })
        assertEquals(
            before.champions.map { it.hitPoints },
            world.champions.map { it.hitPoints },
            "somebody was hurt before the arm had moved",
        )

        world = world.swingsCarriedOn()
        assertTrue(world.monsters.all { it.striking == MonsterPose.ATTACK_B })
        assertEquals(
            before.champions.map { it.hitPoints },
            world.champions.map { it.hitPoints },
            "somebody was hurt on the way down",
        )

        world = world.swingsCarriedOn()
        assertTrue(world.monsters.all { it.striking == null }, "the arm never came to rest")
    }

    /** An arm already swinging does not start over on the next turn. */
    @Test
    fun `a swing already going is not begun again`() {
        val turn = turn(everyDieHighest)
        val going = turn.begun(world()).swingsCarriedOn()

        assertTrue(turn.begun(going).monsters.all { it.striking == MonsterPose.ATTACK_B })
    }

    /**
     * A monster in reach swings every other turn, not every one. It is the
     * single biggest thing between a fight and a mauling: two of these do 2d8
     * apiece, so at every turn they take a champion down in about two seconds.
     */
    @Test
    fun `a monster swings every other turn`() {
        val turn = turn(everyDieHighest)

        // It swings the first time its turn comes round, and the arm is back
        // at rest well before the turn after that.
        val first = turn.begun(world())
        assertTrue(first.monsters.all { it.striking == MonsterPose.ATTACK_A })

        val resting = first.swingsCarriedOn().swingsCarriedOn()
        assertTrue(resting.monsters.none { it.striking != null })

        val second = turn.begun(resting)
        assertTrue(second.monsters.none { it.striking != null }, "swung on both turns")

        val third = turn.begun(second)
        assertTrue(third.monsters.all { it.striking == MonsterPose.ATTACK_A })
    }

    /** And it is heard doing it — the one sound a monster has. */
    @Test
    fun `a monster is heard striking`() {
        val heard = swungThrough(world()).struck.mapNotNull { it.heard }

        assertEquals(2, heard.size)
        assertEquals(setOf(37), heard.map { it.value }.toSet())
    }

    /**
     * A monster facing away cannot reach, however close it stands. Both of
     * these face south at a party to their south; turned about, neither is in
     * the fight.
     */
    @Test
    fun `a monster facing away strikes nothing`() {
        val turnedAround = world().let { world ->
            world.copy(monsters = world.monsters.map { it.copy(direction = Direction.NORTH) })
        }

        assertEquals(emptyList(), swungThrough(turnedAround).struck)
    }

    /**
     * A monster swings only at the square it already faces, so one caught
     * facing the wrong way spends a whole turn turning and swings on the next.
     * That single turn is the room a party stepping round one have.
     */
    @Test
    fun `turning takes a turn, and swinging comes after it`() {
        val turn = turn(everyDieHighest)
        val fromTheSide = world().let { world ->
            world.copy(monsters = world.monsters.map { it.copy(direction = Direction.NORTH) })
        }

        val turned = turn.begun(fromTheSide)
        assertTrue(turned.monsters.all { it.direction == Direction.SOUTH }, "never turned")
        assertTrue(turned.monsters.none { it.striking != null }, "turned and swung at once")

        val swinging = turn.begun(turned)
        assertTrue(swinging.monsters.all { it.striking == MonsterPose.ATTACK_A })
    }

    /**
     * Once an arm is coming down at the party they can do nothing until it
     * lands: the wind-up announces a hit rather than offering a chance to
     * duck, and what a party dance away from is a monster's turn coming
     * round, not the swing they can already see.
     */
    @Test
    fun `a swing in front of the party pins them until it lands`() {
        val turn = turn(everyDieHighest)

        assertFalse(world().pinnedByASwing, "pinned before anything swung")

        val swinging = turn.begun(world())
        assertTrue(swinging.pinnedByASwing, "the party can walk out of a swing")

        val landed = swinging.swingsCarriedOn().swingsCarriedOn()
        assertFalse(landed.pinnedByASwing, "the party are held after the blow")
    }

    /** Something swinging from the side is not drawn doing it, and pins nobody. */
    @Test
    fun `a swing from the side pins nobody`() {
        val fromTheSide = world().partyMovedTo(Location(13, 9)).let { world ->
            world.copy(party = world.party.copy(facing = Direction.EAST))
        }

        assertTrue(turn(everyDieHighest).begun(fromTheSide).anythingSwinging)
        assertFalse(turn(everyDieHighest).begun(fromTheSide).pinnedByASwing)
    }

    /**
     * A landed blow shows on the champion it landed on, and goes again after
     * a moment. It is the only thing that says they were hit: hit points move
     * too, but a bar creeping down is not something anybody sees mid-fight.
     */
    @Test
    fun `a landed blow shows on the portrait and then fades`() {
        val taken = swungThrough(world())
        val hit = taken.struck.first()

        assertEquals(hit.damage, taken.world.damageShownOn(hit.at))

        var fading = taken.world
        repeat(DamageShown.WHILE_IT_SHOWS.value / DamageShown.STEP.value) {
            assertEquals(hit.damage, fading.damageShownOn(hit.at), "it went early")
            fading = fading.damageFaded()
        }

        assertNull(fading.damageShownOn(hit.at), "it never went")
    }

    /** A second blow before the first has gone shows its own number, not the sum. */
    @Test
    fun `a fresh blow replaces the one still showing`() {
        val world = world().championHurt(PartySlot(0), 5).championHurt(PartySlot(0), 3)

        assertEquals(3, world.damageShownOn(PartySlot(0)))
        assertEquals(1, world.showingDamage.size)
    }

    /** A miss shows nothing at all. */
    @Test
    fun `a miss puts no splat up`() {
        val armoured = world(List(6) { champion(armour = -8) })
        val taken = swungThrough(armoured, everyDieLowest)

        assertTrue(taken.world.showingDamage.isEmpty())
    }

    /** Nor does one that is nowhere near. */
    @Test
    fun `a monster a square away strikes nothing`() {
        val backedOff = world().partyMovedTo(Location(13, 10))

        assertEquals(emptyList(), swungThrough(backedOff).struck)
    }

    // --- the clock the dance is danced on -----------------------------------

    /**
     * The party get five actions to a monster's one. That ratio is the whole
     * of the dance: step aside, turn, step back and swing all fit inside one
     * turn of something standing next to you, with one to spare.
     *
     * Both numbers are the original's, and neither is generous. If either ever
     * moves, the fight stops being the fight the game was designed around.
     */
    @Test
    fun `a party get five steps to a monster's turn`() {
        assertEquals(5, A_MONSTER_TURN.value / GameState.A_STEP.value)
    }

    /**
     * A step is counted in whole numbers of the clock's own ticks, so it ends
     * exactly when it should rather than a tick late.
     */
    @Test
    fun `a step is a whole number of clock ticks`() {
        assertEquals(0, GameState.A_STEP.value % GameState.CLOCK_STEP.value)
        assertEquals(0, A_MONSTER_TURN.value % GameState.CLOCK_STEP.value)
    }

    /** A champion already down is passed over for one still standing. */
    @Test
    fun `a blow falls on somebody still standing`() {
        val mostlyDown = world(
            List(6) { if (it == 5) champion() else champion(hitPoints = -10) },
        )

        val taken = swungThrough(mostlyDown)

        assertTrue(taken.struck.isNotEmpty())
        assertTrue(taken.struck.all { it.at == PartySlot(5) }, "a corpse was hit")
    }
}
