package com.silentcalendrer.core

import kotlinx.serialization.Serializable

const val MINUTE_MS = 60_000L
const val HOUR_MS = 60 * MINUTE_MS

/** The attendee response of the device owner, as exposed by CalendarContract.SELF_ATTENDEE_STATUS. */
enum class AttendeeStatus { NONE, ACCEPTED, DECLINED, INVITED, TENTATIVE, UNKNOWN }

/** CalendarContract.Events.STATUS. UNKNOWN covers a null column. */
enum class EventStatus { TENTATIVE, CONFIRMED, CANCELED, UNKNOWN }

/** One event instance exactly as read from the Calendar Provider, before filtering. */
data class RawEvent(
    val eventId: Long,
    val title: String?,
    val begin: Long,
    /** May be null or <= begin when the provider has no end for the event. */
    val end: Long?,
    val allDay: Boolean,
    val status: EventStatus,
    val selfStatus: AttendeeStatus,
    val calendarId: Long,
)

/** A meeting that passed the filter, with a guaranteed end time. Times are epoch millis. */
data class Meeting(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val calendarId: Long = 0,
)

/** A period during which the phone should be silent: [start, end). */
@Serializable
data class SilentWindow(val start: Long, val end: Long) {
    operator fun contains(time: Long): Boolean = time >= start && time < end
}

enum class RingerMode { NORMAL, VIBRATE, SILENT }

/** Result of reading meetings from the calendar. */
sealed interface MeetingLoad {
    data class Ok(val meetings: List<Meeting>) : MeetingLoad
    /** READ_CALENDAR is not granted: there are no meetings the app is allowed to know about. */
    data object NoPermission : MeetingLoad
    /** The provider could not be read (locked device, provider error). Keep the previous plan. */
    data object Unavailable : MeetingLoad
}

/** What caused a reconcile run. Only RINGER_CHANGED changes the logic; the rest is for logging. */
enum class Trigger {
    ALARM, BOOT, LOCKED_BOOT, TIME_CHANGED, PACKAGE_REPLACED, PERMISSION_CHANGED,
    CALENDAR_CHANGED, PERIODIC, APP_OPENED, RINGER_CHANGED, SETTINGS_CHANGED, USER_RELEASE,
}
