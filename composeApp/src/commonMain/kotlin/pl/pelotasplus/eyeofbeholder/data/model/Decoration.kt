package pl.pelotasplus.eyeofbeholder.data.model

data class Decoration(
    val wallIndex: Int, /* This is the index used by the .maz file. */
    val wallType: Int,  /* Index to what backdrop wall type that is being used. */
    val decorationID: Int, /* Index to and optional overlay decoration image in
                                  the DecorationData.decorations array in the
                                  [[eob.dat|.dat]] files. */
    val specialType: Int,
    val flags: Int,
    val dec: Dec,
    val cps: Cps
) {
    override fun toString(): String {
        return "Decoration(wallIndex=$wallIndex, wallType=$wallType, decorationID=$decorationID, specialType=$specialType, flags=$flags, dec=..., cps=...)"
    }
}
