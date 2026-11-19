package com.silentcalendrer.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The single place that decides the ringer mode. Every trigger (alarm, reboot, time change,
 * calendar change, periodic work, opening the app, a ringer change, a settings change) calls
 * [reconcile], which makes the phone match the schedule and sets the next alarm. Running it
 * twice gives the same result as running it once, so recovery after reboot or process death
 * needs no special path.
 */
class Reconciler(
    private val meetings: MeetingSource,
    private val ringer: RingerPort,
    private val store: StatePort,
    private val alarms: AlarmPort,
    private val platform: PlatformPort,
    private val clock: Clock,
    private val settings: SettingsPort = SettingsPort { UserSettings() },
) {
    companion object {
        const val LOOKBACK_MS = 24 * HOUR_MS
        const val LOOKAHEAD_MS = 48 * HOUR_MS
        /** Wait this long after a manual change before applying Silent again. */
        const val RECHECK_DELAY_MS = 2 * MINUTE_MS
        /** Fallback check interval while silencing if the foreground service could not start. */
        const val POLL_INTERVAL_MS = 2 * MINUTE_MS
    }

    private val mutex = Mutex()

    /**
     * "Let it ring": stop silencing for the window that is active now, until it ends.
     * Returns false if no window is active.
     */
    suspend fun releaseCurrentWindow(now: Long = clock.now()): Boolean {
        val active = mutex.withLock {
            val s = store.read()
            val active = SilentWindowCalculator.activeAt(s.plan, now) ?: return false
            store.write(s.copy(releasedWindowStart = active.start))
            active
        }
        reconcile(Trigger.USER_RELEASE, now)
        return active.end > now
    }

    /** Undo "Let it ring": silence again for the rest of the current window. */
    suspend fun resumeSilencing(now: Long = clock.now()) {
        mutex.withLock { store.write(store.read().copy(releasedWindowStart = 0)) }
        reconcile(Trigger.USER_RELEASE, now)
    }

    suspend fun reconcile(trigger: Trigger, now: Long = clock.now()): EngineState = mutex.withLock {
        var s = store.read()
        val unlocked = platform.isUserUnlocked
        val prefs = settings.read()

        // 1. Refresh the plan from the calendar when possible; otherwise keep the saved one.
        if (unlocked) {
            when (val load = meetings.load(now - LOOKBACK_MS, now + LOOKAHEAD_MS, prefs.excludedCalendarIds)) {
                is MeetingLoad.Ok -> s = s.copy(
                    plan = SilentWindowCalculator.windows(load.meetings, prefs.leadMs, prefs.tailMs),
                    planComputedAt = now,
                )
                MeetingLoad.NoPermission -> s = s.copy(plan = emptyList(), planComputedAt = now)
                MeetingLoad.Unavailable -> Unit
            }
        }

        // 2. Apply the plan.
        val active = SilentWindowCalculator.activeAt(s.plan, now)
        val released = active != null && s.releasedWindowStart == active.start
        val canControl = ringer.canControl()

        if (active != null && canControl && !released) {
            platform.clearCannotSilence()
            val current = ringer.current()
            val refusedThisWindow = s.failedWindowStart == active.start
            when {
                !s.controlling -> {
                    // Entering a window (or recovering one after a reboot).
                    s = applySilent(s.copy(controlling = true, overrideRecheckAt = 0), active)
                }
                current == RingerMode.SILENT -> s = s.copy(overrideRecheckAt = 0)
                refusedThisWindow -> Unit // The phone won't stay Silent; don't fight it every 2 minutes.
                trigger == Trigger.RINGER_CHANGED -> {
                    // The user just left Silent: wait 2 minutes from this (latest) change.
                    s = s.copy(overrideRecheckAt = now + RECHECK_DELAY_MS)
                }
                s.overrideRecheckAt == 0L -> {
                    // A manual change noticed by a check rather than the live receiver.
                    s = s.copy(overrideRecheckAt = now + RECHECK_DELAY_MS)
                }
                now >= s.overrideRecheckAt -> {
                    // Two minutes passed and the window is still active: silence again.
                    s = applySilent(s.copy(overrideRecheckAt = 0), active)
                }
                else -> Unit // Still within the 2-minute grace period.
            }
            s = s.copy(controlledUntil = active.end)
            if (unlocked) platform.startEnforcement(active.end)
        } else {
            when {
                active == null || released -> platform.clearCannotSilence()
                // A meeting window is active but the app has no Do Not Disturb access.
                s.dndWarnedWindowStart != active.start -> {
                    platform.notifyCannotSilence()
                    s = s.copy(dndWarnedWindowStart = active.start)
                }
            }
            if (s.controlling && canControl && (active == null || released)) {
                // Window over (or released by the user): always restore Ringing. Sent even if the
                // phone already seems to ring: the reported mode can still be Silent with Do Not
                // Disturb on (seen on ColorOS).
                ringer.set(RingerMode.NORMAL)
            }
            if (s.controlling) {
                s = s.copy(controlling = false, controlledUntil = 0, overrideRecheckAt = 0)
            }
            if (active == null) s = s.copy(failedWindowStart = 0, failedReportedMode = null, releasedWindowStart = 0)
            platform.stopEnforcement()
        }

        // 3. One alarm for the next moment something has to happen.
        val pollAt = if (active != null && s.controlling && !platform.isEnforcementRunning) now + POLL_INTERVAL_MS else null
        val next = listOfNotNull(
            SilentWindowCalculator.nextStartAfter(s.plan, now),
            active?.end,
            s.overrideRecheckAt.takeIf { it > now },
            pollAt,
        ).minOrNull()
        alarms.scheduleAt(next)

        if (unlocked) platform.ensureBackgroundWork()
        store.write(s)
        s
    }

    private fun applySilent(state: EngineState, window: SilentWindow): EngineState {
        ringer.set(RingerMode.SILENT)
        val after = ringer.current()
        return if (after == RingerMode.SILENT) {
            state.copy(failedWindowStart = 0, failedReportedMode = null)
        } else {
            state.copy(failedWindowStart = window.start, failedReportedMode = after)
        }
    }
}
