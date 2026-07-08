# 10 – Final Report

## Introduction

This project is a compiler that reads a Flask (Python) application together with
its Jinja2/HTML/CSS templates, performs full front-end compiler analysis
(lexing, parsing, AST construction, symbol-table building, and multi-stage
semantic analysis), and then generates a complete, runnable Flask web
application. It is built in plain Java with ANTLR4 and runs on Windows via
PowerShell scripts.

## Objectives

- Build lexers/parsers and grammars for the Python(Flask) subset and for
  Jinja2 + HTML + CSS.
- Construct two distinct ASTs and prove a real link between them.
- Provide an OOP node hierarchy with line information.
- Detect a strong set of semantic errors on both sides and in the linking.
- Generate a working Flask CRUD app whose data comes from the analysed Python.
- Ship reproducible build/run/test tooling and full documentation.

## Technologies

- Java (JDK 21), ANTLR 4.13.2.
- Flask + Jinja2 (generated app), pytest (generated-app tests).
- PowerShell (build automation), Graphviz DOT (AST diagram, optional).

## Compiler phases

1. **Lexer** — `FlaskLexer.g4` (with Python INDENT/DEDENT and implicit line
   joining) and the mode-based `TemplateLexer.g4`.
2. **Parser** — `FlaskParser.g4` (root `prog`) and `TemplateParser.g4`
   (root `template`), both consuming `EOF`.
3. **Parse tree → AST** — `FlaskVisitor`/`ProgramVisitor` and `TemplateVisitor`.
4. **AST** — `Program` root (Python) and `HtmlDocument` root (template).
5. **Symbol table** — `FlaskSymbolTable` with scoped declarations and separate
   usage tracking.
6. **Semantic analysis** — Python, Template, and Cross-language analyzers.
7. **Code generation** — `FlaskAppGenerator`.

## The two trees and OOP

- Base `ASTNode` (line/column, children, toString) → `Expression`/`Statement`
  → concrete nodes. Template side: `HtmlNode`/`CssNode`/`JinjaNode` hierarchies.
- Encapsulation (private fields + accessors), inheritance (shared bases),
  polymorphism (`getChildren`/`toString` overrides drive printing, analysis,
  generation).

## Symbol table

Global/function/loop/if/class scopes, inner-to-outer lookup, distinct symbol
kinds (variable/function/parameter/import/attribute/builtin), line numbers, and
function parameter lists. Attribute and built-in **uses** are tracked separately
so they never masquerade as declarations — fixing the old route/args/get/append
warnings while keeping true duplicate detection.

## Semantic analysis

18 error types collected in a single pass, each a structured `Diagnostic`
(kind, severity, code, message, file, line, column, suggestion):

- Python: PY001–PY007.
- Template: TPL001–TPL006.
- Cross-language: X001–X005.

The pipeline prints `Errors: X   Warnings: Y` and treats any error as failure.

## Generator, Flask/Jinja2/HTML/CSS, and the UI

The generator emits `app.py`, `templates/` (base, products, details, add,
not_found), `static/style.css`, `tests/test_app.py`, `requirements.txt`, and a
README — 10 files. The product rows and schema are taken from the analysed
Python. The UI supports listing, adding (with validation and redirect), viewing
details (404 on missing), and deleting (POST-only), with a shared nav bar and
readable CSS.

## Testing

- **Java suite** (`tests.TestRunner`): 30 tests across lexer/parser, AST, symbol
  table, semantic (Python/template/cross), and generator.
- **Generated-app suite** (pytest via Flask test client): 8 tests covering all
  routes and status codes.

## Results (verified in terminal)

```
Build succeeded -> out/            (121 Java files)
Errors: 0   Warnings: 0            (demo analysis)
Generated 10 files                 (generated-app)
Passed: 30   Failed: 0             (Java tests)
8 passed                           (generated Flask app pytest)
```

## Challenges

- Python-style indentation and implicit line joining in an ANTLR lexer.
- Separating symbol *uses* from *declarations* to remove false warnings without
  weakening duplicate detection.
- Establishing a verifiable link between two independently built ASTs.

## Improvements / future work

- Render the AST PNG automatically when Graphviz is present.
- Extend the Python subset (comprehensions, `try/except`).
- Wire the legacy template symbol table into the pipeline or remove it.

## Conclusion

The full pipeline — input files → lexer → parser → AST → symbol table →
semantic analysis → generator → running Flask app — is implemented and verified
end to end, including automated tests for the generated application.

## AST diagram

Real DOT graph is in `ast.dot` (regenerated each run); a Mermaid version is in
`docs/05_AST_AND_NODES.md`.

## Run commands (quick reference)

```powershell
./scripts/generate-antlr.ps1
./scripts/build.ps1
./scripts/run.ps1 --flask examples/demo/app.py --template examples/demo/templates --generate
./scripts/test.ps1
# run the generated app:
cd generated-app; python -m venv .venv; .\.venv\Scripts\python -m pip install -r requirements.txt; .\.venv\Scripts\python app.py
```
