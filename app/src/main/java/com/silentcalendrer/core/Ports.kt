package com.silentcalendrer.core

/** Boundaries between the engine and Android, so the engine can be tested on the JVM. */

fun interface Clock {
    fun now(): Long
}

interface MeetingSource {
    fun load(from: Long, to: Long, excludedCalendars: Set<Long> = emptySet()): MeetingLoad
}

interface RingerPort {
    fun current(): RingerMode
    /** True when the app may switch into and out of Silent (Do Not Disturb access). */
    fun canControl(): Boolean
    /** Returns false if the platform refused the change. */
    fun set(mode: RingerMode): Boolean
}

interface StatePort {
    suspend fun read(): EngineState
    suspend fun write(state: EngineState)
}

interface AlarmPort {
    /** Schedules the single reconcile alarm, replacing any earlier one. Null cancels it. */
    fun scheduleAt(time: Long?)
}

interface PlatformPort {
    /** False before the first unlock after a reboot (only device-protected storage is usable). */
    val isUserUnlocked: Boolean
    val isEnforcementRunning: Boolean
    /** Starts or updates the foreground service. Returns false if Android did not allow it. */
    fun startEnforcement(until: Long): Boolean
    fun stopEnforcement()
    fun ensureBackgroundWork()
    fun notifyCannotSilence()
    fun clearCannotSilence()
}
