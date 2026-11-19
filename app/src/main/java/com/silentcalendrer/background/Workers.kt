package com.silentcalendrer.background

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.silentcalendrer.appContainer
import com.silentcalendrer.core.Trigger
import java.time.Duration
import java.util.concurrent.TimeUnit

/**
 * WorkManager jobs:
 * - a periodic refresh every 15 minutes (the minimum; Android runs it inexactly, and later in Doze),
 * - a job triggered by changes to the calendar provider, which runs even when the app process is
 *   dead. Content-trigger jobs fire once, so the worker re-arms itself after each run.
 */
object BackgroundWork {
    private const val PERIODIC = "periodic-refresh"
    private const val CALENDAR_TRIGGER = "calendar-trigger"

    fun ensure(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build(),
        )
        if (hasCalendarPermission(context)) {
            wm.enqueueUniqueWork(CALENDAR_TRIGGER, ExistingWorkPolicy.KEEP, calendarTriggerRequest())
        }
    }

    fun rearmCalendarTrigger(context: Context) {
        if (!hasCalendarPermission(context)) return
        WorkManager.getInstance(context)
            .enqueueUniqueWork(CALENDAR_TRIGGER, ExistingWorkPolicy.REPLACE, calendarTriggerRequest())
    }

    private fun calendarTriggerRequest(): OneTimeWorkRequest =
        OneTimeWorkRequestBuilder<CalendarChangeWorker>()
            .setConstraints(
                Constraints.Builder()
                    .addContentUriTrigger(CalendarContract.CONTENT_URI, true)
                    // Calendar sync writes in bursts; wait for it to settle.
                    .setTriggerContentUpdateDelay(Duration.ofSeconds(5))
                    .setTriggerContentMaxDelay(Duration.ofSeconds(30))
                    .build(),
            )
            .build()

    private fun hasCalendarPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
}

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        applicationContext.appContainer.reconciler.reconcile(Trigger.PERIODIC)
        return Result.success()
    }
}

class CalendarChangeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        applicationContext.appContainer.reconciler.reconcile(Trigger.CALENDAR_CHANGED)
        // Last step: re-arm the one-shot trigger (this replaces the finished run).
        BackgroundWork.rearmCalendarTrigger(applicationContext)
        return Result.success()
    }
}
