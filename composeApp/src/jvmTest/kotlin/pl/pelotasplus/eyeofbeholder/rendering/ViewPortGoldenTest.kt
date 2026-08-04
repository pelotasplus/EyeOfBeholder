package pl.pelotasplus.eyeofbeholder.rendering

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import kotlinx.coroutines.runBlocking
import pl.pelotasplus.eyeofbeholder.data.model.CampMenu
import pl.pelotasplus.eyeofbeholder.data.model.CharacterSheet
import pl.pelotasplus.eyeofbeholder.data.model.OpenSheet
import pl.pelotasplus.eyeofbeholder.data.model.Preferences
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.repository.ItemTypesRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.model.Maz
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.WallByte
import pl.pelotasplus.eyeofbeholder.data.model.WallSide
import pl.pelotasplus.eyeofbeholder.data.model.getWall
import pl.pelotasplus.eyeofbeholder.data.model.wallsInSight
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.FloorReach
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MessageId
import pl.pelotasplus.eyeofbeholder.data.model.Naming
import pl.pelotasplus.eyeofbeholder.data.model.PaletteIndex
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.RecordingStage
import pl.pelotasplus.eyeofbeholder.data.model.ScriptEvent
import pl.pelotasplus.eyeofbeholder.data.model.TeleporterPulse
import pl.pelotasplus.eyeofbeholder.data.model.ViewPort
import pl.pelotasplus.eyeofbeholder.data.model.toImageBitmap
import pl.pelotasplus.eyeofbeholder.data.model.DialogueScene
import pl.pelotasplus.eyeofbeholder.data.model.DialogueTextId
import pl.pelotasplus.eyeofbeholder.data.model.PlayField
import pl.pelotasplus.eyeofbeholder.data.repository.CpsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DialogueTextRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.FontRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DcrRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.DecRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.InfRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ItemsRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.MazRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.PalRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ResourceRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.OriginalSaveRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VcnRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.ViewConeRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.repository.VmpRepositoryImpl
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.fail

/**
 * Golden-image tests for the 3D viewport renderer.
 *
 * Each test renders a known player position using the real game assets and
 * compares the resulting 176×120 frame against a reference PNG checked in
 * under src/jvmTest/goldens/. Any pixel difference fails the test and writes
 * the actual frame plus a diff mask to build/golden-failures/ for inspection.
 *
 * To accept an intentional rendering change (or bootstrap missing goldens):
 *   UPDATE_GOLDENS=1 ./gradlew :composeApp:jvmTest
 */
class ViewPortGoldenTest {

    /**
     * The items the dungeon is laid out with. A played game keeps its own
     * table of them, the party having moved things about; these scenes are of
     * levels as their files describe them, so the file is what they render.
     */
    private val dungeonItems: List<Item> by lazy {
        runBlocking {
            ItemsRepositoryImpl(ResourceRepositoryImpl()).loadItems().getOrThrow().items
        }
    }

    /** The mage's spellbook, which nobody but a mage has any use for. */
    private val SPELLBOOK = ItemIndex(462)

    @Test
    fun `level7 start position`() =
        checkGolden("level7-start", "LEVEL7.INF", x = 29, y = 15, direction = Direction.SOUTH)

    @Test
    fun `level7 silver tower entrance`() =
        checkGolden("level7-silver-tower", "LEVEL7.INF", x = 15, y = 6, direction = Direction.EAST)

    @Test
    fun `level6 temple`() =
        checkGolden("level6-temple", "LEVEL6.INF", x = 27, y = 29, direction = Direction.NORTH)

    @Test
    fun `level1 stairs down`() =
        checkGolden("level1-stairs", "LEVEL1.INF", x = 10, y = 12, direction = Direction.SOUTH)

    @Test
    fun `level1 door with button up close`() =
        checkGolden("level1-door", "LEVEL1.INF", x = 9, y = 13, direction = Direction.WEST)

    @Test
    fun `level1 four guards two rows ahead`() =
        checkGolden("level1-guards", "LEVEL1.INF", x = 10, y = 18, direction = Direction.SOUTH)

    /** The pair that speak on level 5, seen from the square they speak from. */
    @Test
    fun `level5 encounter one square ahead`() =
        checkGolden("level5-encounter", "LEVEL5.INF", x = 13, y = 9, direction = Direction.NORTH)

    /** The same pair on the diagonal square, walking away to the left. */
    @Test
    fun `level5 encounter on the left diagonal`() =
        checkGolden("level5-encounter-diagonal", "LEVEL5.INF", x = 12, y = 9, direction = Direction.EAST)

    /** The pair straight ahead but side-on, one cleric overlapping the other. */
    @Test
    fun `level5 encounter side on`() =
        checkGolden("level5-encounter-side", "LEVEL5.INF", x = 12, y = 8, direction = Direction.EAST)

    /** The same side pose mirrored, with the near wall cutting the pair in half. */
    @Test
    fun `level5 encounter side on mirrored`() =
        checkGolden("level5-encounter-mirrored", "LEVEL5.INF", x = 14, y = 9, direction = Direction.WEST)

    /** The pair three rows back, shrunk twice. */
    @Test
    fun `level5 encounter three rows ahead`() =
        checkGolden("level5-encounter-far", "LEVEL5.INF", x = 13, y = 11, direction = Direction.NORTH)

    /**
     * The priest level 6 conjures behind the party as they walk east, seen
     * after its script spins them round to face it.
     */
    @Test
    fun `level6 priest blocks the way back`() =
        checkGolden(
            "level6-priest",
            renderAfterStepping("LEVEL6.INF", number = 6, x = 10, y = 2, direction = Direction.EAST),
        )

    /**
     * Where the woman by level 4's temple door leaves the party after walking
     * them up the corridor: four squares north of where they were, which the
     * script draws one step at a time.
     */
    @Test
    fun `level4 escorted to the temple door`() =
        checkGolden(
            "level4-escort",
            renderAfterStepping(
                "LEVEL4.INF",
                number = 4,
                x = 12,
                y = 11,
                direction = Direction.NORTH,
                answers = listOf(1, 1),
                frame = LAST_FRAME,
            ),
        )

    @Test
    fun `level7 sword at the party's feet`() =
        checkGolden("level7-sword-at-feet", "LEVEL7.INF", x = 29, y = 16, direction = Direction.SOUTH)

    /**
     * An item on the front-left diagonal square draws partly on top of the wall
     * that should hide it. Frozen while broken so the fix shows up as a diff.
     */
    @Test
    fun `level4 item behind the wall to the left`() =
        checkGolden("level4-item-behind-wall", "LEVEL4.INF", x = 12, y = 5, direction = Direction.SOUTH)

    /** A dagger on a square whose tree trunk stands between it and the party. */
    @Test
    fun `level4 dagger behind the middle tree`() =
        checkGolden("level4-dagger-behind-tree", "LEVEL4.INF", x = 16, y = 12, direction = Direction.SOUTH)

    /** The same dagger one square further away. */
    @Test
    fun `level4 dagger behind the middle tree from further back`() =
        checkGolden("level4-dagger-behind-tree-far", "LEVEL4.INF", x = 16, y = 11, direction = Direction.SOUTH)

    /** The scene the sprite-versus-wall work is being fixed against. */
    @Test
    fun `level4 item against the wall facing west`() =
        checkGolden("level4-item-west", "LEVEL4.INF", x = 18, y = 14, direction = Direction.WEST)

    /**
     * A monster stands at 21x26, with the solid block at 22x26 between it and
     * the party and the button wall two squares ahead beside it. The two walls
     * close its band between them, so none of it is drawn.
     */
    @Test
    fun `level3 monster two ahead and one left`() =
        checkGolden("level3-monster-through-the-block", "LEVEL3.INF", x = 23, y = 25, direction = Direction.WEST)

    /**
     * The same monster from a step nearer, where the button wall now stands one
     * square ahead and closes its band on its own.
     */
    @Test
    fun `level3 monster one ahead and one left`() =
        checkGolden("level3-monster-on-the-diagonal", "LEVEL3.INF", x = 22, y = 25, direction = Direction.WEST)

    /**
     * Rooms belonging to level 3's second sublevel, which the party can walk
     * into without a script sending them. Drawn with the sublevel its own walls
     * name, so bytes 61 and 64 have the appearance only that sublevel gives
     * them instead of the red that says nobody does.
     */
    @Test
    fun `level3 rooms of the second sublevel`() =
        checkGolden("level3-second-sublevel", "LEVEL3.INF", x = 7, y = 14, direction = Direction.NORTH)

    /**
     * The far corner of this view is a face of the level's other half, which
     * this one has no appearance for and so leaves blank.
     */
    @Test
    fun `level3 a wall of the other half in the far corner`() =
        checkGolden("level3-boundary-wall", "LEVEL3.INF", x = 3, y = 10, direction = Direction.SOUTH)

    /** The same corridor a square west, where that corner falls off the maze. */
    @Test
    fun `level3 a step west of the boundary wall`() =
        checkGolden("level3-boundary-wall-stepped", "LEVEL3.INF", x = 2, y = 10, direction = Direction.SOUTH)

    /**
     * A door to either side, looking straight at the boundary: nearly
     * everything beyond them belongs to the sublevel the party are not in, and
     * the doors alongside are all that says so.
     */
    @Test
    fun `level3 doors either side facing the boundary`() =
        checkGolden("level3-doors-facing-the-boundary", "LEVEL3.INF", x = 3, y = 10, direction = Direction.EAST)

    /**
     * The door at 4x9 seen two squares off and one to the side, where its panel
     * sits wrongly in its frame: the wall byte says the door stands open, and
     * the panel is drawn as though it were shut.
     *
     * Frozen while broken so the fix shows up as a diff.
     */
    @Test
    fun `level3 2x10 east`() = checkGolden(
        "level3-2x10-east",
        // both doors a step down from the open the level holds them at, so the
        // two positions off to a side each have a panel to place
        renderFrame("LEVEL3.INF", x = 2, y = 10, direction = Direction.EAST) { at, side ->
            ALMOST_OPEN.takeIf { side == WallSide.WEST && at in DOORS_BESIDE_2X10 }
        },
    )

    /** The same door straight ahead from 3x9, for comparison. */
    @Test
    fun `level3 3x9 east`() =
        checkGolden("level3-3x9-east", "LEVEL3.INF", x = 3, y = 9, direction = Direction.EAST)

    /**
     * The doors either side of 2x10 held half open, which level 3 never does —
     * its own are open to the last step, and an open door has no panel to look
     * at. Half way there is one, in both of the positions off to a side at
     * once, which is where a panel is hardest to place.
     */
    @Test
    fun `level3 doors either side held half open`() = checkGolden(
        "level3-doors-half-open",
        renderFrame("LEVEL3.INF", x = 2, y = 10, direction = Direction.EAST) { at, side ->
            HALF_OPEN.takeIf { side == WallSide.WEST && at in DOORS_BESIDE_2X10 }
        },
    )

    /**
     * The end of a corridor with its walls to either side, which is what a
     * square back from it ought to look like too.
     */
    @Test
    fun `level3 4x11 east`() =
        checkGolden("level3-4x11-east", "LEVEL3.INF", x = 4, y = 11, direction = Direction.EAST)

    /**
     * A square further back, where the sides of the same corridor are missing.
     *
     * Frozen while broken so the fix shows up as a diff.
     */
    @Test
    fun `level3 3x11 east`() =
        checkGolden("level3-3x11-east", "LEVEL3.INF", x = 3, y = 11, direction = Direction.EAST)

    /**
     * The same corridor looked back along, where a wall has no appearance and
     * is painted red.
     *
     * Frozen while broken so the fix shows up as a diff.
     */
    @Test
    fun `level3 5x11 west`() =
        checkGolden("level3-5x11-west", "LEVEL3.INF", x = 5, y = 11, direction = Direction.WEST)

    /**
     * A door on the front-left square, which is where one used to be drawn a
     * size too small and pulled in towards the middle with it.
     */
    @Test
    fun `level3 14x11 north`() =
        checkGolden("level3-14x11-north", "LEVEL3.INF", x = 14, y = 11, direction = Direction.NORTH)

    /** A square nearer the same doors, where they sit where they belong. */
    @Test
    fun `level3 13x11 north`() =
        checkGolden("level3-13x11-north", "LEVEL3.INF", x = 13, y = 11, direction = Direction.NORTH)

    /** The rooms further west, which have no door in sight at all. */
    @Test
    fun `level3 11x12 north`() =
        checkGolden("level3-11x12-north", "LEVEL3.INF", x = 11, y = 12, direction = Direction.NORTH)

    /** The corridor a square back from them. */
    @Test
    fun `level3 14x12 north`() =
        checkGolden("level3-14x12-north", "LEVEL3.INF", x = 14, y = 12, direction = Direction.NORTH)

    /** The same corridor from its western end. */
    @Test
    fun `level3 12x12 north`() =
        checkGolden("level3-12x12-north", "LEVEL3.INF", x = 12, y = 12, direction = Direction.NORTH)

    /**
     * A spider's web drawn over the wall standing in front of it rather than
     * on the wall it hangs from.
     *
     * Frozen while broken so the fix shows up as a diff.
     */
    @Test
    fun `level1 28x4 south`() =
        checkGolden("level1-28x4-south", "LEVEL1.INF", x = 28, y = 4, direction = Direction.SOUTH)

    /**
     * The same web a square further back.
     *
     * Frozen while broken so the fix shows up as a diff.
     */
    @Test
    fun `level1 28x5 south`() =
        checkGolden("level1-28x5-south", "LEVEL1.INF", x = 28, y = 5, direction = Direction.SOUTH)

    /** The same web from a square that shows it where it belongs. */
    @Test
    fun `level1 26x6 south`() =
        checkGolden("level1-26x6-south", "LEVEL1.INF", x = 26, y = 6, direction = Direction.SOUTH)

    /*
     * A decoration is cut down to what the walls in front of it leave showing,
     * and dropped when they leave nothing. These stand one in a window of every
     * shape — shut, a sliver, half a view, at each depth a front wall is drawn
     * at, and on a side wall — and none of them moved when the cutting was put
     * in, which is what they are here to keep saying. A narrowed window is not
     * the same as a decoration that overruns it, and only the second is a
     * change anyone should see.
     */

    /** Straight ahead with the way to it shut, so none of it should show. */
    @Test
    fun `level3 5x3 north`() =
        checkGolden("level3-5x3-north", "LEVEL3.INF", x = 5, y = 3, direction = Direction.NORTH)

    /** The same decoration and the same position, left half in view. */
    @Test
    fun `level3 10x3 north`() =
        checkGolden("level3-10x3-north", "LEVEL3.INF", x = 10, y = 3, direction = Direction.NORTH)

    /** The furthest row a front wall is drawn at, down to six columns. */
    @Test
    fun `level3 29x13 north`() =
        checkGolden("level3-29x13-north", "LEVEL3.INF", x = 29, y = 13, direction = Direction.NORTH)

    /** The same far row on another level, seen along a corridor. */
    @Test
    fun `level4 19x10 west`() =
        checkGolden("level4-19x10-west", "LEVEL4.INF", x = 19, y = 10, direction = Direction.WEST)

    /** A decoration on a side wall beside the party, cut to three columns. */
    @Test
    fun `level4 16x12 north`() =
        checkGolden("level4-16x12-north", "LEVEL4.INF", x = 16, y = 12, direction = Direction.NORTH)

    /** The row between, on the level whose decorations are the biggest. */
    @Test
    fun `level7 10x4 north`() =
        checkGolden("level7-10x4-north", "LEVEL7.INF", x = 10, y = 4, direction = Direction.NORTH)

    /** Another shut window, where the same decoration should not be drawn. */
    @Test
    fun `level7 7x11 south`() =
        checkGolden("level7-7x11-south", "LEVEL7.INF", x = 7, y = 11, direction = Direction.SOUTH)

    /** A decoration close on the party's own row, clipped to its left edge. */
    @Test
    fun `level1 27x3 south`() =
        checkGolden("level1-27x3-south", "LEVEL1.INF", x = 27, y = 3, direction = Direction.SOUTH)

    /**
     * The party on the top row of the maze looking off the edge of it, where a
     * wall is missing.
     *
     * Frozen while broken so the fix shows up as a diff.
     */
    @Test
    fun `level1 25x0 north`() =
        checkGolden("level1-25x0-north", "LEVEL1.INF", x = 25, y = 0, direction = Direction.NORTH)

    /**
     * A keyhole wall with a scroll hanging in the air in front of it.
     *
     * Frozen while broken so the fix shows up as a diff.
     */
    @Test
    fun `level3 29x6 east`() =
        checkGolden("level3-29x6-east", "LEVEL3.INF", x = 29, y = 6, direction = Direction.EAST)

    /** The same square looked north from, where no scroll shows. */
    @Test
    fun `level3 29x6 north`() =
        checkGolden("level3-29x6-north", "LEVEL3.INF", x = 29, y = 6, direction = Direction.NORTH)

    /**
     * The locked shelf straight ahead, four scrolls on the square behind it.
     * A wall keeps what is on its square to itself unless it is marked as one
     * that shows it, and a shelf that locks is not: nothing should hang there
     * until a key turns.
     */
    @Test
    fun `level3 29x5 east`() =
        checkGolden("level3-29x5-east", "LEVEL3.INF", x = 29, y = 5, direction = Direction.EAST)

    /**
     * A door jammed in its frame, which rests a little higher than a shut one
     * and by less the further off it is. Level 2 has the nearest of the four
     * the game keeps.
     */
    @Test
    fun `level2 the jammed door at 8x3`() =
        checkGolden("level2-jammed-door", "LEVEL2.INF", x = 8, y = 4, direction = Direction.NORTH)


    /**
     * A door of the kind that has something fixed behind it — here the forest
     * of the level above, seen through the opening. Only the panel over it
     * slides, and we draw neither: the opening comes out black.
     *
     * Frozen while broken so the fix shows up as a diff.
     */
    @Test
    fun `level5 14x9 east`() =
        checkGolden("level5-14x9-east", "LEVEL5.INF", x = 14, y = 9, direction = Direction.EAST)

    /**
     * The door at 11x9 seen off to one side, where its panel spills past the
     * frame it hangs in.
     *
     * Frozen while broken so the fix shows up as a diff.
     */
    @Test
    fun `level5 13x8 west`() =
        checkGolden("level5-13x8-west", "LEVEL5.INF", x = 13, y = 8, direction = Direction.WEST)

    /** The same door off the other side, where it stays inside its frame. */
    @Test
    fun `level5 13x10 west`() =
        checkGolden("level5-13x10-west", "LEVEL5.INF", x = 13, y = 10, direction = Direction.WEST)

    /**
     * The same door one square off to the side, where the wall straight ahead
     * is what the party actually see. The button belongs to the doorway and
     * goes where the doorway went, which here is past the edge of the view.
     */
    @Test
    fun `level5 10x8 east`() =
        checkGolden("level5-10x8-east", "LEVEL5.INF", x = 10, y = 8, direction = Direction.EAST)

    /** A door with a button, straight ahead, where the button is written to go. */
    @Test
    fun `level5 10x9 east`() =
        checkGolden("level5-10x9-east", "LEVEL5.INF", x = 10, y = 9, direction = Direction.EAST)

    /** A monster two squares off, seen down the corridor it stands in. */
    @Test
    fun `level3 monster at 3x12 facing south`() =
        checkGolden("level3-monster-south", "LEVEL3.INF", x = 3, y = 12, direction = Direction.SOUTH)

    /**
     * The same monster from the same square after turning right, where the
     * faces of the level's other half come into view. It is the same creature:
     * a monster's graphic is an index into its sublevel's own list, so being in
     * the wrong one draws a different animal entirely.
     */
    @Test
    fun `level3 monster at 3x12 facing west`() = checkGolden(
        "level3-monster-west",
        // the door at 1x13 is held open by the level, and an open door is not
        // drawn — a step down from that puts a panel in the frame beside the
        // monster, where a sprite and a door have to agree about the same square
        renderFrame("LEVEL3.INF", x = 3, y = 12, direction = Direction.WEST) { at, side ->
            ALMOST_OPEN.takeIf { side == WallSide.EAST && at == Location(1, 13) }
        },
    )

    /**
     * Two doors seen while the party are still carrying level 3's second
     * sublevel, which defines no doors whatever. Its own rooms have none, so
     * being able to see one is what says the party have left them.
     */
    @Test
    fun `level3 doors seen while carrying the other sublevel`() =
        checkGolden(
            "level3-doors-from-the-other-sublevel",
            "LEVEL3.INF",
            x = 3,
            y = 8,
            direction = Direction.SOUTH,
            arrivedIn = 1,
        )

    @Test
    fun `toImageBitmap matches the raw pixel buffer`() {
        val viewPort = renderFrame("LEVEL7.INF", x = 29, y = 15, direction = Direction.SOUTH)
        val fromBuffer = viewPort.toImage()
        val fromBitmap = viewPort.toImageBitmap().toPixelMap()

        var differing = 0
        for (y in 0 until ViewPort.ROWS) {
            for (x in 0 until ViewPort.COLS) {
                val expected = fromBuffer.getRGB(x, y)
                val actual = fromBitmap[x, y].toArgb()
                // compare only visible pixels; both encode transparent as alpha 0
                val same = if ((expected ushr 24) == 0) (actual ushr 24) == 0 else expected == actual
                if (!same) differing++
            }
        }
        if (differing > 0) {
            fail("toImageBitmap differs from the pixel buffer at $differing pixels")
        }
    }

    /**
     * @param arrivedIn the sublevel the party are carrying, which is whichever
     *   one they were last put in rather than anything the square knows.
     */
    private fun checkGolden(
        name: String,
        level: String,
        x: Int,
        y: Int,
        direction: Direction,
        arrivedIn: Int = 0,
    ) = checkGolden(name, renderFrame(level, x, y, direction, arrivedIn))

    private fun checkGolden(name: String, viewPort: ViewPort) =
        checkGolden(name, viewPort.toImage())

    private fun checkGolden(name: String, actual: BufferedImage) {
        val goldenFile = goldensDir.resolve("$name.png")

        if (updateGoldens) {
            goldenFile.parentFile.mkdirs()
            ImageIO.write(actual, "png", goldenFile)
            println("Updated golden ${goldenFile.absolutePath}")
            return
        }

        if (!goldenFile.exists()) {
            val candidate = failuresDir.resolve("$name-candidate.png")
            candidate.parentFile.mkdirs()
            ImageIO.write(actual, "png", candidate)
            fail(
                "Missing golden ${goldenFile.absolutePath}\n" +
                        "Review ${candidate.absolutePath} and run UPDATE_GOLDENS=1 ./gradlew :composeApp:jvmTest to accept it."
            )
        }

        val golden = ImageIO.read(goldenFile)
        val differing = countDifferingPixels(golden, actual)
        if (differing > 0) {
            failuresDir.mkdirs()
            val actualFile = failuresDir.resolve("$name-actual.png")
            val diffFile = failuresDir.resolve("$name-diff.png")
            ImageIO.write(actual, "png", actualFile)
            ImageIO.write(diffImage(golden, actual), "png", diffFile)
            fail(
                "$name: $differing of ${actual.width * actual.height} pixels differ from golden.\n" +
                        "  actual: ${actualFile.absolutePath}\n" +
                        "  diff:   ${diffFile.absolutePath}\n" +
                        "  If the change is intentional: UPDATE_GOLDENS=1 ./gradlew :composeApp:jvmTest"
            )
        }
    }

    /**
     * The two places a script can put a picture: a speaker framed at the top
     * left with the speech beside and below, and a plate across the whole
     * width, which is drawn instead of the frame rather than inside it.
     */
    @Test
    fun `dialogue with a speaker in the frame`() =
        checkGolden(
            "dialogue-speaker",
            dialogueOver(
                level = "LEVEL6.INF", x = 10, y = 2,
                picture = "SOUT2.CPS", sourceLeft = 160, sourceTop = 0,
                goes = DialogueScene.PictureFrame.SPEAKER,
                textId = 28, buttons = listOf("leave", "attack"),
            ),
        )

    @Test
    fun `dialogue with a picture across the top`() =
        checkGolden(
            "dialogue-across-the-top",
            dialogueOver(
                level = "LEVEL4.INF", x = 15, y = 10,
                picture = "DARKMOON.CPS", sourceLeft = 0, sourceTop = 0,
                goes = DialogueScene.PictureFrame.ACROSS_THE_TOP,
                textId = 18, buttons = listOf("yes", "no"),
            ),
        )

    /**
     * A question asked with nobody drawn: the grave on level 4, which the
     * party stand in front of and click. The dungeon stays up and the box that
     * would have framed a speaker is not drawn at all.
     */
    @Test
    fun `dialogue asked by nobody`() =
        checkGolden(
            "dialogue-nobody",
            dialogueOver(
                level = "LEVEL4.INF", x = 17, y = 5,
                message = 8, buttons = listOf("yes", "no"),
            ),
        )

    /** A script writing a line and holding the screen, with nothing to click. */
    @Test
    fun `dialogue with a line and nothing to click`() =
        checkGolden(
            "dialogue-said",
            dialogueOver(
                level = "LEVEL6.INF", x = 10, y = 2,
                picture = "SOUT2.CPS", sourceLeft = 160, sourceTop = 0,
                goes = DialogueScene.PictureFrame.SPEAKER,
                message = 5,
            ),
        )

    /**
     * A line written with no box open goes on the bar beside the camp button,
     * in the ink the script asked for — "going down..." is written in 5.
     */
    @Test
    fun `a message on the bar along the bottom`() =
        checkGolden(
            "message-bar",
            messagesOver(level = "LEVEL6.INF", x = 10, y = 3, messages = listOf(1 to 5)),
        )

    /** Three short lines fill the bar, each keeping the ink it was written in. */
    @Test
    fun `three messages stack up the bar`() =
        checkGolden(
            "message-bar-three",
            messagesOver(
                level = "LEVEL4.INF",
                x = 15,
                y = 11,
                messages = listOf(0 to 5, 1 to 15, 2 to 9),
            ),
        )

    /**
     * The bar is 18 pixels of a 6 pixel font, so a long line fills three of
     * them and whatever will not fit is dropped.
     */
    @Test
    fun `a message long enough to fill the bar`() =
        checkGolden(
            "message-bar-full",
            messagesOver(level = "LEVEL4.INF", x = 15, y = 11, messages = listOf(11 to 9)),
        )

    /**
     * The party the game ships with, down the right of the screen: four
     * champions and two slots nobody has rolled up.
     */
    @Test
    fun `the quick start party on the panel`() =
        checkGolden("party-panel", partyOver(level = "LEVEL4.INF", x = 15, y = 11))

    /**
     * One item put down in each piece of floor the party can reach, so that
     * where a click puts a thing can be checked against where the renderer
     * draws it. Facing east, so that none of the four corners is its own
     * quadrant and a table written out backwards would show.
     */
    @Test
    fun `an item in each corner the party can reach`() =
        checkGolden("floor-reach", itemsInReach(level = "LEVEL4.INF", x = 15, y = 11))

    /**
     * A hand its champion cannot strike with is drawn over with a grid. The
     * paladin has been handed the mage's spellbook, which is for a class he
     * is not; the mage keeps hers, where it is no trouble at all.
     */
    @Test
    fun `a hand its champion cannot use`() =
        checkGolden(
            "party-panel-unusable",
            partyOver(
                level = "LEVEL4.INF",
                x = 15,
                y = 11,
                lendingTo = 0 to SPELLBOOK,
            ),
        )

    /**
     * The same panel with the party hurt, so the bars show all three of their
     * colours at once: well, down to a third, and out cold.
     */
    @Test
    fun `hit point bars as a party takes damage`() =
        checkGolden(
            "party-panel-hurt",
            partyOver(
                level = "LEVEL4.INF",
                x = 15,
                y = 11,
                hurtTo = listOf(78, 20, 1, -6),
            ),
        )

    /**
     * A champion's own page, which stands where the party boxes were: the
     * quick start party's cleric, with what the save says he is carrying.
     */
    @Test
    fun `a champion's belongings`() =
        checkGolden("character-sheet", sheetOver("LEVEL4.INF", x = 15, y = 11, slot = 0))

    /**
     * The same page with bar graphs turned off. Only the hit points change:
     * how full a champion is has no numbers to be written as, so the food bar
     * stays a bar.
     */
    @Test
    fun `a champion's belongings with hit points written out`() =
        checkGolden(
            "character-sheet-written",
            sheetOver(
                "LEVEL4.INF",
                x = 15,
                y = 11,
                slot = 0,
                preferences = Preferences(barGraphs = false),
            ),
        )

    /**
     * The other side of the same page: what the champion is. The party's
     * dwarf is a fighter and a thief at once, so his two careers are listed
     * one under the other with a level and an experience each.
     */
    @Test
    fun `a champion's stats`() =
        checkGolden(
            "character-sheet-stats",
            sheetOver("LEVEL4.INF", x = 15, y = 11, slot = 1, page = CharacterSheet.Page.STATS),
        )

    /**
     * Camp → Preferences, each line saying which way its setting stands.
     * Sounds is on and bar graphs off, so both readings show at once.
     */
    @Test
    fun `the preferences menu`() =
        checkGolden(
            "camp-preferences",
            menuOver(CampMenu.preferences(Preferences(sounds = true, barGraphs = false))),
        )

    /**
     * The same party with bar graphs turned off: the boxes write out what each
     * champion has left instead of drawing it, and the word HP goes with the
     * bar it was there to name.
     */
    @Test
    fun `hit points written out rather than drawn`() =
        checkGolden(
            "party-panel-written",
            partyOver(
                level = "LEVEL4.INF",
                x = 15,
                y = 11,
                hurtTo = listOf(78, 20, 1, -6),
                preferences = Preferences(barGraphs = false),
            ),
        )

    /** Camp → the menu the original opens, over the view. */
    @Test
    fun `the camp menu`() = checkGolden("camp-menu", menuOver(CampMenu.camp()))

    @Test
    fun `the game options menu`() =
        checkGolden("camp-game-options", menuOver(CampMenu.gameOptions()))

    /** Three slots used and three not, the way the original's list reads. */
    @Test
    fun `the load game slots`() =
        checkGolden(
            "camp-load-game",
            menuOver(
                CampMenu.slots(saving = false) { slot ->
                    listOf("01", "02", "LEVEL5", null, null, null)[slot]
                }
            ),
        )

    /** A slot being named, with the caret where the next letter lands. */
    @Test
    fun `naming a save slot`() =
        checkGolden(
            "camp-naming-a-slot",
            menuOver(
                CampMenu.slots(saving = true) { slot ->
                    listOf("01", "02", null, null, null, null)[slot]
                }.copy(naming = Naming(slot = 2, typed = "LEVEL4 15x11")),
            ),
        )

    private fun menuOver(menu: CampMenu): BufferedImage = runBlocking {
        val resources = ResourceRepositoryImpl()
        val cps = CpsRepositoryImpl(resources)
        val repository = repository()
        val inf = repository.loadLevel("LEVEL4.INF").getOrThrow()
        val sublevel = inf.subLevels[0]

        val viewPort = repository.renderPosition(
            items = dungeonItems,
            monsters = inf.monsterInstances,
            sublevel = sublevel,
            playerX = 15,
            playerY = 11,
            direction = Direction.NORTH,
        ).getOrThrow()

        PlayField(
            background = cps.loadCps("PLAYFLD.CPS").getOrThrow(),
            decorations = cps.loadCps("DECORATE.CPS").getOrThrow(),
            palette = sublevel.palette,
            font = FontRepositoryImpl(resources).loadFont("FONT6.FNT").getOrThrow(),
            menuFont = FontRepositoryImpl(resources).loadFont("FONT8.FNT").getOrThrow(),
        ).render(
            viewPort = viewPort,
            direction = Direction.NORTH,
            menu = menu,
        ).toImage()
    }

    /**
     * @param hurtTo current hit points per champion, or empty to leave them well
     * @param lendingTo which party slot to put which item in the second hand of
     */
    private fun partyOver(
        level: String,
        x: Int,
        y: Int,
        hurtTo: List<Int> = emptyList(),
        preferences: Preferences = Preferences(),
        lendingTo: Pair<Int, ItemIndex>? = null,
    ): BufferedImage = runBlocking {
        val resources = ResourceRepositoryImpl()
        val cps = CpsRepositoryImpl(resources)
        val repository = repository()
        val inf = repository.loadLevel(level).getOrThrow()
        val sublevel = inf.subLevels[0]

        val saved = OriginalSaveRepositoryImpl(resources)
            .loadOriginalSave(OriginalSaveRepositoryImpl.QUICK_START)
            .getOrThrow()

        val world = GameState(party = saved.standing, items = saved.items)

        var hurt = 0
        val party = saved.party.mapIndexed { slot, champion ->
            if (!champion.inTheParty) return@mapIndexed champion

            val hurtChampion = if (hurtTo.isEmpty()) champion else champion.copy(
                hitPoints = champion.hitPoints.copy(current = hurtTo[hurt++]),
            )

            if (lendingTo?.first != slot) hurtChampion else hurtChampion.copy(
                carrying = hurtChampion.carrying.toMutableList()
                    .also { it[1] = lendingTo.second },
            )
        }

        val viewPort = repository.renderPosition(
            items = dungeonItems,
            monsters = inf.monsterInstances,
            sublevel = sublevel,
            playerX = x,
            playerY = y,
            direction = Direction.NORTH,
        ).getOrThrow()

        PlayField(
            background = cps.loadCps("PLAYFLD.CPS").getOrThrow(),
            decorations = cps.loadCps("DECORATE.CPS").getOrThrow(),
            palette = sublevel.palette,
            font = FontRepositoryImpl(resources).loadFont("FONT6.FNT").getOrThrow(),
            itemIcons = cps.loadCps("ITEMICN.CPS").getOrThrow(),
            itemTypes = ItemTypesRepositoryImpl(resources).loadItemTypes().getOrThrow(),
            preferences = preferences,
        ).render(
            viewPort = viewPort,
            direction = Direction.NORTH,
            party = party,
            portraits = cps.loadCps("CHARGENA.CPS").getOrThrow(),
            carrying = { slot -> world.item(slot) },
        ).toImage()
    }

    /**
     * The level as it stands with one item put into each corner the party can
     * reach into from where they are.
     */
    private fun itemsInReach(level: String, x: Int, y: Int): BufferedImage = runBlocking {
        val repository = repository()
        val inf = repository.loadLevel(level).getOrThrow()
        val sublevel = inf.subLevels[0]
        val facing = Direction.EAST

        val here = Location(x, y)
        val (dx, dy) = facing.transformCoordinates(0, -1)
        val ahead = Location(x + dx, y + dy)

        val dagger = dungeonItems.first { it.icon.value > 0 }
        val putDown = FloorReach.entries.map { reach ->
            dagger.copy(
                level = sublevel.level,
                location = if (reach.aheadOfTheParty) ahead else here,
                pos = reach.quadrantFacing(facing),
            )
        }

        repository.renderPosition(
            items = dungeonItems + putDown,
            monsters = inf.monsterInstances,
            sublevel = sublevel,
            playerX = x,
            playerY = y,
            direction = facing,
        ).getOrThrow().toImage()
    }

    /** @param slot which of the six the page belongs to. */
    private fun sheetOver(
        level: String,
        x: Int,
        y: Int,
        slot: Int,
        page: CharacterSheet.Page = CharacterSheet.Page.BELONGINGS,
        preferences: Preferences = Preferences(),
    ): BufferedImage = runBlocking {
        val resources = ResourceRepositoryImpl()
        val cps = CpsRepositoryImpl(resources)
        val repository = repository()
        val inf = repository.loadLevel(level).getOrThrow()
        val sublevel = inf.subLevels[0]

        val saved = OriginalSaveRepositoryImpl(resources)
            .loadOriginalSave(OriginalSaveRepositoryImpl.QUICK_START)
            .getOrThrow()
        val world = GameState(party = saved.standing, items = saved.items)
        val champion = saved.party[slot]

        val viewPort = repository.renderPosition(
            items = dungeonItems,
            monsters = inf.monsterInstances,
            sublevel = sublevel,
            playerX = x,
            playerY = y,
            direction = Direction.NORTH,
        ).getOrThrow()

        PlayField(
            background = cps.loadCps("PLAYFLD.CPS").getOrThrow(),
            decorations = cps.loadCps("DECORATE.CPS").getOrThrow(),
            palette = sublevel.palette,
            font = FontRepositoryImpl(resources).loadFont("FONT6.FNT").getOrThrow(),
            invent = cps.loadCps("INVENT.CPS").getOrThrow(),
            itemIcons = cps.loadCps("ITEMICN.CPS").getOrThrow(),
            itemTypes = ItemTypesRepositoryImpl(resources).loadItemTypes().getOrThrow(),
            preferences = preferences,
        ).render(
            viewPort = viewPort,
            direction = Direction.NORTH,
            party = saved.party,
            portraits = cps.loadCps("CHARGENA.CPS").getOrThrow(),
            sheet = OpenSheet(
                page = page,
                champion = champion,
                carrying = champion.carrying.map { world.item(it) },
            ),
        ).toImage()
    }

    /** @param messages the level's own message ids, each with the ink to write it in. */
    private fun messagesOver(
        level: String,
        x: Int,
        y: Int,
        messages: List<Pair<Int, Int>>,
    ): BufferedImage =
        runBlocking {
            val resources = ResourceRepositoryImpl()
            val cps = CpsRepositoryImpl(resources)
            val repository = repository()
            val inf = repository.loadLevel(level).getOrThrow()
            val sublevel = inf.subLevels[0]

            val viewPort = repository.renderPosition(
                items = dungeonItems,
                monsters = inf.monsterInstances,
                sublevel = sublevel,
                playerX = x,
                playerY = y,
                direction = Direction.NORTH,
            ).getOrThrow()

            PlayField(
                background = cps.loadCps("PLAYFLD.CPS").getOrThrow(),
                decorations = cps.loadCps("DECORATE.CPS").getOrThrow(),
                palette = sublevel.palette,
                font = FontRepositoryImpl(resources).loadFont("FONT6.FNT").getOrThrow(),
            ).render(
                viewPort = viewPort,
                direction = Direction.NORTH,
                messages = messages.mapNotNull { (id, ink) ->
                    inf.message(MessageId(id))?.let { PlayField.Message(it, PaletteIndex(ink)) }
                },
            ).toImage()
        }

    private fun dialogueOver(
        level: String,
        x: Int,
        y: Int,
        picture: String? = null,
        sourceLeft: Int = 0,
        sourceTop: Int = 0,
        goes: DialogueScene.PictureFrame = DialogueScene.PictureFrame.SPEAKER,
        textId: Int? = null,
        message: Int? = null,
        buttons: List<String> = emptyList(),
        direction: Direction = Direction.NORTH,
    ): BufferedImage = runBlocking {
        val resources = ResourceRepositoryImpl()
        val cps = CpsRepositoryImpl(resources)
        val repository = repository()
        val inf = repository.loadLevel(level).getOrThrow()
        val sublevel = inf.subLevels[0]

        val viewPort = repository.renderPosition(
            items = dungeonItems,
            monsters = inf.monsterInstances,
            sublevel = sublevel,
            playerX = x,
            playerY = y,
            direction = Direction.NORTH,
        ).getOrThrow()

        val font = FontRepositoryImpl(resources).loadFont("FONT6.FNT").getOrThrow()

        // a question's words come from the shared text file; a line a script
        // writes into the box is one of the level's own messages
        val text = textId
            ?.let { DialogueTextRepositoryImpl(resources).text(DialogueTextId(it)).getOrThrow().first }
            ?: message?.let { inf.message(MessageId(it)) }
            ?: ""

        PlayField(
            background = cps.loadCps("PLAYFLD.CPS").getOrThrow(),
            decorations = cps.loadCps("DECORATE.CPS").getOrThrow(),
            palette = sublevel.palette,
            font = font,
        ).render(
            viewPort = viewPort,
            direction = Direction.NORTH,
            dialogue = DialogueScene.layout(
                frame = cps.loadCps("BORDER.CPS").getOrThrow(),
                portrait = picture?.let {
                    DialogueScene.Picture(
                        cps = cps.loadCps(it).getOrThrow(),
                        sourceLeft = sourceLeft,
                        sourceTop = sourceTop,
                        goes = goes,
                    )
                },
                text = text,
                buttonLabels = buttons,
                font = font,
            ),
        ).toImage()
    }

    private fun PlayField.toImage(): BufferedImage {
        val image = BufferedImage(PlayField.WIDTH, PlayField.HEIGHT, BufferedImage.TYPE_INT_ARGB)
        getRows().forEachIndexed { y, row ->
            row.forEachIndexed { x, rgb ->
                val argb = if (rgb.transparent) 0
                else (0xFF shl 24) or (rgb.red shl 16) or (rgb.green shl 8) or rgb.blue
                image.setRGB(x, y, argb)
            }
        }
        return image
    }

    /**
     * The wall with the button on it, and the same wall after the button has
     * been clicked: the script opens it into a teleporter.
     */
    @Test
    fun `level5 wall with a button`() =
        checkGolden(
            "level5-button",
            renderAfterClicking("LEVEL5.INF", number = 5, x = 10, y = 8, clicked = null),
        )

    @Test
    fun `level5 wall opened by its button`() =
        checkGolden(
            "level5-button-clicked",
            renderAfterClicking(
                "LEVEL5.INF",
                number = 5,
                x = 10,
                y = 8,
                clicked = Location(9, 8),
            ),
        )

    /**
     * The other half of the teleporter's flicker. Nothing but the sparks may
     * differ between this and `level5-button-clicked`.
     */
    @Test
    fun `level5 teleporter on the other half of its pulse`() =
        checkGolden(
            "level5-teleporter-traded",
            renderAfterClicking(
                "LEVEL5.INF",
                number = 5,
                x = 10,
                y = 8,
                clicked = Location(9, 8),
                pulse = TeleporterPulse.TRADED,
            ),
        )

    /**
     * The same teleporter a square further back, where it wears the middle of
     * the three hazes. The corridor turns before the third, so the smallest
     * haze has nowhere on this level to be seen from.
     */
    @Test
    fun `level5 teleporter two squares back`() =
        checkGolden(
            "level5-teleporter-two-back",
            renderAfterClicking(
                "LEVEL5.INF",
                number = 5,
                x = 11,
                y = 8,
                clicked = Location(9, 8),
            ),
        )

    /**
     * Renders what the party see, having clicked the wall of [clicked] if
     * anything — the click runs that square's script, which is what changes
     * the wall.
     */
    private fun renderAfterClicking(
        level: String,
        number: Int,
        x: Int,
        y: Int,
        clicked: Location?,
        pulse: TeleporterPulse = TeleporterPulse.AS_LAID_OUT,
    ): ViewPort = runBlocking {
        val repository = repository()
        val inf = repository.loadLevel(level).getOrThrow()
        val sublevel = inf.subLevels[0]

        val standing = GameState(
            party = PartyState(Location(x, y), Direction.WEST),
            monsters = inf.monsterInstances,
        ).arrivingAt(number, inf.monsterInstances, sublevel.maz)

        val world = if (clicked == null) {
            standing
        } else {
            LevelScriptRunner(inf.script, level = number).onEvent(
                triggers = inf.triggers,
                event = ScriptEvent.WALL_CLICKED,
                state = standing,
                at = clicked,
            ).state
        }

        repository.renderPosition(
            items = dungeonItems,
            monsters = world.monsters,
            sublevel = sublevel,
            playerX = x,
            playerY = y,
            direction = Direction.WEST,
            wallAt = { at, side -> world.wall(number, at, side) },
            pulse = pulse,
        ).getOrThrow()
    }

    private fun repository(): ViewConeRepositoryImpl {
        val resources = ResourceRepositoryImpl()
        val palRepository = PalRepositoryImpl(resources)
        val cpsRepository = CpsRepositoryImpl(resources)
        return ViewConeRepositoryImpl(
            infRepository = InfRepositoryImpl(
                resourceRepository = resources,
                mazRepository = MazRepositoryImpl(resources),
                vmpRepository = VmpRepositoryImpl(resources),
                vcnRepository = VcnRepositoryImpl(resources),
                palRepository = palRepository,
                cpsRepository = cpsRepository,
                decRepository = DecRepositoryImpl(resources),
            ),
            cpsRepository = cpsRepository,
            dcrRepository = DcrRepositoryImpl(resources),
        )
    }

    /**
     * Renders the frame a square's trigger script puts up rather than the level
     * as loaded, so a scene the party is walked into — a monster conjured, the
     * party spun round to face it — is drawn the way the player meets it.
     *
     * A script says when it wants to be seen, so the frame drawn here is the
     * one it drew first. Everything after that is the conversation, which a
     * golden has no way to click through.
     */
    private fun renderAfterStepping(
        level: String,
        number: Int,
        x: Int,
        y: Int,
        direction: Direction,
        /** The buttons to click, in order, for a script that asks its way along. */
        answers: List<Int> = emptyList(),
        /** Which frame the script drew to render; [LAST_FRAME] takes its final one. */
        frame: Int = 0,
    ): ViewPort = runBlocking {
        val repository = repository()
        val inf = repository.loadLevel(level).getOrThrow()

        val stage = RecordingStage(answers)
        val stepped = LevelScriptRunner(inf.script, level = number).onEvent(
            triggers = inf.triggers,
            event = ScriptEvent.PARTY_ENTERED,
            state = GameState(
                party = PartyState(Location(x, y), direction),
                monsters = inf.monsterInstances,
            ),
            stage = stage,
        )
        val wanted = if (frame == LAST_FRAME) stage.shown.lastIndex else frame
        val world = stage.shown.getOrNull(wanted) ?: stepped.state

        repository.renderPosition(
            items = dungeonItems,
            monsters = world.monsters,
            sublevel = inf.subLevels[0],
            playerX = world.party.position.x,
            playerY = world.party.position.y,
            direction = world.party.facing,
        ).getOrThrow()
    }

    /**
     * The same view with some faces set to a byte the level does not hold, so a
     * door a level keeps fully open can be seen as a panel.
     *
     * @param instead the byte to use at a face, or null to take the level's own
     */
    private fun renderFrame(
        level: String,
        x: Int,
        y: Int,
        direction: Direction,
        instead: (Location, WallSide) -> WallByte?,
    ): ViewPort = runBlocking {
        val repository = repository()
        val inf = repository.loadLevel(level).getOrThrow()
        val sublevel = inf.subLevels[inf.subLevelAt(0, x, y, direction)]

        repository.renderPosition(
            items = dungeonItems,
            monsters = inf.monsterInstances.arrivingIn(sublevel.index),
            sublevel = sublevel,
            playerX = x,
            playerY = y,
            direction = direction,
            wallAt = { at, side ->
                instead(at, side)?.let { Maz.WallType.of(it) }
                    ?: sublevel.maz.square(at).getWall(side)
            },
        ).getOrThrow()
    }

    /** What the party entering a sublevel does to the monsters the file lists. */
    private fun List<MonsterInstance>.arrivingIn(subLevel: Int) =
        map { it.copy(subLevel = subLevel) }

    private fun renderFrame(
        level: String,
        x: Int,
        y: Int,
        direction: Direction,
        arrivedIn: Int = 0,
    ): ViewPort =
        runBlocking {
            val repository = repository()
            val inf = repository.loadLevel(level).getOrThrow()
            val sublevel = inf.subLevels[inf.subLevelAt(arrivedIn, x, y, direction)]
            repository.renderPosition(
                items = dungeonItems,
                monsters = inf.monsterInstances.arrivingIn(sublevel.index),
                sublevel = sublevel,
                playerX = x,
                playerY = y,
                direction = direction
            ).getOrThrow()
        }

    /** The same choice the game makes when no script has said which sublevel. */
    private fun Inf.subLevelAt(showing: Int, x: Int, y: Int, direction: Direction): Int {
        val maz = subLevels[showing].maz
        return subLevelShowing(
            showing = showing,
            sight = wallsInSight(Location(x, y), direction) { at, side ->
                maz.square(at).getWall(side)
            },
        )
    }

    private fun ViewPort.toImage(): BufferedImage {
        val image = BufferedImage(ViewPort.COLS, ViewPort.ROWS, BufferedImage.TYPE_INT_ARGB)
        getRows().forEachIndexed { y, row ->
            row.forEachIndexed { x, rgb ->
                val argb = if (rgb.transparent) {
                    0
                } else {
                    (0xFF shl 24) or (rgb.red shl 16) or (rgb.green shl 8) or rgb.blue
                }
                image.setRGB(x, y, argb)
            }
        }
        return image
    }

    private fun countDifferingPixels(golden: BufferedImage, actual: BufferedImage): Int {
        if (golden.width != actual.width || golden.height != actual.height) {
            return golden.width * golden.height
        }
        var count = 0
        for (y in 0 until golden.height) {
            for (x in 0 until golden.width) {
                if (golden.getRGB(x, y) != actual.getRGB(x, y)) count++
            }
        }
        return count
    }

    /** Golden pixels dimmed to grayscale, differing pixels highlighted in red. */
    private fun diffImage(golden: BufferedImage, actual: BufferedImage): BufferedImage {
        val diff = BufferedImage(golden.width, golden.height, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until golden.height) {
            for (x in 0 until golden.width) {
                val g = golden.getRGB(x, y)
                diff.setRGB(
                    x, y,
                    if (x < actual.width && y < actual.height && g == actual.getRGB(x, y)) {
                        val gray = ((g shr 16 and 0xFF) + (g shr 8 and 0xFF) + (g and 0xFF)) / 6
                        (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
                    } else {
                        0xFFFF0000.toInt()
                    }
                )
            }
        }
        return diff
    }

    companion object {
        /** Render the last frame a script drew rather than its first. */
        private const val LAST_FRAME = -1

        /** Door 1 without a button, two steps of five out of its frame. */
        private val HALF_OPEN = WallByte(10)

        /** Door 1 without a button, three steps of five out of its frame. */
        private val ALMOST_OPEN = WallByte(11)

        private val DOORS_BESIDE_2X10 = listOf(Location(4, 9), Location(4, 11))

        // Gradle runs jvmTest with the subproject as working directory, but be
        // tolerant of an IDE launching from the repository root.
        private val projectDir: File = run {
            val cwd = File(System.getProperty("user.dir"))
            if (cwd.resolve("src/jvmTest").isDirectory) cwd else cwd.resolve("composeApp")
        }

        private val goldensDir = projectDir.resolve("src/jvmTest/goldens")
        private val failuresDir = projectDir.resolve("build/golden-failures")

        private val updateGoldens =
            System.getenv("UPDATE_GOLDENS") == "1" || System.getProperty("updateGoldens") != null
    }
}
