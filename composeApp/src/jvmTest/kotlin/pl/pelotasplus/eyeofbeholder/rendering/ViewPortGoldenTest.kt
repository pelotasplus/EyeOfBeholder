package pl.pelotasplus.eyeofbeholder.rendering

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import kotlinx.coroutines.runBlocking
import org.junit.experimental.categories.Category
import pl.pelotasplus.eyeofbeholder.NeedsGameData
import pl.pelotasplus.eyeofbeholder.data.model.CampMenu
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.CharacterSheet
import pl.pelotasplus.eyeofbeholder.data.model.OpenSheet
import pl.pelotasplus.eyeofbeholder.data.model.Preferences
import pl.pelotasplus.eyeofbeholder.data.model.Damage
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.ItemKind
import pl.pelotasplus.eyeofbeholder.data.repository.ItemTypesRepositoryImpl
import pl.pelotasplus.eyeofbeholder.data.model.Maz
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSlot
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSpell
import pl.pelotasplus.eyeofbeholder.data.model.SparksInTheRoom
import pl.pelotasplus.eyeofbeholder.data.model.MonsterTypeId
import pl.pelotasplus.eyeofbeholder.data.model.MonsterPose
import pl.pelotasplus.eyeofbeholder.data.model.NpcId
import pl.pelotasplus.eyeofbeholder.data.model.NpcMeeting
import pl.pelotasplus.eyeofbeholder.data.model.WallByte
import pl.pelotasplus.eyeofbeholder.data.model.WallSide
import pl.pelotasplus.eyeofbeholder.data.model.getWall
import pl.pelotasplus.eyeofbeholder.data.model.wallsInSight
import pl.pelotasplus.eyeofbeholder.data.model.LevelScriptRunner
import pl.pelotasplus.eyeofbeholder.data.model.FloorReach
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.MessageId
import pl.pelotasplus.eyeofbeholder.data.model.OnAParchment
import pl.pelotasplus.eyeofbeholder.data.model.Naming
import pl.pelotasplus.eyeofbeholder.data.model.PaletteIndex
import pl.pelotasplus.eyeofbeholder.data.model.PartySlot
import pl.pelotasplus.eyeofbeholder.data.model.PartyState
import pl.pelotasplus.eyeofbeholder.data.model.Burst
import pl.pelotasplus.eyeofbeholder.data.model.ConjuredBolt
import pl.pelotasplus.eyeofbeholder.data.model.Dice
import pl.pelotasplus.eyeofbeholder.data.model.Projectile
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.THROWN_CPS
import pl.pelotasplus.eyeofbeholder.data.model.WhatTheBlowCameTo
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
@Category(NeedsGameData::class)
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

    /** The one the party meet on level 1, whose scene is the one drawn here. */
    private val INSAL = NpcMeeting.called(NpcId(0))!!

    @Test
    fun `level7 start position`() =
        checkGolden("level7-start", "LEVEL7.INF", x = 29, y = 15, direction = Direction.SOUTH)

    @Test
    fun `level7 silver tower entrance`() =
        checkGolden("level7-silver-tower", "LEVEL7.INF", x = 15, y = 6, direction = Direction.EAST)

    @Test
    fun `level6 temple`() =
        checkGolden("level6-temple", "LEVEL6.INF", x = 27, y = 29, direction = Direction.NORTH)

    /**
     * A face that is seen through but cannot be reached through, which is
     * what showed that the two are not the same question.
     */
    @Test
    fun `level6 16x2 north`() =
        checkGolden("level6-16x2-north", "LEVEL6.INF", x = 16, y = 2, direction = Direction.NORTH)

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

    /**
     * A wall index the level maps nothing for, which is a wall all the same:
     * 16x6's south face is index 55 and level 5 names no wall set for it, so
     * nothing is drawn there and the party cannot walk through it either.
     */
    @Test
    fun `level5 a wall the level maps nothing for`() =
        checkGolden("level5-unmapped-wall", "LEVEL5.INF", x = 16, y = 7, direction = Direction.NORTH)

    /**
     * The nearer cleric the moment it is hit, drawn as its own silhouette in
     * one colour while the other stands beside it in its own.
     *
     * A flash is a thing that passes, so it is frozen at the phase that shows
     * it and rendered from that rather than from a clock.
     */
    @Test
    fun `level5 a monster the moment it is struck`() =
        checkGolden(
            "level5-monster-struck",
            renderFrame("LEVEL5.INF", x = 13, y = 9, direction = Direction.NORTH) { monsters ->
                monsters.map { it.copy(struck = it.place == SquarePlace.SOUTH_WEST) }
            },
        )

    /**
     * The nearer cleric swinging at the party: the arm going back, and then
     * coming down. Two frames, and a monster is only drawn in them from the
     * square right in front — one further off and it stands as it stood.
     *
     * A swing is a thing that passes, so each phase is frozen and rendered
     * from an explicit one rather than from a clock.
     */
    @Test
    fun `level5 a monster winding up to strike`() =
        checkGolden(
            "level5-monster-winding-up",
            renderFrame("LEVEL5.INF", x = 13, y = 9, direction = Direction.NORTH) { monsters ->
                monsters.map { it.striking(MonsterPose.ATTACK_A) }
            },
        )

    @Test
    fun `level5 a monster bringing the blow down`() =
        checkGolden(
            "level5-monster-striking",
            renderFrame("LEVEL5.INF", x = 13, y = 9, direction = Direction.NORTH) { monsters ->
                monsters.map { it.striking(MonsterPose.ATTACK_B) }
            },
        )

    /** Only the one in the near corner swings; the other keeps its own pose. */
    private fun MonsterInstance.striking(pose: MonsterPose) =
        if (place == SquarePlace.SOUTH_WEST) copy(striking = pose) else this

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

    /** A wolf side-on from the square in front of it, whole. */
    @Test
    fun `level4 18x14 south`() =
        checkGolden("level4-18x14-south", "LEVEL4.INF", x = 18, y = 14, direction = Direction.SOUTH)

    /**
     * The same wolf a square further back, where the side wall it walks past
     * used to take its tail off.
     */
    @Test
    fun `level4 18x13 south`() =
        checkGolden("level4-18x13-south", "LEVEL4.INF", x = 18, y = 13, direction = Direction.SOUTH)

    /**
     * The same wolf with another of its kind sharing the square, which is as
     * many as that size goes: the two stand on opposite corners and the pack
     * arrives in pairs.
     */
    @Test
    fun `level4 two wolves on one square`() =
        checkGolden(
            "level4-wolves-bunched",
            renderFrame("LEVEL4.INF", x = 18, y = 14, direction = Direction.SOUTH) { monsters ->
                val joined = monsters.first { it.x == 18 && it.y == 15 }

                monsters.map {
                    if (it.index == joined.index) it.copy(place = SquarePlace.NORTH_WEST) else it
                } + joined.copy(index = MonsterSlot(29), place = SquarePlace.SOUTH_EAST)
            },
        )

    /** And two squares back, where the same cut was made a size smaller. */
    @Test
    fun `level4 18x12 south`() =
        checkGolden("level4-18x12-south", "LEVEL4.INF", x = 18, y = 12, direction = Direction.SOUTH)

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
     * The corridor north of 12x4, which is where more than one kind of monster
     * has been seen sharing a square. This is the level as its file lays it
     * out, so it says what is standing there before anything has walked.
     */
    @Test
    fun `level3 the corridor north of 12x4`() =
        checkGolden("level3-north-of-12x4", "LEVEL3.INF", x = 12, y = 4, direction = Direction.NORTH)

    /**
     * Looking east into the margoyle trap from 14x13. Five of them are placed
     * along 15x12 to 15x15 and 16x13, penned in by the two walls the trap
     * takes down, so this is what springing it should leave in sight.
     */
    @Test
    fun `level3 into the margoyle trap`() =
        checkGolden("level3-margoyle-trap", "LEVEL3.INF", x = 14, y = 13, direction = Direction.EAST)

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

    /**
     * The corridor down the seventh floor's second sublevel, looking at a
     * square something is standing on behind a wall. Whatever is on the far
     * side of masonry should not be drawn through it, and these two are the
     * pair that say whether it is: one down the length of the corridor, and
     * one with the wall a single step ahead.
     */
    @Test
    fun `level7 7x20 south`() =
        checkGolden("level7-7x20-south", "LEVEL7.INF", x = 7, y = 20, direction = Direction.SOUTH)

    @Test
    fun `level7 8x22 south`() =
        checkGolden("level7-8x22-south", "LEVEL7.INF", x = 8, y = 22, direction = Direction.SOUTH)

    /**
     * The same wall, with something standing on the far side of it.
     *
     * The two above are of an empty corridor, because a floor's own monsters
     * are the ones it was shipped with and this floor ships none down here —
     * whatever walks these squares is conjured while it is played. So one is
     * put there by hand: a square the party cannot see into should not show
     * what is standing on it, however it came to be there.
     */
    @Test
    fun `level7 8x22 south with something behind the wall`() =
        checkGolden(
            "level7-8x22-south-behind-the-wall",
            renderFrame("LEVEL7.INF", x = 8, y = 22, direction = Direction.SOUTH) { monsters ->
                monsters.map {
                    it.copy(location = Location(8, 23), subLevel = 1, place = SquarePlace.NORTH_WEST)
                }
            },
        )

    /**
     * A square with as many on it as it can hold: four, one to each corner.
     *
     * There are four sets of screen coordinates and no fifth, which is why a
     * square cannot usefully hold more — put two on a corner and the second
     * is drawn over the first, pixel for pixel, biting from inside a picture
     * of one. That is what [GameState.monsterCreated] refuses, and this is
     * the arrangement it refuses anything beyond.
     */
    @Test
    fun `level7 7x20 south with four on one square`() =
        checkGolden(
            "level7-7x20-south-four-on-a-square",
            renderFrame("LEVEL7.INF", x = 7, y = 20, direction = Direction.SOUTH) {
                waspsOn7x21(
                    SquarePlace.NORTH_WEST,
                    SquarePlace.NORTH_EAST,
                    SquarePlace.SOUTH_WEST,
                    SquarePlace.SOUTH_EAST,
                )
            },
        )

    private fun waspsOn7x21(vararg corners: SquarePlace) =
        corners.toList().mapIndexed { slot, corner ->
            MonsterInstance(
                index = MonsterSlot(slot),
                unit = 0,
                location = Location(7, 21),
                place = corner,
                direction = Direction.NORTH,
                type = MonsterTypeId(0),
                gfxIndex = 0,
                mode = 0,
                pause = 0,
                weapon = 0,
                pocketItem = 0,
                subLevel = 1,
            )
        }

    /**
     * The eighth floor, which is the first this suite has looked at.
     */
    @Test
    fun `level8 12x1 north`() =
        checkGolden("level8-12x1-north", "LEVEL8.INF", x = 12, y = 1, direction = Direction.NORTH)

    /**
     * The same corridor with what the floor actually puts at the end of it.
     *
     * This floor ships no monsters at all — everything on it is conjured by a
     * script, so the empty frame above is the whole of what the file says is
     * there. Walking onto 16x3 is what fills it: a gas spore straight ahead on
     * 12x0, and a flying snake off on 14x1. The spore is the one worth a
     * picture, being the first thing drawn that has one hit point and bursts
     * rather than dies.
     */
    @Test
    fun `level8 12x1 north with the spore it is sent`() =
        checkGolden(
            "level8-12x1-north-a-gas-spore",
            renderFrame("LEVEL8.INF", x = 12, y = 1, direction = Direction.NORTH) {
                listOf(
                    MonsterInstance(
                        index = MonsterSlot(0),
                        unit = 0,
                        location = Location(12, 0),
                        place = SquarePlace.MIDDLE,
                        direction = Direction.SOUTH,
                        type = MonsterTypeId(5),
                        gfxIndex = 1,
                        mode = 0,
                        pause = 0,
                        weapon = 0,
                        pocketItem = 0,
                    ),
                )
            },
        )

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

    /** The web across the corridor, which is a wall until it is cut down. */
    @Test
    fun `level1 27x9 north`() =
        checkGolden("level1-27x9-north", "LEVEL1.INF", x = 27, y = 9, direction = Direction.NORTH)

    /**
     * The same web after a blow, which is where it goes rather than what it
     * looks like: it is torn rather than taken away, so a good deal of it is
     * still hanging there once the party can walk through.
     */
    @Test
    fun `level1 27x9 north with the web cut down`() {
        val inf = runBlocking { repository().loadLevel("LEVEL1.INF").getOrThrow() }
        val sublevel = inf.subLevels[inf.subLevelAt(0, 27, 9, Direction.NORTH)]

        val cut = GameState(party = PartyState(Location(27, 9), Direction.NORTH))
            .arrivingAt(level = 1, places = emptyList(), maz = sublevel.maz)
            .websCutOn(1, Location(27, 8), sublevel.wallsThatGiveWay)

        checkGolden(
            "level1-27x9-north-cut",
            renderFrame(
                level = "LEVEL1.INF",
                x = 27,
                y = 9,
                direction = Direction.NORTH,
                instead = { at, side -> cut.wallByte(1, at, side) },
            ),
        )
    }

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
        sameAsTheBuffer(
            fromBuffer = viewPort.toImage(),
            fromBitmap = viewPort.toImageBitmap(),
            what = "the view",
        )
    }

    /**
     * The whole screen the same way. This is the one the app puts up, and the
     * goldens do not go through it — they rasterize the buffer themselves — so
     * without this nothing would notice the rasterizer losing a pixel.
     */
    @Test
    fun `the screen's toImageBitmap matches the raw pixel buffer`() {
        val screen = screenOver(menu = null)
        sameAsTheBuffer(
            fromBuffer = screen.toImage(),
            fromBitmap = screen.toImageBitmap(),
            what = "the screen",
        )
    }

    private fun sameAsTheBuffer(
        fromBuffer: BufferedImage,
        fromBitmap: ImageBitmap,
        what: String,
    ) {
        val pixels = fromBitmap.toPixelMap()

        var differing = 0
        for (y in 0 until fromBuffer.height) {
            for (x in 0 until fromBuffer.width) {
                val expected = fromBuffer.getRGB(x, y)
                val actual = pixels[x, y].toArgb()
                // compare only visible pixels; both encode transparent as alpha 0
                val same = if ((expected ushr 24) == 0) (actual ushr 24) == 0 else expected == actual
                if (!same) differing++
            }
        }
        if (differing > 0) {
            fail("toImageBitmap differs from $what's pixel buffer at $differing pixels")
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

    /**
     * The widest question the game asks: which of a full party leaves to make
     * room for somebody met in the dungeon. Seven answers do not fit a row, so
     * they are laid three to a row and wrap onto a second and a third.
     *
     * Two answers stand side by side and any more go into the grid, so this is
     * also where a fourth answer crowding the first row would show.
     */
    @Test
    fun `dialogue asking which of six champions to drop`() =
        checkGolden(
            "dialogue-who-leaves",
            dialogueOver(
                level = "LEVEL2.INF", x = 13, y = 26,
                textId = 4,
                buttons = listOf(
                    "Pericles", "\"Stumpy\"", "Wolfspirit",
                    "Laurann", "Insal", "Shorn",
                    NpcMeeting.ABORT,
                ),
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
     * A page read off something that was picked up rather than said by anyone:
     * the orders the old woman on level 4 was carrying. It goes up over the
     * view rather than in the strip a script speaks from, so the party's side
     * of the screen stays in sight and the word that closes it sits in the
     * page's own corner.
     */
    @Test
    fun `a parchment being read`() =
        checkGolden(
            "parchment-read",
            dialogueOver(
                level = "LEVEL4.INF", x = 12, y = 11,
                textId = 15, buttons = listOf(DialogueScene.OK), waitsToBeRead = true,
                readOff = DialogueScene.ReadOff.APageOverTheView,
            ),
        )

    /**
     * How a parchment is actually reached: off an open page, since that is
     * where a thing being carried is clicked. The page stays in sight beside
     * what is being read — it is the only way to put the parchment down again.
     */
    @Test
    fun `a parchment read off an open page`() =
        checkGolden(
            "parchment-read-from-the-page",
            sheetOver(
                "LEVEL4.INF", x = 15, y = 11, slot = 0,
                reading = OnAParchment.Writing(DialogueTextId(15)),
            ),
        )

    /**
     * And a map off the same page. A map is wider than the page it is read
     * from is narrow — its frame reaches eight pixels past where the champion's
     * panel starts — so this is what says the panel is drawn over it and not
     * under it.
     */
    @Test
    fun `a map looked at off an open page`() =
        checkGolden(
            "parchment-map-from-the-page",
            sheetOver(
                "LEVEL4.INF", x = 15, y = 11, slot = 0,
                reading = OnAParchment.Map(0, 0),
            ),
        )

    /**
     * The other thing a parchment can be: the map lying on level 1 at 23x11.
     * A picture is looked at rather than read — the frame a speaker would be
     * in, nothing written under it, and nothing to press.
     */
    @Test
    fun `a map being looked at`() =
        checkGolden(
            "parchment-map",
            mapOver("LEVEL1.INF", x = 23, y = 12, map = OnAParchment.Map(0, 0)),
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
     * Things lying on the squares diagonally ahead, with the walls of those
     * squares standing between them and the party.
     *
     * The wall wins: what is behind one is not seen through it. This is the
     * scene a step sideways makes — a thing that was lying in front of the
     * party is suddenly round a corner — and it is where measuring a wall at
     * its square rather than at the wall itself gives out, since the square is
     * further off than the very thing the wall has to hide.
     */
    @Test
    fun `a thing round the corner is behind the wall`() =
        checkGolden(
            "item-behind-a-side-wall",
            anItemOnTheNextSquareOver(level = "LEVEL12.INF", x = 14, y = 30),
        )

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
     * What the slot says a blow came to, which is the only report of it there
     * is: a number on a splash of blood for one that landed, the same for a
     * miss, and a warning box for the arm that never went anywhere.
     *
     * All three at once here, on the three champions who have one, so that the
     * two backgrounds and the one and two line layouts are all in one frame.
     */
    @Test
    fun `what the slots say a blow came to`() =
        checkGolden(
            "party-panel-blows",
            partyOver(
                level = "LEVEL4.INF", x = 15, y = 11,
                reported = mapOf(
                    (0 to 0) to WhatTheBlowCameTo.TookOff(Damage(7)),
                    (1 to 0) to WhatTheBlowCameTo.Missed,
                    (2 to 0) to WhatTheBlowCameTo.CannotReach,
                ),
            ),
        )

    /**
     * A hand that has just swung, drawn over with the same grid as a hand
     * holding something its champion cannot use. The two are not told
     * the two apart, and neither does this — the weapon is still shown under
     * it, and shown to be no use for the moment.
     *
     * The first champion's weapon hand here; the shield hand beside it and
     * everybody else's are untouched, which is the half worth seeing.
     */
    @Test
    fun `a hand that has just swung`() =
        checkGolden(
            "party-panel-swung",
            partyOver(level = "LEVEL4.INF", x = 15, y = 11, swung = listOf(0 to 0)),
        )

    /**
     * A blow just taken, splashed over the portrait with the number on it.
     *
     * One digit, two digits and the biggest a champion can be hit for at once,
     * so the way the number is centred can be seen to hold. The fourth is left
     * clean for comparison.
     */
    @Test
    fun `a blow just taken shows on the portrait`() =
        checkGolden(
            "party-panel-splattered",
            partyOver(
                level = "LEVEL4.INF", x = 15, y = 11,
                splattered = mapOf(0 to Damage(3), 1 to Damage(12), 2 to Damage(127)),
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

    /** Camp → the menu it opens, over the view. */
    @Test
    fun `the camp menu`() = checkGolden("camp-menu", menuOver(CampMenu.camp()))

    @Test
    fun `the game options menu`() =
        checkGolden("camp-game-options", menuOver(CampMenu.gameOptions()))

    /**
     * The party asleep. It fills the panel like a menu and has nothing on it
     * to click, since anything the player does wakes them.
     */
    @Test
    fun `the resting party box`() =
        checkGolden("camp-resting", menuOver(CampMenu.resting(hours = 16)))

    /**
     * The questions a rest asks, each a smaller box set into the resting one
     * with the two answers side by side under it — the hours stay legible
     * above, which is what says the party are still asleep behind it.
     */
    @Test
    fun `the starving question`() =
        checkGolden("camp-starving", menuOver(CampMenu.starving(hours = 16)))

    @Test
    fun `the still injured question`() =
        checkGolden("camp-still-injured", menuOver(CampMenu.stillInjured(hours = 0)))

    /** Three slots used and three not, the way the list reads. */
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

    private fun menuOver(menu: CampMenu): BufferedImage = screenOver(menu).toImage()

    private fun screenOver(menu: CampMenu?): PlayField = runBlocking {
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
        )
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
        /** Whose hands have just swung, and so are drawn over with the grid. */
        swung: List<Pair<Int, Int>> = emptyList(),
        /** And what a hand's swing came to, while its slot is still saying. */
        reported: Map<Pair<Int, Int>, WhatTheBlowCameTo> = emptyMap(),
        /** What a champion has just been hit for, while it still shows. */
        splattered: Map<Int, Damage> = emptyMap(),
        /** Somebody met in the dungeon, and which place they take. */
        joinedBy: Champion? = null,
        joiningAt: Int = 5,
        /** Which of them the venom has hold of, by the place they stand in. */
        poisoned: List<Int> = emptyList(),
        /** Who is waiting to change places, on the half the word is showing. */
        swapping: Int? = null,
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

            val lent = if (lendingTo?.first != slot) hurtChampion else hurtChampion.copy(
                carrying = hurtChampion.carrying.toMutableList()
                    .also { it[1] = lendingTo.second },
            )

            if (slot in poisoned) lent.poisoned(true) else lent
        }.let { standing ->
            if (joinedBy == null) standing
            else standing.mapIndexed { slot, champion ->
                when {
                    slot < joiningAt -> champion
                    slot == joiningAt -> joinedBy
                    else -> Champion.NOBODY
                }
            }
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
            thrown = cps.loadCps(THROWN_CPS).getOrThrow(),
            preferences = preferences,
        ).render(
            viewPort = viewPort,
            direction = Direction.NORTH,
            party = party,
            portraits = cps.loadCps("CHARGENA.CPS").getOrThrow(),
            metPortraits = cps.loadCps(NpcMeeting.FACES).getOrThrow(),
            carrying = { slot -> world.item(slot) },
            recovering = { whose, hand -> (whose.index to hand.index) in swung },
            reporting = { whose, hand -> reported[whose.index to hand.index] },
            hurt = { whose -> splattered[whose.index] },
            swapping = swapping?.let(::PartySlot),
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
                place = reach.placeFacing(facing),
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

    /**
     * The level with an item in every corner of both squares diagonally ahead.
     *
     * Every corner rather than one, because nothing chose these: a thing a
     * monster dropped where it died lies wherever it fell, so each of the eight
     * is a place the party can find one. The two squares are asked together so
     * that the left and the right of the view answer in the same frame — a
     * table of corners written out mirrored shows here and nowhere else.
     */
    private fun anItemOnTheNextSquareOver(level: String, x: Int, y: Int): BufferedImage =
        runBlocking {
            val repository = repository()
            val inf = repository.loadLevel(level).getOrThrow()
            val sublevel = inf.subLevels[inf.subLevelAt(0, x, y, Direction.NORTH)]

            // A key, because that is what the scene is about: the small bright
            // thing a monster leaves behind, which is exactly the size that
            // shows through a wall without anybody noticing.
            val itemTypes = ItemTypesRepositoryImpl(ResourceRepositoryImpl())
                .loadItemTypes().getOrThrow()
            val key = dungeonItems.first { itemTypes.kindOf(it) == ItemKind.KEY }

            val dropped = listOf(x - 1, x + 1).flatMap { acrossFrom ->
                SquarePlace.entries.filter { it.onTheFloor }.map { corner ->
                    key.copy(
                        level = sublevel.level,
                        location = Location(acrossFrom, y - 1),
                        place = corner,
                    )
                }
            }

            repository.renderPosition(
                items = dungeonItems + dropped,
                monsters = emptyList(),
                sublevel = sublevel,
                playerX = x,
                playerY = y,
                direction = Direction.NORTH,
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
        /** A parchment held up over the view, which is how one is read: off the open page. */
        reading: OnAParchment? = null,
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

        val font = FontRepositoryImpl(resources).loadFont("FONT6.FNT").getOrThrow()

        PlayField(
            background = cps.loadCps("PLAYFLD.CPS").getOrThrow(),
            decorations = cps.loadCps("DECORATE.CPS").getOrThrow(),
            palette = sublevel.palette,
            font = font,
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
            dialogue = when (reading) {
                null -> null

                is OnAParchment.Writing -> DialogueScene.layout(
                    frame = null,
                    portrait = null,
                    text = DialogueTextRepositoryImpl(resources)
                        .text(reading.page).getOrThrow().first,
                    buttonLabels = listOf(DialogueScene.OK),
                    font = font,
                    waitsToBeRead = true,
                    readOff = DialogueScene.ReadOff.APageOverTheView,
                )

                is OnAParchment.Map -> DialogueScene.aPicture(
                    frame = cps.loadCps("BORDER.CPS").getOrThrow(),
                    picture = DialogueScene.Picture(
                        cps = cps.loadCps(OnAParchment.Map.SHEET).getOrThrow(),
                        sourceLeft = reading.sourceLeft,
                        sourceTop = reading.sourceTop,
                        goes = DialogueScene.PictureFrame.SPEAKER,
                    ),
                )
            },
        ).toImage()
    }

    /**
     * The person waiting on level 1, standing in the view as they ask to come
     * along. They are not framed the way a speaker a script names is: they are
     * cut from the sheet the meetings share, at their own size, and stand on
     * the floor of the view.
     */
    @Test
    fun `level1 the person met in the crypt`() =
        checkGolden(
            "level1-met-in-the-crypt",
            dialogueOver(
                level = "LEVEL1.INF",
                x = 15,
                y = 11,
                direction = Direction.WEST,
                picture = NpcMeeting.SHEET,
                sourceTop = INSAL.standing.sourceTop,
                goes = INSAL.standing.inTheView(),
                textId = INSAL.asks.number,
                buttons = listOf(NpcMeeting.YES, NpcMeeting.NO),
            ),
        )

    /**
     * The party with the person met on level 1 in the last place, whose face
     * is not on the sheet the made ones come from.
     */
    @Test
    fun `party panel with somebody met in it`() =
        checkGolden(
            "party-panel-joined",
            partyOver(level = "LEVEL4.INF", x = 15, y = 11, joinedBy = INSAL.joiningAs),
        )

    /**
     * And a party of four he makes five of: the place he takes is the first
     * one free, which is the left of the bottom row rather than the right.
     */
    @Test
    fun `party panel with somebody met in the fifth place`() =
        checkGolden(
            "party-panel-joined-fifth",
            partyOver(
                level = "LEVEL4.INF",
                x = 15,
                y = 11,
                joinedBy = INSAL.joiningAs,
                joiningAt = 4,
            ),
        )

    /**
     * The three ways a champion is shown: on their feet, down under a grid,
     * and past raising with another picture in place of the face. The rest of
     * the box stays in all three, which is the half worth seeing.
     */
    @Test
    fun `a party with one down and one past raising`() =
        checkGolden(
            "party-panel-down",
            partyOver(
                level = "LEVEL4.INF",
                x = 15,
                y = 11,
                hurtTo = listOf(78, -3, 20, Champion.BEYOND_RAISING),
            ),
        )

    /**
     * A champion named to change places says so where their name goes, and the
     * strip flickers between the two. This is the half the word is on; the
     * other half is the ordinary panel.
     */
    @Test
    fun `a champion waiting to change places`() =
        checkGolden(
            "party-panel-swapping",
            partyOver(level = "LEVEL4.INF", x = 15, y = 11, swapping = 2),
        )

    /**
     * A poisoned champion is written in red, which is the only sign of it on
     * the party's boxes: the same red the game gives all three of the troubles
     * it draws that way, and the reason the second and fourth names here look
     * unlike the first and third.
     */
    @Test
    fun `party panel with two of them poisoned`() =
        checkGolden(
            "party-panel-poisoned",
            partyOver(level = "LEVEL4.INF", x = 15, y = 11, poisoned = listOf(1, 3)),
        )

    /**
     * A fireball going off two squares up the corridor, frozen at three
     * moments of its burning.
     *
     * The sparks are thrown by dice, so the dice here are fixed rather than
     * rolled: a burst rendered from a clock would be a different picture every
     * run and could never be compared with anything.
     */
    private fun aBurst(
        afterTurns: Int,
        burning: List<Int> = Burst.LIKE_FIRE,
    ): ViewPort = runBlocking {
        val repository = repository()
        val inf = repository.loadLevel("LEVEL2.INF").getOrThrow()
        val sublevel = inf.subLevels[inf.subLevelAt(0, 3, 12, Direction.NORTH)]

        var burst = Burst.of(Location(3, 10), seededDice(), burning = burning)
        repeat(afterTurns) { burst = burst.onward(steps = 1) }

        repository.renderPosition(
            items = dungeonItems,
            monsters = emptyList(),
            sublevel = sublevel,
            playerX = 3,
            playerY = 12,
            direction = Direction.NORTH,
            bursting = listOf(burst),
        ).getOrThrow()
    }

    /**
     * Dice that roll differently every time but the same on every run.
     *
     * A burst is thirty-five sparks thrown at random, so real dice would draw
     * a different picture each run and there would be nothing to compare; dice
     * that always land the same would throw all thirty-five to one pixel and
     * there would be nothing to look at. These are neither.
     */
    /** So the goldens sort into the order the animation runs in. */
    private fun Int.twoDigits() = toString().padStart(2, '0')

    private fun seededDice(): Dice {
        var seed = 1L
        return Dice { times, pips, modifier ->
            var total = modifier
            repeat(times) {
                seed = (seed * 6364136223846793005L + 1442695040888963407L)
                total += ((seed ushr 33) % pips).toInt() + 1
            }
            total
        }
    }

    /**
     * And one that went off on the party themselves, which is a different
     * throw: half again as many sparks, three times as hard, and much less of
     * it upward — so it sprays across the view rather than arcing down it.
     */
    private fun aBurstOnTheParty(afterTurns: Int): ViewPort = runBlocking {
        val repository = repository()
        val inf = repository.loadLevel("LEVEL2.INF").getOrThrow()
        val sublevel = inf.subLevels[inf.subLevelAt(0, 3, 12, Direction.NORTH)]

        var burst = Burst.of(Location(3, 12), seededDice(), inYourFace = true)
        repeat(afterTurns) { burst = burst.onward(steps = 1) }

        repository.renderPosition(
            items = dungeonItems,
            monsters = emptyList(),
            sublevel = sublevel,
            playerX = 3,
            playerY = 12,
            direction = Direction.NORTH,
            bursting = listOf(burst),
        ).getOrThrow()
    }

    /**
     * A fireball going off down the corridor, every step of it.
     *
     * Sampling a burst at three moments says the three moments are right and
     * nothing about the arc between them, which is the whole of what a burst
     * is: thirty-five sparks thrown out and up, slowing, falling back
     * steeper, bouncing off the floor at half the speed, and going out
     * through thirteen colours as they go. A frame wrong in the middle of
     * that is invisible to any test that only looks at the ends.
     *
     * It goes out after fifty-eight steps and the last is drawn dark, which
     * is why the range runs one past it.
     */
    @Test
    fun `a fireball down the corridor, every step`() {
        (0..BURNS_FOR).forEach { step ->
            checkGolden("burst-step-${step.twoDigits()}", aBurst(step))
        }
    }

    /**
     * And one going off on the party themselves, which is thrown differently:
     * half again as many sparks, three times as hard, and much less of it
     * upward.
     */
    @Test
    fun `a fireball in your face, every step`() {
        (0..BURNS_IN_YOUR_FACE_FOR).forEach { step ->
            checkGolden("burst-near-step-${step.twoDigits()}", aBurstOnTheParty(step))
        }
    }

    /**
     * A bolt of lightning going off, which looks nothing like a fireball.
     *
     * The colours a burst runs through are a table, and there are three of
     * them: lightning and ice take the one that goes white, blue and pale
     * rather than red and orange, and it is three colours shorter — so this
     * burst is not only another colour but a shorter one, out in
     * forty-four steps against the fireball's fifty-eight.
     */
    @Test
    fun `lightning going off, every step`() {
        (0..LIGHTNING_BURNS_FOR).forEach { step ->
            checkGolden(
                "burst-lightning-step-${step.twoDigits()}",
                aBurst(step, Burst.LIKE_LIGHTNING),
            )
        }
    }

    /** And the third of them, dimmer than either and what a magic missile leaves. */
    @Test
    fun `a magic missile going off, every step`() {
        (0..A_MISSILE_BURNS_FOR).forEach { step ->
            checkGolden(
                "burst-missile-step-${step.twoDigits()}",
                aBurst(step, Burst.LIKE_A_MISSILE),
            )
        }
    }

    /**
     * A trap's bolt coming up the corridor, one square away and three.
     *
     * It is drawn down the middle of the square at the height of the party's
     * eyes rather than stood on the floor, and only its size says how far off
     * it is — so the pair of these is what shows it approaching.
     */
    @Test
    fun `a bolt in the air one square off`() =
        checkGolden("bolt-one-square-off", aBoltInFlight("LEVEL2.INF", 3, 11, Direction.NORTH, 1))

    /**
     * And one crossing the view rather than coming down it. There is one
     * picture of a burst and it is drawn the same way round whichever way it
     * is going, so what says it is passing is where it is, not how it looks.
     */
    @Test
    fun `a bolt crossing the view from the side`() =
        checkGolden(
            "bolt-from-the-side",
            aBoltInFlight(
                "LEVEL2.INF", 3, 12, Direction.NORTH,
                boltAt = Location(4, 10),
                going = Direction.WEST,
            ),
        )

    @Test
    fun `a bolt in the air three squares off`() =
        checkGolden("bolt-three-squares-off", aBoltInFlight("LEVEL2.INF", 3, 11, Direction.NORTH, 3))

    /**
     * A bolt and a monster on the same square, which is the only picture that
     * says which of them is in front.
     *
     * The game draws a row in a fixed order — what lies on the floor, the
     * door, the monsters, then whatever is flying, then the teleporter — so a
     * bolt about to strike something is drawn over it and reads as about to
     * strike it. Drawn the other way round it reads as already past, or as
     * not there at all where the monster fills the square.
     *
     * Nothing else here has both on one square, so nothing else could catch
     * that order going wrong.
     */
    @Test
    fun `a bolt passes in front of what it is about to hit`() =
        checkGolden(
            "bolt-over-a-monster",
            aBoltInFlight(
                "LEVEL2.INF", 3, 11, Direction.NORTH,
                squaresOff = 1,
                looksLike = ConjuredBolt.LIKE_ICE,
                standing = listOf(aMonsterOn(Location(3, 10))),
            ),
        )

    /** One of the second floor's own, stood where the test wants it. */
    private fun aMonsterOn(where: Location) = MonsterInstance(
        index = MonsterSlot(0),
        unit = 0,
        location = where,
        place = SquarePlace.MIDDLE,
        direction = Direction.SOUTH,
        type = MonsterTypeId(0),
        gfxIndex = 0,
        mode = 0,
        pause = 0,
        weapon = 0,
        pocketItem = 0,
    )

    /**
     * The other three conjured bolts coming down the same corridor.
     *
     * Four of them sit in a column of their own on the sheet everything in
     * flight is cut from, and which spell takes which is a table in the game.
     * Drawing one for another is the kind of mistake nothing but a picture
     * catches: a bolt of lightning is not a fireball, and the ice storm is
     * the fireball's shape in the colours of the lightning.
     */
    @Test
    fun `each of the conjured bolts coming down the corridor`() {
        ConjuredBolt.entries.filter { it != ConjuredBolt.LIKE_FIRE }.forEach { bolt ->
            val called = bolt.name.removePrefix("LIKE_").removePrefix("A_").lowercase()

            (3 downTo 0).forEach { squaresOff ->
                checkGolden(
                    "bolt-$called-$squaresOff",
                    aBoltInFlight(
                        "LEVEL2.INF", 3, 11, Direction.NORTH,
                        squaresOff = squaresOff,
                        looksLike = bolt,
                    ),
                )
            }
        }
    }

    /**
     * A bolt coming the length of the corridor, every step of the way.
     *
     * It crosses a square in two: in at the near end and on to the far one,
     * and the two are drawn at different sizes because how big it is is the
     * only thing that says how far off it is. Three squares of corridor and
     * the party's own square make eight positions, and a bolt that grows
     * wrongly between any two of them is a bolt that reads as jumping.
     *
     * This is the shot a monster takes, drawn from the far end of what it can
     * reach — three squares — down onto the party.
     */
    @Test
    fun `a bolt down the whole corridor, every step`() {
        (3 downTo 0).forEach { squaresOff ->
            // the far end of the square is the end it leaves by, and going
            // south that is the near side of it as the party see it
            listOf(SquarePlace.NORTH_WEST to "far", SquarePlace.SOUTH_WEST to "near")
                .forEach { (end, which) ->
                    checkGolden(
                        "bolt-corridor-$squaresOff-$which",
                        aBoltInFlight(
                            "LEVEL2.INF", 3, 11, Direction.NORTH,
                            squaresOff = squaresOff,
                            over = end,
                        ),
                    )
                }
        }
    }

    /**
     * A beholder's ray crossing an empty square to reach the party.
     *
     * The scene is the one that is hard to read while playing: something too
     * big for a corner two squares off, one empty square between, and the ray
     * coming down it. Every step of the crossing is frozen — both ends of the
     * beholder's own square, both ends of the empty one, and the party's.
     *
     * What the pictures are for is the size. A row has one size and the whole
     * of a square is drawn at it, so the ray is seen small over the beholder,
     * a size larger over the gap, and larger again on the party's own square.
     *
     * One frame a square is enough, and that is the point of it: a ray is
     * drawn down the middle whatever quarter it is really on, so the two ends
     * of a square are the same picture and freezing both would prove nothing.
     * The last of the three never happens in play either — a ray aimed at the
     * party hits on arrival and is gone before anything draws it — so it is
     * here as the size that is missing rather than as a thing anybody sees.
     */
    @Test
    fun `a beholder's ray coming across an empty square`() {
        (2 downTo 0).forEach { squaresOff ->
            checkGolden(
                "ray-across-a-gap-$squaresOff",
                aBoltInFlight(
                    "LEVEL2.INF", 3, 11, Direction.NORTH,
                    squaresOff = squaresOff,
                    looksLike = ConjuredBolt.LIKE_MOTES,
                    standing = listOf(aMonsterOn(Location(3, 9))),
                ),
            )
        }
    }

    /**
     * And the one spell that is not drawn down the middle, at both ends of a
     * square, which is where the difference shows.
     *
     * Two of the fourteen keep the quarter they are really crossing — the two
     * drawn as a line rather than as a ball — and a lightning bolt is the one
     * a monster ever fires. These two frames are the only proof that the
     * centring is applied to some and not all: everything else in flight
     * renders the same at both ends of a square, so nothing else can tell a
     * rule that centres selectively from one that centres everything.
     */
    @Test
    fun `a lightning bolt keeps the side of the square it is crossing`() {
        listOf(SquarePlace.NORTH_WEST to "near", SquarePlace.SOUTH_WEST to "far")
            .forEach { (end, which) ->
                checkGolden(
                    "bolt-lightning-side-$which",
                    aBoltInFlight(
                        "LEVEL2.INF", 3, 11, Direction.NORTH,
                        squaresOff = 1,
                        over = end,
                        spell = MonsterSpell.LIGHTNING_BOLT,
                    ),
                )
            }
    }

    /**
     * The sparks a casting throws about the room, at four points of their life.
     *
     * Sixteen places, fixed in the view rather than anywhere in the corridor,
     * each showing one of three pictures or none. They light in a spreading
     * wave and go out the same way, so the four frames here are a beginning,
     * two middles and an end — one frame alone would say nothing about whether
     * the wave moves.
     */
    @Test
    fun `the sparks of a casting`() {
        listOf(0, 8, 20, 36).forEach { frame ->
            checkGolden(
                "sparks-$frame",
                sparkling("LEVEL2.INF", 3, 11, Direction.NORTH, frame),
            )
        }
    }

    private fun sparkling(
        level: String,
        x: Int,
        y: Int,
        direction: Direction,
        frame: Int,
    ): ViewPort = runBlocking {
        val repository = repository()
        val inf = repository.loadLevel(level).getOrThrow()

        repository.renderPosition(
            items = dungeonItems,
            monsters = emptyList(),
            sublevel = inf.subLevels[inf.subLevelAt(0, x, y, direction)],
            playerX = x,
            playerY = y,
            direction = direction,
            sparkling = SparksInTheRoom(frame),
        ).getOrThrow()
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
        waitsToBeRead: Boolean = false,
        readOff: DialogueScene.ReadOff.Written = DialogueScene.ReadOff.TheStripBelow,
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
            direction = direction,
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
                waitsToBeRead = waitsToBeRead,
                readOff = readOff,
            ),
        ).toImage()
    }

    /** A map held up: the sheet's own corner, in the frame, with nothing written. */
    private fun mapOver(level: String, x: Int, y: Int, map: OnAParchment.Map): BufferedImage =
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
                dialogue = DialogueScene.aPicture(
                    frame = cps.loadCps("BORDER.CPS").getOrThrow(),
                    picture = DialogueScene.Picture(
                        cps = cps.loadCps(OnAParchment.Map.SHEET).getOrThrow(),
                        sourceLeft = map.sourceLeft,
                        sourceTop = map.sourceTop,
                        goes = DialogueScene.PictureFrame.SPEAKER,
                    ),
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

    /**
     * A bolt in the air on the square [squaresOff] ahead of the party, which
     * is the only thing this draws that is not standing on a floor.
     */
    private fun aBoltInFlight(
        level: String,
        x: Int,
        y: Int,
        direction: Direction,
        squaresOff: Int = 0,
        boltAt: Location? = null,
        going: Direction? = null,
        over: SquarePlace = SquarePlace.MIDDLE,
        looksLike: ConjuredBolt = ConjuredBolt.LIKE_FIRE,
        standing: List<MonsterInstance> = emptyList(),
        // Given only where the scene is about a spell being one rather than
        // about the picture: it is what says whether the thing is drawn down
        // the middle or over the quarter it is on.
        spell: MonsterSpell? = null,
    ): ViewPort = runBlocking {
        val repository = repository()
        val inf = repository.loadLevel(level).getOrThrow()
        val sublevel = inf.subLevels[inf.subLevelAt(0, x, y, direction)]

        val at = boltAt ?: run {
            var walked = Location(x, y)
            repeat(squaresOff) { walked = direction.oneStepFrom(walked) }
            walked
        }

        repository.renderPosition(
            items = dungeonItems,
            monsters = standing,
            sublevel = sublevel,
            playerX = x,
            playerY = y,
            direction = direction,
            inFlight = listOf(
                Projectile(
                    what = null,
                    at = at,
                    place = over,
                    going = going
                        ?: Direction.entries[(direction.ordinal + 2) % Direction.entries.size],
                    thrownBy = Projectile.Thrower.TheLevel,
                    spell = spell,
                    looksLike = spell?.looksLike ?: looksLike,
                ),
            ),
        ).getOrThrow()
    }

    /** What the party entering a sublevel does to the monsters the file lists. */
    private fun List<MonsterInstance>.arrivingIn(subLevel: Int) =
        map { it.copy(subLevel = subLevel) }

    /** @param monsters the level's own, put into whatever state is being shown. */
    private fun renderFrame(
        level: String,
        x: Int,
        y: Int,
        direction: Direction,
        monsters: (List<MonsterInstance>) -> List<MonsterInstance>,
    ): ViewPort = runBlocking {
        val repository = repository()
        val inf = repository.loadLevel(level).getOrThrow()
        val sublevel = inf.subLevels[inf.subLevelAt(0, x, y, direction)]

        repository.renderPosition(
            items = dungeonItems,
            monsters = monsters(inf.monsterInstances.arrivingIn(sublevel.index)),
            sublevel = sublevel,
            playerX = x,
            playerY = y,
            direction = direction,
        ).getOrThrow()
    }

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

        /**
         * How many steps a burst lasts before every spark of it is dark.
         *
         * Measured rather than transcribed: the sparks fade at a rate rolled
         * between two bounds, so how long one lives depends on the dice, and
         * the seeded dice these goldens use always give this.
         */
        private const val BURNS_FOR = 58
        private const val BURNS_IN_YOUR_FACE_FOR = 60

        /** A shorter table is a shorter burst: these go out sooner. */
        private const val LIGHTNING_BURNS_FOR = 44
        private const val A_MISSILE_BURNS_FOR = 49

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
