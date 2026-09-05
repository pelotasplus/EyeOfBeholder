package pl.pelotasplus.eyeofbeholder.data

import pl.pelotasplus.eyeofbeholder.data.model.Volume
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How far anything happening on the floor is heard — a monster, or a sound a
 * script makes.
 *
 * Walls do not stop sound in this dungeon — a thing walking about in a part of
 * the floor the party have never reached is heard through the rock — and
 * distance is the only thing that quietens it. The numbers are transcribed
 * rather than chosen: a sixteenth of full volume for each square, and nothing
 * at all past fifteen.
 *
 * This is the curve on its own; [HowFarAScriptsSoundCarriesTest] is a script
 * being put through it.
 */
class HowFarSoundCarriesTest {

    @Test
    fun `something in the next square is nearly as loud as being there`() {
        assertEquals(Volume(14 * 16), Volume.asFarOffAs(1))
    }

    @Test
    fun `and each square further takes a sixteenth off it`() {
        (0..Volume.AS_FAR_AS_IT_CARRIES).forEach { squares ->
            assertEquals(
                Volume((Volume.AS_FAR_AS_IT_CARRIES - squares) * 16),
                Volume.asFarOffAs(squares),
            )
        }
    }

    /** Fifteen squares off is where it runs out, and nothing beyond is heard. */
    @Test
    fun `past fifteen squares nothing is heard`() {
        assertEquals(Volume.SILENT, Volume.asFarOffAs(Volume.AS_FAR_AS_IT_CARRIES))

        (16..40).forEach { squares ->
            assertEquals(Volume.SILENT, Volume.asFarOffAs(squares), "heard from $squares")
        }
    }

    /** Nothing asks to be louder than a sound goes. */
    @Test
    fun `nothing is louder than being on the square itself`() {
        assertTrue(Volume.asFarOffAs(0).gain <= Volume.FULL.gain)
    }
}
