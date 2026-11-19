package com.silentcalendrer

import android.Manifest
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Events
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.silentcalendrer.calendar.CalendarRepository
import com.silentcalendrer.core.HOUR_MS
import com.silentcalendrer.core.MeetingLoad
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Event filtering against the real Calendar Provider on the device. */
@RunWith(AndroidJUnit4::class)
class CalendarRepositoryTest {

    @get:Rule
    val permissions: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val calendar = TestCalendar(context)
    private val repo = CalendarRepository(context)

    @After fun cleanUp() = calendar.deleteAll()

    @Test fun filtersEventsAsSpecified() {
        // Midday tomorrow (UTC) so the all-day event is clearly separate from "now".
        val base = (System.currentTimeMillis() / (24 * HOUR_MS) + 1) * (24 * HOUR_MS) + 12 * HOUR_MS
        val accepted = calendar.addEvent("Accepted", base, base + HOUR_MS, selfStatus = Attendees.ATTENDEE_STATUS_ACCEPTED)
        val tentative = calendar.addEvent("Tentative", base + HOUR_MS, base + 2 * HOUR_MS, selfStatus = Attendees.ATTENDEE_STATUS_TENTATIVE)
        val personal = calendar.addEvent("Personal block", base + 3 * HOUR_MS, base + 4 * HOUR_MS)
        val noEnd = calendar.addEvent("No end", base + 5 * HOUR_MS, base + 5 * HOUR_MS)
        calendar.addEvent("Declined", base, base + HOUR_MS, selfStatus = Attendees.ATTENDEE_STATUS_DECLINED)
        calendar.addEvent("Invited", base, base + HOUR_MS, selfStatus = Attendees.ATTENDEE_STATUS_INVITED)
        calendar.addEvent("Cancelled", base, base + HOUR_MS, status = Events.STATUS_CANCELED)
        val dayStart = base - 12 * HOUR_MS
        calendar.addEvent("All day", dayStart, dayStart + 24 * HOUR_MS, allDay = true)

        val load = repo.load(dayStart, dayStart + 24 * HOUR_MS)
        assertTrue(load is MeetingLoad.Ok)
        val ours = (load as MeetingLoad.Ok).meetings.filter { it.title in TITLES }
        assertEquals(listOf("Accepted", "Tentative", "Personal block", "No end"), ours.map { it.title })
        assertEquals(listOf(accepted, tentative, personal, noEnd), ours.map { it.eventId })
        // An event without a usable end lasts one hour.
        assertEquals(base + 6 * HOUR_MS, ours.last().end)
    }

    @Test fun meetingCrossingMidnightIsReturnedForBothDays() {
        val midnight = (System.currentTimeMillis() / (24 * HOUR_MS) + 2) * (24 * HOUR_MS)
        calendar.addEvent("Late", midnight - HOUR_MS / 2, midnight + HOUR_MS / 2)
        val dayBefore = repo.load(midnight - 24 * HOUR_MS, midnight) as MeetingLoad.Ok
        val dayAfter = repo.load(midnight, midnight + 24 * HOUR_MS) as MeetingLoad.Ok
        assertTrue(dayBefore.meetings.any { it.title == "Late" })
        assertTrue(dayAfter.meetings.any { it.title == "Late" })
    }

    private companion object {
        val TITLES = setOf("Accepted", "Tentative", "Personal block", "No end", "Declined", "Invited", "Cancelled", "All day")
    }
}
