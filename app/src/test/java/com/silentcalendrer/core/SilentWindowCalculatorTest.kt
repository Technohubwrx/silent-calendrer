package com.silentcalendrer.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SilentWindowCalculatorTest {

    private fun windows(vararg m: Meeting) = SilentWindowCalculator.windows(m.toList())

    @Test fun `single meeting is silent from 10 min before to 15 min after`() {
        assertEquals(listOf(SilentWindow(t(9, 50), t(11, 15))), windows(meeting(1, t(10), t(11))))
    }

    @Test fun `overlapping meetings merge into one window`() {
        // Spec example: A 10:00-11:00, B 10:30-11:30 -> silent 9:50, restore 11:45.
        assertEquals(
            listOf(SilentWindow(t(9, 50), t(11, 45))),
            windows(meeting(1, t(10), t(11)), meeting(2, t(10, 30), t(11, 30))),
        )
    }

    @Test fun `meeting nested inside another keeps the outer end`() {
        assertEquals(
            listOf(SilentWindow(t(8, 50), t(12, 15))),
            windows(meeting(1, t(9), t(12)), meeting(2, t(10), t(10, 30))),
        )
    }

    @Test fun `close consecutive meetings whose windows overlap merge`() {
        // A ends 11:00 (restore 11:15); B starts 11:20 (silent 11:10) -> overlap, no ringing between.
        assertEquals(
            listOf(SilentWindow(t(9, 50), t(12, 15))),
            windows(meeting(1, t(10), t(11)), meeting(2, t(11, 20), t(12))),
        )
    }

    @Test fun `windows that exactly touch merge`() {
        // A restore at 11:15, B silent at 11:15.
        assertEquals(
            listOf(SilentWindow(t(9, 50), t(12, 15))),
            windows(meeting(1, t(10), t(11)), meeting(2, t(11, 25), t(12))),
        )
    }

    @Test fun `meetings with a real gap stay separate`() {
        assertEquals(
            listOf(SilentWindow(t(9, 50), t(11, 15)), SilentWindow(t(11, 20), t(12, 15))),
            windows(meeting(1, t(10), t(11)), meeting(2, t(11, 30), t(12))),
        )
    }

    @Test fun `unsorted input and chains merge correctly`() {
        assertEquals(
            listOf(SilentWindow(t(8, 50), t(12, 15))),
            windows(meeting(3, t(11), t(12)), meeting(1, t(9), t(10)), meeting(2, t(10, 10), t(11))),
        )
    }

    @Test fun `meeting crossing midnight`() {
        assertEquals(
            listOf(SilentWindow(t(23, 20), t(24, 45))),
            windows(meeting(1, t(23, 30), t(24, 30))),
        )
    }

    @Test fun `custom minutes`() {
        assertEquals(
            listOf(SilentWindow(t(9, 30), t(11, 5))),
            SilentWindowCalculator.windows(listOf(meeting(1, t(10), t(11))), 30 * MINUTE_MS, 5 * MINUTE_MS),
        )
    }

    @Test fun `zero minutes - consecutive meetings still merge, gaps stay`() {
        val w = SilentWindowCalculator.windows(
            listOf(meeting(1, t(10), t(11)), meeting(2, t(11), t(12)), meeting(3, t(12, 30), t(13))), 0, 0,
        )
        assertEquals(listOf(SilentWindow(t(10), t(12)), SilentWindow(t(12, 30), t(13))), w)
    }

    @Test fun `no meetings means no windows`() {
        assertEquals(emptyList<SilentWindow>(), windows())
    }

    @Test fun `active window and next start`() {
        val w = windows(meeting(1, t(10), t(11)), meeting(2, t(14), t(15)))
        assertNull(SilentWindowCalculator.activeAt(w, t(9, 49)))
        assertEquals(w[0], SilentWindowCalculator.activeAt(w, t(9, 50)))
        assertEquals(w[0], SilentWindowCalculator.activeAt(w, t(11, 14, 59)))
        assertNull(SilentWindowCalculator.activeAt(w, t(11, 15)))
        assertEquals(t(13, 50), SilentWindowCalculator.nextStartAfter(w, t(11, 15)))
        assertNull(SilentWindowCalculator.nextStartAfter(w, t(13, 50)))
    }
}
