package com.silentcalendrer.background

import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log
import com.silentcalendrer.core.PlatformPort

class AndroidPlatform(context: Context) : PlatformPort {

    private val context = context.applicationContext

    override val isUserUnlocked: Boolean
        get() = context.getSystemService(UserManager::class.java).isUserUnlocked

    override val isEnforcementRunning: Boolean
        get() = EnforcementService.isRunning

    override fun startEnforcement(until: Long): Boolean {
        val intent = Intent(context, EnforcementService::class.java).putExtra(EnforcementService.EXTRA_UNTIL, until)
        EnforcementService.wanted = true
        return try {
            // A running foreground service may be updated with startService; a new one needs
            // startForegroundService, which Android allows from an exact alarm, boot, or the UI,
            // but not from a background worker (the engine then falls back to polling).
            if (EnforcementService.isRunning) context.startService(intent) else context.startForegroundService(intent)
            true
        } catch (e: Exception) {
            Log.i("AndroidPlatform", "Foreground service not allowed now: ${e.javaClass.simpleName}")
            false
        }
    }

    override fun stopEnforcement() {
        // Write the flag first, then check isForeground (the service does the reverse), so a start
        // that is still in flight always sees the stop request and stops itself after
        // startForeground(). Stopping it from here before that point would crash the app.
        EnforcementService.wanted = false
        if (EnforcementService.isForeground) context.stopService(Intent(context, EnforcementService::class.java))
    }

    override fun ensureBackgroundWork() {
        try {
            BackgroundWork.ensure(context)
        } catch (e: Exception) {
            Log.w("AndroidPlatform", "Could not schedule background work", e)
        }
    }

    override fun notifyCannotSilence() = Notifications.postCannotSilence(context)

    override fun clearCannotSilence() = Notifications.clearCannotSilence(context)
}
