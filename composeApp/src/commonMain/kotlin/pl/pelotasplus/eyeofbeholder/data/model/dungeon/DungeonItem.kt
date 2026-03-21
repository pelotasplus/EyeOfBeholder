package pl.pelotasplus.eyeofbeholder.data.model.dungeon

import pl.pelotasplus.eyeofbeholder.data.model.Item

/**
 * A runtime item instance in the dungeon.
 *
 * Created from parsed [Item] data at level initialization. The original DOS
 * linked-list pointers (next/prev) are dropped — items are stored in regular
 * lists per square position in [DungeonSquare].
 *
 * @property itemIndex Index into the global item table (for ItemType lookup, -1 if unknown)
 * @property icon Icon index for rendering (into ITEMS1.CPS shape map)
 * @property type Item type index (references ITEMTYPE.DAT category)
 * @property flags Item flags (bit 7 = identified, others = cursed/magical/etc.)
 * @property value Context-dependent: magical bonus, charges, key ID, etc.
 * @property nameUnidentified Display name when not yet identified
 * @property nameIdentified Display name after identification
 */
data class DungeonItem(
    val itemIndex: Int,
    val icon: Int,
    val type: Int,
    val flags: Int,
    val value: Int,
    val nameUnidentified: String,
    val nameIdentified: String,
) {
    companion object {
        /**
         * Create a [DungeonItem] from a parsed [Item].
         */
        fun fromItem(item: Item, index: Int = -1): DungeonItem {
            return DungeonItem(
                itemIndex = index,
                icon = item.icon,
                type = item.type,
                flags = item.flags,
                value = item.value,
                nameUnidentified = item.nameUnidentified,
                nameIdentified = item.nameIdentified,
            )
        }
    }
}
