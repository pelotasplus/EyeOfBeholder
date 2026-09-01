package pl.pelotasplus.eyeofbeholder.data.model

/**
 * The four bolts a spell is drawn as, which sit in a column of their own past
 * the thrown weapons on the same sheet.
 *
 * A thing somebody threw is drawn as the thing it is; a thing conjured has no
 * such picture and takes one of these instead. Which one goes with which
 * spell is a table in the game and not a guess — a bolt of lightning does not
 * look like a fireball, and drawing one for the other is the kind of mistake
 * no test that counts pixels will ever notice.
 *
 * @property row how far down the column it sits, each thirty-two below the
 *   last.
 */
enum class ConjuredBolt(val row: Int) {
    /** Fireball, flame strike, and the fireballs only monsters throw. */
    LIKE_FIRE(0),

    LIKE_LIGHTNING(1),

    LIKE_ICE(2),

    LIKE_A_MISSILE(3),
}
