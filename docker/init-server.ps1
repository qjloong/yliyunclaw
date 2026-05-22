param(
  [string]$PublicUrl = ""
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$EnvFile = Join-Path $ScriptDir ".env"
$ExampleFile = Join-Path $ScriptDir ".env.production.example"

function New-RandomBase64([int]$Bytes = 48) {
  $buffer = New-Object byte[] $Bytes
  [System.Security.Cryptography.RandomNumberGenerator]::Fill($buffer)
  [Convert]::ToBase64String($buffer)
}

function New-RandomHex([int]$Bytes = 32) {
  $buffer = New-Object byte[] $Bytes
  [System.Security.Cryptography.RandomNumberGenerator]::Fill($buffer)
  ($buffer | ForEach-Object { $_.ToString("x2") }) -join ""
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
  throw "docker is not installed or not in PATH."
}

docker compose version | Out-Null

if ([string]::IsNullOrWhiteSpace($PublicUrl)) {
  $PublicUrl = "http://YOUR_SERVER_IP:18080"
}

if (Test-Path $EnvFile) {
  Write-Host "docker/.env already exists; leaving it unchanged."
} else {
  $content = Get-Content -Raw -Encoding UTF8 $ExampleFile
  $content = $content.Replace("MATECLAW_PUBLIC_URL=http://YOUR_SERVER_IP:18080", "MATECLAW_PUBLIC_URL=$PublicUrl")
  $content = $content.Replace("MATECLAW_CORS_ALLOWED_ORIGINS=http://YOUR_SERVER_IP:18080", "MATECLAW_CORS_ALLOWED_ORIGINS=$PublicUrl")
  $content = $content.Replace("DB_PASSWORD=CHANGE_ME_GENERATE_WITH_INIT_SCRIPT", "DB_PASSWORD=$(New-RandomBase64)")
  $content = $content.Replace("DB_ROOT_PASSWORD=CHANGE_ME_GENERATE_WITH_INIT_SCRIPT", "DB_ROOT_PASSWORD=$(New-RandomBase64)")
  $content = $content.Replace("JWT_SECRET=CHANGE_ME_GENERATE_WITH_INIT_SCRIPT", "JWT_SECRET=$(New-RandomBase64)")
  $content = $content.Replace("SEARXNG_SECRET=CHANGE_ME_GENERATE_WITH_INIT_SCRIPT", "SEARXNG_SECRET=$(New-RandomHex)")
  Set-Content -Path $EnvFile -Value $content -Encoding UTF8
  Write-Host "Created docker/.env for $PublicUrl"
}

Write-Host ""
Write-Host "Next commands:"
Write-Host "  cd $ScriptDir"
Write-Host "  docker compose --env-file .env -f docker-compose.server.yml up -d --build"
Write-Host "  docker compose --env-file .env -f docker-compose.server.yml logs -f yliyunclaw-server"
