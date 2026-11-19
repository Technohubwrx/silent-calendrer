package com.silentcalendrer.ringer

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.util.Log
import com.silentcalendrer.core.RingerMode
import com.silentcalendrer.core.RingerPort

/**
 * Reads and sets the ringer mode. Switching into or out of Silent needs Do Not Disturb
 * (notification policy) access.
 *
 * Android keeps two ringer modes: the "external" one that AudioManager.getRingerMode() returns to
 * apps, and the "internal" one the phone actually uses. They usually agree, but not always. On
 * ColorOS 16, the app sets Silent, the user taps the ringer tile (internal → Normal) and
 * getRingerMode() still says Silent. A second setRingerMode(SILENT) is then a no-op. So this class
 * judges Silent by whether the ring sound is really muted, and forces a real transition when a
 * plain request has no effect.
 */
class AndroidRinger(context: Context) : RingerPort {

    private val resolver = context.applicationContext.contentResolver
    private val audio = context.getSystemService(AudioManager::class.java)
    private val notifications = context.getSystemService(NotificationManager::class.java)

    /** The mode the user actually experiences. */
    override fun current(): RingerMode {
        val external = fromPlatform(audio.ringerMode)
        if (external != RingerMode.SILENT) return external
        // Reported Silent: trust it only if the ring sound is really muted (by ringer mode or DND).
        if (audio.isStreamMute(AudioManager.STREAM_RING)) return RingerMode.SILENT
        return when (val internal = internalMode()) {
            null, RingerMode.SILENT -> RingerMode.NORMAL
            else -> internal
        }
    }

    override fun canControl(): Boolean = notifications.isNotificationPolicyAccessGranted

    override fun set(mode: RingerMode): Boolean = try {
        request(mode)
        if (current() != mode) {
            // The request didn't take effect (Android saw no change in the reported mode).
            // Force a real transition through another mode, then ask again.
            Log.i(TAG, "Ringer still ${current()} after asking for $mode; forcing a transition")
            request(if (mode == RingerMode.NORMAL) RingerMode.SILENT else RingerMode.NORMAL)
            request(mode)
        }
        true
    } catch (e: SecurityException) {
        Log.w(TAG, "Not allowed to set ringer mode $mode", e)
        false
    }

    private fun request(mode: RingerMode) {
        audio.ringerMode = when (mode) {
            RingerMode.NORMAL -> AudioManager.RINGER_MODE_NORMAL
            RingerMode.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
            RingerMode.SILENT -> AudioManager.RINGER_MODE_SILENT
        }
        // Muting is applied by the audio service right after the call; give it a moment.
        Thread.sleep(SETTLE_MS)
    }

    /** The phone's internal ringer mode, persisted by the audio service (null if unreadable). */
    private fun internalMode(): RingerMode? = try {
        fromPlatform(Settings.Global.getInt(resolver, Settings.Global.MODE_RINGER))
    } catch (e: Exception) {
        null
    }

    companion object {
        private const val TAG = "AndroidRinger"
        private const val SETTLE_MS = 200L

        /** Sent when the phone's internal ringer mode changes (e.g. from the quick-settings tile). */
        const val ACTION_INTERNAL_RINGER_MODE_CHANGED = "android.media.INTERNAL_RINGER_MODE_CHANGED_ACTION"

        fun fromPlatform(value: Int): RingerMode = when (value) {
            AudioManager.RINGER_MODE_SILENT -> RingerMode.SILENT
            AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
            else -> RingerMode.NORMAL
        }
    }
}
