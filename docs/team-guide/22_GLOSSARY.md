# 22 – قاموس المصطلحات (Glossary)

| الإنجليزية | العربية | تعريف مبسّط | مثال/ملف في المشروع |
|------------|---------|-------------|----------------------|
| Compiler | مترجم | يحوّل لغة مصدر إلى لغة هدف عبر مراحل | `CompilerPipeline` |
| Lexer | محلّل معجمي | يحوّل الأحرف إلى tokens | `FlaskLexer.g4` |
| Token | رمز | أصغر وحدة ذات معنى | `IDENTIFIER`, `DEF` |
| Parser | محلّل نحوي | يحوّل tokens إلى parse tree | `FlaskParser.g4` |
| Grammar | قواعد | تعريف اللغة (lexer/parser) | ملفات `.g4` |
| Parse Tree | شجرة الاشتقاق | تمثيل حرفي لكل قاعدة | `ProgContext` |
| AST | شجرة تجريدية | تمثيل مجرّد بالمعنى | `Program`, `HtmlDocument` |
| Visitor | زائر | يحوّل parse tree إلى AST | `FlaskVisitor` |
| Listener | مستمع | نمط بديل للزائر (غير مستخدم) | `*Listener.java` |
| Semantic Analysis | التحليل الدلالي | فحص المعنى بعد النحو | `PythonSemanticAnalyzer` |
| Symbol | رمز | اسم ذو معنى | `FlaskSymbolTable.Symbol` |
| Symbol Table | جدول الرموز | تخزين الرموز والبحث فيها | `FlaskSymbolTable` |
| Scope | نطاق | منطقة رؤية الاسم | `Scope.ScopeType` |
| Diagnostic | تشخيص | رسالة خطأ/تحذير منظّمة | `Diagnostic` |
| Code Generation | توليد الكود | إنتاج لغة الهدف | `FlaskAppGenerator` |
| Route | مسار | ربط URL بدالة | `@app.route(...)`, `RouteInfo` |
| Endpoint | نقطة نهاية | اسم دالة المسار | `list_products` |
| Context Variable | متغيّر سياق | بيانات تُمرَّر للقالب | `products=products` |
| Template | قالب | ملف Jinja/HTML | `products.html` |
| Jinja Filter | فلتر Jinja | تحويل قيمة (`| upper`) | `JinjaFilterExpr` |
| Flask Test Client | عميل اختبار Flask | محاكاة طلبات HTTP بلا سيرفر | `app.test_client()` |
| Unit Test | اختبار وحدة | يفحص وحدة معزولة | اختبارات AST |
| Integration Test | اختبار تكامل | يفحص عدّة مراحل معاً | اختبارات المولّد |
| End-to-End Test | اختبار طرفي | المسار كاملاً | pipeline + pytest |
| Exit Code | كود الخروج | نتيجة العملية الرقمية | 0/2/3/64/66 |
| Classpath | مسار الأصناف | أين يبحث JVM عن الأصناف | `out;lib\...jar` |
| Generated Source | مصدر مولّد | كود أنتجته أداة | `src/antlr/*.java`, `generated-app/**` |
| INDENT/DEDENT | إزاحة/تراجع | رموز اصطناعية لمستوى الإزاحة | `FlaskLexer.g4` |
| Line Joining | ضمّ الأسطر | تجاهل newline داخل الأقواس | عدّاد `opened` |
| Polymorphism | تعدّد الأشكال | سلوك مختلف حسب النوع الفعلي | `getChildren()`/`toString()` |
| Inheritance | الوراثة | صنف يرث من آخر | `Expression extends ASTNode` |
