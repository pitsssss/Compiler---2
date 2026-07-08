# Creates an isolated virtual environment, installs the generated app's requirements,
# and runs its pytest suite using the Flask test client.
. "$PSScriptRoot\_common.ps1"

$root   = Get-RepoRoot
$genApp = Join-Path $root "generated-app"

if (-not (Test-Path $genApp)) {
    Write-Fail "generated-app/ not found. Run the compiler with --generate first."
    exit 1
}

$py = Get-Command python -ErrorAction SilentlyContinue
if (-not $py) {
    Write-Fail "python not found on PATH; cannot run generated app tests."
    exit 1
}

$venv = Join-Path $genApp ".venv"
if (-not (Test-Path $venv)) {
    Write-Info "Creating virtual environment..."
    & python -m venv $venv
    if ($LASTEXITCODE -ne 0) { Write-Fail "venv creation failed"; exit 1 }
}

$vpy = Join-Path $venv "Scripts\python.exe"
Write-Info "Installing requirements (Flask, pytest)..."
& $vpy -m pip install --disable-pip-version-check -q -r (Join-Path $genApp "requirements.txt")
if ($LASTEXITCODE -ne 0) { Write-Fail "pip install failed"; exit 1 }

Write-Info "Running pytest..."
Push-Location $genApp
try {
    & $vpy -m pytest -q
    $code = $LASTEXITCODE
}
finally {
    Pop-Location
}
if ($code -ne 0) { Write-Fail "Generated app tests failed"; exit $code }
Write-Ok "Generated Flask app tests passed."
