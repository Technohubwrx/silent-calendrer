package com.silentcalendrer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventFilterTest {

    private fun raw(
        id: Long = 1,
        title: String? = "Meeting",
        begin: Long = t(10),
        end: Long? = t(11),
        allDay: Boolean = false,
        status: EventStatus = EventStatus.CONFIRMED,
        self: AttendeeStatus = AttendeeStatus.ACCEPTED,
        calendar: Long = 1,
    ) = RawEvent(id, title, begin, end, allDay, status, self, calendar)

    @Test fun `accepted and tentative meetings are included`() {
        assertTrue(EventFilter.isIncluded(raw(self = AttendeeStatus.ACCEPTED)))
        assertTrue(EventFilter.isIncluded(raw(self = AttendeeStatus.TENTATIVE)))
    }

    @Test fun `events without attendee data are included`() {
        assertTrue(EventFilter.isIncluded(raw(self = AttendeeStatus.NONE)))
    }

    @Test fun `declined meetings and unanswered invitations are excluded`() {
        assertFalse(EventFilter.isIncluded(raw(self = AttendeeStatus.DECLINED)))
        assertFalse(EventFilter.isIncluded(raw(self = AttendeeStatus.INVITED)))
    }

    @Test fun `all-day and cancelled events are excluded`() {
        assertFalse(EventFilter.isIncluded(raw(allDay = true)))
        assertFalse(EventFilter.isIncluded(raw(status = EventStatus.CANCELED)))
    }

    @Test fun `tentative event status and unknown status are included`() {
        assertTrue(EventFilter.isIncluded(raw(status = EventStatus.TENTATIVE)))
        assertTrue(EventFilter.isIncluded(raw(status = EventStatus.UNKNOWN)))
    }

    @Test fun `missing or invalid end time becomes one hour`() {
        assertEquals(t(11), EventFilter.effectiveEnd(raw(end = null)))
        assertEquals(t(11), EventFilter.effectiveEnd(raw(end = t(10))))
        assertEquals(t(11), EventFilter.effectiveEnd(raw(end = t(9))))
        assertEquals(t(10, 30), EventFilter.effectiveEnd(raw(end = t(10, 30))))
    }

    @Test fun `meetings from excluded calendars are dropped`() {
        val result = EventFilter.toMeetings(
            listOf(raw(id = 1, calendar = 1), raw(id = 2, title = "Other", calendar = 2)),
            excludedCalendars = setOf(1L),
        )
        assertEquals(listOf(2L), result.map { it.eventId })
        assertEquals(2L, result.single().calendarId)
    }

    @Test fun `duplicate in an excluded calendar does not hide the included copy`() {
        val result = EventFilter.toMeetings(
            listOf(raw(id = 1, title = "Standup", calendar = 1), raw(id = 2, title = "Standup", calendar = 2)),
            excludedCalendars = setOf(1L),
        )
        assertEquals(listOf(2L), result.map { it.eventId })
    }

    @Test fun `same meeting from two accounts is shown once`() {
        val result = EventFilter.toMeetings(
            listOf(
                raw(id = 1, title = "Standup", calendar = 1),
                raw(id = 2, title = " standup ", calendar = 2),
                raw(id = 3, title = "Standup", begin = t(12), end = t(13)),
            ),
        )
        assertEquals(2, result.size)
        assertEquals(listOf(t(10), t(12)), result.map { it.begin })
    }

    @Test fun `toMeetings filters, trims titles and sorts by start`() {
        val result = EventFilter.toMeetings(
            listOf(
                raw(id = 1, title = "  Later ", begin = t(14), end = t(15)),
                raw(id = 2, title = null, begin = t(9), end = null),
                raw(id = 3, title = "Declined", self = AttendeeStatus.DECLINED),
                raw(id = 4, title = "Holiday", allDay = true),
            ),
        )
        assertEquals(listOf(Meeting(2, "", t(9), t(10), 1), Meeting(1, "Later", t(14), t(15), 1)), result)
    }
}
