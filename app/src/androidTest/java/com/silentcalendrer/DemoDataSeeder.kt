package com.silentcalendrer

import android.Manifest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.silentcalendrer.core.MINUTE_MS
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Not a test: fills the local test calendar with sample meetings for manual checks and
 * screenshots. Runs only when asked for:
 *   adb shell am instrument -w -e class com.silentcalendrer.DemoDataSeeder -e seedDemo true \
 *       com.silentcalendrer.test/androidx.test.runner.AndroidJUnitRunner
 * Pass -e clearDemo true instead to remove the sample calendar.
 */
@RunWith(AndroidJUnit4::class)
class DemoDataSeeder {

    @get:Rule
    val permissions: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

    private val args = InstrumentationRegistry.getArguments()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun seed() {
        assumeTrue(args.getString("seedDemo") == "true")
        val calendar = TestCalendar(context) // recreates the test calendar empty
        val now = System.currentTimeMillis() / MINUTE_MS * MINUTE_MS
        fun at(minutes: Long) = now + minutes * MINUTE_MS
        calendar.addEvent("Morning stand-up", at(-240), at(-225))
        calendar.addEvent("Design review with the product team", at(-180), at(-120))
        calendar.addEvent("1:1 with Sara", at(-90), at(-60))
        calendar.addEvent("Lunch with partners", at(-50), at(-20))
        calendar.addEvent("Sprint planning", at(-15), at(45))
        calendar.addEvent("Client call", at(-5), at(25))
        calendar.addEvent("Architecture sync", at(70), at(100))
        calendar.addEvent("Budget review", at(120), at(150))
        calendar.addEvent("Interview: Android developer", at(180), at(240))
        calendar.addEvent("Team retro", at(260), at(300))
        calendar.addEvent("Declined: optional town hall", at(90), at(120), selfStatus = android.provider.CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED)
    }

    @Test fun clear() {
        assumeTrue(args.getString("clearDemo") == "true")
        TestCalendar(context).deleteAll()
    }
}
