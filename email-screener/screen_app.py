#!/usr/bin/env python3
"""
Email Screener — Flask web server.

Runs on Ubuntu (or locally on Mac). When mac_agent_url is set in config.json,
fetches emails from the Mac Agent over HTTP. Otherwise runs AppleScript locally.

Start:
    python3 screen_app.py
"""

from __future__ import annotations

import json
import queue
import sys
import threading
import time
from datetime import datetime, timedelta
from pathlib import Path

from flask import Flask, Response, jsonify, render_template, request, stream_with_context

sys.path.insert(0, str(Path(__file__).parent))
import screen_emails as se
import db as _db

app = Flask(__name__)

_db.init_db()

_job_lock = threading.Lock()
_job_running = False
_last_results: list[dict] = []

# Event log + fan-out subscribers.
# _event_log holds every event emitted for the current job so that any
# client connecting mid-run (or after a reconnect) gets the full replay.
# All access is serialised by _subs_lock so subscribe + snapshot is atomic.
_event_log: list[dict] = []
_subscribers: list[queue.Queue] = []
_subs_lock = threading.Lock()


def _is_mac_client() -> bool:
    """Return True if the HTTP request is coming from the Mac itself.

    In remote mode (mac_agent_url set), only the configured mac_ip counts as Mac.
    In local mode (server running on the Mac), localhost also counts.
    """
    cfg = se.load_config()
    mac_ip = cfg.get("mac_ip", "").strip()
    remote = request.remote_addr or ""
    remote_mode = bool(cfg.get("mac_agent_url", "").strip())

    if remote_mode:
        # Server is on Ubuntu — only the Mac's VPN IP is a "Mac client"
        if not mac_ip:
            return False
        # Compare against the resolved IP or hostname directly
        if remote == mac_ip:
            return True
        # Also resolve the hostname in case mac_ip is a DNS name
        try:
            import socket
            resolved = socket.gethostbyname(mac_ip)
            return remote == resolved
        except Exception:
            return False
    else:
        # Server is running locally on the Mac
        return remote in ("127.0.0.1", "::1") or (mac_ip and remote == mac_ip)


def _call_mac_agent(path: str, method: str = "POST", json_body: dict | None = None):
    """Forward a request to the Mac Agent. Returns (response_dict, status_code)."""
    try:
        import requests as req
    except ImportError:
        return {"error": "requests library not available"}, 500

    cfg = se.load_config()
    url = cfg.get("mac_agent_url", "").rstrip("/") + path
    token = cfg.get("mac_agent_token", "")
    headers = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"

    try:
        if method == "POST":
            resp = req.post(url, json=json_body or {}, headers=headers, timeout=15)
        else:
            resp = req.get(url, headers=headers, timeout=15)
        return resp.json(), resp.status_code
    except Exception as e:
        err = str(e)
        if "Connection refused" in err or "Failed to establish" in err or "timed out" in err.lower():
            msg = "Mac is unreachable — make sure it is powered on and the Mac Agent is running (python3 mac_agent.py)"
        else:
            msg = f"Mac Agent error: {err}"
        return {"error": msg, "ok": False, "msg": msg}, 503


def _load_pins() -> dict:
    """Load pinned items from disk. Returns dict keyed by msg_id."""
    if PINS_FILE.exists():
        try:
            with open(PINS_FILE) as f:
                return json.load(f)
        except Exception:
            pass
    return {}


def _save_pins(pins: dict):
    with open(PINS_FILE, "w") as f:
        json.dump(pins, f, indent=2)


# ---------------------------------------------------------------------------
# Routes
# ---------------------------------------------------------------------------

@app.route("/")
def index():
    cfg = se.load_config()
    today = datetime.today().strftime("%Y-%m-%d")
    default_start = (datetime.today() - timedelta(days=cfg.get("date_range_days", 7) - 1)).strftime("%Y-%m-%d")
    return render_template("index.html", cfg=cfg, today=today, default_start=default_start)


@app.route("/run", methods=["POST"])
def run():
    global _job_running
    with _job_lock:
        if _job_running:
            return jsonify({"error": "A screening job is already in progress. Please wait for it to finish.", "running": True}), 409
        _job_running = True

    data = request.get_json()
    threading.Thread(target=_do_screening, args=(data,), daemon=True).start()
    return jsonify({"status": "started"})


@app.route("/status")
def status():
    with _job_lock:
        with _subs_lock:
            return jsonify({"running": _job_running, "has_log": bool(_event_log)})


@app.route("/stream")
def stream():
    """SSE stream with full replay.

    On connect we atomically snapshot the current event log AND add the
    client queue to the subscriber list. This means:
    - Events already emitted → delivered via log replay
    - Events emitted after subscribe → delivered via the queue
    No events are lost and no duplicates occur.
    """
    q: queue.Queue = queue.Queue()
    with _subs_lock:
        _subscribers.append(q)
        log_snapshot = list(_event_log)

    def generate():
        try:
            # Replay everything that happened before this client connected
            terminal = False
            for event in log_snapshot:
                yield f"data: {json.dumps(event)}\n\n"
                if event.get("type") in ("done", "error"):
                    terminal = True

            if terminal:
                # Job already finished — nothing more to wait for
                return

            # Stream live events
            while True:
                try:
                    event = q.get(timeout=30)
                    yield f"data: {json.dumps(event)}\n\n"
                    if event.get("type") in ("done", "error"):
                        break
                except queue.Empty:
                    yield f"data: {json.dumps({'type': 'ping'})}\n\n"
        finally:
            with _subs_lock:
                try:
                    _subscribers.remove(q)
                except ValueError:
                    pass

    return Response(
        stream_with_context(generate()),
        mimetype="text/event-stream",
        headers={"Cache-Control": "no-cache", "X-Accel-Buffering": "no"},
    )


@app.route("/open-email/<msg_id>")
def open_email(msg_id):
    folder = request.args.get("folder", "Inbox")
    try:
        idx = int(request.args.get("index", 0))
    except (ValueError, TypeError):
        idx = 0

    cfg = se.load_config()
    mac_agent_url = cfg.get("mac_agent_url", "").strip()

    if _is_mac_client():
        # Client is on the Mac — open directly in Outlook
        if mac_agent_url:
            result, status = _call_mac_agent(f"/open-email/{msg_id}", json_body={"folder": folder, "index": idx})
            return jsonify(result), status
        else:
            success = se.open_email_in_outlook(msg_id, folder, msg_index=idx)
            return jsonify({"success": success})
    else:
        # Remote client — tell the browser to open the viewer page
        return jsonify({"open_viewer": True, "viewer_url": f"/email/{msg_id}"})


@app.route("/email/<msg_id>")
def email_viewer(msg_id):
    """Full email viewer page — used by non-Mac clients."""
    email = _db.get_email(msg_id)
    if not email:
        return "Email not found in database.", 404
    return render_template("email_view.html", email=email)


def _remove_from_event_log(msg_id: str):
    """Remove an email from the 'done' event in the replay log so reconnecting clients don't see it."""
    with _subs_lock:
        for evt in _event_log:
            if evt.get("type") == "done" and "results" in evt:
                evt["results"] = [r for r in evt["results"] if r.get("email", {}).get("id") != msg_id]


@app.route("/mark-read/<msg_id>", methods=["POST"])
def mark_read(msg_id):
    data = request.get_json() or {}
    folder = data.get("folder", "Inbox")
    idx = int(data.get("index", 0))

    cfg = se.load_config()
    mac_agent_url = cfg.get("mac_agent_url", "").strip()

    if mac_agent_url:
        result, status = _call_mac_agent(f"/mark-read/{msg_id}", json_body={"folder": folder, "index": idx})
        if result.get("success"):
            _remove_from_event_log(msg_id)
        return jsonify(result), status
    else:
        success = se.mark_email_as_read(msg_id, folder, msg_index=idx)
        if success:
            _remove_from_event_log(msg_id)
        return jsonify({"success": success})


@app.route("/pins", methods=["GET"])
def get_pins():
    return jsonify(_db.get_all_pins())


@app.route("/emails/annotations", methods=["POST"])
def email_annotations():
    ids = (request.get_json() or {}).get("ids", [])
    return jsonify(_db.get_email_annotations(ids))


@app.route("/emails/<msg_id>/notes", methods=["PUT"])
def save_notes(msg_id):
    notes = (request.get_json() or {}).get("notes", "")
    _db.set_email_notes(msg_id, notes)
    return jsonify({"ok": True})


@app.route("/emails/<msg_id>/importance", methods=["PUT"])
def save_importance(msg_id):
    importance = (request.get_json() or {}).get("importance", "normal")
    _db.set_user_importance(msg_id, importance)
    return jsonify({"ok": True})


@app.route("/pins/<msg_id>", methods=["POST"])
def add_pin(msg_id):
    data = request.get_json() or {}
    _db.upsert_pin(msg_id, data)
    return jsonify({"status": "pinned"})


@app.route("/pins/<msg_id>", methods=["DELETE"])
def remove_pin(msg_id):
    _db.delete_pin(msg_id)
    return jsonify({"status": "unpinned"})


@app.route("/feedback", methods=["GET"])
def get_feedback():
    return jsonify(_db.get_all_feedback())


@app.route("/feedback", methods=["POST"])
def add_feedback():
    data = request.get_json() or {}
    _db.add_feedback(
        email_id=data.get("msg_id", ""),
        subject=data.get("subject", ""),
        from_=data.get("from_", ""),
        original_category=data.get("original_category", ""),
        correct_category=data.get("correct_category", ""),
        note=data.get("note", ""),
    )
    return jsonify({"status": "saved"})


# ---------------------------------------------------------------------------
# Guidelines
# ---------------------------------------------------------------------------

def _embed_guideline_async(id_: int, text: str):
    """Try to generate a neural embedding and store it (Ollama only, optional)."""
    def _run():
        try:
            cfg = se.load_config()
            if cfg.get("llm_provider") != "ollama":
                return  # Neural embeddings only supported via Ollama for now
            vec = se.get_embedding(text, cfg)
            if vec:
                _db.update_guideline(id_, embedding=json.dumps(vec))
                print(f"[guidelines] Neural embedding stored for id={id_}")
        except Exception as e:
            print(f"[guidelines] Embedding error for id={id_}: {e}")
    threading.Thread(target=_run, daemon=True).start()


@app.route("/guidelines", methods=["GET"])
def get_guidelines():
    gs = _db.get_guidelines()
    # Don't ship the raw embedding vectors to the client — they're large
    for g in gs:
        g["has_embedding"] = bool(g.get("embedding"))
        g.pop("embedding", None)
    return jsonify(gs)


@app.route("/guidelines", methods=["POST"])
def create_guideline():
    data = request.get_json() or {}
    text = data.get("text", "").strip()
    if not text:
        return jsonify({"error": "text is required"}), 400
    id_ = _db.add_guideline(text, source="manual")
    _embed_guideline_async(id_, text)
    g = _db.get_guideline(id_)
    g["has_embedding"] = False  # embedding is in-flight
    g.pop("embedding", None)
    return jsonify(g)


@app.route("/guidelines/<int:gid>", methods=["PUT"])
def update_guideline(gid):
    data = request.get_json() or {}
    text = data.get("text")
    enabled = data.get("enabled")
    if enabled is not None:
        enabled = bool(enabled)
    if text:
        # Text changed — clear old embedding and regenerate
        _db.update_guideline(gid, text=text, enabled=enabled, embedding=None)
        _embed_guideline_async(gid, text)
    else:
        _db.update_guideline(gid, enabled=enabled)
    g = _db.get_guideline(gid)
    g["has_embedding"] = bool(g.get("embedding"))
    g.pop("embedding", None)
    return jsonify(g)


@app.route("/guidelines/<int:gid>", methods=["DELETE"])
def delete_guideline(gid):
    _db.delete_guideline(gid)
    return jsonify({"status": "deleted"})


@app.route("/guidelines/from-feedback", methods=["POST"])
def guideline_from_feedback():
    """AI agent: convert one feedback correction into a generalised guideline."""
    data = request.get_json() or {}
    cfg = se.load_config()
    try:
        text = se.generate_guideline_from_feedback(
            subject=data.get("subject", ""),
            from_=data.get("from_", ""),
            original=data.get("original_category", ""),
            correct=data.get("correct_category", ""),
            note=data.get("note", ""),
            cfg=cfg,
        )
        id_ = _db.add_guideline(text, source="feedback")
        _embed_guideline_async(id_, text)
        g = _db.get_guideline(id_)
        g["has_embedding"] = False  # embedding is in-flight
        g.pop("embedding", None)
        return jsonify({"status": "created", "guideline": g})
    except Exception as e:
        return jsonify({"error": str(e)}), 500


@app.route("/guidelines/reembed", methods=["POST"])
def reembed_guidelines():
    """Regenerate embeddings for all guidelines that are missing one."""
    def _run():
        cfg = se.load_config()
        gs = _db.get_guidelines()
        for g in gs:
            if not g.get("embedding"):
                try:
                    vec = se.get_embedding(g["text"], cfg)
                    if vec:
                        _db.update_guideline(g["id"], embedding=json.dumps(vec))
                        print(f"[guidelines] Embedded id={g['id']}")
                except Exception as e:
                    print(f"[guidelines] Reembed error for id={g['id']}: {e}")
    threading.Thread(target=_run, daemon=True).start()
    return jsonify({"status": "started"})


@app.route("/check-outlook")
def check_outlook():
    cfg = se.load_config()
    mac_agent_url = cfg.get("mac_agent_url", "").strip()
    if mac_agent_url:
        result, status = _call_mac_agent("/check-outlook", method="GET")
        return jsonify(result), status
    is_classic, msg = se.check_outlook_classic_mode()
    return jsonify({"ok": is_classic, "msg": msg})


@app.route("/config", methods=["GET", "POST"])
def config():
    if request.method == "POST":
        data = request.get_json()
        with open(se.CONFIG_FILE, "w") as f:
            json.dump(data, f, indent=2)
        return jsonify({"status": "saved"})
    return jsonify(se.load_config())


@app.route("/client-context")
def client_context():
    """Tell the frontend whether it's running on the Mac or remotely."""
    cfg = se.load_config()
    return jsonify({
        "is_mac": _is_mac_client(),
        "has_agent": bool(cfg.get("mac_agent_url", "").strip()),
    })


# ---------------------------------------------------------------------------
# Screening worker
# ---------------------------------------------------------------------------

def _emit(event: dict):
    """Append event to the persistent log and fan-out to all live subscribers."""
    with _subs_lock:
        _event_log.append(event)
        for q in _subscribers:
            q.put(event)


def _do_screening(data: dict):
    global _job_running, _last_results
    # Clear the log for the new job (existing subscribers see the clear on reconnect)
    with _subs_lock:
        _event_log.clear()

    try:
        cfg = se.load_config()
        mac_agent_url = cfg.get("mac_agent_url", "").strip()

        # Pre-flight: verify Outlook is accessible
        _emit({"type": "log", "msg": "Checking Outlook mode…"})
        if mac_agent_url:
            result, status = _call_mac_agent("/check-outlook", method="GET")
            is_classic = result.get("ok", False)
            mode_msg = result.get("msg", "Unknown")
        else:
            is_classic, mode_msg = se.check_outlook_classic_mode()

        if not is_classic:
            _emit({"type": "error", "msg": mode_msg or result.get("error", "Could not reach Mac Agent")})
            return
        _emit({"type": "log", "msg": mode_msg})

        # Apply request overrides
        provider = data.get("provider") or cfg.get("llm_provider", "github")
        cfg["llm_provider"] = provider
        model = data.get("model", "").strip()
        if model:
            if provider == "github":
                cfg["github_model"] = model
            else:
                cfg["ollama_model"] = model
        folder = data.get("folder", "").strip() or cfg.get("outlook_folder", "Inbox")
        cfg["outlook_folder"] = folder
        no_llm = data.get("no_llm", False)
        unread_only = data.get("unread", False)
        force_rescreen = data.get("force_rescreen", False)

        today = datetime.today().replace(hour=0, minute=0, second=0, microsecond=0)
        end_date = datetime.strptime(data["end"], "%Y-%m-%d") if data.get("end") else today
        start_date = datetime.strptime(data["start"], "%Y-%m-%d") if data.get("start") else today - timedelta(days=cfg["date_range_days"] - 1)

        unread_label = " (unread only)" if unread_only else ""
        _emit({"type": "log", "msg": f"Querying '{folder}' {start_date.date()} → {end_date.date()}{unread_label}…"})
        _emit({"type": "log", "msg": "This may take a minute on large mailboxes."})

        if mac_agent_url:
            _emit({"type": "log", "msg": f"Fetching via Mac Agent at {mac_agent_url}…"})
            emails = se.fetch_emails_from_agent(folder, start_date, end_date, cfg, unread_only=unread_only)
        else:
            emails = se.fetch_emails_from_outlook(
                folder, start_date, end_date,
                int(cfg.get("outlook_query_timeout_sec", 600)),
                bool(cfg.get("outlook_include_body", True)),
                unread_only=unread_only,
                fetch_tail=int(cfg.get("outlook_fetch_tail", 500)),
            )

        if not emails:
            _emit({"type": "done", "results": [], "action_count": 0, "interesting_count": 0})
            return

        _emit({"type": "log", "msg": f"Found {len(emails)} email(s). Screening with {provider}…"})
        _emit({"type": "total", "total": len(emails)})

        results = []
        cache_hits = 0
        for i, email in enumerate(emails, 1):
            email["body_clean"] = se.clean_body(email.get("body", ""), cfg["max_body_chars"])
            subj = email.get("subject", "")[:60]
            _emit({"type": "progress", "current": i, "total": len(emails), "subject": subj})

            # Check cache first (skip if force_rescreen requested)
            cached = None if force_rescreen else _db.get_screening(email["id"])
            if cached:
                screening = cached
                cache_hits += 1
            elif no_llm:
                screening = se.screen_rule_based(email)
            elif provider == "github":
                result = se.screen_with_github(email, cfg)
                if result is None:
                    _emit({"type": "log", "msg": f"⚠ LLM returned no result for '{subj}', using rule-based fallback."})
                    result = se.screen_rule_based(email)
                screening = result
            else:
                result = se.screen_with_llm(email, cfg)
                if result is None:
                    _emit({"type": "log", "msg": f"⚠ LLM returned no result for '{subj}', using rule-based fallback."})
                    result = se.screen_rule_based(email)
                screening = result

            # Persist email + screening to DB (upsert is idempotent for cached hits)
            _db.upsert_email(email, folder=folder)
            if not cached:
                _db.upsert_screening(email["id"], screening)

            results.append({"email": email, "screening": screening})

        # Write report files
        output_dir_cfg = cfg.get("output_dir", "").strip()
        output_dir = Path(output_dir_cfg).expanduser() if output_dir_cfg else Path(se.__file__).parent / "reports"
        se.generate_report(results, output_dir, start_date, end_date)

        new_count = len(emails) - cache_hits
        if cache_hits:
            _emit({"type": "log", "msg": f"📦 {cache_hits} from cache, {new_count} newly screened."})

        threshold = float(cfg.get("confidence_threshold", 0.6))
        action_count = sum(
            1 for r in results
            if r["screening"].get("needs_action") and r["screening"].get("confidence", 0) >= threshold
        )
        interesting_count = sum(
            1 for r in results
            if not r["screening"].get("needs_action") and r["screening"].get("interesting")
        )

        _last_results = results

        # Serialize results for the browser (strip large body fields)
        serializable = []
        for r in results:
            e = {k: v for k, v in r["email"].items() if k not in ("body", "body_clean")}
            e["preview"] = r["email"].get("body_clean", "")[:200]
            serializable.append({"email": e, "screening": r["screening"], "folder": folder})

        _emit({
            "type": "done",
            "results": serializable,
            "action_count": action_count,
            "interesting_count": interesting_count,
        })

    except Exception as exc:
        _emit({"type": "error", "msg": str(exc)})
    finally:
        with _job_lock:
            _job_running = False


# ---------------------------------------------------------------------------
# Entry point
# ---------------------------------------------------------------------------

if __name__ == "__main__":
    import os
    port = int(os.environ.get("PORT", 5001))
    host = os.environ.get("SCREEN_HOST", "127.0.0.1")

    def _open_browser():
        time.sleep(1.2)
        try:
            import subprocess, sys
            # Use subprocess so a failure doesn't affect the server process
            subprocess.Popen(
                ["/usr/bin/open", f"http://127.0.0.1:{port}"],
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                start_new_session=True,
            )
        except Exception:
            pass  # Non-critical — browser launch failure should never crash server

    threading.Thread(target=_open_browser, daemon=True).start()
    print(f"[✓] Email Screener running at http://127.0.0.1:{port}  (Ctrl+C to quit)")
    app.run(host=host, port=port, debug=False, threaded=True)
