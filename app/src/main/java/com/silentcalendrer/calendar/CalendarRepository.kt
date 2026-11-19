package com.silentcalendrer.calendar

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.os.UserManager
import android.provider.CalendarContract
import android.provider.CalendarContract.Instances
import android.util.Log
import com.silentcalendrer.core.AttendeeStatus
import com.silentcalendrer.core.EventFilter
import com.silentcalendrer.core.EventStatus
import com.silentcalendrer.core.MeetingLoad
import com.silentcalendrer.core.MeetingSource
import com.silentcalendrer.core.RawEvent

/**
 * Reads event instances from the Android Calendar Provider. This covers every calendar synced to
 * the device (including Google accounts) without any network access or OAuth.
 */
class CalendarRepository(context: Context) : MeetingSource {

    private val context = context.applicationContext

    fun hasPermission(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    override fun load(from: Long, to: Long, excludedCalendars: Set<Long>): MeetingLoad {
        if (!hasPermission()) return MeetingLoad.NoPermission
        if (!context.getSystemService(UserManager::class.java).isUserUnlocked) return MeetingLoad.Unavailable
        return try {
            MeetingLoad.Ok(EventFilter.toMeetings(queryInstances(from, to), excludedCalendars))
        } catch (e: SecurityException) {
            MeetingLoad.NoPermission
        } catch (e: Exception) {
            Log.w(TAG, "Calendar query failed", e)
            MeetingLoad.Unavailable
        }
    }

    /** A calendar the user can switch on or off in settings. */
    data class CalendarInfo(val id: Long, val name: String, val account: String, val color: Int)

    /** Visible, synced calendars on the device, sorted by account then name. */
    fun listCalendars(): List<CalendarInfo> {
        if (!hasPermission()) return emptyList()
        return try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.ACCOUNT_NAME,
                    CalendarContract.Calendars.CALENDAR_COLOR,
                ),
                "${CalendarContract.Calendars.VISIBLE} = 1 AND ${CalendarContract.Calendars.SYNC_EVENTS} = 1",
                null, null,
            )?.use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add(CalendarInfo(c.getLong(0), c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getInt(3)))
                    }
                }
            }.orEmpty().sortedWith(compareBy({ it.account.lowercase() }, { it.name.lowercase() }))
        } catch (e: Exception) {
            Log.w(TAG, "Calendar list failed", e)
            emptyList()
        }
    }

    /** Number of visible, synced calendars, or null if it can't be read. */
    fun calendarCount(): Int? {
        if (!hasPermission()) return null
        return try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID),
                "${CalendarContract.Calendars.VISIBLE} = 1 AND ${CalendarContract.Calendars.SYNC_EVENTS} = 1",
                null, null,
            )?.use { it.count }
        } catch (e: Exception) {
            null
        }
    }

    /** Instances (not Events) so recurring meetings and their exceptions are expanded correctly. */
    private fun queryInstances(from: Long, to: Long): List<RawEvent> {
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, from)
            ContentUris.appendId(it, to)
        }.build()
        val projection = arrayOf(
            Instances.EVENT_ID,
            Instances.TITLE,
            Instances.BEGIN,
            Instances.END,
            Instances.ALL_DAY,
            Instances.STATUS,
            Instances.SELF_ATTENDEE_STATUS,
            Instances.CALENDAR_ID,
        )
        val selection = "${Instances.VISIBLE} = 1 AND ${Instances.SYNC_EVENTS} = 1"
        val cursor = context.contentResolver.query(uri, projection, selection, null, "${Instances.BEGIN} ASC")
            ?: throw IllegalStateException("Calendar provider returned no cursor")
        return cursor.use { c ->
            val out = ArrayList<RawEvent>(c.count)
            while (c.moveToNext()) {
                out += RawEvent(
                    eventId = c.getLong(0),
                    title = if (c.isNull(1)) null else c.getString(1),
                    begin = c.getLong(2),
                    end = if (c.isNull(3)) null else c.getLong(3),
                    allDay = !c.isNull(4) && c.getInt(4) != 0,
                    status = if (c.isNull(5)) EventStatus.UNKNOWN else eventStatus(c.getInt(5)),
                    selfStatus = if (c.isNull(6)) AttendeeStatus.NONE else attendeeStatus(c.getInt(6)),
                    calendarId = c.getLong(7),
                )
            }
            out
        }
    }

    companion object {
        private const val TAG = "CalendarRepository"

        fun eventStatus(value: Int): EventStatus = when (value) {
            CalendarContract.Events.STATUS_TENTATIVE -> EventStatus.TENTATIVE
            CalendarContract.Events.STATUS_CONFIRMED -> EventStatus.CONFIRMED
            CalendarContract.Events.STATUS_CANCELED -> EventStatus.CANCELED
            else -> EventStatus.UNKNOWN
        }

        fun attendeeStatus(value: Int): AttendeeStatus = when (value) {
            CalendarContract.Attendees.ATTENDEE_STATUS_NONE -> AttendeeStatus.NONE
            CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED -> AttendeeStatus.ACCEPTED
            CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED -> AttendeeStatus.DECLINED
            CalendarContract.Attendees.ATTENDEE_STATUS_INVITED -> AttendeeStatus.INVITED
            CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE -> AttendeeStatus.TENTATIVE
            else -> AttendeeStatus.UNKNOWN
        }
    }
}
