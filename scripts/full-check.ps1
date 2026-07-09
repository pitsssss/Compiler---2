$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

$startTime = Get-Date

function Write-Step {
    param([string]$Message)

    Write-Host ""
    Write-Host "============================================================"
    Write-Host $Message
    Write-Host "============================================================"
}

function Invoke-ProjectScript {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ScriptPath,

        [string[]]$ScriptArguments = @(),

        [int[]]$ExpectedExitCodes = @(0)
    )

    Write-Host "Running: $ScriptPath $($ScriptArguments -join ' ')"

    & powershell.exe `
        -NoProfile `
        -ExecutionPolicy Bypass `
        -File $ScriptPath `
        @ScriptArguments

    $exitCode = $LASTEXITCODE

    if ($ExpectedExitCodes -notcontains $exitCode) {
        throw "Command failed: $ScriptPath. Exit code: $exitCode. Expected: $($ExpectedExitCodes -join ', ')"
    }

    Write-Host "[PASS] Exit code: $exitCode"
}

try {
    Write-Step "1/10 - Cleaning the project"

    Invoke-ProjectScript `
        -ScriptPath ".\scripts\clean.ps1"

    Write-Step "2/10 - Generating ANTLR sources"

    Invoke-ProjectScript `
        -ScriptPath ".\scripts\generate-antlr.ps1"

    $generatedAntlrFiles = @(
        ".\src\antlr\FlaskLexer.java",
        ".\src\antlr\FlaskParser.java",
        ".\src\antlr\FlaskParserBaseVisitor.java",
        ".\src\antlr\TemplateLexer.java",
        ".\src\antlr\TemplateParser.java",
        ".\src\antlr\TemplateParserBaseVisitor.java"
    )

    foreach ($file in $generatedAntlrFiles) {
        if (-not (Test-Path $file)) {
            throw "Missing generated ANTLR file: $file"
        }
    }

    Write-Host "[PASS] Required ANTLR Java files exist."

    Write-Step "3/10 - Building the Java compiler"

    Invoke-ProjectScript `
        -ScriptPath ".\scripts\build.ps1"

    $javaSourceCount = (
        Get-ChildItem .\src -Recurse -Filter *.java
    ).Count

    $classFileCount = (
        Get-ChildItem .\out -Recurse -Filter *.class
    ).Count

    Write-Host "Java source files: $javaSourceCount"
    Write-Host "Compiled class files: $classFileCount"

    if ($classFileCount -eq 0) {
        throw "Build finished without producing any .class files."
    }

    Write-Step "4/10 - Analyzing the valid demo"

    Invoke-ProjectScript `
        -ScriptPath ".\scripts\run.ps1" `
        -ScriptArguments @(
            "--flask",
            ".\examples\demo\app.py",
            "--template",
            ".\examples\demo\templates",
            "--analyze-only"
        )

    Write-Step "5/10 - Generating the Flask application"

    Invoke-ProjectScript `
        -ScriptPath ".\scripts\run.ps1" `
        -ScriptArguments @(
            "--flask",
            ".\examples\demo\app.py",
            "--template",
            ".\examples\demo\templates",
            "--generate"
        )

    Write-Step "6/10 - Verifying generated Flask files"

    $requiredGeneratedFiles = @(
        ".\generated-app\app.py",
        ".\generated-app\requirements.txt",
        ".\generated-app\README.md",
        ".\generated-app\templates\base.html",
        ".\generated-app\templates\products.html",
        ".\generated-app\templates\product_details.html",
        ".\generated-app\templates\add_product.html",
        ".\generated-app\templates\not_found.html",
        ".\generated-app\static\style.css",
        ".\generated-app\tests\test_app.py"
    )

    $missingGeneratedFiles = @()

    foreach ($file in $requiredGeneratedFiles) {
        if (-not (Test-Path $file)) {
            $missingGeneratedFiles += $file
            continue
        }

        if ((Get-Item $file).Length -eq 0) {
            throw "Generated file is empty: $file"
        }
    }

    if ($missingGeneratedFiles.Count -gt 0) {
        throw "Missing generated files:`n$($missingGeneratedFiles -join "`n")"
    }

    Write-Host "[PASS] All 10 generated Flask files exist and are non-empty."

    Write-Step "7/10 - Running Java and Flask test suites"

    Invoke-ProjectScript `
        -ScriptPath ".\scripts\test.ps1"

    Write-Step "8/10 - Testing invalid input"

    $invalidApp = ".\examples\invalid\app.py"
    $invalidTemplates = ".\examples\invalid\templates"

    if (
        (Test-Path $invalidApp) -and
        (Test-Path $invalidTemplates)
    ) {
        Invoke-ProjectScript `
            -ScriptPath ".\scripts\run.ps1" `
            -ScriptArguments @(
                "--flask",
                $invalidApp,
                "--template",
                $invalidTemplates,
                "--analyze-only"
            ) `
            -ExpectedExitCodes @(2)

        Write-Host "[PASS] Invalid input was rejected with exit code 2."
    }
    else {
        throw "Invalid fixtures were not found under examples\invalid."
    }

    Write-Step "9/10 - Verifying AST output"

    if (-not (Test-Path ".\ast.dot")) {
        throw "ast.dot was not found."
    }

    $astFile = Get-Item ".\ast.dot"

    if ($astFile.Length -eq 0) {
        throw "ast.dot exists but is empty."
    }

    Write-Host "AST file: $($astFile.FullName)"
    Write-Host "AST size: $($astFile.Length) bytes"
    Write-Host "AST modified: $($astFile.LastWriteTime)"
    Write-Host "[PASS] ast.dot exists and is non-empty."

    $dotCommand = Get-Command dot -ErrorAction SilentlyContinue

    if ($null -ne $dotCommand) {
        & dot -Tsvg ".\ast.dot" -o ".\ast.svg"

        if ($LASTEXITCODE -ne 0) {
            throw "Graphviz failed to generate ast.svg."
        }

        Write-Host "[PASS] AST SVG generated: ast.svg"
    }
    else {
        Write-Host "[INFO] Graphviz is not installed; ast.dot was verified without creating ast.svg."
    }

    Write-Step "10/10 - Checking for hard-coded user paths"

    $filesToCheck = Get-ChildItem `
        .\src, .\scripts `
        -Recurse `
        -File `
        -Include *.java, *.ps1, *.json

    $absolutePaths = $filesToCheck |
        Select-String -Pattern 'C:\\Users\\' -SimpleMatch

    if ($absolutePaths) {
        Write-Host "[WARNING] Hard-coded Windows user paths were found:"

        $absolutePaths |
            ForEach-Object {
                Write-Host "$($_.Path):$($_.LineNumber) $($_.Line.Trim())"
            }

        throw "Hard-coded user paths must be removed."
    }

    Write-Host "[PASS] No hard-coded C:\Users\ paths were found in source or scripts."

    $duration = (Get-Date) - $startTime

    Write-Host ""
    Write-Host "============================================================"
    Write-Host "FULL PROJECT CHECK PASSED"
    Write-Host "============================================================"
    Write-Host "Java source files: $javaSourceCount"
    Write-Host "Compiled class files: $classFileCount"
    Write-Host "Generated Flask files: 10"
    Write-Host "Valid demo: accepted"
    Write-Host "Invalid demo: rejected correctly"
    Write-Host "Java tests: passed"
    Write-Host "Flask pytest tests: passed"
    Write-Host "AST: verified"
    Write-Host "Duration: $($duration.ToString())"
    Write-Host "============================================================"

    exit 0
}
catch {
    Write-Host ""
    Write-Host "============================================================"
    Write-Host "FULL PROJECT CHECK FAILED"
    Write-Host "============================================================"
    Write-Host $_.Exception.Message
    Write-Host "============================================================"

    exit 1
}