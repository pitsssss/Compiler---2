# 19 – دليل مقابلة اللجنة (Committee Interview Guide)

لكل سؤال: جواب قصير، جواب تفصيلي، الملف/الـ Class/Method للإشارة إليه، ومثال.

## 1. أسئلة عامة

**لماذا يُسمّى Compiler رغم أن الناتج تطبيق Flask؟**
- قصير: لأنه ينفّذ مراحل المترجم (lexing→parsing→AST→symbol table→semantic→codegen).
- تفصيلي: راجع [00](00_PROJECT_OVERVIEW.md#3). التطبيق هو «اللغة الهدف» لمرحلة التوليد.
- أشر إلى: `CompilerPipeline.run`.

**ما مدخلات ومخرجات المشروع؟**
- مدخلات: `examples/demo/app.py` + `templates/`. مخرجات: diagnostics، AST، symbol
  table، `generated-app/`. أشر إلى `Main.java` و`CompilerPipeline`.

## 2. أسئلة ANTLR

**لماذا ANTLR؟** قصير: توليد lexer/parser قوي من قواعد `.g4` مع دعم visitor.
تفصيلي: يوفّر إدارة الأولويات والأنماط (modes) وأصناف Context جاهزة. أشر إلى
`generate-antlr.ps1` و`FlaskParser.g4`.

**ما معنى `-visitor` و`-package`؟** يولّدان أصناف الزائر ويضعانها في الحزمة `antlr`.

## 3. Lexer vs Parser
**الفرق؟** Lexer: أحرف→tokens (`FlaskLexer.g4`). Parser: tokens→parse tree
(`FlaskParser.g4`). مثال: `def` كلمة مفتاحية من اللكسر؛ `func_def` قاعدة من البارسر.

## 4. Parse Tree vs AST
**الفرق؟** Parse Tree حرفي بكل الرموز؛ AST مجرّد بالمعنى. أشر إلى
[05](05_PARSE_TREE_AND_AST.md) و`FlaskVisitor`. مثال: اختفاء `LPAREN/COLON` في الـ AST.

## 5. OOP
**أين الوراثة؟** كل العقد ترث `ASTNode` (`Expression/Statement/Suite/TemplateNode`).
**أين Polymorphism؟** `ASTPrinter`/`ASTGraphvizPrinter` يستدعيان `getChildren()`/
`toString()` دون معرفة النوع. أشر إلى `ASTNode.java` و`ASTPrinter.java`.

## 6. Symbol Table
**كيف بُني؟** `SymbolTableVisitor` يبني `FlaskSymbolTable` بنطاقات (global/function/…).
**كيف تكتشفون التكرار؟** `addSymbol` يمنع نفس الاسم في نفس النطاق.

## 7. Semantic Analysis
**كيف تكتشفون undefined variable؟** `PythonSemanticAnalyzer.checkExpr` عند
`IdentifierExpr` مع `!isDefined` → PY001. مثال: `x = y` بدون `y`.

**لماذا `route` ليس تعريفاً مكرّراً؟** لأنه **استخدام سمة** يُسجَّل في `attributeUsages`
لا في النطاق (`FlaskSymbolTable.addAttribute`)؛ راجع [08](08_SYMBOL_TABLE_AND_SCOPES.md#9).

## 8. الربط بين Python وJinja
**كيف تنتقل products من Python إلى Jinja؟** `AppModelBuilder.extractProducts` يستخرج
schema من الـ AST → `AppModel` → `CrossLanguageSemanticAnalyzer` يقارنها باستخدام
القالب → `FlaskAppGenerator` يكتبها. أشر إلى [11](11_CROSS_LANGUAGE_LINKING.md).

**كيف تتحقّقون من `product.name`؟** `JinjaAttrExpr` (product.name) تُقارَن بـ
`productFields`؛ إن غاب المفتاح → X005.

## 9. Generator
**أين يحدث التوليد؟** `FlaskAppGenerator.generate`. **كيف تثبتون أنه ليس Hardcoded؟**
غيّرنا اسم منتج في المدخل إلى «Test Product From AST» فظهر في `generated-app/app.py`
(السطر 12). **لماذا الحذف POST؟** لأنه عملية تغيّر حالة؛ GET عليه يعطي 405.

## 10. الاختبارات
**كيف اختبرتم؟** 30 اختبار Java (`TestRunner`) + 8 اختبارات Flask (pytest). **الفرق
بين Unit/Integration/E2E؟** راجع [14](14_TESTS_FILE_BY_FILE.md#ج-الفرق-بين-أنواع-الاختبارات-وأين-توجد).

## 11. Flask
**ما test client؟** أداة Flask لمحاكاة طلبات HTTP بلا سيرفر (`app.test_client()`).

## 12. الأخطاء والحلول
**ماذا يحدث عند Syntax Error؟** يُبلَّغ عبر `CompilerErrorListener`، ويتوقّف بناء الـ
AST (حارس pipeline)، وخروج 2. **عند Semantic Error؟** يُجمَع في التقرير، لا توليد،
خروج 2. **لماذا Exit Code = 2؟** يعني «فشل التحليل - أصلح الأخطاء قبل التوليد».

> نصيحة: افتح أثناء المناقشة `examples/demo/app.py`, `FlaskVisitor.java`,
> `CrossLanguageSemanticAnalyzer.java`, و`generated-app/app.py`.
