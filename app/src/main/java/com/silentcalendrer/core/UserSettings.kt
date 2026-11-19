package com.silentcalendrer.core

import kotlinx.serialization.Serializable

/** Choices the user makes in the settings screen. One setting for all meetings. */
@Serializable
data class UserSettings(
    /** Minutes before a meeting starts that the phone goes silent. */
    val leadMinutes: Int = DEFAULT_LEAD_MINUTES,
    /** Minutes after a meeting ends that Ringing is restored. */
    val tailMinutes: Int = DEFAULT_TAIL_MINUTES,
    /** Calendars the user switched off. Stored as exclusions so new calendars count by default. */
    val excludedCalendarIds: Set<Long> = emptySet(),
) {
    val leadMs: Long get() = leadMinutes.coerceIn(0, MAX_MINUTES) * MINUTE_MS
    val tailMs: Long get() = tailMinutes.coerceIn(0, MAX_MINUTES) * MINUTE_MS

    companion object {
        const val DEFAULT_LEAD_MINUTES = 10
        const val DEFAULT_TAIL_MINUTES = 15
        const val MAX_MINUTES = 60
    }
}

fun interface SettingsPort {
    suspend fun read(): UserSettings
}
