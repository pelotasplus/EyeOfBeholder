package pl.pelotasplus.eyeofbeholder.agent

import kotlinx.serialization.Serializable
import pl.pelotasplus.eyeofbeholder.data.model.CampMenu
import pl.pelotasplus.eyeofbeholder.data.model.CarrySlot
import pl.pelotasplus.eyeofbeholder.data.model.Champion
import pl.pelotasplus.eyeofbeholder.data.model.Direction
import pl.pelotasplus.eyeofbeholder.data.model.Discipline
import pl.pelotasplus.eyeofbeholder.data.model.GameState
import pl.pelotasplus.eyeofbeholder.data.model.ItemNames
import pl.pelotasplus.eyeofbeholder.data.model.ItemTypes
import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.Maz
import pl.pelotasplus.eyeofbeholder.data.model.MonsterInstance
import pl.pelotasplus.eyeofbeholder.data.model.SpellBook
import pl.pelotasplus.eyeofbeholder.data.model.SpellSlot
import pl.pelotasplus.eyeofbeholder.data.model.SubLevel
import pl.pelotasplus.eyeofbeholder.data.model.WallSet
import pl.pelotasplus.eyeofbeholder.data.model.WallSide
import pl.pelotasplus.eyeofbeholder.data.model.inSight
import pl.pelotasplus.eyeofbeholder.data.model.showsWhatIsOnIt
import pl.pelotasplus.eyeofbeholder.data.model.CampSpellMessages

/**
 * What an agent playing the game is told: what a player at the screen could
 * know, in words, and nothing they could not.
 *
 * Only what is in sight, as it looks. A wall is described by its picture and
 * not by what it does, so an illusory wall is a wall and a button is a
 * picture until somebody presses it; a monster that cannot be seen is not
 * here; a thing the party have not identified is called what it looks like.
 */
@Serializable
data class Observation(
    val level: Int,
    val at: Location,
    val facing: Direction,
    val inSight: List<SeenSquare>,
    val party: List<SeenChampion>,
    /** What is on the pointer, picked up and not yet put anywhere. */
    val holding: String?,
    val messages: List<String>,
    val menu: SeenMenu?,
    val book: SeenBook? = null,
)

/**
 * The book open over the compass: whose, which tab, the six lines as shown
 * (an empty one is a place still being learned or not chosen), which line is
 * picked, and whether it is resting after a casting.
 */
@Serializable
data class SeenBook(
    val champion: Int,
    val tab: Int,
    val lines: List<String?>,
    val picked: Int?,
    val resting: Boolean,
)

/**
 * One square in sight, [ahead] squares in front of the party and [across]
 * to the side (negative to the left). The party's own square is 0, 0.
 *
 * The faces are named as the party see them: [near] is the one turned
 * towards them, [left], [right] and [far] the others. A face with nothing on
 * it is left out.
 */
@Serializable
data class SeenSquare(
    val ahead: Int,
    val across: Int,
    val near: String? = null,
    val left: String? = null,
    val right: String? = null,
    val far: String? = null,
    /** Something painted on the floor of the square — a plate, a stain. */
    val floor: String? = null,
    val monsters: List<SeenMonster> = emptyList(),
    val items: List<SeenItem> = emptyList(),
)

@Serializable
data class SeenMonster(
    /** The name of the picture it is drawn with. */
    val looksLike: String,
    /** Which way it faces as the party see it: towards them, away, to the left or right. */
    val facing: String,
    /** Where on its square, as the party see it. */
    val standing: String,
)

@Serializable
data class SeenItem(val name: String, val lying: String)

@Serializable
data class SeenChampion(
    val slot: Int,
    val name: String,
    val hitPoints: String,
    val food: Int,
    val conditions: List<String>,
    val hands: List<String>,
    /** Ready to cast, by the name the book shows them under. */
    val spells: List<String>,
)

@Serializable
data class SeenMenu(val title: String, val says: List<String>, val choices: List<String>)

/** How far ahead the party can see, which is as far as the view draws. */
private const val SIGHT = 3

private const val QUARTER_RIGHT = 1
private const val HALF_TURN = 2

fun observe(
    world: GameState,
    level: Int,
    sublevel: SubLevel,
    names: ItemNames?,
    types: ItemTypes?,
    messages: List<String>,
    menu: CampMenu?,
    book: SpellBook? = null,
    bookResting: Boolean = false,
): Observation {
    val facing = world.party.facing
    fun squareAt(across: Int, ahead: Int): Location {
        val (dx, dy) = facing.transformCoordinates(across, -ahead)
        return Location(world.party.position.x + dx, world.party.position.y + dy)
    }
    fun face(at: Location, side: WallSide) = world.wall(level, at, facing.transformWallSide(side))

    val visibleMonsters = world.monsters.inSight(sublevel.monsters, world.seesTheHidden)
    fun contentsOf(at: Location) = Pair(
        visibleMonsters.filter { it.x == at.x && it.y == at.y }.map { it.seenFrom(facing, sublevel) },
        world.items
            .filter { it.level == level && it.location == at }
            .map { SeenItem(names?.of(it, types) ?: "something", it.place.asSeenFacing(facing)?.name?.lowercase() ?: "on the floor") },
    )

    val squares = mutableListOf<SeenSquare>()
    for (ahead in 0..SIGHT) {
        val here = squareAt(0, ahead)
        val near = face(here, WallSide.SOUTH)

        if (ahead > 0 && !sublevel.showsWhatIsOnIt(near)) {
            squares += SeenSquare(ahead, 0, near = describe(near, sublevel))
            break
        }

        // Every face is the one the square beyond it turns towards the party's
        // line of sight, which is the one drawn: the corridor's own side of a
        // wall can be bare where the square next door shows trees.
        val leftFace = face(squareAt(-1, ahead), WallSide.EAST)
        val rightFace = face(squareAt(1, ahead), WallSide.WEST)

        val (monsters, items) = contentsOf(here)
        squares += SeenSquare(
            ahead = ahead,
            across = 0,
            near = if (ahead > 0) describe(near, sublevel) else null,
            left = describe(leftFace, sublevel),
            right = describe(rightFace, sublevel),
            // the next square says it as its near face, unless there is none
            far = if (ahead == SIGHT) describe(face(squareAt(0, ahead + 1), WallSide.SOUTH), sublevel) else null,
            floor = if (ahead > 0) onTheFloor(near, sublevel) else null,
            monsters = monsters,
            items = items,
        )

        // What stands beside a square in sight shows through its open sides.
        if (ahead > 0) {
            for ((across, side) in listOf(-1 to leftFace, 1 to rightFace)) {
                if (!sublevel.showsWhatIsOnIt(side)) continue
                val (beside, besideItems) = contentsOf(squareAt(across, ahead))
                if (beside.isNotEmpty() || besideItems.isNotEmpty()) {
                    squares += SeenSquare(ahead, across, monsters = beside, items = besideItems)
                }
            }
        }
    }

    return Observation(
        level = level,
        at = world.party.position,
        facing = facing,
        inSight = squares,
        party = world.champions.mapIndexedNotNull { slot, who ->
            if (!who.inTheParty) null else who.seen(slot, world, names, types)
        },
        holding = world.held?.let { names?.of(it, types) },
        messages = messages,
        menu = menu?.let { open ->
            SeenMenu(
                title = open.title,
                says = open.says.map { it.trim() }.filter { it.isNotEmpty() },
                choices = open.entries.map { it.label }.filter { it.isNotBlank() },
            )
        },
        book = book?.let { open ->
            SeenBook(
                champion = open.whose.index,
                tab = open.at.page,
                lines = open.lines.map { spell -> spell?.let { CampSpellMessages.menuNameOf(it, open.discipline) } },
                picked = if (open.onAbort) null else open.at.line,
                resting = bookResting,
            )
        },
    )
}

/** A face as it looks, or null for none at all. */
private fun describe(wall: Maz.WallType, sublevel: SubLevel): String? = when (wall) {
    Maz.WallType.NoWall -> null
    is Maz.WallType.FixedWall -> "wall"
    is Maz.WallType.Door -> buildString {
        append(
            when {
                wall.isOpen -> "open door"
                wall.isShut -> "shut door"
                else -> "half-open door"
            },
        )
        if (wall.hasButton) append(" with a button")
    }
    Maz.WallType.StairUp -> "stairs up"
    Maz.WallType.StairDown -> "stairs down"
    is Maz.WallType.Decoration -> {
        val painted = sublevel.decorations.firstOrNull { it.decorationWallIndex == wall.decorationWallIndex }
        when {
            painted == null -> "wall"
            painted.lyingOnTheFloor -> null
            else -> {
                val base = if (painted.wallType == WallSet.DOOR_FRAME.vmpIndex) "doorway" else "wall"
                if (painted.decorationID > 0) "$base with picture ${painted.decorationID}" else base
            }
        }
    }
}

/**
 * What is painted on the floor of the square whose near face is [near], if
 * anything: such a decoration is kept on a face but drawn lying flat.
 */
private fun onTheFloor(near: Maz.WallType, sublevel: SubLevel): String? {
    if (near !is Maz.WallType.Decoration) return null
    val painted = sublevel.decorations.firstOrNull { it.decorationWallIndex == near.decorationWallIndex }
    if (painted == null || !painted.lyingOnTheFloor || painted.decorationID <= 0) return null
    return "picture ${painted.decorationID}"
}

private fun MonsterInstance.seenFrom(facing: Direction, sublevel: SubLevel) = SeenMonster(
    looksLike = sublevel.monsterGfx.getOrNull(gfxIndex)?.name ?: "something",
    facing = when (direction) {
        facing -> "away"
        facing.turnedBy(HALF_TURN) -> "towards you"
        facing.turnedBy(QUARTER_RIGHT) -> "to your right"
        else -> "to your left"
    },
    standing = place.asSeenFacing(facing)?.name?.lowercase() ?: "middle",
)

private fun Champion.seen(slot: Int, world: GameState, names: ItemNames?, types: ItemTypes?) = SeenChampion(
    slot = slot,
    name = name,
    hitPoints = "${hitPoints.current}/${hitPoints.max}",
    food = food.value,
    conditions = buildList {
        if (dead) add("down")
        if (poisoned) add("poisoned")
        if (paralysed) add("paralysed")
        if (petrified) add("stone")
        if (heldFast) add("held")
    },
    hands = CarrySlot.BOTH_HANDS.map { hand ->
        val held = world.item(holding(hand))?.let { names?.of(it, types) } ?: "empty"
        if (world.isRecovering(pl.pelotasplus.eyeofbeholder.data.model.PartySlot(slot), hand)) "$held (recovering)" else held
    },
    spells = Discipline.entries.flatMap { discipline ->
        spellsOf(discipline).slots
            .filterIsInstance<SpellSlot.Ready>()
            .map { CampSpellMessages.menuNameOf(it.spell, discipline) }
    },
)
