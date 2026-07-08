# Runs the Java test suite (TestRunner) and, if Python+Flask are available,
# the generated Flask app's pytest suite. Returns non-zero if anything fails.
. "$PSScriptRoot\_common.ps1"

$root = Get-RepoRoot
$jar  = Get-AntlrJar
$out  = Join-Path $root "out"

Write-Info "Building before tests..."
& "$PSScriptRoot\build.ps1"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$cp = "$out;$jar"
$failed = 0

Write-Info "Running Java test suite (tests.TestRunner)..."
Push-Location $root
try {
    & java -cp $cp tests.TestRunner
    if ($LASTEXITCODE -ne 0) { $failed = 1 }
}
finally {
    Pop-Location
}

# Optional: run the generated Flask app tests through pytest in an isolated venv.
$genApp = Join-Path $root "generated-app"
if ((Test-Path $genApp) -and (Get-Command python -ErrorAction SilentlyContinue)) {
    Write-Info "Running generated Flask app tests (pytest)..."
    & "$PSScriptRoot\test-flask-app.ps1"
    if ($LASTEXITCODE -ne 0) { $failed = 1 }
} else {
    Write-Info "Skipping generated Flask app tests (no generated-app or python not found)."
}

if ($failed -ne 0) {
    Write-Fail "Some tests FAILED."
    exit 1
}
Write-Ok "All tests passed."
