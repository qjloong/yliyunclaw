param(
    [string]$HostAddress = "0.0.0.0",
    [int]$Port = 5173
)

$ErrorActionPreference = "Stop"
$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$MateClawRoot = (Resolve-Path (Join-Path $ScriptRoot "..\..")).Path
$MateClawUi = Join-Path $MateClawRoot "mateclaw-ui"
$LogRoot = Join-Path $MateClawRoot "data\yliyun-dev\logs"

$listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
if ($listener) {
    Write-Host "MateClaw UI dev server is already listening on http://127.0.0.1:$Port (PID $($listener[0].OwningProcess))."
    exit 0
}

$pnpm = (Get-Command pnpm.cmd -ErrorAction Stop).Source
New-Item -ItemType Directory -Force -Path $LogRoot | Out-Null

$process = Start-Process -FilePath $pnpm `
    -ArgumentList "exec", "vite", "--host", $HostAddress, "--port", "$Port" `
    -WorkingDirectory $MateClawUi `
    -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $LogRoot "mateclaw-ui.out.log") `
    -RedirectStandardError (Join-Path $LogRoot "mateclaw-ui.err.log")

for ($attempt = 0; $attempt -lt 120; $attempt++) {
    Start-Sleep -Milliseconds 500
    if ($process.HasExited) {
        throw "MateClaw UI dev server exited early. See $LogRoot\mateclaw-ui.err.log"
    }
    try {
        $response = Invoke-WebRequest "http://127.0.0.1:$Port/" -TimeoutSec 2 -UseBasicParsing
        if ($response.StatusCode -eq 200) {
            Write-Host "MateClaw UI dev server started at http://127.0.0.1:$Port (launcher PID $($process.Id))."
            exit 0
        }
    } catch {
        # Keep polling while Vite initializes.
    }
}

throw "MateClaw UI dev server did not become ready. See $LogRoot\mateclaw-ui.out.log"
