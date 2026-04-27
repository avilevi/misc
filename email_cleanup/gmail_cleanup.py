"""
Gmail cleanup script.

Usage:
    python gmail_cleanup.py              # Classify and label all emails (dry-run by default)
    python gmail_cleanup.py --apply      # Actually apply labels
    python gmail_cleanup.py --max 500    # Limit to 500 emails (useful for testing)
    python gmail_cleanup.py --apply --max 500
"""

import argparse
import csv
import os
import re
import sys
from collections import defaultdict
from datetime import datetime, timedelta

from dotenv import load_dotenv
from google.auth.transport.requests import Request
from google.oauth2.credentials import Credentials
from google_auth_oauthlib.flow import InstalledAppFlow
from googleapiclient.discovery import build

from classify import classify_all
from config import CATEGORIES, CATEGORY_DISPOSITION, GMAIL_LABEL_PREFIX

load_dotenv()

SCOPES = ["https://www.googleapis.com/auth/gmail.modify"]
TOKEN_FILE = "token.json"
CREDENTIALS_FILE = os.environ.get("GMAIL_CREDENTIALS_FILE", "credentials.json")


# ---------------------------------------------------------------------------
# Auth
# ---------------------------------------------------------------------------

def get_gmail_service():
    creds = None
    if os.path.exists(TOKEN_FILE):
        creds = Credentials.from_authorized_user_file(TOKEN_FILE, SCOPES)
    if not creds or not creds.valid:
        if creds and creds.expired and creds.refresh_token:
            creds.refresh(Request())
        else:
            flow = InstalledAppFlow.from_client_secrets_file(
                CREDENTIALS_FILE, SCOPES,
                redirect_uri="urn:ietf:wg:oauth:2.0:oob"
            )
            auth_url, _ = flow.authorization_url(prompt="consent")
            print("\nOpen this URL in your browser to authorize:\n")
            print(auth_url)
            print()
            code = input("Paste the authorization code here: ").strip()
            flow.fetch_token(code=code)
            creds = flow.credentials
        with open(TOKEN_FILE, "w") as f:
            f.write(creds.to_json())
    return build("gmail", "v1", credentials=creds)


# ---------------------------------------------------------------------------
# Label management
# ---------------------------------------------------------------------------

def get_or_create_label(service, name: str) -> str:
    """Return label ID, creating it if it doesn't exist."""
    existing = service.users().labels().list(userId="me").execute().get("labels", [])
    for label in existing:
        if label["name"] == name:
            return label["id"]
    created = service.users().labels().create(
        userId="me",
        body={
            "name": name,
            "labelListVisibility": "labelShow",
            "messageListVisibility": "show",
        },
    ).execute()
    print(f"  Created label: {name}")
    return created["id"]


def ensure_labels(service) -> dict[str, str]:
    """Create all cleanup labels and return {category: label_id}."""
    print("Setting up labels...")
    label_ids = {}
    for category in CATEGORIES:
        disposition = CATEGORY_DISPOSITION.get(category, "keep")
        label_name = f"{GMAIL_LABEL_PREFIX}/{disposition}/{category}"
        label_ids[category] = get_or_create_label(service, label_name)
    return label_ids


# ---------------------------------------------------------------------------
# Message fetching
# ---------------------------------------------------------------------------

def fetch_all_message_ids(service, max_results: int | None, query: str = "") -> list[str]:
    """Fetch all message IDs from the inbox (paginated)."""
    print("Fetching message list from Gmail...")
    ids = []
    kwargs = {"userId": "me", "maxResults": 500}
    if query:
        kwargs["q"] = query
    while True:
        resp = service.users().messages().list(**kwargs).execute()
        batch = resp.get("messages", [])
        ids.extend(m["id"] for m in batch)
        print(f"  Fetched {len(ids)} message IDs so far...")
        if max_results and len(ids) >= max_results:
            ids = ids[:max_results]
            break
        page_token = resp.get("nextPageToken")
        if not page_token:
            break
        kwargs["pageToken"] = page_token
    print(f"Total messages to process: {len(ids)}")
    return ids


def fetch_metadata_batch(service, message_ids: list[str]) -> list[dict]:
    """Fetch sender/subject/snippet for a list of message IDs using batch requests."""
    results = []
    # Gmail batch HTTP requests (up to 100 per batch)
    FETCH_BATCH = 100

    def make_callback(msg_id):
        def callback(request_id, response, exception):
            if exception:
                results.append({"id": msg_id, "sender": "", "subject": "", "snippet": ""})
                return
            headers = {h["name"]: h["value"] for h in response.get("payload", {}).get("headers", [])}
            results.append({
                "id": msg_id,
                "sender": headers.get("From", ""),
                "subject": headers.get("Subject", ""),
                "snippet": response.get("snippet", ""),
            })
        return callback

    for start in range(0, len(message_ids), FETCH_BATCH):
        chunk = message_ids[start : start + FETCH_BATCH]
        batch = service.new_batch_http_request()
        for mid in chunk:
            batch.add(
                service.users().messages().get(
                    userId="me", id=mid, format="metadata",
                    metadataHeaders=["From", "Subject"]
                ),
                callback=make_callback(mid),
            )
        batch.execute()
        print(f"  Fetched metadata for {min(start + FETCH_BATCH, len(message_ids))}/{len(message_ids)} messages...")

    # Re-order to match input order
    order = {mid: i for i, mid in enumerate(message_ids)}
    results.sort(key=lambda x: order.get(x["id"], 0))
    return results


# ---------------------------------------------------------------------------
# Label application
# ---------------------------------------------------------------------------

def apply_labels(service, categorized: dict[str, list[str]], label_ids: dict[str, str]):
    """Apply labels in bulk using batchModify."""
    for category, message_ids in categorized.items():
        if not message_ids:
            continue
        label_id = label_ids[category]
        print(f"  Labeling {len(message_ids)} emails as '{GMAIL_LABEL_PREFIX}/{category}'...")
        # batchModify supports up to 1000 IDs at a time
        MODIFY_BATCH = 1000
        for start in range(0, len(message_ids), MODIFY_BATCH):
            chunk = message_ids[start : start + MODIFY_BATCH]
            service.users().messages().batchModify(
                userId="me",
                body={"ids": chunk, "addLabelIds": [label_id]},
            ).execute()


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def normalize_subject(subject: str) -> str:
    """Strip noise from subjects to find duplicate email types."""
    s = subject.lower()
    s = re.sub(r"\b(re|fwd|fw)\s*:", "", s)           # Re:/Fwd:
    s = re.sub(r"#[\w-]+", "", s)                      # order/ticket numbers like #12345
    s = re.sub(r"\b\d[\d\s,.-]*\b", "", s)             # standalone numbers and dates
    s = re.sub(r"[^\w\s]", " ", s)                     # punctuation
    s = re.sub(r"\s+", " ", s).strip()
    return " ".join(s.split()[:6])                     # first 6 words


def sender_domain(sender: str) -> str:
    match = re.search(r"@([\w.-]+)", sender)
    return match.group(1).lower() if match else sender.lower()


def deduplicate_emails(emails: list[dict], target: int) -> list[dict]:
    """Pick one representative per (sender_domain, normalized_subject) type."""
    seen: dict[tuple, dict] = {}
    for email in emails:
        key = (sender_domain(email["sender"]), normalize_subject(email["subject"]))
        if key not in seen:
            seen[key] = email

    unique = list(seen.values())
    print(f"  Deduplicated {len(emails)} emails → {len(unique)} unique types")

    if len(unique) <= target:
        return unique

    # Spread evenly across sender domains so no single sender dominates
    by_domain: dict[str, list] = defaultdict(list)
    for e in unique:
        by_domain[sender_domain(e["sender"])].append(e)

    result = []
    buckets = list(by_domain.values())
    i = 0
    while len(result) < target and buckets:
        bucket = buckets[i % len(buckets)]
        if bucket:
            result.append(bucket.pop(0))
        if not bucket:
            buckets.pop(i % len(buckets))
        else:
            i += 1
    return result


def export_csv(emails, categories, path="review.csv"):
    with open(path, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["id", "sender", "subject", "snippet", "category", "disposition"])
        for email, category in zip(emails, categories):
            disposition = CATEGORY_DISPOSITION.get(category, "keep")
            writer.writerow([
                email["id"],
                email.get("sender", ""),
                email.get("subject", ""),
                email.get("snippet", "")[:200],
                category,
                disposition,
            ])
    print(f"\nExported {len(emails)} emails to {path}")
    print("Edit the 'disposition' column (keep/remove), then re-import with --import-csv.")


def main():
    parser = argparse.ArgumentParser(description="Classify and label Gmail messages")
    parser.add_argument("--apply", action="store_true", help="Apply labels (default is dry-run)")
    parser.add_argument("--export", action="store_true", help="Export classified emails to review.csv")
    parser.add_argument("--sample", action="store_true", help="Export 200 unique email types from last 3 years for review")
    parser.add_argument("--max", type=int, default=None, help="Max number of emails to process")
    args = parser.parse_args()

    if args.sample:
        print("Mode: SAMPLE — fetching unique email types from last 3 years")
    elif args.export:
        print("Mode: EXPORT to review.csv")
    elif args.apply:
        print("Mode: APPLY (labels will be written to Gmail)")
    else:
        print("Mode: DRY-RUN (use --apply to actually label emails)")

    service = get_gmail_service()

    # Sample mode: fetch diverse unique types from last 3 years
    if args.sample:
        cutoff = (datetime.now() - timedelta(days=3*365)).strftime("%Y/%m/%d")
        query = f"after:{cutoff}"
        print(f"  Fetching up to 5000 emails since {cutoff}...")
        message_ids = fetch_all_message_ids(service, max_results=5000, query=query)
        print("\nFetching email metadata...")
        emails = fetch_metadata_batch(service, message_ids)
        print("\nDeduplicating...")
        emails = deduplicate_emails(emails, target=200)
        print("\nClassifying emails with Claude...")
        categories = classify_all(emails)
        export_csv(emails, categories, path="review.csv")
        return

    # Fetch message IDs
    message_ids = fetch_all_message_ids(service, args.max)
    if not message_ids:
        print("No messages found.")
        return

    # Fetch metadata
    print("\nFetching email metadata...")
    emails = fetch_metadata_batch(service, message_ids)

    # Classify
    print("\nClassifying emails with Claude...")
    categories = classify_all(emails)

    # Group by category
    categorized: dict[str, list[str]] = defaultdict(list)
    for email, category in zip(emails, categories):
        categorized[category].append(email["id"])

    # Summary
    print("\n--- Classification Summary ---")
    for category in CATEGORIES:
        count = len(categorized.get(category, []))
        disposition = CATEGORY_DISPOSITION.get(category, "keep")
        print(f"  {GMAIL_LABEL_PREFIX}/{disposition}/{category}: {count} emails")
    print(f"  Total: {len(emails)} emails")

    if args.export:
        export_csv(emails, categories)
        return

    if not args.apply:
        print("\nDry-run complete. Run with --apply to label emails.")
        return

    # Apply labels
    print("\nApplying labels...")
    label_ids = ensure_labels(service)
    apply_labels(service, categorized, label_ids)
    print("\nDone! Check your Gmail — labels are under '_cleanup/' in the sidebar.")


if __name__ == "__main__":
    main()
