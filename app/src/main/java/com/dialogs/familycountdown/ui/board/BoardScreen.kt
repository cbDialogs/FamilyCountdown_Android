package com.dialogs.familycountdown.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dialogs.familycountdown.model.CountdownEngine
import com.dialogs.familycountdown.model.CountdownEvent
import com.dialogs.familycountdown.model.EventStore
import com.dialogs.familycountdown.model.HolidayProvider
import com.dialogs.familycountdown.model.SettingsStore
import com.dialogs.familycountdown.model.TrainSchedule
import com.dialogs.familycountdown.ui.theme.JetBrainsMono
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * The station-board display: black, landscape, as many rows as fit.
 * Names on the left, split-flap countdowns on the right. A subtle gear — or a
 * tap anywhere on the board — opens the editor. Tapping the first tile of the
 * top row starts a "razzle dazzle" run; between 9 PM and 6 AM (display zone)
 * the board is painted black when night blackout is on. While any event shows
 * ARRIVED (and the board is lit), a steam train whistles across the middle of
 * the screen every few minutes; tapping the bottom-right card runs it on demand.
 */
@Composable
fun BoardScreen(store: EventStore, settings: SettingsStore, onOpenEditor: () -> Unit) {
    val userEvents by store.events.collectAsState()
    val tzId by settings.timeZoneIdentifier.collectAsState()
    val blackoutEnabled by settings.nightBlackoutEnabled.collectAsState()
    val trainEnabled by settings.arrivalTrainEnabled.collectAsState()

    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L - System.currentTimeMillis() % 1000L)   // tick on the wall-clock second
            now = Instant.now()
        }
    }

    // Resolved every tick so a system time-zone change (Automatic) takes effect.
    val zone = SettingsStore.zoneFor(tzId)
    val year = now.atZone(zone).year
    // User events plus generated US holidays (deduped by label against user events).
    val allEvents: List<CountdownEvent> = remember(userEvents, zone, year) {
        val userLabels = userEvents.map { it.label.lowercase() }.toSet()
        userEvents + HolidayProvider.holidays(now, zone).filter { it.label.lowercase() !in userLabels }
    }
    val board = CountdownEngine.board(allEvents, now, zone)
    val blackout = blackoutEnabled && SettingsStore.isBlackoutHour(now, zone)

    // Razzle dazzle: one run at a time; BoardScreen owns the tick driver.
    var dazzle by remember { mutableStateOf<DazzleRun?>(null) }
    LaunchedEffect(dazzle) {
        val run = dazzle ?: return@LaunchedEffect
        for (t in 1..DazzleRun.TICKS + 1) {
            delay(DazzleRun.TICK_MS)
            run.tick = t
        }
        delay(DazzleRun.SETTLE_GRACE_MS)   // let the staggered bounce finish before tiles go static
        dazzle = null
    }

    // Arrival train: first run shortly after an ARRIVED row appears, then at random
    // every few minutes. Keyed on the boolean so it only restarts on transitions.
    val context = LocalContext.current
    val whistle = remember { TrainWhistle(context) }
    DisposableEffect(whistle) { onDispose { whistle.release() } }
    val trainActive = TrainSchedule.isActive(trainEnabled, blackout, board.any { it.arrived })
    var train by remember { mutableStateOf<TrainRun?>(null) }
    LaunchedEffect(trainActive) {
        if (!trainActive) { train = null; return@LaunchedEffect }
        delay(TrainSchedule.FIRST_RUN_DELAY_MS)
        while (true) {
            if (train == null) train = TrainRun()
            delay(TrainSchedule.randomGapMs())
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Tap anywhere opens the editor; the gear and the dazzle tile consume their own taps first.
            .pointerInput(Unit) { detectTapGestures(onTap = { onOpenEditor() }) },
    ) {
        if (blackout) return@BoxWithConstraints   // night: nothing but black (and the tap handler)

        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()
        val longest = board.maxOfOrNull { it.label.length } ?: 1
        val layout = remember(widthPx, heightPx, longest) { BoardLayout(widthPx, heightPx, longest) }

        // Pinned events are always kept on screen (at the top); the rest of the
        // rows are filled with the soonest non-pinned events.
        val pinned = board.filter { it.pinned }.take(layout.maxRows)
        val nonPinned = board.filter { !it.pinned }.take(maxOf(0, layout.maxRows - pinned.size))
        val visible = pinned + nonPinned

        if (visible.isEmpty()) {
            EmptyState(layout)
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = with(density) { layout.hPadding.toDp() })
                    .padding(top = with(density) { layout.topInset.toDp() })
            ) {
                Box(
                    Modifier.fillMaxWidth().height(with(density) { layout.headerHeight.toDp() }),
                    contentAlignment = Alignment.BottomEnd,
                ) {
                    ClockHeader(layout.metrics)
                }
                val rowHeight = with(density) { layout.rowHeight.toDp() }
                visible.forEachIndexed { idx, event ->
                    key(event.id) {
                        EventRow(
                            event = event, metrics = layout.metrics, rowHeight = rowHeight,
                            dazzle = dazzle,
                            row = idx,
                            clockStartColumn = longest + 1,
                            onDazzleTap = if (idx == 0) ({ if (dazzle == null) dazzle = DazzleRun() }) else null,
                            onTrainTap = if (idx == visible.lastIndex) ({ if (train == null) train = TrainRun() }) else null,
                        )
                        Box(Modifier.fillMaxWidth().height(with(density) { 1f.toDp() }).background(FlipMetrics.separator))
                    }
                }
            }
        }

        train?.let { run ->
            TrainOverlay(
                run = run, widthPx = widthPx, heightPx = heightPx, rowHeightPx = layout.rowHeight,
                whistle = whistle, onFinished = { train = null },
            )
        }

        IconButton(onClick = onOpenEditor, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(48.dp)) {
            Icon(Icons.Filled.Settings, contentDescription = "Edit events", tint = FlipMetrics.gear, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun EmptyState(layout: BoardLayout) {
    val density = LocalDensity.current
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "No upcoming events",
            fontFamily = JetBrainsMono,
            fontSize = with(density) { layout.metrics.fontSize.toSp() },
            color = Color(0xFF999999),
        )
        Text(
            "Tap the gear to add one",
            fontSize = 18.sp,
            color = Color(0xFF595959),
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}
