# Semantic Error Coverage Matrix

| Error Code | Implemented | Invalid Test | Valid Test | Boundary Test | Line/Column Verified | False Positive Test | Documentation |
|------------|-------------|--------------|------------|---------------|----------------------|---------------------|---------------|
| PY001 | Yes | Yes | Yes | — | Yes | Yes (demo) | Yes |
| PY002 | Yes | Yes | — | — | Partial | — | Yes |
| PY003 | Yes | Yes | — | — | Partial | — | Yes |
| PY004 | Yes | Yes | — | — | Partial | — | Yes |
| PY005 | Yes | Yes | — | — | Partial | — | Yes |
| PY006 | Yes | Yes | — | — | Partial | — | Yes |
| PY007 | Yes | Yes | — | — | Partial | — | Yes |
| PY008 | Yes | Yes | Yes | Yes | Partial | — | Yes |
| PY009 | Yes | Yes | Yes | — | Partial | — | Yes |
| PY010 | Yes | Yes | Yes | — | Partial | — | Yes |
| PY011 | Yes | Yes | Yes | — | Partial | — | Yes |
| TPL001 | Yes | Yes | — | — | Yes | — | Yes |
| TPL002 | Yes | Yes | Yes | — | Partial | Yes (demo) | Yes |
| TPL003 | Yes | Yes | — | — | Partial | — | Yes |
| TPL004 | Yes | Yes | Yes | — | Partial | Yes (demo) | Yes |
| TPL005 | Yes | Yes | Yes | — | Partial | Yes (demo) | Yes |
| TPL006 | Yes | Yes | Yes | — | Partial | Yes (demo) | Yes |
| TPL007 | Yes | Yes | Yes | — | Partial | Yes (demo) | Yes |
| TPL008 | Yes | Yes | Yes | — | Partial | Yes (demo) | Yes |
| TPL009 | Yes | Yes | — | — | Partial | Yes (demo) | Yes |
| TPL010 | Yes | Yes | Yes | — | Partial | Yes (demo) | Yes |
| X001 | Yes | Yes | — | — | Partial | — | Yes |
| X002 | Yes | Yes | — | — | Partial | Yes (demo) | Yes |
| X003 | Yes | Yes | Yes | — | Partial | Yes (demo) | Yes |
| X004 | Yes | Yes | Yes | — | Partial | Yes (demo) | Yes |
| X005 | Yes | Yes | Yes | — | Partial | Yes (demo) | Yes |
| X006 | Yes | Yes | Yes | — | Yes | Yes (demo) | Yes |
| X007 | Yes | Yes | Yes | — | Partial | Yes (demo) | Yes |
| X008 | Yes | Yes | Yes | — | Partial | Yes (demo) | Yes |
| X009 | Yes | Yes | Yes | — | Partial | Yes (demo) | Yes |
| X010 | Yes | Yes | — | — | Partial | Yes (demo) | Yes |
| X011 | Yes | Yes | — | — | Partial | Yes (demo) | Yes |

**Test evidence:** `scripts/test.ps1` — **67/67 Java tests PASS**, **8/8 pytest PASS**, `examples/demo` analyze-only — **0 errors**.

**Notes:**
- *Partial* line/column: available where ANTLR attaches columns; cross-language `render_template` gaps report the Python call line.
- *False Positive Test (demo)*: `--flask examples/demo/app.py --template examples/demo/templates --analyze-only`.
