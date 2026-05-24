#!/usr/bin/env bash
set -euo pipefail

Component="all"
BackendUrl="http://meta.ylicloud.com:8080"
DesktopTarget="dist"
MavenSettings=""
MavenCommand=""
OutputDir=""
RemoteDockerDir="/opt/yliyunclaw/docker"
SkipBuild=false

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Options:
  -Component <all|backend|frontend|app>   Component to build (default: all)
  -BackendUrl <url>                       Backend URL for desktop client (default: http://meta.ylicloud.com:8080)
  -DesktopTarget <dist|dist:dmg|pack>     Desktop build target (default: dist)
  -MavenSettings <path>                   Maven settings.xml path
  -MavenCommand <path>                    Maven binary path
  -OutputDir <path>                       Output directory (default: .release/manual-<timestamp>)
  -RemoteDockerDir <path>                 Remote docker directory for server-update.sh (default: /opt/yliyunclaw/docker)
  -SkipBuild                              Skip build and use existing artifacts
EOF
  exit 1
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -Component)
      Component="$2"
      shift 2
      ;;
    -BackendUrl)
      BackendUrl="$2"
      shift 2
      ;;
    -DesktopTarget)
      DesktopTarget="$2"
      shift 2
      ;;
    -MavenSettings)
      MavenSettings="$2"
      shift 2
      ;;
    -MavenCommand)
      MavenCommand="$2"
      shift 2
      ;;
    -OutputDir)
      OutputDir="$2"
      shift 2
      ;;
    -RemoteDockerDir)
      RemoteDockerDir="$2"
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
ServerDir="$RepoRoot/mateclaw-server"
UiDir="$RepoRoot/mateclaw-ui"
DesktopDir="$RepoRoot/mateclaw-desktop"
RuntimeDir="$(cd "$(dirname "$0")" && pwd)/runtime"
Stamp="$(date +%Y%m%d-%H%M%S)"

BuiltFileKeys=()
BuiltFileValues=()

add_built_file() {
  BuiltFileKeys+=("$1")
  BuiltFileValues+=("$2")
}

if [[ -z "$OutputDir" ]]; then
  OutputDir="$RepoRoot/.release/manual-$Stamp"
fi

write_step() {
  echo ""
  echo "==> $1"
}

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

resolve_maven_command() {
  if [[ -n "$MavenCommand" ]]; then
    echo "$MavenCommand"
    return
  fi
  # Check common IntelliJ IDEA paths on macOS
  for idea_path in \
    "/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" \
    "/Applications/IntelliJ IDEA CE.app/Contents/plugins/maven/lib/maven3/bin/mvn" \
    "$HOME/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn"
  do
    if [[ -x "$idea_path" ]]; then
      echo "$idea_path"
      return
    fi
  done
  if command -v mvn &>/dev/null; then
    command -v mvn
    return
  fi
  echo "Maven command not found. Pass -MavenCommand or add mvn to PATH." >&2
  exit 1
}

get_git_commit() {
  if command -v git &>/dev/null && [[ -d "$RepoRoot/.git" ]]; then
    git -C "$RepoRoot" rev-parse --short HEAD 2>/dev/null || true
  fi
}

write_static_build_info() {
  local staticOut="$1"
  local gitCommit
  gitCommit="$(get_git_commit)"
  cat > "$staticOut/release-manifest.json" <<EOF
{
  "app": "Meta Y",
  "packageStamp": "$Stamp",
  "generatedAt": "$(date '+%Y-%m-%d %H:%M:%S %z')",
  "backendUrl": "$BackendUrl",
  "component": "$Component",
  "desktopTarget": "$DesktopTarget",
  "gitCommit": "$gitCommit"
}
EOF
}

build_backend() {
  write_step "Build backend jar"
  if [[ "$SkipBuild" != true ]]; then
    local mvn
    mvn="$(resolve_maven_command)"
    local args=()
    if [[ -n "$MavenSettings" ]]; then
      args+=("-s" "$MavenSettings")
    elif [[ -f "$ServerDir/settings.xml" ]]; then
      args+=("-s" "$ServerDir/settings.xml")
    fi
    args+=("-DskipTests" "-Dmaven.test.skip=true" "clean" "package")

    # Prefer IntelliJ bundled JDK if available
    local idea_java=""
    for jbr_path in \
      "/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home" \
      "/Applications/IntelliJ IDEA CE.app/Contents/jbr/Contents/Home"
    do
      if [[ -d "$jbr_path" ]]; then
        idea_java="$jbr_path"
        break
      fi
    done

    local oldJavaHome="${JAVA_HOME:-}"
    if [[ -n "$idea_java" ]]; then
      export JAVA_HOME="$idea_java"
      export PATH="$JAVA_HOME/bin:$PATH"
    fi

    invoke_checked "$ServerDir" "$mvn" "${args[@]}"

    if [[ -n "$oldJavaHome" ]]; then
      export JAVA_HOME="$oldJavaHome"
    else
      unset JAVA_HOME
    fi
  fi

  local jar
  jar="$(find "$ServerDir/target" -maxdepth 1 -name '*.jar' ! -name '*sources*' ! -name '*javadoc*' ! -name '*.original' -print0 2>/dev/null | \
    xargs -0 ls -t 2>/dev/null | head -n 1)"
  if [[ -z "$jar" ]]; then
    echo "Backend jar not found under mateclaw-server/target."
    exit 1
  fi

  local target="$OutputDir/app.jar"
  cp -f "$jar" "$target"
  add_built_file "app.jar" "$target"
}

build_frontend_static() {
  write_step "Build frontend static"
  local staticOut="$OutputDir/static"
  rm -rf "$staticOut"
  mkdir -p "$staticOut"

  if [[ "$SkipBuild" != true ]]; then
    invoke_checked "$UiDir" pnpm install
    invoke_checked "$UiDir" pnpm exec vite build --outDir "$staticOut" --emptyOutDir
  else
    local existing="$RuntimeDir/static"
    if [[ ! -f "$existing/index.html" ]]; then
      echo "SkipBuild requires existing docker/runtime/static/index.html."
      exit 1
    fi
    cp -R "$existing/"* "$staticOut/"
  fi

  write_static_build_info "$staticOut"

  local archive="$OutputDir/static.tar.gz"
  rm -f "$archive"
  invoke_checked "" tar -czf "$archive" -C "$staticOut" .
  add_built_file "static.tar.gz" "$archive"
}

build_desktop_client() {
  write_step "Build desktop client with default backend $BackendUrl"
  if [[ ! "$BackendUrl" =~ ^https?:// ]]; then
    echo "BackendUrl must start with http:// or https://"
    exit 1
  fi

  if [[ "$SkipBuild" != true ]]; then
    invoke_checked "" bash "$(cd "$(dirname "$0")" && pwd)/package-client-mac.sh" -BackendUrl "$BackendUrl" -Target "$DesktopTarget"
  fi

  local releaseDir="$DesktopDir/release"
  local artifact=""
  local artifactName=""

  # Look for .dmg first
  artifact="$(find "$releaseDir" -maxdepth 1 -name '*.dmg' -print0 2>/dev/null | xargs -0 ls -t 2>/dev/null | head -n 1)"
  if [[ -n "$artifact" ]]; then
    artifactName="MetaY-Desktop.dmg"
  else
    # Look for .app directory (pack target)
    artifact="$(find "$releaseDir" -maxdepth 2 -name '*.app' -type d -print0 2>/dev/null | xargs -0 ls -td 2>/dev/null | head -n 1)"
    if [[ -n "$artifact" ]]; then
      artifactName="MetaY-Desktop.app"
    fi
  fi

  if [[ -z "$artifact" ]]; then
    echo "Desktop client artifact not found under $releaseDir."
    exit 1
  fi

  local target="$OutputDir/$artifactName"
  rm -rf "$target"
  cp -R "$artifact" "$target"
  add_built_file "$artifactName" "$target"
}

write_release_manifest() {
  local manifestPath="$OutputDir/release-manifest.json"
  local gitCommit
  gitCommit="$(get_git_commit)"

  local files_json=""
  for i in "${!BuiltFileKeys[@]}"; do
    local key="${BuiltFileKeys[$i]}"
    local path="${BuiltFileValues[$i]}"
    if [[ -e "$path" ]]; then
      local size
      local sha256
      if [[ -d "$path" ]]; then
        size="$(du -sk "$path" | awk '{print $1 * 1024}')"
        sha256="$(find "$path" -type f -exec shasum -a 256 {} + | shasum -a 256 | awk '{print $1}')"
      else
        size="$(stat -f%z "$path" 2>/dev/null || stat -c%s "$path" 2>/dev/null)"
        sha256="$(shasum -a 256 "$path" | awk '{print $1}')"
      fi
      if [[ -n "$files_json" ]]; then
        files_json="$files_json,"
      fi
      files_json="$files_json
    {
      \"name\": \"$key\",
      \"size\": $size,
      \"sha256\": \"$sha256\"
    }"
    fi
  done

  cat > "$manifestPath" <<EOF
{
  "app": "Meta Y",
  "packageStamp": "$Stamp",
  "generatedAt": "$(date '+%Y-%m-%d %H:%M:%S %z')",
  "backendUrl": "$BackendUrl",
  "component": "$Component",
  "desktopTarget": "$DesktopTarget",
  "gitCommit": "$gitCommit",
  "files": [$files_json
  ]
}
EOF
}

write_server_update_script() {
  local scriptPath="$OutputDir/server-update.sh"
  local readmePath="$OutputDir/README.txt"
  local clientFile="MetaY-Desktop.dmg"
  # Check if we built a .app instead
  if [[ -d "$OutputDir/MetaY-Desktop.app" ]]; then
    clientFile="MetaY-Desktop.app"
  fi

  cat > "$scriptPath" <<EOF
#!/usr/bin/env bash
set -euo pipefail

REMOTE_DOCKER_DIR="\${1:-$RemoteDockerDir}"
PACKAGE_DIR="\$(cd "\$(dirname "\$0")" && pwd)"
STAMP="\$(date +%Y%m%d-%H%M%S)"

cd "\$REMOTE_DOCKER_DIR"
mkdir -p ../backups runtime/app runtime/static runtime/static/downloads

if [ -f "\$PACKAGE_DIR/app.jar" ]; then
  echo "Updating backend jar..."
  if [ -f runtime/app/app.jar ]; then
    cp runtime/app/app.jar "../backups/app-\${STAMP}.jar"
  fi
  cp "\$PACKAGE_DIR/app.jar" runtime/app/app.jar
fi

if [ -f "\$PACKAGE_DIR/static.tar.gz" ]; then
  echo "Updating frontend static files..."
  if [ -d runtime/static ]; then
    tar -czf "../backups/static-\${STAMP}.tar.gz" runtime/static
  fi
  find runtime/static -mindepth 1 -maxdepth 1 ! -name downloads -exec rm -rf {} +
  tar -xzf "\$PACKAGE_DIR/static.tar.gz" -C runtime/static
fi

if [ -e "\$PACKAGE_DIR/$clientFile" ]; then
  echo "Updating desktop client download..."
  cp -R "\$PACKAGE_DIR/$clientFile" runtime/static/downloads/$clientFile
fi

if [ -f "\$PACKAGE_DIR/release-manifest.json" ]; then
  cp "\$PACKAGE_DIR/release-manifest.json" runtime/static/release-manifest.json
fi

docker compose --env-file .env -f docker-compose.runtime.yml restart yliyunclaw-server \\
  || docker-compose --env-file .env -f docker-compose.runtime.yml restart yliyunclaw-server

echo ""
echo "Release manifest:"
cat runtime/static/release-manifest.json 2>/dev/null || true
echo ""
echo "Runtime file status:"
ls -lh runtime/app/app.jar 2>/dev/null || true
ls -lh runtime/static/index.html runtime/static/release-manifest.json runtime/static/downloads/$clientFile 2>/dev/null || true
echo ""
echo "Container status:"
docker compose --env-file .env -f docker-compose.runtime.yml ps yliyunclaw-server \\
  || docker-compose --env-file .env -f docker-compose.runtime.yml ps yliyunclaw-server
echo ""
echo "Health check:"
curl -fsS http://127.0.0.1:18080/actuator/health || true
echo ""
echo "Served frontend verification:"
curl -fsSI http://127.0.0.1:18080/index.html | grep -Ei 'HTTP/|cache-control|etag|last-modified' || true
curl -fsS http://127.0.0.1:18080/release-manifest.json || true
echo ""
curl -fsS http://127.0.0.1:18080/index.html | grep -Eo 'assets/[^"]+\\.(js|css)' | head -20 || true
EOF

  cat > "$readmePath" <<EOF
Meta Y manual release package

Backend URL for desktop client:
$BackendUrl

Local package directory:
$OutputDir

Files:
- app.jar: backend application package
- static.tar.gz: frontend static files
- $clientFile: desktop client download package
- release-manifest.json: package metadata and file hashes
- server-update.sh: run on server after upload

Upload example:
scp -r "$OutputDir" root@192.168.0.50:/opt/yliyunclaw/packages/

Server update example:
ssh root@192.168.0.50
cd /opt/yliyunclaw/packages/$(basename "$OutputDir")
bash server-update.sh $RemoteDockerDir
EOF
}

# ---- Main ----
mkdir -p "$OutputDir"

write_step "Package target: $Component"
echo "Output: $OutputDir"

case "$Component" in
  backend)
    build_backend
    ;;
  frontend)
    build_frontend_static
    ;;
  app)
    build_desktop_client
    ;;
  all)
    build_backend
    build_frontend_static
    build_desktop_client
    ;;
  *)
    echo "Unknown component: $Component"
    exit 1
    ;;
esac

write_release_manifest
write_server_update_script

echo ""
echo "Package completed."
echo "Output: $OutputDir"
echo "Client default backend: $BackendUrl"
