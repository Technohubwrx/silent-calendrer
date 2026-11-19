package com.silentcalendrer.core

/**
 * Decides which calendar events count as meetings.
 *
 * Included: accepted and tentative meetings, and events with no attendee data
 * (ATTENDEE_STATUS_NONE: personal blocks or events the user organised).
 * Excluded: all-day events, cancelled events, declined meetings and unanswered invitations.
 * Events marked "Free" are included.
 */
object EventFilter {

    /** Duration assumed for events that have no end time. */
    const val DEFAULT_DURATION_MS = HOUR_MS

    fun isIncluded(event: RawEvent): Boolean {
        if (event.allDay) return false
        if (event.status == EventStatus.CANCELED) return false
        return when (event.selfStatus) {
            AttendeeStatus.ACCEPTED,
            AttendeeStatus.TENTATIVE,
            AttendeeStatus.NONE -> true
            AttendeeStatus.DECLINED,
            AttendeeStatus.INVITED -> false
            // A value the platform added later: better silent in a meeting than ringing.
            AttendeeStatus.UNKNOWN -> true
        }
    }

    fun effectiveEnd(event: RawEvent): Long =
        event.end?.takeIf { it > event.begin } ?: (event.begin + DEFAULT_DURATION_MS)

    /**
     * Filters (including calendars the user switched off), normalises and de-duplicates events.
     * The same meeting synced through two accounts (same title, start and end) is shown once.
     */
    fun toMeetings(events: List<RawEvent>, excludedCalendars: Set<Long> = emptySet()): List<Meeting> =
        events.asSequence()
            .filter { it.calendarId !in excludedCalendars }
            .filter(::isIncluded)
            .map { Meeting(it.eventId, it.title?.trim().orEmpty(), it.begin, effectiveEnd(it), it.calendarId) }
            .distinctBy { Triple(it.title.lowercase(), it.begin, it.end) }
            .sortedWith(compareBy<Meeting>({ it.begin }, { it.end }, { it.title }))
            .toList()
}
