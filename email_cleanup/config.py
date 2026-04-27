CATEGORIES = {
    "newsletters": "Marketing emails, newsletters, promotional content, subscription updates, bulk senders",
    "receipts": "Order confirmations, invoices, shipping notifications, purchase receipts, delivery updates",
    "notifications": "App alerts, GitHub/GitLab activity, social media notifications, automated system emails",
    "finance": "Bank statements, credit card alerts, tax documents, insurance, investment updates, payment confirmations",
    "travel": "Flight bookings, hotel reservations, car rentals, itineraries, boarding passes, Airbnb",
    "work": "Job-related emails, colleagues, recruiters, LinkedIn job alerts, professional correspondence",
    "personal": "Real person-to-person emails from friends, family, or individuals (not automated)",
    "misc": "Anything that doesn't clearly fit the above categories",
}

# Which categories go under "remove" vs "keep"
# Edit this to match your preferences
CATEGORY_DISPOSITION = {
    "newsletters":   "remove",
    "notifications": "remove",
    "receipts":      "keep",    # may want for tax/warranty records
    "finance":       "keep",
    "travel":        "keep",
    "work":          "keep",
    "personal":      "keep",
    "misc":          "keep",
}

GMAIL_LABEL_PREFIX = "_cleanup"
BATCH_SIZE = 50  # emails per Claude API call
