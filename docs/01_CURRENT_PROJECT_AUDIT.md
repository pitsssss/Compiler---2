# 01 – Current Project Audit (Baseline Before Changes)

> This document records the state of the project **before** any modifications, based on
> reading every source file and actually building and running the project.

## 1. How the baseline was verified

```
# Build (from repo root)
Get-ChildItem -Recurse src -Filter *.java | %{ '"'+($_.FullName -replace '\\','/')+'"' } | Set-Content -Encoding ASCII sources.txt
javac -encoding UTF-8 -cp lib\antlr-4.13.2-complete.jar -d out @sources.txt   # EXIT 0

# Run
java -cp "out;lib\antlr-4.13.2-complete.jar" Main                            # EXIT 0
```

Environment: **JDK 21 (Temurin 21.0.11)**, **Python 3.14.3**, **ANTLR 4.13.2**, Windows.

The build succeeds. Running `Main` prints `Number of statements = 8`, a set of
false-positive `already declared` warnings, and a symbol table dump.

## 2. Current structure

```
src/
  Main.java                       # hardcoded entry point (only Flask path active)
  antlr/                          # 4 .g4 grammars + generated Java (git-ignored)
    FlaskLexer.g4 / FlaskParser.g4
    TemplateLexer.g4 / TemplateParser.g4
  AST/
    ASTNode.java (base), Program.java
    ASTGraphvizPrinter.java
    expr/ literal/ stmt/ suite/   # Flask/Python AST hierarchy
    template/                     # Template AST (html/ css/ jinja/)
    visitor/                      # ProgramVisitor, FlaskVisitor
  SymbolTable/
    FlaskSymbolTable.java, SymbolTableVisitor.java (Flask side, working)
    TemplateSymbolTable.java, JinjaASTVisitor.java (template side)
    Scope.java, Symbol.java       # older/duplicate abstractions (unused by Flask table)
  Tests/                          # sample INPUT files only (run manually), no assertions
lib/antlr-4.13.2-complete.jar
ast.dot
README.md                         # outdated (says Template AST "not implemented")
```

## 3. Current working mechanism / data flow

```
src/Tests/FinalTests/app.py   (HARDCODED path in Main.java)
   → FlaskLexer → CommonTokenStream → FlaskParser.prog()
   → ProgramVisitor + FlaskVisitor  → Program (Flask AST)
   → ASTGraphvizPrinter.print(program, "ast.dot")
   → SymbolTableVisitor → FlaskSymbolTable.printSymbolTable()
```

The **Template** pipeline (TemplateLexer/Parser → TemplateVisitor → JinjaASTVisitor →
TemplateSymbolTable) is fully **commented out** in `Main.java` and never runs.

## 4. Features actually implemented

| Area | Status | Evidence |
|------|--------|----------|
| Flask lexer + parser (Python subset) | Implemented | `FlaskLexer.g4`, `FlaskParser.g4`, parses `app.py` |
| Python indentation (INDENT/DEDENT) | Implemented | lexer `@members` block |
| Flask AST (OOP hierarchy) | Implemented | `AST/expr,literal,stmt,suite` all extend `ASTNode` |
| Flask parse-tree → AST visitor | Implemented | `FlaskVisitor`, `ProgramVisitor` |
| Template lexer + parser (HTML/CSS/Jinja) | Implemented | `TemplateLexer.g4`, `TemplateParser.g4` |
| Template AST + visitor | Implemented | `AST/template/**`, `TemplateVisitor` |
| Flask symbol table + scopes | Implemented (buggy) | `FlaskSymbolTable`, `SymbolTableVisitor` |
| Template symbol table | Implemented (never wired) | `TemplateSymbolTable`, `JinjaASTVisitor` |
| Graphviz AST export | Implemented | `ASTGraphvizPrinter` |

## 5. Features MISSING (major gaps vs. requirements)

1. **No Semantic Analyzer.** The only checks are ad-hoc `System.err` warnings inside the
   symbol table. No error types, no severities, no file/column info, no fix suggestions.
2. **No Code Generator at all.** There is *no* class that emits a Flask app. This is the
   central deliverable and is entirely absent.
3. **No cross-language linking** between Python data and Jinja templates.
4. **No custom ANTLR error listener** → lexical/syntax errors are printed by ANTLR's
   default listener to stderr and otherwise ignored; parsing continues silently.
5. **No CLI.** `Main.java` hardcodes a single input path; the template pipeline is
   commented out; no arguments, no exit codes, no `--help`.
6. **No real tests.** `src/Tests/**` are only input samples that were run by hand.
7. **No build/run/test scripts.**
8. **Docs/README outdated.**

## 6. Design bugs / issues found

1. **False `already declared` warnings (route/args/get/append).**
   `SymbolTableVisitor.visitAttributeExpression` / `processDecoratorAttribute` call
   `FlaskSymbolTable.addAttribute(base, attribute, line)`, which stores a symbol **named
   after the attribute** (`route`, `args`, `get`, …) in the *current scope*. The second
   `@app.route(...)` then collides with the first → false duplicate. Attribute **uses**
   and built-in method **uses** are wrongly treated as declarations.
   *Evidence:* run output shows `Symbol 'route' already declared … at line 11/18/25/42/49`,
   `'args'`/`'get'` at line 27, `'append'` at line 53.

2. **String-parsing instead of AST.** `visitFromImport` and the attribute extractor use
   `toString()` + `substring`/`replaceAll`/`split` to recover names that already exist as
   typed AST fields. Fragile and against the "use AST not regex" rule.

3. **Two conflicting `Symbol`/`Scope` designs.** `SymbolTable/Symbol.java` +
   `SymbolTable/Scope.java` are a separate, older model that the Flask table does not use
   (it uses its own nested `FlaskSymbolTable.Symbol` / `FlaskSymbolTable.Scope`). Dead/duplicated.

4. **Grammar: list/set literals require ≥ 2 elements.**
   `list_literal : LBRACK expr COMMA expr (COMMA expr)* COMMA? RBRACK` → cannot parse `[]`
   or `[x]`. Same for `set_literal`. Limits real inputs.

5. **No dictionary literal** in the Flask grammar, yet the FinalTests templates use
   `product.name`, `product.price`, `product.in_stock` (dict/attribute access) while
   `app.py` defines `products` as a **list of lists**. This is a genuine
   Python↔Jinja data-contract mismatch that nothing currently detects or reconciles.

6. **Silent exception swallowing / broad `catch (Exception)`** in `SymbolTableVisitor`
   (import + attribute handling) — hides real problems.

7. **Hardcoded absolute path** via `Paths.get("src/Tests/FinalTests/app.py").toAbsolutePath()`.

8. **`.gitignore` ignores generated ANTLR `*.java` and the `*.jar`** — acceptable, but the
   grammar → Java generation step must be reproducible (no script currently exists).

## 7. Requirements coverage (high level; detailed table in `02_REQUIREMENTS_TRACEABILITY.md`)

| # | Requirement | Baseline status |
|---|-------------|-----------------|
| 1 | Lexer/Parser for Python-Flask, Jinja2, HTML, CSS | Partial (present, some grammar gaps, no error listener) |
| 2 | Two ASTs + Generator passes Python data → template | Partial (two ASTs exist; **no generator, no linking**) |
| 3 | Node OOP (encapsulation/inheritance/polymorphism, line info) | Partial (line only; no column; toString ad-hoc; no unified printer) |
| 4 | ≥ 5 + 5 semantic errors, 4 error classes, rich diagnostics | **Missing** |
| 5 | Code generation → integrated Flask app | **Missing** |
| 6 | Product CRUD UI + navigation | **Missing** (only sample templates) |
| 7 | Printing: nodes, both ASTs, symbol table, scopes | Partial (graphviz + symtab dump; no hierarchical AST printer) |

## 8. Files that need to change / be created

**Fix:** `SymbolTable/SymbolTableVisitor.java`, `SymbolTable/FlaskSymbolTable.java`,
`Main.java`, `README.md`, `.gitignore`, `.vscode/settings.json`.

**Create (new):**
- `src/diagnostics/` – `Diagnostic`, `DiagnosticSeverity`, `DiagnosticKind`, `DiagnosticReporter`, `CompilerErrorListener`.
- `src/semantic/` – `PythonSemanticAnalyzer`, `TemplateSemanticAnalyzer`, `CrossLanguageSemanticAnalyzer`, plus a shared `AppModel` (routes + product data + render calls).
- `src/AST/ASTPrinter.java` – hierarchical text printer for both trees.
- `src/generator/` – `FlaskAppGenerator` and helpers that emit `generated-app/`.
- `src/pipeline/CompilerPipeline.java` – orchestration.
- `scripts/*.ps1` – generate-antlr / build / run / test / clean.
- `src/Tests` Java test harness + fixtures; generated `tests/test_app.py`.
- `docs/*.md`, `TEAM_AND_REPOSITORY.txt`.

## 9. Execution plan (priority order)

1. Reproducible build/run scripts + `.vscode` settings. *(low risk, enables everything)*
2. Fix symbol-table false duplicates (attributes/built-ins are **uses**, not declarations),
   remove string-parsing, keep real duplicate detection.
3. Diagnostics framework + ANTLR error listener (lexical/syntax/semantic/warning classes).
4. Semantic analysis: Python (≥5), Template (≥5), cross-language linking.
5. AST hierarchical printer for both trees; regenerate `ast.dot` from real AST.
6. Generator → working `generated-app/` (AST-driven routes + product data → Flask+Jinja+CSS).
7. CLI (`Main` + `CompilerPipeline`) with args, exit codes, `--help`.
8. Tests: Java unit/integration + end-to-end + generated Flask app (Flask test client).
9. Docs (02–10), README, TEAM file, `.gitignore`.
