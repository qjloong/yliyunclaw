param(
    [string]$CloudBackendRoot = "D:\project\yly-rag\cloud-saas\yly-saas-cdms-ai",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$MateClawRoot = (Resolve-Path (Join-Path $ScriptRoot "..\..")).Path
$CredentialRoot = Join-Path $MateClawRoot "data\yliyun-dev"
$LogRoot = Join-Path $CredentialRoot "logs"
$JdkHome = "C:\Users\loong\.jdks\ms-21.0.7"
$Maven = "D:\program\IntelliJ IDEA 2025.1.3\plugins\maven\lib\maven3\bin\mvn.cmd"
$Jar = Join-Path $CloudBackendRoot "yudao-server\target\yliyun-server-cloud.jar"

if (-not (Test-Path (Join-Path $CloudBackendRoot "pom.xml"))) {
    throw "Cloud backend repo not found: $CloudBackendRoot"
}

& node (Join-Path $ScriptRoot "generate-credentials.mjs") | Out-Null
New-Item -ItemType Directory -Force -Path $LogRoot | Out-Null

$listener = Get-NetTCPConnection -LocalPort 30303 -State Listen -ErrorAction SilentlyContinue
if ($listener) {
    Write-Host "Cloud backend is already listening on 127.0.0.1:30303 (PID $($listener[0].OwningProcess))."
    exit 0
}

if (-not $SkipBuild) {
    $previousJavaHome = $env:JAVA_HOME
    $previousPath = $env:Path
    try {
        $env:JAVA_HOME = $JdkHome
        $env:Path = "$JdkHome\bin;$previousPath"
        & $Maven "-pl" "yudao-server" "-am" "-DskipTests" "package" "-f" (Join-Path $CloudBackendRoot "pom.xml")
        if ($LASTEXITCODE -ne 0) { throw "Cloud backend build failed." }
    } finally {
        $env:JAVA_HOME = $previousJavaHome
        $env:Path = $previousPath
    }
}

if (-not (Test-Path $Jar)) {
    throw "Cloud backend executable jar not found: $Jar"
}

$ticketSecretCurrent = (Get-Content (Join-Path $CredentialRoot "ticket-secret.txt") -Raw).Trim()
$ticketKeyIdCurrent = (Get-Content (Join-Path $CredentialRoot "ticket-key-id.txt") -Raw).Trim()
$ticketSecretPreviousPath = Join-Path $CredentialRoot "ticket-secret-previous.txt"
$ticketKeyIdPreviousPath = Join-Path $CredentialRoot "ticket-key-id-previous.txt"
$ticketSecretPrevious = if (Test-Path $ticketSecretPreviousPath) { (Get-Content $ticketSecretPreviousPath -Raw).Trim() } else { "" }
$ticketKeyIdPrevious = if (Test-Path $ticketKeyIdPreviousPath) { (Get-Content $ticketKeyIdPreviousPath -Raw).Trim() } else { "previous" }
$mcpAppKey = (Get-Content (Join-Path $CredentialRoot "mcp-app-key.txt") -Raw).Trim()

[Environment]::SetEnvironmentVariable("YLIYUN_TICKET_SECRET", $ticketSecretCurrent)
[Environment]::SetEnvironmentVariable("YLIYUN_TICKET_SECRET_CURRENT", $ticketSecretCurrent)
[Environment]::SetEnvironmentVariable("YLIYUN_TICKET_SECRET_PREVIOUS", $ticketSecretPrevious)
[Environment]::SetEnvironmentVariable("YLIYUN_TICKET_KEY_ID_CURRENT", $ticketKeyIdCurrent)
[Environment]::SetEnvironmentVariable("YLIYUN_TICKET_KEY_ID_PREVIOUS", $ticketKeyIdPrevious)
[Environment]::SetEnvironmentVariable("YLIYUN_MCP_APP_KEY", $mcpAppKey)

$process = Start-Process -FilePath (Join-Path $JdkHome "bin\java.exe") `
    -ArgumentList "-jar", $Jar -WorkingDirectory $CloudBackendRoot `
    -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $LogRoot "cloud-backend.out.log") `
    -RedirectStandardError (Join-Path $LogRoot "cloud-backend.err.log")

for ($attempt = 0; $attempt -lt 180; $attempt++) {
    Start-Sleep -Milliseconds 500
    if ($process.HasExited) {
        throw "Cloud backend exited early. See $LogRoot\cloud-backend.err.log"
    }
    $active = Get-NetTCPConnection -LocalPort 30303 -State Listen -ErrorAction SilentlyContinue
    if ($active) {
        Write-Host "Cloud backend started (PID $($process.Id), port 30303)."
        exit 0
    }
}

throw "Cloud backend did not start listening. See $LogRoot\cloud-backend.out.log"
