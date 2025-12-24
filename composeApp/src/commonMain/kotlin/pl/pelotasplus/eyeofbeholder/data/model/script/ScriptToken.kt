package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader

sealed interface ScriptToken {

    fun read(reader: ByteReader): ScriptToken

}
