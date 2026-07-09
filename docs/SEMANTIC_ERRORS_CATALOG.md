# Semantic Errors Catalog

Reference for **semantic** (`SEMANTIC`) and **cross-language** (`CROSS`) diagnostics only. Lexical/syntax codes are out of scope.

## Diagnostic format

```
[ERROR X006] page.html:1:0
url_for('show') is missing required route argument 'item_id'.
Suggestion: pass item_id=...
```

Source: `src/diagnostics/Diagnostic.java`.

---

## Python (PY001–PY011)

| Code | Severity | Definition | Invalid | Valid | AST / Model | Implementation |
|------|----------|------------|---------|-------|-------------|----------------|
| PY001 | ERROR | Undefined variable | `x = y` | `y=1; x=y` | `IdentifierExpr`, scopes | `PythonSemanticAnalyzer` |
| PY002 | ERROR | Duplicate top-level function | two `def foo()` | unique names | `FunctionDefStmt` | `detectDuplicateTopLevel` |
| PY003 | ERROR | Duplicate parameter | `def f(a,a)` | `def f(a,b)` | `FunctionDefStmt` | `checkFunction` |
| PY004 | ERROR | Return outside function | top-level `return` | return in `def` | `ReturnStmt` | `checkStmt` |
| PY005 | ERROR | Break/continue outside loop | top-level `break` | break in `for` | `BreakStmt` | `checkStmt` |
| PY006 | ERROR | Undefined function call | `ghost()` | `len(x)` | `CallExpr` | `checkCall` |
| PY007 | ERROR | Duplicate route path | two `@app.route("/")` | unique paths | `Decorator` | `detectDuplicateTopLevel` |
| PY008 | ERROR | Argument count mismatch | `greet()` vs `def greet(n)` | `greet("a")` | `CallExpr`, `FunctionSignature` | `checkArgumentCount` |
| PY009 | ERROR | Path param missing on function | `/x/<id>` + `def f():` | `def f(id):` | `RouteSignature` | `checkRouteParameterAlignment` |
| PY010 | ERROR | Local used before assign | `x=y; y=1` in def | assign first | `assignedLocals` | `resolveIdentifier` |
| PY011 | ERROR | Route without guaranteed return | route body without `return` | `return render_template(...)` | `ReturnStmt`, routes | `checkRouteReturns` |

**Why semantic:** parse tree is valid; rules about scopes, arity, Flask routes, and control flow are violated.

**Tests:** `TestRunner.pythonSemanticTests()` (19 tests).

---

## Template (TPL001–TPL010)

| Code | Severity | Definition | Invalid | Valid | AST / Model | Implementation |
|------|----------|------------|---------|-------|-------------|----------------|
| TPL001 | ERROR | Duplicate `{% block %}` | two `content` blocks | unique block names | `JinjaBlockStmt` | `walk` |
| TPL002 | ERROR | `extends` not first | HTML before extends | extends first line | `JinjaExtendsStmt`, `HtmlDocument` | `checkExtendsPosition` |
| TPL003 | ERROR | Missing include file | `include "nope.html"` | known template set | `JinjaIncludeStmt` | `walk` |
| TPL004 | ERROR | Unknown filter | `\| bogus` | `\| upper` | `JinjaFilterExpr` | `walk` |
| TPL005 | ERROR | Duplicate HTML `id` | two `id="x"` | unique ids | `HtmlAttribute` | `walk` |
| TPL006 | ERROR | Unknown Jinja function | `mystery_fn()` | `url_for(...)` | `JinjaCallExpr` | `walk` |
| TPL007 | ERROR | Loop var outside `for` | `{{ p }}` after loop | use inside loop | `TemplateScope`, loop stack | `checkLoopVarScope` |
| TPL008 | ERROR | Missing parent template | extends missing file | parent exists | `TemplateDependencyGraph` | `analyzeInheritance` |
| TPL009 | ERROR | Inheritance cycle | a→b→a | DAG | `TemplateDependencyGraph` | `findCycle` |
| TPL010 | ERROR | Block not in parent | child `footer`, parent none | matching blocks | block sets per file | `analyzeInheritance` |

**Tests:** `TestRunner.templateSemanticTests()` (18 tests).

---

## Cross-language (X001–X011)

| Code | Severity | Definition | Invalid | Valid | AST / Model | Implementation |
|------|----------|------------|---------|-------|-------------|----------------|
| X001 | ERROR | render → missing template | `missing.html` | template exists | `RenderCall`, template map | `checkRenderTargets` |
| X002 | ERROR | url_for → unknown endpoint | `url_for('ghost')` | known endpoint | `UrlForRef` | `checkUrlForEndpoints` |
| X003 | ERROR | Context var not passed | `{{ foo }}` not in render | `products=products` | `RenderCall`, Jinja vars | `checkTemplateContexts` |
| X004 | ERROR | Details needs `product` | `product.name` not passed | `product=...` | `attrsByVar` | `checkTemplateContexts` |
| X005 | ERROR | Product key not in schema | `p.price` missing in data | keys match schema | `AppModel.productFields` | `checkTemplateContexts` |
| X006 | ERROR | url_for arg mismatch | missing `<id>` kwarg | `item_id=1` | `RouteSignature`, `UrlForRef` | `checkUrlForArguments` |
| X007 | ERROR | Form method vs route | POST form → GET route | POST route | `FormModel`, `RouteSignature` | `checkFormsAgainstRoutes` |
| X008 | ERROR | Form fields vs request.form | `name` vs `title` | matching names | `FormModel`, form reads | `checkFormsAgainstRoutes` |
| X009 | ERROR | Context type vs usage | `products.upper` | `title.upper` | `TemplateContextModel`, `ValueType` | `checkContextTypes` |
| X010 | ERROR | Inconsistent product schemas | rows with different keys | uniform dict keys | `ProductSchema` | `checkProductSchemaConsistency` |
| X011 | ERROR | Inconsistent field types | `price` int vs string | uniform types | `ProductSchema` | `checkProductSchemaConsistency` |

**Tests:** `TestRunner.crossLanguageTests()` (20 tests).

---

## Model infrastructure

| Class | File |
|-------|------|
| `ValueType` | `src/model/ValueType.java` |
| `FunctionSignature` | `src/model/FunctionSignature.java` |
| `RouteSignature` | `src/model/RouteSignature.java` |
| `TemplateScope` | `src/model/TemplateScope.java` |
| `TemplateContextModel` | `src/model/TemplateContextModel.java` |
| `FormModel` | `src/model/FormModel.java` |
| `ProductSchema` | `src/model/ProductSchema.java` |
| `TemplateDependencyGraph` | `src/model/TemplateDependencyGraph.java` |
| `TypeInference` | `src/model/TypeInference.java` |

Pipeline entry points: `PythonSemanticAnalyzer`, `TemplateSemanticAnalyzer`, `CrossLanguageSemanticAnalyzer` via `CompilerPipeline`.
