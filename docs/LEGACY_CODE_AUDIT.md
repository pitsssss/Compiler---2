# Legacy Code Audit

| File | Used? | Where? | Decision | Reason |
|------|-------|--------|----------|--------|
| `SymbolTable/FlaskSymbolTable.java` | Yes | `CompilerPipeline`, `SymbolTableVisitor`, tests | **Keep** | Active symbol table |
| `SymbolTable/SymbolTableVisitor.java` | Yes | `CompilerPipeline` | **Keep** | Builds FlaskSymbolTable from Python AST |
| `SymbolTable/Scope.java` | No | No imports/instantiation | **Keep and document** | Superseded by `FlaskSymbolTable.Scope`; safe to remove later |
| `SymbolTable/Symbol.java` | No | No imports/instantiation | **Keep and document** | Superseded by `FlaskSymbolTable.Symbol` |
| `SymbolTable/TemplateSymbolTable.java` | No | Not in pipeline | **Keep and document** | Early template ST experiment |
| `SymbolTable/JinjaASTVisitor.java` | No | Not in pipeline | **Keep and document** | Paired with unused TemplateSymbolTable |

## Active path

```
Python AST -> SymbolTableVisitor -> FlaskSymbolTable -> printSymbolTable()
Template AST -> TemplateSemanticAnalyzer (not TemplateSymbolTable)
```

## Why not deleted?

- No references found via import/instantiation grep across `src/` and `scripts/`
- Deletion is safe but low value vs. risk of course materials referencing file names
- Documented here so the committee knows which implementation is authoritative

## Verification command

```powershell
rg "import SymbolTable\.(Scope|Symbol|TemplateSymbolTable|JinjaASTVisitor)" src/
# (no matches except internal legacy files)
```
