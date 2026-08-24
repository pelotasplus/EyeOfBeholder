package pl.pelotasplus.eyeofbeholder.rendering

import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.FloorReach
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.ViewPort
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DcrRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.MazRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VcnRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ViewConeRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VmpRepositoryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where things lying on the floor end up on screen, which is what a click on
 * one of them has to be answered from.
 *
 * Such a click is otherwise answered by which of four strips of floor it fell
 * in. A thing is drawn centred on its corner and not inside a strip, so the
 * two do not agree everywhere — and where they part, a click on the picture
 * of a thing reaches past it. The renderer records what it drew so that the
 * picture can answer for itself.
 */
class ItemsOnTheFloorTest {

    private val resources = ResourceRepositoryImpl()

    private val dungeonItems = runBlocking {
        ItemsRepositoryImpl(resources).loadItems().getOrThrow().items
    }

    /**
     * One thing on the corner [onlyOn] reaches, on the open run of forest
     * where the square ahead shows what lies on it.
     */
    private fun lyingOn(onlyOn: FloorReach?, inHand: Boolean = false): ViewPort = runBlocking {
        val cpsRepository = CpsRepositoryImpl(resources)
        val repository = ViewConeRepositoryImpl(
            infRepository = InfRepositoryImpl(
                resourceRepository = resources,
                mazRepository = MazRepositoryImpl(resources),
                vmpRepository = VmpRepositoryImpl(resources),
                vcnRepository = VcnRepositoryImpl(resources),
                palRepository = PalRepositoryImpl(resources),
                cpsRepository = cpsRepository,
                decRepository = DecRepositoryImpl(resources),
            ),
            cpsRepository = cpsRepository,
            dcrRepository = DcrRepositoryImpl(resources),
        )

        val inf = repository.loadLevel("LEVEL4.INF").getOrThrow()
        val sublevel = inf.subLevels[0]
        val here = Location(18, 14)
        val (dx, dy) = FACING.transformCoordinates(0, -1)
        val ahead = Location(here.x + dx, here.y + dy)

        val dagger = dungeonItems.first { it.icon.value > 0 }
        val putDown = listOfNotNull(
            onlyOn?.let { reach ->
                dagger.copy(
                    level = sublevel.level,
                    location = if (reach.aheadOfTheParty) ahead else here,
                    place = reach.placeFacing(FACING),
                )
            },
        )

        val carried = if (!inHand) emptyList() else listOf(dagger.copy(level = sublevel.level))

        repository.renderPosition(
            items = dungeonItems + putDown + carried,
            monsters = inf.monsterInstances,
            sublevel = sublevel,
            playerX = here.x,
            playerY = here.y,
            direction = FACING,
            holding = ItemIndex(dungeonItems.size).takeIf { inHand },
        ).getOrThrow()
    }

    /** The one thing this test put down, as it was drawn. */
    private fun pictureOf(reach: FloorReach) = lyingOn(reach).itemsOnTheFloor
        .single { it.slot.value >= dungeonItems.size }

    @Test
    fun `a thing on any corner within reach is drawn where a click can find it`() {
        FloorReach.entries.forEach { reach ->
            val picture = pictureOf(reach)

            assertTrue(
                picture.covers(
                    picture.left + picture.width / 2,
                    picture.top + picture.height / 2,
                ),
                "$reach: a click in the middle of the picture missed it",
            )
        }
    }

    /**
     * Putting a thing down is aimed the same way: the view says where the
     * thing in hand would come to rest on each corner, and that has to be
     * where it actually comes to rest — otherwise the click would be aimed at
     * a picture that never appears.
     */
    @Test
    fun `where a thing would land is where it lands`() {
        val landings = lyingOn(onlyOn = null, inHand = true).landingSpots.associateBy { it.reach }

        assertEquals(FloorReach.entries.toSet(), landings.keys, "not every corner was offered")

        FloorReach.entries.forEach { reach ->
            assertEquals(
                pictureOf(reach),
                landings.getValue(reach).where,
                "$reach: it would not land where it lands",
            )
        }
    }

    /**
     * The reason the picture has to answer at all: a thing on the party's own
     * square is drawn at full size and stands up out of the lower strip into
     * the one belonging to the square ahead, so the top of it is over floor
     * that reaches a different square, and the strips alone would send a click
     * there past it.
     */
    @Test
    fun `a thing on the party's own square is drawn above the strip that reaches it`() {
        listOf(FloorReach.OWN_LEFT, FloorReach.OWN_RIGHT).forEach { reach ->
            val picture = pictureOf(reach)

            assertTrue(
                picture.top < LOWER_STRIP_TOP,
                "$reach: the picture sits inside its strip, so the strips would do",
            )
            assertTrue(
                FloorReach.at(picture.left + picture.width / 2, picture.top) != reach,
                "$reach: the top of the picture already reaches the corner it lies on",
            )
        }
    }

    private companion object {
        val FACING = Direction.SOUTH

        /** Where the strip that reaches the party's own square starts. */
        const val LOWER_STRIP_TOP = 102
    }
}
