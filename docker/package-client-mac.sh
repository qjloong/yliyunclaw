#!/usr/bin/env bash
set -euo pipefail

BackendUrl=""
Target="dist"
NoStopRunningClient=false

usage() {
  cat <<EOF
Usage: $(basename "$0") -BackendUrl <url> [-Target <dist|dist:dmg|pack>] [-NoStopRunningClient]

  -BackendUrl <url>          Required. Backend URL (must start with http:// or https://)
  -Target <target>           Build target: dist (default), dist:dmg, pack
  -NoStopRunningClient       Do not kill running Meta Y Desktop before packaging
EOF
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -BackendUrl)
      BackendUrl="$2"
      shift 2
      ;;
    -Target)
      Target="$2"
      shift 2
      ;;
    -NoStopRunningClient)
      NoStopRunningClient=true
      shift
      ;;
    -h|--help)
      usage
      ;;
    *)
      echo "Unknown option: $1"
      usage
      ;;
  esac
done

if [[ -z "$BackendUrl" ]]; then
  echo "Error: -BackendUrl is required."
  usage
fi

if [[ ! "$BackendUrl" =~ ^https?:// ]]; then
  echo "Error: BackendUrl must start with http:// or https://"
  exit 1
fi

RepoRoot="$(cd "$(dirname "$0")/.." && pwd)"
DesktopDir="$RepoRoot/mateclaw-desktop"
ConfigFile="$DesktopDir/electron/desktopConfig.cjs"

if ! command -v pnpm &>/dev/null; then
  echo "Error: pnpm is required to package the client."
  exit 1
fi

invoke_checked() {
  local wd="${1:-}"
  shift
  if [[ -n "$wd" ]]; then
    echo "[$wd] $*"
    pushd "$wd" >/dev/null
  else
    echo "$*"
  fi
  "$@"
  local exitCode=$?
  if [[ -n "$wd" ]]; then
    popd >/dev/null
  fi
  if [[ $exitCode -ne 0 ]]; then
    echo "Command failed with exit code $exitCode: $*"
    exit 1
  fi
}

stop_running_client() {
  if [[ "$NoStopRunningClient" == true ]]; then
    return
  fi

  local stopped=false
  for name in "Meta Y Desktop" "MetaY-Desktop" "MetaY Desktop" "electron" "crashpad_handler"; do
    if pgrep -x "$name" &>/dev/null || pgrep -f "$name" &>/dev/null; then
      echo "Stopping running desktop client process: $name"
      pkill -x "$name" 2>/dev/null || pkill -f "$name" 2>/dev/null || true
      stopped=true
    fi
  done

  if [[ "$stopped" == true ]]; then
    sleep 2
  fi
}

remove_dir_with_retry() {
  local path="$1"
  if [[ ! -e "$path" ]]; then
    return
  fi
  for i in 1 2 3 4 5 6 7 8; do
    if rm -rf "$path" 2>/dev/null; then
      return
    fi
    if [[ $i -eq 8 ]]; then
      echo "Failed to clean '$path'. Close Meta Y Desktop and retry."
      exit 1
    fi
    sleep 3
  done
}

ensure_desktop_dependencies() {
  invoke_checked "$DesktopDir" pnpm install
}

ensure_electron_runtime() {
  local electronApp="$DesktopDir/node_modules/electron/dist/Electron.app"
  if [[ -d "$electronApp" ]]; then
    return
  fi

  echo "Electron runtime is missing. Downloading packaged Electron binary..."
  invoke_checked "$DesktopDir" node node_modules/electron/install.js

  if [[ ! -d "$electronApp" ]]; then
    echo "Electron runtime download did not produce '$electronApp'."
    exit 1
  fi
}

# Backup original config
original="$(cat "$ConfigFile")"

# Use Python to safely escape and patch the config file
if ! python3 -c "
import sys, re
content = sys.stdin.read()
pattern = r\"return isPackaged \? '[^']+' : 'http://127\.0\.0\.1:18088';\"
replacement = \"return isPackaged ? '\" + sys.argv[1].replace(\"'\", \"\\\\'\") + \"' : 'http://127.0.0.1:18088';\"
if not re.search(pattern, content):
    sys.stderr.write('Could not find the packaged default backend URL in desktopConfig.cjs.\n')
    sys.exit(1)
new_content = re.sub(pattern, replacement, content)
sys.stdout.write(new_content)
" "$BackendUrl" < "$ConfigFile" > "$ConfigFile.tmp"; then
  echo "Could not find the packaged default backend URL in desktopConfig.cjs."
  exit 1
fi
mv "$ConfigFile.tmp" "$ConfigFile"

# Restore config on exit
cleanup() {
  echo "$original" > "$ConfigFile"
}
trap cleanup EXIT

stop_running_client
remove_dir_with_retry "$DesktopDir/release"
remove_dir_with_retry "$DesktopDir/renderer-dist"
ensure_desktop_dependencies
ensure_electron_runtime

# Build renderer first
invoke_checked "$DesktopDir" pnpm run build:renderer

# Package for macOS
case "$Target" in
  dist)
    invoke_checked "$DesktopDir" npx electron-builder --mac
    ;;
  dist:dmg)
    invoke_checked "$DesktopDir" npx electron-builder --mac dmg
    ;;
  pack)
    invoke_checked "$DesktopDir" npx electron-builder --mac --dir
    ;;
  *)
    echo "Unknown target: $Target"
    exit 1
    ;;
esac

echo "Client package created in: $DesktopDir/release"
