package com.silentcalendrer

import android.Manifest
import android.app.NotificationManager
import android.media.AudioManager
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.silentcalendrer.core.MINUTE_MS
import com.silentcalendrer.core.Trigger
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end on a device: real Calendar Provider, real ringer, real alarms.
 * Needs Do Not Disturb access and exact alarms granted beforehand:
 *   adb shell cmd notification allow_dnd com.silentcalendrer
 *   adb shell appops set com.silentcalendrer SCHEDULE_EXACT_ALARM allow
 */
@RunWith(AndroidJUnit4::class)
class EngineOnDeviceTest {

    @get:Rule
    val permissions: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR, Manifest.permission.POST_NOTIFICATIONS,
    )

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val container = context.appContainer
    private val audio = context.getSystemService(AudioManager::class.java)
    private lateinit var calendar: TestCalendar

    @Before fun setUp() {
        assumeTrue(
            "Grant DND access first: adb shell cmd notification allow_dnd com.silentcalendrer",
            context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted,
        )
        calendar = TestCalendar(context)
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        reconcile(Trigger.APP_OPENED)
    }

    @After fun tearDown() {
        if (::calendar.isInitialized) {
            calendar.deleteAll()
            reconcile(Trigger.CALENDAR_CHANGED)
        }
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
    }

    private fun reconcile(trigger: Trigger, now: Long = System.currentTimeMillis()) =
        runBlocking { container.reconciler.reconcile(trigger, now) }

    private fun waitForRinger(expected: Int, timeoutMs: Long): Boolean {
        val deadline = SystemClock.elapsedRealtime() + timeoutMs
        while (SystemClock.elapsedRealtime() < deadline) {
            if (audio.ringerMode == expected) return true
            Thread.sleep(500)
        }
        return audio.ringerMode == expected
    }

    @Test fun activeMeetingSilencesAndCancellationRestoresRinging() {
        val now = System.currentTimeMillis()
        val id = calendar.addEvent("Device test meeting", now + 5 * MINUTE_MS, now + 35 * MINUTE_MS)
        val state = reconcile(Trigger.CALENDAR_CHANGED)
        assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)
        assertTrue(state.controlling)

        calendar.deleteEvent(id)
        val after = reconcile(Trigger.CALENDAR_CHANGED)
        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
        assertFalse(after.controlling)
    }

    @Test fun manualChangeIsRespectedForTwoMinutes() {
        val now = System.currentTimeMillis()
        calendar.addEvent("Device test meeting", now + 5 * MINUTE_MS, now + 35 * MINUTE_MS)
        reconcile(Trigger.CALENDAR_CHANGED)
        assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)

        // The user turns the ringer back on.
        audio.ringerMode = AudioManager.RINGER_MODE_NORMAL
        val changedAt = System.currentTimeMillis()
        val state = reconcile(Trigger.RINGER_CHANGED, changedAt)
        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
        assertTrue(state.overrideRecheckAt in changedAt + 2 * MINUTE_MS - 1_000..changedAt + 2 * MINUTE_MS + 5_000)

        // Two minutes later (simulated), still inside the window: Silent again.
        reconcile(Trigger.ALARM, changedAt + 2 * MINUTE_MS + 1_000)
        assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)
    }

    /** Real AlarmManager: the window starts ~60 s from now and the alarm must switch to Silent. */
    @Test fun exactAlarmSwitchesToSilentAtWindowStart() {
        val windowStart = System.currentTimeMillis() + 60_000
        calendar.addEvent("Alarm start test", windowStart + 10 * MINUTE_MS, windowStart + 40 * MINUTE_MS)
        reconcile(Trigger.CALENDAR_CHANGED)
        assertEquals(AudioManager.RINGER_MODE_NORMAL, audio.ringerMode)
        assertTrue("Ringer should switch to Silent within ~75 s", waitForRinger(AudioManager.RINGER_MODE_SILENT, 75_000))
    }

    /** Real AlarmManager: the window ends ~60 s from now and the alarm must restore Ringing. */
    @Test fun exactAlarmRestoresRingingAtWindowEnd() {
        val windowEnd = System.currentTimeMillis() + 60_000
        val meetingEnd = windowEnd - 15 * MINUTE_MS
        calendar.addEvent("Alarm end test", meetingEnd - 30 * MINUTE_MS, meetingEnd)
        reconcile(Trigger.CALENDAR_CHANGED)
        assertEquals(AudioManager.RINGER_MODE_SILENT, audio.ringerMode)
        assertTrue("Ringer should return to Ringing within ~75 s", waitForRinger(AudioManager.RINGER_MODE_NORMAL, 75_000))
    }
}
