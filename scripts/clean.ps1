# Removes build output and temporary files ONLY. Never touches source, grammars,
# generated-app source, docs, or user files.
. "$PSScriptRoot\_common.ps1"

$root = Get-RepoRoot
$targets = @(
    (Join-Path $root "out"),
    (Join-Path $root "sources.txt"),
    (Join-Path $root "generated-app\.venv"),
    (Join-Path $root "generated-app\.pytest_cache")
)

foreach ($t in $targets) {
    if (Test-Path $t) {
        Remove-Item -Recurse -Force $t
        Write-Ok "Removed $t"
    }
}

# Remove Python caches under generated-app
Get-ChildItem -Path $root -Recurse -Directory -Filter "__pycache__" -ErrorAction SilentlyContinue |
    ForEach-Object { Remove-Item -Recurse -Force $_.FullName }

Write-Ok "Clean complete."
