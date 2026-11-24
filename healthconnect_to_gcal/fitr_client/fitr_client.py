#!/usr/bin/env python3
"""Fitr client for HYPR workouts.

Reads the HYPR programming directly from Fitr's mobile API
(https://app.fitr.training/mobile/api/v15/) instead of scraping
hypr-workouts.pages.dev.

Auth is the same OAuth2 password grant the official Fitr client uses: a form
POST to users/sign_in carrying the app's own client_id/client_secret plus the
user's email and password. The access token is cached and re-minted on a 401.

Credentials, first match wins:
  1. $FITR_EMAIL / $FITR_PASSWORD
  2. ~/.config/fitr/credentials          (email=... / password=...)
  3. ~/.config/personal-assistant/agents/fitr-login.json  (same format)

Examples:
  ./fitr_client.py login
  ./fitr_client.py days --from 2026-11-16 --to 2026-11-30
  ./fitr_client.py show --date 2026-11-23
  ./fitr_client.py pull --from 2026-11-01 --to 2026-11-30 --out workouts.json
  ./fitr_client.py history --since 2024-01-01 --out history.json
  ./fitr_client.py note --date 2026-11-23 --type WOD --text "..." --yes
  ./fitr_client.py complete --date 2026-11-23 --type WOD --yes
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import date, datetime, timedelta, timezone
from html import unescape
from pathlib import Path

BASE = "https://app.fitr.training/mobile/api/v15/"
CLIENT_ID = "r4QOoCIXlpRsI0HSSsk4MNgYtH2e6AxZqkuASTGkli0"
CLIENT_SECRET = "SBQDsewDnDtaFMFx9Xf995iMlnM750-0PFRzGIn2AJI"
USER_AGENT = "okhttp/4.12.0"

CONFIG_DIR = Path.home() / ".config" / "fitr"
CACHE_DIR = Path.home() / ".cache" / "fitr_client"
SESSION_FILE = CACHE_DIR / "session.json"
DAYS_DIR = CACHE_DIR / "days"

CRED_FILES = [
    CONFIG_DIR / "credentials",
    Path.home() / ".config" / "personal-assistant" / "agents" / "fitr-login.json",
]


class FitrError(Exception):
    pass


class AuthError(FitrError):
    pass


# ── credentials and session ───────────────────────────────────────────────


def load_credentials() -> tuple[str, str]:
    email = os.environ.get("FITR_EMAIL")
    password = os.environ.get("FITR_PASSWORD")
    if email and password:
        return email, password
    for path in CRED_FILES:
        if not path.is_file():
            continue
        values = {}
        for line in path.read_text().splitlines():
            line = line.strip()
            if not line or line.startswith("#") or "=" not in line:
                continue
            key, value = line.split("=", 1)
            values[key.strip()] = value.strip()
        if values.get("email") and values.get("password"):
            return values["email"], values["password"]
    raise FitrError(
        "No Fitr credentials. Set FITR_EMAIL/FITR_PASSWORD or write "
        f"{CONFIG_DIR / 'credentials'} with email= / password= lines."
    )


def _write_private(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(text)
    os.chmod(tmp, 0o600)
    tmp.replace(path)


class FitrClient:
    def __init__(self, email: str | None = None, password: str | None = None):
        self._email = email
        self._password = password
        self._auth: str | None = None

    # -- auth ------------------------------------------------------------

    def _credentials(self) -> tuple[str, str]:
        if self._email and self._password:
            return self._email, self._password
        return load_credentials()

    def _cached_auth(self) -> str | None:
        if not SESSION_FILE.is_file():
            return None
        try:
            data = json.loads(SESSION_FILE.read_text())
        except Exception:
            return None
        email, _ = self._credentials()
        if data.get("email") != email:
            return None
        return data.get("auth")

    def sign_in(self) -> str:
        email, password = self._credentials()
        status, body = self._request(
            "users/sign_in",
            method="POST",
            form={
                "client_id": CLIENT_ID,
                "client_secret": CLIENT_SECRET,
                "email": email,
                "password": password,
            },
            auth=None,
        )
        if status != 200:
            raise AuthError(f"sign-in failed ({status}): {_error_text(body)}")
        data = json.loads(body)
        auth = f'{data["token_type"]} {data["access_token"]}'
        self._auth = auth
        _write_private(
            SESSION_FILE,
            json.dumps({"email": email, "auth": auth, "saved_at": int(time.time())}),
        )
        return auth

    def auth(self) -> str:
        if self._auth:
            return self._auth
        cached = self._cached_auth()
        if cached:
            self._auth = cached
            return cached
        return self.sign_in()

    # -- transport --------------------------------------------------------

    @staticmethod
    def _request(path: str, method: str = "GET", form: dict | None = None,
                 auth: str | None = "") -> tuple[int, bytes]:
        url = BASE + path
        headers = {"Accept": "application/json", "User-Agent": USER_AGENT}
        body = None
        if form is not None:
            body = urllib.parse.urlencode(form, doseq=True).encode()
            headers["Content-Type"] = "application/x-www-form-urlencoded"
        if auth:
            headers["Authorization"] = auth
        req = urllib.request.Request(url, data=body, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=40) as resp:
                return resp.status, resp.read()
        except urllib.error.HTTPError as e:
            return e.code, e.read()

    def get(self, path: str, params: dict | None = None) -> bytes:
        if params:
            path += ("&" if "?" in path else "?") + urllib.parse.urlencode(params)
        status, body = self._request(path, auth=self.auth())
        if status == 401:
            self._auth = None
            status, body = self._request(path, auth=self.sign_in())
        if status != 200:
            raise FitrError(f"GET {path}: HTTP {status}: {_error_text(body)}")
        return body

    def post(self, path: str, form: dict) -> bytes:
        status, body = self._request(path, method="POST", form=form, auth=self.auth())
        if status == 401:
            self._auth = None
            status, body = self._request(path, method="POST", form=form, auth=self.sign_in())
        if status not in (200, 201, 204):
            raise FitrError(f"POST {path}: HTTP {status}: {_error_text(body)}")
        return body

    def delete(self, path: str) -> bytes:
        status, body = self._request(path, method="DELETE", auth=self.auth())
        if status == 401:
            self._auth = None
            status, body = self._request(path, method="DELETE", auth=self.sign_in())
        if status not in (200, 204):
            raise FitrError(f"DELETE {path}: HTTP {status}: {_error_text(body)}")
        return body

    # -- API --------------------------------------------------------------

    def me(self) -> dict:
        return json.loads(self.get("users/me"))

    def schedules(self, start: str, end: str) -> list[dict]:
        data = json.loads(self.get("schedules", {"from": start, "to": end}))
        return data.get("schedules") or []

    def day(self, day: str, plan_id: int, track_id: int | None = None) -> dict:
        params = {}
        if track_id:
            params["plan_track_id"] = track_id
        return json.loads(
            self.get(f"athlete/schedules/{day}/plans/{plan_id}/details", params)
        )

    def section(self, schedule_id: int, section_id: int) -> dict:
        return json.loads(self.get(f"schedules/{schedule_id}/sections/{section_id}"))

    def performances(self, schedule_id: int) -> list[dict]:
        return json.loads(self.get(f"schedules/{schedule_id}/performances"))

    # -- writes (real; call only with explicit consent) --------------------

    def save_performance(self, schedule_id: int, section_id: int, text: str,
                         complete: bool | None = None) -> dict:
        if complete is None:
            complete = bool(
                self.section(schedule_id, section_id).get("completed")
            )
        return json.loads(self.post(
            f"schedules/{schedule_id}/sections/{section_id}/performance",
            {"performance[text]": text, "complete_section": str(complete).lower()},
        ))

    def set_complete(self, schedule_id: int, section_id: int, complete: bool) -> bytes:
        verb = "POST" if complete else "DELETE"
        path = f"schedules/{schedule_id}/sections/{section_id}/complete"
        status, body = self._request(path, method=verb, auth=self.auth())
        if status == 401:
            self._auth = None
            status, body = self._request(path, method=verb, auth=self.sign_in())
        if status not in (200, 204):
            raise FitrError(f"{verb} {path}: HTTP {status}: {_error_text(body)}")
        return body

    def comment(self, schedule_id: int, text: str) -> dict:
        body = json.dumps({"comment": {"text": text, "attachment_ids": []}}).encode()
        for attempt in (0, 1):
            req = urllib.request.Request(
                BASE + f"schedules/{schedule_id}/comments", data=body, method="POST",
                headers={"Authorization": self.auth(), "Accept": "application/json",
                         "Content-Type": "application/json", "User-Agent": USER_AGENT},
            )
            try:
                with urllib.request.urlopen(req, timeout=40) as resp:
                    return json.loads(resp.read())
            except urllib.error.HTTPError as e:
                if e.code == 401 and attempt == 0:
                    self._auth = None
                    continue
                raise FitrError(f"comment: HTTP {e.code}: {_error_text(e.read())}")
        raise FitrError("comment: authentication kept failing")


# ── normalisation ─────────────────────────────────────────────────────────

STYLE_BLOCK = re.compile(r"<style.*?</style>", re.S | re.I)
BREAK = re.compile(r"<br\s*/?>", re.I)
TAG = re.compile(r"<[^>]+>")
SPACES = re.compile(r"[ \t]+")


def html_to_text(raw: str) -> str:
    """Section description HTML -> the workout text the app stores."""
    text = STYLE_BLOCK.sub(" ", raw or "")
    text = BREAK.sub("\n", text)
    text = TAG.sub("", text)
    text = unescape(text)
    lines = [SPACES.sub(" ", line).strip() for line in text.splitlines()]
    out: list[str] = []
    for line in lines:
        if not line:
            if out and out[-1] != "":
                out.append("")
            continue
        out.append(line)
    return "\n".join(out).strip()


def type_key(label: str) -> str:
    upper = label.upper()
    if "WOD" in upper:
        return "wod"
    if "HYROX" in upper:
        return "hyrox"
    if "PUMP" in upper and "BLEND" in upper:
        return "pump_blend"
    if "PUMP" in upper:
        return "pump"
    if "WEIGHTLIFTING" in upper:
        return "weightlifting"
    key = re.sub(r"[^a-z0-9]+", "_", label.lower()).strip("_")
    return key or "other"


def workouts_for_day(
    schedule: dict, details: dict | None = None
) -> list[dict]:
    """One schedule entry -> workout rows (date, type_key, type_label, content)."""
    day = datetime.fromtimestamp(schedule["date"], tz=timezone.utc).date().isoformat()
    sections = (details or {}).get("sections") or schedule.get("sections") or []
    rows: list[dict] = []
    seen: set[str] = set()
    for order, sec in enumerate(sections):
        label = (sec.get("title") or "").strip()
        if not label:
            continue
        key = type_key(label)
        if key in seen:
            continue
        seen.add(key)
        rows.append({
            "date": day,
            "type_key": key,
            "type_label": label,
            "content": html_to_text(sec.get("description") or ""),
            "sort_order": sec.get("position", order),
            "schedule_id": schedule.get("id"),
            "section_id": sec.get("id"),
        })
    return rows


def pull_range(client: FitrClient, start: str, end: str, *, cache: bool = True,
               progress=None) -> list[dict]:
    """Pull every day in a range, one details request per schedule entry."""
    entries = client.schedules(start, end)
    today = date.today()
    rows: list[dict] = []
    for entry in entries:
        day = datetime.fromtimestamp(entry["date"], tz=timezone.utc).date()
        plan = entry.get("plan") or {}
        plan_id = plan.get("id")
        track_id = plan.get("default_track_id")
        if plan.get("plan_tracks"):
            track_id = plan["plan_tracks"][0].get("id") or track_id
        details = None
        cache_file = DAYS_DIR / f"{day.isoformat()}_{plan_id}.json"
        if cache and cache_file.is_file() and day < today:
            try:
                details = json.loads(cache_file.read_text())
            except Exception:
                details = None
        if details is None:
            try:
                details = client.day(day.isoformat(), plan_id, track_id)
            except FitrError as e:
                if progress:
                    progress(f"{day}: {e}")
                details = None
            else:
                if cache and day < today:
                    _write_private(cache_file, json.dumps(details))
        rows.extend(workouts_for_day(entry, details))
        if progress:
            progress(f"{day}: {len(rows)} rows so far")
    return rows


# ── CLI ───────────────────────────────────────────────────────────────────


def _error_text(body: bytes) -> str:
    try:
        data = json.loads(body)
        errors = data.get("errors")
        if errors:
            return "; ".join(
                str(e.get("detail") or e.get("title") or e)
                for e in errors
            )
        return json.dumps(data)[:300]
    except Exception:
        return body.decode("utf-8", "replace")[:300]


def _match_section(client: FitrClient, day: str, kind: str) -> tuple[int, int, str]:
    entries = client.schedules(day, day)
    if not entries:
        raise FitrError(f"no workout on {day}")
    for entry in entries:
        plan = entry.get("plan") or {}
        details = client.day(day, plan.get("id"),
                             (plan.get("plan_tracks") or [{}])[0].get("id"))
        for sec in details.get("sections") or []:
            label = (sec.get("title") or "").strip()
            if type_key(label) == type_key(kind) or kind.lower() in label.lower():
                return entry["id"], sec["id"], label
    raise FitrError(f"no section matching {kind!r} on {day}")


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description="Fitr client for HYPR workouts")
    sub = parser.add_subparsers(dest="cmd", required=True)

    sub.add_parser("login", help="sign in and print the account")

    p = sub.add_parser("days", help="list scheduled days")
    p.add_argument("--from", dest="start", required=True)
    p.add_argument("--to", dest="end", required=True)

    p = sub.add_parser("show", help="print one day's workouts")
    p.add_argument("--date", required=True)

    p = sub.add_parser("pull", help="pull a range into JSON")
    p.add_argument("--from", dest="start", required=True)
    p.add_argument("--to", dest="end", required=True)
    p.add_argument("--out")
    p.add_argument("--quiet", action="store_true")

    p = sub.add_parser("history", help="pull everything since a date")
    p.add_argument("--since", default="2024-01-01")
    p.add_argument("--to", default=date.today().isoformat())
    p.add_argument("--out")
    p.add_argument("--quiet", action="store_true")

    p = sub.add_parser("note", help="write a section performance note")
    p.add_argument("--date", required=True)
    p.add_argument("--type", required=True)
    p.add_argument("--text", required=True)
    p.add_argument("--yes", action="store_true")

    p = sub.add_parser("complete", help="mark a section complete")
    p.add_argument("--date", required=True)
    p.add_argument("--type", required=True)
    p.add_argument("--undo", action="store_true")
    p.add_argument("--yes", action="store_true")

    p = sub.add_parser("comment", help="post to a day's comment thread")
    p.add_argument("--date", required=True)
    p.add_argument("--text", required=True)
    p.add_argument("--yes", action="store_true")

    args = parser.parse_args(argv)
    client = FitrClient()

    try:
        if args.cmd == "login":
            me = client.me()
            print(f'signed in: {me.get("full_name")} <{me.get("email")}> id={me.get("id")}')
        elif args.cmd == "days":
            for entry in client.schedules(args.start, args.end):
                day = datetime.fromtimestamp(entry["date"], tz=timezone.utc).date()
                titles = ", ".join(s.get("title", "") for s in entry.get("sections") or [])
                print(f'{day}  schedule {entry["id"]}  {titles}')
        elif args.cmd == "show":
            entries = client.schedules(args.date, args.date)
            if not entries:
                print("no workout")
                return 1
            for entry in entries:
                plan = entry.get("plan") or {}
                details = client.day(args.date, plan.get("id"),
                                     (plan.get("plan_tracks") or [{}])[0].get("id"))
                for sec in details.get("sections") or []:
                    print(f'===== {sec.get("title")} (section {sec.get("id")}, '
                          f'schedule {entry["id"]})')
                    print(html_to_text(sec.get("description") or ""))
                    print()
        elif args.cmd in ("pull", "history"):
            progress = None if args.quiet else (
                lambda line: print(line, file=sys.stderr)
            )
            rows = pull_range(client, args.start, args.end, progress=progress)
            payload = json.dumps(rows, ensure_ascii=False, indent=1)
            if args.out:
                Path(args.out).write_text(payload)
                print(f"wrote {len(rows)} workouts to {args.out}")
            else:
                print(payload)
        elif args.cmd == "note":
            if not args.yes:
                raise FitrError("refusing to write without --yes")
            schedule_id, section_id, label = _match_section(client, args.date, args.type)
            result = client.save_performance(schedule_id, section_id, args.text)
            print(json.dumps(result, ensure_ascii=False))
        elif args.cmd == "complete":
            if not args.yes:
                raise FitrError("refusing to write without --yes")
            schedule_id, section_id, label = _match_section(client, args.date, args.type)
            client.set_complete(schedule_id, section_id, not args.undo)
            print(f'{"unmarked" if args.undo else "completed"}: {label}')
        elif args.cmd == "comment":
            if not args.yes:
                raise FitrError("refusing to write without --yes")
            entries = client.schedules(args.date, args.date)
            if not entries:
                raise FitrError(f"no workout on {args.date}")
            print(json.dumps(client.comment(entries[0]["id"], args.text),
                             ensure_ascii=False))
        return 0
    except FitrError as e:
        print(f"error: {e}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
