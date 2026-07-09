# AST Class Hierarchy

Based on hand-written classes under `src/AST/` (ANTLR generated code excluded).

```mermaid
classDiagram
    class ASTNode {
        +String nodeName
        +int lineNumber
        +int columnNumber
        +getChildren() List~ASTNode~
        +toString() String
    }

    class Program
    class Expression
    class Statement
    class LiteralExpr
    class FunctionDefStmt
    class CallExpr
    class DictLiteralExpr

    class HtmlDocument
    class HtmlElement
    class HtmlNormalElement
    class HtmlAttribute
    class JinjaExpr
    class JinjaBlockStmt
    class CssStylesheet

    ASTNode <|-- Program
    ASTNode <|-- Expression
    ASTNode <|-- Statement
    Expression <|-- LiteralExpr
    Statement <|-- FunctionDefStmt
    Expression <|-- CallExpr
    LiteralExpr <|-- DictLiteralExpr

    ASTNode <|-- HtmlDocument
    ASTNode <|-- HtmlElement
    HtmlElement <|-- HtmlNormalElement
    ASTNode <|-- HtmlAttribute
    ASTNode <|-- JinjaExpr
    ASTNode <|-- JinjaBlockStmt
    ASTNode <|-- CssStylesheet
```

**Polymorphism:** `ASTPrinter`, semantic analyzers, and `ASTGraphvizPrinter` call `getChildren()` and `toString()` on any `ASTNode` without knowing the concrete type.

**Synthetic nodes (line 0 allowed):** `TemplateAstForest` — wrapper for multi-template DOT export only.
