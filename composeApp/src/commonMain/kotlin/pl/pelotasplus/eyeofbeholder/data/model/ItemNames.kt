package pl.pelotasplus.eyeofbeholder.data.model

/**
 * The names in ITEM.DAT, which every item in the game is called through — an
 * item carries the number of its name and not the name, and a save carries no
 * names at all.
 *
 * Not every name is in the file. A magical thing has none of its own: it is
 * called what kind of thing it is and then what it does, and what it does is
 * a number that points into one of the lists below.
 */
data class ItemNames(private val names: List<String>) {

    operator fun get(id: ItemNameId): String = names.getOrNull(id.value)?.trim().orEmpty()

    /**
     * What to call [item].
     *
     * Until the party know what a thing is they call it what it looks like.
     * Once they know, it is called what it is — except that a plain enchanted
     * weapon has no name of its own, only a number, so it is called what it
     * looks like with the number on the front: a Long Sword the party have
     * identified as enchanted is a "+1 Long Sword", and a cursed one is a
     * "-1 Cursed Long Sword".
     *
     * @param types what kind of thing it is, which decides what the number
     *   means. On a weapon or a piece of armour it is a bonus; on a potion, a
     *   ring or a wand it picks the effect out of a list; on a scroll it is
     *   the spell written on it.
     */
    fun of(item: Item, types: ItemTypes?): String {
        val looksLike = get(item.nameUnidentified)
        if (!item.identified) return looksLike

        val known = get(item.nameIdentified)
        if (known.isNotEmpty()) return known

        val value = item.value

        return when ((types?.get(item.type)?.extraProperties ?: 0) and KIND) {
            in NUMBER_IS_A_BONUS -> when {
                value == 0 -> looksLike
                value < 0 -> "$value Cursed $looksLike"
                else -> "+$value $looksLike"
            }

            MAGE_SCROLL -> named(MAGE_SCROLL_NAME, spellNames, value)
            CLERIC_SCROLL -> named(CLERIC_SCROLL_NAME, spellNames, value)
            POTION -> named(POTION_NAME, potionEffects, value)
            RING -> named(RING_NAME, ringEffects, value)

            // One wand goes by its effect alone. The game keeps a word of its
            // own for that one — "Stick" — which the English text never
            // reaches, so whether it is meant to read "Stick of Starfire" or
            // just "Starfire" is not settled; this is the latter, which is
            // what the reverse-engineered engine produces.
            WAND -> if (value == GOES_BY_ITS_EFFECT) {
                wandEffects.getOrNull(value).orEmpty()
            } else {
                named(WAND_NAME, wandEffects, value)
            }

            else -> looksLike
        }
    }

    /** A magical thing is called what it is and then what it does. */
    private fun named(kind: String, effects: List<String>, value: Int): String {
        val effect = effects.getOrNull(value)?.takeIf { it.isNotBlank() } ?: return kind
        return "$kind of $effect"
    }

    private companion object {
        const val KIND = 0x7F

        /** The kinds whose value is a plain bonus: weapons and armour. */
        val NUMBER_IS_A_BONUS = 0..3

        const val MAGE_SCROLL = 9
        const val CLERIC_SCROLL = 10
        const val POTION = 14
        const val RING = 16
        const val WAND = 18

        /** The one wand called by what it does rather than by what it is. */
        const val GOES_BY_ITS_EFFECT = 5

        const val MAGE_SCROLL_NAME = "Mage Scroll"
        const val CLERIC_SCROLL_NAME = "Cleric Scroll"
        const val POTION_NAME = "Potion"
        const val RING_NAME = "Ring"
        const val WAND_NAME = "Wand"
    }
}

/** What a potion's value says is in it. */
private val potionEffects = listOf(
    "Giant Strength", "Healing", "Extra Healing", "Poison",
    "Vitality", "Speed", "Invisibility", "Cure Poison",
)

private val ringEffects = listOf("Adornment", "Wizardry", "Sustenance", "Feather Fall")

private val wandEffects = listOf(
    "Stick", "Lightning", "Frost", "Curing",
    "Fireball", "Starfire", "Magic Missile", "Dispel Magic",
)

/**
 * What a scroll's value says is written on it. One list for both kinds of
 * scroll: the cleric spells follow the mage ones, and a scroll's value points
 * into the whole of it. The blanks at the end are the game's own.
 */
private val spellNames = listOf(
    "armor", "burning hands", "detect magic", "magic missile", "shield",
    "shocking grasp", "blur", "detect invisibility", "improved identify",
    "invisibility", "melf's acid arrow", "dispel magic", "fireball", "haste",
    "Hold Person", "invisibility 10' radius", "lightning bolt",
    "vampiric touch", "fear", "ice storm", "improved invisibility",
    "remove curse", "cone of cold", "hold monster", "wall of force",
    "disintegrate", "flesh to stone", "stone to flesh", "true seeing",
    "finger of death", "power word stun", "bigby's clenched fist",
    "bless", "cause light wounds", "cure light wounds", "detect magic",
    "protection from evil", "aid", "flame blade", "hold person",
    "slow poison", "create food", "dispel magic", "magical vestment",
    "prayer", "remove paralysis", "cause serious wounds",
    "cure serious wounds", "neutralize poison",
    "protection from evil 10' radius", "cause critical wounds",
    "cure critical wounds", "flame strike", "raise dead", "slay living",
    "true seeing", "harm", "heal",
    // spelled as the game spells it; correcting it would not match its data
    "ressurection",
    "lay on hands", "turn undead", "", "mystic defense", "", "", "", "", "",
)

/** What ITEM.DAT says: the items the dungeon starts with, and every name. */
data class ItemDefinitions(
    val items: List<Item>,
    val names: ItemNames,
)

/** The lines the game writes along the bottom when things are moved about. */
object ItemMessages {
    fun taken(name: String) = "$name taken."

    const val WILL_NOT_GO_THERE = "You can't put that item there."
}
