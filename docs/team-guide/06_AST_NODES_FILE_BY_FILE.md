# 06 – عقد الـ AST ملفاً ملفاً

هذا مرجع تفصيلي لكل عقد الـ AST المكتوبة يدوياً تحت `src/AST/`. جميعها ترث من
`ASTNode` (مباشرة أو عبر أصناف مجرّدة). القاعدة العامة:

- كل عقدة تحمل `nodeName`, `lineNumber`, `columnNumber` (من `ASTNode`).
- `getChildren()` تُعيد الأبناء المستخدمين في الطباعة والتحليل والتوليد.
- `toString()` يُستخدم في الطباعة (`ASTPrinter`) وفي `ast.dot`.
- من يملأ الحقول: **الزوّار** (`FlaskVisitor`/`TemplateVisitor`) عبر الـ constructors.
- من يقرأها: `SymbolTableVisitor`, `AppModelBuilder`, المحلّلات الدلالية،
  `FlaskAppGenerator`, والطابعات.

> ملاحظة عامة: أرقام الأسطر تُمرَّر من الزائر عبر `ctx.start.getLine()`، والعمود
> يُضبط فقط في `IdentifierExpr` و`FunctionDefStmt` (عبر `setColumn`).

---

## أ) القاعدة والجذر

### `src/AST/ASTNode.java`
- **النوع**: abstract class (القاعدة العليا لكل العقد).
- **يمثّل**: أي عقدة في الشجرة.
- **Fields**: `nodeName:String`, `lineNumber:int`, `columnNumber:int(=-1)`.
- **Constructor**: `(String nodeName, int lineNumber)`.
- **Methods**: `getNodeName`, `getLineNumber`, `getColumnNumber`, `setColumn(int)→ASTNode`,
  `getChildren():List<ASTNode>` (فارغة افتراضياً), `toString()` (يضيف `col` إن `>=0`).
- **علاقتها بالتحليل/التوليد**: كل شيء يعتمد على تعدّدية أشكالها (polymorphism).

### `src/AST/Program.java`
- **النوع**: concrete، **الجذر لشجرة Python**.
- **يمثّل**: البرنامج كله.
- **Fields**: `statements:List<Statement>`.
- **Constructor**: `(List<Statement> statements, int lineNumber)`.
- **Methods**: `getChildren()` (نسخة من `statements`).
- **مثال منتج لها**: كامل `app.py`. **علاقتها بالتوليد**: `AppModelBuilder.build`
  يمرّ على `program.getChildren()`.

---

## ب) `src/AST/expr/` — التعبيرات

### `Expression.java`
- abstract، ترث `ASTNode`؛ قاعدة كل التعبيرات. لا حقول إضافية.

### `IdentifierExpr.java`
- concrete. يمثّل **اسماً** (متغير/دالة). Field: `name:String`. Constructor
  `(String name, int line)`. Methods: `getName()`, `toString()`. **يضبط العمود** في
  الزائر. يقرؤه `AppModelBuilder` (اسم app، products، endpoints) والمحلّل الدلالي.

### `CallExpr.java`
- concrete. يمثّل **استدعاء دالة** `f(args, k=v)`. Fields: `function:Expression`,
  `arguments:List<Expression>`, `keywordArguments:Map<String,Expression>`. Constructors:
  بدون/مع kwargs. Methods: `getFunction`, `getArguments`, `getKeywordArguments`,
  `getChildren`. **حاسمة للربط**: منها يُكتشف `render_template`, `url_for`,
  `app.route`, و`methods=[...]`.

### `AttributeExpr.java`
- concrete. يمثّل `base.attr`. Fields: `base:Expression`, `attribute:String`. Methods:
  `getBase`, `getAttribute`. يُستخدم لاكتشاف `.route` وللوصول للسمات في القوالب/Python.

### `IndexExpr.java`
- concrete. يمثّل `base[index]`. Fields: `base`, `index` (لا getters؛ عبر
  `getChildren`). يظهر في `products[0]`.

### `BinaryExpr.java`
- concrete. عملية ثنائية `left op right` (يشمل `and/or/+/-/*/...`). Fields: `left`,
  `operator:String`, `right`. Methods: `getLeft/getOperator/getRight`.

### `UnaryExpr.java`
- concrete. عملية أحادية `op expr` (مثل `-5`). Fields: `operator`, `expression`.
  يقرؤه `AppModelBuilder.literalValue` لدعم الأرقام السالبة.

### `NotExpr.java`
- concrete. `not expr`. Field: `expr`.

### `CompareExpr.java`
- concrete. سلسلة مقارنات `a < b == c`. Fields: `left`, `operators:List<String>`,
  `rights:List<Expression>`. Methods: `getLeft/getOperators/getRights`.

### `LambdaExpr.java`
- concrete. `lambda p: body`. Fields: `params:List<String>`, `body:Expression`.
  Methods: `getParams/getBody`. يفحصها `PythonSemanticAnalyzer` (بارامترات lambda).

---

## ج) `src/AST/literal/` — القيم الحرفية

### `LiteralExpr.java`
- abstract، ترث `Expression`؛ قاعدة الحرفيات القياسية.

### `StringLiteralExpr.java`
- concrete. نص. Field: `value:String` (يشمل علامات الاقتباس). Methods: `getValue()`
  (خام)، `getUnquoted()` (بلا اقتباس — يستخدمه `AppModelBuilder` و`RouteInfo`).

### `NumberLiteralExpr.java`
- concrete. Field: `value:String`. Method `getValue()`. يحوّله `literalValue` إلى
  Integer/Double.

### `BooleanLiteralExpr.java`
- concrete. Field: `value:boolean`. Method `getValue()`. مهم لحقل `in_stock`.

### `NoneLiteralExpr.java`
- concrete. `None`. لا حقول. يتحوّل إلى `null` في `AppModel`.

### `ListLiteralExpr.java`
- concrete (ترث `Expression` مباشرة). Field: `elements:List<Expression>`. Method
  `getElements()`. **جوهرية**: قائمة `products` والقيمة `methods=[...]`.

### `SetLiteralExpr.java`
- concrete. Field: `elements` (بلا getter). نادر الاستخدام في الـ demo.

### `DictLiteralExpr.java`
- concrete (ترث `Expression`). يمثّل `{k: v, ...}`. Fields: `keys:List<Expression>`,
  `values:List<Expression>`. Methods: `getKeys/getValues/size`. **جوهرية**: كل سجل
  منتج في الـ demo هو `DictLiteralExpr`؛ منه يُستخرج schema المنتجات في
  `AppModelBuilder.extractProducts` (الحالة `DictLiteralExpr`).

---

## د) `src/AST/stmt/` — الجُمل

### `Statement.java`
- abstract، قاعدة كل الجُمل.

### `AssignmentStmt.java`
- concrete. `target = value`. Fields: `target`, `value`. Methods: `getTarget/getValue`.
  يقرؤه `AppModelBuilder.handleGlobalAssignment` (لاكتشاف `app=Flask(...)` و`products=[...]`).

### `AssignmentChainStmt.java`
- concrete. `a = b = value`. Fields: `targets:List<Expression>`, `value`. Method
  `getValue`.

### `FunctionDefStmt.java`
- concrete. `def name(params): body`. Fields: `name:String`, `parameters:List<String>`,
  `body:Suite`. Methods: `getName/getParameters/getBody`. **يضبط العمود** على اسم
  الدالة. يقرؤه `AppModel.functions` و`PythonSemanticAnalyzer`.

### `Decorator.java`
- concrete (ترث `ASTNode`). `@expr`. Field: `expr:Expression`. Method `getExpr`.

### `DecoratedStmt.java`
- concrete. `@decorators + (func|class)`. Fields: `decorators:List<Decorator>`,
  `target:Statement`. Methods: `getDecorators/getTarget`. **مصدر الـ routes**.

### `ReturnStmt.java`
- concrete. `return value`. Field: `value`. Method `getValue`. يفحص `PY004` (return
  خارج دالة) وجودها خارج function scope.

### `IfStmt.java`
- concrete. `if/elif/else`. Fields: `condition`, `thenSuite`, `elifConditions`,
  `elifSuites`, `elseSuite`.

### `WhileStmt.java`
- concrete. `while cond: body`. Fields: `condition`, `body`.

### `ForStmt.java`
- concrete. `for it in iterable: body`. Fields: `iterator:IdentifierExpr`,
  `iterable:Expression`, `body:Suite`. Methods: `getIterator/getIterable/getBody`.

### `BreakStmt.java` / `ContinueStmt.java` / `PassStmt.java`
- concrete بلا حقول. `break`/`continue` يفحصهما `PY005` (خارج حلقة).

### `FromImportStmt.java`
- concrete. `from module import a, b`. Fields: `module:String`, `names:List<String>`.
  Methods: `getModule/getNames`. يقرؤه `AppModelBuilder` (imports) و`SymbolTableVisitor`.

### `GlobalStmt.java`
- concrete. `global a, b`. Field: `names`.

### `DelStmt.java`
- concrete. `del a, b`. Field: `targets`.

### `ClassDefStmt.java`
- concrete. `class Name(Parent): body`. Fields: `name`, `parent(nullable)`, `body`.
  Methods: `getName/getParent/getBody`.

### `ExpressionStmt.java`
- concrete. تعبير كجملة (مثل استدعاء دالة سطراً مستقلاً). Field: `expression`. Method
  `getExpression`.

---

## هـ) `src/AST/suite/` — الكتل

### `Suite.java`
- abstract، قاعدة الكتل.

### `BlockSuite.java`
- concrete. كتلة مُزاحة (`INDENT stmt+ DEDENT`). Field: `statements`. `getChildren`
  يعيد نسخة.

### `InlineSuite.java`
- concrete. جملة على نفس السطر (`if x: return`). Field: `statement`.

---

## و) `src/AST/template/` — قاعدة القوالب

### `TemplateNode.java`
- abstract، ترث `ASTNode`؛ قاعدة كل عقد القوالب (HTML/CSS/Jinja).

---

## ز) `src/AST/template/html/` — عقد HTML

### `HtmlNode.java`
- abstract، قاعدة عقد HTML.

### `HtmlDocument.java`
- concrete، **جذر شجرة القالب**. Field: `children:List<ASTNode>`. Methods:
  `getChildrenNodes()` (الأصل)، `getChildren()`. يقرؤه `CrossLanguageSemanticAnalyzer`
  و`TemplateSemanticAnalyzer` بالمرور على الأبناء.

### `HtmlElement.java`
- abstract. Fields (protected): `tagName:String`, `attributes:List<HtmlAttribute>`.
  Method `getAttributes`. قاعدة العناصر.

### `HtmlNormalElement.java`
- concrete. `<tag>...</tag>`. Field إضافي: `children:List<ASTNode>`. `getChildren`
  يجمع السمات + الأبناء.

### `HtmlSelfClosingElement.java`
- concrete. `<tag/>`. لا حقول إضافية.

### `HtmlVoidElement.java`
- concrete. `<br>` ونحوه.

### `HtmlStyleElement.java`
- concrete. `<style>...</style>`. Field: `stylesheet:CssStylesheet` (الوسم مثبّت "style").

### `HtmlText.java`
- concrete. نص حر. Field: `text:String`. Method `getText`.

### `HtmlAttribute.java`
- concrete. `name="value"`. Fields: `name`, `value`. Methods: `getName/getValue`.
  يُستخدم لفحص تكرار `id` (`TPL006`) في `TemplateSemanticAnalyzer`.

---

## ح) `src/AST/template/css/` — عقد CSS

- **`CssNode.java`**: abstract، قاعدة CSS.
- **`CssStylesheet.java`**: concrete، ورقة الأنماط. Field `contents:List<ASTNode>`.
- **`CssRule.java`**: قاعدة `selector { ... }`. Fields: `selector:CssSelector`,
  `blockContents:List<CssNode>`.
- **`CssSelector.java`**: نص المحدّد + قائمة `jinjaExpressions`. Fields:
  `selectorText:String`, `jinjaExpressions:List<JinjaExpr>`.
- **`CssDeclaration.java`**: `property: value`. Fields: `property:String`,
  `value:CssValue`.
- **`CssValue.java`**: قيمة مركّبة. Field `parts:List<CssValuePart>`.
- **`CssValuePart.java`**: abstract، جزء قيمة.
- **`CssPrimitiveValue.java`**: قيمة نصية بسيطة. Field `text`.
- **`CssFunctionCall.java`**: `func(args)`. Fields: `name`, `args:CssValue(nullable)`.
- **`CssJinjaExpressionValue.java`**: `{{ expr }}` داخل قيمة. Field `expr:JinjaExpr`.
- **`CssJinjaValueIf.java`**: `{% if %}...{% else %}...` داخل قيمة. Fields:
  `condition`, `thenValue`, `elseValue(nullable)`.

## ط) `src/AST/template/jinja/` — عقد Jinja

- **`JinjaNode.java`**: abstract، قاعدة Jinja.
- **`JinjaBody.java`**: جسم (قائمة أبناء مختلطة HTML/CSS). Field `children`. Method
  `getBodyChildren`.

### `jinja/expr/` — تعبيرات Jinja
- **`JinjaExpr.java`**: abstract؛ يوفّر `getVariables():Set<String>` التي تجمع أسماء
  المتغيّرات من الأبناء — يستخدمها التحليل عبر اللغات لمعرفة متغيّرات السياق.
- **`JinjaIdentifierExpr.java`**: اسم. Field `name`. Method `getName/getVariables`.
- **`JinjaAttrExpr.java`**: `target.attribute`. Fields: `target:JinjaExpr`,
  `attribute:String`. Methods: `getTarget/getAttribute`. **جوهرية**: `product.name`.
- **`JinjaCallExpr.java`**: استدعاء. Fields: `callee`, `args`.
- **`JinjaFilterExpr.java`**: `base | filter(args)`. Fields: `base`, `filterName`,
  `args`. Method `getFilterName` — يُستخدم لفحص الفلاتر المجهولة (`TPL004`).
- **`JinjaBinaryExpr.java`**: `left op right`. Fields: `left/op/right`.
- **`JinjaUnaryExpr.java`**: `not expr`. Fields: `op/expr`.
- **`JinjaStringLiteralExpr.java`**: نص خام (`rawText`).
- **`JinjaNumberLiteralExpr.java`**: رقم (`text`).

### `jinja/stmt/` — جُمل Jinja
- **`JinjaStmt.java`**: abstract.
- **`JinjaIfStmt.java`**: `if/elif/else`. Fields: `condition`, `thenBody`,
  `elifClauses:List<JinjaElifClause>`, `elseBody(nullable)`.
- **`JinjaElifClause.java`**: `elif`. Fields: `condition`, `body`.
- **`JinjaForStmt.java`**: `for vars in iterable`. Fields: `variables:List<String>`,
  `iterable:JinjaExpr`, `body`. متغيّرات الحلقة تُعامل كمُعرّفة محلياً في القالب.
- **`JinjaIncludeStmt.java`**: `include "x"`. Field `templateName`. يفحصه `TPL003`.
- **`JinjaBlockStmt.java`**: `block name`. Fields: `name`, `body`. يفحص تكرار الاسم
  (`TPL001`).
- **`JinjaExtendsStmt.java`**: `extends "base"`. Field `templateName`. يفحص موضعه
  (`TPL002`) ووجود القالب.

> السلسلة الوراثية الكاملة والحقول الدقيقة (بما فيها `toString()` لكل عقدة) موجودة في
> جدول مُفصّل داخل [FULL_PROJECT_GUIDE.md](FULL_PROJECT_GUIDE.md#ast-nodes).
