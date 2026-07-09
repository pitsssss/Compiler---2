# 02 – Requirements Traceability

Each official requirement is mapped to its implementation, the tests that prove
it, and the evidence (verified terminal output). Status legend:
**Complete** / Partial / Missing.

| # | Requirement | Implementation (files) | Tests | Evidence | Status |
|---|-------------|------------------------|-------|----------|--------|
| 1 | Lexer + Parser for Python(Flask), Jinja2, HTML, CSS | `src/antlr/FlaskLexer.g4`, `FlaskParser.g4`, `TemplateLexer.g4`, `TemplateParser.g4` | `TestRunner` Lexer/Parser section | 30/30 Java tests pass; demo parses with 0 syntax errors | **Complete** |
| 2 | Two separate ASTs (Python + Template) | `src/AST/**` (Python), `src/AST/template/**` (Jinja/HTML/CSS) | AST section tests | `ast.dot` generated from real Python AST; template AST printed with `--print-template-ast` | **Complete** |
| 3 | Generator passes Python data into the template tree | `AppModelBuilder` → `AppModel` → `FlaskAppGenerator`; `CrossLanguageSemanticAnalyzer` | Generator + Cross-language tests | Generated `app.py` contains the 3 products extracted from the parsed source | **Complete** |
| 4 | OOP node hierarchy (encapsulation/inheritance/polymorphism), name+line+children+value | `ASTNode` (base), `Expression`/`Statement`/`LiteralExpr`, all concrete nodes | AST tests (name/params/line) | `function def node carries name, params and a line number` PASS | **Complete** |
| 5 | ≥5 Python semantic errors | `PythonSemanticAnalyzer` (PY001–PY011) | 19 Python semantic tests | All PY tests PASS; demo 0 errors | **Complete** |
| 6 | ≥5 template/cross semantic errors | `TemplateSemanticAnalyzer` (TPL001–TPL010), `CrossLanguageSemanticAnalyzer` (X001–X011) | 38 template/cross tests | All TPL/X tests PASS | **Complete** |
| 7 | Distinguish lexical/syntax/semantic/warning + structured messages | `diagnostics/*` | invalid-input + line tests | `[ERROR PY001] file:line:col` + `Suggestion:` line | **Complete** |
| 8 | Generate a complete, runnable Flask app (not scattered files) | `FlaskAppGenerator`, `CompilerPipeline.generate` | Generated Flask app pytest (8) | `8 passed in 0.18s` in isolated venv | **Complete** |
| 9 | UI: list/add/details/delete + navigation, POST delete, redirects, CSS, empty/404 handling | `FlaskAppGenerator` (templates + routes + css) | pytest client tests | list 200, add GET 200, add POST redirect, details 200, missing 404, delete POST redirect, delete GET 405 | **Complete** |
| 10 | Printing: per-node, children, full hierarchical AST (both trees), symbol table, scopes | `ASTPrinter`, `ASTGraphvizPrinter`, `FlaskSymbolTable.printSymbolTable` | manual `--print-*` flags | `--print-python-ast`, `--print-template-ast`, `--print-symbol-table` render trees + table | **Complete** |
| 11 | Reproducible build/run on Windows | `scripts/*.ps1`, `.vscode/settings.json` | `build.ps1`, `test.ps1` | `Build succeeded`; `All tests passed.` | **Complete** |
| 12 | No false route/args/get/append warnings; real duplicate detection kept | `FlaskSymbolTable` (usages vs declarations), `SymbolTableVisitor` | Symbol Table tests | attribute-use test PASS; duplicate-declaration test PASS | **Complete** |
| 13 | CLI without absolute paths, exit codes, help | `Main.java`, `CompilerPipeline` | manual run | `--help` prints usage; exit 0 success / 2 on analysis errors | **Complete** |
| 14 | Documentation + reports | `docs/01`–`docs/10`, `README.md`, `TEAM_AND_REPOSITORY.txt` | — | this folder | **Complete** |

## Semantic error catalogue (32 types across Python, template, and linking)

Full per-code reference: **`docs/SEMANTIC_ERRORS_CATALOG.md`**  
Coverage matrix: **`docs/SEMANTIC_COVERAGE_MATRIX.md`**

| Code | Part | Meaning |
|------|------|---------|
| PY001 | Python | Undefined variable |
| PY002 | Python | Duplicate function definition (same scope) |
| PY003 | Python | Duplicate parameter name |
| PY004 | Python | `return` outside a function |
| PY005 | Python | `break`/`continue` outside a loop |
| PY006 | Python | Call to an undefined function |
| PY007 | Python | Duplicate route path |
| PY008 | Python | Function argument count mismatch |
| PY009 | Python | Route path parameters ≠ function parameters |
| PY010 | Python | Local variable used before assignment |
| PY011 | Python | Route handler may finish without return |
| TPL001 | Template | Duplicate `{% block %}` name |
| TPL002 | Template | `{% extends %}` in an invalid position |
| TPL003 | Template | `{% include %}` of a missing template |
| TPL004 | Template | Unknown Jinja filter |
| TPL005 | Template | Duplicate HTML `id` |
| TPL006 | Template | Unknown Jinja global function |
| TPL007 | Template | Jinja variable used outside its scope |
| TPL008 | Template | Missing parent template in extends |
| TPL009 | Template | Template inheritance cycle |
| TPL010 | Template | Child block not declared in parent |
| X001 | Cross | `render_template` targets a missing template |
| X002 | Cross | `url_for` references an undefined endpoint |
| X003 | Cross | Template uses a context variable never passed |
| X004 | Cross | Details template needs `product` but route omits it |
| X005 | Cross | Template accesses a product key not in the schema |
| X006 | Cross | `url_for` missing/extra/incorrect route arguments |
| X007 | Cross | HTML form method not accepted by target route |
| X008 | Cross | Form field names ≠ `request.form` reads |
| X009 | Cross | Context variable type incompatible with template usage |
| X010 | Cross | Product dictionaries have inconsistent schemas |
| X011 | Cross | Product fields have inconsistent inferred types |

**Evidence:** `scripts/test.ps1` → **67** Java + **8** pytest PASS (2026-07-09).
