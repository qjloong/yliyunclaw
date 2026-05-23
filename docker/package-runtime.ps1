param(
  [string]$JarPath = "",
  [string]$StaticPath = ""
)

$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$runtimeDir = Join-Path $PSScriptRoot "runtime"
$appDir = Join-Path $runtimeDir "app"
$staticDir = Join-Path $runtimeDir "static"

New-Item -ItemType Directory -Force -Path $appDir | Out-Null
New-Item -ItemType Directory -Force -Path $staticDir | Out-Null

if ($JarPath) {
  Copy-Item -LiteralPath $JarPath -Destination (Join-Path $appDir "app.jar") -Force
} else {
  Push-Location (Join-Path $root "mateclaw-server")
  try {
    mvn package -DskipTests -Dmaven.test.skip=true
    $jar = Get-ChildItem -Path "target" -Filter "*.jar" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $jar) { throw "Backend jar not found. Please check Maven build output." }
    Copy-Item -LiteralPath $jar.FullName -Destination (Join-Path $appDir "app.jar") -Force
  } finally {
    Pop-Location
  }
}

if ($StaticPath) {
  Remove-Item -LiteralPath $staticDir -Recurse -Force -ErrorAction SilentlyContinue
  New-Item -ItemType Directory -Force -Path $staticDir | Out-Null
  Copy-Item -Path (Join-Path $StaticPath "*") -Destination $staticDir -Recurse -Force
} else {
  Push-Location (Join-Path $root "mateclaw-ui")
  try {
    pnpm exec vite build --outDir (Resolve-Path $staticDir).Path --emptyOutDir
  } finally {
    Pop-Location
  }
}

Write-Host "Runtime package prepared:"
Write-Host "  jar:    $appDir\app.jar"
Write-Host "  static: $staticDir"
