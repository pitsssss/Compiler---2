# 09 – Changelog & Implementation Report

Each entry: problem → cause → file(s) → solution → how tested → result →
requirement satisfied.

## 1. False "already declared" warnings (route / args / get / append)

- **Problem**: running the compiler spammed `Symbol 'route' already declared`,
  and the same for `args`, `get`, `append`.
- **Cause**: attribute accesses and built-in method calls were stored as new
  declarations in the current scope, so repeated `@app.route(...)` collided.
- **Files**: `SymbolTable/FlaskSymbolTable.java`, `SymbolTable/SymbolTableVisitor.java`.
- **Solution**: split *declarations* from *uses*. Attribute accesses →
  `attributeUsages`; built-in calls → `builtinUsages`; built-ins registered for
  lookup only. `addSymbol` returns `false` on true same-scope duplicates without
  printing. String parsing was replaced with AST getters.
- **Tested**: Symbol Table tests (attribute-use not duplicate; real duplicate
  rejected; built-ins not declarations).
- **Result**: warnings gone; real duplicates still caught. **Req 12 satisfied.**

## 2. Grammar could not parse dicts / empty / single-element collections

- **Problem**: real Flask code with `{...}` dicts and `[]`/`[x]` failed to parse.
- **Cause**: `list_literal`/`set_literal` required ≥2 elements; no `dict_literal`.
- **Files**: `src/antlr/FlaskParser.g4`, `FlaskVisitor.java`, new
  `AST/literal/DictLiteralExpr.java`.
- **Solution**: relaxed list/set rules; added `dict_literal`/`dict_entry`; added
  keyword-argument support in calls; visitor builds `DictLiteralExpr` and keyword
  args.
- **Tested**: Lexer/Parser + AST tests (multi-line dict/list; dict node).
- **Result**: real product data parses. **Req 1 improved.**

## 3. Newlines inside brackets broke multi-line literals

- **Problem**: multi-line lists/dicts produced spurious syntax errors.
- **Cause**: the `NEWLINE` lexer rule emitted NEWLINE/INDENT even inside
  `()[]{}`.
- **File**: `src/antlr/FlaskLexer.g4`.
- **Solution**: track an `opened` counter and `skip()` newlines while `opened > 0`
  (Python implicit line joining).
- **Tested**: multi-line literal parse test; demo `app.py` parses with 0 errors.
- **Result**: fixed. **Req 1 improved.**

## 4. No structured diagnostics; errors printed ad-hoc

- **Problem**: errors were `System.err`/`System.out` prints, no codes, no
  severity separation.
- **Cause**: no diagnostics subsystem.
- **Files**: new `src/diagnostics/*`, plus a custom `CompilerErrorListener`.
- **Solution**: `Diagnostic`/`DiagnosticKind`/`DiagnosticSeverity`/
  `DiagnosticReporter`; ANTLR listener converts syntax/lexical errors into
  diagnostics. The pipeline decides success from the error count.
- **Tested**: invalid-input tests; report format verified in runs.
- **Result**: lexical/syntax/semantic/warning distinguished with file:line:col +
  hint. **Req 7 satisfied.**

## 5. Missing semantic analysis

- **Problem**: no real semantic checks.
- **Files**: new `src/semantic/PythonSemanticAnalyzer.java`,
  `TemplateSemanticAnalyzer.java`, `CrossLanguageSemanticAnalyzer.java`; plus
  `src/model/*` for the shared application model.
- **Solution**: 18 error types (PY001–PY007, TPL001–TPL006, X001–X005), all
  collected in one pass.
- **Tested**: 18 dedicated semantic/cross tests + clean-input tests.
- **Result**: **Reqs 5, 6 satisfied.**

## 6. Python ↔ Jinja2 linking absent

- **Problem**: the two trees were analysed in isolation; no proof of a link.
- **Files**: `src/model/AppModelBuilder.java`, `CrossLanguageSemanticAnalyzer.java`,
  `generator/FlaskAppGenerator.java`.
- **Solution**: `AppModel` carries routes, render calls (template + context),
  url_for endpoints, and product schema/rows extracted from Python; the cross
  analyzer validates templates against it; the generator copies the data into the
  output.
- **Tested**: Cross-language tests (X001/X002/X003/X005) + generator data test.
- **Result**: **Reqs 3, 6 satisfied.**

## 7. No working generator / generated app

- **Problem**: the generator was not producing a runnable app.
- **File**: `src/generator/FlaskAppGenerator.java`.
- **Solution**: emits a full CRUD Flask project (app, templates, static, tests,
  requirements, README) split into per-file methods, driven by the `AppModel`.
- **Tested**: generator unit test + generated pytest (8 checks) in a venv.
- **Result**: `8 passed`. **Reqs 8, 9 satisfied.**

## 8. Main used hard-coded paths; no CLI

- **Problem**: `Main` hard-coded an absolute demo path; no pipeline separation.
- **Files**: `src/Main.java`, `src/pipeline/CompilerPipeline.java`.
- **Solution**: `Main` only parses args (`--flask/--template/--output/--print-*/
  --generate/--analyze-only/--help`) and delegates to `CompilerPipeline`;
  relative demo defaults; proper exit codes; clear errors for missing files.
- **Tested**: manual runs (`--help`, missing file, demo).
- **Result**: **Req 13 satisfied.**

## 9. Robustness: malformed input no longer crashes

- **Problem**: malformed source caused an NPE while building the AST.
- **File**: `src/pipeline/CompilerPipeline.java`.
- **Solution**: after parsing, if there are already lexical/syntax errors, stop
  before AST construction — the reported errors fail the run cleanly (no stack
  trace).
- **Tested**: "invalid Flask program reports a syntax error" test.
- **Result**: expected errors never print stack traces. **Req 7 reinforced.**

## 10. Reproducible build + tidy tree

- **Files**: `scripts/*.ps1`, `.vscode/settings.json`, `.gitignore`.
- **Solution**: PowerShell scripts for generate/build/run/test/clean; settings
  point at `src`/`out`/`lib/**/*.jar`; `.gitignore` excludes build output, Python
  caches, and venvs while keeping the grammars, the ANTLR jar, and settings.
- **Result**: **Req 11 satisfied.**

## Housekeeping

- Removed stray debug prints (`Number of statements = ...`, `Visiting: ...`).
- Legacy, unused classes (`SymbolTable/Scope.java`, `SymbolTable/Symbol.java`,
  `TemplateSymbolTable`, `JinjaASTVisitor`) are documented in the audit; the live
  path uses `FlaskSymbolTable` with its own nested `Scope`/`Symbol`.
