package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Ability
import pl.pelotasplus.eyeofbeholder.data.model.Abilities
import pl.pelotasplus.eyeofbeholder.data.model.ArmorClass
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ChampionFlags
import pl.pelotasplus.eyeofbeholder.data.model.CharacterClass
import pl.pelotasplus.eyeofbeholder.data.model.ClassLevel
import pl.pelotasplus.eyeofbeholder.data.model.Food
import pl.pelotasplus.eyeofbeholder.data.model.HitPoints
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemKind
import pl.pelotasplus.eyeofbeholder.data.model.ItemMessages
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypes
import pl.pelotasplus.eyeofbeholder.data.model.OnAPlate
import pl.pelotasplus.eyeofbeholder.data.model.PortraitId
import pl.pelotasplus.eyeofbeholder.data.model.XpPoints
import pl.pelotasplus.eyeofbeholder.data.repository.ItemTypesRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What the plate does with what is offered it.
 *
 * Four answers, and the order they are asked in is the part worth pinning: a
 * champion who cannot be fed is told so before anything is said about what
 * they were handed, so a stone dwarf offered a potion hears about being stone
 * rather than about the potion.
 */
@Category(NeedsGameData::class)
class OfferedOnAPlateTest {

    private val resources = ResourceRepositoryImpl()

    private val types: ItemTypes by lazy {
        runBlocking { ItemTypesRepositoryImpl(resources).loadItemTypes().getOrThrow() }
    }

    private val dungeonItems: List<Item> by lazy {
        runBlocking { ItemsRepositoryImpl(resources).loadItems().getOrThrow().items }
    }

    private val rations: Item by lazy { dungeonItems.first { types.isEaten(it) } }

    private val potion: Item by lazy {
        dungeonItems.first { types.kindOf(it) == ItemKind.POTION }
    }

    private fun champion(hitPoints: Int = 40, flags: Int = IN_THE_PARTY) = Champion(
        name = NAME,
        portrait = PortraitId(0),
        abilities = Abilities(strength = Ability(10, 10), dexterity = Ability(10, 10)),
        hitPoints = HitPoints(hitPoints, 40),
        armorClass = ArmorClass(10),
        food = Food(50),
        characterClass = CharacterClass.FIGHTER,
        levels = listOf(ClassLevel(1, XpPoints(0))),
        carrying = List(27) { ItemIndex(ItemIndex.NOTHING) },
        flags = ChampionFlags(flags),
    )

    @Test
    fun `good rations are eaten`() {
        assertEquals(
            OnAPlate.Eaten(rations),
            types.offeredOnAPlate(champion(), rations),
        )
    }

    @Test
    fun `an empty hand is not an offer`() {
        assertEquals(
            OnAPlate.NothingOffered,
            types.offeredOnAPlate(champion(), null),
        )
    }

    @Test
    fun `anything that is not food is refused`() {
        assertEquals(
            OnAPlate.Refused(ItemMessages.ONLY_FOOD),
            types.offeredOnAPlate(champion(), potion),
        )
    }

    /** Spoiling costs a ration its worth rather than marking it. */
    @Test
    fun `rations that have gone off are refused`() {
        assertEquals(
            OnAPlate.Refused(ItemMessages.ROTTEN),
            types.offeredOnAPlate(champion(), rations.copy(value = Item.ROTTEN)),
        )
    }

    @Test
    fun `the dead are not fed`() {
        assertEquals(
            OnAPlate.Refused(ItemMessages.cannotEat(NAME)),
            types.offeredOnAPlate(champion(hitPoints = 0), rations),
        )
    }

    @Test
    fun `nor is stone`() {
        assertEquals(
            OnAPlate.Refused(ItemMessages.cannotEat(NAME)),
            types.offeredOnAPlate(champion(flags = IN_THE_PARTY or PETRIFIED), rations),
        )
    }

    /**
     * Being held stops a champion using their own hands, and the hands doing
     * the feeding are somebody else's.
     */
    @Test
    fun `a champion held fast is still fed`() {
        assertEquals(
            OnAPlate.Eaten(rations),
            types.offeredOnAPlate(champion(flags = IN_THE_PARTY or HELD), rations),
        )
    }

    @Test
    fun `who cannot be fed is asked before what they were handed`() {
        assertEquals(
            OnAPlate.Refused(ItemMessages.cannotEat(NAME)),
            types.offeredOnAPlate(champion(flags = IN_THE_PARTY or PETRIFIED), potion),
        )
    }

    private companion object {
        const val NAME = "Beohram"

        const val IN_THE_PARTY = 0x01
        const val HELD = 0x04
        const val PETRIFIED = 0x08
    }
}
