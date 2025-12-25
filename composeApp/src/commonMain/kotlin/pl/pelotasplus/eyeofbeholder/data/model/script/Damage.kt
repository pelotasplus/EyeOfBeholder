package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader

/**
 * Damage script token.
 * Inflicts damage to character(s).
 * charIndex = -1 means all characters.
 */
data class Damage(
    val charIndex: Int,           // -1 for all characters
    val times: Int,               // dice rolls
    val itemOrPips: Int,          // item type or dice pips
    val useStrModifierOrBase: Int, // use str modifier or base damage
    // EOB2 only - not present in EOB1
    val flags: Int,
    val savingThrowType: Int,
    val savingThrowEffect: Int
) : ScriptToken {

    companion object {
        fun read(reader: ByteReader): Damage {
            return Damage(
                charIndex = reader.readI8(),
                times = reader.readI8(),
                itemOrPips = reader.readI8(),
                useStrModifierOrBase = reader.readI8(),
                // EOB2 only
                flags = reader.readU8(),
                savingThrowType = reader.readU8(),
                savingThrowEffect = reader.readU8()
            )
        }
    }
}
