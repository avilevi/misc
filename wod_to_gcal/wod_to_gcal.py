#!/usr/bin/env python3
"""
wod_to_gcal.py

Scans Google Calendar for "WOD" events in the next N days, populates their
description with the 🔥WOD section from hypr-workouts.pages.dev, generates
an HTML page, uploads it to a Google Drive folder, and attaches it to the event.

First run requires interactive auth (copy/paste a URL into a browser).
Subsequent runs use the cached token.pickle and need no interaction.
"""

import re
import html as html_mod
import pickle
import datetime
import argparse
import urllib.request
from pathlib import Path

from google.auth.transport.requests import Request
from google_auth_oauthlib.flow import InstalledAppFlow
from googleapiclient.discovery import build
from googleapiclient.http import MediaInMemoryUpload

SCOPES = [
    'https://www.googleapis.com/auth/calendar',
    'https://www.googleapis.com/auth/drive.file',
]
WORKOUTS_URL = 'https://hypr-workouts.pages.dev/'
HANDLED_MARKER = '# populated by wod_to_gcal'
DRIVE_FOLDER_NAME = 'HYPR WOD'

SCRIPT_DIR = Path(__file__).parent
DEFAULT_CREDENTIALS = SCRIPT_DIR / 'client_secret.json'
DEFAULT_TOKEN = SCRIPT_DIR / 'token.pickle'


# ---------------------------------------------------------------------------
# Auth
# ---------------------------------------------------------------------------

def get_credentials(credentials_path, token_path):
    credentials_path = Path(credentials_path)
    token_path = Path(token_path)

    creds = None
    if token_path.exists():
        with open(token_path, 'rb') as f:
            creds = pickle.load(f)

    if not creds or not creds.valid:
        if creds and creds.expired and creds.refresh_token:
            try:
                creds.refresh(Request())
            except Exception:
                print('Token refresh failed — deleting cached token, re-authenticating.')
                token_path.unlink(missing_ok=True)
                creds = None

        if not creds or not creds.valid:
            if not credentials_path.exists():
                raise FileNotFoundError(
                    f"Credentials file not found: {credentials_path}\n"
                    "Copy client_secret.json from google_calendar_thrusters or create a new\n"
                    "OAuth 2.0 Desktop app credential at console.cloud.google.com."
                )
            flow = InstalledAppFlow.from_client_secrets_file(
                str(credentials_path), SCOPES,
                redirect_uri='urn:ietf:wg:oauth:2.0:oob',
            )
            auth_url, _ = flow.authorization_url(prompt='consent')
            print('\nOpen this URL in a browser to authorize:\n')
            print(auth_url)
            print()
            code = input('Paste the authorization code: ').strip()
            flow.fetch_token(code=code)
            creds = flow.credentials

        with open(token_path, 'wb') as f:
            pickle.dump(creds, f)

    return creds


# ---------------------------------------------------------------------------
# Calendar helpers
# ---------------------------------------------------------------------------

def find_calendar_id(service, calendar_name):
    result = service.calendarList().list().execute()
    for cal in result.get('items', []):
        if cal.get('summary', '').lower() == calendar_name.lower():
            return cal['id']
    return 'primary'


def get_wod_events(service, calendar_id, days):
    today = datetime.datetime.now(datetime.timezone.utc).date()
    time_min = datetime.datetime(today.year, today.month, today.day,
                                 tzinfo=datetime.timezone.utc).isoformat()
    time_max = (datetime.datetime(today.year, today.month, today.day,
                                  tzinfo=datetime.timezone.utc)
                + datetime.timedelta(days=days)).isoformat()

    events = []
    page_token = None
    while True:
        result = service.events().list(
            calendarId=calendar_id,
            timeMin=time_min,
            timeMax=time_max,
            q='WOD',
            singleEvents=True,
            orderBy='startTime',
            pageToken=page_token,
        ).execute()
        events.extend(result.get('items', []))
        page_token = result.get('nextPageToken')
        if not page_token:
            break

    return [e for e in events if e.get('summary', '').strip().upper() == 'WOD']


def event_date(event):
    start = event.get('start', {})
    return start.get('date') or start.get('dateTime', '')[:10]


def already_handled(event):
    return HANDLED_MARKER in (event.get('description') or '')


# ---------------------------------------------------------------------------
# Workout scraping
# ---------------------------------------------------------------------------

def fetch_page(url):
    req = urllib.request.Request(url, headers={'User-Agent': 'wod-to-gcal/1.0'})
    with urllib.request.urlopen(req, timeout=30) as resp:
        return resp.read().decode('utf-8')


def extract_wod_for_date(page_html, date_str):
    """Return plain-text WOD content for date_str (YYYY-MM-DD), or None."""
    pattern = re.compile(
        r'<details[^>]+name="' + re.escape(date_str) + r'"[^>]*>'
        r'.*?<summary[^>]*>.*?🔥WOD.*?</summary>'
        r'.*?<p class="workout-summary">(.*?)</p>',
        re.DOTALL,
    )
    match = pattern.search(page_html)
    if not match:
        return None
    return html_mod.unescape(match.group(1)).strip()


# ---------------------------------------------------------------------------
# HTML generation
# ---------------------------------------------------------------------------

def generate_wod_html(date_str, wod_text):
    day_name = datetime.date.fromisoformat(date_str).strftime('%A, %B %-d %Y')
    lines = wod_text.split('\n')
    body_lines = []
    for line in lines:
        if not line.strip():
            body_lines.append('<br>')
        else:
            body_lines.append(f'<p>{html_mod.escape(line)}</p>')
    content = '\n'.join(body_lines)
    return f"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>WOD — {date_str}</title>
  <style>
    body {{
      font-family: system-ui, -apple-system, sans-serif;
      max-width: 640px;
      margin: 2rem auto;
      padding: 0 1.25rem;
      color: #1a1a1a;
      line-height: 1.6;
    }}
    header {{
      border-bottom: 2px solid #f03;
      padding-bottom: .5rem;
      margin-bottom: 1.5rem;
    }}
    h1 {{ margin: 0; font-size: 1.6rem; }}
    .date {{ color: #666; font-size: .9rem; margin-top: .25rem; }}
    p {{ margin: .15rem 0; }}
    br {{ display: block; margin: .4rem 0; }}
  </style>
</head>
<body>
  <header>
    <h1>🔥 WOD</h1>
    <div class="date">{day_name}</div>
  </header>
  <main>
{content}
  </main>
</body>
</html>
"""


# ---------------------------------------------------------------------------
# Drive helpers
# ---------------------------------------------------------------------------

def _get_or_create_drive_folder(drive_service, name):
    results = drive_service.files().list(
        q=f"name='{name}' and mimeType='application/vnd.google-apps.folder' and trashed=false",
        fields='files(id)',
    ).execute()
    files = results.get('files', [])
    if files:
        return files[0]['id']
    folder = drive_service.files().create(
        body={'name': name, 'mimeType': 'application/vnd.google-apps.folder'},
        fields='id',
    ).execute()
    return folder['id']


def _list_drive_folder_files(drive_service, folder_id):
    """Return {filename: file_id} for all files in the folder."""
    existing = {}
    page_token = None
    while True:
        result = drive_service.files().list(
            q=f"'{folder_id}' in parents and trashed=false",
            fields='files(id,name)',
            pageToken=page_token,
        ).execute()
        for f in result.get('files', []):
            existing[f['name']] = f['id']
        page_token = result.get('nextPageToken')
        if not page_token:
            break
    return existing


def drive_upsert(drive_service, folder_id, filename, html_content, existing):
    """Create or update an HTML file in Drive; return the file resource."""
    media = MediaInMemoryUpload(html_content.encode('utf-8'),
                                mimetype='text/html', resumable=False)
    if filename in existing:
        return drive_service.files().update(
            fileId=existing[filename],
            media_body=media,
            fields='id,webViewLink',
        ).execute()
    return drive_service.files().create(
        body={'name': filename, 'parents': [folder_id], 'mimeType': 'text/html'},
        media_body=media,
        fields='id,webViewLink',
    ).execute()


def upload_wod_to_drive(drive_service, date_str, wod_text):
    """Generate HTML, upload to HYPR WOD Drive folder, return webViewLink."""
    folder_id = _get_or_create_drive_folder(drive_service, DRIVE_FOLDER_NAME)
    existing = _list_drive_folder_files(drive_service, folder_id)
    filename = f'{date_str} - WOD.html'
    html_content = generate_wod_html(date_str, wod_text)
    file = drive_upsert(drive_service, folder_id, filename, html_content, existing)
    print(f"  Drive: {filename} → {file.get('webViewLink', '')}")
    return file['webViewLink']


# ---------------------------------------------------------------------------
# Calendar update
# ---------------------------------------------------------------------------

def update_event(cal_service, calendar_id, event, wod_text, drive_link=None):
    event['description'] = wod_text + '\n\n' + HANDLED_MARKER
    if drive_link:
        date_str = event_date(event)
        event['attachments'] = [{
            'fileUrl': drive_link,
            'mimeType': 'text/html',
            'title': f'{date_str} - WOD.html',
        }]
    return cal_service.events().update(
        calendarId=calendar_id,
        eventId=event['id'],
        body=event,
        supportsAttachments=True,
    ).execute()


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    parser = argparse.ArgumentParser(
        description='Populate WOD calendar events from hypr-workouts.pages.dev'
    )
    parser.add_argument('--calendar', default='Sports',
                        help='Google Calendar name to search (default: Sports)')
    parser.add_argument('--credentials', default=str(DEFAULT_CREDENTIALS),
                        help='Path to OAuth client_secret.json')
    parser.add_argument('--token', default=str(DEFAULT_TOKEN),
                        help='Path to cached OAuth token pickle')
    parser.add_argument('--days', type=int, default=3,
                        help='How many days ahead to scan (default: 3)')
    parser.add_argument('--no-drive', action='store_true',
                        help='Skip Drive upload and attachment')
    parser.add_argument('--force', action='store_true',
                        help='Re-populate events that already have a description')
    parser.add_argument('--dry-run', action='store_true',
                        help='Print what would be done without making any changes')
    args = parser.parse_args()

    creds = get_credentials(args.credentials, args.token)
    cal_service = build('calendar', 'v3', credentials=creds)
    drive_service = None if args.no_drive else build('drive', 'v3', credentials=creds)

    calendar_id = find_calendar_id(cal_service, args.calendar)
    print(f"Calendar: {args.calendar!r}")

    wod_events = get_wod_events(cal_service, calendar_id, args.days)
    if not wod_events:
        print(f"No WOD events found in the next {args.days} day(s).")
        return

    print(f"Found {len(wod_events)} WOD event(s). Fetching workouts...")
    page_html = fetch_page(WORKOUTS_URL)

    for event in wod_events:
        date = event_date(event)

        if not args.force and already_handled(event):
            print(f"  {date}: already populated — skipping (use --force to redo).")
            continue

        wod_text = extract_wod_for_date(page_html, date)
        if wod_text is None:
            print(f"  {date}: no WOD found on website for this date.")
            continue

        if args.dry_run:
            print(f"  {date}: [dry-run] would set description to:\n---\n{wod_text}\n---")
            continue

        drive_link = None
        if drive_service:
            drive_link = upload_wod_to_drive(drive_service, date, wod_text)

        update_event(cal_service, calendar_id, event, wod_text, drive_link)
        print(f"  {date}: calendar event updated.")


if __name__ == '__main__':
    main()
