package com.silentcalendrer.background

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.silentcalendrer.core.AlarmPort

/**
 * Keeps exactly one alarm: the next moment the engine has to act (window start, window end,
 * 2-minute recheck). Uses exact alarms when the user allowed them; otherwise falls back to an
 * inexact alarm that Android may deliver several minutes late.
 */
class AlarmScheduler(context: Context) : AlarmPort {

    private val context = context.applicationContext
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    fun exactAlarmsAllowed(): Boolean = alarmManager.canScheduleExactAlarms()

    override fun scheduleAt(time: Long?) {
        if (time == null) {
            pendingIntent(PendingIntent.FLAG_NO_CREATE)?.let {
                alarmManager.cancel(it)
                it.cancel()
            }
            return
        }
        val pi = pendingIntent(PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        try {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
                return
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Exact alarm refused, using inexact alarm", e)
        }
        alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
    }

    private fun pendingIntent(flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_RECONCILE),
        PendingIntent.FLAG_IMMUTABLE or flags,
    )

    private companion object {
        const val TAG = "AlarmScheduler"
        const val REQUEST_CODE = 1
    }
}
