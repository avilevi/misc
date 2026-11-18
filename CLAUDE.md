# misc utilities

Personal utility scripts living under `/home/avil/git/misc/`. Each subdirectory is an independent utility.

## Utilities

| Directory | What it does |
|-----------|--------------|
| `wod_to_gcal/` | Populates Google Calendar "WOD" events with workout content from hypr-workouts.pages.dev; uploads HTML to Google Drive and attaches it |
| `google_calendar_thrusters/` | Manages a multi-week Thruster lifting program in Google Calendar: parses an events text file, generates ICS, imports to calendar, uploads per-event HTML to Drive, emails a progression plan |
| `email_cleanup/` | Gmail cleanup: classifies emails with Claude AI and applies labels via the Gmail API (OAuth, `token.json`) |
| `base44_to_elementor/` | Converts base44 page data to Elementor-compatible JSON for WordPress import |
| `keren_hish_calculations/` | Standalone calculation scripts (no external APIs) |
| `outlook_to_gcal_sync/` | Outlook → Google Calendar sync (in progress / not yet implemented) |
| `healthconnect_to_gcal/` | Android app: reads Health Connect (sleep + exercise) and writes to Google Calendar via CalendarContract (no OAuth). One **Sync now** action runs website → SQLite workout DB → calendar → journal → watch snapshot, in that order. A 02:00 alarm pulls **all** dates/types from hypr-workouts into the DB (rows survive the site dropping a date); AlarmManager schedules syncs (exact once the user grants Alarms & reminders, otherwise inexact). **Phone UI (2026-11-17)**: home is three destinations — **Journal**, **Workouts**, **Records** — all in Jetpack Compose (Barlow). The journal is now one entry per workout: the AI plan with the values entered (or the original workout text), the watch/strap data merged from Health Connect, and free comments stored in `journal.db`; a workout only earns an entry when an event on any calendar names it, a watch session was recorded, or values were logged into its AI workout (everything qualifying merges; watch-only sessions and sleep are their own entries, and a month grid marks workouts, watch data, sleep and notes). Workouts is a week/month/day calendar over the DB (week default; changed in Settings) plus a **History** archive with every pulled workout by year/month and a CSV export; `workouts.db`/`records.db` are in Android backup. Records adds `records.db` with a lifts × 1RM/2RM/3RM table (detail accepts any RM) and benchmark histories, manual or quick-added from a workout, each linked back to its source workout. **AI workout** turns a workout into interactive sections/sets/metrics through a configurable provider — Claude, OpenAI, DeepSeek, Kimi, Gemini or a custom endpoint (Anthropic, OpenAI-compatible or native Gemini protocols; per-provider endpoint/model/key). DeepSeek uses `deepseek-flash` by default or `deepseek-v4-pro` (the old `deepseek-chat`/`deepseek-reasoner` aliases were retired 2026-07-24; V4 thinking is on by default). Plans+values live in `interactive.db`; every key is encrypted in the Android Keystore; only workout date/type/text are sent. Plans support multi-movement **rounds** (one round = several exercises: a 5-round snatch + squats + push-ups triplet is one round with three movements, not three exercises) and **check-only** work (`✓ Done`) for fixed intervals/holds such as a 40s/20s EMOM — no number to record. Prescribed reps/time pre-fill from the set's target (weight always stays open), time results are picked on a minutes/seconds wheel, and older circuits stored as separate exercises are regrouped when all share the same `Round N` labels. **Settings** is a Compose hamburger drawer: Calendar, Sync & reliability, Workouts & watch, AI workout, Diagnostics, About. **Wear OS companion** (`wear/`, same `applicationId` + signing key): workouts by date/type and the workout journal with comments, from a gzipped/chunked Data Layer snapshot cached on the watch. CI builds signed phone + wear APKs to GitHub Releases with self-update support (`app-release.apk`, `wear-release.apk`). Journal stores raw data append-only; display filters hide covered WOD entries. See its `README.md`. **Superseded as the Android reference — see `child-supervision/CLAUDE.md`.** |

## Google Calendar / Drive API — how it works

All Google Calendar (and Drive) utilities in this repo use the same OAuth 2.0 pattern. Use this as the template when building new ones.

### Packages

```
google-api-python-client>=2.0
google-auth>=2.0
google-auth-oauthlib>=1.0
```

### Credentials file

`client_secret.json` — OAuth 2.0 Desktop app credential downloaded from Google Cloud Console.

- Project: console.cloud.google.com
- APIs to enable: Google Calendar API, Google Drive API (if needed)
- Credential type: **OAuth client ID → Desktop app**
- Download JSON → save as `client_secret.json` in the script directory

The authoritative `client_secret.json` lives in `google_calendar_thrusters/`. Other utilities that share the same GCP project copy it from there.

### Token caching

`token.pickle` — cached OAuth token, auto-created after first interactive auth. Excluded from git.

### Auth pattern (copy-paste this)

```python
import pickle
from pathlib import Path
from google.auth.transport.requests import Request
from google_auth_oauthlib.flow import InstalledAppFlow
from googleapiclient.discovery import build

SCOPES = ['https://www.googleapis.com/auth/calendar']
SCRIPT_DIR = Path(__file__).parent
DEFAULT_CREDENTIALS = SCRIPT_DIR / 'client_secret.json'
DEFAULT_TOKEN = SCRIPT_DIR / 'token.pickle'

def get_credentials(credentials_path=DEFAULT_CREDENTIALS, token_path=DEFAULT_TOKEN):
    creds = None
    if Path(token_path).exists():
        with open(token_path, 'rb') as f:
            creds = pickle.load(f)
    if not creds or not creds.valid:
        if creds and creds.expired and creds.refresh_token:
            try:
                creds.refresh(Request())
            except Exception:
                Path(token_path).unlink(missing_ok=True)
                creds = None
        if not creds or not creds.valid:
            flow = InstalledAppFlow.from_client_secrets_file(
                str(credentials_path), SCOPES,
                redirect_uri='urn:ietf:wg:oauth:2.0:oob',  # copy-paste flow, no browser redirect
            )
            auth_url, _ = flow.authorization_url(prompt='consent')
            print('Open this URL:\n', auth_url)
            code = input('Paste the authorization code: ').strip()
            flow.fetch_token(code=code)
            creds = flow.credentials
        with open(token_path, 'wb') as f:
            pickle.dump(creds, f)
    return creds

# Build service clients
creds = get_credentials()
cal_service   = build('calendar', 'v3', credentials=creds)
drive_service = build('drive',    'v3', credentials=creds)
```

### Common scopes

| Scope | Use |
|-------|-----|
| `https://www.googleapis.com/auth/calendar` | Full read/write on all calendars |
| `https://www.googleapis.com/auth/calendar.readonly` | Read-only |
| `https://www.googleapis.com/auth/drive.file` | Create/update files this app created |
| `https://www.googleapis.com/auth/drive` | Full Drive access |

### Key API calls

```python
# List calendars, find by name
calendars = cal_service.calendarList().list().execute()['items']
cal_id = next(c['id'] for c in calendars if c['summary'].lower() == 'sports')

# Query events
events = cal_service.events().list(
    calendarId=cal_id,
    timeMin=..., timeMax=...,
    q='search term',
    singleEvents=True, orderBy='startTime',
).execute()['items']

# Create event
cal_service.events().insert(calendarId=cal_id, body={...}, supportsAttachments=True).execute()

# Update event
cal_service.events().update(calendarId=cal_id, eventId=event['id'], body=event, supportsAttachments=True).execute()

# Upload file to Drive (in-memory)
from googleapiclient.http import MediaInMemoryUpload
media = MediaInMemoryUpload(content.encode('utf-8'), mimetype='text/html')
file = drive_service.files().create(
    body={'name': 'file.html', 'parents': [folder_id], 'mimeType': 'text/html'},
    media_body=media, fields='id,webViewLink',
).execute()

# Attach Drive file to calendar event
event['attachments'] = [{'fileUrl': file['webViewLink'], 'mimeType': 'text/html', 'title': 'file.html'}]
```

### First-run auth flow

The `redirect_uri='urn:ietf:wg:oauth:2.0:oob'` means no local HTTP server is needed. The script prints a URL → user opens it in a browser → Google shows a code → user pastes it back. On a headless server this is the right approach.
