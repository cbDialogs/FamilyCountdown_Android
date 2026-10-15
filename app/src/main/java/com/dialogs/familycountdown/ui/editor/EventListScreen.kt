package com.dialogs.familycountdown.ui.editor

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import com.dialogs.familycountdown.model.CountdownEvent
import com.dialogs.familycountdown.model.EventStore
import com.dialogs.familycountdown.model.SettingsStore
import java.time.LocalDate

/**
 * Editable list of the user's events (generated holidays are not shown here —
 * they're computed automatically and can't be edited), plus display settings.
 */
@Composable
fun EventListScreen(store: EventStore, settings: SettingsStore, state: EditorState, onDone: () -> Unit) {
    val events by store.events.collectAsState()
    val tzId by settings.timeZoneIdentifier.collectAsState()
    val zone = SettingsStore.zoneFor(tzId)
    val sorted = remember(events) { events.sortedBy { it.targetDate } }
    var pendingDelete by remember { mutableStateOf<CountdownEvent?>(null) }

    Column(Modifier.fillMaxSize()) {
        SheetTopBar(
            title = "Events",
            leading = { BarButton("Done", bold = true, onClick = onDone) },
            trailing = {
                IconButton(onClick = {
                    // Next midnight in the display zone is a sensible default for a new event.
                    val tomorrow = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant()
                    state.draft = CountdownEvent(label = "", targetDate = tomorrow)
                    state.draftIsNew = true
                    state.push(Screen.Editor)
                }) { Icon(Icons.Filled.Add, contentDescription = "Add event") }
            },
        )

        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Section(
                    header = "Display",
                    footer = "Time Zone sets when each day rolls over. Shift Event Times re-stamps your saved events " +
                        "from one zone to another (e.g. treat times authored in Eastern as the same wall-clock time in Central).",
                ) {
                    SettingsRow(
                        title = "Time Zone",
                        value = if (tzId.isEmpty()) "Automatic" else SettingsStore.pretty(zone.id),
                        chevron = true,
                        onClick = { state.push(Screen.ZonePicker(ZoneTarget.DISPLAY)) },
                    )
                    RowDivider()
                    SettingsRow(
                        title = "Shift Event Times…",
                        chevron = true,
                        onClick = {
                            state.shiftApplied = false
                            state.push(Screen.Shift)
                        },
                    )
                }
            }

            item { KioskSection(settings) }

            item {
                if (pendingDelete != null) {
                    val ev = pendingDelete!!
                    InlineConfirm(
                        message = "Delete \"${ev.label.ifBlank { "(untitled)" }}\"?",
                        confirmLabel = "Delete",
                        onConfirm = { store.delete(ev); pendingDelete = null },
                        onCancel = { pendingDelete = null },
                    )
                }
            }

            item {
                Section(
                    footer = "US holidays (New Year's, Easter, July 4th, Thanksgiving, Christmas) are added automatically and don't appear here.",
                ) {
                    if (sorted.isEmpty()) {
                        SettingsRow(title = "No events", subtitle = "Tap + to add one")
                    }
                    Column {
                        sorted.forEachIndexed { idx, event ->
                            SettingsRow(
                                title = event.label.ifBlank { "(untitled)" },
                                subtitle = SettingsStore.mediumString(event.targetDate, zone),
                                onClick = {
                                    state.draft = event
                                    state.draftIsNew = false
                                    state.push(Screen.Editor)
                                },
                                trailing = {
                                    if (event.pinned) PinIcon()
                                    if (event.repeats) {
                                        Icon(
                                            Icons.Filled.Refresh, contentDescription = "Repeats yearly",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 8.dp).size(20.dp),
                                        )
                                    }
                                    IconButton(onClick = { pendingDelete = event }) {
                                        Icon(
                                            Icons.Filled.Delete, contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                },
                            )
                            if (idx < sorted.lastIndex) RowDivider()
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

/** Orange pin glyph (material-icons-core has no pin icon). */
@Composable
private fun PinIcon() {
    Text("📌", fontSize = 16.sp, color = Color(0xFFFF9500), modifier = Modifier.padding(start = 8.dp))
}

/**
 * Kiosk: the boot receiver can only bring the board back after a reboot when
 * "Display over other apps" has been granted (Android 10+ background-start rule);
 * the night-blackout and arrival-train switches; and Exit Kiosk, which closes the app.
 */
@Composable
private fun KioskSection(settings: SettingsStore) {
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }
    val blackout by settings.nightBlackoutEnabled.collectAsState()
    val train by settings.arrivalTrainEnabled.collectAsState()
    var confirmExit by remember { mutableStateOf(false) }

    // Re-check when we come back from the system settings screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) allowed = Settings.canDrawOverlays(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Section(
        header = "Kiosk",
        footer = (if (allowed) "The board relaunches itself after the tablet reboots. "
        else "Android only lets the board relaunch itself after a reboot when it may display over other apps. Grant it once. ") +
            "Exit Kiosk closes the app; while auto-start is allowed it comes back at the next reboot.",
    ) {
        SettingsRow(
            title = "Auto-start after reboot",
            subtitle = if (allowed) "Allowed" else "Needs \"Display over other apps\"",
            trailing = {
                if (!allowed) {
                    TextButton(onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        )
                        runCatching { context.startActivity(intent) }
                    }) { Text("Allow…") }
                    Spacer(Modifier.width(4.dp))
                }
            },
        )
        RowDivider()
        SettingsRow(
            title = "Night blackout",
            subtitle = "Board goes black 9:00 PM – 6:00 AM (display time zone)",
            trailing = { Switch(checked = blackout, onCheckedChange = { settings.setNightBlackoutEnabled(it) }) },
        )
        RowDivider()
        SettingsRow(
            title = "Arrival train",
            subtitle = "Steam train and whistle every few minutes on an event's day",
            trailing = { Switch(checked = train, onCheckedChange = { settings.setArrivalTrainEnabled(it) }) },
        )
        RowDivider()
        SettingsRow(
            title = "Exit Kiosk",
            subtitle = "Close FamilyCountdown and return to Android",
            titleColor = MaterialTheme.colorScheme.error,
            onClick = { confirmExit = true },
        )
    }
    if (confirmExit) {
        InlineConfirm(
            message = "Close FamilyCountdown?",
            confirmLabel = "Exit",
            onConfirm = {
                confirmExit = false
                (context as? Activity)?.finishAndRemoveTask()
            },
            onCancel = { confirmExit = false },
        )
    }
}
