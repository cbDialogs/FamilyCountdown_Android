package com.dialogs.familycountdown.ui.board

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/**
 * One arrival-train run: a cartoon steam locomotive pulling three passenger
 * cars crosses the middle of the board from right to left over [CROSSING_MS],
 * whistling as it enters and again part-way across. Runs are started by
 * BoardScreen; the run itself only carries a seed for the smoke jitter.
 */
class TrainRun(val seed: Long = System.nanoTime()) {
    companion object {
        const val CROSSING_MS = 9_000
        /** The clip is ~2 s, so it sounds twice per crossing, like a grade crossing. */
        const val SECOND_WHISTLE_AT = 0.55f
        /** Train height (stack top to rail) relative to one board row. */
        const val HEIGHT_PER_ROW = 1.6f
    }
}

/**
 * Draws [run] over the board. No pointer handling, so taps fall through to the
 * board's tap-anywhere handler and the gear. Progress is read only inside the
 * draw lambda, so the board never recomposes while the train is moving.
 */
@Composable
fun TrainOverlay(
    run: TrainRun,
    widthPx: Float,
    heightPx: Float,
    rowHeightPx: Float,
    sounds: BoardSounds?,
    onFinished: () -> Unit,
) {
    val progress = remember(run) { Animatable(0f) }
    LaunchedEffect(run) {
        sounds?.whistle()
        launch {
            delay((TrainRun.CROSSING_MS * TrainRun.SECOND_WHISTLE_AT).toLong())
            sounds?.whistle()
        }
        progress.animateTo(1f, tween(TrainRun.CROSSING_MS, easing = LinearEasing))
        onFinished()
    }

    val geo = remember(rowHeightPx) { TrainGeometry(rowHeightPx * TrainRun.HEIGHT_PER_ROW) }
    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        val travel = widthPx + 2f * geo.length          // fully off-screen right → fully off-screen left
        val nose = widthPx + geo.length - p * travel    // x of the cowcatcher tip
        drawTrain(geo, nose = nose, rail = heightPx / 2f + geo.h / 2f, travelled = p * travel,
            seconds = p * TrainRun.CROSSING_MS / 1000f, seed = run.seed)
    }
}

/** All lengths in pixels, derived from the train height [h] (stack top to rail). */
class TrainGeometry(val h: Float) {
    val locoLength = 1.9f * h
    val carLength = 1.5f * h
    val coupling = 0.12f * h
    val length = locoLength + 3 * (coupling + carLength)
    val driverRadius = 0.17f * h
    val pilotRadius = 0.10f * h
    val carWheelRadius = 0.12f * h
    val stroke = (0.025f * h).coerceAtLeast(2f)
}

private val outline = Color(0xFF1A1A1A)
private val locoRed = Color(0xFFD32F2F)
private val cabRed = Color(0xFFB71C1C)
private val roofDark = Color(0xFF37474F)
private val frameGrey = Color(0xFF455A64)
private val brass = Color(0xFFFFC107)
private val windowGlow = Color(0xFFFFF59D)
private val wheelDark = Color(0xFF263238)
private val wheelRim = Color(0xFF90A4AE)
private val smokeGrey = Color(0xFFCFD8DC)
private val carColors = listOf(Color(0xFF2E7D32), Color(0xFF1565C0), Color(0xFFF9A825))

private fun DrawScope.drawTrain(geo: TrainGeometry, nose: Float, rail: Float, travelled: Float, seconds: Float, seed: Long) {
    val h = geo.h
    val stackX = nose + 0.45f * h
    val stackTop = rail - h

    // Smoke first (behind everything): six puffs on a looping cycle, drifting back and up.
    val jitter = ((seed ushr 7) % 1000L) / 1000f
    for (i in 0 until 6) {
        val age = ((seconds * 0.9f + i / 6f + jitter) % 1f)
        val px = stackX + age * 1.1f * h + sin((age + i) * 6.28f) * 0.06f * h
        val py = stackTop - 0.05f * h - age * 1.0f * h
        val r = 0.07f * h + age * 0.18f * h
        drawCircle(smokeGrey.copy(alpha = (1f - age) * 0.85f), r, Offset(px, py))
    }

    // Three passenger cars, coupled behind the locomotive.
    var carFront = nose + geo.locoLength + geo.coupling
    for (i in 0 until 3) {
        drawCoupling(carFront - geo.coupling, carFront, rail, h)
        drawCar(carFront, rail, geo, carColors[i], travelled)
        carFront += geo.carLength + geo.coupling
    }

    drawLocomotive(nose, rail, geo, travelled)
}

private fun DrawScope.drawCoupling(x0: Float, x1: Float, rail: Float, h: Float) {
    drawRect(frameGrey, Offset(x0 - 0.02f * h, rail - 0.34f * h), Size(x1 - x0 + 0.04f * h, 0.06f * h))
}

private fun DrawScope.drawLocomotive(nose: Float, rail: Float, geo: TrainGeometry, travelled: Float) {
    val h = geo.h
    val s = geo.stroke
    val frameTop = rail - 0.27f * h

    // Frame / running board.
    drawRect(frameGrey, Offset(nose + 0.18f * h, frameTop), Size(geo.locoLength - 0.18f * h, 0.07f * h))

    // Cowcatcher.
    val cow = Path().apply {
        moveTo(nose, rail - 0.02f * h)
        lineTo(nose + 0.24f * h, rail - 0.42f * h)
        lineTo(nose + 0.24f * h, rail - 0.02f * h)
        close()
    }
    drawPath(cow, frameGrey)
    drawPath(cow, outline, style = Stroke(s))

    // Boiler.
    val boiler = Offset(nose + 0.22f * h, rail - 0.66f * h)
    val boilerSize = Size(0.98f * h, 0.42f * h)
    drawRoundRect(locoRed, boiler, boilerSize, CornerRadius(0.14f * h))
    drawRoundRect(outline, boiler, boilerSize, CornerRadius(0.14f * h), style = Stroke(s))
    // Boiler bands.
    for (k in 1..2) {
        val bx = boiler.x + k * 0.32f * h
        drawLine(outline, Offset(bx, boiler.y), Offset(bx, boiler.y + boilerSize.height), s)
    }
    // Headlamp.
    drawCircle(brass, 0.07f * h, Offset(nose + 0.24f * h, rail - 0.47f * h))
    drawCircle(outline, 0.07f * h, Offset(nose + 0.24f * h, rail - 0.47f * h), style = Stroke(s))

    // Smokestack with flared top.
    drawRect(outline, Offset(nose + 0.38f * h, rail - 0.96f * h), Size(0.14f * h, 0.32f * h))
    drawRoundRect(outline, Offset(nose + 0.32f * h, rail - 1.0f * h), Size(0.26f * h, 0.09f * h), CornerRadius(0.03f * h))
    // Steam dome and bell.
    drawArc(brass, 180f, 180f, true, Offset(nose + 0.78f * h, rail - 0.78f * h), Size(0.22f * h, 0.24f * h))
    drawArc(outline, 180f, 180f, false, Offset(nose + 0.78f * h, rail - 0.78f * h), Size(0.22f * h, 0.24f * h), style = Stroke(s))

    // Cab with roof and window.
    val cab = Offset(nose + 1.2f * h, rail - 0.86f * h)
    val cabSize = Size(0.62f * h, 0.62f * h)
    drawRoundRect(cabRed, cab, cabSize, CornerRadius(0.05f * h))
    drawRoundRect(outline, cab, cabSize, CornerRadius(0.05f * h), style = Stroke(s))
    drawRoundRect(roofDark, Offset(cab.x - 0.06f * h, cab.y - 0.07f * h), Size(cabSize.width + 0.12f * h, 0.1f * h), CornerRadius(0.03f * h))
    val win = Offset(cab.x + 0.1f * h, cab.y + 0.12f * h)
    val winSize = Size(0.28f * h, 0.24f * h)
    drawRoundRect(windowGlow, win, winSize, CornerRadius(0.03f * h))
    drawRoundRect(outline, win, winSize, CornerRadius(0.03f * h), style = Stroke(s))

    // Wheels: pilot, two drivers with a connecting rod, trailing wheel.
    drawWheel(Offset(nose + 0.40f * h, rail - geo.pilotRadius), geo.pilotRadius, travelled, s)
    val d1 = Offset(nose + 0.85f * h, rail - geo.driverRadius)
    val d2 = Offset(nose + 1.30f * h, rail - geo.driverRadius)
    drawWheel(d1, geo.driverRadius, travelled, s)
    drawWheel(d2, geo.driverRadius, travelled, s)
    drawWheel(Offset(nose + 1.68f * h, rail - geo.carWheelRadius), geo.carWheelRadius, travelled, s)
    val crank = -travelled / geo.driverRadius
    val pinOffset = Offset(cos(crank) * 0.55f * geo.driverRadius, sin(crank) * 0.55f * geo.driverRadius)
    drawLine(locoRed, d1 + pinOffset, d2 + pinOffset, s * 2.2f)
    drawLine(outline, d1 + pinOffset, d2 + pinOffset, s * 0.8f)
}

private fun DrawScope.drawCar(front: Float, rail: Float, geo: TrainGeometry, color: Color, travelled: Float) {
    val h = geo.h
    val s = geo.stroke
    val body = Offset(front, rail - 0.80f * h)
    val bodySize = Size(geo.carLength, 0.56f * h)
    drawRoundRect(color, body, bodySize, CornerRadius(0.08f * h))
    drawRoundRect(outline, body, bodySize, CornerRadius(0.08f * h), style = Stroke(s))
    // Roof strip and under-frame.
    drawRoundRect(roofDark, Offset(front - 0.04f * h, body.y - 0.06f * h), Size(bodySize.width + 0.08f * h, 0.1f * h), CornerRadius(0.04f * h))
    drawRect(frameGrey, Offset(front + 0.05f * h, rail - 0.27f * h), Size(geo.carLength - 0.1f * h, 0.06f * h))
    // Four lit windows.
    val winW = 0.20f * h
    val winH = 0.24f * h
    val gap = (geo.carLength - 4 * winW) / 5f
    for (k in 0 until 4) {
        val wx = front + gap + k * (winW + gap)
        val wy = body.y + 0.14f * h
        drawRoundRect(windowGlow, Offset(wx, wy), Size(winW, winH), CornerRadius(0.03f * h))
        drawRoundRect(outline, Offset(wx, wy), Size(winW, winH), CornerRadius(0.03f * h), style = Stroke(s))
    }
    drawWheel(Offset(front + 0.30f * h, rail - geo.carWheelRadius), geo.carWheelRadius, travelled, s)
    drawWheel(Offset(front + geo.carLength - 0.30f * h, rail - geo.carWheelRadius), geo.carWheelRadius, travelled, s)
}

/** A spoked wheel; spokes turn counter-clockwise with distance travelled (the train moves left). */
private fun DrawScope.drawWheel(center: Offset, r: Float, travelled: Float, s: Float) {
    drawCircle(wheelDark, r, center)
    drawCircle(wheelRim, r, center, style = Stroke(s * 1.5f))
    val angle = -travelled / r
    for (k in 0 until 4) {
        val a = angle + k * (Math.PI / 4).toFloat()
        val dx = cos(a) * r * 0.85f
        val dy = sin(a) * r * 0.85f
        drawLine(wheelRim, Offset(center.x - dx, center.y - dy), Offset(center.x + dx, center.y + dy), s)
    }
    drawCircle(wheelRim, r * 0.22f, center)
}
