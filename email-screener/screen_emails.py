#!/usr/bin/env python3
"""
Email Screener — local, on-demand, no online registration required.
Reads from Outlook (macOS) via AppleScript, screens with a local LLM (Ollama)
or GitHub Copilot (via GitHub Models API), and outputs a Markdown + CSV action report.

Usage:
    python screen_emails.py                          # use dates from config.json
    python screen_emails.py --start 2026-05-20 --end 2026-05-26
    python screen_emails.py --start 2026-05-20 --end 2026-05-26 --no-llm
    python screen_emails.py --provider github        # use GitHub Copilot / GitHub Models
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import re
import subprocess
import sys
import textwrap
from datetime import datetime, timedelta
from pathlib import Path

try:
    import requests
except ImportError:
    requests = None  # handled gracefully below

# ---------------------------------------------------------------------------
# Config
# ---------------------------------------------------------------------------

CONFIG_FILE = Path(__file__).parent / "config.json"

DEFAULT_CONFIG = {
    "date_range_days": 7,          # default lookback window when no dates given
    "llm_provider": "github",      # "github" or "ollama"
    "ollama_url": "http://localhost:11434",
    "ollama_model": "llama3",      # any instruct model available in Ollama
    "github_model": "gpt-4o-mini", # model to use via GitHub Models API
    "github_token": "",            # leave blank to auto-detect from GITHUB_TOKEN env or gh CLI
    "confidence_threshold": 0.6,   # only include actions above this confidence
    "output_dir": "",              # empty = <script dir>/reports
    "max_body_chars": 3000,        # truncate long bodies before sending to LLM
    "outlook_folder": "Inbox",     # Outlook folder name to read from
    "outlook_query_timeout_sec": 600,
    "outlook_include_body": False,
    "outlook_fetch_tail": 500,        # scan only the last N messages (avoids full mailbox scan)
    "user_name": "Avi",               # your first name — used to detect whether actions are assigned to you
}


def load_config() -> dict:
    cfg = dict(DEFAULT_CONFIG)
    if CONFIG_FILE.exists():
        with open(CONFIG_FILE) as f:
            cfg.update(json.load(f))
    return cfg


# ---------------------------------------------------------------------------
# AppleScript: read emails from Outlook
# ---------------------------------------------------------------------------

APPLESCRIPT_TEMPLATE = """
with timeout of {timeout_sec} seconds
tell application "Microsoft Outlook"
    set startDate to (current date)
    set year of startDate to {start_year}
    set month of startDate to {start_month}
    set day of startDate to {start_day}
    set time of startDate to 0

    set endDate to (current date)
    set year of endDate to {end_year}
    set month of endDate to {end_month}
    set day of endDate to {end_day}
    set time of endDate to 86399

    set matchingFolders to (mail folders whose name is "{folder}")
    if (count of matchingFolders) is 0 then
        error "Folder not found: {folder}"
    end if

    -- Pick the folder with the most messages
    set targetFolder to item 1 of matchingFolders
    set maxCount to -1
    repeat with f in matchingFolders
        set c to count messages of f
        if c > maxCount then
            set maxCount to c
            set targetFolder to f
        end if
    end repeat

    -- Messages are ordered newest-first (index 1 = most recent).
    -- Scan from index 1 up to fetch_tail to get recent mail without
    -- touching the rest of the 176k+ message mailbox.
    set totalCount to count messages of targetFolder
    set endIdx to {fetch_tail}
    if endIdx > totalCount then set endIdx to totalCount

    set outText to ""
    repeat with i from 1 to endIdx
        set msg to message i of targetFolder
        set msgDate to time sent of msg

        -- Skip messages outside our date window
        if msgDate < startDate or msgDate > endDate then
        else
            set msgIsRead to is read of msg

            -- Skip read messages when unread_only mode is on
            if {unread_filter} and msgIsRead then
            else
                set msgId to id of msg
                set msgSubject to subject of msg
                {body_expr}
                set msgDateStr to (msgDate as string)

                set msgSender to ""
                set senderRef to sender of msg
                if senderRef is not missing value then
                    try
                        set msgSender to address of senderRef
                    end try
                    if msgSender is "" then
                        try
                            set msgSender to name of senderRef
                        end try
                    end if
                end if
                if msgSender is "" then set msgSender to "unknown"

                -- Collect To recipients
                set msgTo to ""
                try
                    set toRecips to to recipients of msg
                    repeat with recip in toRecips
                        set recipAddr to ""
                        try
                            set recipAddr to address of recip
                        end try
                        if recipAddr is "" then
                            try
                                set recipAddr to name of recip
                            end try
                        end if
                        if recipAddr is not "" then
                            if msgTo is "" then
                                set msgTo to recipAddr
                            else
                                set msgTo to msgTo & ", " & recipAddr
                            end if
                        end if
                    end repeat
                end try

                -- Collect CC recipients
                set msgCc to ""
                try
                    set ccRecips to cc recipients of msg
                    repeat with recip in ccRecips
                        set recipAddr to ""
                        try
                            set recipAddr to address of recip
                        end try
                        if recipAddr is "" then
                            try
                                set recipAddr to name of recip
                            end try
                        end if
                        if recipAddr is not "" then
                            if msgCc is "" then
                                set msgCc to recipAddr
                            else
                                set msgCc to msgCc & ", " & recipAddr
                            end if
                        end if
                    end repeat
                end try

                set outText to outText & "<<<EMAIL_START>>>" & return
                set outText to outText & "ID:" & msgId & return
                set outText to outText & "INDEX:" & i & return
                set outText to outText & "DATE:" & msgDateStr & return
                set outText to outText & "FROM:" & msgSender & return
                set outText to outText & "TO:" & msgTo & return
                set outText to outText & "CC:" & msgCc & return
                set outText to outText & "SUBJECT:" & msgSubject & return
                set outText to outText & "READ:" & (msgIsRead as string) & return
                set outText to outText & "BODY:" & msgBody & return
                set outText to outText & "<<<EMAIL_END>>>" & return
            end if
        end if
    end repeat
    return outText
end tell
end timeout
"""


def check_outlook_classic_mode() -> tuple[bool, str]:
    """
    Verify Outlook is running in classic/legacy AppleScript mode.

    New Outlook can answer `count of mail folders` but cannot access message
    properties via AppleScript — trying to read `subject of message 1 of inbox`
    will either error out or return an empty/missing value.
    """
    probe = """
with timeout of 8 seconds
tell application "Microsoft Outlook"
    try
        set targetFolder to mail folder "Inbox"
        set msgCount to count of messages of targetFolder
        if msgCount > 0 then
            set testSubject to subject of message 1 of targetFolder
            if testSubject is missing value then
                return "new_outlook"
            end if
            return "classic:" & (msgCount as string)
        else
            return "classic:empty"
        end if
    on error errMsg
        return "error:" & errMsg
    end try
end tell
end timeout
"""
    try:
        result = subprocess.run(
            ["osascript", "-e", probe],
            capture_output=True, text=True, timeout=12,
        )
        stdout = result.stdout.strip()
        stderr = result.stderr.strip()

        if result.returncode != 0:
            err_lower = stderr.lower()
            if "not allowed" in err_lower or "not permitted" in err_lower:
                return False, (
                    "Outlook Automation permission is not granted. "
                    "Go to System Settings → Privacy & Security → Automation and allow Terminal to control Outlook."
                )
            return False, (
                f"Outlook did not respond correctly ({stderr or 'unknown error'}). "
                "Make sure Outlook is open in Classic (Legacy) mode — go to Help → Revert to Legacy Outlook."
            )

        if stdout.startswith("classic:"):
            detail = stdout[len("classic:"):]
            count_note = f" ({detail} messages in Inbox)" if detail != "empty" else " (Inbox is empty)"
            return True, f"Outlook Classic mode confirmed{count_note}."

        if stdout == "new_outlook" or stdout.startswith("error:"):
            detail = stdout[len("error:"):] if stdout.startswith("error:") else ""
            hint = f" ({detail})" if detail else ""
            return False, (
                f"Outlook is running in New mode{hint} — AppleScript cannot access messages. "
                "Go to Help → Revert to Legacy Outlook, then click Re-check."
            )

        # Unexpected response
        return False, (
            f"Unexpected Outlook response: '{stdout}'. "
            "Outlook may be in New mode. Go to Help → Revert to Legacy Outlook."
        )

    except subprocess.TimeoutExpired:
        return False, (
            "Outlook did not respond within 8 seconds. "
            "It may be loading or in New mode. Try again once Outlook is fully open."
        )
    except FileNotFoundError:
        return False, "osascript not found — this tool requires macOS."


def fetch_emails_from_outlook(
    folder: str,
    start: datetime,
    end: datetime,
    timeout_sec: int,
    include_body: bool,
    unread_only: bool = False,
    fetch_tail: int = 500,
) -> list[dict]:
    """Run AppleScript to pull emails from Outlook within the date window."""
    script = APPLESCRIPT_TEMPLATE.format(
        folder=folder,
        start_year=start.year,
        start_month=start.month,
        start_day=start.day,
        end_year=end.year,
        end_month=end.month,
        end_day=end.day,
        timeout_sec=timeout_sec,
        fetch_tail=fetch_tail,
        unread_filter="true" if unread_only else "false",
        body_expr=("set msgBody to plain text content of msg" if include_body else "set msgBody to \"\""),
    )

    unread_note = " (unread only)" if unread_only else ""
    print(f"[>] Querying Outlook '{folder}' from {start.date()} to {end.date()}{unread_note}"
          f" (scanning last {fetch_tail} messages)…")

    try:
        result = subprocess.run(
            ["osascript", "-e", script],
            capture_output=True,
            text=True,
            timeout=timeout_sec,
        )
    except subprocess.TimeoutExpired:
        print("[!] AppleScript timed out. Outlook may be unresponsive or the folder is very large.")
        sys.exit(1)
    except FileNotFoundError:
        print("[!] osascript not found. This script requires macOS.")
        sys.exit(1)

    if result.returncode != 0:
        print(f"[!] AppleScript error:\n{result.stderr}")
        print(
            "\nTroubleshooting tips:\n"
            "  1. Make sure Outlook is open.\n"
            "  2. Check that the folder name in config.json matches exactly (case-sensitive).\n"
            "  3. macOS may ask for Automation permission — allow it in System Settings > Privacy.\n"
        )
        sys.exit(1)

    return parse_applescript_output(result.stdout)


def fetch_emails_from_agent(
    folder: str,
    start: datetime,
    end: datetime,
    cfg: dict,
    unread_only: bool = False,
) -> list[dict]:
    """Fetch emails by calling the remote Mac Agent over HTTP.

    Used when running the server on Ubuntu. The Mac runs mac_agent.py which
    exposes this endpoint. cfg must contain mac_agent_url and optionally
    mac_agent_token.
    """
    if requests is None:
        raise RuntimeError("'requests' library required for remote agent mode. Run: pip install requests")

    url = cfg["mac_agent_url"].rstrip("/") + "/fetch-emails"
    token = cfg.get("mac_agent_token", "")
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"

    payload = {
        "folder":       folder,
        "start":        start.strftime("%Y-%m-%d"),
        "end":          end.strftime("%Y-%m-%d"),
        "include_body": bool(cfg.get("outlook_include_body", True)),
        "unread_only":  unread_only,
        "fetch_tail":   int(cfg.get("outlook_fetch_tail", 500)),
        "timeout_sec":  int(cfg.get("outlook_query_timeout_sec", 600)),
    }

    try:
        resp = requests.post(url, json=payload, headers=headers,
                             timeout=int(cfg.get("outlook_query_timeout_sec", 600)) + 30)
        resp.raise_for_status()
    except requests.exceptions.ConnectionError:
        raise RuntimeError(f"Cannot connect to Mac Agent at {url}. Is mac_agent.py running?")
    except requests.exceptions.Timeout:
        raise RuntimeError(f"Mac Agent timed out fetching emails.")

    data = resp.json()
    if "error" in data:
        raise RuntimeError(f"Mac Agent error: {data['error']}")
    return data.get("emails", [])


def open_email_in_outlook(msg_id: str, folder: str = "Inbox", msg_index: int = 0) -> bool:
    """Open a specific message in Outlook by ID.

    Uses msg_index as a fast starting hint, then verifies the ID matches.
    If new mail arrived since screening the indices will have shifted — in that
    case we scan a small window around the hint to find the right message.
    This avoids any `whose` filter (which causes a full 176k-message scan).
    """
    scan_window = 100  # scan ±N around the hint if the hint misses
    script = f"""
with timeout of 15 seconds
tell application "Microsoft Outlook"
    activate
    set matchingFolders to (mail folders whose name is "{folder}")
    set targetFolder to item 1 of matchingFolders
    set maxCount to -1
    repeat with f in matchingFolders
        set c to count messages of f
        if c > maxCount then
            set maxCount to c
            set targetFolder to f
        end if
    end repeat

    set totalCount to count messages of targetFolder
    set targetId to "{msg_id}"
    set hintIdx to {msg_index if msg_index > 0 else 1}

    -- First try the exact hint index
    if hintIdx >= 1 and hintIdx <= totalCount then
        set msg to message hintIdx of targetFolder
        if (id of msg as string) is targetId then
            open msg
            return "ok:hint"
        end if
    end if

    -- Hint missed (new mail shifted indices). Scan a window around the hint.
    set winStart to hintIdx - {scan_window}
    if winStart < 1 then set winStart to 1
    set winEnd to hintIdx + {scan_window}
    if winEnd > totalCount then set winEnd to totalCount

    repeat with i from winStart to winEnd
        if i is not hintIdx then  -- already checked
            set msg to message i of targetFolder
            if (id of msg as string) is targetId then
                open msg
                return "ok:scan"
            end if
        end if
    end repeat

    return "not found"
end tell
end timeout
"""
    try:
        result = subprocess.run(
            ["osascript", "-e", script],
            capture_output=True, text=True, timeout=20,
        )
        return "ok" in result.stdout
    except Exception:
        return False


def mark_email_as_read(msg_id: str, folder: str = "Inbox", msg_index: int = 0) -> bool:
    """Mark a specific message as read in Outlook using the same index-hint strategy."""
    scan_window = 100
    script = f"""
with timeout of 15 seconds
tell application "Microsoft Outlook"
    set matchingFolders to (mail folders whose name is "{folder}")
    set targetFolder to item 1 of matchingFolders
    set maxCount to -1
    repeat with f in matchingFolders
        set c to count messages of f
        if c > maxCount then
            set maxCount to c
            set targetFolder to f
        end if
    end repeat

    set totalCount to count messages of targetFolder
    set targetId to "{msg_id}"
    set hintIdx to {msg_index if msg_index > 0 else 1}

    if hintIdx >= 1 and hintIdx <= totalCount then
        set msg to message hintIdx of targetFolder
        if (id of msg as string) is targetId then
            set is read of msg to true
            return "ok:hint"
        end if
    end if

    set winStart to hintIdx - {scan_window}
    if winStart < 1 then set winStart to 1
    set winEnd to hintIdx + {scan_window}
    if winEnd > totalCount then set winEnd to totalCount

    repeat with i from winStart to winEnd
        if i is not hintIdx then
            set msg to message i of targetFolder
            if (id of msg as string) is targetId then
                set is read of msg to true
                return "ok:scan"
            end if
        end if
    end repeat

    return "not found"
end tell
end timeout
"""
    try:
        result = subprocess.run(
            ["osascript", "-e", script],
            capture_output=True, text=True, timeout=20,
        )
        return "ok" in result.stdout
    except Exception:
        return False


def parse_applescript_output(raw: str) -> list[dict]:
    """Parse the structured AppleScript output into a list of email dicts."""
    emails = []
    blocks = re.split(r"<<<EMAIL_START>>>", raw)
    for block in blocks:
        block = block.strip()
        if "<<<EMAIL_END>>>" not in block:
            continue
        block = block.replace("<<<EMAIL_END>>>", "").strip()

        email = {}
        # Extract ID (numeric, on first match)
        m = re.search(r"^ID:(.+)$", block, re.MULTILINE)
        if m:
            email["id"] = m.group(1).strip()

        m = re.search(r"^INDEX:(.+)$", block, re.MULTILINE)
        if m:
            try:
                email["index"] = int(m.group(1).strip())
            except ValueError:
                pass

        m = re.search(r"^DATE:(.+)$", block, re.MULTILINE)
        if m:
            email["date"] = m.group(1).strip()

        m = re.search(r"^FROM:(.+)$", block, re.MULTILINE)
        if m:
            email["from"] = m.group(1).strip()

        m = re.search(r"^TO:(.*)$", block, re.MULTILINE)
        if m:
            email["to"] = m.group(1).strip()

        m = re.search(r"^CC:(.*)$", block, re.MULTILINE)
        if m:
            email["cc"] = m.group(1).strip()

        m = re.search(r"^SUBJECT:(.+)$", block, re.MULTILINE)
        if m:
            email["subject"] = m.group(1).strip()

        m = re.search(r"^READ:(.+)$", block, re.MULTILINE)
        if m:
            email["read"] = m.group(1).strip().lower() == "true"


        m = re.search(r"^BODY:(.*)$", block, re.MULTILINE | re.DOTALL)
        if m:
            email["body"] = m.group(1).strip()

        if email.get("subject") or email.get("body"):
            emails.append(email)

    return emails


# ---------------------------------------------------------------------------
# Text cleaning
# ---------------------------------------------------------------------------

QUOTED_PATTERNS = [
    re.compile(r"^>.*$", re.MULTILINE),
    re.compile(r"(?:^|\n)-+\s*Original Message\s*-+.*", re.DOTALL | re.IGNORECASE),
    re.compile(r"(?:^|\n)On .+wrote:.*", re.DOTALL | re.IGNORECASE),
    re.compile(r"(?:^|\n)From:.*Sent:.*To:.*Subject:.*", re.DOTALL | re.IGNORECASE),
    re.compile(r"\[cid:[^\]]+\]"),  # inline image refs
]

SIGNATURE_PATTERNS = [
    re.compile(r"\n--\s*\n.*", re.DOTALL),
    re.compile(r"\nBest regards.*", re.DOTALL | re.IGNORECASE),
    re.compile(r"\nKind regards.*", re.DOTALL | re.IGNORECASE),
    re.compile(r"\nRegards.*", re.DOTALL | re.IGNORECASE),
    re.compile(r"\nThanks.*", re.DOTALL | re.IGNORECASE),
    re.compile(r"\nCheers.*", re.DOTALL | re.IGNORECASE),
]


def clean_body(body: str, max_chars: int) -> str:
    for pat in QUOTED_PATTERNS:
        body = pat.sub("", body)
    for pat in SIGNATURE_PATTERNS:
        body = pat.sub("", body)
    body = re.sub(r"\n{3,}", "\n\n", body)
    body = body.strip()
    return body[:max_chars] if len(body) > max_chars else body


# ---------------------------------------------------------------------------
# LLM screening via Ollama
# ---------------------------------------------------------------------------

SYSTEM_PROMPT = textwrap.dedent("""
You are an expert email analyst. Your job is to determine whether an email requires
a direct action from the recipient, and if so, extract those actions precisely.
You also flag emails that are worth reading even if no action is needed.

Respond ONLY with valid JSON. No prose before or after the JSON.
""").strip()


def _build_system_prompt() -> str:
    """Build the system prompt, appending enabled guidelines as extra rules."""
    try:
        import db
        guidelines = db.get_enabled_guidelines()
    except Exception:
        guidelines = []

    if not guidelines:
        return SYSTEM_PROMPT

    lines = ["\nAdditional guidelines to apply when classifying emails:"]
    for g in guidelines:
        lines.append(f"- {g}")
    return SYSTEM_PROMPT + "\n" + "\n".join(lines)

USER_PROMPT_TEMPLATE = textwrap.dedent("""
The recipient's name is {user_name}. Analyse the following email and return a JSON
object with EXACTLY this structure:

{{
  "summary": "1-2 sentences describing what this email is about and what it's asking — written as a neutral description, not a classification",
  "needs_action": true or false,
  "urgency": "low" | "medium" | "high",
  "confidence": 0.0 to 1.0,
  "reason": "one sentence explaining specifically WHY {user_name} does or does not need to act",
  "actions": [
    {{
      "description": "concrete description of what {user_name} needs to do",
      "due_date": "YYYY-MM-DD or null if not specified",
      "assignee": "who must do it — use '{user_name}' if the recipient, otherwise the actual name",
      "confidence": 0.0 to 1.0
    }}
  ],
  "interesting": true or false,
  "interest_reason": "one sentence why this is worth reading, or null if not interesting"
}}

Rules for needs_action:
1. Set needs_action=true ONLY if {user_name} personally needs to do something —
   reply, approve, review, attend, decide, or take any concrete step.
2. If ALL actions in the email are assigned to OTHER people (not {user_name}),
   set needs_action=false. Classify as interesting=true if {user_name} should be
   aware (e.g. progress update, dependency, escalation involving their team).
3. Meeting notes / meeting summaries: set needs_action=false UNLESS the notes
   explicitly name {user_name} with a direct action request (e.g. "{user_name} to
   review…", "Action: {user_name}…"). General meeting follow-ups without a named
   action for {user_name} belong in interesting, not action required.
4. Escalations, outages, or issues that directly affect {user_name}'s
   responsibilities should be needs_action=true even if not explicitly addressed
   to them.
5. FYI emails, cc'd threads where others own the work, newsletters, and automated
   notifications should be needs_action=false.
6. Only include actions where assignee is {user_name}. Do NOT list actions assigned
   to other people — those are irrelevant to {user_name}'s action list.

Set interesting=true if the email contains useful information, announcements,
updates, or context that {user_name} would likely want to know about — even if
no reply or action is needed. Set it to false for routine notifications, automated
messages, calendar noise, or newsletters.

--- EMAIL ---
From: {sender}
Date: {date}
Subject: {subject}

{body}
--- END EMAIL ---
""").strip()


def screen_with_llm(email: dict, cfg: dict) -> dict | None:
    """Call Ollama and return the parsed JSON result, or None on failure."""
    if requests is None:
        raise RuntimeError("'requests' library not installed. Run: pip install requests")

    prompt = USER_PROMPT_TEMPLATE.format(
        user_name=cfg.get("user_name", "the recipient"),
        sender=email.get("from", "unknown"),
        date=email.get("date", "unknown"),
        subject=email.get("subject", "(no subject)"),
        body=email.get("body_clean", ""),
    )

    payload = {
        "model": cfg["ollama_model"],
        "messages": [
            {"role": "system", "content": _build_system_prompt()},
            {"role": "user", "content": prompt},
        ],
        "stream": False,
        "format": "json",
    }

    try:
        resp = requests.post(
            f"{cfg['ollama_url']}/api/chat",
            json=payload,
            timeout=60,
        )
        resp.raise_for_status()
    except requests.exceptions.ConnectionError:
        raise RuntimeError(
            f"Cannot connect to Ollama at {cfg['ollama_url']}. "
            "Make sure Ollama is running: ollama serve"
        )
    except requests.exceptions.HTTPError as e:
        print(f"[!] Ollama HTTP error: {e}")
        return None

    try:
        content = resp.json()["message"]["content"]
        # Strip any accidental markdown fencing
        content = re.sub(r"^```(?:json)?\s*|\s*```$", "", content.strip())
        return json.loads(content)
    except (KeyError, json.JSONDecodeError) as e:
        print(f"[!] Could not parse LLM response for '{email.get('subject')}': {e}")
        return None


# ---------------------------------------------------------------------------
# GitHub Copilot screener
# ---------------------------------------------------------------------------

GITHUB_COPILOT_FALLBACK_URL = "https://api.business.githubcopilot.com/chat/completions"


def _resolve_github_token(cfg: dict) -> str | None:
    """Return a GitHub token from config, env var, or the gh CLI."""
    token = cfg.get("github_token", "").strip()
    if token:
        return token
    token = os.environ.get("GITHUB_TOKEN", "").strip()
    if token:
        return token
    try:
        result = subprocess.run(
            ["gh", "auth", "token"],
            capture_output=True,
            text=True,
            timeout=10,
        )
        if result.returncode == 0:
            return result.stdout.strip()
    except (FileNotFoundError, subprocess.TimeoutExpired):
        pass
    return None


def _get_copilot_endpoint(token: str) -> str:
    """Return the correct Copilot chat completions URL for this account."""
    if requests is None:
        return GITHUB_COPILOT_FALLBACK_URL
    try:
        resp = requests.get(
            "https://api.github.com/copilot_internal/user",
            headers={"Authorization": f"token {token}"},
            timeout=10,
        )
        if resp.status_code == 200:
            api_base = resp.json().get("endpoints", {}).get("api", "").rstrip("/")
            if api_base:
                return f"{api_base}/chat/completions"
    except Exception:
        pass
    return GITHUB_COPILOT_FALLBACK_URL


def screen_with_github(email: dict, cfg: dict) -> dict | None:
    """Call the GitHub Copilot Chat API and return parsed JSON result."""
    if requests is None:
        raise RuntimeError("'requests' library not installed. Run: pip install requests")

    token = _resolve_github_token(cfg)
    if not token:
        raise RuntimeError(
            "No GitHub token found. "
            "Run: gh auth login && gh auth refresh -s copilot"
        )

    prompt = USER_PROMPT_TEMPLATE.format(
        user_name=cfg.get("user_name", "the recipient"),
        sender=email.get("from", "unknown"),
        date=email.get("date", "unknown"),
        subject=email.get("subject", "(no subject)"),
        body=email.get("body_clean", ""),
    )

    payload = {
        "model": cfg.get("github_model", "gpt-4o-mini"),
        "messages": [
            {"role": "system", "content": _build_system_prompt()},
            {"role": "user", "content": prompt},
        ],
        "response_format": {"type": "json_object"},
    }

    # Resolve endpoint once per process (cached on cfg to avoid repeated lookups)
    if "_github_endpoint" not in cfg:
        cfg["_github_endpoint"] = _get_copilot_endpoint(token)

    try:
        resp = requests.post(
            cfg["_github_endpoint"],
            json=payload,
            headers={
                "Authorization": f"Bearer {token}",
                "Content-Type": "application/json",
                "Copilot-Integration-Id": "vscode-chat",
            },
            timeout=60,
        )
        resp.raise_for_status()
    except requests.exceptions.ConnectionError:
        raise RuntimeError("Cannot connect to GitHub Copilot API. Check your internet connection.")
    except requests.exceptions.HTTPError as e:
        if resp.status_code == 401:
            raise RuntimeError(
                "GitHub token rejected (401). Make sure your token has Copilot access. "
                "Try: gh auth refresh -s copilot"
            )
        if resp.status_code == 429:
            print("[!] GitHub Models rate limit hit. Try again shortly.")
            return None
        print(f"[!] GitHub Models HTTP error: {e}")
        return None

    try:
        content = resp.json()["choices"][0]["message"]["content"]
        content = re.sub(r"^```(?:json)?\s*|\s*```$", "", content.strip())
        return json.loads(content)
    except (KeyError, IndexError, json.JSONDecodeError) as e:
        print(f"[!] Could not parse GitHub Models response for '{email.get('subject')}': {e}")
        return None


# ---------------------------------------------------------------------------
# Rule-based fallback screener (no LLM)
# ---------------------------------------------------------------------------

ACTION_KEYWORDS = [
    r"\bplease\b.{0,60}\b(review|confirm|approve|send|complete|update|respond|reply|check|action)\b",
    r"\b(action required|action needed|urgent|asap|by eod|by cob|deadline|due date|response needed)\b",
    r"\b(can you|could you|would you|will you|do you)\b.{0,80}\b(send|check|review|confirm|let me know|advise)\b",
    r"\bplease\b.{0,40}\b(let me know|advise|confirm|respond)\b",
    r"\b(follow[- ]?up|awaiting your|waiting for your)\b",
    r"\bfor your (review|approval|action|input)\b",
]

URGENCY_HIGH = [r"\bURGENT\b", r"\bASAP\b", r"\bCRITICAL\b", r"\bimmediately\b", r"\bby (today|EOD|COB)\b"]
URGENCY_MEDIUM = [r"\bby (tomorrow|end of week|Friday)\b", r"\bdeadline\b", r"\bdue (by|on)\b"]


def screen_rule_based(email: dict) -> dict:
    text = f"{email.get('subject', '')} {email.get('body_clean', '')}".lower()

    matched_actions = [p for p in ACTION_KEYWORDS if re.search(p, text, re.IGNORECASE)]
    needs_action = len(matched_actions) > 0
    confidence = min(0.5 + 0.1 * len(matched_actions), 0.9) if needs_action else 0.1

    urgency = "low"
    if any(re.search(p, text, re.IGNORECASE) for p in URGENCY_HIGH):
        urgency = "high"
    elif any(re.search(p, text, re.IGNORECASE) for p in URGENCY_MEDIUM):
        urgency = "medium"

    # Build a basic summary from subject + body preview
    preview = email.get("body_clean", "")[:120].strip()
    subject = email.get("subject", "")
    summary = f"{preview}…" if preview else subject

    return {
        "summary": summary,
        "needs_action": needs_action,
        "urgency": urgency,
        "confidence": round(confidence, 2),
        "reason": f"⚠ Rule-based fallback (LLM unavailable): matched {len(matched_actions)} action keyword(s)." if needs_action else "⚠ Rule-based fallback (LLM unavailable): no action keywords found.",
        "actions": [],
        "interesting": False,
        "interest_reason": None,
    }



# ---------------------------------------------------------------------------
# Guideline generation (AI distills a feedback correction into a reusable rule)
# ---------------------------------------------------------------------------

def generate_guideline_from_feedback(
    subject: str,
    from_: str,
    original: str,
    correct: str,
    note: str,
    cfg: dict,
) -> str:
    """Call the LLM to produce a single generalized guideline from one feedback correction.

    Returns the guideline text (a short sentence or two).
    Raises RuntimeError if the LLM cannot be reached.
    """
    if requests is None:
        raise RuntimeError("'requests' library not installed.")

    prompt = (
        "A user corrected an email classification:\n"
        f"- Subject: {subject}\n"
        f"- From: {from_}\n"
        f"- Original classification: {original}\n"
        f"- Correct classification: {correct}\n"
        f"- User note: {note or 'none'}\n\n"
        "Write ONE concise guideline rule (1–2 sentences) that generalises this correction "
        "so it can be applied to similar future emails. "
        "The rule must be broad enough to cover similar patterns, not just this exact email. "
        "Return ONLY the guideline text — no prefix, no JSON, no bullet point."
    )

    provider = cfg.get("llm_provider", "github")

    if provider == "github":
        token = _resolve_github_token(cfg)
        if not token:
            raise RuntimeError("No GitHub token found.")
        if "_github_endpoint" not in cfg:
            cfg["_github_endpoint"] = _get_copilot_endpoint(token)
        resp = requests.post(
            cfg["_github_endpoint"],
            json={
                "model": cfg.get("github_model", "gpt-4o-mini"),
                "messages": [
                    {"role": "system", "content": "You are a helpful assistant. Respond concisely."},
                    {"role": "user", "content": prompt},
                ],
            },
            headers={
                "Authorization": f"Bearer {token}",
                "Content-Type": "application/json",
                "Copilot-Integration-Id": "vscode-chat",
            },
            timeout=30,
        )
        resp.raise_for_status()
        return resp.json()["choices"][0]["message"]["content"].strip()

    elif provider == "ollama":
        ollama_url = cfg.get("ollama_url", "http://localhost:11434").rstrip("/")
        model = cfg.get("ollama_model", "llama3")
        resp = requests.post(
            f"{ollama_url}/api/chat",
            json={
                "model": model,
                "messages": [
                    {"role": "system", "content": "You are a helpful assistant. Respond concisely."},
                    {"role": "user", "content": prompt},
                ],
                "stream": False,
            },
            timeout=60,
        )
        resp.raise_for_status()
        return resp.json()["message"]["content"].strip()

    else:
        raise RuntimeError(f"Unsupported provider for guideline generation: {provider}")


# ---------------------------------------------------------------------------
# Report generation
# ---------------------------------------------------------------------------

def generate_report(results: list[dict], output_dir: Path, start: datetime, end: datetime):
    output_dir.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    md_path = output_dir / f"report_{timestamp}.md"
    csv_path = output_dir / f"report_{timestamp}.csv"

    urgency_order = {"high": 0, "medium": 1, "low": 2}

    action_items = [r for r in results if r["screening"].get("needs_action")]
    action_items.sort(key=lambda x: (
        urgency_order.get(x["screening"].get("urgency", "low"), 2),
        -x["screening"].get("confidence", 0),
    ))

    interesting_items = [
        r for r in results
        if not r["screening"].get("needs_action") and r["screening"].get("interesting")
    ]
    interesting_items.sort(key=lambda x: -x["screening"].get("confidence", 0))

    noise_items = [
        r for r in results
        if not r["screening"].get("needs_action") and not r["screening"].get("interesting")
    ]

    # --- Markdown report ---
    lines = [
        "# Email Screening Report",
        f"**Date range:** {start.date()} → {end.date()}  ",
        f"**Generated:** {datetime.now().strftime('%Y-%m-%d %H:%M')}  ",
        f"**Total screened:** {len(results)}  ",
        f"**Action required:** {len(action_items)}  ",
        f"**Worth reading:** {len(interesting_items)}  ",
        "",
        "---",
        "",
        "## 🔔 Action Required",
        "",
    ]

    if action_items:
        for r in action_items:
            s = r["screening"]
            urgency_badge = {"high": "🔴 HIGH", "medium": "🟡 MEDIUM", "low": "🟢 LOW"}.get(
                s.get("urgency", "low"), "⚪ -"
            )
            lines += [
                f"### {r['email'].get('subject', '(no subject)')}",
                f"- **From:** {r['email'].get('from', '?')}",
                f"- **Date:** {r['email'].get('date', '?')}",
                f"- **Urgency:** {urgency_badge}",
                f"- **Confidence:** {s.get('confidence', 0):.0%}",
                f"- **Why:** {s.get('reason', '')}",
            ]
            actions = s.get("actions", [])
            if actions:
                lines.append("- **Actions:**")
                for a in actions:
                    due = f" _(due {a.get('due_date')})_" if a.get("due_date") else ""
                    lines.append(f"  - {a.get('description', '')}{due}")
            lines.append("")
    else:
        lines.append("_No emails requiring action found._\n")

    lines += ["---", "", "## 💡 Worth Reading", ""]

    if interesting_items:
        for r in interesting_items:
            s = r["screening"]
            lines += [
                f"### {r['email'].get('subject', '(no subject)')}",
                f"- **From:** {r['email'].get('from', '?')}",
                f"- **Date:** {r['email'].get('date', '?')}",
                f"- **Why interesting:** {s.get('interest_reason', '')}",
                "",
            ]
    else:
        lines.append("_Nothing flagged as particularly interesting._\n")

    lines += ["---", "", "## ✅ Noise / No Action Needed", ""]
    for r in noise_items:
        lines.append(
            f"- **{r['email'].get('subject', '(no subject)')}** "
            f"— {r['email'].get('from', '?')} ({r['email'].get('date', '?')})"
        )
    lines.append("")

    md_path.write_text("\n".join(lines))
    print(f"[✓] Markdown report: {md_path}")

    # --- CSV report ---
    with open(csv_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow([
            "subject", "from", "date", "needs_action", "urgency",
            "confidence", "reason", "actions_summary", "interesting", "interest_reason",
        ])
        for r in results:
            s = r["screening"]
            actions_summary = "; ".join(a.get("description", "") for a in s.get("actions", []))
            writer.writerow([
                r["email"].get("subject", ""),
                r["email"].get("from", ""),
                r["email"].get("date", ""),
                s.get("needs_action", False),
                s.get("urgency", ""),
                s.get("confidence", ""),
                s.get("reason", ""),
                actions_summary,
                s.get("interesting", False),
                s.get("interest_reason", ""),
            ])
    print(f"[✓] CSV report:      {csv_path}")


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def parse_args():
    parser = argparse.ArgumentParser(
        description="Screen Outlook Inbox emails for action items using an LLM."
    )
    parser.add_argument("--start", help="Start date YYYY-MM-DD (inclusive)", default=None)
    parser.add_argument("--end", help="End date YYYY-MM-DD (inclusive, defaults to today)", default=None)
    parser.add_argument("--no-llm", action="store_true", help="Use rule-based screening (no LLM required)")
    parser.add_argument(
        "--provider",
        choices=["github", "ollama"],
        default=None,
        help="LLM provider: 'github' (GitHub Models/Copilot) or 'ollama' (local). Overrides config.",
    )
    parser.add_argument("--model", help="Override model name (applies to whichever provider is active)", default=None)
    parser.add_argument("--unread", action="store_true", help="Only screen unread emails")
    parser.add_argument("--folder", help="Outlook folder name (default: Inbox)", default=None)
    return parser.parse_args()


def main():
    args = parse_args()
    cfg = load_config()

    if args.provider:
        cfg["llm_provider"] = args.provider
    if args.model:
        provider = cfg.get("llm_provider", "github")
        if provider == "github":
            cfg["github_model"] = args.model
        else:
            cfg["ollama_model"] = args.model
    if args.folder:
        cfg["outlook_folder"] = args.folder

    provider = cfg.get("llm_provider", "github")
    if not args.no_llm:
        print(f"[i] LLM provider: {provider} "
              f"(model: {cfg.get('github_model') if provider == 'github' else cfg.get('ollama_model')})")

    today = datetime.today().replace(hour=0, minute=0, second=0, microsecond=0)

    if args.end:
        end_date = datetime.strptime(args.end, "%Y-%m-%d")
    else:
        end_date = today

    if args.start:
        start_date = datetime.strptime(args.start, "%Y-%m-%d")
    else:
        start_date = today - timedelta(days=cfg["date_range_days"] - 1)

    if start_date > end_date:
        print("[!] --start must be before --end")
        sys.exit(1)

    # 1. Fetch emails via AppleScript (single call for full date range)
    timeout_sec = int(cfg.get("outlook_query_timeout_sec", 600))
    include_body = bool(cfg.get("outlook_include_body", False))

    emails = fetch_emails_from_outlook(
        cfg["outlook_folder"],
        start_date,
        end_date,
        timeout_sec,
        include_body,
        unread_only=args.unread,
    )

    if not emails:
        label = "unread " if args.unread else ""
        print(f"[i] No {label}emails found in that date range.")
        sys.exit(0)

    unread_count = sum(1 for e in emails if not e.get("read", True))
    total_label = f"{len(emails)} unread" if args.unread else f"{len(emails)} (unread: {unread_count})"
    print(f"[i] Found {total_label} email(s). Screening ...")

    # 2. Screen each email
    results = []
    for i, email in enumerate(emails, 1):
        email["body_clean"] = clean_body(email.get("body", ""), cfg["max_body_chars"])
        subject = email.get("subject", "(no subject)")
        print(f"    [{i}/{len(emails)}] {subject[:70]}")

        if args.no_llm:
            screening = screen_rule_based(email)
        elif provider == "github":
            screening = screen_with_github(email, cfg)
            if screening is None:
                screening = screen_rule_based(email)
        else:
            screening = screen_with_llm(email, cfg)
            if screening is None:
                screening = screen_rule_based(email)

        results.append({"email": email, "screening": screening})

    # 3. Generate report
    output_dir_cfg = cfg.get("output_dir", "").strip()
    if output_dir_cfg:
        output_dir = Path(output_dir_cfg).expanduser()
    else:
        output_dir = Path(__file__).parent / "reports"
    generate_report(results, output_dir, start_date, end_date)

    # 4. Summary to console
    action_count = sum(
        1 for r in results
        if r["screening"].get("needs_action")
        and r["screening"].get("confidence", 0) >= cfg["confidence_threshold"]
    )
    print(f"\n[✓] Done. {action_count} email(s) need your attention (confidence ≥ {cfg['confidence_threshold']:.0%}).")


if __name__ == "__main__":
    main()
