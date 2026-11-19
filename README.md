# Silent Calendrer

Android app (Android 14 / API 34 and later) that silences the phone before each calendar meeting
and restores **Ringing** after it ends: by default **10 minutes before** and **15 minutes after**,
adjustable in Settings (0–60 min each, one setting for all meetings). It reads the calendars synced
to the device (including Google accounts) through the Calendar Provider. No network, no OAuth.

## Build and run

Requirements: Android SDK with platform 36 (`local.properties` points to it) and JDK 17 (`JAVA_HOME`).

```bash
./gradlew assembleDebug                 # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest             # JVM unit tests (engine, filter, windows, sections)
./gradlew connectedDebugAndroidTest     # on a device/emulator, see "On-device tests" below
```

The version shown in the footer comes from `versionName` in `app/build.gradle.kts`.

## How it works

```
 triggers ──► Reconciler.reconcile(now) ──► ringer + one exact alarm for the next boundary
  • exact alarm (window start/end, 2-min recheck)        • AlarmReceiver (direct-boot aware)
  • reboot (LOCKED_BOOT / BOOT), app update, time / time-zone change, permission change
  • calendar change (WorkManager content-URI job; ContentObserver while the UI is open)
  • periodic refresh every 15 min (WorkManager, fallback)  • app opened / resumed
  • ringer changed (receiver inside the foreground service, only during a silent window)
```

* `core/` is plain Kotlin with no Android code: `EventFilter`, `SilentWindowCalculator`,
  `TodaySchedule`, `Reconciler`. The reconciler is idempotent: each run reads the calendar,
  rebuilds the merged silent windows, makes the ringer match and sets **one** alarm for the next
  moment something must happen. Reboot, process death and calendar edits all go through this path.
* State (`EngineState`: windows, "app controls the ringer until T", pending recheck) is stored
  in **device-protected** storage. That lets an expired silent window be ended right after a
  reboot, before the user unlocks. Meeting titles are never stored there.
* `EnforcementService` is a `specialUse` foreground service that runs **only while the phone is
  held silent**. It shows "Silent until …" and catches manual ringer changes the moment they
  happen (`RINGER_MODE_CHANGED` can't reach manifest receivers). If Android refuses to start it,
  the engine checks every 2 minutes instead.

### Rules implemented

| Rule | Where |
|---|---|
| Silent at start − *lead*, Ringing at end + *tail* (defaults 10 / 15, Settings) | `SilentWindowCalculator`, `UserSettings` |
| Calendars switched off in Settings are ignored | `EventFilter.toMeetings` |
| "Let it ring" (notification) stops silencing until the current window ends; tap the mode pill to silence again | `Reconciler.releaseCurrentWindow` / `resumeSilencing` |
| Overlapping or touching windows merge (never ring between them) | `SilentWindowCalculator.windows` |
| Always restore **Ringing**, never auto-switch to Vibration | `Reconciler` |
| Manual change during a window: wait 2 min after the *latest* change, then Silent if still inside | `Reconciler` (`overrideRecheckAt`) |
| Included: accepted, tentative, no-attendee events, events marked Free | `EventFilter` |
| Excluded: declined, unanswered invitations, all-day, cancelled | `EventFilter` |
| No end time → 1 hour | `EventFilter.effectiveEnd` |
| Same meeting in two accounts shown once | `EventFilter.toMeetings` |
| Phone refuses Silent (some OEMs) → reported, not retried every 2 min | `Reconciler.applySilent` |

## Permissions

| Permission | How it is granted | Without it |
|---|---|---|
| `READ_CALENDAR` | Runtime dialog (setup screen) | No meetings; ringer untouched; if the app was holding Silent it restores Ringing |
| Do Not Disturb access (`ACCESS_NOTIFICATION_POLICY`) | Settings screen | Schedule is shown but nothing is switched; a notification warns when a meeting starts |
| `SCHEDULE_EXACT_ALARM` | Settings screen ("Alarms & reminders") | Inexact alarms, so switching may be several minutes late; banner shown |
| `POST_NOTIFICATIONS` | Runtime dialog | Service runs without a visible notification; no warnings |
| Battery unrestricted (optional) | Settings link | Only matters on phones whose makers kill background apps |
| `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE(_SPECIAL_USE)`, `WAKE_LOCK` | Granted at install | – |

`WRITE_CALENDAR` is added **only to debug builds** (`app/src/debug/AndroidManifest.xml`) so the
instrumented tests can create a local test calendar.

## UI

Both supplied backgrounds are drawn unchanged. Text is placed into rectangles measured from the
images (`ui/BackgroundSpec.kt`). The image is scaled to fit the window, and any leftover space is
filled by stretching the image's outermost pixels, so the overlay always lines up with the frames.
Portrait windows use `mobile bg.png`, landscape windows use `tablet bg.png`. Phones are locked to
portrait; tablets rotate (Android 16 ignores orientation locks on large screens anyway).
Single-line fields shrink to fit; lists scroll inside their frame and show a thin scroll indicator.
The launcher icon is `icone.png` cropped to the inside of the blue square, placed in the 72/108 dp
safe zone on a matching gradient, with a monochrome layer for themed icons.

## Version history

| Version | Changes |
|---|---|
| 1.2.0 | Settings (minutes before/after, calendar choice), "Let it ring", bell icon matches the mode, faster mode refresh on screen, TechnoHub™ bottom-left |
| 1.1.0 | Real (effective) ringer state on ColorOS, forced switch when a request has no effect, "Upcoming" includes the next 6 hours, TechnoHub™ |
| 1.0.0 | First version |

## Test results (2026-11-17, emulator API 36.1, 1080×2400)

| Scenario | Test | Result |
|---|---|---|
| Single meeting: silent 9:50, still silent 11:00, ringing 11:15 | `ReconcilerTest` | ✅ |
| Overlapping meetings (spec example → restore 11:45) | `SilentWindowCalculatorTest`, `ReconcilerTest` | ✅ |
| Closely consecutive / touching / nested / chained meetings | `SilentWindowCalculatorTest` | ✅ |
| Real gap between meetings (two switches) | `ReconcilerTest` | ✅ |
| Meetings without end time | `EventFilterTest`, `CalendarRepositoryTest` (device) | ✅ |
| Declined, unanswered, cancelled, all-day excluded; accepted, tentative, personal included | `EventFilterTest`, `CalendarRepositoryTest` (device) | ✅ |
| Meeting crossing midnight | `SilentWindowCalculatorTest`, `TodayScheduleTest`, `CalendarRepositoryTest` (device) | ✅ |
| No meetings | `SilentWindowCalculatorTest`, manual UI check | ✅ |
| Meeting cancelled / extended / moved during a window | `ReconcilerTest`, `EngineOnDeviceTest` (device) | ✅ |
| Manual Silent → Ringing during a meeting: respected for 2 min, then Silent | `ReconcilerTest`, `EngineOnDeviceTest` (device) | ✅ |
| Second manual change restarts the 2 min; change near the end isn't overridden | `ReconcilerTest` | ✅ |
| Real exact alarm switches to Silent at window start / back to Ringing at window end | `EngineOnDeviceTest` (device, waits ~60 s each) | ✅ |
| Reboot during a silent window → Silent kept, service and end alarm restored without opening the app | Manual `adb reboot` | ✅ |
| Reboot after the window ended (phone off) → Ringing restored before unlock | `ReconcilerTest` (locked-boot path) | ✅ unit only |
| DND access missing / granted mid-window; calendar permission revoked | `ReconcilerTest` | ✅ |
| Exact alarms denied → banner → setup screen | Manual (`appops … deny`) | ✅ |
| Phone portrait, tablet landscape (2560×1600), tablet portrait (1600×2560) layouts | Manual screenshots | ✅ |
| Main screen sections, mode and version | `MainScreenTest` (Compose, device) | ✅ |

Totals (v1.2.0): 58 JVM unit tests and 7 on-device tests passing (plus the demo seeder, which is skipped
unless requested).

### On-device tests

The engine tests need the special access granted first:

```bash
adb shell cmd notification allow_dnd com.silentcalendrer
adb shell appops set com.silentcalendrer SCHEDULE_EXACT_ALARM allow
```

Fill the device with sample meetings for manual checks, and remove them again:

```bash
adb shell am instrument -w -e class com.silentcalendrer.DemoDataSeeder -e seedDemo true com.silentcalendrer.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class com.silentcalendrer.DemoDataSeeder -e clearDemo true com.silentcalendrer.test/androidx.test.runner.AndroidJUnitRunner
```

Note: `am instrument` **force-stops** the app when it finishes, which cancels its alarms and jobs
(see the limitations below). Open the app once afterwards to re-arm everything.

## Known limitations (Android does not guarantee these)

* **Force stop** (Settings → Force stop, or `am instrument`) cancels all alarms and jobs until the
  app is opened again. Swiping the app away from recents does **not** do this on stock Android.
* **Phone-maker battery managers** (Xiaomi, Huawei, OnePlus, Samsung "deep sleeping apps") may
  kill the app or delay alarms. The setup screen links to the battery settings.
* **Exact alarms** need the user's permission on Android 14+; without it Android may deliver
  the alarm several minutes late. In deep Doze, allow-while-idle alarms are rate-limited.
* **Silent and Do Not Disturb are linked on stock Android 15/16.** On the API 36 emulator,
  switching the ringer to Silent also turns on Do Not Disturb (`zen_mode=1`), and restoring
  Ringing turns it off again (verified). Other phone makers may behave differently; check this
  on your own phone.
* Calendar data is only as current as the phone's own calendar sync.
* Not yet tested on real hardware or a Samsung/Xiaomi phone.

## Project layout

```
app/src/main/java/com/silentcalendrer/
  core/        pure Kotlin engine (unit-tested)
  calendar/    Calendar Provider access
  ringer/      AudioManager / DND access
  data/        device-protected state store
  background/  alarms, receivers, WorkManager, foreground service, notifications
  ui/          Compose screens, background geometry, onboarding
app/src/test/         JVM unit tests
app/src/androidTest/  on-device tests + demo data seeder
```
