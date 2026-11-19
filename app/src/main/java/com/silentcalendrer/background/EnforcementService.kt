package com.silentcalendrer.background

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.IBinder
import android.util.Log
import com.silentcalendrer.appContainer
import com.silentcalendrer.core.Trigger
import com.silentcalendrer.ringer.AndroidRinger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Runs only while the app keeps the phone silent for a meeting. Its job is to notice manual
 * ringer changes right away: RINGER_MODE_CHANGED is not delivered to manifest receivers, only to
 * receivers registered by a running process. The engine then waits 2 minutes before re-applying
 * Silent. The notification also tells the user until when the phone stays silent.
 */
class EnforcementService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val ringerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // The broadcast is sticky: ignore the copy delivered at registration time.
            if (isInitialStickyBroadcast) return
            // Both actions can fire for one change; the engine's mutex and the 2-minute timer make
            // a duplicate harmless.
            scope.launch { reconcile(Trigger.RINGER_CHANGED) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        val filter = IntentFilter().apply {
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            // The quick-settings tile on some phones changes only the internal mode.
            addAction(AndroidRinger.ACTION_INTERNAL_RINGER_MODE_CHANGED)
        }
        registerReceiver(ringerReceiver, filter, RECEIVER_NOT_EXPORTED)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val until = intent?.getLongExtra(EXTRA_UNTIL, 0L) ?: 0L
        if (intent == null) wanted = true // Restarted by the system: keep it until the engine decides.
        try {
            // Android requires startForeground() after startForegroundService(), even if the
            // service is no longer wanted; stopping before this point crashes the app.
            startForeground(
                Notifications.ENFORCEMENT_ID,
                Notifications.enforcement(this, until),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
            isForeground = true
        } catch (e: Exception) {
            // E.g. restarted by the system in a state where a foreground service isn't allowed.
            Log.w(TAG, "Could not enter the foreground", e)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!wanted) {
            // The engine asked to stop while this start was still in flight.
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent == null) {
            // Restarted after the process was killed: let the engine decide.
            scope.launch { reconcile(Trigger.APP_OPENED) }
        }
        return START_STICKY
    }

    private suspend fun reconcile(trigger: Trigger) {
        try {
            applicationContext.appContainer.reconciler.reconcile(trigger)
        } catch (t: Throwable) {
            Log.e(TAG, "Reconcile failed", t)
        }
    }

    override fun onDestroy() {
        isRunning = false
        isForeground = false
        try { unregisterReceiver(ringerReceiver) } catch (_: IllegalArgumentException) {}
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "EnforcementService"
        const val EXTRA_UNTIL = "until"

        @Volatile
        var isRunning: Boolean = false
            private set

        /** True once startForeground() succeeded; only then may the service be stopped from outside. */
        @Volatile
        var isForeground: Boolean = false
            private set

        /** What the engine currently wants. Checked by the service right after startForeground(). */
        @Volatile
        var wanted: Boolean = false
    }
}
