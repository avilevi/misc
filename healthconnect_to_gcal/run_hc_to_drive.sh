#!/data/data/com.termux/files/usr/bin/bash
# Called by Tasker via Termux:Tasker after tasker_hc_query.js writes data.json.
# Place this file at: /data/data/com.termux/files/home/run_hc_to_drive.sh
# Make executable: chmod +x ~/run_hc_to_drive.sh

python /data/data/com.termux/files/home/hc_to_drive.py \
    --timezone Asia/Jerusalem \
    "$@"
