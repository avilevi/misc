"""
SQLite database layer for Email Screener.

Tables:
  emails      — raw email data fetched from Outlook
  screenings  — LLM classification results (one per email)
  pins        — pinned emails (persists across sessions)
  feedback    — user corrections fed back into the LLM prompt
"""

from __future__ import annotations

import json
import sqlite3
import threading
from pathlib import Path

DB_PATH = Path(__file__).parent / "emails.db"
_lock = threading.Lock()


def _connect() -> sqlite3.Connection:
    conn = sqlite3.connect(str(DB_PATH), check_same_thread=False)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA foreign_keys=ON")
    return conn


def init_db():
    """Create tables if they don't exist and migrate legacy JSON files."""
    with _lock:
        conn = _connect()
        conn.executescript("""
        CREATE TABLE IF NOT EXISTS emails (
            id          TEXT PRIMARY KEY,
            subject     TEXT,
            from_       TEXT,
            to_         TEXT,
            cc          TEXT,
            date        TEXT,
            body_raw    TEXT,
            body_clean  TEXT,
            is_read     INTEGER DEFAULT 0,
            idx         INTEGER,
            folder      TEXT,
            fetched_at  TEXT DEFAULT (datetime('now'))
        );

        CREATE TABLE IF NOT EXISTS screenings (
            email_id        TEXT PRIMARY KEY REFERENCES emails(id),
            category        TEXT,
            urgency         TEXT,
            confidence      REAL,
            summary         TEXT,
            reason          TEXT,
            actions_json    TEXT,
            interesting     INTEGER DEFAULT 0,
            interest_reason TEXT,
            screened_at     TEXT DEFAULT (datetime('now'))
        );

        CREATE TABLE IF NOT EXISTS pins (
            email_id    TEXT PRIMARY KEY REFERENCES emails(id),
            payload_json TEXT NOT NULL,
            pinned_at   TEXT DEFAULT (datetime('now'))
        );

        CREATE TABLE IF NOT EXISTS feedback (
            id                  INTEGER PRIMARY KEY AUTOINCREMENT,
            email_id            TEXT,
            subject             TEXT,
            from_               TEXT,
            original_category   TEXT,
            correct_category    TEXT,
            note                TEXT,
            ts                  TEXT DEFAULT (datetime('now'))
        );

        CREATE TABLE IF NOT EXISTS guidelines (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            text        TEXT NOT NULL,
            source      TEXT DEFAULT 'manual',
            enabled     INTEGER DEFAULT 1,
            created_at  TEXT DEFAULT (datetime('now'))
        );
        """)
        conn.commit()
        conn.close()

    _migrate_json_files()
    _migrate_schema()


def _migrate_schema():
    """Add columns introduced after initial release."""
    with _lock:
        conn = _connect()
        existing = {row[1] for row in conn.execute("PRAGMA table_info(screenings)").fetchall()}
        if "summary" not in existing:
            conn.execute("ALTER TABLE screenings ADD COLUMN summary TEXT")

        g_existing = {row[1] for row in conn.execute("PRAGMA table_info(guidelines)").fetchall()}
        if "embedding" not in g_existing:
            conn.execute("ALTER TABLE guidelines ADD COLUMN embedding TEXT")

        conn.commit()
        conn.close()


def _migrate_json_files():
    """One-time migration of pins.json and feedback.json into SQLite."""
    base = Path(__file__).parent

    pins_file = base / "pins.json"
    if pins_file.exists():
        try:
            with open(pins_file) as f:
                pins = json.load(f)
            for msg_id, payload in pins.items():
                upsert_pin(msg_id, payload)
            pins_file.rename(pins_file.with_suffix(".json.bak"))
            print("[db] Migrated pins.json → SQLite")
        except Exception as e:
            print(f"[db] Could not migrate pins.json: {e}")

    feedback_file = base / "feedback.json"
    if feedback_file.exists():
        try:
            with open(feedback_file) as f:
                items = json.load(f)
            for item in items:
                add_feedback(
                    email_id=item.get("id", ""),
                    subject=item.get("subject", ""),
                    from_=item.get("from", ""),
                    original_category=item.get("original_category", ""),
                    correct_category=item.get("correct_category", ""),
                    note=item.get("note", ""),
                )
            feedback_file.rename(feedback_file.with_suffix(".json.bak"))
            print("[db] Migrated feedback.json → SQLite")
        except Exception as e:
            print(f"[db] Could not migrate feedback.json: {e}")


# ---------------------------------------------------------------------------
# Email CRUD
# ---------------------------------------------------------------------------

def upsert_email(email: dict, folder: str = "Inbox"):
    """Insert or update an email record. Returns the email id."""
    with _lock:
        conn = _connect()
        conn.execute("""
            INSERT INTO emails (id, subject, from_, to_, cc, date, body_raw, body_clean, is_read, idx, folder, fetched_at)
            VALUES (:id, :subject, :from_, :to_, :cc, :date, :body_raw, :body_clean, :is_read, :idx, :folder, datetime('now'))
            ON CONFLICT(id) DO UPDATE SET
                subject=excluded.subject, from_=excluded.from_, to_=excluded.to_, cc=excluded.cc,
                date=excluded.date, body_raw=excluded.body_raw, body_clean=excluded.body_clean,
                is_read=excluded.is_read, idx=excluded.idx, folder=excluded.folder,
                fetched_at=excluded.fetched_at
        """, {
            "id":         email.get("id", ""),
            "subject":    email.get("subject", ""),
            "from_":      email.get("from", ""),
            "to_":        email.get("to", ""),
            "cc":         email.get("cc", ""),
            "date":       email.get("date", ""),
            "body_raw":   email.get("body", ""),
            "body_clean": email.get("body_clean", ""),
            "is_read":    1 if email.get("read") else 0,
            "idx":        email.get("index"),
            "folder":     folder,
        })
        conn.commit()
        conn.close()
    return email.get("id", "")


def get_email(email_id: str) -> dict | None:
    with _lock:
        conn = _connect()
        row = conn.execute("SELECT * FROM emails WHERE id=?", (email_id,)).fetchone()
        conn.close()
    return dict(row) if row else None


def upsert_screening(email_id: str, screening: dict):
    category = ("action_required" if screening.get("needs_action")
                else ("worth_reading" if screening.get("interesting") else "noise"))
    with _lock:
        conn = _connect()
        conn.execute("""
            INSERT INTO screenings (email_id, category, urgency, confidence, summary, reason, actions_json, interesting, interest_reason, screened_at)
            VALUES (:email_id, :category, :urgency, :confidence, :summary, :reason, :actions_json, :interesting, :interest_reason, datetime('now'))
            ON CONFLICT(email_id) DO UPDATE SET
                category=excluded.category, urgency=excluded.urgency,
                confidence=excluded.confidence, summary=excluded.summary,
                reason=excluded.reason, actions_json=excluded.actions_json,
                interesting=excluded.interesting, interest_reason=excluded.interest_reason,
                screened_at=excluded.screened_at
        """, {
            "email_id":       email_id,
            "category":       category,
            "urgency":        screening.get("urgency", "low"),
            "confidence":     screening.get("confidence", 0.0),
            "summary":        screening.get("summary", ""),
            "reason":         screening.get("reason", ""),
            "actions_json":   json.dumps(screening.get("actions", [])),
            "interesting":    1 if screening.get("interesting") else 0,
            "interest_reason": screening.get("interest_reason", ""),
        })
        conn.commit()
        conn.close()


# ---------------------------------------------------------------------------
# Pins
# ---------------------------------------------------------------------------

def upsert_pin(email_id: str, payload: dict):
    with _lock:
        conn = _connect()
        # Disable FK enforcement for pins migrated before emails table is populated
        conn.execute("PRAGMA foreign_keys=OFF")
        conn.execute("""
            INSERT INTO pins (email_id, payload_json, pinned_at)
            VALUES (?, ?, datetime('now'))
            ON CONFLICT(email_id) DO UPDATE SET payload_json=excluded.payload_json
        """, (email_id, json.dumps(payload)))
        conn.commit()
        conn.close()


def delete_pin(email_id: str):
    with _lock:
        conn = _connect()
        conn.execute("DELETE FROM pins WHERE email_id=?", (email_id,))
        conn.commit()
        conn.close()


def get_all_pins() -> list[dict]:
    with _lock:
        conn = _connect()
        rows = conn.execute("SELECT email_id, payload_json FROM pins ORDER BY pinned_at DESC").fetchall()
        conn.close()
    return [json.loads(r["payload_json"]) for r in rows]


# ---------------------------------------------------------------------------
# Feedback
# ---------------------------------------------------------------------------

def add_feedback(email_id: str, subject: str, from_: str,
                 original_category: str, correct_category: str, note: str):
    with _lock:
        conn = _connect()
        conn.execute("""
            INSERT INTO feedback (email_id, subject, from_, original_category, correct_category, note)
            VALUES (?, ?, ?, ?, ?, ?)
        """, (email_id, subject, from_, original_category, correct_category, note))
        conn.commit()
        conn.close()


def get_screening(email_id: str) -> dict | None:
    """Return a cached screening dict for the given email, or None if not found."""
    with _lock:
        conn = _connect()
        row = conn.execute(
            "SELECT * FROM screenings WHERE email_id=?", (email_id,)
        ).fetchone()
        conn.close()
    if not row:
        return None
    row = dict(row)
    return {
        "needs_action":    row["category"] == "action_required",
        "urgency":         row["urgency"] or "low",
        "confidence":      row["confidence"] or 0.0,
        "summary":         row["summary"] or "",
        "reason":          row["reason"] or "",
        "actions":         json.loads(row["actions_json"]) if row.get("actions_json") else [],
        "interesting":     bool(row["interesting"]),
        "interest_reason": row["interest_reason"] or "",
        "_cached":         True,
        "_screened_at":    row["screened_at"],
    }


def get_all_feedback() -> list[dict]:
    with _lock:
        conn = _connect()
        rows = conn.execute("SELECT * FROM feedback ORDER BY ts DESC").fetchall()
        conn.close()
    return [dict(r) for r in rows]


def get_feedback_for_prompt(limit: int = 40) -> list[dict]:
    """Return the most recent N feedback entries for injecting into LLM prompt."""
    with _lock:
        conn = _connect()
        rows = conn.execute(
            "SELECT subject, original_category, correct_category, note FROM feedback "
            "WHERE correct_category != '' ORDER BY ts DESC LIMIT ?", (limit,)
        ).fetchall()
        conn.close()
    return [dict(r) for r in rows]


# ---------------------------------------------------------------------------
# Guidelines (AI-generated or manually authored rules injected into prompts)
# ---------------------------------------------------------------------------

def add_guideline(text: str, source: str = "manual") -> int:
    with _lock:
        conn = _connect()
        cur = conn.execute(
            "INSERT INTO guidelines (text, source) VALUES (?, ?)", (text.strip(), source)
        )
        conn.commit()
        row_id = cur.lastrowid
        conn.close()
    return row_id


def get_guidelines(enabled_only: bool = False) -> list[dict]:
    with _lock:
        conn = _connect()
        if enabled_only:
            rows = conn.execute(
                "SELECT * FROM guidelines WHERE enabled=1 ORDER BY created_at DESC"
            ).fetchall()
        else:
            rows = conn.execute(
                "SELECT * FROM guidelines ORDER BY created_at DESC"
            ).fetchall()
        conn.close()
    return [dict(r) for r in rows]


def get_guideline(id_: int) -> dict | None:
    with _lock:
        conn = _connect()
        row = conn.execute("SELECT * FROM guidelines WHERE id=?", (id_,)).fetchone()
        conn.close()
    return dict(row) if row else None


def update_guideline(id_: int, text: str | None = None, enabled: bool | None = None,
                     embedding: str | None = None):
    with _lock:
        conn = _connect()
        if text is not None:
            conn.execute("UPDATE guidelines SET text=? WHERE id=?", (text.strip(), id_))
        if enabled is not None:
            conn.execute("UPDATE guidelines SET enabled=? WHERE id=?", (1 if enabled else 0, id_))
        if embedding is not None:
            conn.execute("UPDATE guidelines SET embedding=? WHERE id=?", (embedding, id_))
        conn.commit()
        conn.close()


def delete_guideline(id_: int):
    with _lock:
        conn = _connect()
        conn.execute("DELETE FROM guidelines WHERE id=?", (id_,))
        conn.commit()
        conn.close()


def get_enabled_guidelines() -> list[str]:
    """Return only the text of enabled guidelines, for prompt injection."""
    return [g["text"] for g in get_guidelines(enabled_only=True)]
