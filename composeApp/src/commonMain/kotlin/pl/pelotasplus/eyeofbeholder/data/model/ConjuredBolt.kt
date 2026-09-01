package pl.pelotasplus.eyeofbeholder.data.model

/**
 * What a conjured thing is drawn as, and where that picture is cut from the
 * sheet everything in flight shares.
 *
 * A thing somebody threw is drawn as the thing it is. A thing conjured has no
 * such picture and takes one of these instead, and which one goes with which
 * spell is a table in the game rather than a guess — a bolt of lightning does
 * not look like a fireball, and drawing one for the other is a mistake no
 * test that counts pixels will ever notice.
 *
 * Four of them sit in a column of their own past the thrown weapons, thirty-
 * two apart. The fifth is not in that column at all: it is the one shape left
 * over once the thrown weapons have taken theirs in pairs, and it is the
 * commonest of the lot.
 *
 * The rectangles are transcribed from where the shapes actually sit rather
 * than measured off a picture, and they are cut tight, because what draws
 * them centres on the width.
 */
enum class ConjuredBolt(val x: Int, val y: Int, val width: Int, val height: Int) {
    /** Fireball, flame strike, and the fireballs only monsters throw. */
    LIKE_FIRE(x = 64, y = 0, width = 48, height = 32),

    LIKE_LIGHTNING(x = 64, y = 32, width = 48, height = 32),

    LIKE_ICE(x = 64, y = 64, width = 48, height = 32),

    LIKE_A_MISSILE(x = 64, y = 96, width = 48, height = 32),

    /**
     * A scatter of blue motes, and what everything with no shape of its own
     * is drawn as: hold person and hold monster, and every one of a
     * beholder's four rays — death, disintegration, wounds and stone all
     * cross the corridor looking exactly alike.
     *
     * It is therefore the most-seen spell in the game, and the one the two
     * floors of clerics and beholders are made of.
     */
    LIKE_MOTES(x = 38, y = 1, width = 21, height = 20),
}
