# Changelog

## 1.2.0 — 2026-11-19

### Added
- **Settings** (⚙, top right): minutes of silence before and after meetings (0–60, defaults 10 / 15),
  applied immediately — also to a meeting in progress.
- **Calendar choice**: switch off calendars that shouldn't silence the phone.
- **Let it ring** button on the *Silent until…* notification: rings until the current meeting ends;
  tap the mode bar in the app to silence again.
- The **bell** on the main screen now matches the mode (crossed out for Silent, vibration marks for Vibration).
- Signed, optimized release build (~5 MB).

### Changed
- **TechnoHub™** mark moved to the bottom-left corner and enlarged.
- The ringer mode on screen refreshes within about 2 seconds of a change (was up to ~1 minute on some phones).

## 1.1.0 — 2026-11-18

### Fixed
- On ColorOS, after the user turned the ringer back on during a meeting, re-applying Silent showed
  *Silent* in the app but the phone kept ringing. The app now checks whether the ring sound is
  really muted and forces a real switch when needed.
- A foreground-service start/stop race that could crash the app when a meeting was cancelled right
  as silencing began.

### Added
- **Upcoming** also lists tomorrow's meetings that start within the next 6 hours.
- **TechnoHub™** mark.

## 1.0.0 — 2026-11-17

First version: automatic Silent before meetings and Ringing after, merged overlapping meetings,
2-minute respect for manual changes, reboot recovery, phone and tablet layouts.
