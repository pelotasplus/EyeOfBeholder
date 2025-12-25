package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.ByteReader

data object End : ScriptToken {

    override fun read(reader: ByteReader): ScriptToken = this
}
