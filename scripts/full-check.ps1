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
    Write-Step "1/12 - Cleaning the project"

    Invoke-ProjectScript `
        -ScriptPath ".\scripts\clean.ps1"

    Write-Step "2/12 - Generating ANTLR sources"

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

    Write-Step "3/12 - Building the Java compiler"

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

    Write-Step "4/12 - Analyzing the valid demo"

    Invoke-ProjectScript `
        -ScriptPath ".\scripts\run.ps1" `
        -ScriptArguments @(
            "--flask",
            ".\examples\demo\app.py",
            "--template",
            ".\examples\demo\templates",
            "--analyze-only"
        )

    Write-Step "5/12 - Generating the Flask application"

    Invoke-ProjectScript `
        -ScriptPath ".\scripts\run.ps1" `
        -ScriptArguments @(
            "--flask",
            ".\examples\demo\app.py",
            "--template",
            ".\examples\demo\templates",
            "--generate",
            "--export-ast",
            "--ast-output",
            ".\docs\diagrams\generated"
        )

    Write-Step "6/12 - Verifying generated Flask files"

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

    Write-Step "7/12 - Running Java and Flask test suites"

    Invoke-ProjectScript `
        -ScriptPath ".\scripts\test.ps1"

    Write-Step "8/12 - Testing invalid input"

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

    Write-Step "9/12 - Verifying AST output and exported diagrams"

    if (-not (Test-Path ".\ast.dot")) {
        throw "ast.dot was not found."
    }

    $astFile = Get-Item ".\ast.dot"

    if ($astFile.Length -eq 0) {
        throw "ast.dot exists but is empty."
    }

    Write-Host "Legacy AST file: $($astFile.FullName) ($($astFile.Length) bytes)"
    Write-Host "[PASS] ast.dot exists and is non-empty."

    $diagramDir = ".\docs\diagrams\generated"
    $requiredDots = @(
        "python-ast.dot",
        "template-ast.dot",
        "cross-language-data-flow.dot"
    )

    foreach ($dotName in $requiredDots) {
        $dotPath = Join-Path $diagramDir $dotName
        if (-not (Test-Path $dotPath)) {
            throw "Missing exported diagram: $dotPath"
        }
        if ((Get-Item $dotPath).Length -eq 0) {
            throw "Exported diagram is empty: $dotPath"
        }
        Write-Host "[PASS] $dotName exists and is non-empty."
    }

    $pythonDot = Get-Content (Join-Path $diagramDir "python-ast.dot") -Raw
    $templateDot = Get-Content (Join-Path $diagramDir "template-ast.dot") -Raw

    if ($pythonDot -notmatch "FunctionDefStmt|Program|AssignmentStmt") {
        throw "python-ast.dot does not appear to contain Python AST nodes."
    }
    if ($templateDot -notmatch "HtmlDocument|HtmlNormalElement|Jinja") {
        throw "template-ast.dot does not appear to contain Template AST nodes."
    }
    if ($pythonDot -match "HtmlDocument" -or $templateDot -match "FunctionDefStmt") {
        throw "Python and Template DOT files appear to contain cross-contaminated nodes."
    }

    $dotCommand = Get-Command dot -ErrorAction SilentlyContinue

    if ($null -ne $dotCommand) {
        foreach ($dotName in $requiredDots) {
            $svgName = $dotName -replace '\.dot$', '.svg'
            & dot -Tsvg (Join-Path $diagramDir $dotName) -o (Join-Path $diagramDir $svgName)
            if ($LASTEXITCODE -ne 0) {
                throw "Graphviz failed to generate $svgName."
            }
            Write-Host "[PASS] Generated $svgName"
        }
    }
    else {
        Write-Host "[INFO] Graphviz is not installed; DOT files verified without SVG export."
        Write-Host "[INFO] To render: dot -Tsvg docs/diagrams/generated/python-ast.dot -o python-ast.svg"
    }

    Write-Step "10/12 - Verifying documentation and packaging scripts"

    $requiredDocs = @(
        ".\ENVIRONMENT.md",
        ".\docs\FINAL_PROFESSIONAL_AUDIT.md",
        ".\docs\PYTHON_TO_TEMPLATE_DATA_FLOW.md",
        ".\docs\LEGACY_CODE_AUDIT.md",
        ".\docs\SUBMISSION_PACKAGING_GUIDE.md",
        ".\docs\diagrams\ast-class-hierarchy.md",
        ".\docs\diagrams\compiler-pipeline.md",
        ".\docs\diagrams\python-template-data-flow.md",
        ".\docs\diagrams\semantic-analysis-architecture.md"
    )

    foreach ($doc in $requiredDocs) {
        if (-not (Test-Path $doc)) {
            throw "Missing required documentation: $doc"
        }
        if ((Get-Item $doc).Length -eq 0) {
            throw "Documentation file is empty: $doc"
        }
        Write-Host "[PASS] Found $doc"
    }

    if (-not (Test-Path ".\scripts\package-submission.ps1")) {
        throw "Missing scripts\package-submission.ps1"
    }
    Write-Host "[PASS] package-submission.ps1 exists."

    Write-Step "11/12 - Checking for hard-coded user paths"

    $hardCodedUserPathPattern = "C:" + '\' + "Users" + '\'

    $filesToCheck = Get-ChildItem `
        .\src, .\scripts `
        -Recurse `
        -File `
        -Include *.java, *.ps1, *.json |
        Where-Object { $_.Name -ne 'full-check.ps1' }

    $absolutePaths = $filesToCheck |
        Select-String -Pattern $hardCodedUserPathPattern -SimpleMatch

    if ($absolutePaths) {
        Write-Host "[WARNING] Hard-coded Windows user paths were found:"

        $absolutePaths |
            ForEach-Object {
                Write-Host "$($_.Path):$($_.LineNumber) $($_.Line.Trim())"
            }

        throw "Hard-coded user paths must be removed."
    }

    Write-Host "[PASS] No hard-coded user paths were found in source or scripts."

    Write-Step "12/12 - Summary"

    $duration = (Get-Date) - $startTime
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