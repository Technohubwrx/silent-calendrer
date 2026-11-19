package com.silentcalendrer.core

/** The meetings shown in the three sections of the main screen. */
data class TodaySchedule(
    val current: List<Meeting>,
    val past: List<Meeting>,
    val upcoming: List<Meeting>,
) {
    companion object {
        val EMPTY = TodaySchedule(emptyList(), emptyList(), emptyList())

        /** Meetings from the next day are listed as upcoming if they start within this time. */
        const val UPCOMING_LOOKAHEAD_MS = 6 * HOUR_MS

        /**
         * [dayStart, dayEnd) is the local day. A meeting that crosses midnight belongs to every
         * day it overlaps. "Upcoming" holds all of today's remaining meetings plus later ones that
         * start within [UPCOMING_LOOKAHEAD_MS] from now (e.g. tomorrow just after midnight).
         */
        fun build(meetings: List<Meeting>, now: Long, dayStart: Long, dayEnd: Long): TodaySchedule {
            val today = meetings.filter { it.end > dayStart && it.begin < dayEnd }
            val upcomingLimit = maxOf(dayEnd, now + UPCOMING_LOOKAHEAD_MS)
            return TodaySchedule(
                current = today.filter { it.begin <= now && now < it.end }.sortedBy { it.end },
                past = today.filter { it.end <= now }.sortedBy { it.begin },
                upcoming = meetings.filter { it.begin > now && it.begin < upcomingLimit }.sortedBy { it.begin },
            )
        }
    }
}
