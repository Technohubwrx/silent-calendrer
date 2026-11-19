package com.silentcalendrer.core

/**
 * Turns meetings into silent windows: from `leadMs` before the start to `tailMs` after the end
 * (user settings; defaults [LEAD_MS] and [TAIL_MS]).
 * Windows that overlap or touch are merged, so the phone never rings between them.
 */
object SilentWindowCalculator {

    const val LEAD_MS = 10 * MINUTE_MS
    const val TAIL_MS = 15 * MINUTE_MS

    fun windowFor(meeting: Meeting, leadMs: Long = LEAD_MS, tailMs: Long = TAIL_MS) =
        SilentWindow(meeting.begin - leadMs, meeting.end + tailMs)

    fun windows(meetings: List<Meeting>, leadMs: Long = LEAD_MS, tailMs: Long = TAIL_MS): List<SilentWindow> {
        val sorted = meetings.map { windowFor(it, leadMs, tailMs) }.filter { it.end > it.start }.sortedBy { it.start }
        val merged = ArrayList<SilentWindow>(sorted.size)
        for (w in sorted) {
            val last = merged.lastOrNull()
            if (last != null && w.start <= last.end) {
                merged[merged.lastIndex] = SilentWindow(last.start, maxOf(last.end, w.end))
            } else {
                merged += w
            }
        }
        return merged
    }

    fun activeAt(windows: List<SilentWindow>, now: Long): SilentWindow? = windows.firstOrNull { now in it }

    fun nextStartAfter(windows: List<SilentWindow>, now: Long): Long? =
        windows.firstOrNull { it.start > now }?.start
}
