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
- **Journal**: the training diary, one entry per workout on a day. A workout earns an
  entry only when at least one of these is true: an event on **any** calendar names it
  (matched by name, so "WOD", "🔥 WOD", "CrossFit WOD" and "WOD 8:00" all count, and a
  recurring event counts on every occurrence), a watch/strap session was recorded, or
  values were logged into its AI workout. An entry is built from three merged parts:
  the **workout** (the AI plan with the weights, times, reps and other values you
  entered, or the original workout text when no plan exists), the **watch data**
  Health Connect recorded for it (duration, distance, pace, calories, heart rate and
  the HR curve), and **free comments**. A session merges into the workout whose
  calendar window it overlaps the most; when it is the only workout on the day it
  takes the day's sessions, and anything still unmatched appears as its own activity
  entry. A night's sleep is an entry on the day it ended. The month grid marks
  workouts, watch data, sleep and notes. Comments live in `journal.db`, keyed to the
  workout or entry id so they survive re-syncs.
- **Phone UI**: the home screen is three destinations — **Journal**, **Workouts** and
  **Records** — all in Jetpack Compose (Barlow typeface, the `ui/theme` design system).
  - **Workouts** is a calendar over the local workout database with Week, Month and Day
    views. Week is the default; Settings → Workouts & watch changes it. Tapping a day
    opens that day's workouts, and tapping one opens the full session, parsed into
    headings, bullets and body text.
  - **History** (from Workouts) is the permanent archive: every pulled workout is kept,
    grouped by year and month, even after HYPR rotates a date out. It can export the
    whole archive as a CSV file. `workouts.db` and `records.db` are included in Android
    backup (the health journal is not).
  - **Records** stores personal bests in `records.db`: **Weights** (a lift × 1RM/2RM/3RM
    table with every attempt, date and source workout) and **Benchmarks** (for time,
    reps, rounds + reps, load or points, with a best result and full history). The table
    shows 1/2/3RM; the detail screen accepts any rep max (5RM, 8RM, ...). Records are
    entered manually or quick-added from a workout's detail screen; weights are in
    kilograms.
  - **AI workout** turns a workout's text into an interactive plan with the provider
    you configure — **Claude, OpenAI, DeepSeek, Kimi, Gemini or a custom endpoint**:
    sections, exercises, sets and the right metrics (weight, reps, time, distance,
    calories, rounds or notes). Each provider keeps its own endpoint, model and
    encrypted API key. DeepSeek defaults to `deepseek-flash` (`deepseek-v4-pro` is the
    stronger option; the retired `deepseek-chat`/`deepseek-reasoner` aliases migrate to
    Flash, and V4 thinking is on by default). Three wire protocols are supported:
    Anthropic Messages, OpenAI-compatible Chat Completions (OpenAI, DeepSeek, Kimi,
    most proxies) and native Gemini `generateContent`. The client adapts to common
    provider differences (`max_tokens` vs `max_completion_tokens`, `response_format`,
    system-role handling) and the plan is parsed from JSON. The user fills the values in; plans and values
    are stored in `interactive.db` and included in backup. Every key is stored
    encrypted with the Android Keystore. Only the workout's date, type and text are
    sent.
  - **Settings** is a Compose page with a hamburger drawer: Calendar, Sync &
    reliability, Workouts & watch, AI workout, Diagnostics, and About.
- **Wear OS companion** (`:wear`): all workouts by date and type, plus the workout
  journal entries (exercise + WOD) grouped by date and including edited comments. The
  phone pushes a gzipped, chunked Data Layer snapshot; the watch caches it and works
  offline. Records are phone-only for now.

## Build

```bash
./gradlew :app:assembleDebug :wear:assembleDebug
./gradlew :app:testDebugUnitTest :shared:testDebugUnitTest
```

The unit tests include a local socket HTTP server that exercises the real
Anthropic, OpenAI-compatible and Gemini request/response shapes, including the
provider-compatibility retries, without contacting a live provider.

Release builds are produced by `.github/workflows/build_hc_apk.yml` and published to
the `latest` GitHub release as `app-release.apk` and `wear-release.apk`.

## Install / update

- Phone: `adb install -r app-release.apk` updates in place, keeping the journal and
  settings.
- Watch: delegate to the watch-manager agent
  (`~/git/misc/Agents/watch-manager/watchctl install hc --yes`). It resolves the
  phone-Termux-to-watch route, installs the latest `wear-release.apk` and records what
  landed. The watch APK **must** share the phone APK's `applicationId` (`com.hcexport`)
  and signing key, or the Wearable Data Layer silently drops the snapshot. Debug builds
  use `com.hcexport.debug` on both modules, so two debug APKs pair with each other.
- Do not install the wear APK on the phone: it has the phone's applicationId and will
  replace the phone app.

## Layout

| Path | Purpose |
|---|---|
| `app/` | Phone app. Journal/Workouts/Records/History/AI workout/Settings: Jetpack Compose (Barlow typeface, `ui/theme` design system). Stores: `WorkoutStore` (`workouts.db`), `RecordStore` (`records.db`), `InteractiveStore` (`interactive.db`), `JournalNotesStore` (`journal.db`, free comments; kept off cloud backup with the health data). The journal feed (`JournalFeed`) merges workouts, AI results and Health Connect sessions. AI: `AiClient` ↔ Anthropic Messages / OpenAI-compatible / native Gemini; provider presets and per-provider endpoint/model in `AiConfigStore`, encrypted keys in `AiKeyStore` (Android Keystore). |
| `shared/` | Snapshot DTOs, JSON/gzip/chunk codec, launcher icons |
| `wear/` | Wear OS companion (Compose for Wear) |
| `hc_to_drive.py`, `samsung_to_gcal.py`, `sync_from_hc.py`, `tasker_hc_query.js` | Older Termux/Tasker export path — see `SETUP.md` |
