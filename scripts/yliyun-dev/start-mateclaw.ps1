param(
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$MateClawRoot = (Resolve-Path (Join-Path $ScriptRoot "..\..")).Path
$CredentialRoot = Join-Path $MateClawRoot "data\yliyun-dev"
$LogRoot = Join-Path $CredentialRoot "logs"
$JdkHome = "C:\Users\loong\.jdks\ms-21.0.7"
$Maven = "D:\program\IntelliJ IDEA 2025.1.3\plugins\maven\lib\maven3\bin\mvn.cmd"
$TargetRoot = Join-Path $MateClawRoot "mateclaw-server\target"

function Resolve-MateClawExecutableJar {
    if (-not (Test-Path -LiteralPath $TargetRoot)) { return $null }
    $candidate = Get-ChildItem -LiteralPath $TargetRoot -File -Filter "mateclaw-server-*.jar" |
        Where-Object {
            $_.Name -notmatch '-(sources|javadoc|tests)\.jar$' -and $_.Length -gt 10MB
        } |
        Sort-Object LastWriteTimeUtc -Descending |
        Select-Object -First 1
    if ($candidate) { return $candidate.FullName }
    return $null
}

& node (Join-Path $ScriptRoot "generate-credentials.mjs") | Out-Null
New-Item -ItemType Directory -Force -Path $LogRoot | Out-Null

$listener = Get-NetTCPConnection -LocalPort 18088 -State Listen -ErrorAction SilentlyContinue
if ($listener) {
    Write-Host "MateClaw is already listening on 127.0.0.1:18088 (PID $($listener[0].OwningProcess))."
    exit 0
}

if (-not $SkipBuild) {
    $previousJavaHome = $env:JAVA_HOME
    $previousPath = $env:Path
    try {
        $env:JAVA_HOME = $JdkHome
        $env:Path = "$JdkHome\bin;$previousPath"
        & $Maven "-pl" "mateclaw-server" "-am" "-Dmaven.test.skip=true" "install"
        if ($LASTEXITCODE -ne 0) { throw "MateClaw build failed." }
    } finally {
        $env:JAVA_HOME = $previousJavaHome
        $env:Path = $previousPath
    }
}

$Jar = Resolve-MateClawExecutableJar
if (-not $Jar -or -not (Test-Path -LiteralPath $Jar)) {
    throw "MateClaw executable jar not found under: $TargetRoot"
}
Write-Host "Using MateClaw executable jar: $Jar"

$previousTicketSecret = $env:YLIYUN_TICKET_SECRET
$previousTicketSecretCurrent = $env:YLIYUN_TICKET_SECRET_CURRENT
$previousTicketSecretPrevious = $env:YLIYUN_TICKET_SECRET_PREVIOUS
$previousTicketKeyIdCurrent = $env:YLIYUN_TICKET_KEY_ID_CURRENT
$previousTicketKeyIdPrevious = $env:YLIYUN_TICKET_KEY_ID_PREVIOUS
$previousPrivateKey = $env:MATECLAW_MCP_OBO_PRIVATE_KEY_PEM
try {
    $ticketSecretCurrent = (Get-Content (Join-Path $CredentialRoot "ticket-secret.txt") -Raw).Trim()
    $env:YLIYUN_TICKET_SECRET = $ticketSecretCurrent
    $env:YLIYUN_TICKET_SECRET_CURRENT = $ticketSecretCurrent
    $env:YLIYUN_TICKET_KEY_ID_CURRENT = (Get-Content (Join-Path $CredentialRoot "ticket-key-id.txt") -Raw).Trim()
    $ticketSecretPreviousPath = Join-Path $CredentialRoot "ticket-secret-previous.txt"
    $ticketKeyIdPreviousPath = Join-Path $CredentialRoot "ticket-key-id-previous.txt"
    $env:YLIYUN_TICKET_SECRET_PREVIOUS = if (Test-Path $ticketSecretPreviousPath) {
        (Get-Content $ticketSecretPreviousPath -Raw).Trim()
    } else { "" }
    $env:YLIYUN_TICKET_KEY_ID_PREVIOUS = if (Test-Path $ticketKeyIdPreviousPath) {
        (Get-Content $ticketKeyIdPreviousPath -Raw).Trim()
    } else { "previous" }
    $env:MATECLAW_MCP_OBO_PRIVATE_KEY_PEM = Get-Content (Join-Path $CredentialRoot "obo-private.pem") -Raw
    $process = Start-Process -FilePath (Join-Path $JdkHome "bin\java.exe") `
        -ArgumentList "-jar", $Jar -WorkingDirectory $MateClawRoot `
        -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $LogRoot "mateclaw.out.log") `
        -RedirectStandardError (Join-Path $LogRoot "mateclaw.err.log")
} finally {
    $env:YLIYUN_TICKET_SECRET = $previousTicketSecret
    $env:YLIYUN_TICKET_SECRET_CURRENT = $previousTicketSecretCurrent
    $env:YLIYUN_TICKET_SECRET_PREVIOUS = $previousTicketSecretPrevious
    $env:YLIYUN_TICKET_KEY_ID_CURRENT = $previousTicketKeyIdCurrent
    $env:YLIYUN_TICKET_KEY_ID_PREVIOUS = $previousTicketKeyIdPrevious
    $env:MATECLAW_MCP_OBO_PRIVATE_KEY_PEM = $previousPrivateKey
}

for ($attempt = 0; $attempt -lt 90; $attempt++) {
    Start-Sleep -Milliseconds 500
    if ($process.HasExited) {
        throw "MateClaw exited early. See $LogRoot\mateclaw.err.log"
    }
    try {
        $health = Invoke-RestMethod "http://127.0.0.1:18088/actuator/health" -TimeoutSec 2
        if ($health.status -eq "UP") {
            Write-Host "MateClaw started (PID $($process.Id), health $($health.status))."
            exit 0
        }
    } catch {
        # Keep polling during Spring startup.
    }
}

throw "MateClaw did not become healthy. See $LogRoot\mateclaw.out.log"
