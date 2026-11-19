package com.silentcalendrer

import android.app.Application
import android.content.Context
import com.silentcalendrer.background.AlarmScheduler
import com.silentcalendrer.background.AndroidPlatform
import com.silentcalendrer.background.Notifications
import com.silentcalendrer.calendar.CalendarRepository
import com.silentcalendrer.core.Clock
import com.silentcalendrer.core.Reconciler
import com.silentcalendrer.data.SettingsStore
import com.silentcalendrer.data.StateStore
import com.silentcalendrer.ringer.AndroidRinger

class SilentCalendrerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // Note: this can run before the first unlock (direct boot). Keep it free of
        // credential-protected storage; everything below is created lazily.
        container = AppContainer(this)
        Notifications.createChannels(this)
    }
}

/** Hand-written dependency container; one instance per process. */
class AppContainer(context: Context) {
    private val app = context.applicationContext

    val stateStore by lazy { StateStore(app) }
    val settingsStore by lazy { SettingsStore(app) }
    val calendar by lazy { CalendarRepository(app) }
    val ringer by lazy { AndroidRinger(app) }
    val alarms by lazy { AlarmScheduler(app) }
    val platform by lazy { AndroidPlatform(app) }
    val reconciler by lazy {
        Reconciler(calendar, ringer, stateStore, alarms, platform, Clock { System.currentTimeMillis() }, settingsStore)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as SilentCalendrerApp).container
