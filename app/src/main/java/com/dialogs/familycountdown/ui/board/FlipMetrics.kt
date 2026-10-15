package com.dialogs.familycountdown.ui.board

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import com.dialogs.familycountdown.ui.theme.JetBrainsMono
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Shared look/feel + geometry for the split-flap cards, derived from one base
 * font size so the whole board scales together. All lengths are **pixels**
 * (the board is laid out in physical pixels so a 1080p panel fills the same
 * way regardless of the density it reports).
 */
data class FlipMetrics(val fontSize: Float) {

    companion object {
        /** PQINA defaults ported from flip.min.js (via the iOS app). */
        const val DURATION_MS = 800          // flipDuration 800ms
        const val STAGGER_MS = 50            // 50ms right-to-left step
        /**
         * Where in a flip the falling lower leaf first touches down (0.5 + 0.5 × 1/2.75 of
         * the ease-out-bounce), i.e. when the card audibly lands.
         */
        const val CLACK_AT = 0.68f
        val CLACK_DELAY_MS: Int = (DURATION_MS * CLACK_AT).toInt()

        // The split-flap tile: a white flap with a dark character and a hairline seam.
        val glyphColor = Color(0xFF0F0F0F)   // dark character on the flap
        val cardTop = Color(0xFFFFFFFF)      // upper leaf (white)
        val cardBottom = Color(0xFFEDEDED)   // lower leaf (a hair darker, like a real flap)
        val arrivedTile = Color(0xFFD90D0D)  // red flap for ARRIVED
        val seam = Color(0xFFB8B8B8)         // hairline where the two leaves meet
        val separator = Color(0xFF333333)    // 1px line between board rows
        val headerText = Color(0xFF808080)   // DAYS / HOURS / MINUTES / SECONDS
        val gear = Color(0xFF595959)

        // Geometry ratios for the drawn flap + JetBrains Mono glyph inside it.
        const val GLYPH_SCALE = 1.2f          // glyph drawn larger than the base so it fills the flap
        const val WIDTH_RATIO = 1.05f         // flap width
        const val HEIGHT_RATIO = 1.42f        // flap height (tall, like a real split-flap)
        const val INTRA_SPACING_RATIO = 0.10f // gap between adjacent flaps
        const val GROUP_SPACING_RATIO = 0.34f // gap between D / H / M / S groups
        const val ADVANCE_RATIO = 1.15f       // per-character footprint (flap + gap) for name layout

        /** A full DDDD HH MM SS readout: 10 cards, 6 intra-group gaps, 3 group gaps. */
        val clockWidthUnits: Float =
            10 * WIDTH_RATIO + 6 * INTRA_SPACING_RATIO + 3 * GROUP_SPACING_RATIO
    }

    val width: Float = (fontSize * WIDTH_RATIO).roundToInt().toFloat()
    val halfHeight: Float = (fontSize * HEIGHT_RATIO / 2f).roundToInt().toFloat()
    val height: Float = halfHeight * 2f
    val glyphSize: Float = fontSize * GLYPH_SCALE
    val corner: Float = max(3f, fontSize * 0.07f)
    val seamThickness: Float = max(1f, fontSize * 0.03f)
    val intraSpacing: Float = fontSize * INTRA_SPACING_RATIO
    val groupSpacing: Float = fontSize * GROUP_SPACING_RATIO
    val clockWidth: Float = fontSize * clockWidthUnits

    /** Width of one digit group (used to align column labels over each group). */
    fun groupWidth(digits: Int): Float = digits * width + max(0, digits - 1) * intraSpacing

    /** Text style for measuring/drawing one glyph at this size (ignores system font scaling). */
    fun glyphStyle(density: Density): TextStyle = with(density) {
        TextStyle(
            fontFamily = JetBrainsMono,
            fontWeight = FontWeight.ExtraBold,
            fontSize = glyphSize.toSp(),
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        )
    }
}
