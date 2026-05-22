param(
  [ValidateSet("all", "backend", "frontend", "app")]
  [string]$Component = "all",

  [string]$ServerHost = "192.168.0.50",
  [string]$ServerUser = "root",
  [string]$ServerPassword = "",
  [int]$SshPort = 22,
  [string]$RemoteDockerDir = "/opt/yliyunclaw/docker",
  [string]$BackendUrl = "http://192.168.0.50:18080",

  [ValidateSet("dist", "dist:nsis", "dist:portable", "pack")]
  [string]$DesktopTarget = "dist:portable",

  [string]$MavenSettings = "",
  [string]$MavenCommand = "",
  [switch]$SkipBuild,
  [switch]$NoRestart,
  [switch]$SkipHealthCheck
)

$ErrorActionPreference = "Stop"

$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$ServerDir = Join-Path $RepoRoot "mateclaw-server"
$UiDir = Join-Path $RepoRoot "mateclaw-ui"
$DesktopDir = Join-Path $RepoRoot "mateclaw-desktop"
$RuntimeDir = Join-Path $PSScriptRoot "runtime"
$PackageDir = Join-Path $RepoRoot ".release\test"
$Stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$Remote = "${ServerUser}@${ServerHost}"
$SshBaseArgs = @("-p", "$SshPort")
$ScpBaseArgs = @("-P", "$SshPort")
$script:SshSession = $null
$script:SftpSession = $null

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

function Get-RemoteParent([string]$RemotePath) {
  $normalized = $RemotePath.Replace("\", "/")
  $idx = $normalized.LastIndexOf("/")
  if ($idx -le 0) { return "/" }
  return $normalized.Substring(0, $idx)
}

function Get-RemoteLeaf([string]$RemotePath) {
  $normalized = $RemotePath.Replace("\", "/")
  $idx = $normalized.LastIndexOf("/")
  if ($idx -lt 0) { return $normalized }
  return $normalized.Substring($idx + 1)
}

function Initialize-PasswordSsh {
  if (-not $ServerPassword) { return }
  if ($script:SshSession -and $script:SftpSession) { return }
  Import-Module Posh-SSH -ErrorAction Stop
  $secure = ConvertTo-SecureString $ServerPassword -AsPlainText -Force
  $credential = [System.Management.Automation.PSCredential]::new($ServerUser, $secure)
  $script:SshSession = New-SSHSession -ComputerName $ServerHost -Port $SshPort -Credential $credential -AcceptKey -Force -ErrorAction Stop
  $script:SftpSession = New-SFTPSession -ComputerName $ServerHost -Port $SshPort -Credential $credential -AcceptKey -Force -ErrorAction Stop
}

function Invoke-Remote([string]$Command) {
  if ($ServerPassword) {
    Initialize-PasswordSsh
    Write-Host "[remote] $Command"
    $result = Invoke-SSHCommand -SessionId $script:SshSession.SessionId -Command $Command -ErrorAction Stop
    if ($result.Output) { $result.Output | ForEach-Object { Write-Host $_ } }
    if ($result.Error) { $result.Error | ForEach-Object { Write-Host $_ -ForegroundColor Yellow } }
    if ($result.ExitStatus -ne 0) {
      throw "Remote command failed with exit status $($result.ExitStatus): $Command"
    }
    return
  }
  Invoke-CheckedCommand -File "ssh" -Arguments ($SshBaseArgs + @($Remote, $Command))
}

function Copy-ToRemote([string]$LocalPath, [string]$RemotePath) {
  if ($ServerPassword) {
    Initialize-PasswordSsh
    $parent = Get-RemoteParent $RemotePath
    $remoteLeaf = Get-RemoteLeaf $RemotePath
    $localLeaf = Split-Path -Leaf $LocalPath
    Write-Host "[sftp] $LocalPath -> ${Remote}:${RemotePath}"
    Set-SFTPItem -SessionId $script:SftpSession.SessionId -Path $LocalPath -Destination $parent -Force -ErrorAction Stop | Out-Null
    if ($remoteLeaf -and $remoteLeaf -ne $localLeaf) {
      Invoke-Remote "mv -f '${parent}/${localLeaf}' '${RemotePath}'"
    }
    return
  }
  Invoke-CheckedCommand -File "scp" -Arguments ($ScpBaseArgs + @($LocalPath, "${Remote}:${RemotePath}"))
}

function Resolve-MavenCommand {
  if ($MavenCommand) { return $MavenCommand }
  $ideaMaven = "D:\program\IntelliJ IDEA 2025.1.3\plugins\maven\lib\maven3\bin\mvn.cmd"
  if (Test-Path $ideaMaven) { return $ideaMaven }
  $cmd = Get-Command mvn -ErrorAction SilentlyContinue
  if ($cmd) { return $cmd.Source }
  throw "Maven command not found. Pass -MavenCommand or add mvn to PATH."
}

function Build-Backend {
  Write-Step "Build backend jar"
  if ($SkipBuild) {
    $jar = Get-ChildItem -Path (Join-Path $ServerDir "target") -Filter "*.jar" |
      Sort-Object LastWriteTime -Descending |
      Select-Object -First 1
    if (-not $jar) { throw "No existing backend jar found under mateclaw-server\target." }
    return $jar.FullName
  }

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

  $jar = Get-ChildItem -Path (Join-Path $ServerDir "target") -Filter "*.jar" |
    Where-Object { $_.Name -notmatch "sources|javadoc" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
  if (-not $jar) { throw "Backend jar not found after build." }
  return $jar.FullName
}

function Build-FrontendStatic {
  Write-Step "Build frontend static"
  $staticOut = Join-Path $PackageDir "static"
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
  return $staticOut
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
  $preferred = Get-ChildItem -Path $releaseDir -File -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -match "\.exe$" -and $_.Name -match "portable|Meta Y|MetaY|Desktop|Setup" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
  if (-not $preferred) {
    $preferred = Get-ChildItem -Path $releaseDir -File -Filter "*.exe" -ErrorAction SilentlyContinue |
      Sort-Object LastWriteTime -Descending |
      Select-Object -First 1
  }
  if (-not $preferred) { throw "Desktop client exe not found under $releaseDir." }
  return $preferred.FullName
}

function Publish-Backend {
  $jarPath = Build-Backend
  Write-Step "Publish backend jar to test server"
  New-Item -ItemType Directory -Force -Path $PackageDir | Out-Null
  $localJar = Join-Path $PackageDir "app.jar"
  Copy-Item -LiteralPath $jarPath -Destination $localJar -Force
  Invoke-Remote "mkdir -p '${RemoteDockerDir}/../packages' '${RemoteDockerDir}/../backups' '${RemoteDockerDir}/runtime/app'"
  Copy-ToRemote $localJar "${RemoteDockerDir}/../packages/app-${Stamp}.jar"
  Invoke-Remote "cd '${RemoteDockerDir}' && if [ -f runtime/app/app.jar ]; then cp runtime/app/app.jar ../backups/app-${Stamp}.jar; fi && cp ../packages/app-${Stamp}.jar runtime/app/app.jar"
}

function Publish-Frontend {
  $staticPath = Build-FrontendStatic
  Write-Step "Package and publish frontend static"
  $archive = Join-Path $PackageDir "static-${Stamp}.tar.gz"
  if (Test-Path $archive) { Remove-Item -LiteralPath $archive -Force }
  Invoke-CheckedCommand -File "tar" -Arguments @("-czf", $archive, "-C", $staticPath, ".")
  Invoke-Remote "mkdir -p '${RemoteDockerDir}/../packages' '${RemoteDockerDir}/../backups' '${RemoteDockerDir}/runtime/static'"
  Copy-ToRemote $archive "${RemoteDockerDir}/../packages/static-${Stamp}.tar.gz"
  Invoke-Remote "cd '${RemoteDockerDir}' && if [ -d runtime/static ]; then tar -czf ../backups/static-${Stamp}.tar.gz runtime/static; fi && rm -rf runtime/static/* && tar -xzf ../packages/static-${Stamp}.tar.gz -C runtime/static"
}

function Publish-App {
  $exePath = Build-DesktopClient
  Write-Step "Publish desktop client download"
  $localExe = Join-Path $PackageDir "MetaY-Desktop.exe"
  New-Item -ItemType Directory -Force -Path $PackageDir | Out-Null
  Copy-Item -LiteralPath $exePath -Destination $localExe -Force
  Invoke-Remote "mkdir -p '${RemoteDockerDir}/runtime/static/downloads' '${RemoteDockerDir}/../backups'"
  Copy-ToRemote $localExe "${RemoteDockerDir}/runtime/static/downloads/MetaY-Desktop.exe"
}

function Restart-ServerIfNeeded {
  if ($NoRestart) {
    Write-Host "Skip restart because -NoRestart was set."
    return
  }
  Write-Step "Restart server container"
  Invoke-Remote "cd '${RemoteDockerDir}' && (docker compose --env-file .env -f docker-compose.runtime.yml restart yliyunclaw-server || docker-compose --env-file .env -f docker-compose.runtime.yml restart yliyunclaw-server)"
}

function Test-Health {
  if ($SkipHealthCheck) { return }
  Write-Step "Health check"
  Invoke-Remote "curl -fsS '${BackendUrl.TrimEnd('/')}/actuator/health' || curl -fsS 'http://127.0.0.1:18080/actuator/health'"
}

New-Item -ItemType Directory -Force -Path $PackageDir | Out-Null

Write-Step "Publish target: $Component -> ${Remote}:$RemoteDockerDir"
switch ($Component) {
  "backend" {
    Publish-Backend
    Restart-ServerIfNeeded
    Test-Health
  }
  "frontend" {
    Publish-Frontend
    Restart-ServerIfNeeded
    Test-Health
  }
  "app" {
    Publish-App
  }
  "all" {
    Publish-Backend
    Publish-Frontend
    Publish-App
    Restart-ServerIfNeeded
    Test-Health
  }
}

Write-Host ""
Write-Host "Publish completed." -ForegroundColor Green
Write-Host "Backend URL: $BackendUrl"
Write-Host "Client download: $($BackendUrl.TrimEnd('/'))/downloads/MetaY-Desktop.exe"

if ($script:SftpSession) { Remove-SFTPSession -SessionId $script:SftpSession.SessionId | Out-Null }
if ($script:SshSession) { Remove-SSHSession -SessionId $script:SshSession.SessionId | Out-Null }
