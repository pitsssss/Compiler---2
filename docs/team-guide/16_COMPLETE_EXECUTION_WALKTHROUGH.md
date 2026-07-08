# 16 – تتبّع تنفيذي كامل (Complete Execution Walkthrough)

نتتبّع تشغيل `examples/demo` خطوة بخطوة كما يحدث فعلياً في `CompilerPipeline`.

الأمر:
```powershell
.\scripts\run.ps1 --flask .\examples\demo\app.py --template .\examples\demo\templates --generate
```

| # | الخطوة | Class.Method | Input | Output |
|---|--------|--------------|-------|--------|
| 1 | قراءة `app.py` | `CharStreams.fromPath` | مسار الملف | `CharStream` |
| 2 | إنشاء lexer | `new FlaskLexer(input)` | `CharStream` | `FlaskLexer` |
| 3 | ربط الأخطاء | `addErrorListener(new CompilerErrorListener(...))` | reporter+file | — |
| 4 | تيار الرموز | `new CommonTokenStream(lexer)` | lexer | `CommonTokenStream` |
| 5 | إنشاء parser | `new FlaskParser(tokens)` | tokens | `FlaskParser` |
| 6 | القاعدة الجذر | `parser.prog()` | tokens | `ProgContext` (Parse Tree) |
| 7 | حارس الأخطاء | `reporter.hasErrors()` | — | يتابع (0 أخطاء) |
| 8 | بناء AST | `new ProgramVisitor().visit(tree)` | ProgContext | `Program` |
| 9 | جدول الرموز | `SymbolTableVisitor.visitProgram` | Program | `FlaskSymbolTable` |
| 10 | النموذج | `new AppModelBuilder().build(program)` | Program | `AppModel` |
| 11 | تحليل Python | `PythonSemanticAnalyzer.analyze(program, model)` | Program+model | diagnostics |
| 12 | كتابة ast.dot | `ASTGraphvizPrinter.print(program, "ast.dot")` | Program | `ast.dot` |
| 13 | قراءة القوالب | `analyzeTemplates` → `templateFiles` | مجلد templates | قائمة `.html` |
| 14 | بناء Template AST | `parseTemplate` → `new TemplateVisitor().visit(...)` | كل قالب | `HtmlDocument` |
| 15 | تحليل القوالب | `TemplateSemanticAnalyzer.analyze(root)` | HtmlDocument | diagnostics |
| 16 | تحليل عبر اللغات | `CrossLanguageSemanticAnalyzer.analyze()` | model+templates | diagnostics |
| 17 | التقرير | `reporter.printReport()` | diagnostics | نص + ملخّص |
| 18 | قرار التوليد | `run` (hasErrors?) | — | 0 أخطاء → توليد |
| 19 | التوليد | `FlaskAppGenerator.generate(outputDir)` | AppModel | `generated-app/` (10 ملفات) |
| 20 | اختبار (اختياري) | `test.ps1` → pytest | generated-app | `8 passed` |

## أمثلة بيانات فعلية عبر الخطوات

- **بعد الخطوة 6–8** (AST): جملة `products = [...]` تصبح
  `AssignmentStmt(IdentifierExpr("products"), ListLiteralExpr([DictLiteralExpr,...]))`.
- **بعد الخطوة 10** (AppModel): الطرفية تطبع فعلياً:
  ```text
  Routes discovered: [home [GET] / (line 11), list_products [GET] /products (line 15),
                      product_details [GET] /products/details (line 19),
                      add_product [GET] /products/add (line 23)]
  Products discovered: 3 with fields [id, name, price, in_stock, description, category]
  ```
- **بعد الخطوة 14** (Template AST): `{{ product.name }}` في `products.html` يصبح
  `JinjaAttrExpr(target=JinjaIdentifierExpr("product"), attribute="name")`.
- **بعد الخطوة 16** (Cross): يتأكّد أن `products.html` موجود (X001)، وأن `product_details`
  endpoint موجود (X002)، وأن `products` مُمرَّر (X003)، وأن `name`/`in_stock` ضمن schema
  (X005).
- **بعد الخطوة 17**: 
  ```text
  Errors: 0   Warnings: 0
  Analysis completed successfully.
  ```
- **بعد الخطوة 19**:
  ```text
  Generated 10 files: app.py, requirements.txt, README.md, templates/*, static/style.css, tests/test_app.py
  ```

هذا الملف هو الأهم لفهم «كيف يتحرّك الكائن من مرحلة إلى مرحلة».
