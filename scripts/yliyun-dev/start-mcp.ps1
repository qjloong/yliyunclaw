param(
    [string]$McpRoot = "",
    [switch]$SkipBuild
)

$ErrorActionPreference = "Stop"
$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$MateClawRoot = (Resolve-Path (Join-Path $ScriptRoot "..\..")).Path
if (-not $McpRoot) {
    $McpRoot = Join-Path (Split-Path -Parent $MateClawRoot) "yliyun-mcp-server"
}

if (-not (Test-Path (Join-Path $McpRoot "package.json"))) {
    throw "MCP repo not found: $McpRoot"
}

& node (Join-Path $ScriptRoot "generate-credentials.mjs") | Out-Null
$CredentialRoot = Join-Path $MateClawRoot "data\yliyun-dev"
$PublicKey = Get-Content (Join-Path $CredentialRoot "obo-public.pem") -Raw
$LogRoot = Join-Path $CredentialRoot "logs"
New-Item -ItemType Directory -Force -Path $LogRoot | Out-Null

$listener = Get-NetTCPConnection -LocalPort 18100 -State Listen -ErrorAction SilentlyContinue
if ($listener) {
    Write-Host "Yliyun MCP is already listening on 127.0.0.1:18100 (PID $($listener[0].OwningProcess))."
    exit 0
}

if (-not $SkipBuild) {
    & npm.cmd run build --prefix $McpRoot
    if ($LASTEXITCODE -ne 0) { throw "MCP build failed." }
}

$McpEnv = @{
    MCP_HOST                      = "127.0.0.1"
    MCP_PORT                      = "18100"
    YLIYUN_API_BASE_URL           = "http://127.0.0.1:30303"
    MCP_ALLOW_INSECURE_LOOPBACK   = "true"
    MATECLAW_OBO_PUBLIC_KEY_PEM   = $PublicKey
    YLIYUN_APP_KEY                = (Get-Content (Join-Path $CredentialRoot "mcp-app-key.txt") -Raw).Trim()
}

foreach ($key in $McpEnv.Keys) {
    [Environment]::SetEnvironmentVariable($key, $McpEnv[$key])
}

$process = Start-Process -FilePath "node.exe" -ArgumentList "dist/index.js" `
    -WorkingDirectory $McpRoot -WindowStyle Hidden -PassThru `
    -RedirectStandardOutput (Join-Path $LogRoot "mcp.out.log") `
    -RedirectStandardError (Join-Path $LogRoot "mcp.err.log")

for ($attempt = 0; $attempt -lt 20; $attempt++) {
    Start-Sleep -Milliseconds 300
    try {
        $health = Invoke-RestMethod "http://127.0.0.1:18100/health" -TimeoutSec 2
        Write-Host "Yliyun MCP started (PID $($process.Id), version $($health.version))."
        exit 0
    } catch {
        if ($process.HasExited) {
            throw "MCP exited early. See $LogRoot\mcp.err.log"
        }
    }
}
throw "MCP did not become healthy. See $LogRoot\mcp.err.log"
