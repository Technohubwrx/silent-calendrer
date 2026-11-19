package com.silentcalendrer.core

import kotlinx.serialization.Serializable

/**
 * Everything the engine needs to recover after the process is killed or the phone restarts.
 * Contains only times and flags (no meeting titles), so it can live in device-protected storage
 * and be read before the first unlock.
 */
@Serializable
data class EngineState(
    /** Merged silent windows for roughly the last day and the next two days. */
    val plan: List<SilentWindow> = emptyList(),
    val planComputedAt: Long = 0,
    /** True while the app has put the phone in Silent for a window; Ringing is restored at its end. */
    val controlling: Boolean = false,
    val controlledUntil: Long = 0,
    /** When > 0: the user left Silent manually, re-check at this time (2 minutes after the change). */
    val overrideRecheckAt: Long = 0,
    /** Start of the window in which the phone refused to switch to Silent (0 = none). */
    val failedWindowStart: Long = 0,
    /** Ringer mode the phone reported after the refused switch. */
    val failedReportedMode: RingerMode? = null,
    /** Start of the window for which the "no DND access" notification was already shown. */
    val dndWarnedWindowStart: Long = 0,
    /** Start of the window the user chose to let ring through ("Let it ring"); 0 = none. */
    val releasedWindowStart: Long = 0,
)
