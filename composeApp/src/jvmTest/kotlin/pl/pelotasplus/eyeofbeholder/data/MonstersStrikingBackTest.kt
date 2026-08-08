package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Abilities
import pl.pelotasplus.eyeofbeholder.data.model.Ability
import pl.pelotasplus.eyeofbeholder.data.model.ArmorClass
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
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
import kotlin.test.assertTrue

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

    @Test
    fun `nothing that has not been roused takes a turn`() {
        val talking = world().copy(
            monsters = world().monsters.map { it.copy(provoked = false) },
        )

        assertEquals(emptyList(), turn(everyDieHighest).taken(talking).struck)
    }

    /** Both of them reach, so both strike. */
    @Test
    fun `each roused monster in reach strikes once`() {
        val taken = turn(everyDieHighest).taken(world())

        assertEquals(listOf(16, 17), taken.struck.map { it.monster }.sorted())
    }

    /**
     * Their kind is 2d8, so the hardest they can hit is 16 and the softest is
     * 2 — and a champion who takes one from each is down that much.
     */
    @Test
    fun `a landed blow takes its damage off the champion it lands on`() {
        val before = world()
        val taken = turn(everyDieHighest).taken(before)

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

        assertTrue(turn(everyDieHighest).taken(armoured).struck.all { it.damage > 0 })
        assertTrue(turn(everyDieLowest).taken(armoured).struck.all { it.damage == 0 })
    }

    /** A blow that misses is still a blow, and still swung and heard. */
    @Test
    fun `a miss is still a turn taken`() {
        val taken = turn(everyDieLowest).taken(world(List(6) { champion(armour = -8) }))

        assertEquals(2, taken.struck.size)
        assertTrue(taken.struck.all { it.damage == 0 })
        assertTrue(taken.world.anythingSwinging, "nothing swung at all")
    }

    /** Whatever swung is drawn mid-swing, and the arm goes back before it comes down. */
    @Test
    fun `a monster that strikes leans back and then comes down`() {
        var world = turn(everyDieHighest).taken(world()).world

        assertTrue(world.monsters.all { it.striking == MonsterPose.ATTACK_A })

        world = world.swingsCarriedOn()
        assertTrue(world.monsters.all { it.striking == MonsterPose.ATTACK_B })

        world = world.swingsCarriedOn()
        assertTrue(world.monsters.all { it.striking == null }, "the arm never came to rest")
    }

    /** And it is heard doing it — the one sound a monster has. */
    @Test
    fun `a monster is heard striking`() {
        val heard = turn(everyDieHighest).taken(world()).struck.mapNotNull { it.heard }

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

        assertEquals(emptyList(), turn(everyDieHighest).taken(turnedAround).struck)
    }

    /** Nor does one that is nowhere near. */
    @Test
    fun `a monster a square away strikes nothing`() {
        val backedOff = world().partyMovedTo(Location(13, 10))

        assertEquals(emptyList(), turn(everyDieHighest).taken(backedOff).struck)
    }

    /** A champion already down is passed over for one still standing. */
    @Test
    fun `a blow falls on somebody still standing`() {
        val mostlyDown = world(
            List(6) { if (it == 5) champion() else champion(hitPoints = -10) },
        )

        val taken = turn(everyDieHighest).taken(mostlyDown)

        assertTrue(taken.struck.isNotEmpty())
        assertTrue(taken.struck.all { it.at == PartySlot(5) }, "a corpse was hit")
    }
}
