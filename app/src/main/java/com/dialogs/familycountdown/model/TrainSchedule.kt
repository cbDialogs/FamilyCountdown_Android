package com.dialogs.familycountdown.model

import kotlin.random.Random

/**
 * When the arrival train runs. Pure so the timing is unit-testable: the board
 * owns the clock and the coroutine, this only answers "should it be running"
 * and "how long until the next one".
 */
object TrainSchedule {
    /** First run shortly after an ARRIVED row appears (or the board comes back from blackout). */
    const val FIRST_RUN_DELAY_MS = 10_000L

    /** Then a random gap, uniform between these, for the rest of the day. */
    const val MIN_GAP_MS = 2 * 60_000L
    const val MAX_GAP_MS = 5 * 60_000L

    /** Uniform draw in [MIN_GAP_MS, MAX_GAP_MS]. */
    fun randomGapMs(random: Random = Random.Default): Long =
        random.nextLong(MIN_GAP_MS, MAX_GAP_MS + 1)

    /** The train runs only while the switch is on, the board is lit, and some event has arrived. */
    fun isActive(enabled: Boolean, blackout: Boolean, anyArrived: Boolean): Boolean =
        enabled && !blackout && anyArrived
}
