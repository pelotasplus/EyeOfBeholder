package pl.pelotasplus.eyeofbeholder.data.repository

import co.touchlab.kermit.Logger
import pl.pelotasplus.eyeofbeholder.data.model.Cps
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Inf
import pl.pelotasplus.eyeofbeholder.data.model.Item
import pl.pelotasplus.eyeofbeholder.data.model.ItemIconId
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.Maz
import pl.pelotasplus.eyeofbeholder.data.model.WallSide
import pl.pelotasplus.eyeofbeholder.data.model.DistanceFromParty
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.MonsterSheet
import pl.pelotasplus.eyeofbeholder.data.model.Door
import pl.pelotasplus.eyeofbeholder.data.model.DoorIndex
import pl.pelotasplus.eyeofbeholder.data.model.SubLevel
import pl.pelotasplus.eyeofbeholder.data.model.TeleporterPulse
import pl.pelotasplus.eyeofbeholder.data.model.ViewBlock
import pl.pelotasplus.eyeofbeholder.data.model.ViewPort
import pl.pelotasplus.eyeofbeholder.data.model.ViewWindow
import pl.pelotasplus.eyeofbeholder.data.model.WallSet
import pl.pelotasplus.eyeofbeholder.data.model.getWall
import pl.pelotasplus.eyeofbeholder.data.model.ItemIndex
import pl.pelotasplus.eyeofbeholder.data.model.SquarePlace
import pl.pelotasplus.eyeofbeholder.data.model.itemScaleStepsAt
import pl.pelotasplus.eyeofbeholder.data.model.nudgeOf
import pl.pelotasplus.eyeofbeholder.data.model.showsWhatIsOnIt
import pl.pelotasplus.eyeofbeholder.data.model.sightThrough
import pl.pelotasplus.eyeofbeholder.data.model.teleportersInView
import pl.pelotasplus.eyeofbeholder.data.model.viewBlockRows
import pl.pelotasplus.eyeofbeholder.data.model.viewWindow
import pl.pelotasplus.eyeofbeholder.data.model.visibleBlocks
import pl.pelotasplus.eyeofbeholder.data.model.monsterFacing
import pl.pelotasplus.eyeofbeholder.data.model.monsterSheet
import pl.pelotasplus.eyeofbeholder.data.model.WallAction
import pl.pelotasplus.eyeofbeholder.data.model.doesWhenClicked
import pl.pelotasplus.eyeofbeholder.data.model.viewSlots

/**
 * Orchestrates level loading and 3D viewport rendering.
 *
 * This is the highest-level repository, combining data from all other
 * repositories to produce the final rendered viewport image.
 *
 * ## Level loading flow
 * [loadLevel] delegates to [InfRepository], which cascades to MAZ, VMP, VCN,
 * PAL, DEC, CPS repositories.
 *
 * ## Rendering flow ([renderPosition])
 * Given a player position (x, y), facing direction, and sublevel:
 * 1. Draw the backdrop (floor/ceiling) from VMP+VCN
 * 2. For each of 25 wall positions (back-to-front):
 *    a. Transform relative coordinates by player direction
 *    b. Look up the maze square and wall type
 *    c. Draw wall/door/stairs/decoration as appropriate
 *    d. Draw any items at that location
 * 3. Return the completed ViewPort pixel buffer
 *
 * ## Wall type dispatch
 * - NoWall → skip (open passage)
 * - FixedWall → draw VCN wall tiles
 * - Door → draw door frame + CPS door panel (± button)
 * - StairUp/Down → draw stair tiles
 * - Decoration → look up decoration, optionally draw base wall, then overlay
 *   (specialType 5 = stuck door gets special treatment)
 */
interface ViewConeRepository {
    suspend fun loadLevel(name: String): Result<Inf>

    /**
     * @param wallAt what a square's side is now, which is not what the file
     *   says once a script has changed it. Defaults to the file.
     * @param pulse which half of their flicker any teleporters in view show
     * @param fromTheBottomUp which of two things in one corner is under the
     *   other, drawn in that order so the top of a pile is the one on screen.
     *   Defaults to the order of the table, which is how a file lays a level
     *   out before anything has been moved.
     */
    suspend fun renderPosition(
        items: List<Item>,
        monsters: List<MonsterInstance>,
        sublevel: SubLevel,
        playerX: Int,
        playerY: Int,
        direction: Direction,
        wallAt: (Location, WallSide) -> Maz.WallType = { at, side ->
            sublevel.maz.square(at).getWall(side)
        },
        pulse: TeleporterPulse = TeleporterPulse.AS_LAID_OUT,
        fromTheBottomUp: List<ItemIndex> = items.indices.map(::ItemIndex),
    ): Result<ViewPort>
}

class ViewConeRepositoryImpl(
    private val infRepository: InfRepository,
    private val cpsRepository: CpsRepository,
    private val dcrRepository: DcrRepository,
) : ViewConeRepository {

    private var smallItemIcons: Cps? = null
    private var largeItemIcons: Cps? = null
    private var decorationShapes: Cps? = null
    private val monsterSheetCache = mutableMapOf<String, MonsterSheet>()

    private suspend fun getSmallItemIcons(): Cps {
        return smallItemIcons ?: cpsRepository.loadCps("ITEMS1.CPS").getOrThrow().also {
            smallItemIcons = it
        }
    }

    private suspend fun getLargeItemIcons(): Cps {
        return largeItemIcons ?: cpsRepository.loadCps("ITEML1.CPS").getOrThrow().also {
            largeItemIcons = it
        }
    }

    /** The sheet the interface art is cut from, which also holds the teleporter blobs. */
    private suspend fun getDecorations(): Cps {
        return decorationShapes ?: cpsRepository.loadCps("DECORATE.CPS").getOrThrow().also {
            decorationShapes = it
        }
    }

    override suspend fun renderPosition(
        items: List<Item>,
        monsters: List<MonsterInstance>,
        sublevel: SubLevel,
        playerX: Int,
        playerY: Int,
        direction: Direction,
        wallAt: (Location, WallSide) -> Maz.WallType,
        pulse: TeleporterPulse,
        fromTheBottomUp: List<ItemIndex>,
    ): Result<ViewPort> {
        Logger.d(TAG) { "Render position $playerX x $playerY level ${sublevel.level}"}

        val viewPort = ViewPort(
            vmp = sublevel.vmp,
            vcn = sublevel.vcn,
            palette = sublevel.palette
        )
        viewPort.drawBackdrop()

        val smallIcons = getSmallItemIcons()
        val largeIcons = getLargeItemIcons()
        val monsterSheets = loadMonsterSheets(sublevel)
        val teleporters = teleportersInView(Location(playerX, playerY), direction, wallAt)
        val decorations = if (teleporters.isEmpty()) null else getDecorations()
        val windows = viewWindows(sublevel, playerX, playerY, direction, wallAt)

        // Data-driven wall rendering using the viewSlots table
        viewSlots.forEachIndexed { wallPosition, slot ->
            // Items and monsters of a depth row draw after that row's walls
            // and before the next (nearer) row's walls, so closer walls
            // occlude them. Items draw first so monsters stand in front, and
            // a teleporter's sparks hang in front of both.
            when (wallPosition) {
                11, 18, 23 -> {
                    val relY = if (wallPosition == 11) -3 else if (wallPosition == 18) -2 else -1
                    drawItemsAtRow(relY, viewPort, items, fromTheBottomUp, smallIcons, largeIcons, sublevel, playerX, playerY, direction, windows, wallAt)
                    drawMonstersAtRow(relY, viewPort, monsters, monsterSheets, sublevel, playerX, playerY, direction, windows)
                    drawTeleportersAtRow(relY, viewPort, teleporters, decorations, pulse, windows)
                }
            }
            // Transform coordinates based on player direction
            val (dx, dy) = direction.transformCoordinates(
                slot.relativeX,
                slot.relativeY
            )

            // Calculate actual maze position
            val mazX = playerX + dx
            val mazY = playerY + dy

            // Transform wall side based on player direction
            val actualWallSide = direction.transformWallSide(slot.wallSide)
            val wallType = wallAt(Location(mazX, mazY), actualWallSide)

            // A slot's SOUTH wall is the far face of its square; the others are
            // the faces turned towards the party.
            val distance = if (slot.wallSide == WallSide.SOUTH) {
                DistanceFromParty.farSideOfSquare(slot.relativeX, slot.relativeY)
            } else {
                DistanceFromParty.nearSideOfSquare(slot.relativeX, slot.relativeY)
            }

            viewPort.at(distance) {
                when (wallType) {
                    is Maz.WallType.Decoration -> {
                        val levelDecoration = sublevel.decorations
                            .find { it.decorationWallIndex == wallType.decorationWallIndex }
//                        Logger.d(TAG) { "Wall wallPosition $wallPosition matching decoration $levelDecoration" }
                        // A level names a wall set only for the indices it uses,
                        // and one it says nothing about is drawn as nothing —
                        // which is a wall all the same, and one the party
                        // cannot walk through. Level 5 has four faces like this
                        // and they are ordinary parts of the level, not a hole
                        // in what was read.
                        if (levelDecoration == null) return@at

                        if (levelDecoration.doesWhenClicked == WallAction.STUCK_DOOR) {
                            sublevel.door(DoorIndex(0), mazX, mazY)?.let { door ->
                                viewPort.drawDoor(
                                    wallPosition = wallPosition,
                                    door = door,
                                    secondLayer = sublevel.doors.getOrNull(1),
                                    showButton = false,
                                    stuckDoor = true,
                                )
                            } ?: viewPort.drawUndrawableWall(wallPosition)
                        } else if ((levelDecoration.wallType - 1) >= 0) {
                            viewPort.drawWall(levelDecoration.wallType - 1, wallPosition)
                        }

                        // The wall a decoration is on is drawn whole, the way
                        // every wall is; the decoration on it is a shape and is
                        // cut to what the walls in front leave of its square —
                        // to nothing at all where they leave nothing. A face
                        // the party see side-on is cut by its own square too.
                        viewPort.at(distance, within = windows[slot.block]) {
                            viewPort.drawDecoration(
                                decoration = levelDecoration,
                                wallPosition = wallPosition,
                            )
                        }
                    }

                    is Maz.WallType.Door -> {
                        sublevel.door(wallType.doorIndex, mazX, mazY)?.let { door ->
                            viewPort.drawDoor(
                                wallPosition = wallPosition,
                                door = door,
                                secondLayer = sublevel.doors.getOrNull(wallType.doorIndex.value + 1),
                                showButton = wallType.hasButton,
                                opened = wallType.state,
                            )
                        } ?: viewPort.drawUndrawableWall(wallPosition)
                    }

                    is Maz.WallType.FixedWall -> {
                        viewPort.drawWall(wallType.wallType, wallPosition)
                    }

                    Maz.WallType.NoWall -> {
                        // no-wall to render
                    }

                    Maz.WallType.StairDown -> {
                        viewPort.drawWall(WallSet.STAIRS_DOWN, wallPosition)
                    }

                    Maz.WallType.StairUp -> {
                        viewPort.drawWall(WallSet.STAIRS_UP, wallPosition)
                    }
                }
            }
        }

        // Items on the party's own square (visible block 16, dim 3): only the
        // two quadrants ahead of the party are visible; rear quadrants are
        // behind the camera and niche items beside it are never drawn.
        drawItemsAtBlock(
            viewPort, items, fromTheBottomUp, smallIcons, largeIcons, sublevel,
            mazX = playerX, mazY = playerY,
            blockIndex = ViewPort.OWN_BLOCK_INDEX, dim = 3,
            partyFacing = direction,
        )

        return Result.success(viewPort)
    }

    override suspend fun loadLevel(
        name: String,
    ): Result<Inf> = infRepository.loadInf(name.replace(".MAZ", ".INF"))

    /** Loads and caches the near-size poses for each of the sublevel's sprite sheets. */
    private suspend fun loadMonsterSheets(sublevel: SubLevel): List<MonsterSheet> {
        return sublevel.monsterGfx.map { gfx ->
            val baseName = gfx.name.filter { it.code in 33..126 }.uppercase()
            monsterSheetCache.getOrPut(baseName) {
                val dcr = if (gfx.hasDecorations) {
                    dcrRepository.loadDcr("$baseName.DCR")
                        .onFailure { Logger.e(TAG) { "Failed to load overlays for $baseName: $it" } }
                        .getOrNull()
                } else {
                    null
                }

                cpsRepository.loadCps("$baseName.CPS")
                    .map { cps -> cps.monsterSheet(gfx, dcr) }
                    .onFailure { Logger.e(TAG) { "Failed to load monster sheet $baseName: $it" } }
                    .getOrDefault(MonsterSheet.EMPTY)
            }
        }
    }

    /**
     * How much of each of the 18 squares in the view is left once the walls
     * have covered what they cover, worked out before anything is drawn.
     *
     * Only the face a square turns towards the party takes part: a wall the
     * party sees side-on stands along their line of sight rather than across
     * it, and hides nothing that the face at the end of it does not.
     */
    private fun viewWindows(
        sublevel: SubLevel,
        playerX: Int,
        playerY: Int,
        direction: Direction,
        wallAt: (Location, WallSide) -> Maz.WallType,
    ): List<ViewWindow> {
        val facingUs = direction.transformWallSide(WallSide.SOUTH)

        val sight = visibleBlocks.map { block ->
            val (dx, dy) = direction.transformCoordinates(block.relativeX, block.relativeY)
            sublevel.sightThrough(wallAt(Location(playerX + dx, playerY + dy), facingUs))
        }

        return visibleBlocks.indices.map { block -> viewWindow(block) { sight[it] } }
    }

    private fun drawTeleportersAtRow(
        relativeY: Int,
        viewPort: ViewPort,
        teleporters: List<ViewBlock>,
        decorations: Cps?,
        pulse: TeleporterPulse,
        windows: List<ViewWindow>,
    ) {
        if (decorations == null) return

        val dim = when (relativeY) {
            -3 -> 0
            -2 -> 1
            else -> 2
        }

        for (block in teleporters.filter { it.relativeY == relativeY }) {
            val window = windows[block.blockIndex]
            if (window.closed) continue

            viewPort.at(
                DistanceFromParty.standingOnSquare(block.relativeX, block.relativeY),
                hiddenByCloserThings = true,
                within = window,
            ) {
                viewPort.drawTeleporter(decorations, block.blockIndex, dim, pulse)
            }
        }
    }

    private fun drawItemsAtRow(
        relativeY: Int,
        viewPort: ViewPort,
        items: List<Item>,
        fromTheBottomUp: List<ItemIndex>,
        smallIcons: Cps,
        largeIcons: Cps,
        sublevel: SubLevel,
        playerX: Int,
        playerY: Int,
        direction: Direction,
        windows: List<ViewWindow>,
        wallAt: (Location, WallSide) -> Maz.WallType,
    ) {
        val dim = when (relativeY) {
            -3 -> 0
            -2 -> 1
            else -> 2
        }

        val facingUs = direction.transformWallSide(WallSide.SOUTH)

        for (block in viewBlockRows.getValue(relativeY)) {
            val window = windows[block.blockIndex]
            if (window.closed) continue

            val (dx, dy) = direction.transformCoordinates(block.relativeX, block.relativeY)

            // What a square holds is behind the wall it turns towards the
            // party, and stays there unless that wall is one that shows it: an
            // alcove open to the room, a doorway, or no wall at all. A shelf
            // that locks keeps its scrolls until something unlocks it.
            val face = wallAt(Location(playerX + dx, playerY + dy), facingUs)
            if (!sublevel.showsWhatIsOnIt(face)) continue

            viewPort.at(
                DistanceFromParty.standingOnSquare(block.relativeX, block.relativeY),
                hiddenByCloserThings = true,
                within = window,
            ) {
                drawItemsAtBlock(
                    viewPort, items, fromTheBottomUp, smallIcons, largeIcons, sublevel,
                    mazX = playerX + dx, mazY = playerY + dy,
                    blockIndex = block.blockIndex, dim = dim,
                    partyFacing = direction,
                )
            }
        }
    }

    private fun drawItemsAtBlock(
        viewPort: ViewPort,
        items: List<Item>,
        fromTheBottomUp: List<ItemIndex>,
        smallIcons: Cps,
        largeIcons: Cps,
        sublevel: SubLevel,
        mazX: Int,
        mazY: Int,
        blockIndex: Int,
        dim: Int,
        partyFacing: Direction,
    ) {
        // the slot is the item's place in the world's table, which is what its
        // nudge is taken from, so it is carried along with it
        val itemsHere = fromTheBottomUp.mapNotNull { slot ->
            items.getOrNull(slot.value)
                ?.takeIf {
                    it.level == sublevel.level &&
                        it.location.x == mazX &&
                        it.location.y == mazY
                }
                ?.let { slot to it }
        }

        for ((slot, item) in itemsHere) {
            Logger.d(TAG) { "drawItem icon=${item.icon} at ($mazX, $mazY) ${item.place} block=$blockIndex" }

            val nudge = nudgeOf(slot)

            when {
                item.place == SquarePlace.IN_A_NICHE -> {
                    // niche items are hidden when too far (dim 0) or on the own square (dim 3)
                    if (dim == 1 || dim == 2) {
                        sheetFor(item.icon, smallIcons, largeIcons)?.let { sheet ->
                            viewPort.drawNicheItem(sheet, item.icon, blockIndex, dim, nudge)
                        }
                    }
                }

                item.place.onTheFloor -> {
                    val seenAt = item.place.asSeenFacing(partyFacing) ?: continue
                    val scaleSteps = itemScaleStepsAt(dim, seenAt)
                    if (scaleSteps.isVisible) {
                        sheetFor(item.icon, smallIcons, largeIcons)?.let { sheet ->
                            viewPort.drawFloorItem(
                                largeIcons = sheet,
                                iconIdx = item.icon,
                                blockIndex = blockIndex,
                                place = seenAt,
                                scaleSteps = scaleSteps,
                                nudge = nudge,
                            )
                        }
                    }
                }

                // in the middle of a square is where a thing in the air is,
                // and what is in the air is not drawn lying on the floor
                else -> Logger.d(TAG) { "Not on the floor: ${item.place}, skipping" }
            }
        }
    }

    /**
     * Which sprite sheet an icon's shape lives in. The shape map decides
     * whether an icon is a small or a large item, and the two sizes are packed
     * into different files — cutting a small shape out of the large sheet
     * yields whatever else happens to sit at those coordinates.
     */
    private fun sheetFor(icon: ItemIconId, small: Cps, large: Cps): Cps? =
        when (small.locate(icon)) {
            is Cps.ShapeLocation.SmallItem -> small
            is Cps.ShapeLocation.LargeItem -> large
            Cps.ShapeLocation.NoShape -> null
        }

    private fun drawMonstersAtRow(
        relativeY: Int,
        viewPort: ViewPort,
        monsters: List<MonsterInstance>,
        monsterSheets: List<MonsterSheet>,
        sublevel: SubLevel,
        playerX: Int,
        playerY: Int,
        direction: Direction,
        windows: List<ViewWindow>,
    ) {
        for (block in viewBlockRows.getValue(relativeY)) {
            val window = windows[block.blockIndex]
            if (window.closed) continue

            val (dx, dy) = direction.transformCoordinates(block.relativeX, block.relativeY)
            val mazX = playerX + dx
            val mazY = playerY + dy

            // A monster of another sublevel is not somewhere else, it is
            // nowhere: its type and graphic index mean whatever that sublevel's
            // tables say, and this one's would make it a different creature.
            val monstersHere = monsters
                .filter { it.x == mazX && it.y == mazY && it.subLevel == sublevel.index }
                .sortedBy { it.place.asSeenFacing(direction) }

            for (monster in monstersHere) {
                val seenAt = monster.place.asSeenFacing(direction) ?: continue
                val sheet = monsterSheets.getOrNull(monster.gfxIndex) ?: continue
                val facing = monsterFacing(direction, monster.direction)
                val frame = sheet.pose(facing.pose, monster.colors) ?: continue

                // the overlays go with the species, not with the instance
                val decorations = sublevel.monsters
                    .firstOrNull { it.id == monster.type.value }
                    ?.decorations.orEmpty()
                    .mapNotNull { sheet.decoration(it, facing.pose) }

                viewPort.at(
                    DistanceFromParty.standingOnSquare(block.relativeX, block.relativeY),
                    hiddenByCloserThings = true,
                    within = window,
                ) {
                    viewPort.drawMonster(
                        frame = frame,
                        decorations = decorations,
                        blockIndex = block.blockIndex,
                        place = seenAt,
                        mirrored = facing.mirrored,
                        scaleSteps = block.scaleSteps,
                    )
                }
            }
        }
    }

    /**
     * The door definition a square's wall asks for, or null where the sublevel
     * has none.
     *
     * A sublevel defines up to two doors, and a maze is a fixed 32x32 whose
     * unreachable parts keep whatever bytes were left in them — so a wall
     * asking for a door nobody defined is expected, as long as it stays out of
     * sight. Level 6 defines one door and its maze asks for two.
     */
    private fun SubLevel.door(index: DoorIndex, mazX: Int, mazY: Int): Door? =
        doors.getOrNull(index.value).also {
            if (it == null) {
                Logger.w(TAG) {
                    "Door ${index.value} at ($mazX,$mazY) is not defined by this level"
                }
            }
        }

    companion object {
        private const val TAG = "ViewConeRepository"
    }
}
