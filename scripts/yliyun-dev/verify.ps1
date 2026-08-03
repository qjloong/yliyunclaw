param(
    [string]$McpRoot = "",
    [string]$CloudFrontendRoot = "D:\project\yly-rag\cloud-driver",
    [string]$CloudBackendRoot = "D:\project\yly-rag\cloud-saas\yly-saas-cdms-ai",
    [switch]$RequireServices
)

$ErrorActionPreference = "Stop"
$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$MateClawRoot = (Resolve-Path (Join-Path $ScriptRoot "..\..")).Path
if (-not $McpRoot) {
    $McpRoot = Join-Path (Split-Path -Parent $MateClawRoot) "yliyun-mcp-server"
}

$JdkHome = "C:\Users\loong\.jdks\ms-21.0.7"
$Maven = "D:\program\IntelliJ IDEA 2025.1.3\plugins\maven\lib\maven3\bin\mvn.cmd"
$CredentialRoot = Join-Path $MateClawRoot "data\yliyun-dev"

function Write-Check([string]$Name, [bool]$Success, [string]$Detail) {
    $mark = if ($Success) { "[OK]" } else { "[FAIL]" }
    Write-Host ("{0,-6} {1,-20} {2}" -f $mark, $Name, $Detail)
    return $Success
}

function Test-Port([int]$Port) {
    if (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue) {
        return $true
    }
    foreach ($hostName in @("127.0.0.1", "localhost")) {
        $client = [System.Net.Sockets.TcpClient]::new()
        try {
            $task = $client.ConnectAsync($hostName, $Port)
            if ($task.Wait(800) -and $client.Connected) {
                return $true
            }
        } catch {
            # Try the other loopback family. Vite may bind localhost to ::1.
        } finally {
            $client.Dispose()
        }
    }
    return $false
}

$checks = @()
$checks += Write-Check "MateClaw repo" (Test-Path (Join-Path $MateClawRoot "pom.xml")) $MateClawRoot
$checks += Write-Check "MCP repo" (Test-Path (Join-Path $McpRoot "package.json")) $McpRoot
$checks += Write-Check "Cloud frontend" (Test-Path (Join-Path $CloudFrontendRoot "package.json")) $CloudFrontendRoot
$checks += Write-Check "Cloud backend" (Test-Path (Join-Path $CloudBackendRoot "pom.xml")) $CloudBackendRoot
$checks += Write-Check "Node.js" ([version]((node --version).TrimStart("v")) -ge [version]"22.0.0") (node --version)
$checks += Write-Check "JDK 21" (Test-Path (Join-Path $JdkHome "bin\java.exe")) $JdkHome
$checks += Write-Check "Maven" (Test-Path $Maven) $Maven
$checks += Write-Check "OBO private key" (Test-Path (Join-Path $CredentialRoot "obo-private.pem")) $CredentialRoot
$checks += Write-Check "OBO public key" (Test-Path (Join-Path $CredentialRoot "obo-public.pem")) $CredentialRoot
$checks += Write-Check "Ticket secret" (Test-Path (Join-Path $CredentialRoot "ticket-secret.txt")) $CredentialRoot
$checks += Write-Check "MCP app key" (Test-Path (Join-Path $CredentialRoot "mcp-app-key.txt")) $CredentialRoot
$credentialOutput = & node (Join-Path $ScriptRoot "verify-credentials.mjs") 2>&1
$credentialPairOk = $LASTEXITCODE -eq 0
$checks += Write-Check "Credential pairing" $credentialPairOk ($credentialOutput -join " ")

$services = @(
    @{ Name = "Cloud frontend"; Port = 8080 },
    @{ Name = "Cloud backend"; Port = 30303 },
    @{ Name = "Yliyun MCP"; Port = 18100 },
    @{ Name = "MateClaw backend"; Port = 18088 },
    @{ Name = "MateClaw UI"; Port = 5173 }
)
foreach ($service in $services) {
    $up = Test-Port $service.Port
    $checks += Write-Check $service.Name $up ("127.0.0.1:{0}" -f $service.Port)
}

$prerequisiteCount = 12
$prerequisitesOk = ($checks[0..($prerequisiteCount - 1)] -notcontains $false)
$servicesOk = ($checks[$prerequisiteCount..($checks.Count - 1)] -notcontains $false)
if (-not $prerequisitesOk -or ($RequireServices -and -not $servicesOk)) {
    exit 1
}
