package com.silentcalendrer

import android.Manifest
import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.silentcalendrer.core.MINUTE_MS
import com.silentcalendrer.ui.MainActivity
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runner.Description
import org.junit.runners.model.Statement

/** The main screen shows today's meetings in the right sections, plus mode and version. */
@RunWith(AndroidJUnit4::class)
class MainScreenTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var calendar: TestCalendar

    /** Inserts test meetings before the activity starts. */
    private val seed = object : TestRule {
        override fun apply(base: Statement, description: Description) = object : Statement() {
            override fun evaluate() {
                context.getSharedPreferences("ui", Context.MODE_PRIVATE).edit().putBoolean("onboarding_done", true).commit()
                calendar = TestCalendar(context)
                // All three meetings must touch *today*, whatever the time of day the test runs.
                val zone = java.time.ZoneId.systemDefault()
                val nowTime = java.time.LocalTime.now(zone)
                org.junit.Assume.assumeTrue(
                    "Needs 30 min after midnight and 5 min before it",
                    nowTime.toSecondOfDay() >= 30 * 60 && nowTime.toSecondOfDay() <= 24 * 3600 - 5 * 60,
                )
                val now = System.currentTimeMillis()
                calendar.addEvent("UI Past meeting", now - 25 * MINUTE_MS, now - 15 * MINUTE_MS)
                calendar.addEvent("UI Current meeting", now - 5 * MINUTE_MS, now + 20 * MINUTE_MS)
                calendar.addEvent("UI Upcoming meeting", now + 2 * MINUTE_MS, now + 30 * MINUTE_MS)
                base.evaluate()
            }
        }
    }

    private val compose = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain
        .outerRule(GrantPermissionRule.grant(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
        .around(seed)
        .around(compose)

    @After fun cleanUp() = calendar.deleteAll()

    @Test fun showsMeetingsModeAndVersion() {
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasText("UI Upcoming meeting")).fetchSemanticsNodes().isNotEmpty()
        }
        fun inSection(tag: String, title: String) =
            compose.onNode(hasText(title).and(hasAncestorTag(tag)), useUnmergedTree = true).assertIsDisplayed()

        inSection("now-list", "UI Current meeting")
        inSection("earlier-list", "UI Past meeting")
        inSection("upcoming-list", "UI Upcoming meeting")
        compose.onNode(hasText(BuildConfig.VERSION_NAME)).assertIsDisplayed()
        compose.onNodeWithTag("mode").assertIsDisplayed()
    }

    private fun hasAncestorTag(tag: String) = androidx.compose.ui.test.hasAnyAncestor(hasTestTag(tag))
}
