param(
  [ValidateSet("all", "backend", "frontend", "app")]
  [string]$Component = "all",

  [string]$BackendUrl = "http://meta.ylicloud.com:8080",

  [ValidateSet("dist", "dist:nsis", "dist:portable", "pack")]
  [string]$DesktopTarget = "dist:portable",

  [string]$MavenSettings = "",
  [string]$MavenCommand = "",
  [string]$OutputDir = "",
  [string]$RemoteDockerDir = "/opt/yliyunclaw/docker",
  [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$ServerDir = Join-Path $RepoRoot "mateclaw-server"
$UiDir = Join-Path $RepoRoot "mateclaw-ui"
$DesktopDir = Join-Path $RepoRoot "mateclaw-desktop"
$RuntimeDir = Join-Path $PSScriptRoot "runtime"
$Stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$BuiltFiles = [ordered]@{}

if (-not $OutputDir) {
  $OutputDir = Join-Path $RepoRoot ".release\manual-$Stamp"
}

function Write-Step([string]$Message) {
  Write-Host ""
  Write-Host "==> $Message" -ForegroundColor Cyan
}

function Invoke-CheckedCommand([string]$File, [string[]]$Arguments, [string]$WorkingDirectory = "") {
  $display = "$File $($Arguments -join ' ')"
  if ($WorkingDirectory) {
    Write-Host "[$WorkingDirectory] $display"
    Push-Location $WorkingDirectory
  } else {
    Write-Host $display
  }
  $oldErrorActionPreference = $ErrorActionPreference
  try {
    $ErrorActionPreference = "Continue"
    $output = & $File @Arguments 2>&1
    $exitCode = $LASTEXITCODE
    if ($output) {
      $output | ForEach-Object { Write-Host $_ }
    }
    if ($exitCode -ne 0) {
      throw "Command failed with exit code ${exitCode}: $display"
    }
  } finally {
    $ErrorActionPreference = $oldErrorActionPreference
    if ($WorkingDirectory) { Pop-Location }
  }
}

function Resolve-MavenCommand {
  if ($MavenCommand) { return $MavenCommand }
  $ideaMaven = "D:\program\IntelliJ IDEA 2025.1.3\plugins\maven\lib\maven3\bin\mvn.cmd"
  if (Test-Path $ideaMaven) { return $ideaMaven }
  $cmd = Get-Command mvn -ErrorAction SilentlyContinue
  if ($cmd) { return $cmd.Source }
  throw "Maven command not found. Pass -MavenCommand or add mvn to PATH."
}

function Get-GitCommit {
  try {
    $commit = (& git -C $RepoRoot rev-parse --short HEAD 2>$null)
    if ($commit) { return "$commit" }
  } catch {
    return ""
  }
  return ""
}

function Write-StaticBuildInfo([string]$StaticOut) {
  $info = [ordered]@{
    app = "Meta Y"
    packageStamp = $Stamp
    generatedAt = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss zzz")
    backendUrl = $BackendUrl
    component = $Component
    desktopTarget = $DesktopTarget
    gitCommit = Get-GitCommit
  }
  $json = $info | ConvertTo-Json -Depth 4
  $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
  [System.IO.File]::WriteAllText((Join-Path $StaticOut "release-manifest.json"), ($json -replace "`r`n", "`n"), $utf8NoBom)
}

function Build-Backend {
  Write-Step "Build backend jar"
  if (-not $SkipBuild) {
    $mvn = Resolve-MavenCommand
    $args = @()
    if ($MavenSettings) {
      $args += @("-s", $MavenSettings)
    } elseif (Test-Path (Join-Path $ServerDir "settings.xml")) {
      $args += @("-s", (Join-Path $ServerDir "settings.xml"))
    }
    $args += @("-DskipTests", "-Dmaven.test.skip=true", "clean", "package")

    $ideaJava = "D:\program\IntelliJ IDEA 2025.1.3\jbr"
    $oldJavaHome = $env:JAVA_HOME
    $oldPath = $env:Path
    if (Test-Path $ideaJava) {
      $env:JAVA_HOME = $ideaJava
      $env:Path = "$env:JAVA_HOME\bin;$env:Path"
    }
    try {
      Invoke-CheckedCommand -File $mvn -Arguments $args -WorkingDirectory $ServerDir
    } finally {
      $env:JAVA_HOME = $oldJavaHome
      $env:Path = $oldPath
    }
  }

  $jar = Get-ChildItem -Path (Join-Path $ServerDir "target") -Filter "*.jar" |
    Where-Object { $_.Name -notmatch "sources|javadoc|\.original$" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
  if (-not $jar) { throw "Backend jar not found under mateclaw-server\target." }

  $target = Join-Path $OutputDir "app.jar"
  Copy-Item -LiteralPath $jar.FullName -Destination $target -Force
  $script:BuiltFiles["app.jar"] = $target
  return $target
}

function Build-FrontendStatic {
  Write-Step "Build frontend static"
  $staticOut = Join-Path $OutputDir "static"
  Remove-Item -LiteralPath $staticOut -Recurse -Force -ErrorAction SilentlyContinue
  New-Item -ItemType Directory -Force -Path $staticOut | Out-Null
  if (-not $SkipBuild) {
    Invoke-CheckedCommand -File "pnpm" -Arguments @("install") -WorkingDirectory $UiDir
    Invoke-CheckedCommand -File "pnpm" -Arguments @("exec", "vite", "build", "--outDir", $staticOut, "--emptyOutDir") -WorkingDirectory $UiDir
  } else {
    $existing = Join-Path $RuntimeDir "static"
    if (-not (Test-Path (Join-Path $existing "index.html"))) {
      throw "SkipBuild requires existing docker\runtime\static\index.html."
    }
    Copy-Item -Path (Join-Path $existing "*") -Destination $staticOut -Recurse -Force
  }

  Write-StaticBuildInfo $staticOut

  $archive = Join-Path $OutputDir "static.tar.gz"
  if (Test-Path $archive) { Remove-Item -LiteralPath $archive -Force }
  Invoke-CheckedCommand -File "tar" -Arguments @("-czf", $archive, "-C", $staticOut, ".")
  $script:BuiltFiles["static.tar.gz"] = $archive
  return $archive
}

function Build-DesktopClient {
  Write-Step "Build desktop client with default backend $BackendUrl"
  if ($BackendUrl -notmatch "^https?://") {
    throw "BackendUrl must start with http:// or https://"
  }
  if (-not $SkipBuild) {
    Invoke-CheckedCommand -File "powershell" -Arguments @("-NoProfile", "-ExecutionPolicy", "Bypass", "-File", (Join-Path $PSScriptRoot "package-client.ps1"), "-BackendUrl", $BackendUrl, "-Target", $DesktopTarget)
  }

  $releaseDir = Join-Path $DesktopDir "release"
  $exe = Get-ChildItem -Path $releaseDir -File -Filter "*.exe" -ErrorAction SilentlyContinue |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
  if (-not $exe) { throw "Desktop client exe not found under $releaseDir." }

  $target = Join-Path $OutputDir "MetaY-Desktop.exe"
  Copy-Item -LiteralPath $exe.FullName -Destination $target -Force
  $script:BuiltFiles["MetaY-Desktop.exe"] = $target
  return $target
}

function Write-ReleaseManifest {
  $manifestPath = Join-Path $OutputDir "release-manifest.json"
  $gitCommit = Get-GitCommit
  $files = @()
  foreach ($entry in $script:BuiltFiles.GetEnumerator()) {
    if (Test-Path $entry.Value) {
      $item = Get-Item -LiteralPath $entry.Value
      $hash = Get-FileHash -LiteralPath $entry.Value -Algorithm SHA256
      $files += [ordered]@{
        name = $entry.Key
        size = $item.Length
        sha256 = $hash.Hash.ToLowerInvariant()
      }
    }
  }
  $manifest = [ordered]@{
    app = "Meta Y"
    packageStamp = $Stamp
    generatedAt = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss zzz")
    backendUrl = $BackendUrl
    component = $Component
    desktopTarget = $DesktopTarget
    gitCommit = $gitCommit
    files = $files
  }
  $json = $manifest | ConvertTo-Json -Depth 5
  $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
  [System.IO.File]::WriteAllText($manifestPath, ($json -replace "`r`n", "`n"), $utf8NoBom)
}

function Write-ServerUpdateScript {
  $scriptPath = Join-Path $OutputDir "server-update.sh"
  $readmePath = Join-Path $OutputDir "README.txt"

  $serverScript = @'
#!/usr/bin/env bash
set -euo pipefail

REMOTE_DOCKER_DIR="${1:-__REMOTE_DOCKER_DIR__}"
PACKAGE_DIR="$(cd "$(dirname "$0")" && pwd)"
STAMP="$(date +%Y%m%d-%H%M%S)"

cd "$REMOTE_DOCKER_DIR"
mkdir -p ../backups runtime/app runtime/static runtime/static/downloads

if [ -f "$PACKAGE_DIR/app.jar" ]; then
  echo "Updating backend jar..."
  if [ -f runtime/app/app.jar ]; then
    cp runtime/app/app.jar "../backups/app-${STAMP}.jar"
  fi
  cp "$PACKAGE_DIR/app.jar" runtime/app/app.jar
fi

if [ -f "$PACKAGE_DIR/static.tar.gz" ]; then
  echo "Updating frontend static files..."
  if [ -d runtime/static ]; then
    tar -czf "../backups/static-${STAMP}.tar.gz" runtime/static
  fi
  find runtime/static -mindepth 1 -maxdepth 1 ! -name downloads -exec rm -rf {} +
  tar -xzf "$PACKAGE_DIR/static.tar.gz" -C runtime/static
fi

if [ -f "$PACKAGE_DIR/MetaY-Desktop.exe" ]; then
  echo "Updating desktop client download..."
  cp "$PACKAGE_DIR/MetaY-Desktop.exe" runtime/static/downloads/MetaY-Desktop.exe
fi

if [ -f "$PACKAGE_DIR/release-manifest.json" ]; then
  cp "$PACKAGE_DIR/release-manifest.json" runtime/static/release-manifest.json
fi

docker compose --env-file .env -f docker-compose.runtime.yml restart yliyunclaw-server \
  || docker-compose --env-file .env -f docker-compose.runtime.yml restart yliyunclaw-server

echo ""
echo "Release manifest:"
cat runtime/static/release-manifest.json 2>/dev/null || true
echo ""
echo "Runtime file status:"
ls -lh runtime/app/app.jar 2>/dev/null || true
ls -lh runtime/static/index.html runtime/static/release-manifest.json runtime/static/downloads/MetaY-Desktop.exe 2>/dev/null || true
echo ""
echo "Container status:"
docker compose --env-file .env -f docker-compose.runtime.yml ps yliyunclaw-server \
  || docker-compose --env-file .env -f docker-compose.runtime.yml ps yliyunclaw-server
echo ""
echo "Health check:"
curl -fsS http://127.0.0.1:18080/actuator/health || true
echo ""
echo "Served frontend verification:"
curl -fsSI http://127.0.0.1:18080/index.html | grep -Ei 'HTTP/|cache-control|etag|last-modified' || true
curl -fsS http://127.0.0.1:18080/release-manifest.json || true
echo ""
curl -fsS http://127.0.0.1:18080/index.html | grep -Eo 'assets/[^"]+\.(js|css)' | head -20 || true
'@

  $readme = @"
Meta Y manual release package

Backend URL for desktop client:
$BackendUrl

Local package directory:
$OutputDir

Files:
- app.jar: backend application package
- static.tar.gz: frontend static files
- MetaY-Desktop.exe: desktop client download package
- release-manifest.json: package metadata and file hashes
- server-update.sh: run on server after upload

Upload example:
scp -r "$OutputDir" root@192.168.0.50:/opt/yliyunclaw/packages/

Server update example:
ssh root@192.168.0.50
cd /opt/yliyunclaw/packages/$(Split-Path -Leaf $OutputDir)
bash server-update.sh $RemoteDockerDir
"@

  $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
  $serverScript = $serverScript.Replace("__REMOTE_DOCKER_DIR__", $RemoteDockerDir)
  [System.IO.File]::WriteAllText($scriptPath, ($serverScript -replace "`r`n", "`n"), $utf8NoBom)
  [System.IO.File]::WriteAllText($readmePath, ($readme -replace "`r`n", "`n"), $utf8NoBom)
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

Write-Step "Package target: $Component"
Write-Host "Output: $OutputDir"

switch ($Component) {
  "backend" { Build-Backend | Out-Null }
  "frontend" { Build-FrontendStatic | Out-Null }
  "app" { Build-DesktopClient | Out-Null }
  "all" {
    Build-Backend | Out-Null
    Build-FrontendStatic | Out-Null
    Build-DesktopClient | Out-Null
  }
}

Write-ReleaseManifest
Write-ServerUpdateScript

Write-Host ""
Write-Host "Package completed." -ForegroundColor Green
Write-Host "Output: $OutputDir"
Write-Host "Client default backend: $BackendUrl"
