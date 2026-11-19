package com.silentcalendrer.core

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconcilerTest {

    // ---- fakes -------------------------------------------------------------------------------

    private class FakeSource(var load: MeetingLoad = MeetingLoad.Ok(emptyList())) : MeetingSource {
        var calls = 0
        var lastExcluded: Set<Long> = emptySet()
        override fun load(from: Long, to: Long, excludedCalendars: Set<Long>): MeetingLoad {
            calls++; lastExcluded = excludedCalendars
            return when (val l = load) {
                is MeetingLoad.Ok -> MeetingLoad.Ok(l.meetings.filter { it.calendarId !in excludedCalendars })
                else -> l
            }
        }
        fun meetings(vararg m: Meeting) { load = MeetingLoad.Ok(m.toList()) }
    }

    private class FakeRinger(var mode: RingerMode = RingerMode.NORMAL, var canControl: Boolean = true) : RingerPort {
        /** If set, asking for SILENT leaves the phone in this mode (an OEM that refuses Silent). */
        var silentBecomes: RingerMode? = null
        val history = mutableListOf<RingerMode>()
        override fun current() = mode
        override fun canControl() = canControl
        override fun set(mode: RingerMode): Boolean {
            if (!canControl) return false
            history += mode
            this.mode = if (mode == RingerMode.SILENT) silentBecomes ?: mode else mode
            return true
        }
    }

    private class FakeStore(var state: EngineState = EngineState()) : StatePort {
        override suspend fun read() = state
        override suspend fun write(state: EngineState) { this.state = state }
    }

    private class FakeAlarms : AlarmPort {
        var next: Long? = null
        override fun scheduleAt(time: Long?) { next = time }
    }

    private class FakePlatform(var unlocked: Boolean = true, var serviceAllowed: Boolean = true) : PlatformPort {
        var running = false
        var cannotSilenceNotifications = 0
        var backgroundEnsured = 0
        override val isUserUnlocked get() = unlocked
        override val isEnforcementRunning get() = running
        override fun startEnforcement(until: Long): Boolean { running = serviceAllowed; return serviceAllowed }
        override fun stopEnforcement() { running = false }
        override fun ensureBackgroundWork() { backgroundEnsured++ }
        override fun notifyCannotSilence() { cannotSilenceNotifications++ }
        override fun clearCannotSilence() {}
    }

    private val source = FakeSource()
    private val ringer = FakeRinger()
    private val store = FakeStore()
    private val alarms = FakeAlarms()
    private val platform = FakePlatform()
    private var settings = UserSettings()
    private val engine = Reconciler(source, ringer, store, alarms, platform, { error("tests pass 'now'") }, { settings })

    private suspend fun at(time: Long, trigger: Trigger = Trigger.ALARM) = engine.reconcile(trigger, time)

    // ---- single meeting ----------------------------------------------------------------------

    @Test fun `single meeting - silent at 9_50, still silent at 11_00, ringing at 11_15`() = runTest {
        source.meetings(meeting(1, t(10), t(11)))

        at(t(9, 0), Trigger.APP_OPENED)
        assertEquals(RingerMode.NORMAL, ringer.mode)
        assertEquals(t(9, 50), alarms.next)

        at(t(9, 50))
        assertEquals(RingerMode.SILENT, ringer.mode)
        assertTrue(store.state.controlling)
        assertEquals(t(11, 15), alarms.next)
        assertTrue(platform.running)

        at(t(11, 0), Trigger.PERIODIC)
        assertEquals(RingerMode.SILENT, ringer.mode)

        at(t(11, 15))
        assertEquals(RingerMode.NORMAL, ringer.mode)
        assertFalse(store.state.controlling)
        assertFalse(platform.running)
        assertEquals(null, alarms.next)
    }

    @Test fun `ringing is restored even if the phone was on vibrate before`() = runTest {
        source.meetings(meeting(1, t(10), t(11)))
        ringer.mode = RingerMode.VIBRATE
        at(t(9, 50))
        assertEquals(RingerMode.SILENT, ringer.mode)
        at(t(11, 15))
        assertEquals(RingerMode.NORMAL, ringer.mode)
    }

    // ---- overlapping / consecutive -----------------------------------------------------------

    @Test fun `overlapping meetings - never ring in between, restore at 11_45`() = runTest {
        source.meetings(meeting(1, t(10), t(11)), meeting(2, t(10, 30), t(11, 30)))
        at(t(9, 50))
        assertEquals(t(11, 45), alarms.next)
        at(t(11, 15), Trigger.PERIODIC)
        assertEquals(RingerMode.SILENT, ringer.mode)
        at(t(11, 45))
        assertEquals(RingerMode.NORMAL, ringer.mode)
        assertEquals(listOf(RingerMode.SILENT, RingerMode.NORMAL), ringer.history)
    }

    @Test fun `separate windows switch twice and schedule the next start`() = runTest {
        source.meetings(meeting(1, t(10), t(11)), meeting(2, t(11, 30), t(12)))
        at(t(9, 50))
        at(t(11, 15))
        assertEquals(RingerMode.NORMAL, ringer.mode)
        assertEquals(t(11, 20), alarms.next)
        at(t(11, 20))
        assertEquals(RingerMode.SILENT, ringer.mode)
        assertEquals(t(12, 15), alarms.next)
    }

    // ---- calendar changes --------------------------------------------------------------------

    @Test fun `meeting cancelled during its window restores ringing immediately`() = runTest {
        source.meetings(meeting(1, t(10), t(11)))
        at(t(9, 50))
        source.meetings()
        at(t(10, 5), Trigger.CALENDAR_CHANGED)
        assertEquals(RingerMode.NORMAL, ringer.mode)
        assertFalse(store.state.controlling)
    }

    @Test fun `meeting extended during its window moves the restore time`() = runTest {
        source.meetings(meeting(1, t(10), t(11)))
        at(t(9, 50))
        source.meetings(meeting(1, t(10), t(12)))
        at(t(10, 30), Trigger.CALENDAR_CHANGED)
        assertEquals(t(12, 15), alarms.next)
        at(t(11, 15))
        assertEquals(RingerMode.SILENT, ringer.mode)
    }

    @Test fun `meeting moved earlier into now starts silence on the calendar change`() = runTest {
        source.meetings(meeting(1, t(15), t(16)))
        at(t(10), Trigger.APP_OPENED)
        assertEquals(RingerMode.NORMAL, ringer.mode)
        source.meetings(meeting(1, t(10, 5), t(11)))
        at(t(10, 1), Trigger.CALENDAR_CHANGED)
        assertEquals(RingerMode.SILENT, ringer.mode)
    }

    @Test fun `provider unavailable keeps the previous plan`() = runTest {
        source.meetings(meeting(1, t(10), t(11)))
        at(t(9, 50))
        source.load = MeetingLoad.Unavailable
        at(t(10, 30), Trigger.PERIODIC)
        assertEquals(RingerMode.SILENT, ringer.mode)
        assertTrue(store.state.controlling)
    }

    @Test fun `calendar permission revoked during a window restores ringing`() = runTest {
        source.meetings(meeting(1, t(10), t(11)))
        at(t(9, 50))
        source.load = MeetingLoad.NoPermission
        at(t(10, 30), Trigger.APP_OPENED)
        assertEquals(RingerMode.NORMAL, ringer.mode)
        assertEquals(emptyList<SilentWindow>(), store.state.plan)
    }

    // ---- manual changes ----------------------------------------------------------------------

    @Test fun `manual change - not overridden immediately, silenced again after 2 minutes`() = runTest {
        source.meetings(meeting(1, t(10), t(11)))
        at(t(9, 50))

        ringer.mode = RingerMode.NORMAL // the user turns the ringer on
        at(t(10, 5), Trigger.RINGER_CHANGED)
        assertEquals(RingerMode.NORMAL, ringer.mode)
        assertEquals(t(10, 7), store.state.overrideRecheckAt)
        assertEquals(t(10, 7), alarms.next)

        // Some other trigger inside the 2 minutes must not override the user either.
        at(t(10, 6), Trigger.CALENDAR_CHANGED)
        assertEquals(RingerMode.NORMAL, ringer.mode)

        at(t(10, 7))
        assertEquals(RingerMode.SILENT, ringer.mode)
        assertEquals(0L, store.state.overrideRecheckAt)
        assertEquals(t(11, 15), alarms.next)
    }

    @Test fun `a second manual change restarts the 2-minute wait`() = runTest {
        source.meetings(meeting(1, t(10), t(11)))
        at(t(9, 50))
        ringer.mode = RingerMode.NORMAL
        at(t(10, 5), Trigger.RINGER_CHANGED)
        ringer.mode = RingerMode.VIBRATE
        at(t(10, 6), Trigger.RINGER_CHANGED)
        assertEquals(t(10, 8), store.state.overrideRecheckAt)
        at(t(10, 7))
        assertEquals(RingerMode.VIBRATE, ringer.mode)
        at(t(10, 8))
        assertEquals(RingerMode.SILENT, ringer.mode)
    }

    @Test fun `user switching back to silent cancels the pending recheck`() = runTest {
        source.meetings(meeting(1, t(10), t(11)))
        at(t(9, 50))
        ringer.mode = RingerMode.NORMAL
        at(t(10, 5), Trigger.RINGER_CHANGED)
        ringer.mode = RingerMode.SILENT
        at(t(10, 6), Trigger.RINGER_CHANGED)
        assertEquals(0L, store.state.overrideRecheckAt)
        assertEquals(t(11, 15), alarms.next)
    }

    @Test fun `manual change near the end - window ends first, silence is not re-applied`() = runTest {
        source.meetings(meeting(1, t(10), t(11)))
        at(t(9, 50))
        ringer.mode = RingerMode.VIBRATE
        at(t(11, 14), Trigger.RINGER_CHANGED)
        assertEquals(t(11, 15), alarms.next) // window end comes before the 11:16 recheck
        at(t(11, 15))
        assertEquals(RingerMode.NORMAL, ringer.mode) // always restore Ringing
        at(t(11, 16))
        assertEquals(RingerMode.NORMAL, ringer.mode)
        assertEquals(listOf(RingerMode.SILENT, RingerMode.NORMAL), ringer.history)
    }

    @Test fun `manual change found by polling when the service could not run`() = runTest {
        platform.serviceAllowed = false
        source.meetings(meeting(1, t(10), t(11)))
        at(t(9, 50))
        assertEquals(t(9, 52), alarms.next) // poll every 2 minutes
        ringer.mode = RingerMode.NORMAL
        at(t(9, 52))
        assertEquals(RingerMode.NORMAL, ringer.mode) // detected, not overridden yet
        assertEquals(t(9, 54), store.state.overrideRecheckAt)
        at(t(9, 54))
        assertEquals(RingerMode.SILENT, ringer.mode)
    }

    @Test fun `phone that refuses silent is reported and not fought every 2 minutes`() = runTest {
        ringer.silentBecomes = RingerMode.VIBRATE
        source.meetings(meeting(1, t(10), t(11)))
        at(t(9, 50))
        assertEquals(t(9, 50), store.state.failedWindowStart)
        assertEquals(RingerMode.VIBRATE, store.state.failedReportedMode)
        at(t(9, 55), Trigger.RINGER_CHANGED)
        assertEquals(0L, store.state.overrideRecheckAt)
        at(t(11, 15))
        assertEquals(RingerMode.NORMAL, ringer.mode)
        assertEquals(0L, store.state.failedWindowStart)
    }

    // ---- permissions -------------------------------------------------------------------------

    @Test fun `no DND access - ringer untouched, user notified once per window`() = runTest {
        ringer.canControl = false
        source.meetings(meeting(1, t(10), t(11)))
        at(t(9, 50))
        at(t(10, 0), Trigger.PERIODIC)
        assertEquals(RingerMode.NORMAL, ringer.mode)
        assertEquals(1, platform.cannotSilenceNotifications)
        assertFalse(store.state.controlling)
    }

    @Test fun `DND access granted mid-window starts silence`() = runTest {
        ringer.canControl = false
        source.meetings(meeting(1, t(10), t(11)))
        at(t(9, 50))
        ringer.canControl = true
        at(t(10, 0), Trigger.PERMISSION_CHANGED)
        assertEquals(RingerMode.SILENT, ringer.mode)
    }

    // ---- reboot / process death ----------------------------------------------------------------

    @Test fun `reboot after the window ended - ringing restored before unlock from the saved plan`() = runTest {
        source.meetings(meeting(1, t(10), t(11)))
        at(t(9, 50))
        // Phone was off from 10:30 to 12:00; ringer mode survives reboot as Silent.
        platform.unlocked = false
        source.load = MeetingLoad.Unavailable
        val callsBefore = source.calls
        at(t(12, 0), Trigger.LOCKED_BOOT)
        assertEquals(callsBefore, source.calls) // calendar not touched before unlock
        assertEquals(RingerMode.NORMAL, ringer.mode)
        assertFalse(store.state.controlling)
    }

    @Test fun `reboot during the window - silence kept and end alarm re-armed`() = runTest {
        source.meetings(meeting(1, t(10), t(11)))
        at(t(9, 50))
        alarms.next = null // alarms are lost on reboot
        platform.running = false
        at(t(10, 30), Trigger.BOOT)
        assertEquals(RingerMode.SILENT, ringer.mode)
        assertEquals(t(11, 15), alarms.next)
        assertTrue(platform.running)
    }

    @Test fun `phone off through the whole window - nothing to do afterwards`() = runTest {
        source.meetings(meeting(1, t(10), t(11)))
        at(t(9, 0), Trigger.APP_OPENED)
        at(t(12, 0), Trigger.BOOT)
        assertEquals(emptyList<RingerMode>(), ringer.history)
    }

    @Test fun `locked device does not schedule WorkManager or start the service`() = runTest {
        platform.unlocked = false
        store.state = EngineState(plan = listOf(SilentWindow(t(9, 50), t(11, 15))))
        at(t(10, 0), Trigger.LOCKED_BOOT)
        assertEquals(RingerMode.SILENT, ringer.mode)
        assertEquals(0, platform.backgroundEnsured)
        assertFalse(platform.running)
    }

    // ---- user settings ---------------------------------------------------------------------------

    @Test fun `custom minutes before and after are used`() = runTest {
        settings = UserSettings(leadMinutes = 5, tailMinutes = 30)
        source.meetings(meeting(1, t(10), t(11)))
        at(t(9, 0), Trigger.APP_OPENED)
        assertEquals(t(9, 55), alarms.next)
        at(t(9, 55))
        assertEquals(RingerMode.SILENT, ringer.mode)
        assertEquals(t(11, 30), alarms.next)
        at(t(11, 30))
        assertEquals(RingerMode.NORMAL, ringer.mode)
    }

    @Test fun `zero minutes - silent exactly during the meeting`() = runTest {
        settings = UserSettings(leadMinutes = 0, tailMinutes = 0)
        source.meetings(meeting(1, t(10), t(11)))
        at(t(9, 59))
        assertEquals(RingerMode.NORMAL, ringer.mode)
        at(t(10))
        assertEquals(RingerMode.SILENT, ringer.mode)
        at(t(11))
        assertEquals(RingerMode.NORMAL, ringer.mode)
    }

    @Test fun `changing settings during a window moves the restore time`() = runTest {
        source.meetings(meeting(1, t(10), t(11)))
        at(t(9, 50))
        assertEquals(t(11, 15), alarms.next)
        settings = UserSettings(tailMinutes = 45)
        at(t(10, 30), Trigger.SETTINGS_CHANGED)
        assertEquals(t(11, 45), alarms.next)
    }

    @Test fun `excluded calendar does not silence, and is passed to the source`() = runTest {
        settings = UserSettings(excludedCalendarIds = setOf(7))
        source.meetings(Meeting(1, "Family", t(10), t(11), calendarId = 7), Meeting(2, "Work", t(14), t(15), calendarId = 3))
        at(t(10))
        assertEquals(setOf(7L), source.lastExcluded)
        assertEquals(RingerMode.NORMAL, ringer.mode)
        assertEquals(t(13, 50), alarms.next)
    }

    @Test fun `excluding the calendar of the current meeting restores ringing`() = runTest {
        source.meetings(Meeting(1, "Family", t(10), t(11), calendarId = 7))
        at(t(9, 50))
        assertEquals(RingerMode.SILENT, ringer.mode)
        settings = UserSettings(excludedCalendarIds = setOf(7))
        at(t(10), Trigger.SETTINGS_CHANGED)
        assertEquals(RingerMode.NORMAL, ringer.mode)
    }

    // ---- let it ring ---------------------------------------------------------------------------

    @Test fun `let it ring - rings until the window ends, next meeting silences normally`() = runTest {
        source.meetings(meeting(1, t(10), t(11)), meeting(2, t(14), t(15)))
        at(t(9, 50))
        assertTrue(engine.releaseCurrentWindow(t(10, 0)))
        assertEquals(RingerMode.NORMAL, ringer.mode)
        assertFalse(store.state.controlling)
        assertFalse(platform.running)
        // Other triggers during the window don't silence again.
        at(t(10, 30), Trigger.PERIODIC)
        assertEquals(RingerMode.NORMAL, ringer.mode)
        at(t(11, 15))
        assertEquals(0L, store.state.releasedWindowStart)
        at(t(13, 50))
        assertEquals(RingerMode.SILENT, ringer.mode)
    }

    @Test fun `silence again after let it ring`() = runTest {
        source.meetings(meeting(1, t(10), t(11)))
        at(t(9, 50))
        engine.releaseCurrentWindow(t(10, 0))
        engine.resumeSilencing(t(10, 5))
        assertEquals(RingerMode.SILENT, ringer.mode)
        assertTrue(store.state.controlling)
    }

    @Test fun `let it ring with no active meeting does nothing`() = runTest {
        source.meetings(meeting(1, t(10), t(11)))
        at(t(8))
        assertFalse(engine.releaseCurrentWindow(t(8)))
        assertEquals(0L, store.state.releasedWindowStart)
    }

    @Test fun `running twice gives the same result`() = runTest {
        source.meetings(meeting(1, t(10), t(11)))
        val first = at(t(10, 0))
        val second = at(t(10, 0))
        assertEquals(first, second)
        assertEquals(listOf(RingerMode.SILENT), ringer.history)
    }
}
