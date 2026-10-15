package com.dialogs.familycountdown.ui.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Half { TOP, BOTTOM }

/** Measures each glyph once per (char, size) so draw passes never re-layout text. */
private class GlyphCache(private val measurer: TextMeasurer, private val style: TextStyle) {
    private val cache = HashMap<Char, TextLayoutResult>()
    operator fun get(ch: Char): TextLayoutResult =
        cache.getOrPut(ch) { measurer.measure(AnnotatedString(ch.toString()), style, softWrap = false) }
}

@Composable
private fun rememberGlyphCache(metrics: FlipMetrics): GlyphCache {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    return remember(measurer, metrics, density) { GlyphCache(measurer, metrics.glyphStyle(density)) }
}

/** Draws one half (top or bottom) of a glyph on its leaf, clipped to that half. */
private fun DrawScope.drawLeaf(
    glyph: TextLayoutResult, half: Half, m: FlipMetrics,
    tile: Color, ink: Color, darken: Float = 0f,
) {
    val top = if (half == Half.TOP) 0f else m.halfHeight
    clipRect(0f, top, m.width, top + m.halfHeight) {
        drawRect(tile, Offset(0f, top), Size(m.width, m.halfHeight))
        // Glyph centered in the full flap; the clip shows only this half.
        drawText(
            glyph, color = ink,
            topLeft = Offset((m.width - glyph.size.width) / 2f, (m.height - glyph.size.height) / 2f),
        )
        if (darken > 0f) drawRect(Color.Black.copy(alpha = darken), Offset(0f, top), Size(m.width, m.halfHeight))
    }
}

private fun DrawScope.drawSeam(m: FlipMetrics) {
    drawRect(FlipMetrics.seam, Offset(0f, m.halfHeight - m.seamThickness / 2f), Size(m.width, m.seamThickness))
}

private fun tileModifier(m: FlipMetrics, density: Density, outer: Modifier = Modifier): Modifier = with(density) {
    outer.size(m.width.toDp(), m.height.toDp()).clip(RoundedCornerShape(m.corner.toDp()))
}

/**
 * A single resting flap (no animation), used for the static event-name and
 * ARRIVED lettering so names and digits read as the same physical tiles.
 */
@Composable
fun TileFace(
    char: Char,
    metrics: FlipMetrics,
    tileTop: Color = FlipMetrics.cardTop,
    tileBottom: Color = FlipMetrics.cardBottom,
    glyph: Color = FlipMetrics.glyphColor,
    modifier: Modifier = Modifier,
) {
    val glyphs = rememberGlyphCache(metrics)
    Canvas(tileModifier(metrics, LocalDensity.current, modifier)) {
        val g = glyphs[char]
        drawLeaf(g, Half.TOP, metrics, tileTop, glyph)
        drawLeaf(g, Half.BOTTOM, metrics, tileBottom, glyph)
        drawSeam(metrics)
    }
}

/**
 * One flip card showing a single character, animating the classic two-leaf
 * mechanical fold whenever its [value] changes. [delayMs] staggers it within a group.
 * When [tick] is true and [LocalBoardSounds] is set, the card clicks as its lower
 * leaf lands ([FlipMetrics.CLACK_AT]).
 *
 * Phase 1 (progress 0–0.5): the old top leaf folds down toward the viewer.
 * Phase 2 (0.5–1): the new bottom leaf drops and bounces to settle.
 * Progress is read inside `graphicsLayer` / the draw lambda only, so the
 * 60 fps animation never recomposes or re-lays-out the board.
 */
@Composable
fun FlipDigit(
    value: Char,
    metrics: FlipMetrics,
    delayMs: Int = 0,
    tick: Boolean = true,
    tileTop: Color = FlipMetrics.cardTop,
    tileBottom: Color = FlipMetrics.cardBottom,
    glyph: Color = FlipMetrics.glyphColor,
    modifier: Modifier = Modifier,
) {
    var oldChar by remember { mutableStateOf(value) }
    var newChar by remember { mutableStateOf(value) }
    val progress = remember { Animatable(1f) }   // 1 = fully settled on the new char
    val sounds = if (tick) LocalBoardSounds.current else null

    LaunchedEffect(value) {
        if (value != newChar) {
            oldChar = newChar
            newChar = value
            if (sounds != null) {
                // Cancelled with this effect if the value changes again before the card lands.
                launch { delay((delayMs + FlipMetrics.CLACK_DELAY_MS).toLong()); sounds.tick() }
            }
            progress.snapTo(0f)                   // jump to show the old char
            progress.animateTo(1f, tween(FlipMetrics.DURATION_MS, delayMs, LinearEasing))
        }
    }

    val glyphs = rememberGlyphCache(metrics)
    val density = LocalDensity.current

    Box(tileModifier(metrics, density, modifier)) {
        // Static backing: new on top (revealed as old folds away), old on bottom
        // (until the falling new leaf covers it).
        Canvas(Modifier.size(with(density) { metrics.width.toDp() }, with(density) { metrics.height.toDp() })) {
            drawLeaf(glyphs[newChar], Half.TOP, metrics, tileTop, glyph)
            drawLeaf(glyphs[oldChar], Half.BOTTOM, metrics, tileBottom, glyph)
        }

        // The moving leaf. Both leaves hinge on the centre seam, so the layer's
        // transform origin is the tile centre and only rotationX changes.
        Canvas(
            Modifier
                .size(with(density) { metrics.width.toDp() }, with(density) { metrics.height.toDp() })
                .graphicsLayer {
                    val p = progress.value
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    cameraDistance = 12f * density.density
                    rotationX = if (p < 0.5f) {
                        90f * easeIn(p / 0.5f)                 // top leaf: top edge swings toward viewer, down
                    } else {
                        -90f * (1f - easeOutBounce((p - 0.5f) / 0.5f))   // bottom leaf: drops from the seam
                    }
                }
        ) {
            val p = progress.value
            if (p < 0.5f) {
                val t = easeIn(p / 0.5f)
                drawLeaf(glyphs[oldChar], Half.TOP, metrics, tileTop, glyph, darken = 0.55f * t)
            } else {
                val t = easeOutBounce((p - 0.5f) / 0.5f)
                drawLeaf(glyphs[newChar], Half.BOTTOM, metrics, tileBottom, glyph, darken = 0.5f * (1f - t))
            }
        }

        // Centre seam.
        Box(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(with(density) { metrics.seamThickness.toDp() })
                .background(FlipMetrics.seam)
        )
    }
}

private fun easeIn(t: Float): Float = t * t

/** Port of PQINA flip's `ease-out-bounce`. */
private fun easeOutBounce(t: Float): Float {
    val n1 = 7.5625f
    val d1 = 2.75f
    var e = t
    return when {
        e < 1f / d1 -> n1 * e * e
        e < 2f / d1 -> { e -= 1.5f / d1; n1 * e * e + 0.75f }
        e < 2.5f / d1 -> { e -= 2.25f / d1; n1 * e * e + 0.9375f }
        else -> { e -= 2.625f / d1; n1 * e * e + 0.984375f }
    }
}
