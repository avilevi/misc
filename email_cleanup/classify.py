import json
import os
import anthropic
from dotenv import load_dotenv
from config import CATEGORIES, BATCH_SIZE

load_dotenv()
client = anthropic.Anthropic(api_key=os.environ["ANTHROPIC_API_KEY"])

CATEGORY_LIST = list(CATEGORIES.keys())
CATEGORY_DESCRIPTIONS = "\n".join(f"- {k}: {v}" for k, v in CATEGORIES.items())

SYSTEM_PROMPT = f"""You are an email classifier. Given a list of emails, assign each one to exactly one category.

Categories:
{CATEGORY_DESCRIPTIONS}

Respond with a JSON array of category strings, one per email, in the same order as the input.
Example response for 3 emails: ["newsletters", "personal", "finance"]
Only output the JSON array, nothing else."""


def classify_batch(emails: list[dict]) -> list[str]:
    """
    Classify a batch of emails.
    Each email dict should have: sender, subject, snippet
    Returns a list of category strings in the same order.
    """
    lines = []
    for i, email in enumerate(emails, 1):
        lines.append(
            f"{i}. From: {email.get('sender', 'unknown')} | "
            f"Subject: {email.get('subject', '(no subject)')} | "
            f"Preview: {email.get('snippet', '')[:150]}"
        )
    user_message = "\n".join(lines)

    response = client.messages.create(
        model="claude-haiku-4-5-20251001",
        max_tokens=512,
        system=SYSTEM_PROMPT,
        messages=[{"role": "user", "content": user_message}],
    )

    raw = response.content[0].text.strip()

    # Extract JSON array even if Claude adds extra text
    start = raw.find("[")
    end = raw.rfind("]") + 1
    if start == -1 or end == 0:
        print(f"  Warning: unexpected Claude response: {raw[:200]}")
        return ["misc"] * len(emails)

    categories = json.loads(raw[start:end])

    # Sanitize: fall back to "misc" for unknown values
    return [c if c in CATEGORY_LIST else "misc" for c in categories]


def classify_all(emails: list[dict]) -> list[str]:
    """Classify a large list of emails in batches."""
    results = []
    total = len(emails)
    for start in range(0, total, BATCH_SIZE):
        batch = emails[start : start + BATCH_SIZE]
        end = min(start + BATCH_SIZE, total)
        print(f"  Classifying emails {start + 1}–{end} of {total}...")
        batch_results = classify_batch(batch)
        # Handle size mismatch defensively
        if len(batch_results) != len(batch):
            print(f"  Warning: got {len(batch_results)} results for {len(batch)} emails, padding with 'misc'")
            batch_results += ["misc"] * (len(batch) - len(batch_results))
        results.extend(batch_results)
    return results
