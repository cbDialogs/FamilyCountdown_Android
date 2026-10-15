package com.dialogs.familycountdown.model

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * User preferences: the display time zone used for day boundaries (ARRIVED /
 * roll-forward), holiday midnights, and the editor's date fields (empty
 * identifier == Automatic, follows the device), the night-blackout switch, and the
 * arrival-train switch.
 */
class SettingsStore(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _timeZoneIdentifier = MutableStateFlow(prefs.getString(KEY_TZ, "") ?: "")
    val timeZoneIdentifier: StateFlow<String> = _timeZoneIdentifier

    fun setTimeZoneIdentifier(id: String) {
        _timeZoneIdentifier.value = id
        prefs.edit().putString(KEY_TZ, id).apply()
    }

    private val _nightBlackoutEnabled = MutableStateFlow(prefs.getBoolean(KEY_BLACKOUT, true))
    /** When on, the board is painted black between [BLACKOUT_START_HOUR] and [BLACKOUT_END_HOUR] (display zone). */
    val nightBlackoutEnabled: StateFlow<Boolean> = _nightBlackoutEnabled

    fun setNightBlackoutEnabled(enabled: Boolean) {
        _nightBlackoutEnabled.value = enabled
        prefs.edit().putBoolean(KEY_BLACKOUT, enabled).apply()
    }

    private val _arrivalTrainEnabled = MutableStateFlow(prefs.getBoolean(KEY_TRAIN, true))
    /** When on, a steam train crosses the board every few minutes while an event shows ARRIVED. */
    val arrivalTrainEnabled: StateFlow<Boolean> = _arrivalTrainEnabled

    fun setArrivalTrainEnabled(enabled: Boolean) {
        _arrivalTrainEnabled.value = enabled
        prefs.edit().putBoolean(KEY_TRAIN, enabled).apply()
    }

    val isAutomatic: Boolean get() = _timeZoneIdentifier.value.isEmpty()

    /** Resolved zone; read freshly each time so a system zone change is picked up. */
    val zone: ZoneId get() = zoneFor(_timeZoneIdentifier.value)

    /** Human-readable label for the current selection. */
    val timeZoneLabel: String get() = if (isAutomatic) "Automatic" else pretty(zone.id)

    companion object {
        private const val KEY_TZ = "timeZoneIdentifier"
        private const val KEY_BLACKOUT = "nightBlackoutEnabled"
        private const val KEY_TRAIN = "arrivalTrainEnabled"

        /** Night blackout window: 9:00 PM up to (not including) 6:00 AM, local to the display zone. */
        const val BLACKOUT_START_HOUR = 21
        const val BLACKOUT_END_HOUR = 6

        /** True when [instant], read in [zone], falls inside the night-blackout window. */
        fun isBlackoutHour(instant: Instant, zone: ZoneId): Boolean {
            val hour = instant.atZone(zone).hour
            return hour >= BLACKOUT_START_HOUR || hour < BLACKOUT_END_HOUR
        }

        fun zoneFor(identifier: String): ZoneId {
            if (identifier.isEmpty()) return ZoneId.systemDefault()
            return runCatching { ZoneId.of(identifier) }.getOrDefault(ZoneId.systemDefault())
        }

        fun pretty(identifier: String): String = identifier.replace('_', ' ')

        private val medium: DateTimeFormatter =
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)

        /** Medium date + short time, rendered in [zone]. */
        fun mediumString(instant: Instant, zone: ZoneId): String = medium.format(instant.atZone(zone))
    }
}
