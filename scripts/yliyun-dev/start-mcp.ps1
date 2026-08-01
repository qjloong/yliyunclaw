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

$previous = @{
    MCP_HOST = $env:MCP_HOST
    MCP_PORT = $env:MCP_PORT
    YLIYUN_API_BASE_URL = $env:YLIYUN_API_BASE_URL
    MCP_ALLOW_INSECURE_LOOPBACK = $env:MCP_ALLOW_INSECURE_LOOPBACK
    MATECLAW_OBO_PUBLIC_KEY_PEM = $env:MATECLAW_OBO_PUBLIC_KEY_PEM
    YLIYUN_APP_KEY = $env:YLIYUN_APP_KEY
}
try {
    $env:MCP_HOST = "127.0.0.1"
    $env:MCP_PORT = "18100"
    $env:YLIYUN_API_BASE_URL = "http://127.0.0.1:30303"
    $env:MCP_ALLOW_INSECURE_LOOPBACK = "true"
    $env:MATECLAW_OBO_PUBLIC_KEY_PEM = $PublicKey
    $env:YLIYUN_APP_KEY = (Get-Content (Join-Path $CredentialRoot "mcp-app-key.txt") -Raw).Trim()
    $process = Start-Process -FilePath "node.exe" -ArgumentList "dist/index.js" `
        -WorkingDirectory $McpRoot -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput (Join-Path $LogRoot "mcp.out.log") `
        -RedirectStandardError (Join-Path $LogRoot "mcp.err.log")
} finally {
    $env:MCP_HOST = $previous.MCP_HOST
    $env:MCP_PORT = $previous.MCP_PORT
    $env:YLIYUN_API_BASE_URL = $previous.YLIYUN_API_BASE_URL
    $env:MCP_ALLOW_INSECURE_LOOPBACK = $previous.MCP_ALLOW_INSECURE_LOOPBACK
    $env:MATECLAW_OBO_PUBLIC_KEY_PEM = $previous.MATECLAW_OBO_PUBLIC_KEY_PEM
    $env:YLIYUN_APP_KEY = $previous.YLIYUN_APP_KEY
}

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
