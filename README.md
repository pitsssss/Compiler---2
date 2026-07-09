# Flask/Jinja2 Compiler (ANTLR4 + Java)

A compiler front-end **and** code generator built with **ANTLR4** and plain
**Java**. It reads a Flask (Python) application plus its Jinja2/HTML/CSS
templates, performs full analysis (lexing, parsing, AST construction, symbol
table, and multi-stage semantic analysis), and then **generates a complete,
runnable Flask web application**.

The pipeline is verified end to end: the generated app is exercised by an
automated pytest suite using the Flask test client.

## Description

The compiler handles two language domains, each with its own lexer, parser, and
AST:

1. **Flask (Python subset)** — imports, assignments, functions, decorators
   (`@app.route`), control flow, calls with keyword args, and list/set/dict
   literals.
2. **Templates** — HTML, internal CSS, and Jinja2 (`{{ }}` / `{% %}`).

It then **links** the two: routes, `render_template` context, `url_for`
endpoints, and the product data/schema are extracted from the Python AST and
validated against the templates, then passed into the generated app.

```
Input files → Lexer → Parser → Parse Tree → AST → Symbol Table
            → Semantic Analysis (Python + Template + Cross-language)
            → Generator → generated-app/
```

## Requirements

See **[ENVIRONMENT.md](ENVIRONMENT.md)** for tested tool versions (JDK 21, Python 3.14, ANTLR 4.13.2, etc.).

- **JDK 21** (`java`/`javac` on PATH)
- **ANTLR 4.13.2** — bundled at `lib/antlr-4.13.2-complete.jar`
- **Python 3.x** — for generated Flask app / pytest
- **Windows + PowerShell** for scripts (raw `javac`/`java` works on any OS)

## Build

```powershell
./scripts/generate-antlr.ps1   # regenerate ANTLR sources from the .g4 grammars
./scripts/build.ps1            # compile everything into out/
```

Raw commands (no scripts):

```powershell
$javaFiles = Get-ChildItem .\src -Recurse -Filter *.java | ForEach-Object { $_.FullName }
javac -encoding UTF-8 -cp ".\lib\antlr-4.13.2-complete.jar" -d .\out $javaFiles
```

## Run

```powershell
# Demo (uses examples/demo) and generate the Flask app:
./scripts/run.ps1

# Explicit inputs:
./scripts/run.ps1 --flask examples/demo/app.py --template examples/demo/templates --generate
```

Raw command:

```powershell
java -cp ".\out;.\lib\antlr-4.13.2-complete.jar" Main --flask examples/demo/app.py --template examples/demo/templates --generate
```

### CLI options

| Option | Effect |
|--------|--------|
| `--flask <path>` | Flask Python source file |
| `--template <path>` | template file or folder |
| `--output <path>` | output directory (default `generated-app`) |
| `--generate` | generate the Flask app |
| `--analyze-only` | analyse without generating |
| `--print-python-ast` | print the Python AST |
| `--print-template-ast` | print the template AST(s) |
| `--print-symbol-table` | print the symbol table |
| `--export-ast` | export python/template/data-flow DOT files |
| `--ast-output <path>` | DOT output dir (default `docs/diagrams/generated`) |
| `--help` | usage |

Exit codes: `0` success, `2` analysis errors, `3` I/O error.

## Test

```powershell
./scripts/test.ps1            # Java suite (79 tests) + generated-app pytest
./scripts/full-check.ps1      # end-to-end verification (12 steps)
./scripts/test-flask-app.ps1  # just the generated app's pytest (creates a venv)
```

## Run the generated Flask app

```powershell
cd generated-app
python -m venv .venv
.\.venv\Scripts\python -m pip install -r requirements.txt
.\.venv\Scripts\python app.py
# open http://127.0.0.1:5000
```

## Project structure

```
src/antlr/         four .g4 grammars + ANTLR-generated Java
src/AST/           Python AST nodes + template AST (html/css/jinja) + printers
src/AST/visitor/   parse tree -> Python AST
src/SymbolTable/   FlaskSymbolTable + SymbolTableVisitor
src/model/         AppModel + AppModelBuilder (the Python<->template bridge)
src/semantic/      Python / Template / Cross-language analyzers
src/diagnostics/   structured errors + reporter + ANTLR error listener
src/generator/     FlaskAppGenerator
src/pipeline/      CompilerPipeline (orchestrator)
src/Main.java      CLI
src/Tests/         Java test suite + input fixtures
scripts/           generate-antlr / build / run / test / clean (PowerShell)
examples/demo/     demo Flask app + templates
generated-app/     generated output
docs/              full documentation (01–10)
```

## Supported semantic errors

Distinguished as **lexical / syntax / semantic / warning**, each with a code,
message, file, line, column, and a fix suggestion.

- **Python/Flask**: undefined variable (PY001), duplicate function (PY002),
  duplicate parameter (PY003), `return` outside function (PY004),
  `break`/`continue` outside loop (PY005), call to undefined function (PY006),
  duplicate route path (PY007).
- **Templates**: duplicate block (TPL001), misplaced `extends` (TPL002), missing
  include (TPL003), unknown filter (TPL004), duplicate HTML id (TPL005), unknown
  Jinja global (TPL006).
- **Cross-language**: missing render target (X001), undefined `url_for` endpoint
  (X002), context variable not passed (X003), details template missing
  `product` (X004), product key not in schema (X005).

## Documentation

See `docs/`: audit (01), traceability (02), architecture (03), grammar (04),
AST (05), symbol table & semantics (06), code generation (07), testing (08),
changelog (09), final report (10).

## License

Educational use.
