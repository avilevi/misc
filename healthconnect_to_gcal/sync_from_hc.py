#!/usr/bin/env python3
"""
Health Connect → Google Calendar  (via Tasker + Termux)

This script is called by a Tasker task that:
  1. Queries Health Connect for exercise and sleep data
  2. Writes the results to a JSON file (see tasker_hc_query.js for the format)
  3. Invokes this script via Termux:Tasker

This script then reads that JSON file and creates events in a Google Calendar.

Usage:
    python ~/sync_from_hc.py
    python ~/sync_from_hc.py --data /sdcard/health_sync/data.json
    python ~/sync_from_hc.py --dry-run
    python ~/sync_from_hc.py --no-sleep
"""

import argparse
import json
import os
import sys
from datetime import datetime, timezone, timedelta
from pathlib import Path

try:
    from google.oauth2.credentials import Credentials
    from google_auth_oauthlib.flow import InstalledAppFlow
    from google.auth.transport.requests import Request
    from googleapiclient.discovery import build
    from googleapiclient.errors import HttpError
except ImportError:
    msg = (
        "Error: Google API libraries not installed.\n"
        "Run in Termux:  pip install google-api-python-client google-auth-oauthlib"
    )
    # Write to log even if we can't import
    _log_path = Path("/sdcard/health_sync/last_sync.txt")
    _log_path.parent.mkdir(parents=True, exist_ok=True)
    _log_path.write_text(msg + "\n")
    print(msg)
    sys.exit(1)


# ─── Paths ────────────────────────────────────────────────────────────────────

HOME = Path.home()                               # Termux home: ~/.  (private)
SHARED = Path("/sdcard/health_sync")             # Shared with Tasker

DEFAULT_DATA_FILE  = SHARED / "data.json"        # Written by Tasker JS
LOG_FILE           = SHARED / "last_sync.txt"    # Read back by Tasker
TOKEN_FILE         = HOME / "token.json"         # OAuth token (keep private)
CREDENTIALS_FILE   = HOME / "credentials.json"   # OAuth client secret (keep private)

SCOPES        = ["https://www.googleapis.com/auth/calendar"]
CALENDAR_NAME = "Fitness"


# ─── Health Connect type mappings ─────────────────────────────────────────────

# ExerciseSessionRecord.EXERCISE_TYPE_* integer codes → (emoji, label)
HC_EXERCISE_TYPES: dict[int, tuple[str, str]] = {
    0:  ("💪", "Other Workout"),
    2:  ("🏸", "Badminton"),
    4:  ("⚾", "Baseball"),
    5:  ("🏀", "Basketball"),
    6:  ("🚴", "Cycling"),
    7:  ("🚴", "Stationary Bike"),
    8:  ("💪", "Boot Camp"),
    9:  ("🥊", "Boxing"),
    10: ("💪", "Calisthenics"),
    11: ("🏏", "Cricket"),
    12: ("🎿", "Cross-Country Skiing"),
    13: ("💪", "CrossFit"),
    15: ("💃", "Dancing"),
    16: ("🏃", "Elliptical"),
    17: ("🏇", "Equestrian Sports"),
    18: ("🤸", "Exercise Class"),
    19: ("🤺", "Fencing"),
    20: ("🏈", "American Football"),
    21: ("🏉", "Australian Football"),
    23: ("⛳", "Golf"),
    24: ("🧘", "Guided Breathing"),
    25: ("🤸", "Gymnastics"),
    26: ("🤾", "Handball"),
    27: ("⚡", "HIIT"),
    28: ("🥾", "Hiking"),
    29: ("🏒", "Ice Hockey"),
    30: ("⛸️", "Ice Skating"),
    31: ("🪢", "Jump Rope"),
    32: ("🚣", "Kayaking"),
    33: ("💪", "Kettlebell Training"),
    34: ("🥊", "Kickboxing"),
    35: ("🥍", "Lacrosse"),
    36: ("🥋", "Martial Arts"),
    37: ("🚵", "Mountain Biking"),
    39: ("🚣", "Paddling"),
    41: ("🧘", "Pilates"),
    42: ("🎾", "Racquetball"),
    43: ("🧗", "Rock Climbing"),
    45: ("🚣", "Rowing"),
    46: ("🚣", "Rowing Machine"),
    47: ("🏉", "Rugby"),
    48: ("🏃", "Running"),
    49: ("🏃", "Treadmill Running"),
    50: ("⛵", "Sailing"),
    52: ("⛸️", "Skating"),
    53: ("⛷️", "Skiing"),
    54: ("🏂", "Snowboarding"),
    56: ("⚽", "Soccer"),
    58: ("🎾", "Squash"),
    59: ("🪜", "Stair Climbing"),
    60: ("🪜", "Stair Climbing Machine"),
    61: ("🏋️", "Strength Training"),
    62: ("🧘", "Stretching"),
    63: ("🏄", "Surfing"),
    64: ("🏊", "Open Water Swimming"),
    65: ("🏊", "Pool Swimming"),
    66: ("🏓", "Table Tennis"),
    67: ("🎾", "Tennis"),
    68: ("🏐", "Volleyball"),
    69: ("🚶", "Walking"),
    71: ("🏋️", "Weightlifting"),
    72: ("♿", "Wheelchair"),
}

# SleepSessionRecord.StageType integer codes → label
HC_SLEEP_STAGES: dict[int, str] = {
    0: "Unknown",
    1: "Awake",
    2: "Sleeping",     # undifferentiated sleep (older devices)
    3: "Out of Bed",
    4: "Light Sleep",
    5: "Deep Sleep",
    6: "REM",
}


# ─── Logging ──────────────────────────────────────────────────────────────────

_log_lines: list[str] = []

def _log(msg: str = ""):
    """Print to stdout and accumulate for the log file."""
    print(msg)
    _log_lines.append(msg)

def _write_log():
    """Flush accumulated log lines to LOG_FILE (Tasker reads this back)."""
    LOG_FILE.parent.mkdir(parents=True, exist_ok=True)
    LOG_FILE.write_text("\n".join(_log_lines) + "\n")


# ─── JSON input parsing ───────────────────────────────────────────────────────

def parse_dt(value: str | int | float) -> datetime | None:
    """
    Parse a timestamp from the Tasker-written JSON.

    Accepts:
    - ISO 8601 strings:  "2024-03-01T07:14:00Z"  or  "2024-03-01T07:14:00.000+02:00"
    - Epoch milliseconds (int/float): 1709276040000
    - Epoch seconds (int/float < 1e11): 1709276040
    """
    if not value and value != 0:
        return None

    # Numeric: epoch ms or epoch s
    if isinstance(value, (int, float)):
        ts = float(value)
        if ts > 1e11:
            ts /= 1000  # convert ms → s
        return datetime.fromtimestamp(ts, tz=timezone.utc)

    s = str(value).strip()
    if not s:
        return None

    # Numeric string
    if s.lstrip("-").isdigit():
        return parse_dt(float(s))

    # ISO 8601 string — try several formats
    formats = [
        "%Y-%m-%dT%H:%M:%S.%f%z",
        "%Y-%m-%dT%H:%M:%S%z",
        "%Y-%m-%dT%H:%M:%SZ",
        "%Y-%m-%dT%H:%M:%S.%fZ",
        "%Y-%m-%d %H:%M:%S.%f",
        "%Y-%m-%d %H:%M:%S",
    ]
    # Python's %z doesn't handle the colon in +02:00 before 3.7 — normalise it
    normalised = s
    if len(s) > 19 and (s[-6] in ("+", "-")) and s[-3] == ":":
        normalised = s[:-3] + s[-2:]  # "…+05:30" → "…+0530"

    for fmt in formats:
        try:
            dt = datetime.strptime(normalised, fmt)
            if dt.tzinfo is None:
                dt = dt.replace(tzinfo=timezone.utc)
            return dt
        except ValueError:
            continue

    return None


def safe_float(v) -> float | None:
    if v is None or v == "":
        return None
    try:
        return float(v)
    except (ValueError, TypeError):
        return None


def load_health_data(data_file: Path) -> dict:
    """Read and validate the JSON file written by the Tasker task."""
    if not data_file.exists():
        _log(f"Error: data file not found: {data_file}")
        _log("Make sure the Tasker task ran successfully first.")
        _write_log()
        sys.exit(1)

    with open(data_file, encoding="utf-8") as f:
        data = json.load(f)

    gen = data.get("generated_at", "unknown")
    _log(f"Data file generated: {gen}")
    return data


# ─── Event building ───────────────────────────────────────────────────────────

def format_duration(total_minutes: int) -> str:
    if total_minutes >= 60:
        h, m = divmod(total_minutes, 60)
        return f"{h}h {m}min" if m else f"{h}h"
    return f"{total_minutes} min"


def exercise_to_event(ex: dict) -> dict | None:
    """Convert one exercise record dict to a Google Calendar event dict."""
    start = parse_dt(ex.get("start_time"))
    end   = parse_dt(ex.get("end_time"))
    if not start or not end:
        return None

    duration_min = max(1, int((end - start).total_seconds() / 60))

    type_code = int(ex.get("type_code") or 0)
    emoji, label = HC_EXERCISE_TYPES.get(type_code, ("🏃", f"Exercise ({type_code})"))

    # Use the user-supplied title if it's meaningful, otherwise use the type label
    title_raw = (ex.get("title") or "").strip()
    activity  = title_raw if title_raw and title_raw.lower() != label.lower() else label
    summary   = f"{emoji} {activity} – {format_duration(duration_min)}"

    lines = []
    dist = safe_float(ex.get("distance_meters"))
    if dist and dist > 0:
        lines.append(f"Distance: {dist / 1000:.2f} km")

    cal = safe_float(ex.get("energy_calories"))
    if cal and cal > 0:
        lines.append(f"Calories: {int(cal)} kcal")

    avg_hr = safe_float(ex.get("avg_heart_rate_bpm"))
    if avg_hr and avg_hr > 0:
        lines.append(f"Avg HR: {int(avg_hr)} bpm")

    max_hr = safe_float(ex.get("max_heart_rate_bpm"))
    if max_hr and max_hr > 0:
        lines.append(f"Max HR: {int(max_hr)} bpm")

    notes = (ex.get("notes") or "").strip()
    if notes:
        lines.append(f"Notes: {notes}")

    return {
        "summary":     summary,
        "description": "\n".join(lines),
        "start": {"dateTime": start.isoformat()},
        "end":   {"dateTime": end.isoformat()},
    }


def sleep_to_event(sl: dict) -> dict | None:
    """Convert one sleep session dict to a Google Calendar event dict."""
    start = parse_dt(sl.get("start_time"))
    end   = parse_dt(sl.get("end_time"))
    if not start or not end:
        return None

    total_min = max(1, int((end - start).total_seconds() / 60))
    summary   = f"😴 Sleep – {format_duration(total_min)}"

    # Aggregate stage minutes
    stage_totals: dict[str, int] = {}
    for seg in sl.get("stages", []):
        seg_start = parse_dt(seg.get("start_time"))
        seg_end   = parse_dt(seg.get("end_time"))
        code      = int(seg.get("stage_code") or 0)
        name      = HC_SLEEP_STAGES.get(code, f"Stage {code}")

        if seg_start and seg_end:
            mins = int((seg_end - seg_start).total_seconds() / 60)
            stage_totals[name] = stage_totals.get(name, 0) + mins

    lines = []
    if stage_totals:
        lines.append("Sleep stages:")
        order = ["Deep Sleep", "REM", "Light Sleep", "Sleeping", "Awake", "Out of Bed", "Unknown"]
        shown: set[str] = set()
        for stage in order:
            if stage in stage_totals:
                lines.append(f"  {stage}: {format_duration(stage_totals[stage])}")
                shown.add(stage)
        for stage, mins in stage_totals.items():
            if stage not in shown:
                lines.append(f"  {stage}: {format_duration(mins)}")

    return {
        "summary":     summary,
        "description": "\n".join(lines),
        "start": {"dateTime": start.isoformat()},
        "end":   {"dateTime": end.isoformat()},
    }


def build_events(data: dict, skip_exercise: bool, skip_sleep: bool) -> list[dict]:
    events: list[dict] = []

    if not skip_exercise:
        raw = data.get("exercises", [])
        _log(f"  Exercise records : {len(raw)}")
        for ex in raw:
            ev = exercise_to_event(ex)
            if ev:
                events.append(ev)

    if not skip_sleep:
        raw = data.get("sleep_sessions", [])
        _log(f"  Sleep records    : {len(raw)}")
        for sl in raw:
            ev = sleep_to_event(sl)
            if ev:
                events.append(ev)

    return events


# ─── Google Calendar API ──────────────────────────────────────────────────────

def get_calendar_service():
    """Authenticate (OAuth2) and return a Calendar API client."""
    creds = None

    if TOKEN_FILE.exists():
        creds = Credentials.from_authorized_user_file(str(TOKEN_FILE), SCOPES)

    if not creds or not creds.valid:
        if creds and creds.expired and creds.refresh_token:
            creds.refresh(Request())
        else:
            if not CREDENTIALS_FILE.exists():
                _log(f"Error: '{CREDENTIALS_FILE}' not found.")
                _log("Copy credentials.json to your Termux home directory (~/).")
                _log("See ANDROID_SETUP.md for instructions.")
                _write_log()
                sys.exit(1)
            flow = InstalledAppFlow.from_client_secrets_file(
                str(CREDENTIALS_FILE), SCOPES
            )
            # On Android/Termux, use console flow (no local browser)
            creds = flow.run_console()

        TOKEN_FILE.write_text(creds.to_json())

    return build("calendar", "v3", credentials=creds)


def get_or_create_calendar(service, name: str) -> str:
    for cal in service.calendarList().list().execute().get("items", []):
        if cal["summary"] == name:
            return cal["id"]

    new_cal = service.calendars().insert(body={
        "summary":     name,
        "description": "Fitness and sleep data from Samsung Health via Health Connect",
        "timeZone":    "UTC",
    }).execute()
    _log(f"Created calendar: '{name}'")
    return new_cal["id"]


def fetch_existing_keys(service, cal_id: str, time_min: datetime, time_max: datetime) -> set:
    keys: set[tuple[str, str]] = set()
    page_token = None

    while True:
        result = service.events().list(
            calendarId=cal_id,
            timeMin=time_min.isoformat(),
            timeMax=time_max.isoformat(),
            singleEvents=True,
            maxResults=2500,
            pageToken=page_token,
        ).execute()

        for ev in result.get("items", []):
            summary  = ev.get("summary", "")
            start_dt = ev.get("start", {}).get("dateTime", "")
            if summary and start_dt:
                keys.add((summary, start_dt))

        page_token = result.get("nextPageToken")
        if not page_token:
            break

    return keys


def sync_events(service, cal_id: str, events: list[dict], dry_run: bool) -> tuple[int, int]:
    if not events:
        return 0, 0

    starts = [datetime.fromisoformat(e["start"]["dateTime"]) for e in events]
    ends   = [datetime.fromisoformat(e["end"]["dateTime"])   for e in events]
    t_min  = min(starts) - timedelta(days=1)
    t_max  = max(ends)   + timedelta(days=1)

    _log(f"  Checking existing events ({t_min.date()} – {t_max.date()})…")
    existing = fetch_existing_keys(service, cal_id, t_min, t_max)

    created = skipped = 0

    for ev in events:
        key = (ev["summary"], ev["start"]["dateTime"])
        if key in existing:
            skipped += 1
            continue

        if dry_run:
            _log(f"    [DRY RUN] {ev['summary']}  {ev['start']['dateTime']}")
            created += 1
        else:
            try:
                service.events().insert(calendarId=cal_id, body=ev).execute()
                created += 1
            except HttpError as exc:
                _log(f"    Error: {exc} — {ev['summary']}")

    return created, skipped


# ─── CLI ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Sync Health Connect data (via Tasker JSON) to Google Calendar."
    )
    parser.add_argument(
        "--data", "-d",
        default=str(DEFAULT_DATA_FILE),
        metavar="PATH",
        help=f"Path to the JSON file written by Tasker (default: {DEFAULT_DATA_FILE})",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Parse data and show what would be created without touching Google Calendar.",
    )
    parser.add_argument("--no-exercise", action="store_true", help="Skip exercise events.")
    parser.add_argument("--no-sleep",    action="store_true", help="Skip sleep events.")
    parser.add_argument(
        "--auth-only",
        action="store_true",
        help="Just authenticate with Google and save token.json, then exit. "
             "Use this for the one-time setup before Tasker takes over.",
    )

    args = parser.parse_args()

    _log(f"=== Health Connect → Google Calendar  {datetime.now():%Y-%m-%d %H:%M} ===")

    # Auth-only mode: just get a token and exit
    if args.auth_only:
        _log("\nAuthenticating with Google Calendar…")
        service = get_calendar_service()
        _log("Authentication successful! token.json saved.")
        _log("Tasker can now run the script fully automatically.")
        _write_log()
        return

    # Load data
    _log("\nLoading health data…")
    data   = load_health_data(Path(args.data))
    events = build_events(data, args.no_exercise, args.no_sleep)

    if not events:
        _log("\nNo valid events found in data file. Nothing to sync.")
        _write_log()
        return

    _log(f"\nTotal events to process: {len(events)}")

    # Dry run
    if args.dry_run:
        _log("\n[DRY RUN — nothing will be written to Google Calendar]")
        for ev in events:
            _log(f"\n  {ev['summary']}")
            _log(f"    {ev['start']['dateTime']}  →  {ev['end']['dateTime']}")
            for line in ev["description"].splitlines():
                _log(f"    {line}")
        _log(f"\nWould create up to {len(events)} event(s).")
        _write_log()
        return

    # Sync
    _log("\nConnecting to Google Calendar…")
    service = get_calendar_service()
    cal_id  = get_or_create_calendar(service, CALENDAR_NAME)
    _log(f"Calendar: '{CALENDAR_NAME}'")

    _log("\nSyncing…")
    created, skipped = sync_events(service, cal_id, events, dry_run=False)

    _log(f"\nDone!")
    _log(f"  Created : {created}")
    _log(f"  Skipped : {skipped}  (already existed)")

    _write_log()


if __name__ == "__main__":
    main()
