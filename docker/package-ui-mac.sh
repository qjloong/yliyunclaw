#!/usr/bin/env bash
set -euo pipefail

OutDir=""
SkipBuild=false

usage() {
  cat <<EOF
Usage: $(basename "$0") [-OutDir <dir>] [-SkipBuild]

  -OutDir <dir>     Output directory for built static files (default: mateclaw-ui/dist)
  -SkipBuild        Skip the build and use existing output if present
EOF
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -OutDir)
      OutDir="$2"
      shift 2
      ;;
    -SkipBuild)
      SkipBuild=true
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

RepoRoot="$(cd "$(dirname "$0")/.." && pwd)"
UiDir="$RepoRoot/mateclaw-ui"

if [[ -z "$OutDir" ]]; then
  OutDir="$UiDir/dist"
fi

if ! command -v pnpm &>/dev/null; then
  echo "Error: pnpm is required to package the UI."
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

write_build_info() {
  local staticOut="$1"
  local stamp
  stamp="$(date +%Y%m%d-%H%M%S)"
  local gitCommit=""
  if command -v git &>/dev/null && [[ -d "$RepoRoot/.git" ]]; then
    gitCommit="$(git -C "$RepoRoot" rev-parse --short HEAD 2>/dev/null || true)"
  fi

  cat > "$staticOut/release-manifest.json" <<EOF
{
  "app": "Meta Y",
  "packageStamp": "$stamp",
  "generatedAt": "$(date '+%Y-%m-%d %H:%M:%S %z')",
  "component": "frontend",
  "gitCommit": "$gitCommit"
}
EOF
}

echo "==> Build frontend static"
echo "Output: $OutDir"

if [[ "$SkipBuild" == true ]]; then
  if [[ ! -f "$OutDir/index.html" ]]; then
    echo "Error: SkipBuild requires existing index.html in $OutDir"
    exit 1
  fi
  echo "Skipping build, using existing $OutDir"
else
  rm -rf "$OutDir"
  mkdir -p "$OutDir"
  invoke_checked "$UiDir" pnpm install
  invoke_checked "$UiDir" pnpm exec vite build --outDir "$OutDir" --emptyOutDir
fi

write_build_info "$OutDir"

echo ""
echo "UI package completed."
echo "Output: $OutDir"
