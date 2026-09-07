#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)/app/src/main/assets"
PORT="${1:-8765}"
command -v python >/dev/null 2>&1 || { echo "Install Python first: pkg install python"; exit 1; }
echo "Faithbook V19 browser preview"
echo "Serving: $ROOT"
echo "Open: http://127.0.0.1:$PORT/index.html"
echo "Press Ctrl+C to stop."
cd "$ROOT"
python -m http.server "$PORT" --bind 127.0.0.1
