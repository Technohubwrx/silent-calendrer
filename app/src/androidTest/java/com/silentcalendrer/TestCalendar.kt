package com.silentcalendrer

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events

/**
 * A throw-away local calendar (no account sync) for instrumented tests. The device owner is
 * [OWNER], so an attendee row with that email sets SELF_ATTENDEE_STATUS on the event.
 */
class TestCalendar(private val context: Context) {

    companion object {
        const val ACCOUNT = "silentcalendrer-test"
        const val OWNER = "owner@silentcalendrer.test"
    }

    private val resolver get() = context.contentResolver

    private fun asSyncAdapter(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT)
        .appendQueryParameter(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
        .build()

    val calendarId: Long = run {
        deleteAll()
        val values = ContentValues().apply {
            put(Calendars.ACCOUNT_NAME, ACCOUNT)
            put(Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(Calendars.NAME, "Silent Calendrer test")
            put(Calendars.CALENDAR_DISPLAY_NAME, "Silent Calendrer test")
            put(Calendars.CALENDAR_COLOR, 0xFF0A5BC4.toInt())
            put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER)
            put(Calendars.OWNER_ACCOUNT, OWNER)
            put(Calendars.VISIBLE, 1)
            put(Calendars.SYNC_EVENTS, 1)
            put(Calendars.CALENDAR_TIME_ZONE, "UTC")
        }
        ContentUris.parseId(resolver.insert(asSyncAdapter(Calendars.CONTENT_URI), values)!!)
    }

    fun addEvent(
        title: String,
        begin: Long,
        end: Long,
        allDay: Boolean = false,
        status: Int = Events.STATUS_CONFIRMED,
        selfStatus: Int? = null,
    ): Long {
        val values = ContentValues().apply {
            put(Events.CALENDAR_ID, calendarId)
            put(Events.TITLE, title)
            put(Events.DTSTART, begin)
            put(Events.DTEND, end)
            put(Events.EVENT_TIMEZONE, "UTC")
            put(Events.ALL_DAY, if (allDay) 1 else 0)
            put(Events.STATUS, status)
            if (selfStatus != null) {
                put(Events.HAS_ATTENDEE_DATA, 1)
                put(Events.ORGANIZER, "someone.else@silentcalendrer.test")
            }
        }
        val id = ContentUris.parseId(resolver.insert(asSyncAdapter(Events.CONTENT_URI), values)!!)
        if (selfStatus != null) {
            resolver.insert(
                asSyncAdapter(Attendees.CONTENT_URI),
                ContentValues().apply {
                    put(Attendees.EVENT_ID, id)
                    put(Attendees.ATTENDEE_EMAIL, OWNER)
                    put(Attendees.ATTENDEE_NAME, "Owner")
                    put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ATTENDEE)
                    put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_REQUIRED)
                    put(Attendees.ATTENDEE_STATUS, selfStatus)
                },
            )
        }
        return id
    }

    fun updateEvent(id: Long, begin: Long, end: Long) {
        resolver.update(
            asSyncAdapter(ContentUris.withAppendedId(Events.CONTENT_URI, id)),
            ContentValues().apply { put(Events.DTSTART, begin); put(Events.DTEND, end) },
            null, null,
        )
    }

    fun deleteEvent(id: Long) {
        resolver.delete(asSyncAdapter(ContentUris.withAppendedId(Events.CONTENT_URI, id)), null, null)
    }

    fun deleteAll() {
        resolver.delete(
            asSyncAdapter(Calendars.CONTENT_URI),
            "${Calendars.ACCOUNT_NAME} = ? AND ${Calendars.ACCOUNT_TYPE} = ?",
            arrayOf(ACCOUNT, CalendarContract.ACCOUNT_TYPE_LOCAL),
        )
    }
}
