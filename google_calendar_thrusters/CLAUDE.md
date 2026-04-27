# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Generate ICS only (no credentials needed)
python3 gcal_thruster.py thruster_events.txt

# Import events to Google Calendar
python3 gcal_thruster.py thruster_events.txt --import-events

# Full replace: delete old events and import new ones
python3 gcal_thruster.py thruster_events.txt --delete thruster_program.ics --import-events

# Send progression email (uses msmtp)
python3 gcal_thruster.py thruster_events.txt --email [address]
```

Install dependencies:
```bash
pip install google-api-python-client google-auth-httplib2 google-auth-oauthlib
```

## Architecture

Single-file script (`gcal_thruster.py`) with five logical sections:

1. **Auth** — OAuth 2.0 flow via `client_secret.json` + `token.pickle`. First run prints an auth URL and prompts for a code (OOB flow, works across machines). Token is cached as a pickle.

2. **Events file parsing** — `thruster_events.txt` uses a custom plain-text format: `date`/`summary`/`description` fields per block, blocks separated by `\n---\n`. The `description` field captures all remaining lines until the next separator.

3. **ICS generation** — Produces RFC 5545-compliant ICS with UUID-stamped VEVENTs (all-day). UIDs are stored on the event dicts for later use during import (`events().import_()` vs `events().insert()`).

4. **Google Calendar operations** — Deletion matches by summary text extracted from an ICS (searches by last word of each unique summary to reduce API calls). Import uses `events().import_()` when a UID is present to preserve idempotency.

5. **Email** — Parses `Week N` and `Exercise: SxR @ Wkg` patterns from event descriptions to build a per-exercise progression HTML table. Sent via `msmtp` subprocess.

## Key details

- Default calendar: `Sports`; default email: `avi.levi99@gmail.com`
- `client_secret.json` and `token.pickle` are gitignored — never committed
- Generated `.ics` files are also gitignored
- Deload week detection: a week where Thruster weight drops vs. the previous non-deload week
- Exercise order in the email is fixed (see `all_exercises` list in `_build_progression_html`)
