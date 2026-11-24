# HC Sync

Android app that reads Health Connect (exercise + sleep) and writes it to the phone's
own Google Calendar, and builds a workout journal. No OAuth — it uses the calendar
account already on the device.

## What it does

- **One sync action**: workouts → local database → Health Connect → calendar →
  calendar WOD descriptions → journal → watch snapshot. The order matters: the journal
  merges the calendar events, so the WOD descriptions must be filled first.
- **Workout database**: every date/type comes from **Fitr** (`app.fitr.training/mobile/api/v15/`)
  when it is connected — the coaches' own source — and is pulled into SQLite
  (`workouts.db`), not just dates with a calendar event. Installs that have not
  connected Fitr still pull https://hypr-workouts.pages.dev/. Rows are never
  deleted when a source drops a date. A 02:00 alarm pulls a rolling window
  (−14…+28 days) every day; the unified sync also pulls first. See
  `fitr_client/` for a standalone CLI over the same API.
- **Section videos**: Fitr attaches movement demos to each section (YouTube
  videos with a CDN thumbnail). They are stored per date/type in `workout_videos`
  (part of `workouts.db`) and shown as a compact **Videos** strip at the bottom of
  the workout section in the journal entry; tapping a thumbnail plays the video
  in an in-app player. Today's programming has none — the demos appear on the
  days the coaches attach them.
- **Reliable schedules**: AlarmManager `setAndAllowWhileIdle` drives an expedited
  WorkManager job; alarms are re-armed on boot, app open, package replace and
  time/timezone changes, with a catch-up run if a scheduled sync was missed. Battery
  optimisation exemption is offered in Settings.
- **Journal**: the training diary, one entry per workout on a day. A workout earns an
  entry only when at least one of these is true: an event on **any** calendar names it
  (matched by name, so "WOD", "🔥 WOD", "CrossFit WOD" and "WOD 8:00" all count, and a
  recurring event counts on every occurrence), a watch/strap session was recorded, or
  values were logged into its AI workout. An entry is built from three merged parts:
  the **workout** (the AI plan edited in place — its sections, exercises, sets and
  the weights, times, reps and other values entered right there — with the original
  workout text one tap away behind *show original*, or just that text when no plan
  exists), the **watch data**
  Health Connect recorded for it (duration, distance, pace, calories, heart rate and
  the HR curve), the **records** set in it (weight PRs and benchmark results, added
  from that entry; a planned entry has no record section), and **free comments**. A session merges into the workout whose
  calendar window it overlaps the most; when it is the only workout on the day it
  takes the day's sessions, and anything still unmatched appears as its own activity
  entry. A night's sleep is an entry on the day it ended. The month grid marks
  workouts, watch data, sleep and notes. Comments live in `journal.db`, keyed to the
  workout or entry id so they survive re-syncs. The journal calendar has **Week,
  Month and Day** views like Workouts — a switcher sits above it, week is the
  default, and Settings → Workouts & watch sets the default — and it moves by
  swipe: left/right on the calendar steps a week (or a month in the month view),
  while left/right on the entries below steps one day. Every swipe is a two-page
  carousel (`ui/SwipeCarousel`): the current day or period slides out and the
  neighbouring one slides in at the same time, both visible side by side with a
  12dp gap, so a 10% drag shows about 10% of the new page minus the gap. The
  neighbouring day and the neighbouring month/week are loaded ahead of the swipe,
  so the incoming page is real content, not a placeholder.
- **Schedule a session**: in the journal's day view — below the day's title and above
  its entries — **Schedule session** lists the workouts the workout database has for
  that day, minus the ones already in the journal. Picking one makes it a journal
  entry immediately, with no calendar event or watch session needed, and offers to
  add a Google Calendar event too, with a calendar picker and a one-hour start time.
  The scheduled window gives the entry its planned time; a watch session still merges
  only through a calendar event, so scheduling alone never absorbs an unrelated
  recording. The schedule lives on the device with the journal. A scheduled entry
  carries a **SCHEDULED** tag and is marked in the month grid.
- **Entry actions and deletion**: every journal card has a three-dot menu at its
  top-right — and the same menu opens on a long press — with **Create AI workout**
  (or **Open AI workout** once a plan exists), **Start live workout** when there is a
  plan, and **Delete entry**. The entry screen also has **Delete this entry** at the
  very bottom. Deleting removes the app's own data for that entry: the schedule and
  the calendar event this app created (events made elsewhere are left alone), the AI
  plan and its values, a merged watch session, a legacy WOD row, and the comment. The
  entry's key is remembered in `hc_journal_hidden` so a re-sync or a calendar event
  cannot bring it straight back; scheduling that workout again restores it.
- **Phone UI**: the home screen is three destinations — **Journal**, **Workouts** and
  **Records** — all in Jetpack Compose (Barlow typeface, the `ui/theme` design system).
  - **Workouts** is a calendar over the local workout database with Week, Month and Day
    views. Week is the default; Settings → Workouts & watch changes it. Tapping a day
    opens that day's workouts, and tapping one opens the full session, parsed into
    headings, bullets and body text. The calendar and the day's list swipe with the
    same two-page carousel as the journal (the loaded window is the period ±1, so the
    incoming week/month/day is already there). Every workout card has a three-dot
    menu — also on a long press — with **Schedule journal entry**, which opens the
    journal's own scheduling dialog on that workout; the workout's detail page offers
    the same action. The **AI workout** here is a preview: the structured plan can be
    generated and regenerated, but values cannot be edited and there is no live-session
    entry — live workouts start from a journal entry only.
  - **History** (from Workouts) is the permanent archive: every pulled workout is kept,
    grouped by year and month, even after HYPR rotates a date out. It can export the
    whole archive as a CSV file. `workouts.db` and `records.db` are included in Android
    backup (the health journal is not).
  - **BridgeAthletic** is a second workout source (`BridgeAthletic.kt`, `BridgeApi.kt`,
    `BridgePull.kt`). Settings → Workouts & watch → BridgeAthletic stores the login
    (AES-GCM under the Android Keystore, like the AI keys) and pulls the athlete's
    assigned workouts from the official app's REST API: the roster on every sync, the
    full program template — blocks, prescribed sets, reps, percentages and weight
    ranges — at most once a week (16 MB of JSON, 650 KB gzipped, reduced to a small text
    cache). Sessions are rendered into the same workout text and stored as type
    **BridgeAthletic**, so they appear in the calendar, History and the journal's
    Schedule session picker like any other source. Warm-up blocks (massage, rotations)
    are left out to keep the session readable. Disconnecting leaves the archived
    workouts in place.
  - **Records** stores personal bests in `records.db`: **Weights** (a lift × 1RM/2RM/3RM
    table with every attempt, date and source workout) and **Benchmarks** (for time,
    reps, rounds + reps, load or points, with a best result and full history). The table
    shows 1/2/3RM; the detail screen accepts any rep max (5RM, 8RM, ...). Records are
    entered manually or quick-added from a workout's journal entry — a record can only
    be set in a session that was done, so it is set where the session lives, and its
    source link opens that journal entry rather than the workout calendar page;
    weights are in kilograms. **Strength sets auto-record**: when values are entered
    into a strength section of the AI workout, the best set at each rep max (1–10RM)
    is stored if it beats the standing record for that lift and rep max — or is the
    lift's first record. Auto records are revised or removed when the entry's values
    change; manual records are never touched.
  - **AI workout** turns a workout's text into an interactive plan with the provider
    you configure — **Claude, OpenAI, DeepSeek, Kimi, Gemini or a custom endpoint**:
    sections, exercises, sets and the right metrics (weight, reps, time, distance,
    calories, rounds or notes). Weight metrics carry the workout's own load guidance
    as a hint — "70% of 1RM", "16–32 kg", per set when the load changes — shown in
    the plan's weight box and under the weight in a live session; any workout text
    with a stated load gets it, not only BridgeAthletic. Each provider keeps its own
    endpoint, model and encrypted API key. DeepSeek defaults to `deepseek-flash` (`deepseek-v4-pro` is the
    stronger option; the retired `deepseek-chat`/`deepseek-reasoner` aliases migrate to
    Flash, and V4 thinking is on by default). Three wire protocols are supported:
    Anthropic Messages, OpenAI-compatible Chat Completions (OpenAI, DeepSeek, Kimi,
    most proxies) and native Gemini `generateContent`. The client adapts to common
    provider differences (`max_tokens` vs `max_completion_tokens`, `response_format`,
    system-role handling) and the plan is parsed from JSON. The plan is the workout's
    presentation: it is embedded in the journal entry and edited there (the same
    panel is also reachable on its own from a workout's detail screen); plans and
    values are stored in `interactive.db` and included in backup. A section result
    (total time) is only offered where the piece is scored as a whole — a For Time
    or AMRAP section — never on strength or accessory work. Every key is stored
    encrypted with the
    Android Keystore. Only the workout's date, type and text are sent.
  - **Live workout** plays the AI plan one set or movement at a time, full screen.
    The first section opens on a get-ready countdown (10 s by default; Settings →
    Live workout changes it), then each step shows the movement large, the weight and
    reps with steppers that carry the last weight forward, and the timer the workout
    itself prescribes — "every 1:30", an EMOM, a time cap, an AMRAP, or repeated
    rounds such as "4 x 5:00 with 1:00 rest". Round-timed metcons run a countdown per
    round, and a movement with a time metric (the 400m run) records its split when
    marked done while the round clock keeps going. **Only one step is live at a
    time**: swiping left/right, or Previous/Next, moves between the session's steps
    without completing anything — past steps show a green completed circle and future
    ones a preview, neither with a timer, and the live clock keeps running while you
    look ahead (it pauses when you step back to edit). The live step has two side-by-
    side actions: **Mark as complete** (green; toggles to red **Not completed**) and
    **Skip timer**. Marking keeps the timer running under a COMPLETED banner; letting
    the timer run out or skipping completes the step — its circle turns green with a
    white check, and its buttons grey out while its fields stay editable. When a
    round's work is done early the next line names the rest ("1:00 min rest"); after
    the round clock, the rest counts down before the next round (a **Skip rest** button starts the next round immediately). Between sections a
    preview shows the shape ("4 rounds of … · 1:00 rest") with a Start button. Values
    are the plan's own, so a live session fills the journal and the records exactly
    as typing them would; ending early keeps every value and marks the last set
    reached, and sections the session never got to stay out of the totals.
    Sound/vibration cues and keep-screen-on are settings.
  - **A workout with the phone in a pocket** stays controllable from a "dumb" band.
    A foreground service posts a new high-priority notification for each set or
    round, with **Mark as complete** / **Skip** buttons (and **Skip rest** during a
    rest), and hosts a silent media session so the Galaxy Fit3/Fitbit **Music
    Controller** drives the same actions — play/pause marks the step, next skips.
    The step, round and timer appear in the notification and as the media metadata.
    A new action added to the live action list reaches both surfaces. An AMRAP
    records a score — full rounds plus the unfinished round, e.g. `5 rounds + 5
    reps of Toes-to-Bar = 151`, with a 200m run counting as one rep — instead of
    its fixed time; a For Time piece reports the finishing clock.
  - **Settings** is a Compose page with a hamburger drawer: Calendar, Sync &
    reliability, Workouts & watch, AI workout, Live workout, Diagnostics, and About.
    Workouts & watch also sets the **first day of the week** (Sunday…Saturday,
    defaulting to the device locale's own first day) for both calendars.
- **Wear OS companion** (`:wear`, rebuilt 2026-11-21): the journal and the phone's
  **live workouts** on the wrist, in the phone's own design language (Barlow, deep
  navy, one jewel accent per workout type). The phone pushes a gzipped, chunked Data
  Layer snapshot of its *rendered* journal — workouts, watch sessions, sleep, comments,
  AI summaries — plus the AI plans the watch can run; the watch caches it and works
  offline. A workout opens on a deliberately minimal page — its type, the planned date
  and time and a one-line summary — with two round actions, **START** (or **RESUME**) and
  **DETAILS**. Everything longer (stats, plan sections, the original text, the note) is
  on the details page, so the watch never shows a shrunk phone layout.
  The live screen runs the same plan and the same state machine as the phone: get-ready
  countdown, interval countdowns, round clocks, AMRAP caps, weight/reps/time steppers
  (the rotating bezel nudges the selected value), Done / Skip, rest between rounds, the
  whole-plan map, pause, and a done summary. A red **END** pill is always in the live top
  row; pausing offers End workout as well. **Both devices can start and drive the same
  session**: a `LiveSessionDto` DataItem (`/hcexport/live/session`) carries the stage and
  values, published on stage changes (not on clock ticks); the other device adopts it and
  ticks its own clocks. **Sync to phone** (watch) / **Sync from watch** (phone) asks for a
  fresh record, which is what recovers a watch that was disconnected when the workout
  started. **Ending is terminal on both devices**: leaving the live screen — the phone's
  close dialog, the watch's pause menu → *End workout*, the back gesture or a swipe —
  publishes `open=false`, and the other device finalizes its values and leaves. The
  close is persisted as a tombstone so a stale open record cannot revive the workout,
  and no stage may be published after the close. Watch-entered values go back as
  `/hcexport/results/<date>/<type>` and are written to `interactive.db` and auto-records;
  values for a plan that has since been regenerated are rejected. Records remain
  phone-only.

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
  use `com.hcexport.debug` on both modules, so two debug APKs pair with each other; the
  debug watch app is labelled **HC Sync dev** and coexists with the release `HC Sync`
  (the two do not talk to each other). The watch-manager also tracks the debug variant
  as `hc-debug`.
- Do not install the wear APK on the phone: it has the phone's applicationId and will
  replace the phone app.

## Layout

| Path | Purpose |
|---|---|
| `app/` | Phone app. Journal/Workouts/Records/History/AI workout/Settings: Jetpack Compose (Barlow typeface, `ui/theme` design system). Stores: `WorkoutStore` (`workouts.db`), `RecordStore` (`records.db`), `InteractiveStore` (`interactive.db`), `JournalNotesStore` (`journal.db`, free comments; kept off cloud backup with the health data). The journal feed (`JournalFeed`) merges workouts, AI results and Health Connect sessions; `JournalDeleter` + `JournalHiddenStore` (`hc_journal_hidden`) implement entry deletion; `ui/SwipeCarousel` animates every swipe as a two-page carousel. AI: `AiClient` ↔ Anthropic Messages / OpenAI-compatible / native Gemini; provider presets and per-provider endpoint/model in `AiConfigStore`, encrypted keys in `AiKeyStore` (Android Keystore). |
| `shared/` | Snapshot DTOs, JSON/gzip/chunk codec, the shared plan/live model (`InteractiveWorkout`, `LiveWorkout`), the live-session/result protocols, launcher icons |
| `wear/` | Wear OS companion (Compose for Wear): journal, entry detail and the two-way live workout |
| `hc_to_drive.py`, `samsung_to_gcal.py`, `sync_from_hc.py`, `tasker_hc_query.js` | Older Termux/Tasker export path — see `SETUP.md` |
