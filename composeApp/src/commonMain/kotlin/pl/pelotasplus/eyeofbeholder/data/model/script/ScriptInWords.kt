package pl.pelotasplus.eyeofbeholder.data.model.script

import pl.pelotasplus.eyeofbeholder.data.model.Location
import pl.pelotasplus.eyeofbeholder.data.model.WallSide

/**
 * A script written out the way it reads, for the log.
 *
 * A trace is read to answer one question — why did that door not open — and a
 * line of nested `data class` printouts is the wrong shape for it: the numbers
 * that matter are buried in field names that repeat on every line. So a step
 * becomes a sentence, and a condition becomes the expression it evaluates,
 * with the stack the bytecode is written on folded back into infix.
 *
 * Nothing here decides anything. A name it cannot put into words falls back on
 * the value itself, so an opcode nobody has looked at yet still prints.
 */
fun ScriptToken.inWords(): String = when (this) {
    End -> "end"
    Return -> "return"
    UpdateScreen -> "redraw"
    is Goto -> "go to ${offset.value}"
    is GoSub -> "call ${offset.value}"
    is Eval -> "if ${tokens.inWords()}"
    is Wait -> "wait $delay"
    is Message -> "say message ${messageId.index} in colour $color"
    is Sound -> "play sound $soundId at ${location.xy}"
    is OpenDoor -> "open the door at ${location.xy}"
    is CloseDoor -> "close the door at ${location.xy}"
    is Damage -> "hurt ${whoIsHurt()} for ${times}d$itemOrPips"
    is Turn -> turnInWords()
    is SetWall -> setWallInWords()
    is ToggleWall -> toggleWallInWords()
    is SetFlag -> setFlagInWords()
    is ClearFlag -> clearFlagInWords()
    is ConsumeItem -> consumeInWords()
    is NewItem -> newItemInWords()
    is Teleport -> teleportInWords()
    is CreateMonster -> createMonsterInWords()
    is NewLevelOrMonster -> newLevelInWords()
    is Dialog -> dialogInWords()
    is Encounter -> encounterInWords()
    is Launcher -> launcherInWords()
    is SpecialEvent -> specialEventInWords()
}

/**
 * A condition as an expression rather than the stack it is written on.
 *
 * The bytecode pushes what it is comparing and then the comparison, so the
 * tokens are folded back the way they were written: each operator takes the
 * two answers before it and becomes one. A stream that does not balance is
 * printed as it stands rather than guessed at.
 */
fun List<Conditional>.inWords(): String {
    val stack = ArrayDeque<String>()

    forEach { token ->
        val operator = token.asAnOperator()
        if (operator == null) {
            stack.addLast(token.inWords())
            return@forEach
        }

        val right = stack.removeLastOrNull()
        val left = stack.removeLastOrNull()
        if (left == null || right == null) {
            return joinToString(" ") { it.asAnOperator() ?: it.inWords() }
        }
        stack.addLast("$left $operator $right")
    }

    return stack.joinToString(", ")
}

private fun Conditional.asAnOperator(): String? = when (this) {
    Conditional.Equals -> "=="
    Conditional.NotEquals -> "!="
    Conditional.MoreThan -> ">"
    Conditional.MoreEqualsThan -> ">="
    Conditional.LessThan -> "<"
    Conditional.LessEqualsThan -> "<="
    Conditional.And -> "and"
    Conditional.Or -> "or"
    else -> null
}

fun Conditional.inWords(): String = when (this) {
    is Conditional.ImmediateShort -> "$value"
    is Conditional.GetWallNumber -> "wall ${location.xy}"
    is Conditional.GetWallSide -> "wall ${location.xy} ${wallIndex.asASide}"
    is Conditional.GetLevelFlag -> "level flag ${bit.index}"
    is Conditional.GetGlobalFlag -> "global flag ${bit.index}"
    Conditional.GetPartyDirection -> "the way the party face"
    Conditional.DialogResult -> "the answer given"
    Conditional.GetTriggerFlag -> "what set this off"
    Conditional.IsPartyVisible -> "the party are in sight"
    Conditional.OnSpell -> "the spell cast"
    Conditional.HasAlignment -> "alignment"
    Conditional.TestCharacters -> "how many are standing"
    Conditional.PushTrue -> "true"
    Conditional.PushFalse -> "false"
    is Conditional.HasClass -> "party has ${classes.joinToString("/") { it.name }}"
    is Conditional.HasRace -> "party has ${race?.name ?: "any race"}"
    is Conditional.RollDice -> "a roll of ${rolls}d$size${if (base == 0) "" else "+$base"}"
    is Conditional.IsItemAtLocation -> "item ${item.value} lies on ${location.xy}"
    is Conditional.ItemCountAtLocation ->
        "how many ${type?.let { "of kind ${it.value}" } ?: "things"} " +
            (if (countingWhatIsInTheAir) "fly over" else "lie on") + " ${location.xy}"

    is Conditional.IsMonsterAtLocation.BlockFlags -> "what stands on ${location.xy}"
    is Conditional.IsMonsterAtLocation.CountMonsters ->
        "how many of monsters ${monsterIds.joinToString(", ")} are left"

    is Conditional.IsPartyAtLocation.CheckCurrentBlock -> "the party stand on ${location.xy}"
    is Conditional.IsPartyAtLocation.CountCharactersWithItems ->
        "how many carry a kind-${ofType.value} worth $worth"

    Conditional.GetPointerItem.ItemType -> "item kind"
    Conditional.GetPointerItem.ItemValue -> "item worth"
    Conditional.GetPointerItem.ItemInHand -> "the thing in hand"
    is Conditional.GetPointerItem.NameContains -> "the item's name holds \"$searchString\""
    is Conditional.GetPointerItem.UnidNameContains ->
        "the item's unknown name holds \"$searchString\""

    Conditional.OnBash.ItemType -> "the kind of the thing used"
    Conditional.OnBash.ItemValue -> "what the thing used is worth"
    Conditional.OnBash.ItemExtraProperties -> "what the thing used does"
    Conditional.OnBash.LastUsedItem -> "the thing used"

    else -> toString()
}

private fun Damage.whoIsHurt() = if (charIndex < 0) "everyone" else "champion $charIndex"

private fun Turn.turnInWords() = when (this) {
    is Turn.TurnParty -> "turn the party $dir"
    is Turn.TurnFlyingObjects -> "turn what is in the air $dir"
    is Turn.Unknown -> "turn $cmd by $dir"
}

private fun SetWall.setWallInWords() = when (this) {
    is SetWall.AllSides -> "make every side of ${location.xy} wall ${to.value}"
    is SetWall.OneSide -> "make the $side side of ${location.xy} wall ${to.value}"
    is SetWall.ChangePartyDirection -> "face the party $direction"
    is SetWall.Unknown -> "set a wall, kind $type"
}

private fun ToggleWall.toggleWallInWords() = when (this) {
    is ToggleWall.AllSides -> "flip every side of ${location.xy} between $a and $b"
    is ToggleWall.OneSide -> "flip ${dir.asASide} of ${location.xy} between $a and $b"
    is ToggleWall.DoorSwitch -> "work the door switch at ${location.xy}"
    is ToggleWall.Unknown -> "flip a wall, kind $type"
}

private fun SetFlag.setFlagInWords() = when (this) {
    is SetFlag.LevelFlag -> "set level flag ${bit.index}"
    is SetFlag.GlobalFlag -> "set global flag ${bit.index}"
    is SetFlag.MonsterFlag -> "set flag ${bit.index} on monster ${monsterId.value}"
    SetFlag.DialogResult -> "set the answer given"
    SetFlag.RestingAllowed -> "let the party rest here"
    is SetFlag.Unknown -> "set a flag, kind $type"
}

private fun ClearFlag.clearFlagInWords() = when (this) {
    is ClearFlag.LevelFlag -> "clear level flag $flag"
    is ClearFlag.GlobalFlag -> "clear global flag $flag"
    ClearFlag.Event -> "clear the answer given"
    ClearFlag.RestingForbidden -> "keep the party from resting here"
    is ClearFlag.Unknown -> "clear a flag, kind $type"
}

private fun ConsumeItem.consumeInWords() = when (this) {
    ConsumeItem.DeleteHandItem -> "take away what is in hand"
    is ConsumeItem.DeleteBlockItem ->
        "take " + (if (itemType < 0) "everything" else "every kind-$itemType thing") +
            " off ${location.xy}"
}

private fun NewItem.newItemInWords(): String {
    val what = "a copy of item ${copyOf.value}"
    val worth = overrides.value?.let { " worth $it" } ?: ""
    return when (val where = goes) {
        ItemDestination.IntoTheHand -> "put $what$worth into the hand"
        ItemDestination.Underfoot -> "drop $what$worth at the party's feet"
        is ItemDestination.OnASquare -> "put $what$worth on ${where.at.xy} ${where.place}"
    }
}

private fun Teleport.teleportInWords() = when (this) {
    is Teleport.MoveParty -> "move the party to ${destination.xy}"
    is Teleport.MoveMonster -> "move the monster on ${source.xy} to ${destination.xy}"
    is Teleport.MoveItems ->
        "move " + (ofType?.let { "every kind-${it.value} thing" } ?: "everything") +
            " on ${from.on(fromLevel)} to ${to.on(toLevel)}"

    is Teleport.Unknown -> "move something, kind $type"
}

private fun CreateMonster.createMonsterInWords(): String {
    val carrying = if (pocketItem == 0) "" else ", carrying item $pocketItem"
    return "put a kind-${type.value} monster on ${location.xy} $place facing $direction$carrying"
}

private fun NewLevelOrMonster.newLevelInWords() = when (this) {
    is NewLevelOrMonster.ChangeLevel ->
        "move the party to level $level.$subLevel at ${location.xy}" +
            (direction?.let { " facing $it" } ?: "")

    is NewLevelOrMonster.LoadMonsterShapes -> "load the monster shapes in $shapesName"
}

private fun Dialog.dialogInWords() = when (this) {
    is Dialog.DisplayPicture -> "show the picture $pictureName at $x,$y"
    Dialog.CloseDialog -> "close the dialogue"
    Dialog.DisplayBackground -> "draw the dialogue's background"
    Dialog.DrawDialogBox -> "draw the dialogue box"
    is Dialog.RunDialog -> "ask text ${textId.number}, answers ${button1.index}/${button2.index}/${button3.index}"
    is Dialog.DialogText -> "speak text ${textId.number}"
    is Dialog.Unknown -> "a dialogue, kind $type"
}

private fun Encounter.encounterInWords() = when (this) {
    Encounter.DeathSequence -> "play the party's death"
    Encounter.PortalSequence -> "play the portal"
    Encounter.PasswordCheck -> "ask the password"
    is Encounter.NpcSequence -> "meet npc ${npc.value}"
}

private fun Launcher.launcherInWords(): String {
    val what = when (this) {
        is Launcher.MagicObject -> "magic $itemId"
        is Launcher.PhysicalItem -> "item $itemId"
    }
    return "launch $what from ${location.xy} going $dir"
}

private fun SpecialEvent.specialEventInWords() = when (this) {
    SpecialEvent.DrawLightningColumn -> "draw the lightning column"
    SpecialEvent.CharSelectDialogue -> "ask which champion"
    SpecialEvent.CharacterLevelGain -> "give a champion a level"
    SpecialEvent.ResurrectionSelectDialogue -> "ask who to raise"
    SpecialEvent.InitNpc -> "bring the npc into the party"
    SpecialEvent.DeletePartyItems -> "take the party's things away"
    SpecialEvent.LoadVcnData -> "load the tiles"
    is SpecialEvent.Unknown -> "a set piece, kind $cmd"
}

/** A square the short way round, the way the game's own coordinates are said. */
val Location.xy: String get() = "${x}x$y"

private fun Location.on(level: Int?) = if (level == null) xy else "$xy on level $level"

/** The face a script names by number, which is the order the maze writes them in. */
private val Int.asASide: String
    get() = WallSide.entries.getOrNull(this)?.name ?: "side $this"
