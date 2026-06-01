# Email Screener

Local, on-demand email screener for **Outlook on macOS**. No online registration, no cloud services. Reads your Inbox via AppleScript, screens emails with an LLM, and writes an action report.

Two LLM backends are supported:
- **GitHub Copilot / GitHub Models** _(default)_ — uses your existing GitHub account, no extra install needed.
- **Ollama** — fully local, no data leaves your machine.

---

## Prerequisites

### 1. Python 3.10+
```bash
python3 --version
```

### 2. Install dependencies
```bash
pip install -r requirements.txt
```

### 3. LLM backend (choose one)

#### Option A — GitHub Copilot / GitHub Models (recommended, no extra install)
Requires a GitHub account with Copilot access. Authenticate once via the GitHub CLI:
```bash
gh auth login
```
That's it. The screener picks up your token automatically.

#### Option B — Local LLM (Ollama, no data leaves your machine)
Download from https://ollama.com and install, then:
```bash
ollama pull llama3      # or any other instruct model
ollama serve            # start the local server (keep running while screening)
```
Set `"llm_provider": "ollama"` in `config.json`, or pass `--provider ollama` at runtime.

#### Option C — No LLM (keyword-based, works completely offline)
Use `--no-llm`. Less precise but requires zero setup.

### 4. Outlook must be open and signed in
The script reads your mailbox via AppleScript. Outlook must be running.

### 5. Allow Automation permission (first run only)
macOS will prompt: *"Terminal wants to control Microsoft Outlook"* — click **OK**.  
If you missed it: **System Settings → Privacy & Security → Automation → Terminal → Microsoft Outlook** → enable.

---

## Usage

```bash
# Screen last 7 days (default from config.json) — uses GitHub Copilot by default
python screen_emails.py

# Custom date range
python screen_emails.py --start 2026-05-01 --end 2026-05-26

# Explicitly choose provider
python screen_emails.py --provider github   # GitHub Copilot / GitHub Models
python screen_emails.py --provider ollama   # local Ollama

# Rule-based only, no LLM needed
python screen_emails.py --start 2026-05-20 --end 2026-05-26 --no-llm

# Use a different model (applies to whichever provider is active)
python screen_emails.py --model gpt-4o
python screen_emails.py --provider ollama --model mistral

# Screen a different folder
python screen_emails.py --folder "Focused"
```

### Output
Reports are saved to `~/email-screener/reports/`:
- `report_<timestamp>.md` — human-readable with urgency badges and action items
- `report_<timestamp>.csv` — spreadsheet-friendly for further analysis

---

## Configuration (`config.json`)

| Key | Default | Description |
|---|---|---|
| `llm_provider` | `"github"` | Active LLM backend: `"github"` or `"ollama"` |
| `github_model` | `"gpt-4o-mini"` | Model to use via GitHub Models API |
| `github_token` | `""` | GitHub token (leave blank to auto-detect from `GITHUB_TOKEN` env or `gh` CLI) |
| `date_range_days` | `7` | Default lookback window when no `--start` given |
| `ollama_url` | `http://localhost:11434` | Ollama server address |
| `ollama_model` | `"llama3"` | Model to use for Ollama screening |
| `confidence_threshold` | `0.6` | Minimum confidence to count in console summary |
| `output_dir` | `~/email-screener/reports` | Where to write reports |
| `max_body_chars` | `3000` | Max email body length sent to LLM |
| `outlook_folder` | `"Inbox"` | Outlook folder name to read |

---

## How it works

1. **AppleScript** queries Outlook locally — no API calls, no credentials stored.
2. **Text cleaning** strips quoted replies, signatures, and noise.
3. **LLM prompt** asks for structured JSON: `needs_action`, `urgency`, `confidence`, `actions[]`.
4. **Report** sorts by urgency + confidence, lists action items per email.

### GitHub token resolution order
1. `github_token` in `config.json`
2. `GITHUB_TOKEN` environment variable
3. `gh auth token` (GitHub CLI)

### Fallback mode (`--no-llm`)
Uses regular expressions to detect action keywords. Less precise but works completely offline and requires zero setup.

---

## Privacy

- With **GitHub Copilot**: email subjects, senders, and bodies (if `outlook_include_body: true`) are sent to GitHub's API. Body fetching is **off by default**.
- With **Ollama**: email content never leaves your machine.
- No data is stored beyond the output report files.

---

## Troubleshooting

**"AppleScript error: folder not found"**  
→ Check `outlook_folder` in `config.json`. The name must match exactly (case-sensitive) as shown in Outlook's sidebar.

**"GitHub token rejected (401)"**  
→ Run `gh auth refresh -s copilot` or ensure your GitHub account has an active Copilot subscription.

**"Cannot connect to Ollama"**  
→ Run `ollama serve` in a separate terminal, or switch to `--provider github`.

**No emails returned for a date range that should have results**  
→ Make sure Outlook is open. Try a wider date range first to confirm AppleScript access is working.

**macOS blocks Automation access**  
→ System Settings → Privacy & Security → Automation → enable Terminal → Microsoft Outlook.
