# Setup Guide: Health Connect → Google Drive on Android

## What you need to install

| App | Source | Notes |
|-----|--------|-------|
| **Termux** | F-Droid only | Play Store version is outdated and broken |
| **Termux:Tasker** | F-Droid | must match Termux's F-Droid version |
| **Tasker** | Play Store | paid app, v6.2+ required |
| **Health Connect** | Play Store | pre-installed on Android 14+ |

Install Termux and Termux:Tasker from F-Droid first. They must be from the same source or inter-plugin communication breaks.

---

## Part 1 — Termux: Python environment

Open Termux and run these commands:

```bash
pkg update && pkg upgrade -y
pkg install python
pip install google-api-python-client google-auth-oauthlib
termux-setup-storage      # tap "Allow" in the popup that appears
mkdir -p /sdcard/health_sync
```

---

## Part 2 — Google credentials

You need an OAuth client secret for the Drive API. If you already have `client_secret.json` from `google_calendar_thrusters/`, you only need to enable the Drive API on the same project — you don't need a new credential.

1. Go to [console.cloud.google.com](https://console.cloud.google.com)
2. Select your existing project (the one used for `google_calendar_thrusters`)
3. **APIs & Services → Enable APIs & Services** → search **"Google Drive API"** → Enable
4. **APIs & Services → Credentials** → click your existing Desktop OAuth client → **Download JSON**
5. Rename the downloaded file to `credentials.json`

If you create a new project: same steps, but also create a new OAuth client ID (type: **Desktop app**).

---

## Part 3 — Transfer files to Termux

Transfer these three files to your phone:
- `hc_to_drive.py`
- `credentials.json`
- `run_hc_to_drive.sh`

The easiest way: upload them to Google Drive on your computer, then open Google Drive on your phone → download each file to `/sdcard/Download/`.

Then in Termux:

```bash
cp /sdcard/Download/hc_to_drive.py       ~/
cp /sdcard/Download/credentials.json     ~/
cp /sdcard/Download/run_hc_to_drive.sh   ~/
chmod +x ~/run_hc_to_drive.sh
```

---

## Part 4 — First-time Google auth

In Termux:

```bash
python ~/hc_to_drive.py --auth-only
```

It prints a long URL. Open it in Chrome on the phone, sign in with your Google account, click Allow, and copy the authorization code it shows. Paste it back in Termux and press Enter.

This saves `~/hc_drive_token.json`. **Never copy this file to `/sdcard/`** — it's a live credential.

---

## Part 5 — Tasker task

Create a new Tasker task called **"HC to Drive"** with these 4 actions in order:

**Action 1 — Read exercise sessions**
- Category: **Health Connect** (under Third Party)
- Operation: **Read Records**
- Record Type: **ExerciseSession**
- Start Time: `-90d` (or however far back you want)
- End Time: *(leave blank for now)*
- Output Variable: `%HC_EXERCISES`

**Action 2 — Read sleep sessions**
- Same as above but:
- Record Type: **SleepSession**
- Output Variable: `%HC_SLEEP`

**Action 3 — Format & write JSON**
- Category: **Code → JavaScript**
- Paste the full contents of `tasker_hc_query.js`
  (or copy the file to your phone and use "From File" if Tasker supports it)

**Action 4 — Run the Python script**
- Category: **Plugin → Termux:Tasker**
- Configuration:
  - Executable: `bash`
  - Arguments: `/data/data/com.termux/files/home/run_hc_to_drive.sh`
  - Working Dir: `/data/data/com.termux/files/home`

---

## Part 6 — Grant permissions

Tasker needs Health Connect read permissions:

1. In Tasker → the HC task → long-press Action 1 → "Request permission" (if shown)
2. Or: Android Settings → Apps → Tasker → Permissions → Health Connect → allow **Exercise Sessions** + **Sleep Sessions**

Termux:Tasker also needs to be unlocked:

1. In Termux: `pkg install termux-tasker` (if not already)
2. Android Settings → Apps → Termux → Battery → **Unrestricted** (prevents process kills)

---

## Part 7 — Test it

Run the Tasker task manually by tapping the play button. After it finishes:

```bash
# Check the data file was created
cat /sdcard/health_sync/data.json | head -30

# Check the log
cat /sdcard/health_sync/last_sync.txt

# Check the manifest (should have entries after a successful run)
cat /sdcard/health_sync/drive_manifest.json | head -20
```

If the data file exists but looks empty (`{"exercises":[],"sleep_sessions":[]}`), the Health Connect permissions for Tasker aren't granted yet — go back to Part 6.

---

## Part 8 — Automate (optional)

Once the manual run works, create a Tasker **Profile** that triggers the task on a schedule:
- **Event: Date/Time** → Repeat every 1 day at a fixed time (e.g., 08:00)
- Link it to the "HC to Drive" task

Or trigger it manually from the Tasker widget whenever you want to sync.

---

## File layout on your phone after setup

```
~/                              (Termux home, private)
  hc_to_drive.py
  credentials.json
  hc_drive_token.json           (auto-created on first auth)
  run_hc_to_drive.sh

/sdcard/health_sync/            (shared with Tasker)
  data.json                     (written by Tasker JS, overwritten each run)
  drive_manifest.json           (written by hc_to_drive.py, accumulates over time)
  last_sync.txt                 (log from last run)
```

The only file the Python script never touches is `data.json` — that's entirely Tasker's job.
