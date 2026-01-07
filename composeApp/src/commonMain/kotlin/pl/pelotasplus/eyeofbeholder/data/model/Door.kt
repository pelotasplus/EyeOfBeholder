package pl.pelotasplus.eyeofbeholder.data.model

data class Door(
    val index: Int,
    val type: Int,
    val knob: Int,
    val rectangles: List<Rectangle>,
    val cps: Cps,
) {
    data class Rectangle(
        val x: Int,
        val y: Int,
        val w: Int,
        val h: Int,
    )
}

