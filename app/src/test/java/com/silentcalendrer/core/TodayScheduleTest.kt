package com.silentcalendrer.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TodayScheduleTest {

    private val dayStart = t(0)
    private val dayEnd = t(24)

    @Test fun `meetings are split into current, past and upcoming`() {
        val past = meeting(1, t(8), t(9))
        val current = meeting(2, t(9, 30), t(10, 30))
        val upcoming = meeting(3, t(11), t(12))
        val s = TodaySchedule.build(listOf(upcoming, current, past), t(10), dayStart, dayEnd)
        assertEquals(listOf(current), s.current)
        assertEquals(listOf(past), s.past)
        assertEquals(listOf(upcoming), s.upcoming)
    }

    @Test fun `several current meetings are all shown`() {
        val a = meeting(1, t(9), t(11))
        val b = meeting(2, t(9, 30), t(10, 30))
        val s = TodaySchedule.build(listOf(a, b), t(10), dayStart, dayEnd)
        assertEquals(listOf(b, a), s.current) // ordered by end time
    }

    @Test fun `boundaries - a meeting ending now is past, starting now is current`() {
        val ended = meeting(1, t(9), t(10))
        val starting = meeting(2, t(10), t(11))
        val s = TodaySchedule.build(listOf(ended, starting), t(10), dayStart, dayEnd)
        assertEquals(listOf(ended), s.past)
        assertEquals(listOf(starting), s.current)
    }

    @Test fun `meetings crossing midnight belong to both days, other days are excluded`() {
        val fromYesterday = meeting(1, t(-1), t(0, 30))
        val intoTomorrow = meeting(2, t(23, 30), t(24, 30))
        val tomorrow = meeting(3, t(25), t(26))
        val yesterday = meeting(4, t(-3), t(-2))
        val s = TodaySchedule.build(listOf(fromYesterday, intoTomorrow, tomorrow, yesterday), t(12), dayStart, dayEnd)
        assertEquals(listOf(fromYesterday), s.past)
        assertEquals(listOf(intoTomorrow), s.upcoming) // tomorrow 01:00 is 13 h away: not listed
        assertEquals(emptyList<Meeting>(), s.current)
    }

    @Test fun `tomorrow's meetings within 6 hours are upcoming, later ones are not`() {
        val laterToday = meeting(1, t(23, 30), t(23, 45))
        val tomorrowSoon = meeting(2, t(24, 30), t(25))      // 00:30 tomorrow, 2.5 h from 22:00
        val tomorrowEdge = meeting(3, t(27, 59), t(28, 30))  // 03:59, just under 6 h
        val tomorrowLate = meeting(4, t(28), t(29))          // 04:00, exactly 6 h: not listed
        val s = TodaySchedule.build(listOf(tomorrowLate, tomorrowEdge, tomorrowSoon, laterToday), t(22), dayStart, dayEnd)
        assertEquals(listOf(laterToday, tomorrowSoon, tomorrowEdge), s.upcoming)
        assertEquals(emptyList<Meeting>(), s.past)
    }

    @Test fun `all of today's remaining meetings stay listed even beyond 6 hours`() {
        val evening = meeting(1, t(20), t(21))
        val s = TodaySchedule.build(listOf(evening), t(8), dayStart, dayEnd)
        assertEquals(listOf(evening), s.upcoming)
    }
}
