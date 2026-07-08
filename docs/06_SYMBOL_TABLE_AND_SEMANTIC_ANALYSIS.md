# 06 – Symbol Table & Semantic Analysis

## Symbol table

`SymbolTable.FlaskSymbolTable` (populated by `SymbolTable.SymbolTableVisitor`).

### Scopes

- A stack of `Scope` objects with types `GLOBAL`, `FUNCTION`, `LOOP`, `IF`,
  `BLOCK`, `CLASS`, `MODULE`.
- `enterScope`/`exitScope` push/pop; child scopes are linked to parents so the
  tree of scopes can be printed.
- `lookup` searches from the innermost scope outward, then falls back to
  built-ins — i.e. inner-to-outer resolution.

### Symbols

Each `Symbol` stores its **name**, **type** (`VARIABLE`, `FUNCTION`,
`PARAMETER`, `MODULE`/import, `LOOP_VAR`, `ATTRIBUTE`, `BUILTIN`, …),
**data type**, **line number**, **parameter list** (for functions), and the
**owning scope name**.

### Declarations vs. uses — the route/args/get/append fix

The original code registered `app.route`, `request.args`, `.get`, `.append` as
new declarations each time, producing false “already declared” warnings. The
redesign separates the two concepts:

- **Declarations** (imports, globals, params, functions, locals) go into a scope
  via `addSymbol`, which returns `false` on a genuine same-scope duplicate.
- **Uses** are tracked separately and never cause duplicates:
  - `addAttribute(base, attr, line)` → `attributeUsages` (e.g. `app.route`).
  - `addBuiltinFunction(name, line)` → `builtinUsages` (e.g. `append`, `get`).
- **Built-ins** (`len`, `append`, `Flask`, …) are registered for *lookup only*
  and are never counted as user declarations.

Result: repeated `@app.route(...)` and `request.args.get(...)` no longer warn,
while a real duplicate (`x = 1` twice in the same scope, or two functions with
the same name) is still detected. This is proven by the Symbol Table tests.

## Diagnostics model

`src/diagnostics` provides a uniform message format used by every phase:

- `DiagnosticSeverity` = `ERROR | WARNING | INFO`
- `DiagnosticKind` = `LEXICAL | SYNTAX | SEMANTIC | CROSS`
- `Diagnostic` = kind + severity + **code** + message + file + line + column +
  optional **suggestion**. Its `toString()` renders:

```
[SEMANTIC/ERROR PY001] app.py:12:4 Undefined variable 'x' (hint: define 'x' before using it)
```

- `DiagnosticReporter` collects everything and prints a grouped report plus a
  summary (`Errors: X   Warnings: Y`). The pipeline treats any error as failure.
  No stack traces are shown for expected errors.

## Semantic errors (18 types)

Analysis never stops at the first error; it collects all it can in one run.

### Python / Flask — `PythonSemanticAnalyzer`

| Code | Meaning | Bad example | Message |
|------|---------|-------------|---------|
| PY001 | Undefined variable | `x = y` | `Undefined variable 'y'` |
| PY002 | Duplicate function | `def f(): ...` twice | `Duplicate function definition 'f'` |
| PY003 | Duplicate parameter | `def f(a, a): ...` | `Duplicate parameter name 'a' in function 'f'` |
| PY004 | `return` outside function | top-level `return x` | `'return' used outside of a function` |
| PY005 | `break`/`continue` outside loop | top-level `break` | `'break' used outside of a loop` |
| PY006 | Call to undefined function | `bar()` | `Call to undefined function 'bar'` |
| PY007 | Duplicate route path | two `@app.route("/")` | `Duplicate route path '/'` |

Good example (0 errors):

```python
from flask import Flask, render_template
app = Flask("a")
items = [1, 2, 3]
@app.route("/")
def home():
    return render_template("i.html", items=items)
```

### Templates — `TemplateSemanticAnalyzer`

| Code | Meaning | Bad example |
|------|---------|-------------|
| TPL001 | Duplicate block name | two `{% block content %}` |
| TPL002 | `extends` not first | content before `{% extends %}` |
| TPL003 | Include missing template | `{% include "nope.html" %}` |
| TPL004 | Unknown filter | `{{ x | bogus }}` |
| TPL005 | Duplicate HTML id | two `id="main"` |
| TPL006 | Unknown Jinja global | `{{ mystery() }}` |

### Cross-language — `CrossLanguageSemanticAnalyzer`

| Code | Meaning | Bad example |
|------|---------|-------------|
| X001 | render target missing | `render_template("missing.html")` |
| X002 | url_for undefined endpoint | `url_for("ghost")` |
| X003 | context var not passed | template uses `{{ foo }}`, route passes nothing |
| X004 | details needs `product` | `product_details.html` uses `product`, route omits it |
| X005 | product key not in schema | `{{ p.price }}` when schema is `{id, name}` |

Every semantic and cross-language error carries file, line (and column where
available) and a short fix suggestion.
