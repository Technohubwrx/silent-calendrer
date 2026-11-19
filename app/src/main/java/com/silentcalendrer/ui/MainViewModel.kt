package com.silentcalendrer.ui

import android.app.Application
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.silentcalendrer.appContainer
import com.silentcalendrer.calendar.CalendarRepository.CalendarInfo
import com.silentcalendrer.core.EngineState
import com.silentcalendrer.core.Meeting
import com.silentcalendrer.core.MeetingLoad
import com.silentcalendrer.core.RingerMode
import com.silentcalendrer.core.TodaySchedule
import com.silentcalendrer.core.Trigger
import com.silentcalendrer.core.UserSettings
import com.silentcalendrer.ringer.AndroidRinger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

enum class CalendarState { OK, NO_PERMISSION, NO_CALENDARS }

sealed interface Issue {
    data object CalendarOff : Issue
    data object DndOff : Issue
    data object ExactAlarmOff : Issue
    data object NotificationsOff : Issue
    data class SwitchFailed(val reported: RingerMode) : Issue
}

data class UiState(
    val now: Long = System.currentTimeMillis(),
    val ringer: RingerMode = RingerMode.NORMAL,
    val schedule: TodaySchedule = TodaySchedule.EMPTY,
    val calendarState: CalendarState = CalendarState.OK,
    val permissions: PermissionStatus = PermissionStatus(),
    val engine: EngineState = EngineState(),
    val settings: UserSettings = UserSettings(),
    val calendars: List<CalendarInfo> = emptyList(),
    val showOnboarding: Boolean = false,
    val showSettings: Boolean = false,
) {
    /** Ordered by how much they affect the app; the banner shows the first one. */
    val issues: List<Issue>
        get() = buildList {
            if (!permissions.calendar) add(Issue.CalendarOff)
            if (!permissions.dnd) add(Issue.DndOff)
            engine.failedReportedMode?.let { if (engine.failedWindowStart != 0L) add(Issue.SwitchFailed(it)) }
            if (!permissions.exactAlarm) add(Issue.ExactAlarmOff)
            if (!permissions.notifications) add(Issue.NotificationsOff)
        }

    val silentUntil: Long?
        get() = engine.controlledUntil.takeIf { engine.controlling && it > now }

    /** End of the current window if the user chose "Let it ring" for it. */
    val pausedUntil: Long?
        get() = engine.plan.firstOrNull { now in it && it.start == engine.releasedWindowStart }?.end
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val container = app.appContainer
    private val prefs = app.getSharedPreferences("ui", Context.MODE_PRIVATE)

    private val _ui = MutableStateFlow(
        UiState(
            ringer = container.ringer.current(),
            permissions = PermissionStatus.read(app),
            showOnboarding = !prefs.getBoolean(KEY_ONBOARDING_DONE, false),
        ),
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    private var meetings: List<Meeting> = emptyList()
    private var loadedDay: LocalDate? = null
    private var reloadJob: Job? = null
    private var calendarReconcileJob: Job? = null
    private var ringerPollJob: Job? = null
    private var ringerRecheckJob: Job? = null
    private var calendarObserver: ContentObserver? = null

    /**
     * Keeps the mode indicator live while the screen exists, whoever changed the ringer. Some
     * phones send the broadcast before the sound is actually muted/unmuted, so the state is read
     * again shortly after (this was the ~1 minute delay seen on ColorOS).
     */
    private val ringerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refreshRinger()
            ringerRecheckJob?.cancel()
            ringerRecheckJob = viewModelScope.launch {
                for (wait in longArrayOf(300, 700, 1_500, 2_500)) {
                    delay(wait)
                    refreshRinger()
                }
            }
        }
    }

    init {
        val ringerFilter = IntentFilter().apply {
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(AndroidRinger.ACTION_INTERNAL_RINGER_MODE_CHANGED)
            // DND on/off changes whether the ring sound is muted, i.e. the effective mode.
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        app.registerReceiver(ringerReceiver, ringerFilter, Context.RECEIVER_NOT_EXPORTED)
        viewModelScope.launch {
            container.stateStore.states.collect { engine ->
                _ui.update { it.copy(engine = engine) }
                refreshRinger()
            }
        }
        viewModelScope.launch {
            container.settingsStore.settings.collect { s -> _ui.update { it.copy(settings = s) } }
        }
        // When the user changes settings: re-plan right away and refresh the lists.
        viewModelScope.launch {
            container.settingsStore.settings.distinctUntilChanged().drop(1).collect {
                reloadMeetings()
                reconcile(Trigger.SETTINGS_CHANGED)
            }
        }
        // Move meetings between Now / Earlier / Upcoming as time passes, and roll over at midnight.
        viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                delay(60_000 - now % 60_000 + 50)
                if (LocalDate.now() != loadedDay) reloadMeetings() else recompute()
            }
        }
    }

    fun onResume() {
        val permissions = PermissionStatus.read(getApplication())
        _ui.update { it.copy(permissions = permissions, ringer = container.ringer.current()) }
        registerCalendarObserver(permissions.calendar)
        reloadMeetings()
        reconcile(Trigger.APP_OPENED)
        // Safety net for phones that don't broadcast every change: re-read every 2 s while visible.
        ringerPollJob?.cancel()
        ringerPollJob = viewModelScope.launch {
            while (isActive) {
                delay(2_000)
                refreshRinger()
            }
        }
    }

    fun onPause() {
        ringerPollJob?.cancel()
        ringerPollJob = null
    }

    fun refreshPermissions() {
        _ui.update { it.copy(permissions = PermissionStatus.read(getApplication())) }
    }

    fun openOnboarding() = _ui.update { it.copy(showOnboarding = true, showSettings = false) }

    fun finishOnboarding() {
        prefs.edit().putBoolean(KEY_ONBOARDING_DONE, true).apply()
        _ui.update { it.copy(showOnboarding = false) }
        onResume()
    }

    // ---- settings ------------------------------------------------------------------------------

    fun openSettings() {
        _ui.update { it.copy(showSettings = true) }
        viewModelScope.launch(Dispatchers.IO) {
            val calendars = container.calendar.listCalendars()
            _ui.update { it.copy(calendars = calendars) }
        }
    }

    fun closeSettings() = _ui.update { it.copy(showSettings = false) }

    fun setLeadMinutes(minutes: Int) = updateSettings { it.copy(leadMinutes = minutes.coerceIn(0, UserSettings.MAX_MINUTES)) }

    fun setTailMinutes(minutes: Int) = updateSettings { it.copy(tailMinutes = minutes.coerceIn(0, UserSettings.MAX_MINUTES)) }

    fun resetTiming() = updateSettings {
        it.copy(leadMinutes = UserSettings.DEFAULT_LEAD_MINUTES, tailMinutes = UserSettings.DEFAULT_TAIL_MINUTES)
    }

    fun setCalendarIncluded(id: Long, included: Boolean) = updateSettings {
        it.copy(excludedCalendarIds = if (included) it.excludedCalendarIds - id else it.excludedCalendarIds + id)
    }

    private fun updateSettings(transform: (UserSettings) -> UserSettings) {
        viewModelScope.launch { container.settingsStore.update(transform) }
    }

    // ---- let it ring ---------------------------------------------------------------------------

    /** Tap on the mode pill while paused: silence again for the rest of the current window. */
    fun resumeSilencing() {
        viewModelScope.launch(Dispatchers.Default) {
            try { container.reconciler.resumeSilencing() } catch (t: Throwable) { Log.e(TAG, "Resume failed", t) }
        }
    }

    // ---- internals -----------------------------------------------------------------------------

    private fun refreshRinger() {
        val mode = container.ringer.current()
        if (_ui.value.ringer != mode) _ui.update { it.copy(ringer = mode) }
    }

    private fun reconcile(trigger: Trigger) {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                container.reconciler.reconcile(trigger)
            } catch (t: Throwable) {
                Log.e(TAG, "Reconcile failed", t)
            }
        }
    }

    private fun reloadMeetings() {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch(Dispatchers.IO) {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val excluded = container.settingsStore.read().excludedCalendarIds
            val calendarState: CalendarState
            // Load a little into tomorrow so "Upcoming" can show meetings within the next 6 hours.
            val loadUntil = dayEnd + TodaySchedule.UPCOMING_LOOKAHEAD_MS
            meetings = when (val load = container.calendar.load(dayStart, loadUntil, excluded)) {
                is MeetingLoad.Ok -> {
                    calendarState = if (container.calendar.calendarCount() == 0) CalendarState.NO_CALENDARS else CalendarState.OK
                    load.meetings
                }
                MeetingLoad.NoPermission -> { calendarState = CalendarState.NO_PERMISSION; emptyList() }
                MeetingLoad.Unavailable -> { calendarState = _ui.value.calendarState; meetings }
            }
            loadedDay = today
            _ui.update { it.copy(calendarState = calendarState) }
            recompute()
        }
    }

    private fun recompute() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val now = System.currentTimeMillis()
        val schedule = TodaySchedule.build(
            meetings, now,
            dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli(),
            dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        )
        _ui.update { it.copy(now = now, schedule = schedule) }
    }

    /** While the screen is alive, refresh as soon as the calendar provider reports a change. */
    private fun registerCalendarObserver(permitted: Boolean) {
        if (!permitted || calendarObserver != null) return
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reloadMeetings()
                calendarReconcileJob?.cancel()
                calendarReconcileJob = viewModelScope.launch(Dispatchers.Default) {
                    delay(2_000) // let a sync burst settle
                    try { container.reconciler.reconcile(Trigger.CALENDAR_CHANGED) } catch (t: Throwable) { Log.e(TAG, "Reconcile failed", t) }
                }
            }
        }
        try {
            getApplication<Application>().contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
            calendarObserver = observer
        } catch (e: SecurityException) {
            Log.w(TAG, "Calendar observer not allowed", e)
        }
    }

    override fun onCleared() {
        val app = getApplication<Application>()
        try { app.unregisterReceiver(ringerReceiver) } catch (_: IllegalArgumentException) {}
        calendarObserver?.let { app.contentResolver.unregisterContentObserver(it) }
        super.onCleared()
    }

    private companion object {
        const val TAG = "MainViewModel"
        const val KEY_ONBOARDING_DONE = "onboarding_done"
    }
}
