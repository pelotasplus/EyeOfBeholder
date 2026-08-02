package pl.pelotasplus.eyeofbeholder.data.model

/**
 * How far away an item lying on the floor is drawn, by depth row and
 * view-relative quadrant: index = dim * 4 + quadrant, where dim runs 0 (three
 * rows ahead) to 3 (the party's own square). Not visible ([ScaleSteps.isVisible])
 * means the item is not drawn there at all — too far to make out, or behind
 * the camera on the party's own square. Niche items use quadrant 0.
 *
 * From the original game.
 */
val itemScaleSteps: List<ScaleSteps> = listOf(
    -1, -1, 3, 3,
    2, 2, 2, 2,
    1, 1, 1, 1,
    0, 0, -1, -1,
).map { ScaleSteps(it) }

/**
 * Screen X of an item sitting in a wall niche, per visible block, before
 * centering it on the icon's width.
 *
 * From the original game.
 */
val nicheItemX: List<ScreenX> = listOf(
    -56, -8, 40, 88, 136, 184, 232,
    -72, 8, 88, 168, 248,
    -40, 88, 216,
    -88, 88, 264,
).map { ScreenX(it) }

/** Niche-item baseline Y per depth row (dim 0-3); the icon's bottom edge. */
val nicheItemY: List<ScreenY> = listOf(37, 49, 56, 0).map { ScreenY(it) }
