package pl.pelotasplus.eyeofbeholder.data.model.script

/**
 * Base type for all script bytecode instructions in the Eye of the Beholder scripting system.
 *
 * The game's scripting engine drives ALL interactive behavior: opening doors, triggering
 * traps, spawning monsters, displaying messages, teleporting the party, and more.
 * Scripts are stored as bytecode in Block B of .INF files and are triggered by:
 * - Stepping on a square (trigger map in Block C)
 * - Interacting with a wall (clicking on buttons, levers)
 * - Timer events ([ScriptTimer])
 * - Other scripts via GoSub/Goto
 *
 * ## Script execution model
 * The engine maintains a program counter (offset into the bytecode) and a condition
 * stack for evaluating [Eval] expressions. Scripts execute sequentially until hitting
 * [End], [Return], or a conditional [Goto] that jumps elsewhere.
 *
 * ## Opcode summary (29 implemented)
 * **World manipulation:** SetWall, ToggleWall, OpenDoor, CloseDoor
 * **Movement:** Teleport, NewLevelOrMonster, Turn
 * **Entities:** CreateMonster, Encounter
 * **Items:** NewItem, ConsumeItem
 * **Control flow:** Goto, GoSub, Return, End, Eval (conditional branching)
 * **Flags/state:** SetFlag, ClearFlag
 * **UI/effects:** Message, Dialog, Sound, Damage, Launcher, SpecialEvent
 * **Timing:** Wait, UpdateScreen
 */
sealed interface ScriptToken
