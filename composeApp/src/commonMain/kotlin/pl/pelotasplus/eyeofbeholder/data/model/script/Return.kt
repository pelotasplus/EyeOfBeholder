package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader

/**
 * UpdateScreen script token.
 * Triggers a screen update/refresh.
 */
data object Return : ScriptToken {

    override fun read(reader: ByteReader): ScriptToken = this

    fun read(): Return = this
}
