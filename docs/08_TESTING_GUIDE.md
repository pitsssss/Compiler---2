# 08 – Testing Guide

## Prerequisites

- **JDK 21** (`javac`/`java` on PATH). Verify: `java -version`.
- **ANTLR jar** already in `lib/antlr-4.13.2-complete.jar`.
- **Python 3.x** (optional, only for the generated-app tests). Verify:
  `python --version`.

All commands are run from the project root in PowerShell.

## 1. Generate ANTLR sources

```powershell
./scripts/generate-antlr.ps1
```

## 2. Build

```powershell
./scripts/build.ps1
```

Expected: `Build succeeded -> out/`.

## 3. Run the compiler (demo)

```powershell
./scripts/run.ps1
```

or explicitly:

```powershell
./scripts/run.ps1 --flask examples/demo/app.py --template examples/demo/templates --generate
```

Expected tail:

```
Errors: 0   Warnings: 0
Analysis completed successfully.
Generated 10 files:
```

## 4. Run the test suite

```powershell
./scripts/test.ps1
```

This builds, runs the Java suite (`tests.TestRunner`), and — if Python is
available — creates an isolated venv and runs the generated app's pytest suite.

### Java test suite coverage (30 tests, all passing)

- **Lexer/Parser**: valid program parses; invalid program reports a syntax
  error; multi-line dict/list literal; valid template parses.
- **AST**: function node name/params/line; dict literal node.
- **Symbol Table**: attribute uses aren't duplicates; real duplicate rejected;
  built-ins resolve but aren't declarations; import lookup.
- **Semantic (Python)**: PY001–PY007 each triggered; a clean program has 0
  errors; multiple errors collected in one run.
- **Semantic (Template)**: TPL001, TPL003, TPL004; clean template has 0 errors.
- **Cross-language**: X001, X002, X003, X005; clean wiring has 0 linking errors.
- **Generator**: writes ≥8 non-empty files; `app.py` carries the product data
  from the Python AST; delete route is POST.

### Generated Flask app tests (8 tests, all passing)

Run only the app tests:

```powershell
./scripts/test-flask-app.ps1
```

They use the Flask test client to assert:

| Check | Expected |
|-------|----------|
| `GET /products` | 200 |
| `GET /` | redirect to list |
| `GET /products/add` | 200 |
| `POST /products/add` (valid) | adds product, redirect |
| `GET /products/<existing>` | 200 |
| `GET /products/<missing>` | 404 |
| `POST /products/<id>/delete` | removes product, redirect |
| `GET /products/<id>/delete` | 405 (POST only) |

## Expected vs. actual (verified)

| Step | Expected | Actual (verified) |
|------|----------|-------------------|
| `generate-antlr.ps1` | generates lexers + parsers | `ANTLR generation complete` |
| `build.ps1` | compiles all sources | `Build succeeded -> out/` (121 files) |
| `run.ps1` (demo) | 0 errors, 10 files | `Errors: 0`, `Generated 10 files` |
| `test.ps1` (Java) | all pass | `Passed: 30   Failed: 0` |
| `test-flask-app.ps1` | pytest passes | `8 passed` |

## Clean

```powershell
./scripts/clean.ps1
```

Removes `out/` and temporary/cache files only — never source or user files.
