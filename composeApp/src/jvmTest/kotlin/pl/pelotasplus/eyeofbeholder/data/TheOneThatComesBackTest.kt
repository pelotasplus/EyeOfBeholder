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
import pl.pelotasplus.eyeofbeholder.data.model.Damage
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterProperty
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.PortraitId
import pl.pelotasplus.eyeofbeholder.data.model.SHOOTS_FOREVER
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
import pl.pelotasplus.eyeofbeholder.data.model.partyEarns
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The one creature in the dungeon a killing blow does not kill.
 *
 * The thing at the end of it has two forms and one slot. Cut down, the first
 * does not leave the field: it comes back on the spot as the next kind on its
 * floor's list, drawn off the next sheet along, whole again and worse. Only
 * the second dies.
 *
 * Which creature that is, is the file's own bit rather than a name written
 * here, and every kind of every floor was read to find out: it is set on both
 * kinds of the sixteenth floor's inner half and nowhere else in the game.
 */
@Category(NeedsGameData::class)
class TheOneThatComesBackTest {

    private val resources = ResourceRepositoryImpl()

    private fun load(name: String): Inf = runBlocking {
        InfRepositoryImpl(
            resourceRepository = resources,
            mazRepository = MazRepositoryImpl(resources),
            vmpRepository = VmpRepositoryImpl(resources),
            vcnRepository = VcnRepositoryImpl(resources),
            palRepository = PalRepositoryImpl(resources),
            cpsRepository = CpsRepositoryImpl(resources),
            decRepository = DecRepositoryImpl(resources),
        ).loadInf(name).getOrThrow()
    }

    private val lastFloor = load("LEVEL16.INF")

    /** The inner half, whose two kinds are the two forms of the same thing. */
    private val bothForms: List<MonsterProperty> = lastFloor.subLevels[1].monsters

    /** The outer half, whose mind flayers die of being killed like anything else. */
    private val theFlayers: List<MonsterProperty> = lastFloor.subLevels[0].monsters

    private val theSlot = MonsterSlot(1)

    private fun standing(kind: Int, canTake: Int) = GameState(
        party = PartyState(Location(28, 3), Direction.NORTH),
        champions = listOf(champion()),
        monsters = listOf(
            MonsterInstance(
                index = theSlot,
                unit = 0,
                location = Location(28, 2),
                place = SquarePlace.MIDDLE,
                direction = Direction.SOUTH,
                type = MonsterTypeId(kind),
                gfxIndex = kind,
                mode = 0,
                pause = 0,
                weapon = 0,
                pocketItem = 0,
                hitPoints = HitPoints(canTake, canTake),
                shotsLeft = 20,
                nextRemoteWeapon = 2,
            ),
        ),
    )

    private fun champion() = Champion(
        name = "Anselm",
        portrait = PortraitId(0),
        abilities = Abilities(strength = Ability(10, 10), dexterity = Ability(10, 10)),
        hitPoints = HitPoints(20, 20),
        armorClass = ArmorClass(10),
        food = Food(100),
        characterClass = CharacterClass.FIGHTER,
        levels = listOf(ClassLevel(9, XpPoints(250_000))),
        carrying = List(27) { ItemIndex(ItemIndex.NOTHING) },
        flags = ChampionFlags(1),
    )

    private fun GameState.cutDown(kinds: List<MonsterProperty>) =
        monsterHurt(theSlot, Damage(500), kinds = kinds)

    /**
     * The bit is on both forms and on nothing else the game holds. Read from
     * every kind of every floor rather than from the two that were expected.
     */
    @Test
    fun `only the last floor's own two are made this way`() {
        assertTrue(bothForms.all { it.changesRatherThanDying })

        val elsewhere = (1..16).flatMap { level ->
            load("LEVEL$level.INF").subLevels.flatMapIndexed { sub, it ->
                it.monsters.map { kind -> Triple(level, sub, kind) }
            }
        }.filter { (_, _, kind) -> kind.changesRatherThanDying }
            .map { (level, sub, kind) -> "LEVEL$level sub $sub kind ${kind.id}" }

        assertEquals(
            listOf("LEVEL16 sub 1 kind 0", "LEVEL16 sub 1 kind 1"),
            elsewhere,
            "something other than the last floor's pair comes back from the dead",
        )
    }

    /**
     * The blow that would kill the first form leaves its slot filled. It is
     * the next kind up, painted off the next sheet along, and what it comes
     * back with is a flat hundred and fifty rather than anything its kind's
     * dice would give.
     */
    @Test
    fun `the first form comes back instead of dying`() {
        val after = standing(kind = 0, canTake = 93).cutDown(bothForms)

        val back = assertNotNull(
            after.monsters.firstOrNull { it.index == theSlot },
            "the first form died, and the fight is over one form early",
        )

        assertEquals(MonsterTypeId(1), back.type, "it came back as the wrong kind")
        assertEquals(1, back.gfxIndex, "it is still being drawn off the first sheet")
        assertEquals(HitPoints(150, 150), back.hitPoints)
    }

    /**
     * And with nothing left to count of what it throws, starting again from
     * the first of its new kind's weapons.
     */
    @Test
    fun `it comes back throwing without counting`() {
        val back = standing(kind = 0, canTake = 93).cutDown(bothForms)
            .monsters.first { it.index == theSlot }

        assertEquals(SHOOTS_FOREVER, back.shotsLeft)
        assertEquals(0, back.nextRemoteWeapon)
    }

    /**
     * A blow that leaves it standing is an ordinary blow: nothing changes and
     * it is simply that much worse off.
     */
    @Test
    fun `a blow it survives changes nothing about it`() {
        val after = standing(kind = 0, canTake = 93).monsterHurt(
            theSlot,
            Damage(50),
            kinds = bothForms,
        )

        val hurt = after.monsters.first { it.index == theSlot }
        assertEquals(MonsterTypeId(0), hurt.type)
        assertEquals(43, hurt.hitPoints.current)
        assertFalse(hurt.changing)
    }

    /** The second form has nothing after it to come back as, and dies. */
    @Test
    fun `the second form dies`() {
        val after = standing(kind = 1, canTake = 150).cutDown(bothForms)

        assertTrue(
            after.monsters.none { it.index == theSlot },
            "the second form came back as a third one, which the floor does not hold",
        )
    }

    /**
     * And its death is the end of the game rather than the end of a fight,
     * which the world says so that whoever owns the screen can play the ending.
     *
     * Only that one death. The first form falling is not it, and neither is
     * anything else in the dungeon.
     */
    @Test
    fun `killing the second form wins the game`() {
        assertTrue(standing(kind = 1, canTake = 150).cutDown(bothForms).theEndingIsOwed)

        assertFalse(
            standing(kind = 0, canTake = 93).cutDown(bothForms).theEndingIsOwed,
            "the ending was owed for the first form, which comes back",
        )
        assertFalse(
            standing(kind = 0, canTake = 68).cutDown(theFlayers).theEndingIsOwed,
            "the ending was owed for a mind flayer",
        )
    }

    /**
     * And nothing else does it. A mind flayer stands on the same floor and is
     * the same species number; it dies of being killed.
     */
    @Test
    fun `a mind flayer just dies`() {
        val after = standing(kind = 0, canTake = 68).cutDown(theFlayers)

        assertTrue(after.monsters.none { it.index == theSlot })
    }

    /**
     * The form that fell is paid for. It is a kill as far as the party's
     * experience is concerned, whatever is standing there afterwards — and
     * the eleven thousand is the first form's, not the second's twelve.
     */
    @Test
    fun `the form that fell is paid for`() {
        val before = standing(kind = 0, canTake = 93)
        val worth = bothForms.first { it.id == 0 }.experience

        assertEquals(11_000, worth, "the first form is not worth what the file says")

        val paid = before.cutDown(bothForms).partyEarns(XpPoints(worth.toLong()), Dice.random)

        assertEquals(
            XpPoints(261_000),
            paid.champions.first().levels.first().experience,
            "killing the first form paid nothing",
        )
    }

    /**
     * The change is a moment the screen is owed a scene for, and it is marked
     * on the creature until that has been played. Nothing else in the world
     * carries the mark, and taking it as shown takes it off.
     */
    @Test
    fun `the change is owed a scene, once`() {
        val after = standing(kind = 0, canTake = 93).cutDown(bothForms)

        assertEquals(theSlot, after.anythingChanging?.index)
        assertNull(
            after.theChangeShown().anythingChanging,
            "the scene would be played again on the next blow struck",
        )
    }
}
