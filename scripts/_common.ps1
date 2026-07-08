# Shared helpers for all build scripts. Dot-source this file: . "$PSScriptRoot\_common.ps1"
$ErrorActionPreference = "Stop"

# Repository root = parent of the scripts/ folder (relative, no absolute paths).
$script:RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path

function Get-RepoRoot { return $script:RepoRoot }

function Get-AntlrJar {
    $jar = Join-Path $script:RepoRoot "lib\antlr-4.13.2-complete.jar"
    if (-not (Test-Path $jar)) {
        throw "ANTLR jar not found at lib\antlr-4.13.2-complete.jar. Download antlr-4.13.2-complete.jar into lib\ (see README)."
    }
    return $jar
}

function Write-Ok($msg)   { Write-Host "[ OK ] $msg"   -ForegroundColor Green }
function Write-Info($msg)  { Write-Host "[INFO] $msg"   -ForegroundColor Cyan }
function Write-Fail($msg)  { Write-Host "[FAIL] $msg"   -ForegroundColor Red }

# Run a native command and throw on non-zero exit.
function Invoke-Checked($file, $argList, $errMessage) {
    & $file @argList
    if ($LASTEXITCODE -ne 0) {
        Write-Fail $errMessage
        exit $LASTEXITCODE
    }
}
