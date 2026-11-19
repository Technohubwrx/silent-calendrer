package com.silentcalendrer.core

/** Test times: an arbitrary fixed day; t(10, 30) is 10:30 on that day. */
const val DAY0 = 1_800_000_000_000L - (1_800_000_000_000L % (24 * HOUR_MS))

fun t(hour: Int, minute: Int = 0, second: Int = 0): Long =
    DAY0 + hour * HOUR_MS + minute * MINUTE_MS + second * 1000L

fun meeting(id: Long, from: Long, to: Long, title: String = "M$id") = Meeting(id, title, from, to)
