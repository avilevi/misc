# HC Sync

Android app that reads Health Connect (exercise + sleep) and writes it to the phone's
own Google Calendar, and builds a workout journal. No OAuth — it uses the calendar
account already on the device.

## What it does

- **One sync action**: workouts → local database → Health Connect → calendar →
  calendar WOD descriptions → journal → watch snapshot. The order matters: the journal
  merges the calendar events, so the WOD descriptions must be filled first.
- **Workout database**: every date/type on https://hypr-workouts.pages.dev/ is pulled
  into SQLite (`workouts.db`), not just dates with a calendar event. Rows are never
  deleted when the site drops a date. A 02:00 alarm pulls the whole page every day;
  the unified sync also pulls first.
- **Reliable schedules**: AlarmManager `setAndAllowWhileIdle` drives an expedited
  WorkManager job; alarms are re-armed on boot, app open, package replace and
  time/timezone changes, with a catch-up run if a scheduled sync was missed. Battery
  optimisation exemption is offered in Settings.
- **Journal**: exercise/sleep entries plus one WOD entry per scheduled "WOD" calendar
  event, with user edits and comments preserved across syncs.
- **Wear OS companion** (`:wear`): all workouts by date and type, plus the workout
  journal entries (exercise + WOD) grouped by date and including edited comments. The
  phone pushes a gzipped, chunked Data Layer snapshot; the watch caches it and works
  offline.

## Build

```bash
./gradlew :app:assembleDebug :wear:assembleDebug
./gradlew :app:testDebugUnitTest :shared:testDebugUnitTest
```

Release builds are produced by `.github/workflows/build_hc_apk.yml` and published to
the `latest` GitHub release as `app-release.apk` and `wear-release.apk`.

## Install / update

- Phone: `adb install -r app-release.apk` updates in place, keeping the journal and
  settings.
- Watch: install `wear-release.apk` through the phone (Termux/ssh + adb to the watch).
  The watch APK **must** share the phone APK's `applicationId` (`com.hcexport`) and
  signing key, or the Wearable Data Layer silently drops the snapshot. Debug builds
  use `com.hcexport.debug` on both modules, so two debug APKs pair with each other.
- Do not install the wear APK on the phone: it has the phone's applicationId and will
  replace the phone app.

## Layout

| Path | Purpose |
|---|---|
| `app/` | Phone app (plain Views) |
| `shared/` | Snapshot DTOs, JSON/gzip/chunk codec, launcher icons |
| `wear/` | Wear OS companion (Compose for Wear) |
| `hc_to_drive.py`, `samsung_to_gcal.py`, `sync_from_hc.py`, `tasker_hc_query.js` | Older Termux/Tasker export path — see `SETUP.md` |
