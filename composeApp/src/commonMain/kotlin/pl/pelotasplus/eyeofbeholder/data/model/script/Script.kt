package pl.pelotasplus.eyeofbeholder.data.model.script

/**
 * A single script instruction with its byte offset within the script block.
 *
 * The [offset] is relative to the start of the script bytecode (not the INF file).
 * It's used by [Goto], [GoSub], and [Eval] to reference jump targets, and by
 * Block C trigger entries to specify which script instruction to execute when
 * the party steps on a trigger square.
 *
 * @property offset Byte offset from the start of the script block
 * @property token The parsed instruction
 */
data class Script(
    val offset: ScriptOffset,
    val token: ScriptToken
)

