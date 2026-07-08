# 07 – الزوّار (Visitors) ملفاً ملفاً

الزوّار هي الأصناف التي **تحوّل Parse Tree إلى AST**. تعتمد على نمط Visitor المولّد
من ANTLR (`-visitor`).

## 0. معنى `TemplateParserBaseVisitor<ASTNode>`

ANTLR يولّد صنفاً `XParserBaseVisitor<T>` عاماً (Generic) حيث `T` هو **نوع القيمة
المُعادة** من كل تابع زيارة. في مشروعنا `T = ASTNode`، أي أن كل تابع `visitXxx(...)`
يُعيد عقدة `ASTNode`. هذا يسمح ببناء الشجرة صعوداً: كل تابع يبني عقدته من عقد أبنائه
المُعادة عبر `visit(child)`.

---

## 1. `src/AST/visitor/ProgramVisitor.java`
- **يزور**: `FlaskParser` tree (نقطة الدخول لبناء Python AST).
- **الوظيفة الفعلية**: يستقبل `ProgContext` ويستدعي `FlaskVisitor` لبناء `Program`،
  وهو الغلاف الذي يستدعيه `CompilerPipeline.analyzeFlask` عبر
  `new ProgramVisitor().visit(tree)`.
- **يعيد**: `Program` (جذر Python AST).

## 2. `src/AST/visitor/FlaskVisitor.java`
- **يمتد**: `FlaskParserBaseVisitor<ASTNode>`.
- **يزور**: كل عقد `FlaskParser` (الأسماء بلاحقة `Context`).
- **يعيد**: `ASTNode` (بأنواعها الفرعية).

### كيف يختار العقدة المناسبة؟
لكل بديل نحوي مُعنون (`# Label`) هناك تابع `visitLabel`. مثال: القاعدة `atom` لها
`# IdAtom` و`# LiteralAtom` فيوجد `visitIdAtom` و`visitLiteralAtom`.

### كيف يزور الأبناء؟
يستدعي `visit(ctx.child)` ويحوّل النتيجة إلى النوع المتوقّع (cast). أمثلة:
- `visitProg` (سطر 18): يمرّ على `ctx.stmt()` ويبني `Program`.
- `visitIf_stmt` (سطر 164): يزور الشرط والـ suites ويبني `IfStmt`.
- `visitFor_stmt` (سطر 196): يبني `IdentifierExpr` للمتغيّر ثم `ForStmt`.

### كيف يحفظ line و column؟
- **line**: من `ctx.start.getLine()` يُمرَّر لكل constructor.
- **column**: يُضبط فقط في موضعين:
  - `visitIdAtom` (سطر 484): `new IdentifierExpr(...).setColumn(ctx.start.getCharPositionInLine())`.
  - `visitFunc_def` (سطر 219): `fn.setColumn(ctx.IDENTIFIER().getSymbol().getCharPositionInLine())`.

### كيف يتعامل مع null / optional؟
يفحص وجود العناصر قبل استخدامها: `if (ctx.parameters() != null)` في `visitFunc_def`،
و`if (ctx.ELSE() != null)` في `visitIf_stmt`، و`visitEmptyLine` يعيد `null` (وتُتجاهل
لاحقاً في `visitProg`).

### كيف يبني القوائم/القواميس؟
- `visitList_literal` (سطر 548): يمرّ على `ctx.expr()` ويبني `ListLiteralExpr`.
- `visitDict_literal` (سطر 561): لكل `dict_entry` يأخذ `expr(0)` مفتاحاً و`expr(1)`
  قيمة، ثم يبني `DictLiteralExpr(keys, values, line)`.

### كيف يبني الاستدعاءات وkeyword args؟
في `visitPrimaryRoot` (سطر 431)، عند `CallTrailerContext`: يفصل `KeywordArgContext`
(يضيف إلى `kwargs` بالمفتاح `IDENTIFIER`) عن `PositionalArgContext` (يضيف إلى `args`)،
ثم يبني `CallExpr(base, args, kwargs, line)`.

### تتبّع عملي لجملة حقيقية
المصدر: `products = [ {"id":1}, ]`
1. `visitAssignment` (سطر 94) → هدف واحد.
2. `visit(assign_target)` → `IdentifierExpr("products")`.
3. `visit(expr)` → يصل إلى `visitList_literal` → `ListLiteralExpr`.
4. داخلها `visitDict_literal` → `DictLiteralExpr(keys=[StringLiteralExpr("id")],
   values=[NumberLiteralExpr("1")])`.
5. الناتج: `AssignmentStmt(IdentifierExpr("products"), ListLiteralExpr([...]), line)`.

---

## 3. `src/AST/template/TemplateVisitor.java`
- **يمتد**: `TemplateParserBaseVisitor<ASTNode>`.
- **يزور**: كل عقد `TemplateParser`.
- **يعيد**: `ASTNode`؛ ويعيد **`null`** لوسوم الإغلاق (`visitJinjaEndIf`,
  `visitJinjaEndFor`, `visitJinjaBlockClose`) التي تُصفّى عند بناء قوائم الأبناء.

### كيف يبني `HtmlDocument` (الجذر)؟
- `visitTemplateRoot` → يفوّض إلى `template_content`.
- `visitTemplateContent` يمرّ على `template_node()` ويبني `HtmlDocument(children, line)`
  متجاهلاً القيم `null`.

### كيف يبني CSS؟
عبر `visitStyleElement` → `HtmlStyleElement` يحوي `CssStylesheet` (من `visitCssStylesheet`)
الذي يحوي `CssRule`/`CssDeclaration`/`CssValue`؛ وتُلَفّ تعبيرات Jinja داخل القيم في
`CssJinjaExpressionValue`/`CssJinjaValueIf`.

### كيف يبني Jinja؟
- التعبيرات `{{ }}`: `visitJinjaExpression → visitJinjaBinaryExpr → visitJinjaTerm`،
  حيث `visitJinjaTerm` يطبّق اللواحق (postfix) لبناء `JinjaAttrExpr` (وصول سمة)،
  `JinjaCallExpr` (استدعاء)، `JinjaFilterExpr` (فلتر).
- الجُمل `{% %}`: `visitJinjaIfBlock → JinjaIfStmt`، `visitJinjaForBlock → JinjaForStmt`،
  `visitJinjaBlock → JinjaBlockStmt`، `visitJinjaExtends → JinjaExtendsStmt`،
  `visitJinjaInclude → JinjaIncludeStmt`.

### تتبّع عملي لتعبير Jinja حقيقي
المصدر: `{{ product.name }}`
1. `visitJinjaExpression` → يفوّض `jinja_expr`.
2. `visitJinjaBinaryExpr` (حد واحد) → `visitJinjaTerm`.
3. `visitJinjaTerm`: الذرّة `JinjaIdentifierExpr("product")`، ثم اللاحقة
   `JinjaMemberAccess` تبني `JinjaAttrExpr(target=product, attribute="name")`.
4. الناتج: `JinjaAttrExpr` — منه يعرف التحليل عبر اللغات أن القالب يصل إلى
   `product.name`، فيتحقّق أن `name` ضمن schema المنتج.

### كيف يحفظ line؟
عبر التابع الخاص `getLine(ParseTree t)` الذي يستخرج السطر من الرمز. لا يُضبط عمود في
عقد القوالب.

---

## 4. زوّار/ملفات قديمة
- `src/SymbolTable/JinjaASTVisitor.java`: زائر قديم على Template AST لبناء
  `TemplateSymbolTable`. **غير مستخدم** في التدفّق الحالي (`CompilerPipeline` لا
  يستدعيه). باقٍ من تصميم سابق؛ انظر [21_COMPLETE_FILE_INDEX.md](21_COMPLETE_FILE_INDEX.md).
