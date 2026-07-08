# Cleans out/, compiles every .java under src/ with UTF-8 and the ANTLR jar on the classpath.
. "$PSScriptRoot\_common.ps1"

$root = Get-RepoRoot
$jar  = Get-AntlrJar
$out  = Join-Path $root "out"

# If ANTLR-generated files are missing, generate them first.
if (-not (Test-Path (Join-Path $root "src\antlr\FlaskParser.java"))) {
    Write-Info "ANTLR generated files missing - running generate-antlr.ps1"
    & "$PSScriptRoot\generate-antlr.ps1"
}

Write-Info "Cleaning out/"
if (Test-Path $out) { Remove-Item -Recurse -Force $out }
New-Item -ItemType Directory -Path $out | Out-Null

# Build a javac argfile (forward slashes: backslash is an escape char in argfiles).
$sources = Join-Path $root "sources.txt"
Get-ChildItem -Recurse (Join-Path $root "src") -Filter *.java |
    ForEach-Object { '"' + ($_.FullName -replace '\\','/') + '"' } |
    Set-Content -Encoding ASCII $sources

$count = (Get-Content $sources).Count
Write-Info "Compiling $count Java files..."

& javac -encoding UTF-8 -cp $jar -d $out "@$sources"
if ($LASTEXITCODE -ne 0) {
    Write-Fail "Compilation failed (exit $LASTEXITCODE)"
    exit $LASTEXITCODE
}
Write-Ok "Build succeeded -> out/"
