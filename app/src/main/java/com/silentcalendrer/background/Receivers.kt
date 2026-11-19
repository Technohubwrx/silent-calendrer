package com.silentcalendrer.background

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.silentcalendrer.appContainer
import com.silentcalendrer.core.Trigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Runs [block] off the main thread while keeping the broadcast alive (up to ~9 s). */
internal fun BroadcastReceiver.reconcileAsync(context: Context, trigger: Trigger) {
    val pending = goAsync()
    val container = context.appContainer
    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
        try {
            withTimeout(9_000) { container.reconciler.reconcile(trigger) }
        } catch (t: Throwable) {
            Log.e("Receivers", "Reconcile failed for $trigger", t)
        } finally {
            pending.finish()
        }
    }
}

/** Window boundaries and 2-minute rechecks. Direct-boot aware. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_RECONCILE) reconcileAsync(context, Trigger.ALARM)
    }

    companion object {
        const val ACTION_RECONCILE = "com.silentcalendrer.action.RECONCILE"
    }
}

/**
 * Before the first unlock after a reboot. The calendar can't be read yet, so the saved plan is
 * used: an expired silent window is ended (Ringing restored) and the next alarm is set.
 */
class LockedBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) reconcileAsync(context, Trigger.LOCKED_BOOT)
    }
}

/** "Let it ring" on the silence notification: stop silencing until the current window ends. */
class ReleaseReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_LET_IT_RING) return
        val pending = goAsync()
        val container = context.appContainer
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                withTimeout(9_000) { container.reconciler.releaseCurrentWindow() }
            } catch (t: Throwable) {
                Log.e("ReleaseReceiver", "Release failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_LET_IT_RING = "com.silentcalendrer.action.LET_IT_RING"
    }
}

/** Reboot, app update, clock / time-zone changes and permission changes. */
class SystemEventReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val trigger = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> Trigger.BOOT
            Intent.ACTION_MY_PACKAGE_REPLACED -> Trigger.PACKAGE_REPLACED
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> Trigger.TIME_CHANGED
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
            NotificationManager.ACTION_NOTIFICATION_POLICY_ACCESS_GRANTED_CHANGED -> Trigger.PERMISSION_CHANGED
            else -> return
        }
        reconcileAsync(context, trigger)
    }
}
