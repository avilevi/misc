# Email Screener — AI Agent Briefing

> **Purpose of this file:** Comprehensive context for an AI agent picking up this project
> on a fresh Ubuntu server. Read this entirely before touching any code.

---

## 1. What This Project Is

An **email screening web app** for a heavy Outlook user (Avi).

- Reads emails from Microsoft Outlook (macOS only, via AppleScript)
- Sends each email to **GitHub Copilot** (LLM) which classifies it as:
  - `needs_action` — Avi personally needs to do something
  - `interesting` — worth reading, but no action needed  
  - noise — skip it
- Presents a **web UI** with three categorized sections, action buttons per email
- User can **pin** important items, **mark as read**, give **feedback** to improve LLM classification

The system is split into two processes:
- **Mac Agent** (`mac_agent.py`) — runs on Avi's Mac, speaks to Outlook via AppleScript, exposes HTTP API on port 5002
- **Screen App** (`screen_app.py`) — runs on the Ubuntu server, serves the web UI on port 5001, calls the Mac Agent to fetch emails, stores everything in SQLite

---

## 2. Architecture

```
┌───────────────────────────────────────────────┐
│  Mac (Avi's machine)                          │
│  ┌─────────────────────────────────────────┐  │
│  │  mac_agent.py  (port 5002)              │  │
│  │  • Runs AppleScript → Outlook           │  │
│  │  • Returns email JSON (id, subject,     │  │
│  │    from, to, cc, date, body, read)      │  │
│  │  • Opens emails in Outlook on request   │  │
│  │  • Marks emails as read on request      │  │
│  └─────────────────────────────────────────┘  │
└───────────────────────┬───────────────────────┘
                        │ HTTP over VPN
                        ▼
┌───────────────────────────────────────────────┐
│  Ubuntu Server                                │
│  ┌─────────────────────────────────────────┐  │
│  │  screen_app.py  (port 5001)             │  │
│  │  • Flask web UI                         │  │
│  │  • Calls Mac Agent to get emails        │  │
│  │  • Screens with GitHub Copilot LLM      │  │
│  │  • SQLite storage (emails.db)           │  │
│  │  • /email/<id> viewer for remote users  │  │
│  └─────────────────────────────────────────┘  │
└───────────────────────────────────────────────┘
          ▲
          │  Browser (any VPN device)
```

**Key constraint:** AppleScript only runs in a macOS GUI session. The Ubuntu server cannot run AppleScript — it must call the Mac Agent over HTTP.

---

## 3. File Reference

| File | Location | Purpose |
|------|----------|---------|
| `screen_app.py` | server | Flask web server — all routes, LLM screening worker, DB calls |
| `screen_emails.py` | server + mac | Email fetching (AppleScript), LLM call logic, report generation, text cleaning |
| `db.py` | server | SQLite layer — all DB reads/writes (emails, screenings, pins, feedback) |
| `mac_agent.py` | mac | Flask HTTP agent — wraps AppleScript for remote access |
| `config.json` | both | Shared config file (see §5) |
| `templates/index.html` | server | Main web UI (single-page, JS-driven) |
| `templates/email_view.html` | server | Full email viewer page for non-Mac clients |
| `deploy/mac_launchagent.plist` | mac | macOS LaunchAgent for auto-starting mac_agent.py on login |
| `deploy/README.md` | reference | Deployment steps (less detailed than this file) |
| `emails.db` | server | SQLite database (created automatically on first run) |
| `requirements.txt` | both | `flask>=3.0.0`, `requests>=2.31.0` |

---

## 4. Ubuntu Server Setup (Step by Step)

### 4.1 Prerequisites

```bash
python3 --version   # needs 3.9+
pip3 install flask requests
```

Or with the project's requirements file:
```bash
pip3 install -r requirements.txt
```

### 4.2 Get a GitHub Token with Copilot Access

The LLM screening uses GitHub Copilot API. The token must have `copilot` scope.

**Option A — GitHub CLI (recommended):**
```bash
gh auth login
gh auth refresh -s copilot
export GITHUB_TOKEN=$(gh auth token)
```

**Option B — Manual token:**
- Go to github.com → Settings → Developer settings → Personal access tokens
- Create a token with `copilot` scope
- `export GITHUB_TOKEN=ghp_xxxxxxxxxxxx`

**Option C — Add to config.json:**
```json
{ "github_token": "ghp_xxxxxxxxxxxx" }
```

The app checks: config.json `github_token` → `$GITHUB_TOKEN` env var → `gh auth token` CLI (in that order).

### 4.3 Configure config.json

Edit `config.json` to set server-side values. Minimum required for remote mode:

```json
{
  "llm_provider": "github",
  "github_model": "gpt-4o-mini",
  "user_name": "Avi",
  "mac_agent_url": "http://<mac-vpn-ip>:5002",
  "mac_agent_token": "your-shared-secret-here",
  "mac_ip": "<mac-vpn-ip>",
  "date_range_days": 7,
  "outlook_folder": "Inbox",
  "outlook_fetch_tail": 500,
  "outlook_include_body": true,
  "outlook_query_timeout_sec": 600,
  "confidence_threshold": 0.6,
  "max_body_chars": 3000
}
```

**Important fields:**
- `mac_agent_url` — base URL of the Mac Agent (e.g. `http://10.0.0.5:5002`)  
- `mac_agent_token` — shared secret. Must match what's in config.json on the Mac. If empty, no auth is required (VPN-only).  
- `mac_ip` — Mac's VPN IP. Used to determine if a browser request is coming from the Mac (to open Outlook directly) vs a remote client (to show the email viewer page)
- `user_name` — used in LLM prompt: "Does **Avi** need to act on this?"
- `outlook_fetch_tail` — how many messages to scan from the top (newest-first). Default 500. Outlook has 176k+ messages; scanning all would hang.

### 4.4 Start the Server

```bash
cd /path/to/email-screener
python3 screen_app.py
```

Server runs at `http://0.0.0.0:5001` by default (accessible from any VPN device).

**Environment variable overrides:**
```bash
PORT=8080 python3 screen_app.py          # different port
SCREEN_HOST=0.0.0.0 python3 screen_app.py  # different bind host
```

> ⚠️ **IMPORTANT — known bug fixed:** Do NOT use `HOST=` env var override — macOS sets `HOST=<hostname>` which breaks binding. The code uses `SCREEN_HOST` specifically to avoid this. On Ubuntu `HOST` is usually not set, so it should be fine, but be aware.

### 4.5 Run as systemd Service

```ini
# /etc/systemd/system/emailscreener.service
[Unit]
Description=Email Screener
After=network.target

[Service]
Type=simple
User=<your-user>
WorkingDirectory=/path/to/email-screener
Environment=GITHUB_TOKEN=<your-token>
ExecStart=/usr/bin/python3 screen_app.py
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

```bash
systemctl daemon-reload
systemctl enable --now emailscreener
```

### 4.6 Test Connectivity to Mac Agent

Before running the full app, verify the Mac Agent is reachable:

```bash
# No token:
curl http://<mac-vpn-ip>:5002/health

# With token:
curl -H "Authorization: Bearer your-token" http://<mac-vpn-ip>:5002/health
# Should return: {"host": "mac-agent", "status": "ok"}
```

---

## 5. Mac Agent Setup

The Mac must run `mac_agent.py` at all times. It exposes Outlook data over HTTP.

### 5.1 Prerequisites on Mac

```bash
pip3 install flask requests
```

Outlook must be open in **Classic (Legacy) mode** — `Help → Revert to Legacy Outlook`. The new Outlook blocks AppleScript access to message properties.

macOS may ask for Automation permission the first time. Allow it: `System Settings → Privacy & Security → Automation → Terminal → Microsoft Outlook`.

### 5.2 Configure config.json on Mac

```json
{
  "mac_agent_token": "your-shared-secret-here",
  "mac_agent_port": 5002,
  "outlook_folder": "Inbox",
  "outlook_fetch_tail": 500,
  "outlook_include_body": true,
  "outlook_query_timeout_sec": 600,
  "user_name": "Avi"
}
```

### 5.3 Run Mac Agent

```bash
cd /path/to/email-screener
python3 mac_agent.py
# Listening on 0.0.0.0:5002
```

### 5.4 Auto-Start on Login (LaunchAgent)

```bash
PROJECT_DIR=$(pwd)
sed "s|REPLACE_WITH_FULL_PATH|$PROJECT_DIR|g" \
    deploy/mac_launchagent.plist \
    > ~/Library/LaunchAgents/com.emailscreener.macagent.plist

launchctl load ~/Library/LaunchAgents/com.emailscreener.macagent.plist
launchctl list | grep emailscreener   # verify it's running
```

---

## 6. API Routes Reference

All routes are on `screen_app.py` (port 5001).

### Web Pages

| Route | Method | Description |
|-------|--------|-------------|
| `/` | GET | Main web UI (`index.html`) |
| `/email/<msg_id>` | GET | Full email viewer page — used by non-Mac browsers |

### Data / Actions

| Route | Method | Description |
|-------|--------|-------------|
| `/run` | POST | Start screening job. Body: `{start, end, folder, provider, model, unread, no_llm}` |
| `/stream` | GET | SSE stream of screening progress events |
| `/open-email/<msg_id>` | GET | Open email. On Mac: opens Outlook. Remote: returns `{open_viewer: true, viewer_url}` |
| `/mark-read/<msg_id>` | POST | Mark email as read via Mac Agent. Body: `{folder, index}` |
| `/check-outlook` | GET | Check Outlook is running in Classic mode |
| `/client-context` | GET | Returns `{is_mac: bool, has_agent: bool}` — tells frontend which mode to use |

### Pins

| Route | Method | Description |
|-------|--------|-------------|
| `/pins` | GET | List all pinned items (array of payload dicts) |
| `/pins/<msg_id>` | POST | Pin an email. Body: full email card data |
| `/pins/<msg_id>` | DELETE | Remove a pin |

### Feedback

| Route | Method | Description |
|-------|--------|-------------|
| `/feedback` | GET | List all feedback entries |
| `/feedback` | POST | Add feedback. Body: `{msg_id, subject, from_, original_category, correct_category, note}` |

### Config

| Route | Method | Description |
|-------|--------|-------------|
| `/config` | GET | Get current config.json |
| `/config` | POST | Save new config.json (body = full config object) |

---

## 7. Mac Agent API (port 5002)

| Route | Method | Auth | Description |
|-------|--------|------|-------------|
| `/health` | GET | No | Health check |
| `/check-outlook` | GET | Yes | Probe Outlook classic mode |
| `/fetch-emails` | POST | Yes | Fetch emails. Body: `{folder, start, end, include_body, unread_only, fetch_tail, timeout_sec}` |
| `/open-email/<msg_id>` | POST | Yes | Open msg in Outlook. Body: `{folder, index}` |
| `/mark-read/<msg_id>` | POST | Yes | Mark msg read. Body: `{folder, index}` |

Auth = `Authorization: Bearer <mac_agent_token>` header. If `mac_agent_token` is empty in config, auth is skipped.

---

## 8. SQLite Schema (`emails.db`)

Database is created automatically by `db.init_db()` at server startup.

```sql
CREATE TABLE emails (
    id          TEXT PRIMARY KEY,       -- Outlook message ID
    subject     TEXT,
    from_       TEXT,                   -- sender email/name
    to_         TEXT,                   -- comma-separated To recipients
    cc          TEXT,                   -- comma-separated CC recipients
    date        TEXT,                   -- date string from Outlook
    body_raw    TEXT,                   -- original body text
    body_clean  TEXT,                   -- cleaned body (quotes/sigs stripped)
    is_read     INTEGER DEFAULT 0,
    idx         INTEGER,                -- Outlook message index (for fast re-opening)
    folder      TEXT,
    fetched_at  TEXT DEFAULT (datetime('now'))
);

CREATE TABLE screenings (
    email_id        TEXT PRIMARY KEY REFERENCES emails(id),
    category        TEXT,               -- 'action_required', 'worth_reading', or 'noise'
    urgency         TEXT,               -- 'low', 'medium', 'high'
    confidence      REAL,
    reason          TEXT,               -- LLM explanation
    actions_json    TEXT,               -- JSON array of action objects
    interesting     INTEGER DEFAULT 0,
    interest_reason TEXT,
    screened_at     TEXT DEFAULT (datetime('now'))
);

CREATE TABLE pins (
    email_id    TEXT PRIMARY KEY REFERENCES emails(id),
    payload_json TEXT NOT NULL,         -- full card data as JSON (used to restore card in UI)
    pinned_at   TEXT DEFAULT (datetime('now'))
);

CREATE TABLE feedback (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    email_id            TEXT,
    subject             TEXT,
    from_               TEXT,
    original_category   TEXT,           -- what the LLM said
    correct_category    TEXT,           -- what the user says it should be
    note                TEXT,
    ts                  TEXT DEFAULT (datetime('now'))
);
```

**Note:** `upsert_pin()` disables FK enforcement temporarily — this allows pinning emails that haven't been stored in the `emails` table yet (e.g. legacy pins migrated from `pins.json`).

---

## 9. Email Screening Logic

### How emails flow

1. Browser sends `POST /run` with date range and options
2. Server calls Mac Agent `POST /fetch-emails` (if `mac_agent_url` is set) — gets list of email dicts
3. Each email dict has: `id, subject, from, to, cc, date, body, read, index`
4. Each email body is cleaned (`clean_body()` strips quotes/signatures/image refs, truncates to `max_body_chars`)
5. Each email is sent to the LLM via `screen_with_github()` (or `screen_with_llm()` for Ollama)
6. Result is stored in SQLite (`upsert_email()` + `upsert_screening()`)
7. Results are sent to browser via SSE stream

### LLM classification output (JSON from LLM)

```json
{
  "needs_action": true,
  "urgency": "medium",
  "confidence": 0.85,
  "reason": "Avi is asked to review the design doc by Friday.",
  "actions": [
    {
      "description": "Review design doc",
      "due_date": "2026-06-06",
      "assignee": "Avi",
      "confidence": 0.85
    }
  ],
  "interesting": false,
  "interest_reason": null
}
```

### Classification rules (baked into LLM system prompt)

The LLM is instructed to:
- `needs_action=true` only if **Avi personally** must do something
- If all actions are assigned to other people → `needs_action=false`, set `interesting=true` if relevant
- Meeting notes without explicit "Avi to do X" → `needs_action=false` (goes to "read later")
- Escalations affecting Avi's team → `needs_action=true` even if not addressed to him
- FYI/CC emails, newsletters, automated notifications → `needs_action=false`

### Feedback loop

User feedback is stored in the `feedback` table. On next screening run, `_build_system_prompt()` reads the last 40 feedback items and appends them as correction rules to the LLM system prompt:
```
Learned corrections from past feedback:
- Email "RE: Design Review": was classified as "noise", correct is "action_required" — note: I need to review these
```

### Fallback: rule-based screening

If LLM call fails or `no_llm=True`, `screen_rule_based()` falls back to keyword matching (patterns like "please review", "action required", "URGENT", etc.).

---

## 10. Web UI Behaviour

The UI (`templates/index.html`) is fully JavaScript-driven:

1. On load: calls `/client-context` to determine if it's running on the Mac → sets `_clientIsMac`
2. On load: calls `/pins` to restore pinned items
3. User hits **Screen Emails** → POST `/run` → opens SSE on `/stream` → renders cards as they arrive
4. Results are split into three sections:
   - 🔔 **Action Required** — `needs_action=true`
   - 💡 **Worth Reading** — `needs_action=false, interesting=true`
   - 🔕 **Noise** — everything else
5. **Pinned** section appears at top — items pinned by user persist across refreshes

### Per-email card actions (bottom row)

| Button | Color | Action |
|--------|-------|--------|
| Open Email | Blue | Mac: opens Outlook. Remote: opens `/email/<id>` in new tab |
| Mark Read | Green | Calls `/mark-read/<id>` → Mac Agent → marks read in Outlook |
| Pin / Unpin | Orange | Adds/removes from `/pins` and shows/hides in Pinned section |
| Done (pinned only) | Green | Marks read + removes pin + hides card |
| 💬 Feedback | Purple | Opens feedback modal |

### Email Viewer (`/email/<id>`)

For non-Mac browsers, "Open Email" navigates to this page which shows:
- Full header: From / To / CC / Date / Subject
- Full email body (renders HTML if available, else plaintext)
- Clean readable styling

---

## 11. "Open Email" — How Correct Linking Works

Outlook messages are identified by two things:
1. **`id`** — Outlook's internal message ID (stable, unique)  
2. **`index`** — position in the mailbox (1-based, newest-first, **shifts as new mail arrives**)

The search strategy (in `open_email_in_outlook()` and `mark_email_as_read()`):
1. Try the stored index first (fast, O(1))
2. If the ID at that index doesn't match (new mail shifted indices), scan ±100 messages around the hint
3. This avoids a full `whose` filter scan on the 176k+ message mailbox (which hangs Outlook)

**Why some emails opened wrong previously:** The original code used only `id` matching with a `whose` clause which caused a full-mailbox scan and Outlook returned wrong results. The index-hint strategy fixes this.

---

## 12. Known Issues & Gotchas

### AppleScript quirks

- `inbox` is a **reserved word** in AppleScript — always use `mail folder "Inbox"` (via `mail folders whose name is "Inbox"`)
- Outlook 176k+ message mailbox: `whose` filters scan the entire mailbox (hangs for minutes). Solution: scan only the last N messages by index (controlled by `outlook_fetch_tail`)
- Messages are ordered **newest-first** (index 1 = today's mail)
- `sender of msg` can return `missing value` — always guard before accessing `.address`
- New Outlook blocks AppleScript message property access — must use Classic/Legacy mode

### Server startup

- **Do not use `HOST` env var** to set the bind host. macOS has `HOST=<hostname>` in the environment; `socket.getaddrinfo(hostname)` fails because the hostname doesn't resolve via DNS. Use `SCREEN_HOST` instead (already fixed in the code).

### DB + pins

- Pins FK constraint is disabled in `upsert_pin()` to allow legacy pins that predate the `emails` table entry
- `pins.json` and `feedback.json` are automatically migrated to SQLite on first run and renamed to `.bak`

### GitHub token scope

- Token needs `copilot` scope (not just `repo` or `read:user`). If you get 401 errors: `gh auth refresh -s copilot`

### HTML email bodies

- The email viewer renders body HTML with `| safe` (no sanitization). If malicious HTML is a concern, add `bleach` sanitizer.

---

## 13. config.json — Full Schema

```json
{
  "llm_provider": "github",              // "github" or "ollama"
  "github_model": "gpt-4o-mini",         // model for GitHub Copilot API
  "github_token": "",                    // leave blank → uses $GITHUB_TOKEN or gh CLI
  "ollama_url": "http://localhost:11434",// Ollama endpoint (only used if llm_provider=ollama)
  "ollama_model": "llama3",
  "date_range_days": 7,                  // default lookback window
  "confidence_threshold": 0.6,           // min confidence to show in Action Required
  "max_body_chars": 3000,                // truncate bodies before LLM
  "outlook_folder": "Inbox",             // folder name to read from
  "outlook_query_timeout_sec": 600,      // AppleScript timeout
  "outlook_include_body": true,          // whether to fetch email body text
  "outlook_fetch_tail": 500,             // scan only the last N messages (newest-first)
  "user_name": "Avi",                    // used in LLM prompt ("Does Avi need to act?")
  "mac_agent_url": "http://10.x.x.x:5002",  // Mac Agent URL (empty = run locally on Mac)
  "mac_agent_token": "secret",           // shared secret for Mac Agent auth (empty = no auth)
  "mac_agent_port": 5002,                // which port mac_agent.py listens on
  "mac_ip": "10.x.x.x",                 // Mac's VPN IP — used to detect local vs remote client
  "output_dir": ""                       // where to write .md/.csv reports (empty = ./reports/)
}
```

---

## 14. What Needs to Be Done for Remote Deployment

When this project lands on the Ubuntu server, here's the exact checklist:

### On the Ubuntu server:

- [ ] `pip3 install flask requests` (or `pip3 install -r requirements.txt`)
- [ ] Set `mac_agent_url`, `mac_agent_token`, `mac_ip` in `config.json`
- [ ] Set up GitHub token: `gh auth login && gh auth refresh -s copilot`
- [ ] Set `GITHUB_TOKEN` environment variable (or add to config.json)
- [ ] Run: `python3 screen_app.py`
- [ ] Verify: `curl http://localhost:5001/` returns HTML

### On the Mac:

- [ ] `pip3 install flask requests`
- [ ] Set `mac_agent_token` and `mac_agent_port` in `config.json`
- [ ] Run: `python3 mac_agent.py`
- [ ] Verify from server: `curl -H "Authorization: Bearer <token>" http://<mac-ip>:5002/health`
- [ ] Open Microsoft Outlook in **Classic mode** (`Help → Revert to Legacy Outlook`)
- [ ] Grant Terminal → Outlook automation in `System Settings → Privacy & Security → Automation`
- [ ] Optional: install LaunchAgent for auto-start (see §5.4)

### Integration test:

1. Open browser → `http://<ubuntu-server-ip>:5001`
2. Set date range to last 7 days, click **Screen Emails**
3. Watch progress stream — should say "Fetching via Mac Agent" then show email cards
4. Click an email's **Open Email** button — remote browser should open `/email/<id>` viewer, Mac browser should open Outlook

---

## 15. Project File Contents Summary

For convenience — what each Python file does in one paragraph:

**`screen_app.py`** (406 lines): Flask server. Imports `screen_emails` and `db`. On startup calls `db.init_db()`. Routes: `/run` starts a background threading job, `/stream` returns SSE events, `/open-email` is context-aware (Mac→Outlook, remote→viewer), `/email/<id>` renders the full viewer page, `/mark-read` proxies to Mac Agent, `/pins` and `/feedback` are CRUD on SQLite, `/client-context` tells the frontend which mode to use. Entry point uses `SCREEN_HOST` env var (not `HOST`).

**`screen_emails.py`** (1151 lines): All the email logic. Contains: `APPLESCRIPT_TEMPLATE` (the full AppleScript that reads Outlook — includes To/CC collection), `check_outlook_classic_mode()`, `fetch_emails_from_outlook()`, `fetch_emails_from_agent()` (HTTP POST to Mac Agent), `parse_applescript_output()` (parses the structured text output), `clean_body()`, `_build_system_prompt()` (base prompt + feedback injections), `screen_with_github()` (Copilot API), `screen_with_llm()` (Ollama), `screen_rule_based()` (keyword fallback), `generate_report()` (writes .md and .csv), `open_email_in_outlook()` and `mark_email_as_read()` (index-hint search strategy), `main()` (CLI entry point).

**`db.py`** (257 lines): SQLite wrapper. All I/O goes through a `threading.Lock()`. Functions: `init_db()` creates tables and runs one-time JSON migration, `upsert_email()`, `get_email()`, `upsert_screening()`, `upsert_pin()` (FK off), `delete_pin()`, `get_all_pins()`, `add_feedback()`, `get_all_feedback()`, `get_feedback_for_prompt()`.

**`mac_agent.py`** (152 lines): Thin Flask wrapper around the AppleScript functions in `screen_emails`. Token auth via `Authorization: Bearer`. Listens on `0.0.0.0` so the Ubuntu server can reach it over VPN. Routes: `/health`, `/check-outlook`, `/fetch-emails`, `/open-email/<id>`, `/mark-read/<id>`.

---

*Generated: 2026-05-30 | Project: email-screener | Owner: Avi*
