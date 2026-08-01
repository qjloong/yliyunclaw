param(
    [string]$CloudFrontendRoot = "D:\project\yly-rag\cloud-driver"
)

$ErrorActionPreference = "Stop"
$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$MateClawRoot = (Resolve-Path (Join-Path $ScriptRoot "..\..")).Path
$LogRoot = Join-Path $MateClawRoot "data\yliyun-dev\logs"

if (-not (Test-Path (Join-Path $CloudFrontendRoot "package.json"))) {
    throw "Cloud frontend repo not found: $CloudFrontendRoot"
}
if (-not (Test-Path (Join-Path $CloudFrontendRoot "node_modules"))) {
    throw "Cloud frontend dependencies are missing. Run npm install in $CloudFrontendRoot first."
}
New-Item -ItemType Directory -Force -Path $LogRoot | Out-Null

$listener = Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue
if ($listener) {
    Write-Host "Cloud frontend is already listening on 127.0.0.1:8080 (PID $($listener[0].OwningProcess))."
    exit 0
}

$process = Start-Process -FilePath "npm.cmd" -ArgumentList "run", "dev" `
    -WorkingDirectory $CloudFrontendRoot -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $LogRoot "cloud-frontend.out.log") `
    -RedirectStandardError (Join-Path $LogRoot "cloud-frontend.err.log")

for ($attempt = 0; $attempt -lt 60; $attempt++) {
    Start-Sleep -Milliseconds 500
    if ($process.HasExited) {
        throw "Cloud frontend exited early. See $LogRoot\cloud-frontend.err.log"
    }
    try {
        $status = & curl.exe --head --silent --output NUL --write-out "%{http_code}" `
            --max-time 5 "http://127.0.0.1:8080/"
        if ($status -eq "200") {
            Write-Host "Cloud frontend started (PID $($process.Id), HTTP 200)."
            exit 0
        }
    } catch {
        # Keep polling while Vite starts.
    }
}

throw "Cloud frontend did not become healthy. See $LogRoot\cloud-frontend.out.log"
