#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="${1:-$SCRIPT_DIR/offline}"
IMAGE_NAME="${YLIYUNCLAW_SERVER_IMAGE:-yliyunclaw-server-runtime:local}"
MYSQL_IMAGE="${MYSQL_IMAGE:-mysql:8.0}"
TAR_PATH="$OUTPUT_DIR/yliyunclaw-runtime-images.tar"

mkdir -p "$OUTPUT_DIR"

docker build -f "$SCRIPT_DIR/Dockerfile.server-runtime" -t "$IMAGE_NAME" "$SCRIPT_DIR"
docker save -o "$TAR_PATH" "$IMAGE_NAME" "$MYSQL_IMAGE"

if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$TAR_PATH" > "$TAR_PATH.sha256"
fi

echo "Offline images exported: $TAR_PATH"
