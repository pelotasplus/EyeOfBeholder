package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemIconId
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemNameId
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypeId
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.script.ItemOverrides
import pl.pelotasplus.eyeofbeholder.data.model.script.NewItem
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What a script makes, and how it differs from what it copied.
 *
 * A script points at something the world's table already holds and asks for
 * another like it, then says which of its three variable properties the copy
 * has instead. Nothing in the game distinguishes one key from another except
 * that last part, so a copy that ignores it is the wrong item.
 */
class ScriptMadeItemsTest {

    private val here = Location(5, 6)
    private val level = 4

    /** Slot zero is nothing at all — it is what an empty hand names. */
    private val scroll = ItemIndex(1)

    private val world = GameState(
        party = PartyState(here, Direction.NORTH),
        items = listOf(nothing(), template(), nothing()),
    )

    // --- what the script sends -----------------------------------------------

    /**
     * The three optional bytes follow the flag byte in the order their bits
     * are numbered, so reading one out of turn silently gives an item another
     * item's icon.
     */
    @Test
    fun `the optional bytes are read in the order the bits name them`() {
        val token = NewItem.read(
            ByteReader(
                ubyteArrayOf(
                    0x01u, 0x00u, // copy of item 1
                    0x10u, 0x00u, // onto square 16
                    0x02u, // in its north-east corner
                    0x07u, // all three overrides follow
                    14u, // value
                    0x40u, // flags
                    23u, // icon
                )
            )
        )

        assertEquals(ItemOverrides(value = 14, flags = 0x40, icon = ItemIconId(23)), token.overrides)
    }

    @Test
    fun `a script that sends nothing overrides nothing`() {
        val token = NewItem.read(
            ByteReader(ubyteArrayOf(0x01u, 0x00u, 0x10u, 0x00u, 0x02u, 0x00u))
        )

        assertEquals(ItemOverrides(), token.overrides)
    }

    /** Bit 1 alone means the one byte that follows is the flags, not the value. */
    @Test
    fun `one bit set names which byte follows`() {
        val token = NewItem.read(
            ByteReader(ubyteArrayOf(0x01u, 0x00u, 0x10u, 0x00u, 0x02u, 0x02u, 0x40u))
        )

        assertEquals(ItemOverrides(flags = 0x40), token.overrides)
    }

    // --- what the copy comes out as ------------------------------------------

    @Test
    fun `the copy carries what the script said instead of what it copied`() {
        val made = made(ItemOverrides(value = 14, flags = 0x40, icon = ItemIconId(23)))

        assertEquals(14, made.value)
        assertEquals(0x40, made.flags)
        assertEquals(ItemIconId(23), made.icon)
    }

    @Test
    fun `what the script leaves out is what it copied`() {
        val made = made(ItemOverrides(value = 14))

        assertEquals(14, made.value)
        assertEquals(template().flags, made.flags)
        assertEquals(template().icon, made.icon)
    }

    @Test
    fun `a copy is otherwise the thing it copied`() {
        val made = made(ItemOverrides(value = 14))

        assertEquals(template().nameUnidentified, made.nameUnidentified)
        assertEquals(template().nameIdentified, made.nameIdentified)
        assertEquals(template().type, made.type)
    }

    /** Overriding the copy must not reach back into what it was copied from. */
    @Test
    fun `the thing copied from is left alone`() {
        val after = world.itemCopied(
            copyOf = scroll,
            level = level,
            at = here,
            place = SquarePlace.NORTH_WEST,
            overrides = ItemOverrides(value = 14, flags = 0x40, icon = ItemIconId(23)),
        )

        assertEquals(template(), after.item(scroll))
    }

    /** The hand is a place like any other, and takes the copy as made. */
    @Test
    fun `one put into the hand is overridden too`() {
        val after = world.itemCopiedIntoTheHand(
            copyOf = scroll,
            level = level,
            place = SquarePlace.NORTH_WEST,
            overrides = ItemOverrides(value = 14),
        )

        assertEquals(14, after.item(after.inHand)?.value)
    }

    private fun made(overrides: ItemOverrides): Item {
        val after = world.itemCopied(
            copyOf = scroll,
            level = level,
            at = here,
            place = SquarePlace.NORTH_WEST,
            overrides = overrides,
        )
        return after.items.last { it.exists }
    }

    private fun template() = Item(
        nameUnidentified = ItemNameId(7),
        nameIdentified = ItemNameId(8),
        flags = 0x20,
        icon = ItemIconId(3),
        type = ItemTypeId(5),
        place = SquarePlace.MIDDLE,
        location = Location(1, 1),
        next = 0,
        prev = 0,
        level = 1,
        value = 0,
    )

    private fun nothing() = template().copy(location = Item.NOWHERE, level = 0)
}
