# wod_to_gcal

Populates Google Calendar "WOD" events with workout descriptions from hypr-workouts.pages.dev, generates an HTML page per event, uploads it to Google Drive, and attaches it to the calendar event.

## What it does

1. Scans a Google Calendar (default: "Sports") for events titled exactly "WOD" in the next N days
2. Fetches the workout page from `https://hypr-workouts.pages.dev/`
3. Extracts the 🔥WOD section for each matching date
4. Uploads a styled HTML file to the "HYPR WOD" Google Drive folder
5. Updates the calendar event: sets description + attaches the Drive HTML file
6. Skips events already populated (detected by `# populated by wod_to_gcal` marker in description)

## Files

- `wod_to_gcal.py` — main script
- `requirements.txt` — Python dependencies
- `wod_to_gcal.service` — systemd oneshot service
- `wod_to_gcal.timer` — systemd timer (runs at 06:00 and 18:00 daily)
- `client_secret.json` — OAuth credentials (not committed; see Auth below)
- `token.pickle` — cached OAuth token (not committed; auto-created on first run)

## Usage

```bash
python3 wod_to_gcal.py                  # default: Sports calendar, next 3 days
python3 wod_to_gcal.py --days 7         # look 7 days ahead
python3 wod_to_gcal.py --force          # re-populate already-handled events
python3 wod_to_gcal.py --dry-run        # print what would change, no writes
python3 wod_to_gcal.py --no-drive       # skip Drive upload
python3 wod_to_gcal.py --calendar Foo   # use a different calendar
```

## Auth

Uses OAuth 2.0 — see `/home/avil/git/misc/CLAUDE.md` for the full pattern.

Scopes required:
- `https://www.googleapis.com/auth/calendar`
- `https://www.googleapis.com/auth/drive.file`

Copy `client_secret.json` from `google_calendar_thrusters/` (same GCP project) or create a new OAuth 2.0 Desktop app credential at console.cloud.google.com.

First run: interactive — script prints a URL, you paste the auth code.  
Subsequent runs: token auto-refreshed from `token.pickle`.

## Deployment

Installed as a systemd service+timer under user `avil`:

```bash
sudo cp wod_to_gcal.service wod_to_gcal.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now wod_to_gcal.timer
```
