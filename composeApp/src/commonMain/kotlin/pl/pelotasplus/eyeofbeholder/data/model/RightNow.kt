package pl.pelotasplus.eyeofbeholder.data.model

import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The wall clock, in milliseconds since the epoch.
 *
 * Only saves ask for it, and only so a list of them can say which is the
 * newest. Nothing in the game is paced by it — [Ticks] does that.
 */
@OptIn(ExperimentalTime::class)
fun rightNow(): Long = Clock.System.now().toEpochMilliseconds()
