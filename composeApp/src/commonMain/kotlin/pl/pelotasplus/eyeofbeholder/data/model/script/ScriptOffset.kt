package pl.pelotasplus.eyeofbeholder.data.model.script

import kotlin.jvm.JvmInline

/**
 * Where an instruction sits, counted in bytes from the start of the script
 * block rather than of the INF file.
 *
 * Jumps, trigger entries and a paused dialogue all name a place to carry on
 * from, and nothing else in a script is measured this way.
 */
@JvmInline
value class ScriptOffset(val value: Int)
