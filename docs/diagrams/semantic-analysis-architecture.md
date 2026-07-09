# Semantic Analysis Architecture

```mermaid
flowchart LR
    subgraph Models
        AM[AppModel]
        FS[FunctionSignature]
        RS[RouteSignature]
        PS[ProductSchema]
        FM[FormModel]
        TCM[TemplateContextModel]
    end

    subgraph Analyzers
        PY[PythonSemanticAnalyzer<br/>PY001-PY011]
        TPL[TemplateSemanticAnalyzer<br/>TPL001-TPL010]
        X[CrossLanguageSemanticAnalyzer<br/>X001-X011]
    end

    DR[DiagnosticReporter]

    AM --> PY
    AM --> X
    PY --> DR
    TPL --> DR
    X --> DR

    AM -.-> FS
    AM -.-> RS
    AM -.-> PS
    X -.-> FM
    X -.-> TCM
```

**32 semantic error codes** documented in `docs/SEMANTIC_ERRORS_CATALOG.md`.

Diagnostic format:

```
[ERROR PY001] app.py:12:4
Undefined variable 'x'.
Suggestion: define 'x' before using it.
```
