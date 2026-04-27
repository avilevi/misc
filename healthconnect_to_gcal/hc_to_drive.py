#!/usr/bin/env python3
"""
Health Connect → Google Drive  (Step 1 of 2)

Reads Health Connect JSON (written by Tasker), generates a styled HTML
report for each exercise / sleep session, and uploads them to a Google
Drive folder named "HealthConnect".

Saves /sdcard/health_sync/drive_manifest.json — Step 2 (hc_to_gcal.py)
reads this to attach Drive files to Google Calendar events.

Usage (Termux on Android):
    python ~/hc_to_drive.py
    python ~/hc_to_drive.py --data /sdcard/health_sync/data.json
    python ~/hc_to_drive.py --timezone Asia/Jerusalem
    python ~/hc_to_drive.py --dry-run
    python ~/hc_to_drive.py --auth-only
"""

import argparse
import json
import sys
from datetime import datetime, timezone
from pathlib import Path

try:
    from google.oauth2.credentials import Credentials
    from google_auth_oauthlib.flow import InstalledAppFlow
    from google.auth.transport.requests import Request
    from googleapiclient.discovery import build
    from googleapiclient.http import MediaInMemoryUpload
    from googleapiclient.errors import HttpError
except ImportError:
    msg = (
        "Error: Google API libraries not installed.\n"
        "Run in Termux:  pip install google-api-python-client google-auth-oauthlib"
    )
    Path("/sdcard/health_sync").mkdir(parents=True, exist_ok=True)
    Path("/sdcard/health_sync/last_sync.txt").write_text(msg + "\n")
    print(msg)
    sys.exit(1)

# ─── Paths ────────────────────────────────────────────────────────────────────

HOME   = Path.home()
SHARED = Path("/sdcard/health_sync")

DEFAULT_DATA_FILE = SHARED / "data.json"
MANIFEST_FILE     = SHARED / "drive_manifest.json"
LOG_FILE          = SHARED / "last_sync.txt"
TOKEN_FILE        = HOME   / "hc_drive_token.json"
CREDENTIALS_FILE  = HOME   / "credentials.json"

SCOPES            = ["https://www.googleapis.com/auth/drive.file"]
DRIVE_FOLDER_NAME = "HealthConnect"


# ─── Health Connect type mappings ─────────────────────────────────────────────

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

HC_SLEEP_STAGES: dict[int, str] = {
    0: "Unknown",
    1: "Awake",
    2: "Sleeping",
    3: "Out of Bed",
    4: "Light Sleep",
    5: "Deep Sleep",
    6: "REM",
}

ACTIVITY_COLORS: dict[str, str] = {
    "Running":             "#E64A19",
    "Treadmill Running":   "#E64A19",
    "Cycling":             "#1565C0",
    "Stationary Bike":     "#1565C0",
    "Mountain Biking":     "#1565C0",
    "Open Water Swimming": "#00838F",
    "Pool Swimming":       "#00838F",
    "Walking":             "#388E3C",
    "Hiking":              "#4E342E",
    "Strength Training":   "#6A1B9A",
    "Weightlifting":       "#6A1B9A",
    "Kettlebell Training": "#6A1B9A",
    "HIIT":                "#C62828",
    "Boot Camp":           "#C62828",
    "CrossFit":            "#D84315",
    "Yoga":                "#F57F17",
    "Pilates":             "#F57F17",
    "Stretching":          "#558B2F",
    "Elliptical":          "#455A64",
    "Stair Climbing":      "#455A64",
    "Stair Climbing Machine": "#455A64",
    "Dancing":             "#AD1457",
    "Rowing":              "#00695C",
    "Kayaking":            "#00695C",
    "Rowing Machine":      "#00695C",
    "Boxing":              "#B71C1C",
    "Kickboxing":          "#B71C1C",
    "Martial Arts":        "#B71C1C",
    "Soccer":              "#2E7D32",
    "Basketball":          "#E65100",
    "Tennis":              "#558B2F",
    "Badminton":           "#558B2F",
    "Calisthenics":        "#37474F",
    "_default":            "#37474F",
}


# ─── Logging ──────────────────────────────────────────────────────────────────

_log_lines: list[str] = []

def _log(msg: str = ""):
    print(msg)
    _log_lines.append(msg)

def _write_log():
    LOG_FILE.parent.mkdir(parents=True, exist_ok=True)
    LOG_FILE.write_text("\n".join(_log_lines) + "\n")


# ─── Helpers ──────────────────────────────────────────────────────────────────

def parse_dt(value) -> datetime | None:
    if not value and value != 0:
        return None
    if isinstance(value, (int, float)):
        ts = float(value)
        if ts > 1e11:
            ts /= 1000
        return datetime.fromtimestamp(ts, tz=timezone.utc)
    s = str(value).strip()
    if not s:
        return None
    if s.lstrip("-").isdigit():
        return parse_dt(float(s))
    normalised = s
    if len(s) > 19 and s[-6] in ("+", "-") and s[-3] == ":":
        normalised = s[:-3] + s[-2:]
    for fmt in [
        "%Y-%m-%dT%H:%M:%S.%f%z",
        "%Y-%m-%dT%H:%M:%S%z",
        "%Y-%m-%dT%H:%M:%SZ",
        "%Y-%m-%dT%H:%M:%S.%fZ",
        "%Y-%m-%d %H:%M:%S.%f",
        "%Y-%m-%d %H:%M:%S",
    ]:
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


def fmt_dur(total_minutes: int) -> str:
    if total_minutes >= 60:
        h, m = divmod(total_minutes, 60)
        return f"{h}h {m}m" if m else f"{h}h"
    return f"{total_minutes}m"


def fmt_time(dt: datetime) -> str:
    return dt.strftime("%-I:%M %p")


def fmt_date_short(dt: datetime) -> str:
    return dt.strftime("%b %-d, %Y")


def fmt_date_full(dt: datetime) -> str:
    return dt.strftime("%A, %B %-d, %Y")


# ─── HTML Generation ──────────────────────────────────────────────────────────

_EXERCISE_CSS = """\
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;
     background:#f0f2f5;color:#1a1a2e;padding:16px;min-height:100vh}
.card{background:#fff;border-radius:20px;overflow:hidden;
      box-shadow:0 4px 24px rgba(0,0,0,.10);max-width:480px;margin:0 auto}
.hdr{padding:28px 24px 22px;color:#fff}
.hdr-emoji{font-size:2.8rem;line-height:1;margin-bottom:10px}
.hdr-title{font-size:1.6rem;font-weight:800;letter-spacing:-.3px}
.hdr-date{font-size:.9rem;opacity:.88;margin-top:6px;font-weight:500}
.stats{display:grid;grid-template-columns:1fr 1fr}
.stat{padding:18px 22px;border-bottom:1px solid #f0f2f5;border-right:1px solid #f0f2f5}
.stat:nth-child(even){border-right:none}
.stat-lbl{font-size:.7rem;text-transform:uppercase;letter-spacing:.8px;
           color:#888;font-weight:600;margin-bottom:6px}
.stat-val{font-size:1.5rem;font-weight:800;color:#1a1a2e}
.stat-unit{font-size:.8rem;font-weight:500;color:#888;margin-left:3px}
.time-row{padding:16px 22px;display:flex;align-items:center;gap:10px;background:#fafafa;
           border-top:1px solid #f0f2f5}
.tbadge{background:#fff;border:1.5px solid #e8eaed;border-radius:10px;
         padding:7px 14px;font-size:.95rem;font-weight:700;
         box-shadow:0 1px 4px rgba(0,0,0,.06)}
.arrow{color:#bbb;font-size:1.1rem}
.notes{padding:16px 22px;border-top:1px solid #f0f2f5}
.notes-lbl{font-size:.7rem;text-transform:uppercase;letter-spacing:.8px;
            color:#888;font-weight:600;margin-bottom:6px}
.notes-txt{font-size:.95rem;color:#555;line-height:1.5}
.footer{padding:12px 22px;background:#fafafa;border-top:1px solid #f0f2f5;
         font-size:.75rem;color:#bbb;text-align:center}"""

_SLEEP_CSS = """\
*{box-sizing:border-box;margin:0;padding:0}
body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;
     background:#f0f2f5;color:#1a1a2e;padding:16px;min-height:100vh}
.card{background:#fff;border-radius:20px;overflow:hidden;
      box-shadow:0 4px 24px rgba(0,0,0,.10);max-width:480px;margin:0 auto}
.hdr{background:linear-gradient(135deg,#283593,#1a237e);
      padding:28px 24px 22px;color:#fff}
.hdr-emoji{font-size:2.8rem;line-height:1;margin-bottom:10px}
.hdr-title{font-size:1.6rem;font-weight:800}
.hdr-date{font-size:.9rem;opacity:.88;margin-top:6px;font-weight:500}
.main{padding:20px 24px;border-bottom:1px solid #f0f2f5}
.dur-lbl{font-size:.7rem;text-transform:uppercase;letter-spacing:.8px;
          color:#888;font-weight:600;margin-bottom:6px}
.dur{font-size:2.8rem;font-weight:900;color:#1a237e;letter-spacing:-1px}
.time-row{padding:16px 22px;display:flex;align-items:center;gap:10px;
           background:#fafafa;border-bottom:1px solid #f0f2f5}
.tbadge{background:#fff;border:1.5px solid #e8eaed;border-radius:10px;
         padding:7px 14px;font-size:.95rem;font-weight:700;
         box-shadow:0 1px 4px rgba(0,0,0,.06)}
.arrow{color:#bbb;font-size:1.1rem}
.stages{padding:20px 24px}
.stages-lbl{font-size:.7rem;text-transform:uppercase;letter-spacing:.8px;
             color:#888;font-weight:600;margin-bottom:14px}
.sbar{display:flex;height:14px;border-radius:8px;overflow:hidden;
       margin-bottom:18px;background:#f0f2f5}
.slist{display:flex;flex-direction:column;gap:10px}
.srow{display:flex;align-items:center;gap:10px}
.sdot{width:12px;height:12px;border-radius:50%;flex-shrink:0}
.sname{flex:1;font-size:.9rem;color:#444;font-weight:500}
.sdur{font-size:.9rem;font-weight:700;color:#1a1a2e}
.footer{padding:12px 22px;background:#fafafa;border-top:1px solid #f0f2f5;
         font-size:.75rem;color:#bbb;text-align:center}"""


def _stat_cell(label: str, value: str, unit: str = "") -> str:
    u = f'<span class="stat-unit">{unit}</span>' if unit else ""
    return (
        f'<div class="stat">'
        f'<div class="stat-lbl">{label}</div>'
        f'<div class="stat-val">{value}{u}</div>'
        f'</div>'
    )


def html_for_exercise(ex: dict, display_tz=None) -> str:
    start = parse_dt(ex.get("start_time"))
    end   = parse_dt(ex.get("end_time"))
    if not start or not end:
        return ""

    if display_tz:
        start = start.astimezone(display_tz)
        end   = end.astimezone(display_tz)

    type_code       = int(ex.get("type_code") or 0)
    emoji, label    = HC_EXERCISE_TYPES.get(type_code, ("🏃", f"Exercise ({type_code})"))
    title_raw       = (ex.get("title") or "").strip()
    activity        = title_raw if title_raw and title_raw.lower() != label.lower() else label
    accent          = ACTIVITY_COLORS.get(activity, ACTIVITY_COLORS.get(label, ACTIVITY_COLORS["_default"]))
    duration_min    = max(1, int((end - start).total_seconds() / 60))

    stats = [_stat_cell("Duration", fmt_dur(duration_min))]

    dist = safe_float(ex.get("distance_meters"))
    if dist and dist > 0:
        stats.append(_stat_cell("Distance", f"{dist / 1000:.2f}", "km"))

    cal = safe_float(ex.get("energy_calories"))
    if cal and cal > 0:
        stats.append(_stat_cell("Calories", str(int(cal)), "kcal"))

    avg_hr = safe_float(ex.get("avg_heart_rate_bpm"))
    if avg_hr and avg_hr > 0:
        stats.append(_stat_cell("Avg HR", str(int(avg_hr)), "bpm"))

    max_hr = safe_float(ex.get("max_heart_rate_bpm"))
    if max_hr and max_hr > 0:
        stats.append(_stat_cell("Max HR", str(int(max_hr)), "bpm"))

    notes = (ex.get("notes") or "").strip()
    notes_html = (
        f'<div class="notes"><div class="notes-lbl">Notes</div>'
        f'<div class="notes-txt">{notes}</div></div>'
    ) if notes else ""

    tz_label = f" ({display_tz})" if display_tz else " (UTC)"

    return f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1.0">
<title>{emoji} {activity} · {fmt_date_short(start)}</title>
<style>{_EXERCISE_CSS}</style>
</head>
<body>
<div class="card">
  <div class="hdr" style="background:linear-gradient(135deg,{accent}ee,{accent}bb)">
    <div class="hdr-emoji">{emoji}</div>
    <div class="hdr-title">{activity}</div>
    <div class="hdr-date">{fmt_date_full(start)}</div>
  </div>
  <div class="stats">{"".join(stats)}</div>
  <div class="time-row">
    <span class="tbadge">{fmt_time(start)}</span>
    <span class="arrow">&#8594;</span>
    <span class="tbadge">{fmt_time(end)}</span>
  </div>
  {notes_html}
  <div class="footer">Health Connect{tz_label} · {fmt_date_short(start)}</div>
</div>
</body>
</html>"""


def html_for_sleep(sl: dict, display_tz=None) -> str:
    start = parse_dt(sl.get("start_time"))
    end   = parse_dt(sl.get("end_time"))
    if not start or not end:
        return ""

    if display_tz:
        start = start.astimezone(display_tz)
        end   = end.astimezone(display_tz)

    total_min = max(1, int((end - start).total_seconds() / 60))

    stage_totals: dict[str, int] = {}
    for seg in sl.get("stages", []):
        seg_s = parse_dt(seg.get("start_time"))
        seg_e = parse_dt(seg.get("end_time"))
        code  = int(seg.get("stage_code") or 0)
        name  = HC_SLEEP_STAGES.get(code, f"Stage {code}")
        if seg_s and seg_e:
            mins = int((seg_e - seg_s).total_seconds() / 60)
            stage_totals[name] = stage_totals.get(name, 0) + mins

    # (name, css-color, dot-color)
    stage_order = [
        ("Deep Sleep",  "#1565C0", "#1565C0"),
        ("REM",         "#7B1FA2", "#7B1FA2"),
        ("Light Sleep", "#42A5F5", "#42A5F5"),
        ("Sleeping",    "#64B5F6", "#64B5F6"),
        ("Awake",       "#EF9A9A", "#EF9A9A"),
        ("Out of Bed",  "#E0E0E0", "#9E9E9E"),
        ("Unknown",     "#CFD8DC", "#B0BEC5"),
    ]

    bar_segs  = []
    list_rows = []
    shown: set[str] = set()

    for name, color, dot_color in stage_order:
        mins = stage_totals.get(name, 0)
        if mins > 0:
            pct = mins / total_min * 100
            bar_segs.append(
                f'<div style="width:{pct:.1f}%;background:{color}" title="{name}: {fmt_dur(mins)}"></div>'
            )
            list_rows.append(
                f'<div class="srow">'
                f'<div class="sdot" style="background:{dot_color}"></div>'
                f'<span class="sname">{name}</span>'
                f'<span class="sdur">{fmt_dur(mins)}</span>'
                f'</div>'
            )
            shown.add(name)

    for name, mins in stage_totals.items():
        if name not in shown and mins > 0:
            pct = mins / total_min * 100
            bar_segs.append(
                f'<div style="width:{pct:.1f}%;background:#CFD8DC" title="{name}: {fmt_dur(mins)}"></div>'
            )
            list_rows.append(
                f'<div class="srow">'
                f'<div class="sdot" style="background:#B0BEC5"></div>'
                f'<span class="sname">{name}</span>'
                f'<span class="sdur">{fmt_dur(mins)}</span>'
                f'</div>'
            )

    stages_block = ""
    if bar_segs:
        stages_block = (
            f'<div class="stages">'
            f'<div class="stages-lbl">Sleep Stages</div>'
            f'<div class="sbar">{"".join(bar_segs)}</div>'
            f'<div class="slist">{"".join(list_rows)}</div>'
            f'</div>'
        )

    tz_label = f" ({display_tz})" if display_tz else " (UTC)"

    return f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width,initial-scale=1.0">
<title>😴 Sleep · {fmt_date_short(start)}</title>
<style>{_SLEEP_CSS}</style>
</head>
<body>
<div class="card">
  <div class="hdr">
    <div class="hdr-emoji">😴</div>
    <div class="hdr-title">Sleep</div>
    <div class="hdr-date">{fmt_date_full(start)}</div>
  </div>
  <div class="main">
    <div class="dur-lbl">Total Sleep</div>
    <div class="dur">{fmt_dur(total_min)}</div>
  </div>
  <div class="time-row">
    <span class="tbadge">{fmt_time(start)}</span>
    <span class="arrow">&#8594;</span>
    <span class="tbadge">{fmt_time(end)}</span>
  </div>
  {stages_block}
  <div class="footer">Health Connect{tz_label} · {fmt_date_short(start)}</div>
</div>
</body>
</html>"""


# ─── Google Drive API ─────────────────────────────────────────────────────────

def get_drive_service():
    creds = None
    if TOKEN_FILE.exists():
        creds = Credentials.from_authorized_user_file(str(TOKEN_FILE), SCOPES)
    if not creds or not creds.valid:
        if creds and creds.expired and creds.refresh_token:
            creds.refresh(Request())
        else:
            if not CREDENTIALS_FILE.exists():
                _log(f"Error: '{CREDENTIALS_FILE}' not found.")
                _log("Copy credentials.json to your Termux home directory.")
                _write_log()
                sys.exit(1)
            flow = InstalledAppFlow.from_client_secrets_file(
                str(CREDENTIALS_FILE), SCOPES,
                redirect_uri='urn:ietf:wg:oauth:2.0:oob',
            )
            auth_url, _ = flow.authorization_url(prompt='consent')
            print('Open this URL in a browser:\n', auth_url)
            code = input('Paste the authorization code: ').strip()
            flow.fetch_token(code=code)
            creds = flow.credentials
        TOKEN_FILE.write_text(creds.to_json())
    return build("drive", "v3", credentials=creds)


_folder_cache: dict[tuple[str, str], str] = {}


def get_or_create_subfolder(service, name: str, parent_id: str | None = None) -> str:
    cache_key = (parent_id or "", name)
    if cache_key in _folder_cache:
        return _folder_cache[cache_key]
    parent_clause = f" and '{parent_id}' in parents" if parent_id else ""
    result = service.files().list(
        q=f"mimeType='application/vnd.google-apps.folder' and name='{name}' and trashed=false{parent_clause}",
        fields="files(id)",
        spaces="drive",
    ).execute()
    files = result.get("files", [])
    if files:
        folder_id = files[0]["id"]
    else:
        body: dict = {"name": name, "mimeType": "application/vnd.google-apps.folder"}
        if parent_id:
            body["parents"] = [parent_id]
        folder_id = service.files().create(body=body, fields="id").execute()["id"]
        _log(f"  Created Drive folder: '{name}'")
    _folder_cache[cache_key] = folder_id
    return folder_id


def get_month_folder(service, root_id: str, dt: datetime) -> str:
    year_id = get_or_create_subfolder(service, dt.strftime("%Y"),       root_id)
    return    get_or_create_subfolder(service, dt.strftime("%m - %B"),  year_id)


def upload_html(service, folder_id: str, filename: str, html: str) -> tuple[str, str]:
    media  = MediaInMemoryUpload(html.encode("utf-8"), mimetype="text/html", resumable=False)
    result = service.files().create(
        body={"name": filename, "parents": [folder_id], "mimeType": "text/html"},
        media_body=media,
        fields="id,webViewLink",
    ).execute()
    file_id = result["id"]
    web_url = result.get("webViewLink", f"https://drive.google.com/file/d/{file_id}/view")
    service.permissions().create(
        fileId=file_id,
        body={"role": "reader", "type": "anyone"},
    ).execute()
    return file_id, web_url


# ─── Manifest ─────────────────────────────────────────────────────────────────

def load_manifest(path: Path) -> dict:
    if path.exists():
        return json.loads(path.read_text(encoding="utf-8"))
    return {}


def save_manifest(manifest: dict, path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(manifest, indent=2, ensure_ascii=False), encoding="utf-8")


def record_key(record_type: str, start: datetime) -> str:
    return f"{record_type}_{start.astimezone(timezone.utc).strftime('%Y%m%dT%H%M%SZ')}"


# ─── Processing ───────────────────────────────────────────────────────────────

def process_exercises(
    data: dict, service, root_id: str, manifest: dict,
    dry_run: bool, display_tz
) -> int:
    records = data.get("exercises", [])
    _log(f"  Exercise records: {len(records)}")
    count = 0

    for ex in records:
        start = parse_dt(ex.get("start_time"))
        end   = parse_dt(ex.get("end_time"))
        if not start or not end:
            continue

        key = record_key("exercise", start)
        if key in manifest:
            _log(f"    [skip] {key}")
            continue

        type_code    = int(ex.get("type_code") or 0)
        emoji, label = HC_EXERCISE_TYPES.get(type_code, ("🏃", "Exercise"))
        title_raw    = (ex.get("title") or "").strip()
        activity     = title_raw if title_raw and title_raw.lower() != label.lower() else label
        duration_min = max(1, int((end - start).total_seconds() / 60))
        summary      = f"{emoji} {activity} – {fmt_dur(duration_min)}"
        filename     = f"exercise_{start.strftime('%Y%m%d_%H%M%S')}.html"
        path_hint    = f"{start.strftime('%Y/%m - %B')}/{filename}"

        if dry_run:
            _log(f"    [DRY RUN] {summary}  →  {path_hint}")
            count += 1
            continue

        html = html_for_exercise(ex, display_tz)
        if not html:
            continue

        folder_id = get_month_folder(service, root_id, start)
        try:
            file_id, web_url = upload_html(service, folder_id, filename, html)
        except HttpError as exc:
            _log(f"    Error uploading {filename}: {exc}")
            continue

        manifest[key] = {
            "type":          "exercise",
            "start_time":    start.astimezone(timezone.utc).isoformat(),
            "end_time":      end.astimezone(timezone.utc).isoformat(),
            "summary":       summary,
            "drive_file_id": file_id,
            "drive_url":     web_url,
            "filename":      filename,
            "uploaded_at":   datetime.now(timezone.utc).isoformat(),
        }
        _log(f"    ✓ {summary}")
        count += 1

    return count


def process_sleep(
    data: dict, service, root_id: str, manifest: dict,
    dry_run: bool, display_tz
) -> int:
    records = data.get("sleep_sessions", [])
    _log(f"  Sleep records: {len(records)}")
    count = 0

    for sl in records:
        start = parse_dt(sl.get("start_time"))
        end   = parse_dt(sl.get("end_time"))
        if not start or not end:
            continue

        key = record_key("sleep", start)
        if key in manifest:
            _log(f"    [skip] {key}")
            continue

        total_min = max(1, int((end - start).total_seconds() / 60))
        summary   = f"😴 Sleep – {fmt_dur(total_min)}"
        filename  = f"sleep_{start.strftime('%Y%m%d_%H%M%S')}.html"
        path_hint = f"{start.strftime('%Y/%m - %B')}/{filename}"

        if dry_run:
            _log(f"    [DRY RUN] {summary}  →  {path_hint}")
            count += 1
            continue

        html = html_for_sleep(sl, display_tz)
        if not html:
            continue

        folder_id = get_month_folder(service, root_id, start)
        try:
            file_id, web_url = upload_html(service, folder_id, filename, html)
        except HttpError as exc:
            _log(f"    Error uploading {filename}: {exc}")
            continue

        manifest[key] = {
            "type":          "sleep",
            "start_time":    start.astimezone(timezone.utc).isoformat(),
            "end_time":      end.astimezone(timezone.utc).isoformat(),
            "summary":       summary,
            "drive_file_id": file_id,
            "drive_url":     web_url,
            "filename":      filename,
            "uploaded_at":   datetime.now(timezone.utc).isoformat(),
        }
        _log(f"    ✓ {summary}")
        count += 1

    return count


# ─── CLI ──────────────────────────────────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="Generate HTML reports from Health Connect data and upload to Google Drive.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog=__doc__,
    )
    parser.add_argument("--data", "-d", default=str(DEFAULT_DATA_FILE), metavar="PATH",
                        help=f"JSON file written by Tasker (default: {DEFAULT_DATA_FILE})")
    parser.add_argument("--manifest", default=str(MANIFEST_FILE), metavar="PATH",
                        help=f"Manifest file tracking uploaded files (default: {MANIFEST_FILE})")
    parser.add_argument("--timezone", "-tz", default=None, metavar="TZ",
                        help="Local timezone for display in HTML, e.g. Asia/Jerusalem")
    parser.add_argument("--dry-run", action="store_true",
                        help="Show what would be uploaded without touching Google Drive.")
    parser.add_argument("--no-exercise", action="store_true", help="Skip exercise records.")
    parser.add_argument("--no-sleep",    action="store_true", help="Skip sleep records.")
    parser.add_argument("--auth-only",   action="store_true",
                        help="Authenticate with Google Drive and save token, then exit.")

    args = parser.parse_args()

    _log(f"=== Health Connect → Google Drive  {datetime.now():%Y-%m-%d %H:%M} ===")

    display_tz = None
    if args.timezone:
        try:
            from zoneinfo import ZoneInfo
            display_tz = ZoneInfo(args.timezone)
        except Exception:
            _log(f"Warning: unknown timezone '{args.timezone}', using UTC")

    if args.auth_only:
        _log("\nAuthenticating with Google Drive…")
        get_drive_service()
        _log("Done — token saved.")
        _write_log()
        return

    data_path = Path(args.data)
    if not data_path.exists():
        _log(f"Error: data file not found: {data_path}")
        _log("Run the Tasker task first to export Health Connect data.")
        _write_log()
        sys.exit(1)

    data = json.loads(data_path.read_text(encoding="utf-8"))
    _log(f"Data generated: {data.get('generated_at', 'unknown')}")

    manifest_path = Path(args.manifest)
    manifest = load_manifest(manifest_path)
    _log(f"Manifest: {len(manifest)} existing entries")

    if args.dry_run:
        _log("\n[DRY RUN — nothing will be written to Google Drive]\n")
        service = root_id = None
    else:
        _log("\nConnecting to Google Drive…")
        service = get_drive_service()
        root_id = get_or_create_subfolder(service, DRIVE_FOLDER_NAME)
        _log(f"Drive root: '{DRIVE_FOLDER_NAME}'  ({root_id})")

    _log("\nProcessing…")
    total = 0

    if not args.no_exercise:
        total += process_exercises(data, service, root_id, manifest, args.dry_run, display_tz)
    if not args.no_sleep:
        total += process_sleep(data, service, root_id, manifest, args.dry_run, display_tz)

    if not args.dry_run and total > 0:
        save_manifest(manifest, manifest_path)
        _log(f"\nManifest saved → {manifest_path}")

    _log(f"\nDone! {total} record(s) processed.")
    _write_log()


if __name__ == "__main__":
    main()
