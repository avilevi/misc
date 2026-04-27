# Google Calendar Thruster Program Manager

A CLI tool that manages a multi-week workout program in Google Calendar.
It reads a structured plain-text events file, generates an ICS, and can
import/delete events via the Google Calendar API and email a per-exercise
HTML progression table.

---

## Files in this repository

| File | Description |
|------|-------------|
| `gcal_thruster.py` | Main script |
| `thruster_events.txt` | 39-week thruster program event data |
| `client_secret.example.json` | Template showing the required credentials format |
| `.gitignore` | Excludes `client_secret.json`, `token.pickle`, and generated `.ics` files |

---

## Prerequisites

### Python packages

```bash
pip install google-api-python-client google-auth-httplib2 google-auth-oauthlib
```

### msmtp (for email sending)

msmtp must be installed and configured with a valid Gmail account in `~/.msmtprc`.
See the [msmtp documentation](https://marlam.de/msmtp/) for setup.

---

## Google API Setup

The script uses the Google Calendar API with OAuth 2.0. You need to create
credentials once and place the file next to the script.

1. Go to [Google Cloud Console](https://console.cloud.google.com/).
2. Create or select a project.
3. Enable the **Google Calendar API**:
   - APIs & Services → Library → search "Google Calendar API" → Enable
4. Create OAuth 2.0 credentials:
   - APIs & Services → Credentials → Create Credentials → **OAuth client ID**
   - Application type: **Desktop app**
5. Download the JSON file and save it as **`client_secret.json`** in the same
   directory as `gcal_thruster.py`.

The file must match the structure shown in `client_secret.example.json`:

```json
{
  "installed": {
    "client_id": "YOUR_CLIENT_ID.apps.googleusercontent.com",
    "project_id": "your-project-id",
    "auth_uri": "https://accounts.google.com/o/oauth2/auth",
    "token_uri": "https://oauth2.googleapis.com/token",
    "auth_provider_x509_cert_url": "https://www.googleapis.com/oauth2/v1/certs",
    "client_secret": "YOUR_CLIENT_SECRET",
    "redirect_uris": ["http://localhost"]
  }
}
```

### First-run authorization

The first time the script connects to Google Calendar it will print an
authorization URL. Open it in any browser (can be a different machine),
approve access, and paste the code back into the terminal.
The token is saved to `token.pickle` and reused automatically on subsequent runs.

> **Note:** `client_secret.json` and `token.pickle` are excluded from git
> (see `.gitignore`) because they contain sensitive credentials.

---

## Events file format

Events are defined in a plain-text file, one block per event, separated by `---`.

```
date: 2026-03-29
summary: Week 1 - Day A - Thruster
description:
  Week 1
  Thruster: 5x3 @ 40kg
  Front Squat: 4x4 @ 67.5kg
  Romanian Deadlift: 3x8 @ 60kg
  Pull-ups: 3 sets to failure
---
date: 2026-03-31
summary: Week 1 - Day B - Press and Pull
description:
  Week 1
  Push Press: 4x5 @ 50kg
  Overhead Press: 3x6 @ 45kg
  Bench Press: 3x8 @ 56kg
  Barbell Row: 4x6 @ 42.5kg
---
```

Each event becomes a Google Calendar **all-day event**.
The `description` field takes all indented lines that follow it until the next `---`.

To adjust weights, edit `thruster_events.txt` and re-run the script.

---

## Usage

### Generate ICS only

Parses the events file and writes `thruster_program.ics` (default output name).
No Google credentials needed.

```bash
python3 gcal_thruster.py thruster_events.txt
```

Custom output path:

```bash
python3 gcal_thruster.py thruster_events.txt --output my_program.ics
```

---

### Import events to Google Calendar

Generates the ICS and imports all events into the named calendar.

```bash
python3 gcal_thruster.py thruster_events.txt --import-events
```

Use a different calendar (default is `Sports`):

```bash
python3 gcal_thruster.py thruster_events.txt --import-events --calendar "My Workouts"
```

---

### Delete existing events from Google Calendar

Deletes calendar events whose titles match those found in the provided ICS file.
Useful for removing a previously imported program before replacing it.

```bash
python3 gcal_thruster.py --delete thruster_program.ics
```

---

### Full replace (delete old + import new)

The standard workflow when updating the program. Generate a new ICS from the
updated events file, delete all matching old events, then import the new ones.

```bash
python3 gcal_thruster.py thruster_events.txt \
  --delete thruster_program.ics \
  --import-events
```

> **Tip:** Run without `--delete` first to generate the ICS, then use that
> ICS as the `--delete` reference when you're ready to replace.

---

### Send a progression email

Generates a styled HTML email showing each exercise's week-by-week weight
progression (with progress bars and deload week highlights) and sends it
via msmtp.

Default recipient (`avi.levi99@gmail.com`):

```bash
python3 gcal_thruster.py thruster_events.txt --email
```

Custom recipient:

```bash
python3 gcal_thruster.py thruster_events.txt --email someone@example.com
```

---

### All options at once

Generate ICS, delete old calendar events, import new ones, and send the email:

```bash
python3 gcal_thruster.py thruster_events.txt \
  --delete thruster_program.ics \
  --import-events \
  --email
```

---

## All arguments

| Argument | Description | Default |
|----------|-------------|---------|
| `events_file` | Structured events text file | *(required unless using `--delete` only)* |
| `--output FILE` | Output ICS file path | `thruster_program.ics` |
| `--import-events` | Import events into Google Calendar | off |
| `--delete ICS_FILE` | Delete calendar events matching this ICS | — |
| `--email [ADDRESS]` | Send progression email | `avi.levi99@gmail.com` |
| `--calendar NAME` | Google Calendar name to use | `Sports` |
| `--credentials FILE` | Path to `client_secret.json` | `./client_secret.json` |
| `--token FILE` | Path to OAuth token pickle file | `./token.pickle` |
