#!/usr/bin/env bash
set -euo pipefail

TAR_PATH="${1:?Usage: ./load-offline-images.sh /path/to/yliyunclaw-runtime-images.tar}"

docker load -i "$TAR_PATH"
echo "Offline images loaded from: $TAR_PATH"
