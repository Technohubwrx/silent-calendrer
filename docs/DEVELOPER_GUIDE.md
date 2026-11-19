# Silent Calendrer — Developer guide

- [Tech stack](#tech-stack)
- [Build and run](#build-and-run)
- [Project layout](#project-layout)
- [Architecture](#architecture)
- [Rules implemented](#rules-implemented)
- [Permissions](#permissions)
- [UI on top of the artwork](#ui-on-top-of-the-artwork)
- [Testing](#testing)
- [Release build and signing](#release-build-and-signing)
- [Versioning and releases](#versioning-and-releases)
- [How to add a feature](#how-to-add-a-feature)
- [Platform notes and limitations](#platform-notes-and-limitations)

---

## Tech stack

| | |
|---|---|
| Language | Kotlin 2.2, JDK 17 |
| Android | `minSdk 34` (Android 14), `targetSdk`/`compileSdk 36` |
| UI | Jetpack Compose (Material 3) drawn over the supplied artwork |
| Background | AlarmManager (exact alarms), WorkManager, a `specialUse` foreground service |
| Storage | DataStore (Preferences) + kotlinx.serialization, in device-protected storage |
| Calendar | Android Calendar Provider (`CalendarContract.Instances`) — no network, no OAuth |
| DI | Hand-written `AppContainer` |
| Build | Gradle 8.13 wrapper, AGP 8.13, version catalog (`gradle/libs.versions.toml`), R8 for release |

## Build and run

Requirements: Android SDK with platform 36, JDK 17 (`JAVA_HOME`). Create `local.properties` with
your SDK path if Android Studio hasn't (`sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk`).

```bash
./gradlew assembleDebug               # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest           # JVM unit tests
./gradlew connectedDebugAndroidTest   # on-device tests (see Testing)
./gradlew assembleRelease             # signed release (see Release build and signing)
```

Or open the folder in Android Studio and run the `app` configuration.

## Project layout

```
app/src/main/java/com/silentcalendrer/
  core/        Pure Kotlin engine — no Android imports, fully unit-tested
               Model, EventFilter, SilentWindowCalculator, TodaySchedule,
               Reconciler, EngineState, UserSettings, Ports (interfaces to Android)
  calendar/    CalendarRepository — Calendar Provider queries, calendar list
  ringer/      AndroidRinger — reads the *effective* ringer mode, sets it, forces real switches
  data/        StateStore, SettingsStore — DataStore in device-protected storage
  background/  AlarmScheduler, receivers, WorkManager workers, EnforcementService,
               Notifications, AndroidPlatform
  ui/          MainActivity, MainViewModel, MainScreen, SettingsScreen, OnboardingScreen,
               BackgroundSpec (pixel rectangles on the artwork), Permissions
  SilentCalendrerApp.kt   Application + AppContainer
app/src/main/res/drawable-nodpi/   Backgrounds and bell variants (unscaled)
app/src/test/                      JVM unit tests
app/src/androidTest/               On-device tests, TestCalendar helper, DemoDataSeeder
app/src/debug/AndroidManifest.xml  WRITE_CALENDAR for tests (debug builds only)
docs/                              User & developer guides, screenshots
```

## Architecture

Everything that changes the ringer goes through **one idempotent function**,
`Reconciler.reconcile(trigger, now)`:

```
 triggers ──► Reconciler.reconcile(now) ──► ringer mode + ONE alarm for the next boundary

  • exact alarm (window start / end, 2-minute recheck)     → AlarmReceiver (direct-boot aware)
  • reboot (LOCKED_BOOT_COMPLETED, BOOT_COMPLETED), app update, time / time-zone change,
    exact-alarm or DND permission change                   → LockedBootReceiver, SystemEventReceiver
  • calendar change                                        → WorkManager content-URI job
                                                             (+ ContentObserver while the UI is open)
  • periodic refresh every 15 min (fallback)               → WorkManager RefreshWorker
  • app opened / resumed, settings changed                 → MainViewModel
  • ringer changed by the user                             → receiver in EnforcementService
  • "Let it ring" / "tap to silence"                       → ReleaseReceiver / MainViewModel
```

Each run:

1. Reads the calendar for −24 h … +48 h (skipping calendars switched off in settings), filters
   events, and merges them into **silent windows** (`SilentWindowCalculator`). Before the first
   unlock after a reboot the calendar can't be read, so the saved plan is used.
2. Applies the plan: entering a window → Silent; leaving it → always Ringing; manual change inside
   a window → wait 2 minutes from the latest change, then Silent again; released window → Ringing.
3. Sets a **single** alarm for the next moment something must happen.
4. Saves `EngineState` (windows, "controlling until T", pending recheck, released window).

Because a run is idempotent, recovery after process death or reboot needs no special code path.

**Effective ringer state.** Android has an *external* ringer mode (what `getRingerMode()` reports)
and an *internal* one (what the phone uses). On ColorOS they can disagree: after the user taps the
ringer tile, `getRingerMode()` still says Silent and another `setRingerMode(SILENT)` is a no-op.
`AndroidRinger.current()` therefore counts Silent only if `STREAM_RING` is really muted, and
`set()` forces a real transition (Normal → Silent) when a plain request has no effect.

**EnforcementService** is a foreground service (`specialUse`) that runs **only while the phone is
held silent**. It shows *Silent until…* with **Let it ring**, and listens for
`RINGER_MODE_CHANGED` / `INTERNAL_RINGER_MODE_CHANGED`, which can't reach manifest receivers. If
Android refuses to start it, the engine polls every 2 minutes instead. It is only stopped from
outside once it has called `startForeground()` — stopping earlier crashes the app
(`ForegroundServiceDidNotStartInTimeException`); otherwise it stops itself.

**Storage.** `EngineState` and `UserSettings` live in **device-protected** storage, so an expired
window can be ended right after a reboot, before the user unlocks. Only times and flags are stored
— never meeting titles.

## Rules implemented

| Rule | Where |
|---|---|
| Silent at start − *lead*, Ringing at end + *tail* (defaults 10 / 15, Settings 0–60) | `SilentWindowCalculator`, `UserSettings` |
| Overlapping or touching windows merge (never ring between them) | `SilentWindowCalculator.windows` |
| Always restore **Ringing**, never auto-switch to Vibration | `Reconciler` |
| Manual change during a window: wait 2 min after the *latest* change | `Reconciler` (`overrideRecheckAt`) |
| "Let it ring" until the current window ends; tap to silence again | `Reconciler.releaseCurrentWindow` / `resumeSilencing` |
| Included: accepted, tentative, no-attendee events, events marked Free | `EventFilter` |
| Excluded: declined, unanswered invitations, all-day, cancelled, switched-off calendars | `EventFilter` |
| No end time → 1 hour | `EventFilter.effectiveEnd` |
| Same meeting in two accounts shown once | `EventFilter.toMeetings` |
| Phone refuses Silent → reported, not retried every 2 min | `Reconciler.applySilent` |
| "Upcoming" = rest of today + anything within the next 6 h | `TodaySchedule` |

## Permissions

| Permission | Granted via | Without it |
|---|---|---|
| `READ_CALENDAR` | Runtime dialog | No meetings; if the app was holding Silent it restores Ringing |
| `ACCESS_NOTIFICATION_POLICY` (DND access) | Settings screen | Nothing is switched; a notification warns when a meeting starts |
| `SCHEDULE_EXACT_ALARM` | Settings screen | Inexact alarms (may be minutes late); banner shown |
| `POST_NOTIFICATIONS` | Runtime dialog | Service runs without a visible notification |
| `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE(_SPECIAL_USE)`, `WAKE_LOCK` | Install time | – |
| `ACCESS_NETWORK_STATE` | Install time, added by WorkManager | – (the app has **no** `INTERNET` permission) |

`WRITE_CALENDAR` is declared **only in debug builds** so tests can create a local test calendar.

## UI on top of the artwork

The supplied backgrounds (`mobile bg.png` 887×1774, `tablet bg.png` 1449×1086) are drawn unchanged.
`BackgroundSpec` holds the pixel rectangles of every frame (mode pill, date, three lists, footer,
bell, gear, trademark). The image is scaled to fit the window and the overlays are placed with the
same scale, so text always lines up with the frames; leftover space is filled by stretching the
image's outermost pixels. Portrait windows use the mobile art, landscape windows the tablet art;
phones are locked to portrait.

The Silent / Vibration bells (`drawable-nodpi/bell_*`) were generated from the same artwork: the
sound-wave arcs are painted out with the disc's own gradient and a red slash or vibration marks are
added. The launcher icon is `icone.png` cropped to the inside of the blue square, in the 72/108 dp
safe zone, with a monochrome layer for themed icons.

## Testing

**Unit tests** (`app/src/test`, JVM, no device): 58 tests covering window merging, custom minutes,
event filtering, calendar exclusion, today's sections, and the reconciler (single / overlapping /
consecutive meetings, manual changes, Let it ring, reboot recovery, missing permissions…).

```bash
./gradlew testDebugUnitTest
```

**On-device tests** (`app/src/androidTest`): real Calendar Provider filtering, real ringer
switching, real exact alarms (they wait ~60 s), the main screen. Grant special access first:

```bash
adb shell cmd notification allow_dnd com.silentcalendrer
adb shell appops set com.silentcalendrer SCHEDULE_EXACT_ALARM allow
./gradlew connectedDebugAndroidTest
```

The tests use a separate local calendar (`silentcalendrer-test`) and delete it afterwards; your
own calendars are never touched.

**Demo data** for manual checks and screenshots (debug build):

```bash
adb shell am instrument -w -e class com.silentcalendrer.DemoDataSeeder -e seedDemo true com.silentcalendrer.test/androidx.test.runner.AndroidJUnitRunner
adb shell am instrument -w -e class com.silentcalendrer.DemoDataSeeder -e clearDemo true com.silentcalendrer.test/androidx.test.runner.AndroidJUnitRunner
```

`am instrument` force-stops the app when it finishes, which cancels its alarms — open the app once
afterwards.

**Manual checks worth repeating on real phones:** reboot during a silent window; manual ringer
change via the quick-settings tile; Let it ring; DND turning off on restore.

## Release build and signing

Release builds are shrunk with R8 (~5 MB vs ~34 MB debug) and signed with the TechnoHub key, which
is **not** in the repository.

1. Create a key once (choose your own passwords):

   ```bash
   "$JAVA_HOME/bin/keytool" -genkeypair -v -keystore silentcalendrer-release.jks -alias silentcalendrer -keyalg RSA -keysize 4096 -validity 10000
   ```

   (PowerShell: `& "$env:JAVA_HOME\bin\keytool.exe" …` with the same arguments.)

2. Create `keystore.properties` next to `settings.gradle.kts` — with the PKCS12 key format both
   passwords are the same:

   ```properties
   storeFile=silentcalendrer-release.jks
   storePassword=<password>
   keyAlias=silentcalendrer
   keyPassword=<password>
   ```

3. `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`.

`*.jks`, `keystore.properties` and `releases/` are git-ignored. Without `keystore.properties` the
release APK is unsigned; `-PsignReleaseWithDebugKey=true` signs it with the debug key for a local
test install only. **Back up the key and password** — updates must be signed with the same key.

Verify a signature:

```bash
"$ANDROID_HOME/build-tools/36.1.0/apksigner" verify --print-certs app/build/outputs/apk/release/app-release.apk
```

A phone with the debug build installed must uninstall it once before installing a release build
(different signatures).

## Versioning and releases

- Version lives only in `app/build.gradle.kts` (`versionCode`, `versionName`); the footer reads
  `BuildConfig.VERSION_NAME`.
- Bump both, update [CHANGELOG.md](../CHANGELOG.md), commit, then tag: `git tag -a v1.3.0 -m "…"`.
- Build the signed APK, copy it to `releases/SilentCalendrer-<version>.apk`, and attach it to a
  GitHub release for the tag.

## How to add a feature

1. Put decision logic in `core/` as plain Kotlin and cover it with unit tests (see `ReconcilerTest`'s fakes).
2. If it needs Android, add an interface in `core/Ports.kt` and implement it in the matching package.
3. If it changes when the phone should be silent, feed it into `Reconciler.reconcile` — never
   switch the ringer from anywhere else.
4. New saved fields go into `EngineState` / `UserSettings` **with defaults** (old data must still load).
5. UI text goes into `res/values/strings.xml`; positions on the artwork into `BackgroundSpec`.
6. Run unit tests, then the on-device tests, then check phone and tablet layouts.

## Platform notes and limitations

- **Force stop** cancels all alarms and jobs until the app is opened again (Android rule).
  Swiping from recents does not, on stock Android.
- **Battery managers** on OPPO/ColorOS, Xiaomi, Huawei, Samsung may delay or kill background work;
  users may need to allow unrestricted background use.
- **Exact alarms** need the user's permission on Android 14+; without it alarms can be minutes late,
  and allow-while-idle alarms are rate-limited in deep Doze.
- **Silent ↔ Do Not Disturb**: on stock Android 15/16 and ColorOS 16, Silent turns DND on and
  restoring Ringing turns it off (verified on the API 36 emulator and an OPPO phone with ColorOS 16).
- Calendar data is only as current as the phone's own calendar sync.
- Tested on: emulator API 36.1 (phone and tablet sizes), an OPPO phone (Android 16, ColorOS 16).
