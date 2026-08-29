package pl.pelotasplus.eyeofbeholder.data

import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.Ability
import pl.pelotasplus.eyeofbeholder.data.model.CarrySlot
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.OriginalSave
import pl.pelotasplus.eyeofbeholder.data.model.armourClassOf
import pl.pelotasplus.eyeofbeholder.data.repository.ItemTypesRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.OriginalSaveRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Working out what a champion's armour is worth.
 *
 * The quick start party is the proof: an armour class was written for
 * each of them into the save, and that number was itself the answer to this
 * sum when the game was saved. Getting all four back from the dexterity and
 * the gear is a check against that arithmetic and not against ours.
 */
@Category(NeedsGameData::class)
class ArmourWornTest {

    private val resources = ResourceRepositoryImpl()

    private val save: OriginalSave = runBlocking {
        OriginalSaveRepositoryImpl(resources)
            .loadOriginalSave(OriginalSaveRepositoryImpl.QUICK_START)
            .getOrThrow()
    }

    private val types = runBlocking {
        ItemTypesRepositoryImpl(resources).loadItemTypes().getOrThrow()
    }

    @Test
    fun `it gets back what the save holds for every champion`() {
        val party = save.party.filter { it.inTheParty }
        assertEquals(4, party.size, "the quick start party is not four")

        party.forEach {
            assertEquals(
                it.armorClass.value,
                types.armourClassOf(it, save.items).value,
                "${it.name} does not add up",
            )
        }
    }

    /**
     * The point of working it out at all: what a champion has on has to make a
     * difference. Nobody is ever harder to hit for taking their gear off, and
     * the ones actually wearing something are easier.
     *
     * Not all of them. The party's mage is at the number her dexterity alone
     * gives her, robes and staff being worth nothing against a blow, so
     * stripping her changes not a thing.
     */
    @Test
    fun `taking everything off is never worth anything`() {
        val party = save.party.filter { it.inTheParty }

        val bare = party.map { champion ->
            champion to types.armourClassOf(
                champion.copy(
                    carrying = List(champion.carrying.size) { ItemIndex(ItemIndex.NOTHING) },
                ),
                save.items,
            ).value
        }

        bare.forEach { (champion, stripped) ->
            assertTrue(
                stripped >= champion.armorClass.value,
                "${champion.name} is harder to hit with nothing on",
            )
        }

        assertEquals(
            3,
            bare.count { (champion, stripped) -> stripped > champion.armorClass.value },
            "the wrong number of the party are wearing anything that turns a blow",
        )
    }

    /**
     * Ten is the middle of the dexterity table and worth nothing either way;
     * clumsy is worth points against and quick is worth points off. The table
     * is the game's own.
     */
    @Test
    fun `being nimble is what moves a bare champion off ten`() {
        fun bareAt(dexterity: Int) = types.armourClassOf(
            Champion.NOBODY.copy(
                abilities = Champion.NOBODY.abilities.copy(
                    dexterity = Ability(dexterity, dexterity),
                ),
                carrying = List(30) { ItemIndex(ItemIndex.NOTHING) },
            ),
            save.items,
        ).value

        assertEquals(15, bareAt(0))
        assertEquals(14, bareAt(3))
        assertEquals(10, bareAt(10))
        assertEquals(9, bareAt(15))
        assertEquals(6, bareAt(18))
        assertEquals(4, bareAt(24))
    }

    /** A hand is worth armour only when a shield is in it. */
    @Test
    fun `a weapon hand is worth nothing`() {
        val fighter = save.party.first { it.inTheParty }
        val withoutShield = fighter.copy(
            carrying = fighter.carrying.toMutableList().also {
                it[CarrySlot(1).index] = ItemIndex(ItemIndex.NOTHING)
            },
        )

        assertTrue(
            types.armourClassOf(withoutShield, save.items).value >
                types.armourClassOf(fighter, save.items).value,
            "putting the shield down changed nothing",
        )
    }
}
