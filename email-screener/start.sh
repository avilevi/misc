#!/bin/bash
# Start the Email Screener server
# Usage: ./start.sh [port]
# The server binds to 0.0.0.0 so it's reachable from any VPN-connected device.

cd "$(dirname "$0")"

PORT="${1:-5001}"

# Use .venv if present, else system python3
if [ -f ".venv/bin/python3" ]; then
    PYTHON=".venv/bin/python3"
else
    PYTHON="python3"
fi

echo "[*] Starting Email Screener on port $PORT..."
SCREEN_HOST=0.0.0.0 PORT=$PORT $PYTHON screen_app.py
