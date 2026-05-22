param(
  [Parameter(Mandatory = $true)]
  [string]$BackendUrl,

  [ValidateSet("dist", "dist:nsis", "dist:portable", "pack")]
  [string]$Target = "dist",

  [switch]$NoStopRunningClient
)

$ErrorActionPreference = "Stop"
$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$DesktopDir = Join-Path $RepoRoot "mateclaw-desktop"
$ConfigFile = Join-Path $DesktopDir "electron\desktopConfig.cjs"

if (-not (Get-Command pnpm -ErrorAction SilentlyContinue)) {
  throw "pnpm is required to package the client."
}

if ($BackendUrl -notmatch "^https?://") {
  throw "BackendUrl must start with http:// or https://"
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

function Stop-RunningDesktopClient {
  if ($NoStopRunningClient) { return }

  $releasePrefix = (Join-Path $DesktopDir "release").ToLowerInvariant()
  $knownProcessNames = @(
    "Meta Y Desktop",
    "MetaY-Desktop",
    "MetaY Desktop",
    "electron",
    "crashpad_handler"
  )
  $candidates = Get-Process -ErrorAction SilentlyContinue | Where-Object {
    $path = $null
    try { $path = $_.Path } catch { $path = $null }
    if ($_.ProcessName -in $knownProcessNames) {
      return $true
    }
    if (-not $path) { return $false }
    $lowerPath = $path.ToLowerInvariant()
    return $lowerPath.StartsWith($releasePrefix)
  }

  foreach ($process in $candidates) {
    Write-Host "Stopping running desktop client process $($process.ProcessName) ($($process.Id))"
    Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
  }

  if ($candidates) {
    Start-Sleep -Seconds 2
  }
}

function Remove-DirectoryWithRetry([string]$Path) {
  if (-not (Test-Path $Path)) { return }
  for ($i = 1; $i -le 8; $i++) {
    try {
      Remove-Item -LiteralPath $Path -Recurse -Force -ErrorAction Stop
      return
    } catch {
      if ($i -eq 8) {
        throw "Failed to clean '$Path'. Close Meta Y Desktop, Explorer preview windows, and retry. Original error: $($_.Exception.Message)"
      }
      Start-Sleep -Seconds 3
    }
  }
}

function Get-BrokenNodeModuleLinks([string]$Path) {
  if (-not (Test-Path $Path)) {
    return @()
  }

  return @(Get-ChildItem -LiteralPath $Path -Recurse -Force -Attributes ReparsePoint -ErrorAction SilentlyContinue | Where-Object {
    $targets = @($_.Target | Where-Object { $_ })
    if (-not $targets.Count) {
      return $false
    }
    return @($targets | Where-Object { -not (Test-Path $_) }).Count -gt 0
  })
}

function Ensure-DesktopDependencies {
  $virtualStorePath = Join-Path $DesktopDir "node_modules\.pnpm\node_modules"
  $brokenLinks = Get-BrokenNodeModuleLinks $virtualStorePath

  if ($brokenLinks.Count -gt 0) {
    Write-Host "Detected broken pnpm links in node_modules. Reinstalling desktop dependencies..."
    $brokenLinks | Select-Object -First 5 | ForEach-Object {
      Write-Host " - $($_.FullName)"
    }
    Remove-DirectoryWithRetry (Join-Path $DesktopDir "node_modules")
  }

  Invoke-CheckedCommand -File "pnpm" -Arguments @("install") -WorkingDirectory $DesktopDir
}

function Ensure-ElectronRuntime {
  $electronExePath = Join-Path $DesktopDir "node_modules\electron\dist\electron.exe"
  if (Test-Path $electronExePath) {
    return
  }

  Write-Host "Electron runtime is missing. Downloading packaged Electron binary..."
  Invoke-CheckedCommand -File "node" -Arguments @("node_modules/electron/install.js") -WorkingDirectory $DesktopDir

  if (-not (Test-Path $electronExePath)) {
    throw "Electron runtime download did not produce '$electronExePath'."
  }
}

$original = Get-Content -Raw -Encoding UTF8 $ConfigFile
$escaped = $BackendUrl.Replace("\", "\\").Replace("'", "\'")
$packagedBackendPattern = "return isPackaged \? '[^']+' : 'http://127\.0\.0\.1:18088';"
$patched = $original -replace $packagedBackendPattern, "return isPackaged ? '$escaped' : 'http://127.0.0.1:18088';"

try {
  if ($original -notmatch $packagedBackendPattern) {
    throw "Could not find the packaged default backend URL in desktopConfig.cjs."
  }
  Set-Content -Path $ConfigFile -Value $patched -Encoding UTF8
  Stop-RunningDesktopClient
  Remove-DirectoryWithRetry (Join-Path $DesktopDir "release")
  Remove-DirectoryWithRetry (Join-Path $DesktopDir "renderer-dist")
  Ensure-DesktopDependencies
  Ensure-ElectronRuntime
  Invoke-CheckedCommand -File "pnpm" -Arguments @("run", $Target) -WorkingDirectory $DesktopDir
  Write-Host "Client package created in: $DesktopDir\release"
} finally {
  Set-Content -Path $ConfigFile -Value $original -Encoding UTF8
}
