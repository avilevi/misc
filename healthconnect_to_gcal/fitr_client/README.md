# fitr_client

Reads HYPR workouts straight from **Fitr** — the app the HYPR coaches write the
programming in — instead of scraping `hypr-workouts.pages.dev`.

The same API backs the official Fitr Android client
(`https://app.fitr.training/mobile/api/v15/`). Auth is an OAuth2 password grant
with the client credentials embedded in Fitr's own app; the access token is
cached and re-minted automatically on a 401.

## Credentials

First match wins:

1. `$FITR_EMAIL` / `$FITR_PASSWORD`
2. `~/.config/fitr/credentials` — `email=…` and `password=…` lines
3. `~/.config/personal-assistant/agents/fitr-login.json` — same format

The session cache is `~/.cache/fitr_client/session.json` (mode 600), keyed to
the email.

## Commands

```bash
python3 fitr_client.py login
python3 fitr_client.py days  --from 2026-11-16 --to 2026-11-30
python3 fitr_client.py show  --date 2026-11-23
python3 fitr_client.py pull  --from 2026-11-01 --to 2026-11-30 --out workouts.json
python3 fitr_client.py history --since 2024-01-01 --out history.json
```

`pull`/`history` write a JSON array of:

```json
{"date": "2026-11-23", "type_key": "wod", "type_label": "🔥WOD",
 "content": "Strength:\n…", "sort_order": 1,
 "schedule_id": 39578059, "section_id": 82041939}
```

`type_key` uses the same mapping as the app (`wod`, `hyrox`, `pump`,
`pump_blend`, `weightlifting`, else a slug). Historical days are cached under
`~/.cache/fitr_client/days/`; today and future days are always refetched.

## Writes (explicit consent only)

```bash
python3 fitr_client.py note     --date 2026-11-23 --type WOD --text "…" --yes
python3 fitr_client.py complete  --date 2026-11-23 --type WOD --yes
python3 fitr_client.py complete  --date 2026-11-23 --type WOD --undo --yes
python3 fitr_client.py comment  --date 2026-11-23 --text "…" --yes
```

Every write refuses without `--yes`. `note` posts a section performance note
(the same field the app's "log performance" writes) and preserves the section's
completed flag; `complete` marks or unmarks the section; `comment` posts to the
day's comment thread.

## API surface used

| Call | Purpose |
|---|---|
| `POST users/sign_in` | form: client_id, client_secret, email, password → access token |
| `GET users/me` | account |
| `GET schedules?from=&to=` | days with section titles, plan and track ids |
| `GET athlete/schedules/{date}/plans/{plan_id}/details?plan_track_id=` | all sections with full description HTML and their attachments — one request per day |
| `GET schedules/{id}/sections/{id}` | one section |
| `GET schedules/{id}/performances` | logged performances |
| `POST schedules/{id}/sections/{id}/performance` | form: `performance[text]`, `complete_section` |
| `POST/DELETE schedules/{id}/sections/{id}/complete` | completed flag |
| `POST schedules/{id}/comments` | day comment thread |

A section carries `has_score`/`score` only when the coach configured scoring.
HYPR's WOD/HYROX/Pump sections do not, so there is no numeric score or
leaderboard entry for them; the note is the only place a result can be written.

Sections also carry `attachments` — movement demo videos. Each has `type`
(`youtube`), `video_id`, `title`, `thumb` (a CDN image) and `src` (the watch
URL); uploaded videos carry `stream_src` instead of a YouTube id. The app stores
them per date/type and shows a Videos strip at the bottom of the journal entry.

## In the app

`FitrApi.kt`, `FitrSecretStore.kt` and `FitrPull.kt` implement the same client
in HC Sync. When Fitr is connected, the app pulls a rolling window
(−14…+28 days) on every sync and **skips the HYPR web page entirely**; the page
is still used only on installs that have not connected Fitr.
