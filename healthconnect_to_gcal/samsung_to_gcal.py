#!/usr/bin/env python3
"""
Samsung Health → Google Calendar

Reads workout and sleep CSV exports from Samsung Health and creates
Google Calendar events in a dedicated "Fitness" calendar.

Usage:
    python samsung_to_gcal.py /path/to/export_folder
    python samsung_to_gcal.py /path/to/export_folder --inspect
    python samsung_to_gcal.py /path/to/export_folder --timezone America/New_York
    python samsung_to_gcal.py /path/to/export_folder --dry-run
"""

import argparse
import csv
import json
import os
import sys
from datetime import datetime, timezone, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

try:
    from google.oauth2.credentials import Credentials
    from google_auth_oauthlib.flow import InstalledAppFlow
    from google.auth.transport.requests import Request
    from googleapiclient.discovery import build
    from googleapiclient.errors import HttpError
except ImportError:
    print("Error: Required Google API libraries not installed.")
    print("Run: pip install google-api-python-client google-auth-oauthlib")
    sys.exit(1)

# ─── Constants ────────────────────────────────────────────────────────────────

SCOPES = ["https://www.googleapis.com/auth/calendar"]
TOKEN_FILE = "token.json"
CREDENTIALS_FILE = "credentials.json"
CALENDAR_NAME = "Fitness"

# Samsung Health exercise type codes → (emoji, display name)
# Based on known Samsung Health export values; unknown codes fall back to
# "Exercise (<code>)". Run --inspect on your export to check actual values.
EXERCISE_TYPES = {
    1001: ("🚶", "Walking"),
    1002: ("🏃", "Running"),
    1003: ("🚴", "Cycling"),
    1004: ("🥾", "Hiking"),
    1005: ("🧗", "Mountain Climbing"),
    1006: ("🏊", "Open Water Swimming"),
    1007: ("🚣", "Rowing"),
    1008: ("⛷️", "Skiing"),
    1009: ("🎿", "Cross-Country Skiing"),
    1010: ("⛸️", "Ice Skating"),
    1011: ("🛼", "Inline Skating"),
    1012: ("🛹", "Skateboarding"),
    1013: ("🏄", "Surfing"),
    1014: ("🏇", "Horse Riding"),
    1015: ("⛳", "Golf"),
    1016: ("⚾", "Baseball"),
    1017: ("🏀", "Basketball"),
    1018: ("🏐", "Volleyball"),
    1019: ("⚽", "Soccer"),
    1020: ("🤾", "Handball"),
    1021: ("🏸", "Badminton"),
    1022: ("🎾", "Tennis"),
    1023: ("🏓", "Table Tennis"),
    1024: ("🎾", "Squash"),
    1025: ("🎾", "Racquetball"),
    1026: ("🏏", "Cricket"),
    1027: ("🏒", "Ice Hockey"),
    1028: ("🏑", "Field Hockey"),
    1029: ("🏉", "Rugby"),
    1030: ("🥊", "Boxing"),
    1031: ("🥊", "Kickboxing"),
    1032: ("🥋", "Judo"),
    1033: ("🥋", "Taekwondo"),
    1034: ("🥋", "Martial Arts"),
    1035: ("🤼", "Wrestling"),
    1036: ("🤺", "Fencing"),
    1037: ("🏋️", "Gym & Fitness"),
    1038: ("🧘", "Pilates"),
    1039: ("🧘", "Yoga"),
    1040: ("💃", "Dancing"),
    1041: ("🤸", "Aerobics"),
    1042: ("🚴", "Exercise Bike"),
    1043: ("🏃", "Treadmill"),
    1044: ("🚣", "Rowing Machine"),
    1045: ("🏃", "Elliptical Trainer"),
    1046: ("🪜", "Stair Climbing"),
    1047: ("🪢", "Jump Rope"),
    1048: ("🧘", "Stretching"),
    1049: ("🏊", "Pool Swimming"),
    1050: ("💪", "Other"),
    2001: ("🏊", "Swimming"),
    3001: ("🧘", "Pilates"),
    3002: ("🧘", "Yoga"),
    3003: ("💪", "Strength Training"),
    3004: ("🤸", "Aerobics"),
    3005: ("💃", "Dancing"),
    3006: ("🏃", "Elliptical Trainer"),
    3007: ("🚴", "Stationary Bike"),
    3008: ("🏃", "Treadmill"),
    3009: ("🧗", "Rock Climbing"),
    3010: ("🏄", "Surfing"),
    3011: ("💪", "CrossFit"),
    3012: ("💪", "Functional Training"),
    10000: ("💪", "Other Workout"),
}

# Sleep stage codes → human-readable name
SLEEP_STAGES = {
    "0": "Awake",
    "1": "Light Sleep",
    "2": "Deep Sleep",
    "3": "REM",
    "4": "Awake",       # alternate awake code in some versions
    "40001": "Awake",
    "40002": "Light Sleep",
    "40003": "Deep Sleep",
    "40004": "REM",
}


# ─── CSV Parsing ──────────────────────────────────────────────────────────────

def parse_samsung_csv(filepath: Path) -> tuple[list[str], list[dict]]:
    """
    Parse a Samsung Health CSV file.

    Samsung Health prepends comment/metadata lines starting with '#' before
    the actual CSV data. This function skips those and returns the headers
    and data rows as a list of dicts.
    """
    with open(filepath, "r", encoding="utf-8-sig") as f:
        raw = f.read()

    # Strip comment lines and blank lines, keep only CSV content
    data_lines = [
        line for line in raw.splitlines()
        if line.strip() and not line.strip().startswith("#")
    ]

    if not data_lines:
        return [], []

    reader = csv.DictReader(data_lines)
    headers = list(reader.fieldnames or [])
    rows = [dict(row) for row in reader]
    return headers, rows


def normalize_row(row: dict) -> dict:
    """
    Return a new dict with simplified keys.

    Samsung Health sometimes prefixes column names with a package path like
    'com.samsung.shealth.exercise.start_time'. This strips the prefix and
    returns just 'start_time'. Also strips surrounding whitespace from values.
    """
    result = {}
    for key, value in row.items():
        if key is None:
            continue
        clean_key = key.strip()
        # Keep the last segment if there's a dot-separated namespace prefix
        if "." in clean_key:
            clean_key = clean_key.split(".")[-1]
        result[clean_key] = value.strip() if isinstance(value, str) else (value or "")
    return result


def parse_dt(dt_str: str, tz=None) -> datetime | None:
    """
    Parse a datetime string from Samsung Health into a timezone-aware datetime.

    Tries several common formats. If the parsed datetime is naive, it is
    assigned the provided timezone (or UTC if none given).
    """
    if not dt_str or not dt_str.strip():
        return None

    s = dt_str.strip()
    formats = [
        "%Y-%m-%d %H:%M:%S.%f",
        "%Y-%m-%d %H:%M:%S",
        "%Y-%m-%dT%H:%M:%S.%f",
        "%Y-%m-%dT%H:%M:%S",
        "%Y-%m-%dT%H:%M:%SZ",
    ]
    for fmt in formats:
        try:
            dt = datetime.strptime(s, fmt)
            if dt.tzinfo is None:
                dt = dt.replace(tzinfo=tz or timezone.utc)
            return dt
        except ValueError:
            continue
    return None


def safe_float(value) -> float | None:
    """Convert to float, returning None on failure."""
    if value is None or value == "":
        return None
    try:
        return float(value)
    except (ValueError, TypeError):
        return None


# ─── File Discovery ───────────────────────────────────────────────────────────

def find_csv_files(export_folder: str) -> dict:
    """
    Walk the Samsung Health export folder and categorize CSV files by type.
    Returns a dict with keys: 'exercise', 'sleep', 'sleep_stage', 'other'.
    """
    folder = Path(export_folder)
    if not folder.exists():
        print(f"Error: Folder not found: {export_folder}")
        sys.exit(1)

    files = {"exercise": [], "sleep": [], "sleep_stage": [], "other": []}

    for csv_file in sorted(folder.rglob("*.csv")):
        name = csv_file.name.lower()
        if "exercise" in name and "stage" not in name:
            files["exercise"].append(csv_file)
        elif "sleep_stage" in name or "sleep.stage" in name:
            files["sleep_stage"].append(csv_file)
        elif "sleep" in name:
            files["sleep"].append(csv_file)
        else:
            files["other"].append(csv_file)

    return files


# ─── Inspect Mode ─────────────────────────────────────────────────────────────

def inspect_csvs(export_folder: str):
    """Print headers (and a sample row) for every CSV in the export folder."""
    files = find_csv_files(export_folder)
    all_files = [
        (cat, f)
        for cat, file_list in files.items()
        for f in file_list
    ]

    if not all_files:
        print(f"No CSV files found in: {export_folder}")
        return

    total = sum(len(v) for v in files.values())
    print(f"\nFound {total} CSV file(s) in: {export_folder}\n")
    print("=" * 72)

    for category, filepath in all_files:
        print(f"\n[{category.upper()}]  {filepath.name}")
        try:
            headers, rows = parse_samsung_csv(filepath)
            print(f"  Rows   : {len(rows)}")
            print(f"  Headers: {', '.join(headers)}")
            if rows:
                sample = normalize_row(rows[0])
                # Show first 6 key=value pairs
                preview = {k: v for k, v in list(sample.items())[:6]}
                print(f"  Sample : {preview}")
        except Exception as exc:
            print(f"  Error reading: {exc}")

    print("\n" + "=" * 72)
    print("\nTip: use --timezone <tz> if timestamps look wrong (e.g. America/New_York)")


# ─── Data Loading ─────────────────────────────────────────────────────────────

def load_exercises(export_folder: str, tz=None) -> list[dict]:
    """Parse all exercise CSV files and return a list of exercise dicts."""
    files = find_csv_files(export_folder)
    exercises = []

    for filepath in files["exercise"]:
        try:
            _, rows = parse_samsung_csv(filepath)
        except Exception as exc:
            print(f"  Warning: could not read {filepath.name}: {exc}")
            continue

        for row in rows:
            norm = normalize_row(row)

            start = parse_dt(norm.get("start_time", ""), tz)
            end = parse_dt(norm.get("end_time", ""), tz)

            if not start:
                continue  # skip rows with no usable start time

            # Duration: prefer explicit 'duration' column (milliseconds),
            # otherwise derive from start/end times.
            duration_ms = safe_float(norm.get("duration"))
            if duration_ms is None and end and start:
                duration_ms = (end - start).total_seconds() * 1000

            if end is None and duration_ms is not None:
                end = start + timedelta(milliseconds=duration_ms)

            if not end:
                continue

            duration_min = int(duration_ms / 60_000) if duration_ms else 0

            exercises.append({
                "start_time": start,
                "end_time": end,
                "exercise_type": norm.get("exercise_type", ""),
                "duration_min": duration_min,
                "distance_m": safe_float(norm.get("distance")),
                "calories": safe_float(norm.get("calorie") or norm.get("calories")),
                "mean_heart_rate": safe_float(norm.get("mean_heart_rate")),
                "max_heart_rate": safe_float(norm.get("max_heart_rate")),
                "comment": norm.get("comment", ""),
            })

    return exercises


def load_sleep(export_folder: str, tz=None) -> list[dict]:
    """
    Parse sleep and sleep-stage CSV files and return a list of sleep dicts.

    Sleep stage data is linked to sleep sessions by a shared ID column when
    available, or by matching the date (fallback).
    """
    files = find_csv_files(export_folder)

    # ── Load stage data ──────────────────────────────────────────────────────
    # Key: session_id (or YYYY-MM-DD date string) → list of stage segments
    stage_data: dict[str, list[dict]] = {}

    for filepath in files["sleep_stage"]:
        try:
            _, rows = parse_samsung_csv(filepath)
        except Exception as exc:
            print(f"  Warning: could not read {filepath.name}: {exc}")
            continue

        for row in rows:
            norm = normalize_row(row)
            seg_start = parse_dt(norm.get("start_time", ""), tz)
            seg_end = parse_dt(norm.get("end_time", ""), tz)
            stage = norm.get("stage") or norm.get("sleep_stage") or ""

            # Try known session-link columns; fall back to date string
            session_key = (
                norm.get("sleep_id")
                or norm.get("combined_id")
                or norm.get("sessionid")
            )
            if not session_key and seg_start:
                session_key = seg_start.strftime("%Y-%m-%d")

            if session_key:
                stage_data.setdefault(session_key, []).append({
                    "start": seg_start,
                    "end": seg_end,
                    "stage": str(stage).strip(),
                })

    # ── Load main sleep sessions ─────────────────────────────────────────────
    sleep_records = []

    for filepath in files["sleep"]:
        try:
            _, rows = parse_samsung_csv(filepath)
        except Exception as exc:
            print(f"  Warning: could not read {filepath.name}: {exc}")
            continue

        for row in rows:
            norm = normalize_row(row)

            start = parse_dt(norm.get("start_time", ""), tz)
            end = parse_dt(norm.get("end_time", ""), tz)

            if not start or not end:
                continue

            # Link to stage data
            session_key = (
                norm.get("sleep_id")
                or norm.get("combined_id")
                or norm.get("sessionid")
                or start.strftime("%Y-%m-%d")
            )
            stages = stage_data.get(session_key, [])

            # Aggregate stage durations (minutes per stage name)
            stage_summary: dict[str, int] = {}
            for seg in stages:
                code = seg["stage"]
                name = SLEEP_STAGES.get(code, f"Stage {code}" if code else "Unknown")
                if seg["start"] and seg["end"]:
                    seg_min = int((seg["end"] - seg["start"]).total_seconds() / 60)
                    stage_summary[name] = stage_summary.get(name, 0) + seg_min

            total_min = int((end - start).total_seconds() / 60)

            sleep_records.append({
                "start_time": start,
                "end_time": end,
                "hours": total_min // 60,
                "minutes": total_min % 60,
                "stage_summary": stage_summary,
                "quality": safe_float(norm.get("quality")),
            })

    return sleep_records


# ─── Event Formatting ─────────────────────────────────────────────────────────

def get_exercise_label(exercise_type: str) -> tuple[str, str]:
    """Return (emoji, name) for a Samsung Health exercise type code."""
    try:
        code = int(exercise_type)
        return EXERCISE_TYPES.get(code, ("🏃", f"Exercise ({code})"))
    except (ValueError, TypeError):
        return ("🏃", str(exercise_type) if exercise_type else "Workout")


def format_duration(total_minutes: int) -> str:
    """Format a duration in minutes as a human-readable string."""
    if total_minutes >= 60:
        h, m = divmod(total_minutes, 60)
        return f"{h}h {m}min" if m else f"{h}h"
    return f"{total_minutes} min"


def format_exercise_event(ex: dict) -> dict:
    """Return a Google Calendar event dict for an exercise record."""
    emoji, name = get_exercise_label(ex["exercise_type"])
    title = f"{emoji} {name} – {format_duration(ex['duration_min'])}"

    lines = []
    if ex["distance_m"] and ex["distance_m"] > 0:
        lines.append(f"Distance: {ex['distance_m'] / 1000:.2f} km")
    if ex["calories"] and ex["calories"] > 0:
        lines.append(f"Calories: {int(ex['calories'])} kcal")
    if ex["mean_heart_rate"] and ex["mean_heart_rate"] > 0:
        lines.append(f"Avg HR: {int(ex['mean_heart_rate'])} bpm")
    if ex["max_heart_rate"] and ex["max_heart_rate"] > 0:
        lines.append(f"Max HR: {int(ex['max_heart_rate'])} bpm")
    if ex["comment"]:
        lines.append(f"Notes: {ex['comment']}")

    return {
        "summary": title,
        "description": "\n".join(lines),
        "start": {"dateTime": ex["start_time"].isoformat()},
        "end": {"dateTime": ex["end_time"].isoformat()},
    }


def format_sleep_event(sl: dict) -> dict:
    """Return a Google Calendar event dict for a sleep record."""
    title = f"😴 Sleep – {format_duration(sl['hours'] * 60 + sl['minutes'])}"

    lines = []
    if sl["stage_summary"]:
        lines.append("Sleep stages:")
        # Order: Deep → REM → Light → Awake
        order = ["Deep Sleep", "REM", "Light Sleep", "Awake"]
        shown = set()
        for stage in order:
            if stage in sl["stage_summary"]:
                lines.append(f"  {stage}: {format_duration(sl['stage_summary'][stage])}")
                shown.add(stage)
        for stage, mins in sl["stage_summary"].items():
            if stage not in shown:
                lines.append(f"  {stage}: {format_duration(mins)}")

    if sl["quality"] is not None:
        lines.append(f"Quality score: {int(sl['quality'])}")

    return {
        "summary": title,
        "description": "\n".join(lines),
        "start": {"dateTime": sl["start_time"].isoformat()},
        "end": {"dateTime": sl["end_time"].isoformat()},
    }


# ─── Google Calendar API ──────────────────────────────────────────────────────

def get_calendar_service():
    """Authenticate via OAuth2 and return an authorized Calendar API client."""
    creds = None

    if os.path.exists(TOKEN_FILE):
        creds = Credentials.from_authorized_user_file(TOKEN_FILE, SCOPES)

    if not creds or not creds.valid:
        if creds and creds.expired and creds.refresh_token:
            creds.refresh(Request())
        else:
            if not os.path.exists(CREDENTIALS_FILE):
                print(f"Error: '{CREDENTIALS_FILE}' not found.")
                print(
                    "Download OAuth credentials from Google Cloud Console → "
                    "APIs & Services → Credentials → Create credentials → "
                    "OAuth client ID (Desktop app), then save as credentials.json."
                )
                sys.exit(1)
            flow = InstalledAppFlow.from_client_secrets_file(CREDENTIALS_FILE, SCOPES)
            creds = flow.run_local_server(port=0)

        with open(TOKEN_FILE, "w") as f:
            f.write(creds.to_json())

    return build("calendar", "v3", credentials=creds)


def get_or_create_calendar(service, name: str) -> str:
    """Return the calendar ID for a calendar with the given name, creating it if needed."""
    calendars = service.calendarList().list().execute()
    for cal in calendars.get("items", []):
        if cal["summary"] == name:
            return cal["id"]

    new_cal = service.calendars().insert(body={
        "summary": name,
        "description": "Fitness and sleep data synced from Samsung Health",
        "timeZone": "UTC",
    }).execute()
    print(f"Created calendar: '{name}'")
    return new_cal["id"]


def fetch_existing_keys(service, calendar_id: str, time_min: datetime, time_max: datetime) -> set:
    """
    Return a set of (summary, start_dateTime_iso) tuples for events already
    in the calendar within the given time window. Used for deduplication.
    """
    keys = set()
    page_token = None

    while True:
        result = service.events().list(
            calendarId=calendar_id,
            timeMin=time_min.isoformat(),
            timeMax=time_max.isoformat(),
            singleEvents=True,
            maxResults=2500,
            pageToken=page_token,
        ).execute()

        for event in result.get("items", []):
            summary = event.get("summary", "")
            start_dt = event.get("start", {}).get("dateTime", "")
            if summary and start_dt:
                keys.add((summary, start_dt))

        page_token = result.get("nextPageToken")
        if not page_token:
            break

    return keys


def sync_events(service, calendar_id: str, events: list[dict], dry_run: bool = False) -> tuple[int, int]:
    """
    Create calendar events, skipping any that already exist.
    Returns (created_count, skipped_count).
    """
    if not events:
        return 0, 0

    starts = [datetime.fromisoformat(e["start"]["dateTime"]) for e in events]
    ends = [datetime.fromisoformat(e["end"]["dateTime"]) for e in events]
    window_min = min(starts) - timedelta(days=1)
    window_max = max(ends) + timedelta(days=1)

    print(f"  Checking existing events ({window_min.date()} – {window_max.date()})…")
    existing = fetch_existing_keys(service, calendar_id, window_min, window_max)

    created = skipped = 0

    for event in events:
        key = (event["summary"], event["start"]["dateTime"])
        if key in existing:
            skipped += 1
            continue

        if dry_run:
            print(f"    [DRY RUN] {event['summary']}  {event['start']['dateTime']}")
            created += 1
        else:
            try:
                service.events().insert(calendarId=calendar_id, body=event).execute()
                created += 1
            except HttpError as exc:
                print(f"    Error creating '{event['summary']}': {exc}")

    return created, skipped


# ─── CLI Entry Point ──────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Sync Samsung Health workout and sleep data to Google Calendar.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument(
        "export_folder",
        nargs="?",
        help="Path to the extracted Samsung Health export folder.",
    )
    parser.add_argument(
        "--inspect",
        action="store_true",
        help="Print CSV headers and sample data for every file found, then exit. "
             "No Google Calendar access needed.",
    )
    parser.add_argument(
        "--timezone", "-tz",
        default=None,
        metavar="TZ",
        help="Timezone to apply to timestamps that have no timezone info "
             "(e.g. 'America/New_York', 'Europe/London'). Defaults to UTC.",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Parse data and show what would be created without touching Google Calendar.",
    )
    parser.add_argument(
        "--no-exercise",
        action="store_true",
        help="Skip exercise/workout events.",
    )
    parser.add_argument(
        "--no-sleep",
        action="store_true",
        help="Skip sleep events.",
    )

    args = parser.parse_args()

    if not args.export_folder:
        parser.print_help()
        sys.exit(1)

    # Resolve timezone
    tz = None
    if args.timezone:
        try:
            tz = ZoneInfo(args.timezone)
        except ZoneInfoNotFoundError:
            print(f"Error: Unknown timezone '{args.timezone}'.")
            print("Use a standard IANA name like 'America/New_York' or 'Europe/London'.")
            sys.exit(1)

    # ── Inspect mode ─────────────────────────────────────────────────────────
    if args.inspect:
        inspect_csvs(args.export_folder)
        return

    # ── Load data ─────────────────────────────────────────────────────────────
    all_events: list[dict] = []

    if not args.no_exercise:
        print("Loading exercise data…")
        exercises = load_exercises(args.export_folder, tz)
        print(f"  Found {len(exercises)} workout record(s)")
        all_events.extend(format_exercise_event(ex) for ex in exercises)

    if not args.no_sleep:
        print("Loading sleep data…")
        sleep_records = load_sleep(args.export_folder, tz)
        print(f"  Found {len(sleep_records)} sleep record(s)")
        all_events.extend(format_sleep_event(sl) for sl in sleep_records)

    if not all_events:
        print("No events to sync. Exiting.")
        return

    print(f"\nTotal events to process: {len(all_events)}")

    # ── Dry run ───────────────────────────────────────────────────────────────
    if args.dry_run:
        print("\n[DRY RUN — nothing will be written to Google Calendar]\n")
        for event in all_events:
            print(f"  {event['summary']}")
            print(f"    {event['start']['dateTime']}  →  {event['end']['dateTime']}")
            if event["description"]:
                for line in event["description"].splitlines():
                    print(f"    {line}")
            print()
        print(f"Would create up to {len(all_events)} event(s).")
        return

    # ── Sync to Google Calendar ───────────────────────────────────────────────
    print("\nConnecting to Google Calendar…")
    service = get_calendar_service()
    calendar_id = get_or_create_calendar(service, CALENDAR_NAME)
    print(f"Using calendar: '{CALENDAR_NAME}'")

    print("\nSyncing…")
    created, skipped = sync_events(service, calendar_id, all_events)

    print(f"\nDone!")
    print(f"  Created : {created}")
    print(f"  Skipped : {skipped}  (already existed)")


if __name__ == "__main__":
    main()
