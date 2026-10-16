package com.dialogs.familycountdown.ui.board

import kotlin.math.max
import kotlin.math.min

/**
 * Derives one uniform font size, row height, and how many rows fit for the
 * current screen (all in pixels). The font size is the largest that still lets
 * the longest event name and the full DDDD·HH·MM·SS clock share a single line
 * **and** lets [TARGET_ROWS] rows fit under the header — so every row (names
 * and digits alike) renders at exactly the same size. A longer name shrinks
 * the font further, and then more rows fit.
 */
class BoardLayout(widthPx: Float, heightPx: Float, longestLabel: Int) {
    val hPadding = 40f
    val topInset = 44f            // reserved strip so the gear never overlaps the header
    val fontSize: Float
    val metrics: FlipMetrics
    val headerHeight: Float
    val rowHeight: Float
    val maxRows: Int

    init {
        // Fixed horizontal chrome: left/right padding + name↔clock gaps.
        val chrome = hPadding * 2 + 60f
        val nameUnits = max(1, longestLabel) * FlipMetrics.ADVANCE_RATIO
        val widthFit = (widthPx - chrome) / (nameUnits + FlipMetrics.clockWidthUnits)

        // Height cap: the largest font at which TARGET_ROWS rows fit. Closed-form
        // estimate first (row ≈ fontSize × (HEIGHT_RATIO + 2 × V_PAD) + 1 px
        // separator; header ≈ fontSize × 0.24 + 10), then step down to absorb
        // the whole-pixel rounding inside FlipMetrics.
        val perFont = TARGET_ROWS * (FlipMetrics.HEIGHT_RATIO + 2 * V_PAD_RATIO) + HEADER_RATIO
        var heightCap = min(MAX_FONT, (heightPx - topInset - HEADER_GAP - TARGET_ROWS) / perFont)
        while (heightCap > MIN_FONT && rowsFor(heightCap, heightPx) < TARGET_ROWS) heightCap -= 0.5f

        fontSize = max(MIN_FONT, min(heightCap, widthFit))
        metrics = FlipMetrics(fontSize)
        headerHeight = headerHeightFor(fontSize)
        rowHeight = rowHeightFor(fontSize, metrics)
        maxRows = rowsFor(fontSize, heightPx)
    }

    private fun headerHeightFor(fontSize: Float) = fontSize * HEADER_RATIO + HEADER_GAP   // label + gap under the header

    private fun rowHeightFor(fontSize: Float, metrics: FlipMetrics) = metrics.height + fontSize * V_PAD_RATIO * 2

    /** How many rows (each followed by a 1 px separator) fit at this font size. */
    private fun rowsFor(fontSize: Float, heightPx: Float): Int {
        val rowHeight = rowHeightFor(fontSize, FlipMetrics(fontSize))
        return max(1, ((heightPx - topInset - headerHeightFor(fontSize)) / (rowHeight + 1f)).toInt())
    }

    companion object {
        /** Rows the font size is chosen to fit on screen (a 1080p panel shows exactly this many). */
        const val TARGET_ROWS = 9
        const val MIN_FONT = 16f
        const val MAX_FONT = 58f
        private const val V_PAD_RATIO = 0.34f     // vertical padding above/below a row's flaps
        private const val HEADER_RATIO = 0.24f    // DAYS / HOURS / … label height
        private const val HEADER_GAP = 10f
    }
}
