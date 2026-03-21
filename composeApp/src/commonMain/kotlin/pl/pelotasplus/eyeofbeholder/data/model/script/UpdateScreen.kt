package pl.pelotasplus.eyeofbeholder.data.model.script

/**
 * Forces a viewport redraw mid-script. Opcode 0xE4.
 *
 * Normally the screen updates only after script execution completes.
 * This opcode forces an immediate refresh, used after visual changes
 * (opening doors, moving walls) that the player should see before the
 * script continues.
 */
data object UpdateScreen : ScriptToken
