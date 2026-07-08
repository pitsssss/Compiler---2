# Builds if needed, then runs the compiler CLI (Main).
# Any arguments after the script are forwarded to Main, e.g.:
#   .\scripts\run.ps1 --flask src/Tests/FinalTests/app.py --template src/Tests/FinalTests --generate
# With no arguments a sensible demo default is used by Main itself.
. "$PSScriptRoot\_common.ps1"

$root = Get-RepoRoot
$jar  = Get-AntlrJar
$out  = Join-Path $root "out"

if (-not (Test-Path (Join-Path $out "Main.class"))) {
    Write-Info "Build output missing - building first"
    & "$PSScriptRoot\build.ps1"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

$cp = "$out;$jar"
Push-Location $root
try {
    & java -cp $cp Main @args
    $code = $LASTEXITCODE
}
finally {
    Pop-Location
}
exit $code
