package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader

/**
 * Inflicts damage on party characters. Opcode 0xF3.
 *
 * Used for traps, environmental hazards, and scripted damage events.
 * Damage is rolled as [times]d[itemOrPips]+[useStrModifierOrBase] following
 * AD&D dice notation.
 *
 * @property charIndex Target character (0-5), or -1 for the entire party
 * @property times Number of damage dice to roll
 * @property itemOrPips Dice size (e.g. 6 for d6), or item type for special damage
 * @property useStrModifierOrBase Base damage bonus (or flag to apply STR modifier)
 * @property flags Damage type flags (fire, cold, etc.) — EOB2 extended field
 * @property savingThrowType Type of saving throw allowed (0 = none) — EOB2 extended
 * @property savingThrowEffect Effect on successful save (half damage, no damage, etc.) — EOB2 extended
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
