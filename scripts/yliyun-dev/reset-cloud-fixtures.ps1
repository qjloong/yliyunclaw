param(
    [string]$CloudUsername = "admin",
    [Parameter(Mandatory = $true)]
    [string]$CloudPassword,
    [string]$TenantId = "1",
    [string]$FolderName = "_mateclaw_p0_fixtures"
)

$ErrorActionPreference = "Stop"
$ScriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$MateClawRoot = (Resolve-Path (Join-Path $ScriptRoot "..\..")).Path
$BuildRoot = Join-Path $MateClawRoot "data\yliyun-dev\fixture-build"
$OutputRoot = Join-Path $BuildRoot "outputs\yliyun-p0"
$BundledRoot = "C:\Users\loong\.cache\codex-runtimes\codex-primary-runtime\dependencies"
$BundledPython = Join-Path $BundledRoot "python\python.exe"
$BundledNode = Join-Path $BundledRoot "node\bin\node.exe"
$BundledNodeModules = Join-Path $BundledRoot "node\node_modules"
$DocxRenderer = "C:\Users\loong\.codex\plugins\cache\openai-primary-runtime\documents\26.727.11326\skills\documents\render_docx.py"
$PdfToPng = Join-Path $BundledRoot "native\poppler\Library\bin\pdftoppm.exe"
$ApiBase = "http://127.0.0.1:30303/admin-api"

foreach ($required in @($BundledPython, $BundledNode, $BundledNodeModules, $DocxRenderer, $PdfToPng)) {
    if (-not (Test-Path -LiteralPath $required)) {
        throw "Required bundled dependency is missing: $required"
    }
}

New-Item -ItemType Directory -Force -Path $BuildRoot, $OutputRoot | Out-Null
$nodeModulesLink = Join-Path $BuildRoot "node_modules"
if (-not (Test-Path -LiteralPath $nodeModulesLink)) {
    New-Item -ItemType Junction -Path $nodeModulesLink -Target $BundledNodeModules | Out-Null
}
$xlsxBuilder = Join-Path $BuildRoot "build-xlsx-fixture.mjs"
Copy-Item -LiteralPath (Join-Path $ScriptRoot "build-xlsx-fixture.mjs") -Destination $xlsxBuilder -Force

& $BundledPython (Join-Path $ScriptRoot "build-document-fixtures.py") $OutputRoot
if ($LASTEXITCODE -ne 0) { throw "DOCX/PDF fixture generation failed." }
Push-Location $BuildRoot
try {
    & $BundledNode $xlsxBuilder $OutputRoot
    if ($LASTEXITCODE -ne 0) { throw "XLSX fixture generation failed." }
} finally {
    Pop-Location
}

$utf8NoBom = [System.Text.UTF8Encoding]::new($false)
[System.IO.File]::WriteAllText(
    (Join-Path $OutputRoot "fixture-sample.txt"),
    "MateClaw cloud fixture`r`nMATECLAW_FIXTURE_ALPHA_2026`r`nRepeatable plain-text read and grep sample.",
    $utf8NoBom)
[System.IO.File]::WriteAllText(
    (Join-Path $OutputRoot "fixture-sample.md"),
    "# MateClaw Cloud Fixture`r`n`r`nStable token: `MATECLAW_FIXTURE_ALPHA_2026`.`r`n`r`nThis file contains no production data.",
    $utf8NoBom)

$docxRenderDir = Join-Path $BuildRoot "docx-render"
New-Item -ItemType Directory -Force -Path $docxRenderDir | Out-Null
$libreOffice = Get-Command soffice, libreoffice -ErrorAction SilentlyContinue | Select-Object -First 1
if ($libreOffice) {
    & $BundledPython $DocxRenderer (Join-Path $OutputRoot "fixture-sample.docx") --output_dir $docxRenderDir
    if ($LASTEXITCODE -ne 0) { throw "DOCX rendering failed." }
    $docxRenderResult = Join-Path $docxRenderDir "page-1.png"
} else {
    Write-Warning "LibreOffice is unavailable; DOCX visual rendering is skipped after structural verification."
    $docxRenderResult = "SKIPPED: LibreOffice unavailable"
}

$pdfRenderPrefix = Join-Path $BuildRoot "pdf-render\fixture-sample"
New-Item -ItemType Directory -Force -Path (Split-Path -Parent $pdfRenderPrefix) | Out-Null
& $PdfToPng -png -r 144 (Join-Path $OutputRoot "fixture-sample.pdf") $pdfRenderPrefix
if ($LASTEXITCODE -ne 0) { throw "PDF rendering failed." }

Add-Type -AssemblyName System.Net.Http
$handler = [System.Net.Http.HttpClientHandler]::new()
$client = [System.Net.Http.HttpClient]::new($handler)

function New-Request([System.Net.Http.HttpMethod]$Method, [string]$Uri, [string]$Token = "") {
    $request = [System.Net.Http.HttpRequestMessage]::new($Method, $Uri)
    $request.Headers.Add("tenant-id", $TenantId)
    if ($Token) {
        $request.Headers.Authorization =
            [System.Net.Http.Headers.AuthenticationHeaderValue]::new("Bearer", $Token)
    }
    return $request
}

function Send-Json([System.Net.Http.HttpMethod]$Method, [string]$Uri,
                   [string]$Token = "", [object]$Body = $null) {
    $request = New-Request $Method $Uri $Token
    try {
        if ($null -ne $Body) {
            $json = $Body | ConvertTo-Json -Depth 10 -Compress
            $request.Content = [System.Net.Http.StringContent]::new(
                $json, [System.Text.Encoding]::UTF8, "application/json")
        }
        $response = $client.SendAsync($request).GetAwaiter().GetResult()
        $raw = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $payload = if ($raw) { $raw | ConvertFrom-Json } else { $null }
        if (-not $response.IsSuccessStatusCode -or ($payload -and $payload.code -ne 0)) {
            throw "HTTP $([int]$response.StatusCode), code $($payload.code): $($payload.msg)"
        }
        return $payload
    } finally {
        $request.Dispose()
    }
}

function Wait-Operation([long]$TaskId, [string]$Token) {
    for ($attempt = 0; $attempt -lt 100; $attempt++) {
        $task = Send-Json ([System.Net.Http.HttpMethod]::Get) `
            "$ApiBase/cloud-drive/operation-task/$TaskId" $Token
        if ($task.data.status -eq "SUCCESS") { return }
        if ($task.data.status -eq "FAILED") {
            throw "Cloud operation $TaskId failed: $($task.data.errorMessage)"
        }
        Start-Sleep -Milliseconds 200
    }
    throw "Cloud operation $TaskId timed out."
}

function Upload-File([string]$Path, [long]$ParentId, [string]$Token) {
    $request = New-Request ([System.Net.Http.HttpMethod]::Post) `
        "$ApiBase/cloud-drive/file/upload?parentId=$ParentId&duplicateStrategy=reject" $Token
    $multipart = [System.Net.Http.MultipartFormDataContent]::new()
    $stream = [System.IO.File]::OpenRead($Path)
    $content = [System.Net.Http.StreamContent]::new($stream)
    $content.Headers.ContentType =
        [System.Net.Http.Headers.MediaTypeHeaderValue]::new("application/octet-stream")
    $multipart.Add($content, "file", [System.IO.Path]::GetFileName($Path))
    $request.Content = $multipart
    try {
        $response = $client.SendAsync($request).GetAwaiter().GetResult()
        $raw = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
        $payload = $raw | ConvertFrom-Json
        if (-not $response.IsSuccessStatusCode -or $payload.code -ne 0) {
            throw "Upload failed for $([System.IO.Path]::GetFileName($Path)): HTTP $([int]$response.StatusCode), code $($payload.code)"
        }
        return $payload.data
    } finally {
        $request.Dispose()
        $multipart.Dispose()
        $content.Dispose()
        $stream.Dispose()
    }
}

try {
    $login = Send-Json ([System.Net.Http.HttpMethod]::Post) `
        "$ApiBase/system/auth/login" "" @{
            username = $CloudUsername
            password = $CloudPassword
        }
    $token = [string]$login.data.accessToken

    $encodedFolder = [Uri]::EscapeDataString($FolderName)
    $existing = Send-Json ([System.Net.Http.HttpMethod]::Get) `
        "$ApiBase/cloud-drive/file/list?parentId=0&keyword=$encodedFolder&pageNo=1&pageSize=100" $token
    $matches = @($existing.data.list | Where-Object {
        $_.name -eq $FolderName -and $_.fileType -eq "FOLDER"
    })
    foreach ($folder in $matches) {
        $operationId = "fixture-reset-" + [Guid]::NewGuid().ToString("N")
        $deleted = Send-Json ([System.Net.Http.HttpMethod]::Delete) `
            "$ApiBase/cloud-drive/file/delete" $token @{
                ids = @([long]$folder.id)
                operationId = $operationId
            }
        Wait-Operation ([long]$deleted.data.id) $token
    }

    $created = Send-Json ([System.Net.Http.HttpMethod]::Post) `
        "$ApiBase/cloud-drive/file/folder" $token @{
            parentId = 0
            name = $FolderName
            spaceType = "PERSONAL"
        }
    $folderId = [long]$created.data.id

    $fixtureFiles = @(
        "fixture-sample.txt",
        "fixture-sample.md",
        "fixture-sample.pdf",
        "fixture-sample.docx",
        "fixture-sample.xlsx"
    )
    foreach ($name in $fixtureFiles) {
        Upload-File (Join-Path $OutputRoot $name) $folderId $token | Out-Null
    }

    $listed = Send-Json ([System.Net.Http.HttpMethod]::Get) `
        "$ApiBase/cloud-drive/file/list?parentId=$folderId&pageNo=1&pageSize=100&sortBy=NAME&sortOrder=ASC" $token
    $actualNames = @($listed.data.list | ForEach-Object { [string]$_.name } | Sort-Object)
    $expectedNames = @($fixtureFiles | Sort-Object)
    if (($actualNames -join "|") -ne ($expectedNames -join "|")) {
        throw "Fixture verification failed. Expected $($expectedNames -join ', '); got $($actualNames -join ', ')"
    }

    [pscustomobject]@{
        TenantId = $TenantId
        FolderId = $folderId
        FolderName = $FolderName
        FileCount = $actualNames.Count
        Files = $actualNames -join ", "
        DocxRender = $docxRenderResult
        PdfRender = "$pdfRenderPrefix-1.png"
        XlsxRender = Join-Path $OutputRoot "fixture-sample-xlsx.png"
    } | Format-List
} finally {
    $client.Dispose()
    $handler.Dispose()
}
