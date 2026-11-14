#!/usr/bin/env bash
set -euo pipefail
: "${1:?Usage: build.sh /path/to/ffmpeg-x64.dll}"
host="$(cd -- "$(dirname -- "$1")" && pwd)/$(basename -- "$1")"
gradle -p "$(dirname -- "$0")/.." --no-daemon "-PsaltPlayerArchive=$host" plugin
