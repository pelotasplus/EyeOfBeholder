package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader

/**
 * UpdateScreen script token.
 * Triggers a screen update/refresh.
 */
data class Wait(
    val delay: Int
) : ScriptToken {

    

    companion object {
        fun read(reader: ByteReader): Wait {
            val delay = reader.readU16LE()

            return Wait(
                delay = delay,
            )
        }
    }
}
