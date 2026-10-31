#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
: "${1:?Usage: build.sh /path/to/ffmpeg-x64.dll}"
gradle --no-daemon "-PsaltPlayerArchive=$1" plugin
