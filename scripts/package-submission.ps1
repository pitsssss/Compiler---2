# Package a submission ZIP after full-check passes.
param(
    [Parameter(Mandatory = $true)]
    [string]$GroupNumber,

    [string]$OutputDirectory = ".\submission",

    [string]$ReportPath = "",

    [string]$RepositoriesFilePath = ".\TEAM_AND_REPOSITORY.txt"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

function Write-Info([string]$Message) { Write-Host "[INFO] $Message" }
function Write-Ok([string]$Message) { Write-Host "[ OK ] $Message" }
function Write-Fail([string]$Message) { Write-Host "[FAIL] $Message" }

Write-Info "Running full project check before packaging..."
& powershell.exe -NoProfile -ExecutionPolicy Bypass -File ".\scripts\full-check.ps1"
if ($LASTEXITCODE -ne 0) {
    Write-Fail "full-check.ps1 failed (exit $LASTEXITCODE). Packaging aborted."
    exit 1
}

$staging = Join-Path $env:TEMP ("compiler-submission-staging-" + [guid]::NewGuid().ToString())
New-Item -ItemType Directory -Path $staging | Out-Null
Write-Info "Staging directory: $staging"

$itemsToCopy = @(
    "src", "lib", "scripts", "examples", "docs", "README.md",
    "TEAM_AND_REPOSITORY.txt", "ENVIRONMENT.md", ".vscode"
)

foreach ($item in $itemsToCopy) {
    $srcPath = Join-Path $projectRoot $item
    if (Test-Path $srcPath) {
        Copy-Item -Path $srcPath -Destination (Join-Path $staging $item) -Recurse -Force
    }
}

if ($ReportPath -and (Test-Path $ReportPath)) {
    $reportInfo = Get-Item $ReportPath
    if ($reportInfo.Length -eq 0) { throw "Report file is empty: $ReportPath" }
    Copy-Item $ReportPath (Join-Path $staging $reportInfo.Name)
    Write-Ok "Included report: $($reportInfo.Name)"
} elseif ($ReportPath) {
    Write-Host "[WARNING] Report not found: $ReportPath (continuing without it)"
}

if (Test-Path $RepositoriesFilePath) {
    $repoText = Get-Content $RepositoriesFilePath -Raw
    $placeholders = @("[ضع الرابط هنا]", "[ضع رقم المجموعة هنا]", "[الاسم الأول]", "[الاسم الثاني]", "[الاسم الثالث]", "[الاسم الرابع]")
    foreach ($ph in $placeholders) {
        if ($repoText -match [regex]::Escape($ph)) {
            throw "TEAM_AND_REPOSITORY.txt still contains placeholder: $ph"
        }
    }
}

$excludePatterns = @(
    "out", "generated-app\.venv", "__pycache__", "\.pytest_cache",
    "\.pyc$", "sources\.txt"
)

Get-ChildItem $staging -Recurse -Directory | ForEach-Object {
    if ($_.Name -eq "out" -or $_.Name -eq ".venv" -or $_.Name -eq "__pycache__" -or $_.Name -eq ".pytest_cache") {
        Remove-Item $_.FullName -Recurse -Force -ErrorAction SilentlyContinue
    }
}

if (-not (Test-Path $OutputDirectory)) {
    New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
}

$zipName = "Group-$GroupNumber.zip"
$zipPath = Join-Path (Resolve-Path $OutputDirectory) $zipName
if (Test-Path $zipPath) { Remove-Item $zipPath -Force }

Compress-Archive -Path (Join-Path $staging "*") -DestinationPath $zipPath -Force

if (-not (Test-Path $zipPath)) { throw "ZIP was not created." }
$zipSize = (Get-Item $zipPath).Length
if ($zipSize -eq 0) { throw "ZIP is empty." }

Write-Ok "Created: $zipPath ($zipSize bytes)"
Write-Info "ZIP contents:"
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead($zipPath)
foreach ($entry in $archive.Entries) {
    Write-Host "  $($entry.FullName)"
}
$archive.Dispose()

Remove-Item $staging -Recurse -Force
Write-Ok "Staging directory removed."
exit 0
