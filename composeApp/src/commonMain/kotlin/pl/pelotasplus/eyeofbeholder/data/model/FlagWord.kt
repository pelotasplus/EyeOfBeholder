package pl.pelotasplus.eyeofbeholder.data.model

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Which bit of a [FlagWord] a script means.
 *
 * The bits have no names anywhere in the game data, and no fixed meanings
 * either: bit 1 of level 5 records that the clerics have been met from the
 * south, and bit 1 of another level records something else entirely. Only the
 * scripts that read and write a bit say what it is for.
 */
@JvmInline
@Serializable
value class FlagBit(val index: Int)

/**
 * A word of flags — one per level, plus one that outlives them all.
 *
 * Scripts remember what has already happened here. The clerics on level 5 can
 * be walked up to from three sides, and each side sets a bit of its own so
 * that way in does not speak twice; one flag for the whole encounter would
 * silence the other two.
 */
@JvmInline
@Serializable
value class FlagWord(private val raw: Int = 0) {
    fun isSet(bit: FlagBit) = (raw and mask(bit)) != 0

    fun with(bit: FlagBit) = FlagWord(raw or mask(bit))

    fun without(bit: FlagBit) = FlagWord(raw and mask(bit).inv())

    private fun mask(bit: FlagBit) = 1 shl bit.index

    override fun toString() = "FlagWord(${(0 until Int.SIZE_BITS).filter {
        isSet(FlagBit(it))
    }})"
}
