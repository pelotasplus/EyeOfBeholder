package pl.pelotasplus.eyeofbeholder.data.model

data class Door(
    val index: Int,
    val type: Int,
    val knob: Int,
    val rectangles: List<Rectangle>,
    val cps: Cps,
    val buttons: List<Button>,
) {
    data class Rectangle(
        val x: Int,
        val y: Int,
        val w: Int,
        val h: Int,
    )

    data class Button(
        val x: Int,
        val y: Int,
        val w: Int,
        val h: Int,
        val posX: Int,
        val posY: Int,
    )
}

