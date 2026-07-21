#!/bin/bash
# Get the directory of the script
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$DIR"

if [ ! -d ".venv" ]; then
    echo "Creating virtual environment..."
    python3 -m venv .venv
    echo "Installing requirements..."
    .venv/bin/pip install -r requirements.txt
fi

echo "Starting Logcat WebSocket Server..."
.venv/bin/python server.py "$@"
