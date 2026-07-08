# 21 – فهرس الملفات الكامل (Complete File Index)

## أ) قواعد ANTLR (يدوية) وملفاتها المولّدة

| المسار | النوع | الوظيفة | المرحلة | مستخدم؟ |
|--------|-------|---------|---------|----------|
| `src/antlr/FlaskLexer.g4` | يدوي | قواعد لكسر Python | Lexing | ✅ |
| `src/antlr/FlaskParser.g4` | يدوي | قواعد نحو Python | Parsing | ✅ |
| `src/antlr/TemplateLexer.g4` | يدوي | قواعد لكسر القوالب | Lexing | ✅ |
| `src/antlr/TemplateParser.g4` | يدوي | قواعد نحو القوالب | Parsing | ✅ |
| `src/antlr/FlaskLexer.java` | مولّد | lexer | Lexing | ✅ (لا يُعدّل) |
| `src/antlr/FlaskParser.java` | مولّد | parser + Context | Parsing | ✅ (لا يُعدّل) |
| `src/antlr/FlaskParserVisitor.java` | مولّد | واجهة زائر | Parsing | ✅ |
| `src/antlr/FlaskParserBaseVisitor.java` | مولّد | زائر أساس | Parsing | ✅ (يُورَث) |
| `src/antlr/FlaskParserListener.java` | مولّد | listener | — | ⚠️ غير مستخدم |
| `src/antlr/FlaskParserBaseListener.java` | مولّد | base listener | — | ⚠️ غير مستخدم |
| `src/antlr/TemplateLexer.java` | مولّد | lexer قوالب | Lexing | ✅ |
| `src/antlr/TemplateParser.java` | مولّد | parser قوالب | Parsing | ✅ |
| `src/antlr/TemplateParserVisitor.java` | مولّد | واجهة زائر | Parsing | ✅ |
| `src/antlr/TemplateParserBaseVisitor.java` | مولّد | زائر أساس | Parsing | ✅ (يُورَث) |
| `src/antlr/TemplateParserListener.java` | مولّد | listener | — | ⚠️ غير مستخدم |
| `src/antlr/TemplateParserBaseListener.java` | مولّد | base listener | — | ⚠️ غير مستخدم |

> الـ listeners مولّدة تلقائياً؛ المشروع يستخدم نمط **Visitor** لا Listener.

## ب) نقطة الدخول والتنسيق

| المسار | يدوي | الوظيفة | المرحلة |
|--------|------|---------|---------|
| `src/Main.java` | يدوي | CLI | Entry |
| `src/pipeline/CompilerPipeline.java` | يدوي | تنسيق المراحل | All |

## ج) AST (يدوي، 85 ملف — أهمها)

| المسار | الوظيفة |
|--------|---------|
| `src/AST/ASTNode.java` | القاعدة العليا |
| `src/AST/Program.java` | جذر Python AST |
| `src/AST/expr/*.java` | التعبيرات (Identifier/Call/Attribute/Index/Binary/…) |
| `src/AST/literal/*.java` | الحرفيات (String/Number/Boolean/None/List/Set/Dict) |
| `src/AST/stmt/*.java` | الجُمل (Assignment/FunctionDef/Decorated/Return/If/For/…) |
| `src/AST/suite/*.java` | الكتل (Block/Inline) |
| `src/AST/template/**` | Template AST (html/css/jinja) — جذر `HtmlDocument` |
| `src/AST/visitor/FlaskVisitor.java` | Parse Tree → Python AST |
| `src/AST/visitor/ProgramVisitor.java` | غلاف بناء `Program` |
| `src/AST/template/TemplateVisitor.java` | Parse Tree → Template AST |
| `src/AST/ASTPrinter.java` | طابع شجري نصّي |
| `src/AST/ASTGraphvizPrinter.java` | مولّد `ast.dot` |

> القائمة الكاملة لعقد AST مشروحة في [06](06_AST_NODES_FILE_BY_FILE.md).

## د) Symbol Table

| المسار | يدوي | مستخدم؟ | ملاحظات |
|--------|------|----------|---------|
| `src/SymbolTable/FlaskSymbolTable.java` | يدوي | ✅ | الجدول الفعلي (+ أصناف داخلية Symbol/Scope) |
| `src/SymbolTable/SymbolTableVisitor.java` | يدوي | ✅ | يبني الجدول |
| `src/SymbolTable/Scope.java` | يدوي | ⚠️ قديم | نسخة منفصلة غير مستخدمة |
| `src/SymbolTable/Symbol.java` | يدوي | ⚠️ قديم | نسخة منفصلة غير مستخدمة |
| `src/SymbolTable/TemplateSymbolTable.java` | يدوي | ⚠️ قديم | جدول رموز قوالب غير مُفعّل |
| `src/SymbolTable/JinjaASTVisitor.java` | يدوي | ⚠️ قديم | زائر قوالب غير مُستدعى |

## هـ) Diagnostics / Model / Semantic / Generator

| المسار | الوظيفة |
|--------|---------|
| `src/diagnostics/Diagnostic.java` | كائن رسالة |
| `src/diagnostics/DiagnosticKind.java` | فئة المرحلة |
| `src/diagnostics/DiagnosticSeverity.java` | الشدة |
| `src/diagnostics/DiagnosticReporter.java` | جمع/عرض |
| `src/diagnostics/CompilerErrorListener.java` | ربط أخطاء ANTLR |
| `src/model/AppModel.java` | الجسر (routes/products/…) |
| `src/model/AppModelBuilder.java` | يبني AppModel من AST |
| `src/model/RouteInfo.java` | route مكتشف |
| `src/model/RenderCall.java` | `render_template` |
| `src/model/UrlForRef.java` | `url_for` |
| `src/semantic/PythonSemanticAnalyzer.java` | PY001–PY007 |
| `src/semantic/TemplateSemanticAnalyzer.java` | TPL001–TPL006 |
| `src/semantic/CrossLanguageSemanticAnalyzer.java` | X001–X005 |
| `src/generator/FlaskAppGenerator.java` | توليد التطبيق |

## و) الاختبارات

| المسار | الوظيفة |
|--------|---------|
| `src/Tests/TestRunner.java` | 30 اختبار Java |
| `src/Tests/TestSupport.java` | مساعدات الاختبار |

## ز) السكربتات والأمثلة والوثائق والناتج

| المسار | النوع | الوظيفة |
|--------|-------|---------|
| `scripts/_common.ps1` | سكربت | دوال مشتركة |
| `scripts/generate-antlr.ps1` | سكربت | توليد ANTLR |
| `scripts/build.ps1` | سكربت | البناء |
| `scripts/run.ps1` | سكربت | التشغيل |
| `scripts/test.ps1` | سكربت | الاختبار |
| `scripts/clean.ps1` | سكربت | التنظيف |
| `scripts/test-flask-app.ps1` | سكربت | pytest للتطبيق |
| `examples/demo/app.py` + `templates/*` | مدخلات | مثال صحيح |
| `examples/invalid/*` | مدخلات | مثال أخطاء |
| `generated-app/**` | مولّد | التطبيق الناتج (10 ملفات) |
| `docs/**` | وثائق | تقارير وأدلة |
| `lib/antlr-4.13.2-complete.jar` | مكتبة | ANTLR |
| `ast.dot` | ناتج | مخطط AST |
| `out/**` | ناتج بناء | `.class` |

## ح) ملفات مرشّحة للتنظيف (اختياري، ليست ضمن هذه المهمة)
- `src/SymbolTable/Scope.java`, `Symbol.java`, `TemplateSymbolTable.java`,
  `JinjaASTVisitor.java` — بقايا تصميم سابق غير مستخدمة في التدفّق الحالي. تُركت دون
  حذف لأن المهمة **توثيق لا تعديل**.
