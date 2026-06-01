# Email Screener — Remote Deployment Guide

## Architecture

```
┌─────────────────────────────────────────────────┐
│  Mac (your machine)                              │
│  ┌─────────────────────────────────────┐         │
│  │  mac_agent.py  (port 5002)          │         │
│  │  • Runs AppleScript on Outlook      │         │
│  │  • Serves email data over HTTP      │         │
│  │  • Opens emails in Outlook locally  │         │
│  └──────────────────┬──────────────────┘         │
└─────────────────────┼───────────────────────────-┘
                      │ HTTP (VPN)
┌─────────────────────┼───────────────────────────-┐
│  Ubuntu Server      ▼                            │
│  ┌─────────────────────────────────────┐         │
│  │  screen_app.py  (port 5001)         │         │
│  │  • Web UI                           │         │
│  │  • LLM screening (GitHub Copilot)   │         │
│  │  • SQLite storage (emails.db)       │         │
│  │  • Email viewer for remote clients  │         │
│  └─────────────────────────────────────┘         │
└──────────────────────────────────────────────────┘
         ▲
         │  Browser (any VPN-connected device)
```

---

## Mac Setup

### 1. Install dependencies
```bash
pip3 install flask requests
```

### 2. Configure `config.json`
Edit `config.json` in the project directory:
```json
{
  "mac_agent_token": "your-secret-token-here",
  "mac_agent_port": 5002
}
```
Pick any strong random string for the token.

### 3. Test the agent manually
```bash
cd /path/to/email-screener
python3 mac_agent.py
```
Check: `curl http://localhost:5002/health`

### 4. Install LaunchAgent (auto-start on login)
```bash
# Edit the plist — replace REPLACE_WITH_FULL_PATH with the real path
PROJECT_DIR=$(pwd)
sed "s|REPLACE_WITH_FULL_PATH|$PROJECT_DIR|g" \
    deploy/mac_launchagent.plist \
    > ~/Library/LaunchAgents/com.emailscreener.macagent.plist

# Load it now
launchctl load ~/Library/LaunchAgents/com.emailscreener.macagent.plist

# Verify it started
launchctl list | grep emailscreener
curl http://localhost:5002/health
```

### 5. Firewall — allow port 5002 from Ubuntu server only
macOS Firewall is application-based, so restrict by IP using `pf` or a VPN policy.
At minimum, make sure your VPN is the only route to reach this port.

---

## Ubuntu Server Setup

### 1. Clone the project
```bash
git clone <your-repo> email-screener
cd email-screener
```

### 2. Install dependencies
```bash
pip3 install flask requests
```

### 3. Configure `config.json`
```json
{
  "mac_agent_url": "http://<mac-vpn-ip>:5002",
  "mac_agent_token": "your-secret-token-here",
  "mac_ip": "<mac-vpn-ip>",
  "llm_provider": "github",
  "github_model": "gpt-4o-mini",
  "user_name": "Avi"
}
```

### 4. Get a GitHub Copilot token
```bash
gh auth login
gh auth refresh -s copilot
gh auth token   # copy this token
```
Set it as environment variable:
```bash
export GITHUB_TOKEN=$(gh auth token)
```

### 5. Test connectivity to Mac Agent
```bash
curl -H "Authorization: Bearer your-secret-token-here" \
     http://<mac-vpn-ip>:5002/health
```

### 6. Start the server
```bash
python3 screen_app.py
# Accessible at http://<ubuntu-vpn-ip>:5001
```

### 7. (Optional) Run as a systemd service
```ini
# /etc/systemd/system/emailscreener.service
[Unit]
Description=Email Screener Web App
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

---

## How "Open Email" works per client

| Client          | Behavior                                              |
|-----------------|-------------------------------------------------------|
| Mac browser     | Calls Mac Agent → opens in Outlook directly           |
| Other devices   | Opens `/email/<id>` in new tab — full email viewer    |

The server detects the client type by comparing `request.remote_addr` to `mac_ip` in config.

---

## Security notes
- The agent token is a shared secret — keep it out of version control
- Only expose port 5002 (Mac) and 5001 (Ubuntu) on your VPN interface
- The email viewer renders email HTML — it uses Flask's `| safe` filter. If emails contain malicious HTML, consider adding a sanitizer (e.g., `bleach`) on the server
