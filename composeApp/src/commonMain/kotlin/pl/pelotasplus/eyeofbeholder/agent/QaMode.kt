package pl.pelotasplus.eyeofbeholder.agent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Location

/**
 * QA mode: what an agent hunting for bugs may do that a player may not.
 * Fair play is the default and these are refused until QA mode is switched
 * on, in the game, by whoever is watching.
 */

/** A shortcut to where a check starts, instead of playing the way there. */
@Serializable
sealed interface QaSetup {

    /** The party put on [level] at [x], [y], facing [facing]. */
    @Serializable
    @SerialName("teleport")
    data class Teleport(val level: Int, val x: Int, val y: Int, val facing: Direction = Direction.NORTH) : QaSetup

    /** [spell] written into [champion]'s book or prayers on the page of its level, ready to cast or still to learn. */
    @Serializable
    @SerialName("give_spell")
    data class GiveSpell(val champion: Int, val spell: String, val ready: Boolean = true) : QaSetup

    /** A copy of the first item in the world whose true name holds [name], put on the pointer. */
    @Serializable
    @SerialName("give_item")
    data class GiveItem(val name: String) : QaSetup

    /** A scroll of [spell] put on the pointer, whether or not the dungeon holds one. */
    @Serializable
    @SerialName("give_scroll")
    data class GiveScroll(val spell: String) : QaSetup

    /**
     * A monster drawn as [looksLike] put [ahead] squares in front of the
     * party, facing them, copied from one of that look the level places.
     */
    @Serializable
    @SerialName("spawn_monster")
    data class SpawnMonster(val looksLike: String, val ahead: Int = 1) : QaSetup

    /** [champion] poisoned, as a bite would leave them. */
    @Serializable
    @SerialName("poison")
    data class Poison(val champion: Int) : QaSetup

    /** [champion] paralysed, on a grip as long as a blow's. */
    @Serializable
    @SerialName("paralyse")
    data class Paralyse(val champion: Int) : QaSetup

    /** Every champion back to full hit points, and nothing else changed. */
    @Serializable
    @SerialName("heal")
    data object Heal : QaSetup
}

/** The whole world behind the view, so a finding can say why and not only that. */
@Serializable
data class Inspection(
    val level: Int,
    val at: Location,
    val facing: Direction,
    val monsters: List<InspectedMonster>,
    val party: List<InspectedChampion>,
    /** The spells running, and on whom. */
    val running: List<String>,
)

@Serializable
data class InspectedMonster(
    val slot: Int,
    val looksLike: String,
    val kind: Int,
    val at: Location,
    val place: String,
    val facing: Direction,
    val hitPoints: String,
    val mode: Int,
    val provoked: Boolean,
    val stone: Boolean,
    val heldFor: Int,
)

@Serializable
data class InspectedChampion(
    val slot: Int,
    val name: String,
    val characterClass: String,
    val levels: List<Int>,
    val hitPoints: String,
    val food: Int,
    val armorClass: Int,
    /** Everything carried, by true name, whether the party know it or not. */
    val carrying: List<String>,
)
