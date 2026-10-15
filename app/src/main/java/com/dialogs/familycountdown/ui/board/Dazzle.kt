package com.dialogs.familycountdown.ui.board

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * One "razzle dazzle" run: for [TICKS] ticks of [TICK_MS] every tile on the
 * board flips to a random letter/digit, then on the final tick every tile
 * flips back to its real character. Ticks are driven by BoardScreen.
 *
 * [charFor] is a pure function of (seed, tick, row, column) so the same run
 * is deterministic and recomposition never changes what a tile is showing.
 */
class DazzleRun(val seed: Long = System.nanoTime()) {
    /** 0 = at rest (real chars), 1..TICKS = scrambled, > TICKS = settling back on the real chars. */
    var tick by mutableIntStateOf(0)

    val isScrambled: Boolean get() = tick in 1..TICKS

    fun charFor(row: Int, column: Int, real: Char): Char {
        if (!isScrambled) return real
        var h = seed xor (tick * 0x9E3779B97F4A7C15UL.toLong()) xor
            ((row * 131 + column) * 0xBF58476D1CE4E5B9UL.toLong())
        h = (h xor (h ushr 31)) * 0x94D049BB133111EBUL.toLong()
        h = h xor (h ushr 29)
        return ALPHABET[((h ushr 1) % ALPHABET.length).toInt()]
    }

    /** Left-to-right wave: each column starts its flip a little after the one before. */
    fun stagger(column: Int): Int = (column * STAGGER_PER_COLUMN_MS).coerceAtMost(MAX_STAGGER_MS)

    companion object {
        const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        /** 6 × 800 ms ≈ 5 s of scrambling before the settle tick. */
        const val TICKS = 6
        /** Equal to the flip duration so every flip completes before its tile gets a new value. */
        const val TICK_MS = FlipMetrics.DURATION_MS.toLong()
        const val STAGGER_PER_COLUMN_MS = 40
        const val MAX_STAGGER_MS = 600
        /** Grace after the settle tick for the staggered bounce to finish before tiles go static again. */
        const val SETTLE_GRACE_MS = FlipMetrics.DURATION_MS.toLong() + MAX_STAGGER_MS + 100L
    }
}

/**
 * A board tile that is a cheap static [TileFace] at rest and becomes an
 * animated [FlipDigit] only while a [DazzleRun] is active. Both ends of the
 * swap happen with the tile showing [real], so nothing visibly jumps.
 */
@Composable
fun DazzleTile(
    real: Char,
    row: Int,
    column: Int,
    dazzle: DazzleRun?,
    metrics: FlipMetrics,
    tileTop: Color = FlipMetrics.cardTop,
    tileBottom: Color = FlipMetrics.cardBottom,
    glyph: Color = FlipMetrics.glyphColor,
    modifier: Modifier = Modifier,
) {
    if (dazzle == null) {
        TileFace(char = real, metrics = metrics, tileTop = tileTop, tileBottom = tileBottom, glyph = glyph, modifier = modifier)
    } else {
        FlipDigit(
            value = dazzle.charFor(row, column, real), metrics = metrics, delayMs = dazzle.stagger(column),
            tick = false,   // the run plays one card shuffle instead of a hundred ticks
            tileTop = tileTop, tileBottom = tileBottom, glyph = glyph, modifier = modifier,
        )
    }
}
