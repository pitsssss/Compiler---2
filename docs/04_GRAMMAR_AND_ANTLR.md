# 04 – Grammar & ANTLR

## The four grammars

| File | Role |
|------|------|
| `src/antlr/FlaskLexer.g4` | Tokenizes the Python/Flask subset (keywords, identifiers, numbers, strings, operators, and Python-style INDENT/DEDENT). |
| `src/antlr/FlaskParser.g4` | Parses the Python/Flask subset; root rule `prog`. |
| `src/antlr/TemplateLexer.g4` | Mode-based lexer for HTML text, Jinja `{{ }}`/`{% %}` expressions & statements, and embedded CSS. |
| `src/antlr/TemplateParser.g4` | Parses templates; root rule `template`. |

All four use `-package antlr`, so the generated classes live in `src/antlr` and
are imported as `antlr.FlaskParser`, etc.

## Generating the Java sources

Never edit the generated `*.java` by hand. Regenerate from the grammars:

```powershell
./scripts/generate-antlr.ps1
```

The script (in order) generates the two lexers, then the two parsers **with the
visitor** (`-visitor`), and stops on the first failure.

## Flask lexer highlights

- **Indentation** is handled in the `@members` block: a stack of indent widths
  emits synthetic `INDENT`/`DEDENT` tokens, mirroring CPython's tokenizer.
- **Implicit line joining**: inside `()`, `[]`, or `{}` a newline is
  insignificant. The `NEWLINE` rule tracks an `opened` counter and `skip()`s the
  newline when `opened > 0`, so multi-line list/dict/call literals parse cleanly:

```python
products = [
    {"id": 1, "name": "Laptop"},
    {"id": 2, "name": "Headphones"},
]
```

## Flask parser coverage

The subset covers what a typical Flask module needs:

- `from ... import ...` imports.
- Assignments, including chained assignment.
- Function definitions with parameters, decorators (`@app.route(...)`).
- Control flow: `if/elif/else`, `for`, `while`, `break`, `continue`, `return`,
  `pass`, `global`, `del`.
- Expressions: calls (positional **and** keyword arguments), attribute access,
  indexing, binary/compare/unary/not, lambdas.
- Literals: numbers, strings, booleans, `None`, lists, sets, and **dicts**
  (including empty/one-element collections).

## Template grammar coverage

- **HTML**: normal elements, self-closing/void elements, attributes, text,
  `<style>` blocks.
- **Jinja**: `{{ expression }}` output; `{% ... %}` statements —
  `extends`, `block/endblock`, `include`, `if/elif/else`, `for/endfor`;
  identifiers, attribute access, filters (`| name`), function calls, literals,
  binary/unary expressions.
- **CSS**: selectors, rules, declarations, primitive values, function calls,
  and Jinja-in-CSS values.

## Error handling

Both parsers install a custom `CompilerErrorListener` (in `src/diagnostics`)
instead of ANTLR's default console listener. It converts each ANTLR
`syntaxError` into a structured `Diagnostic` with kind `SYNTAX` (or `LEXICAL`),
the file name, line, column, message, and — when possible — a hint. Example:

```
[SYNTAX/ERROR SYN001] app.py:6:4 extraneous input '...' expecting ...
```

Incomplete input is never accepted silently: the root rules consume `EOF`, and
any leftover tokens raise a syntax error that fails the run.
