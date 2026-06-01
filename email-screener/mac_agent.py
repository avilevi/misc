#!/usr/bin/env python3
"""
Mac Agent — runs on the Mac and exposes Outlook data over HTTP.

The Ubuntu server calls this agent to fetch emails, open messages in Outlook,
and mark messages as read. This must run on the Mac because AppleScript requires
access to the GUI session.

Start manually:
    python3 mac_agent.py

Auto-start on login: install the LaunchAgent plist from deploy/mac_launchagent.plist

Config (reads from config.json in the same directory):
    mac_agent_token   — shared secret for Authorization: Bearer <token>
    mac_agent_port    — port to listen on (default 5002)
"""

from __future__ import annotations

import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import screen_emails as se

try:
    from flask import Flask, jsonify, request
except ImportError:
    print("[!] Flask not installed. Run: pip install flask")
    sys.exit(1)

app = Flask(__name__)

CONFIG_FILE = Path(__file__).parent / "config.json"


def _load_config() -> dict:
    if CONFIG_FILE.exists():
        with open(CONFIG_FILE) as f:
            return json.load(f)
    return {}


def _check_auth() -> bool:
    cfg = _load_config()
    token = cfg.get("mac_agent_token", "")
    if not token:
        return True  # no token configured → open access (VPN-only deployment)
    auth = request.headers.get("Authorization", "")
    return auth == f"Bearer {token}"


def _auth_error():
    return jsonify({"error": "Unauthorized"}), 401


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------

@app.route("/health")
def health():
    return jsonify({"status": "ok", "host": "mac-agent"})


@app.route("/check-outlook")
def check_outlook():
    if not _check_auth():
        return _auth_error()
    is_classic, msg = se.check_outlook_classic_mode()
    return jsonify({"ok": is_classic, "msg": msg})


@app.route("/fetch-emails", methods=["POST"])
def fetch_emails():
    """Fetch emails from Outlook and return as JSON."""
    if not _check_auth():
        return _auth_error()

    data = request.get_json() or {}
    cfg = _load_config()

    # Allow request to override config fields
    folder = data.get("folder") or cfg.get("outlook_folder", "Inbox")
    timeout_sec = int(data.get("timeout_sec") or cfg.get("outlook_query_timeout_sec", 600))
    include_body = bool(data.get("include_body", cfg.get("outlook_include_body", True)))
    unread_only = bool(data.get("unread_only", False))
    fetch_tail = int(data.get("fetch_tail") or cfg.get("outlook_fetch_tail", 500))

    from datetime import datetime, timedelta
    today = datetime.today().replace(hour=0, minute=0, second=0, microsecond=0)
    start_str = data.get("start")
    end_str = data.get("end")
    start_date = datetime.strptime(start_str, "%Y-%m-%d") if start_str else today - timedelta(days=6)
    end_date = datetime.strptime(end_str, "%Y-%m-%d") if end_str else today

    # Pre-flight Outlook check
    is_classic, mode_msg = se.check_outlook_classic_mode()
    if not is_classic:
        return jsonify({"error": mode_msg, "classic_mode": False}), 503

    emails = se.fetch_emails_from_outlook(
        folder, start_date, end_date,
        timeout_sec=timeout_sec,
        include_body=include_body,
        unread_only=unread_only,
        fetch_tail=fetch_tail,
    )

    return jsonify({"emails": emails, "count": len(emails)})


@app.route("/open-email/<msg_id>", methods=["POST"])
def open_email(msg_id: str):
    """Open a specific email in the Outlook client on this Mac."""
    if not _check_auth():
        return _auth_error()

    data = request.get_json() or {}
    folder = data.get("folder", "Inbox")
    idx = int(data.get("index", 0))
    success = se.open_email_in_outlook(msg_id, folder, msg_index=idx)
    return jsonify({"success": success})


@app.route("/mark-read/<msg_id>", methods=["POST"])
def mark_read(msg_id: str):
    """Mark an email as read in Outlook on this Mac."""
    if not _check_auth():
        return _auth_error()

    data = request.get_json() or {}
    folder = data.get("folder", "Inbox")
    idx = int(data.get("index", 0))
    success = se.mark_email_as_read(msg_id, folder, msg_index=idx)
    return jsonify({"success": success})


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    cfg = _load_config()
    port = int(cfg.get("mac_agent_port", 5002))
    token_set = bool(cfg.get("mac_agent_token"))
    print(f"[✓] Mac Agent running on port {port}")
    print(f"    Token auth: {'enabled' if token_set else 'DISABLED — set mac_agent_token in config.json'}")
    print(f"    Listening on 0.0.0.0:{port} (reachable from Ubuntu server via VPN)")
    app.run(host="0.0.0.0", port=port, debug=False, threaded=True)
