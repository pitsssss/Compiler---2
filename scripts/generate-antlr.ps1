# Regenerates the ANTLR Java files from the .g4 grammars.
# Lexers are generated first (they produce the .tokens the parsers depend on),
# then the parsers/visitors. All output lands in src/antlr with `package antlr`.
. "$PSScriptRoot\_common.ps1"

$root  = Get-RepoRoot
$jar   = Get-AntlrJar
$gram  = Join-Path $root "src\antlr"

Push-Location $gram
try {
    $common = @("-jar", $jar, "-Dlanguage=Java", "-package", "antlr")

    Write-Info "Generating lexers..."
    Invoke-Checked "java" ($common + @("FlaskLexer.g4"))    "FlaskLexer generation failed"
    Invoke-Checked "java" ($common + @("TemplateLexer.g4")) "TemplateLexer generation failed"
    Write-Ok "Lexers generated"

    Write-Info "Generating parsers + visitors..."
    Invoke-Checked "java" ($common + @("-visitor", "FlaskParser.g4"))    "FlaskParser generation failed"
    Invoke-Checked "java" ($common + @("-visitor", "TemplateParser.g4")) "TemplateParser generation failed"
    Write-Ok "Parsers + visitors generated"

    Write-Ok "ANTLR generation complete (src/antlr/*.java)"
}
finally {
    Pop-Location
}
