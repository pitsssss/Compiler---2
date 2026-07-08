# 03 – Architecture

## Overview

The compiler is a plain Java project (no Maven/Gradle) driven by ANTLR4. It
takes a Flask Python file plus a folder of Jinja2/HTML/CSS templates, analyses
both, verifies the links between them, and generates a runnable Flask app.

```
Input files
   → Lexer (ANTLR)
   → Parser (ANTLR) → Parse Tree
   → Visitor → AST (Python tree + Template tree)
   → Symbol Table
   → Semantic Analysis (Python + Template + Cross-language)
   → Generator → generated-app/
```

Every phase reports problems to a single `DiagnosticReporter`; nothing prints
errors directly. The pipeline fails (non-zero exit) if any error is reported.

## Folder map

| Folder | Responsibility |
|--------|----------------|
| `src/antlr/` | The four `.g4` grammars and the ANTLR-generated lexers/parsers/visitors |
| `src/AST/` | Python/Flask AST nodes (`expr`, `literal`, `stmt`, `suite`) + base `ASTNode` |
| `src/AST/template/` | Template AST nodes (`html`, `css`, `jinja`) + `TemplateVisitor` |
| `src/AST/visitor/` | `ProgramVisitor`/`FlaskVisitor` — parse tree → Python AST |
| `src/SymbolTable/` | `FlaskSymbolTable`, `SymbolTableVisitor` (scopes, symbols, usages) |
| `src/model/` | `AppModel` + `AppModelBuilder` (routes, render calls, url_for, product data) |
| `src/semantic/` | `PythonSemanticAnalyzer`, `TemplateSemanticAnalyzer`, `CrossLanguageSemanticAnalyzer` |
| `src/diagnostics/` | Structured error model + reporter + ANTLR error listener |
| `src/generator/` | `FlaskAppGenerator` — emits the Flask project |
| `src/pipeline/` | `CompilerPipeline` — the single ordered orchestrator |
| `src/Tests/` | Java test suite (`TestRunner`, `TestSupport`) + input fixtures |
| `scripts/` | PowerShell build/run/test/clean/generate scripts |
| `examples/demo/` | Demo Flask app + templates used by the default run |
| `generated-app/` | Output produced by the generator |
| `docs/` | This documentation set |

## Key classes and their roles

- **`CompilerPipeline`** – wires all phases in order; owns the `DiagnosticReporter`
  and decides success/failure. `Main` only parses CLI args and calls it.
- **`FlaskVisitor` / `ProgramVisitor`** – turn the Flask parse tree into a clean
  AST (`Program` root), discarding punctuation and grammar noise.
- **`TemplateVisitor`** – turns the template parse tree into the HTML/CSS/Jinja AST.
- **`AppModelBuilder`** – walks the Python AST to build an `AppModel`: routes,
  `render_template` calls (template name + context names), `url_for` endpoints,
  global variables, and the product list/schema. This is the bridge the two
  trees share.
- **`*SemanticAnalyzer`** – three analyzers, one per concern; each reports to the
  shared reporter and collects *all* problems (does not stop at the first).
- **`FlaskAppGenerator`** – reads the `AppModel` and writes `app.py`, templates,
  `static/style.css`, `tests/test_app.py`, `requirements.txt`, `README.md`.

## Relationship between the two ASTs

The Python AST and the Template AST are built independently by different
visitors. They are connected through the `AppModel`:

```
Python AST ──AppModelBuilder──► AppModel (routes, renderCalls, productFields, rows)
                                   │
Template AST ──────────────────────┘  used by
                                   CrossLanguageSemanticAnalyzer  and  FlaskAppGenerator
```

- The **cross-language analyzer** checks that every `render_template` target
  exists, every `url_for` endpoint is defined, every context variable used in a
  template is actually passed, and every product key accessed exists in the
  Python-derived schema.
- The **generator** copies the real product rows and field schema extracted from
  Python into the generated `app.py` and templates — proving the data flows from
  one tree to the other rather than being hard-coded.

## Execution flow (success path)

1. `Main` builds a `Config` from CLI args (or demo defaults).
2. `CompilerPipeline.run` parses Flask → AST → symbol table → `AppModel` →
   Python semantic analysis, writing `ast.dot`.
3. It parses every template → AST → template semantic analysis.
4. It runs the cross-language analyzer over the model + templates.
5. It prints the diagnostics report. If there are errors → exit 2.
6. If `--generate`, it writes `generated-app/`.
