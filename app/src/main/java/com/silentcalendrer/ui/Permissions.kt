package com.silentcalendrer.ui

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings

data class PermissionStatus(
    val calendar: Boolean = false,
    val dnd: Boolean = false,
    val exactAlarm: Boolean = false,
    val notifications: Boolean = false,
    val batteryUnrestricted: Boolean = false,
) {
    companion object {
        fun read(context: Context): PermissionStatus = PermissionStatus(
            calendar = context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED,
            dnd = context.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted,
            exactAlarm = context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
            notifications = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
                context.getSystemService(NotificationManager::class.java).areNotificationsEnabled(),
            batteryUnrestricted = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
        )
    }
}

/** Settings screens for the special permissions that can't be requested with a dialog. */
object PermissionIntents {
    fun dndAccess() = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    fun exactAlarm(context: Context) =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))

    fun batterySettings() = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
}
