# Python-Template Data Flow

See also: [PYTHON_TO_TEMPLATE_DATA_FLOW.md](../PYTHON_TO_TEMPLATE_DATA_FLOW.md)

```mermaid
sequenceDiagram
    participant Src as Python Source
    participant AST as Python AST
    participant Builder as AppModelBuilder
    participant Model as AppModel
    participant Gen as FlaskAppGenerator
    participant Out as generated-app/app.py
    participant Tpl as Template AST
    participant Cross as CrossLanguageSemanticAnalyzer

    Src->>AST: ProgramVisitor
    AST->>Builder: walk literals/routes
    Builder->>Model: productRows, routes, renderCalls
    Model->>Cross: context + schema
    Tpl->>Cross: variable usage
    Model->>Gen: rows + fields
    Gen->>Out: embed products = [...]
```

Architecture DOT (not a merged AST): `docs/diagrams/generated/cross-language-data-flow.dot`

Render SVG (optional):

```powershell
dot -Tsvg docs/diagrams/generated/cross-language-data-flow.dot -o cross-language-data-flow.svg
```
