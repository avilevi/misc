#!/usr/bin/env python3
"""
gcal_thruster.py - Google Calendar event manager for the Thruster program.

Reads a structured events text file, generates an ICS, and optionally:
  - Imports events to Google Calendar
  - Deletes existing events from Google Calendar (using an ICS as reference)
  - Emails a per-exercise progression table

Events file format (blocks separated by ---):
  date: 2026-03-29
  summary: Week 1 - Day A - Thruster
  description:
    Week 1
    Thruster: 5x3 @ 40kg
    Front Squat: 4x4 @ 67.5kg
    ...
  ---
  date: 2026-03-31
  ...

Usage examples:
  # Generate ICS only
  python3 gcal_thruster.py thruster_events.txt

  # Generate ICS + import to Google Calendar
  python3 gcal_thruster.py thruster_events.txt --import-events

  # Delete old events then import new ones
  python3 gcal_thruster.py thruster_events.txt --delete old.ics --import-events

  # Full workflow: delete old, import new, send email
  python3 gcal_thruster.py thruster_events.txt --delete old.ics --import-events --email

  # Delete only (no new import)
  python3 gcal_thruster.py --delete old.ics

  # Send email to custom address
  python3 gcal_thruster.py thruster_events.txt --email someone@example.com
"""

import argparse
import pickle
import re
import subprocess
import sys
import uuid
from datetime import date, timedelta
from pathlib import Path

from google.auth.transport.requests import Request
from google_auth_oauthlib.flow import InstalledAppFlow
from googleapiclient.discovery import build

SCOPES = [
    "https://www.googleapis.com/auth/calendar",
    "https://www.googleapis.com/auth/drive.file",
]
DEFAULT_EMAIL = "avi.levi99@gmail.com"
DEFAULT_CALENDAR = "Sports"
SCRIPT_DIR = Path(__file__).parent
DEFAULT_CREDENTIALS = SCRIPT_DIR / "client_secret.json"
DEFAULT_TOKEN = SCRIPT_DIR / "token.pickle"


# ── Auth ───────────────────────────────────────────────────────────────────

def check_credentials_file(credentials_path):
    """Validate that client_secret.json exists and looks correct. Exit with instructions if not."""
    p = Path(credentials_path)
    if not p.exists():
        print(
            f"\nERROR: Google OAuth credentials file not found: {credentials_path}\n"
            "\nTo set it up:\n"
            "  1. Go to https://console.cloud.google.com/\n"
            "  2. Create a project (or select an existing one).\n"
            "  3. Enable the Google Calendar API:\n"
            "       APIs & Services → Library → search 'Google Calendar API' → Enable\n"
            "  4. Create OAuth 2.0 credentials:\n"
            "       APIs & Services → Credentials → Create Credentials → OAuth client ID\n"
            "       Application type: Desktop app\n"
            "  5. Download the JSON file and save it as:\n"
            f"       {credentials_path}\n"
            "  6. See client_secret.example.json in this repo for the expected format.\n",
            file=sys.stderr,
        )
        sys.exit(1)

    import json
    try:
        data = json.loads(p.read_text())
        if "installed" not in data:
            raise ValueError("missing 'installed' key")
        for field in ("client_id", "client_secret", "auth_uri", "token_uri"):
            if field not in data["installed"]:
                raise ValueError(f"missing field: installed.{field}")
            val = data["installed"][field]
            if "YOUR_" in str(val) or not val:
                raise ValueError(f"placeholder value in field: installed.{field}")
    except (json.JSONDecodeError, ValueError) as e:
        print(
            f"\nERROR: {credentials_path} is invalid: {e}\n"
            "  Download a fresh copy from Google Cloud Console:\n"
            "    APIs & Services → Credentials → your OAuth 2.0 Client ID → Download JSON\n"
            "  See client_secret.example.json for the expected structure.\n",
            file=sys.stderr,
        )
        sys.exit(1)


def get_credentials(credentials_path, token_path):
    check_credentials_file(credentials_path)
    creds = None
    token_path = Path(token_path)
    if token_path.exists():
        with open(token_path, "rb") as f:
            creds = pickle.load(f)
    if not creds or not creds.valid:
        if creds and creds.expired and creds.refresh_token:
            try:
                creds.refresh(Request())
            except Exception:
                print("Token expired and could not be refreshed — deleting token.pickle and re-authenticating.")
                token_path.unlink(missing_ok=True)
                creds = None
        if not creds or not creds.valid:
            flow = InstalledAppFlow.from_client_secrets_file(
                str(credentials_path), SCOPES,
                redirect_uri="urn:ietf:wg:oauth:2.0:oob",
            )
            auth_url, _ = flow.authorization_url(prompt="consent")
            print("\nOpen this URL in a browser to authorize:\n")
            print(auth_url)
            print()
            code = input("Paste the authorization code: ").strip()
            flow.fetch_token(code=code)
            creds = flow.credentials
        with open(token_path, "wb") as f:
            pickle.dump(creds, f)
    return creds


def find_calendar_id(service, name):
    for cal in service.calendarList().list().execute().get("items", []):
        if cal["summary"].lower() == name.lower():
            return cal["id"]
    return None


# ── Events file parsing ────────────────────────────────────────────────────

def parse_events_file(path):
    """Parse structured events text file. Returns list of event dicts."""
    text = Path(path).read_text()
    blocks = re.split(r'\n---\n', text.strip())
    events = []
    for block in blocks:
        block = block.strip()
        if not block:
            continue
        ev = {}
        lines = block.split("\n")
        i = 0
        while i < len(lines):
            line = lines[i]
            if line.startswith("date:"):
                ev["date"] = line.split(":", 1)[1].strip()
            elif line.startswith("summary:"):
                ev["summary"] = line.split(":", 1)[1].strip()
            elif line.startswith("description:"):
                desc_lines = []
                i += 1
                while i < len(lines):
                    desc_lines.append(lines[i].strip())
                    i += 1
                ev["description"] = "\n".join(desc_lines).strip()
                continue
            i += 1
        if "date" in ev and "summary" in ev:
            events.append(ev)
    return events


# ── ICS generation ─────────────────────────────────────────────────────────

def _fold(line):
    """Fold ICS lines longer than 75 bytes per RFC 5545."""
    if len(line.encode("utf-8")) <= 75:
        return line
    parts = []
    while len(line.encode("utf-8")) > 75:
        parts.append(line[:75])
        line = " " + line[75:]
    parts.append(line)
    return "\r\n".join(parts)


def generate_ics(events, output_path):
    """Generate an ICS file from parsed events. Returns path to written file."""
    from datetime import datetime
    stamp = datetime.utcnow().strftime("%Y%m%dT%H%M%SZ")

    lines = [
        "BEGIN:VCALENDAR",
        "VERSION:2.0",
        "PRODID:-//Thruster Program//EN",
        "CALSCALE:GREGORIAN",
    ]

    for ev in events:
        d = ev["date"].replace("-", "")
        year, month, day = int(d[:4]), int(d[4:6]), int(d[6:])
        end = (date(year, month, day) + timedelta(days=1)).strftime("%Y%m%d")
        uid = f"{uuid.uuid4()}@thruster-program"
        ev["_uid"] = uid  # store for later import

        desc = ev.get("description", "").replace("\n", "\\n")
        ev_lines = [
            "BEGIN:VEVENT",
            f"UID:{uid}",
            f"DTSTAMP:{stamp}",
            f"DTSTART;VALUE=DATE:{d}",
            f"DTEND;VALUE=DATE:{end}",
            f"SUMMARY:{ev['summary']}",
        ]
        if desc:
            ev_lines.append(f"DESCRIPTION:{desc}")
        ev_lines.append("END:VEVENT")
        lines.extend(ev_lines)

    lines.append("END:VCALENDAR")
    content = "\r\n".join(_fold(l) for l in lines)
    Path(output_path).write_text(content)
    print(f"ICS written → {output_path}  ({len(events)} events)")
    return output_path


# ── Google Calendar operations ─────────────────────────────────────────────

def delete_events_by_ics(service, cal_id, ics_path, from_date=None):
    """Delete calendar events whose summaries match those in the given ICS."""
    with open(ics_path) as f:
        raw = f.read()
    unfolded = re.sub(r"\r?\n[ \t]", "", raw)
    summaries = {s.strip() for s in re.findall(r"SUMMARY:(.+)", unfolded)}
    label = f" on or after {from_date}" if from_date else ""
    print(f"Deleting events matching {len(summaries)} summary type(s){label}...")

    # Build search terms from the last word of each unique summary
    search_terms = {s.split()[-1] for s in summaries if s.split()}
    deleted, seen = 0, set()

    for term in search_terms:
        page_token = None
        while True:
            result = service.events().list(
                calendarId=cal_id, q=term, singleEvents=True,
                maxResults=250, pageToken=page_token,
            ).execute()
            for e in result.get("items", []):
                if from_date:
                    start = e.get("start", {}).get("date", "")
                    if start and start < from_date:
                        continue
                if e.get("summary") in summaries and e["id"] not in seen:
                    service.events().delete(calendarId=cal_id, eventId=e["id"]).execute()
                    seen.add(e["id"])
                    deleted += 1
            page_token = result.get("nextPageToken")
            if not page_token:
                break

    print(f"Deleted {deleted} events.")


def _augment_description_plates(description):
    """
    Add plate breakdowns to a clean event description:
      - plate summary before the warm-up section
      - per-exercise plate line after each exercise line
      - '· · ·' separator between exercises
    """
    _, exercises = _parse_description(description)
    # Build plate summary header
    summary = workout_plates_summary(exercises)
    if summary:
        plate_parts = " + ".join(f"{cnt*2}×{p:g}kg" for p, cnt in summary)
        plates_header = f"Plates needed: {plate_parts}\n\n"
    else:
        plates_header = ""

    lines = description.split('\n')
    out = []
    in_exercises = False
    prev_was_exercise = False
    for line in lines:
        stripped = line.strip()
        # Detect the separator after warm-up (second '----')
        if stripped.startswith('---') and not in_exercises:
            # If we've already seen the first dashes, next dashes end warm-up
            if any(l.strip().startswith('---') for l in out):
                in_exercises = True
                out.append(line)
                # Insert plates header right after the warm-up separator
                if plates_header:
                    out.append("")
                    out.append(plates_header.rstrip('\n'))
                continue
        if in_exercises and stripped:
            # Check if this is an exercise line (contains '@' or 'sets to failure')
            is_ex = ('@' in stripped or 'sets to failure' in stripped.lower()) and ':' in stripped
            if is_ex:
                if prev_was_exercise:
                    out.append("  · · ·")
                out.append(line)
                # Extract weight and add plates
                m = re.match(r'.*@\s*([\d.]+)kg', stripped)
                if m:
                    plate_label = format_plates_label(float(m.group(1)))
                    out.append(f"    [{plate_label}]")
                prev_was_exercise = True
                continue
            else:
                prev_was_exercise = False
        out.append(line)
    return '\n'.join(out)


def import_to_calendar(service, cal_id, events, progression_link=None):
    """Import parsed events into Google Calendar."""
    print(f"Importing {len(events)} events...")
    for ev in events:
        d = ev["date"].replace("-", "")
        year, month, day = int(d[:4]), int(d[4:6]), int(d[6:])
        end = (date(year, month, day) + timedelta(days=1)).strftime("%Y-%m-%d")
        body = {
            "summary": ev["summary"],
            "start": {"date": ev["date"]},
            "end": {"date": end},
            "reminders": {"useDefault": False, "overrides": [{"method": "popup", "minutes": 10}]},
        }
        if "description" in ev:
            body["description"] = _augment_description_plates(ev["description"])
        attachments = []
        if "_drive_web_link" in ev:
            attachments.append({
                "fileUrl": ev["_drive_web_link"],
                "mimeType": "text/html",
                "title": f"{ev['summary']}.html",
            })
        if progression_link:
            attachments.append({
                "fileUrl": progression_link,
                "mimeType": "text/html",
                "title": "Thruster Program - Complete Progression.html",
            })
        if attachments:
            body["attachments"] = attachments
        if "_uid" in ev:
            body["iCalUID"] = ev["_uid"]
            service.events().import_(
                calendarId=cal_id, body=body, supportsAttachments=True
            ).execute()
        else:
            service.events().insert(
                calendarId=cal_id, body=body, supportsAttachments=True
            ).execute()
    print("Import complete.")


# ── Email ──────────────────────────────────────────────────────────────────

def _build_progression_html(events):
    """Build per-exercise progression HTML from parsed events."""
    data = {}
    for ev in events:
        week_m = re.search(r"Week (\d+)", ev.get("summary", ""))
        if not week_m:
            continue
        week = int(week_m.group(1))
        if week not in data:
            data[week] = {}
        for line in ev.get("description", "").split("\n")[1:]:
            m = re.match(r"(.+?):\s+[\dx]+\s+@\s+([\d.]+)kg", line.strip())
            if m:
                data[week][m.group(1).strip()] = float(m.group(2))

    if not data:
        return "<p>No exercise data found.</p>"

    weeks = sorted(data.keys())

    # Detect deload weeks: thruster weight drops vs previous non-deload week
    deload_weeks = set()
    prev = None
    for w in weeks:
        t = data[w].get("Thruster")
        if t is not None:
            if prev is not None and t < prev:
                deload_weeks.add(w)
            else:
                prev = t

    all_exercises = ["Thruster", "Overhead Squat", "Front Squat", "Romanian Deadlift",
                     "Push Press", "Overhead Press", "Bench Press", "Barbell Row",
                     "Hang Power Clean"]
    present = {ex for d in data.values() for ex in d}
    exercises = [ex for ex in all_exercises if ex in present]

    colors = {
        "Thruster":           ("#1a56db", "#e8f0fe"),
        "Overhead Squat":     ("#6366f1", "#eef2ff"),
        "Front Squat":        ("#0e9f6e", "#e6f9f4"),
        "Romanian Deadlift":  ("#7e3af2", "#f3eeff"),
        "Push Press":         ("#e3a008", "#fef9e7"),
        "Overhead Press":     ("#e02424", "#fde8e8"),
        "Bench Press":        ("#ff5a1f", "#fff3ee"),
        "Barbell Row":        ("#057a55", "#ecfdf5"),
        "Hang Power Clean":   ("#0369a1", "#e0f2fe"),
    }

    def make_table(ex):
        accent, bg = colors.get(ex, ("#333333", "#f9f9f9"))
        weights = [data[w].get(ex) for w in weeks]
        valid = [v for v in weights if v is not None]
        if not valid:
            return ""
        max_w = max(valid)
        rows = ""
        for i, (w, weight) in enumerate(zip(weeks, weights)):
            is_deload = w in deload_weeks
            row_bg = "#fff8e1" if is_deload else ("#f9f9f9" if i % 2 else "#ffffff")
            bar_w = int((weight / max_w) * 100) if weight else 0
            badge = (' <span style="font-size:10px;color:#888;margin-left:4px;">deload</span>'
                     if is_deload else "")
            rows += (
                f'<tr style="background:{row_bg}">'
                f'<td style="padding:6px 10px;font-weight:600;color:#444;width:60px;text-align:center;">'
                f'{w}{badge}</td>'
                f'<td style="padding:6px 10px;width:60px;text-align:right;font-weight:700;color:{accent};">'
                f'{weight}kg</td>'
                f'<td style="padding:6px 12px;">'
                f'<div style="background:#e8e8e8;border-radius:4px;height:10px;width:100%;">'
                f'<div style="background:{accent};width:{bar_w}%;height:10px;border-radius:4px;"></div>'
                f'</div></td></tr>'
            )
        start, peak = weights[0], max_w
        gain = round(peak - (start or 0), 1)
        return (
            f'<div style="background:{bg};border-radius:12px;padding:20px 24px;'
            f'margin-bottom:28px;border-left:5px solid {accent};">'
            f'<h2 style="margin:0 0 4px;color:{accent};font-size:18px;">{ex}</h2>'
            f'<div style="color:#666;font-size:13px;margin-bottom:14px;">'
            f'Start: <strong>{start}kg</strong> &nbsp;→&nbsp; Peak: <strong>{peak}kg</strong>'
            f' &nbsp;|&nbsp; Total gain: <strong>+{gain}kg</strong></div>'
            f'<table style="width:100%;border-collapse:collapse;font-size:13px;">'
            f'<thead><tr style="background:{accent};color:#fff;">'
            f'<th style="padding:7px 10px;text-align:center;">Week</th>'
            f'<th style="padding:7px 10px;text-align:right;">Weight</th>'
            f'<th style="padding:7px 12px;text-align:left;">Progression</th>'
            f'</tr></thead><tbody>{rows}</tbody></table></div>'
        )

    tables = "".join(make_table(ex) for ex in exercises)
    n = len(weeks)
    return f"""<!DOCTYPE html>
<html><head><meta charset="utf-8"></head>
<body style="margin:0;padding:0;background:#f0f2f5;font-family:'Segoe UI',Arial,sans-serif;">
<div style="max-width:620px;margin:30px auto;background:#fff;border-radius:16px;
     overflow:hidden;box-shadow:0 4px 24px rgba(0,0,0,0.10);">
  <div style="background:linear-gradient(135deg,#1a56db,#7e3af2);padding:32px 32px 24px;color:#fff;">
    <h1 style="margin:0 0 6px;font-size:26px;font-weight:800;">Thruster Program</h1>
    <p style="margin:0;opacity:0.85;font-size:15px;">{n}-Week Exercise Progression</p>
  </div>
  <div style="padding:28px 24px;">
    <p style="color:#555;margin:0 0 24px;font-size:14px;">
      Each section shows how a single exercise progresses across all {n} weeks.
      <span style="background:#fff8e1;padding:2px 6px;border-radius:4px;
            font-size:12px;color:#b45309;">Deload weeks</span> are highlighted in yellow.
    </p>
    {tables}
  </div>
  <div style="background:#f8f8f8;padding:16px 24px;text-align:center;
       color:#aaa;font-size:12px;border-top:1px solid #eee;">
    Generated by gcal_thruster.py
  </div>
</div>
</body></html>"""


def _find_next_event(events):
    """Return the event for today or the nearest upcoming date."""
    from datetime import date
    today = date.today().isoformat()
    upcoming = [ev for ev in events if ev["date"] >= today]
    return min(upcoming, key=lambda e: e["date"]) if upcoming else (events[-1] if events else None)


def send_email(events, to_email):
    from email.mime.multipart import MIMEMultipart
    from email.mime.text import MIMEText

    exercise_ranges = _compute_exercise_ranges(events)
    next_ev = _find_next_event(events)
    workout_html = generate_event_html(next_ev, exercise_ranges) if next_ev else None
    progression_html = _build_progression_html(events)

    msg = MIMEMultipart()
    msg["From"] = DEFAULT_EMAIL
    msg["To"] = to_email
    msg["Subject"] = "Thruster Program"

    body = "<p>See attachments: today's workout and the full progression plan.</p>"
    if next_ev:
        body = (f"<p><strong>Next workout:</strong> {next_ev['summary']}"
                f" &mdash; {next_ev['date']}</p>")
    msg.attach(MIMEText(body, "html", "utf-8"))

    if workout_html and next_ev:
        part = MIMEText(workout_html, "html", "utf-8")
        safe_name = re.sub(r"[^\w\s\-.]", "", f"{next_ev['date']} - {next_ev['summary']}.html")
        part.add_header("Content-Disposition", "attachment", filename=safe_name)
        msg.attach(part)

    part2 = MIMEText(progression_html, "html", "utf-8")
    part2.add_header("Content-Disposition", "attachment",
                     filename="Thruster Program - Complete Progression.html")
    msg.attach(part2)

    result = subprocess.run(["msmtp", to_email], input=msg.as_string(), text=True, capture_output=True)
    if result.returncode == 0:
        print(f"Email sent → {to_email}")
    else:
        print(f"Email error: {result.stderr}", file=sys.stderr)
        sys.exit(1)


# ── HTML event pages ───────────────────────────────────────────────────────

DRIVE_FOLDER_NAME = "Thruster Program"
BAR_KG = 20  # standard Olympic barbell

# Available plates per side (largest first). No 25/20/15kg in this gym.
_PLATE_SIZES = [10, 5, 2.5, 1.25, 1, 0.75, 0.5]


def calc_plates(weight_kg, bar_kg=BAR_KG):
    """
    Return list of (plate_kg, count_per_side) for a given total weight.
    Uses DP (minimum-plate coin-change) so it works correctly even when
    the greedy approach would leave an unachievable remainder.
    """
    per_side = round((weight_kg - bar_kg) / 2, 4)
    if per_side <= 0:
        return []

    # Work in units of 0.25 kg to use integer DP
    UNIT = 0.25
    target = round(per_side / UNIT)
    coins = [round(p / UNIT) for p in _PLATE_SIZES]   # [40,20,10,5,4,3,2]

    INF = float('inf')
    dp = [INF] * (target + 1)
    dp[0] = 0
    choice = [-1] * (target + 1)

    for t in range(1, target + 1):
        for ci, c in enumerate(coins):
            if c <= t and dp[t - c] + 1 < dp[t]:
                dp[t] = dp[t - c] + 1
                choice[t] = ci

    if dp[target] == INF:
        return []   # weight not achievable with these plates

    counts = {}
    t = target
    while t > 0:
        ci = choice[t]
        counts[_PLATE_SIZES[ci]] = counts.get(_PLATE_SIZES[ci], 0) + 1
        t -= coins[ci]

    return [(p, counts[p]) for p in _PLATE_SIZES if p in counts]


def format_plates_label(weight_kg, bar_kg=BAR_KG):
    """Return a compact label, e.g. '2×15 + 2×2.5 + 2×0.5' (total plates, both sides)."""
    plates = calc_plates(weight_kg, bar_kg)
    if not plates:
        return "bar only"
    parts = []
    for p, cnt in plates:
        total = cnt * 2
        p_str = f"{p:g}"
        parts.append(f"{total}×{p_str}")
    return " + ".join(parts)


def workout_plates_summary(exercises, bar_kg=BAR_KG):
    """
    Return ordered list of (plate_kg, max_count_per_side) representing
    the maximum of each plate denomination needed across all exercises.
    """
    from collections import defaultdict
    maxc = defaultdict(int)
    for ex in exercises:
        if ex.get('weight') in ('BW', None):
            continue
        try:
            w = float(ex['weight'].rstrip('kg'))
        except (ValueError, AttributeError):
            continue
        for p, cnt in calc_plates(w, bar_kg):
            maxc[p] = max(maxc[p], cnt)
    return [(p, maxc[p]) for p in _PLATE_SIZES if maxc[p] > 0]


_EXERCISE_COLORS = {
    "Thruster":           "#1a56db",
    "Overhead Squat":     "#6366f1",
    "Front Squat":        "#0e9f6e",
    "Romanian Deadlift":  "#7e3af2",
    "Push Press":         "#e3a008",
    "Overhead Press":     "#e02424",
    "Bench Press":        "#ff5a1f",
    "Barbell Row":        "#057a55",
    "Hang Power Clean":   "#0369a1",
    "Pull-ups":           "#7e3af2",
}


def _parse_description(description):
    """Return (warmup_items, exercise_dicts) from a stripped event description."""
    lines = [l.strip() for l in description.split('\n')]
    warmup, exercises = [], []
    in_warmup = False
    dash_count = 0
    for line in lines:
        if not line:
            continue
        if line.startswith('---'):
            dash_count += 1
            in_warmup = (dash_count == 1)
            continue
        if line.startswith('Warm-up') or line.startswith('Week '):
            continue
        if in_warmup:
            warmup.append(line)
        elif ':' in line:
            name, rest = line.split(':', 1)
            rest = rest.strip()
            is_deload = '[DELOAD' in rest
            is_test   = '1RM TEST' in rest
            m = re.match(r'(\d+)x(\d+)\s+@\s+([\d.]+)kg', rest)
            if m:
                exercises.append({
                    'name': name.strip(), 'sets': m.group(1), 'reps': m.group(2),
                    'weight': f"{float(m.group(3)):g}kg",
                    'deload': is_deload, 'test': is_test,
                })
            else:
                m2 = re.match(r'(\d+)\s+sets?\s+to\s+failure', rest)
                exercises.append({
                    'name': name.strip(),
                    'sets': m2.group(1) if m2 else '',
                    'reps': 'failure', 'weight': 'BW',
                    'deload': False, 'test': False,
                })
    return warmup, exercises


def _compute_exercise_ranges(events):
    """Return {exercise_name: {'start': float, 'target': float}} across all events."""
    seen, target = {}, {}
    for ev in events:
        _, exercises = _parse_description(ev.get('description', ''))
        for ex in exercises:
            if ex['reps'] == 'failure' or ex['weight'] == 'BW' or ex['deload']:
                continue
            try:
                w = float(ex['weight'].rstrip('kg'))
            except ValueError:
                continue
            name = ex['name']
            if name not in seen:
                seen[name] = w
            target[name] = max(target.get(name, 0), w)
    return {name: {'start': seen[name], 'target': target[name]} for name in seen}


def generate_event_html(ev, exercise_ranges=None):
    """Generate a styled HTML page for a single event."""
    from datetime import datetime as dt
    date_obj = dt.strptime(ev['date'], '%Y-%m-%d')
    date_str = date_obj.strftime('%A, %-d %B %Y')
    summary  = ev.get('summary', '')
    week_m   = re.search(r'Week (\d+)', summary)
    week_num = week_m.group(1) if week_m else '?'
    day_type = 'Day A — Thruster' if 'Day A' in summary else 'Day B — Press & Pull'
    is_deload = '[DELOAD' in ev.get('description', '')

    warmup, exercises = _parse_description(ev.get('description', ''))

    deload_banner = ''
    if is_deload:
        deload_banner = (
            '<div style="background:#fff8e1;border-left:4px solid #e3a008;'
            'padding:10px 20px;font-size:13px;color:#92400e;font-weight:600;">'
            'Deload Week — reduced volume &amp; intensity</div>'
        )

    warmup_html = ''.join(
        f'<div style="display:flex;align-items:center;gap:10px;padding:8px 0;'
        f'border-bottom:1px solid #f5f5f5;font-size:14px;color:#444;">'
        f'<span style="width:7px;height:7px;border-radius:50%;background:#0e9f6e;'
        f'flex-shrink:0;display:inline-block;"></span>{item}</div>'
        for item in warmup
    )

    # Plates summary at top
    summary = workout_plates_summary(exercises)
    if summary:
        summary_chips = ''.join(
            f'<span style="display:inline-block;font-size:14px;background:#f3f4f6;'
            f'border-radius:6px;padding:4px 10px;margin:3px 3px;color:#374151;'
            f'font-weight:600;">{cnt*2}×{p:g}kg</span>'
            for p, cnt in summary
        )
        plates_section = (
            f'<div class="section">'
            f'<div class="section-title" style="color:#6b7280;">Plates needed (bar = {BAR_KG}kg)</div>'
            f'<div style="margin-top:6px;line-height:2.2;">{summary_chips}</div>'
            f'</div>'
        )
    else:
        plates_section = ''

    def ex_row(ex):
        color = _EXERCISE_COLORS.get(ex['name'], '#555')
        meta  = (f"{ex['sets']} sets × {ex['reps']} reps"
                 if ex['reps'] != 'failure'
                 else f"{ex['sets']} sets to failure")
        weight_str = ex['weight']
        badge = (' <span style="font-size:13px;background:#fff8e1;color:#92400e;'
                 'padding:3px 7px;border-radius:4px;margin-left:6px;">deload</span>'
                 if ex['deload'] else '')
        test_badge = (' <span style="font-size:13px;background:#fde8e8;color:#9b1c1c;'
                      'padding:3px 7px;border-radius:4px;margin-left:6px;">1RM test</span>'
                      if ex['test'] else '')

        bar_html = ''
        if (not ex['deload'] and ex['reps'] != 'failure'
                and exercise_ranges and ex['name'] in exercise_ranges):
            try:
                cur = float(ex['weight'].rstrip('kg'))
                start  = exercise_ranges[ex['name']]['start']
                tgt    = exercise_ranges[ex['name']]['target']
                if tgt > start:
                    pct = max(0, min(100, (cur - start) / (tgt - start) * 100))
                    bar_html = (
                        f'<div style="display:flex;align-items:center;gap:8px;margin-top:8px;">'
                        f'<div style="background:#e8e8e8;border-radius:4px;height:7px;flex:1;">'
                        f'<div style="background:{color};width:{pct:.0f}%;height:7px;border-radius:4px;"></div>'
                        f'</div>'
                        f'<span style="font-size:14px;color:#aaa;white-space:nowrap;">{tgt:g}kg</span>'
                        f'</div>'
                    )
            except (ValueError, AttributeError):
                pass

        # Plate chips for this exercise
        plate_html = ''
        if ex['weight'] != 'BW' and ex['reps'] != 'failure':
            try:
                w = float(ex['weight'].rstrip('kg'))
                plates = calc_plates(w)
                if plates:
                    chips = ''.join(
                        f'<span style="display:inline-block;font-size:14px;'
                        f'background:#f3f4f6;border-radius:5px;padding:4px 9px;'
                        f'margin:3px 3px;color:#374151;font-weight:500;">{cnt*2}×{p:g}kg</span>'
                        for p, cnt in plates
                    )
                    plate_html = f'<div style="margin-top:8px;line-height:2.2;">{chips}</div>'
            except (ValueError, AttributeError):
                pass

        return (
            f'<div style="padding:14px 0;border-bottom:1px solid #f0f0f0;">'
            f'<div style="display:flex;align-items:flex-start;justify-content:space-between;">'
            f'<div><div style="font-size:19px;font-weight:700;color:#222;">'
            f'{ex["name"]}{badge}{test_badge}</div>'
            f'<div style="font-size:15px;color:#888;margin-top:3px;">{meta}</div></div>'
            f'<div style="font-size:28px;font-weight:800;color:{color};white-space:nowrap;padding-left:14px;">'
            f'{weight_str}</div></div>'
            f'{bar_html}'
            f'{plate_html}'
            f'</div>'
        )

    SEPARATOR = (
        '<div style="text-align:center;color:#d1d5db;font-size:16px;'
        'letter-spacing:6px;padding:2px 0;">· · ·</div>'
    )
    exercises_html = SEPARATOR.join(ex_row(ex) for ex in exercises)

    return f"""<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>{summary}</title>
  <style>
    * {{ box-sizing: border-box; margin: 0; padding: 0; }}
    body {{ font-family: 'Segoe UI', Arial, sans-serif; background: #f0f2f5; padding: 16px; }}
    .card {{ max-width: 540px; margin: 0 auto; background: #fff; border-radius: 16px;
             overflow: hidden; box-shadow: 0 4px 24px rgba(0,0,0,0.10); }}
    .section {{ padding: 18px 20px; }}
    .section + .section {{ border-top: 1px solid #f0f0f0; }}
    .section-title {{ font-size: 10px; font-weight: 700; letter-spacing: 0.08em;
                      text-transform: uppercase; margin-bottom: 10px; }}
    .exercise-row:last-child div {{ border-bottom: none !important; }}
  </style>
</head>
<body>
  <div class="card">
    <div style="background:linear-gradient(135deg,#1a56db,#7e3af2);padding:22px 20px 18px;color:#fff;">
      <div style="font-size:12px;opacity:0.75;margin-bottom:4px;">{date_str}</div>
      <h1 style="font-size:20px;font-weight:800;margin-bottom:2px;">Week {week_num}</h1>
      <p style="font-size:13px;opacity:0.85;">{day_type}</p>
    </div>
    {deload_banner}
    {plates_section}
    <div class="section">
      <div class="section-title" style="color:#0e9f6e;">Warm-up — 5 min</div>
      {warmup_html}
    </div>
    <div class="section">
      <div class="section-title" style="color:#1a56db;">Workout</div>
      {exercises_html}
    </div>
    <div style="background:#f8f8f8;padding:10px 20px;text-align:center;
         color:#aaa;font-size:11px;border-top:1px solid #eee;">
      Thruster Program &mdash; Week {week_num} of 39
    </div>
  </div>
</body>
</html>"""


def _get_or_create_drive_folder(drive_service, name):
    results = drive_service.files().list(
        q=f"name='{name}' and mimeType='application/vnd.google-apps.folder' and trashed=false",
        fields="files(id)",
    ).execute()
    files = results.get("files", [])
    if files:
        return files[0]["id"]
    folder = drive_service.files().create(
        body={"name": name, "mimeType": "application/vnd.google-apps.folder"},
        fields="id",
    ).execute()
    return folder["id"]


def _clear_drive_folder(drive_service, folder_id):
    page_token = None
    deleted = 0
    while True:
        result = drive_service.files().list(
            q=f"'{folder_id}' in parents and trashed=false",
            fields="files(id)", pageToken=page_token,
        ).execute()
        for f in result.get("files", []):
            drive_service.files().delete(fileId=f["id"]).execute()
            deleted += 1
        page_token = result.get("nextPageToken")
        if not page_token:
            break
    if deleted:
        print(f"Removed {deleted} old Drive files.")


def _list_drive_folder_files(drive_service, folder_id):
    """Return {filename: file_id} for all files in a Drive folder."""
    existing = {}
    page_token = None
    while True:
        result = drive_service.files().list(
            q=f"'{folder_id}' in parents and trashed=false",
            fields="files(id,name)", pageToken=page_token,
        ).execute()
        for f in result.get("files", []):
            existing[f["name"]] = f["id"]
        page_token = result.get("nextPageToken")
        if not page_token:
            break
    return existing


def _drive_upsert(drive_service, folder_id, filename, html, existing):
    """Upload or update a single HTML file in Drive; return the file resource."""
    from googleapiclient.http import MediaInMemoryUpload
    media = MediaInMemoryUpload(html.encode("utf-8"), mimetype="text/html", resumable=False)
    if filename in existing:
        return drive_service.files().update(
            fileId=existing[filename],
            media_body=media,
            fields="id,webViewLink",
        ).execute()
    return drive_service.files().create(
        body={"name": filename, "parents": [folder_id], "mimeType": "text/html"},
        media_body=media,
        fields="id,webViewLink",
    ).execute()


def upload_html_to_drive(drive_service, events, from_date=None):
    """Upload per-event HTML to Drive; store fileId and webViewLink on each event dict.

    When from_date is set, only events on or after that date are uploaded/updated;
    past files are left untouched.  Without from_date the folder is cleared first
    (full regeneration).
    """
    exercise_ranges = _compute_exercise_ranges(events)
    folder_id = _get_or_create_drive_folder(drive_service, DRIVE_FOLDER_NAME)

    if from_date:
        upload_events = [ev for ev in events if ev["date"] >= from_date]
        existing = _list_drive_folder_files(drive_service, folder_id)
    else:
        _clear_drive_folder(drive_service, folder_id)
        upload_events = events
        existing = {}

    total = len(upload_events)
    print(f"Generating and uploading {total} HTML files to Drive...")
    for i, ev in enumerate(upload_events, 1):
        html = generate_event_html(ev, exercise_ranges)
        filename = f"{ev['date']} - {ev['summary']}.html"
        file = _drive_upsert(drive_service, folder_id, filename, html, existing)
        ev["_drive_file_id"] = file["id"]
        ev["_drive_web_link"] = file["webViewLink"]
        print(f"  [{i}/{total}] {ev['date']} — {ev['summary']}")
    print("Drive upload complete.")
    return folder_id


def upload_progression_plan_to_drive(drive_service, events, folder_id=None):
    """Generate and upload the complete progression plan HTML to Drive."""
    if folder_id is None:
        folder_id = _get_or_create_drive_folder(drive_service, DRIVE_FOLDER_NAME)
    existing = _list_drive_folder_files(drive_service, folder_id)
    html = _build_progression_html(events)
    filename = "Thruster Program - Complete Progression.html"
    file = _drive_upsert(drive_service, folder_id, filename, html, existing)
    print(f"Progression plan uploaded → {file.get('webViewLink', '')}")
    return html, file


# ── Main ───────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Google Calendar Thruster Program Manager",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument("events_file", nargs="?",
                        help="Structured events text file (required for ICS generation, import, email)")
    parser.add_argument("--output", default="thruster_program.ics",
                        help="Output ICS file path (default: thruster_program.ics)")
    parser.add_argument("--import-events", action="store_true",
                        help="Import generated events into Google Calendar")
    parser.add_argument("--delete", metavar="ICS_FILE",
                        help="Delete calendar events matching summaries in this ICS file")
    parser.add_argument("--email", nargs="?", const=DEFAULT_EMAIL, metavar="ADDRESS",
                        help=f"Send progression email (default: {DEFAULT_EMAIL})")
    parser.add_argument("--calendar", default=DEFAULT_CALENDAR,
                        help=f"Google Calendar name (default: {DEFAULT_CALENDAR})")
    parser.add_argument("--credentials", default=str(DEFAULT_CREDENTIALS),
                        help="Path to Google OAuth client_secret.json")
    parser.add_argument("--token", default=str(DEFAULT_TOKEN),
                        help="Path to OAuth token pickle file")
    parser.add_argument("--no-drive", action="store_true",
                        help="Skip Google Drive HTML upload and attachments")
    parser.add_argument("--from-date", metavar="DATE",
                        help="Limit delete/import/Drive-upload to events on or after this date (YYYY-MM-DD). "
                             "Past Drive HTML files are left untouched.")
    args = parser.parse_args()

    if not args.events_file and not args.delete:
        parser.error("Provide an events_file and/or --delete ICS_FILE")

    # Parse events and generate ICS
    events = []
    if args.events_file:
        events = parse_events_file(args.events_file)
        print(f"Parsed {len(events)} events from {args.events_file}")
        generate_ics(events, args.output)

    # Apply --from-date filter for calendar operations (Drive gets all events)
    cal_events = events
    if args.from_date and events:
        cal_events = [ev for ev in events if ev["date"] >= args.from_date]
        print(f"Calendar operations limited to {len(cal_events)} events (from {args.from_date})")

    # Connect to Google APIs if needed
    service = cal_id = drive_service = None
    need_google = args.import_events or args.delete or (events and not args.no_drive)
    if need_google:
        print("Connecting to Google APIs...")
        creds = get_credentials(args.credentials, args.token)
        service = build("calendar", "v3", credentials=creds)
        drive_service = build("drive", "v3", credentials=creds)

    folder_id = None
    progression_link = None
    if drive_service and events and not args.no_drive:
        folder_id = upload_html_to_drive(drive_service, events, from_date=args.from_date)
        _, prog_file = upload_progression_plan_to_drive(drive_service, events, folder_id=folder_id)
        progression_link = prog_file.get("webViewLink")

    if service and (args.import_events or args.delete):
        cal_id = find_calendar_id(service, args.calendar)
        if not cal_id:
            print(f"ERROR: Calendar '{args.calendar}' not found.", file=sys.stderr)
            sys.exit(1)
        print(f"Calendar: {args.calendar}")

    if args.delete:
        delete_events_by_ics(service, cal_id, args.delete, from_date=args.from_date)

    if args.import_events:
        if not cal_events:
            parser.error("events_file is required for --import-events")
        import_to_calendar(service, cal_id, cal_events, progression_link=progression_link)

    if args.email:
        if not events:
            parser.error("events_file is required for --email")
        send_email(events, args.email)


if __name__ == "__main__":
    main()
