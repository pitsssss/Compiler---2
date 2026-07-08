# 20 – خطة دراسة الفريق (Team Study Plan)

توزيع متوازن لأربعة أعضاء مع مراعاة الترابط، وخطة 5 أيام.

## الجزء المشترك (يفهمه الجميع)
- Compiler Pipeline: [03](03_COMPILER_PIPELINE.md).
- متطلبات المشروع: [17](17_REQUIREMENTS_MAPPING.md).
- طريقة التشغيل: [01](01_SETUP_AND_FIRST_RUN.md).
- النتائج النهائية: 121 ملف، 30 اختبار Java، 8 pytest، 10 ملفات مولّدة.

## الشخص الأول — ANTLR / Lexer / Parser
- يقرأ: [04](04_ANTLR_LEXER_AND_PARSER.md)، `FlaskLexer.g4`, `FlaskParser.g4`,
  `TemplateLexer.g4`, `TemplateParser.g4`, `generate-antlr.ps1`.
- يفهم: modes، الأولويات، الإزاحة، line-joining، توليد ANTLR.
- تجربة عملية: عدّل قاعدة نحوية بسيطة ثم أعد التوليد والبناء.
- أسئلة محتملة: Lexer vs Parser، ترتيب القواعد، الأنماط.
- شرح 5 دقائق: «من النص إلى Parse Tree».

## الشخص الثاني — AST / Nodes / Visitors / Printer
- يقرأ: [05](05_PARSE_TREE_AND_AST.md), [06](06_AST_NODES_FILE_BY_FILE.md),
  [07](07_VISITORS_FILE_BY_FILE.md)، `FlaskVisitor.java`, `TemplateVisitor.java`,
  `ASTPrinter.java`, `ASTGraphvizPrinter.java`.
- يفهم: الوراثة، `getChildren()`، بناء العقد، `ast.dot`.
- تجربة عملية: `--print-python-ast` وتوليد `ast.png`.
- أسئلة محتملة: Parse Tree vs AST، Polymorphism، حفظ السطر/العمود.
- شرح 5 دقائق: «كيف يتحوّل السطر إلى شجرة».

## الشخص الثالث — Symbol Table / Diagnostics / Semantic
- يقرأ: [08](08_SYMBOL_TABLE_AND_SCOPES.md), [09](09_DIAGNOSTICS_AND_ERROR_HANDLING.md),
  [10](10_SEMANTIC_ANALYSIS.md)، `FlaskSymbolTable.java`, `SymbolTableVisitor.java`,
  `PythonSemanticAnalyzer.java`, `TemplateSemanticAnalyzer.java`, `diagnostics/*`.
- يفهم: النطاقات، التعريف/الاستخدام، أكواد الأخطاء.
- تجربة عملية: شغّل `examples/invalid` وحلّل الأخطاء.
- أسئلة محتملة: undefined variable، لماذا route ليس تكراراً، جمع الأخطاء.
- شرح 5 دقائق: «كيف نكتشف الأخطاء الدلالية».

## الشخص الرابع — Cross-language / Generator / App / Tests
- يقرأ: [11](11_CROSS_LANGUAGE_LINKING.md), [12](12_CODE_GENERATION.md),
  [15](15_GENERATED_FLASK_APP.md), [14](14_TESTS_FILE_BY_FILE.md)، `model/*`,
  `CrossLanguageSemanticAnalyzer.java`, `FlaskAppGenerator.java`, `TestRunner.java`.
- يفهم: AppModel، تدفّق البيانات، التوليد، الاختبارات.
- تجربة عملية: غيّر بيانات منتج في المدخل وأثبت ظهوره في `generated-app/app.py`.
- أسئلة محتملة: نقل البيانات، ليس Hardcoded، الحذف POST، أنواع الاختبارات.
- شرح 5 دقائق: «من products في Python إلى الصفحة».

## برنامج 5 أيام
| اليوم | الجميع | الأفراد |
|-------|--------|---------|
| 1 | التشغيل من الصفر ([01]) + Pipeline ([03]) | كلٌّ يقرأ مقدمة قسمه |
| 2 | AST ([05]) | الأول: ANTLR؛ الثاني: العقد |
| 3 | التحليل الدلالي ([10]) | الثالث: Symbol/Diag؛ الرابع: AppModel |
| 4 | الربط والتوليد ([11],[12]) | الرابع: Generator؛ الآخرون: اختبار أقسامهم |
| 5 | مراجعة + بروفة مناقشة ([19]) | كلٌّ يشرح قسمه 5 دقائق |
