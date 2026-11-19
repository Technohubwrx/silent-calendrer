package com.silentcalendrer.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.text.format.DateUtils
import com.silentcalendrer.R
import com.silentcalendrer.ui.MainActivity

object Notifications {
    const val ENFORCEMENT_ID = 1
    const val CANNOT_SILENCE_ID = 2
    private const val CHANNEL_ENFORCEMENT = "enforcement"
    private const val CHANNEL_ALERTS = "alerts"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ENFORCEMENT, context.getString(R.string.channel_enforcement), NotificationManager.IMPORTANCE_LOW)
                .apply { description = context.getString(R.string.channel_enforcement_desc); setShowBadge(false) },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERTS, context.getString(R.string.channel_alerts), NotificationManager.IMPORTANCE_HIGH)
                .apply { description = context.getString(R.string.channel_alerts_desc) },
        )
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun enforcement(context: Context, until: Long): Notification {
        val title = if (until > 0) {
            context.getString(R.string.notif_silent_until, DateUtils.formatDateTime(context, until, DateUtils.FORMAT_SHOW_TIME))
        } else {
            context.getString(R.string.app_name)
        }
        return Notification.Builder(context, CHANNEL_ENFORCEMENT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notif_silent_body))
            .setContentIntent(openApp(context))
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_notification),
                    context.getString(R.string.notif_let_it_ring),
                    PendingIntent.getBroadcast(
                        context, 0,
                        Intent(context, ReleaseReceiver::class.java).setAction(ReleaseReceiver.ACTION_LET_IT_RING),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    ),
                ).build(),
            )
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_STATUS)
            .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    fun postCannotSilence(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return
        nm.notify(
            CANNOT_SILENCE_ID,
            Notification.Builder(context, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(context.getString(R.string.notif_cannot_switch_title))
                .setContentText(context.getString(R.string.notif_cannot_switch_body))
                .setContentIntent(openApp(context))
                .setAutoCancel(true)
                .build(),
        )
    }

    fun clearCannotSilence(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(CANNOT_SILENCE_ID)
    }
}
