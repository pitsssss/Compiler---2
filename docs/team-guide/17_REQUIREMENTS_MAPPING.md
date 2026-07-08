# 17 – ربط المتطلبات بالتنفيذ (Requirements Mapping)

الجدول يربط متطلبات مشروع المترجمات الجامعي بالتنفيذ الفعلي والأدلة. **لا نضع
«Complete» إلا بدليل من الكود/الاختبارات.**

| المتطلب الرسمي | كيف نُفّذ | الملفات | الاختبارات | الحالة | ملاحظات |
|-----------------|-----------|---------|-------------|--------|---------|
| Lexer/Parser لـ Python | قواعد ANTLR + معالجة إزاحة | `FlaskLexer.g4`, `FlaskParser.g4` | valid/invalid parse | ✅ Complete | Subset من Python |
| Lexer/Parser لـ Jinja2/HTML/CSS | lexer بأنماط + parser | `TemplateLexer.g4`, `TemplateParser.g4` | valid template parse | ✅ Complete | Subset |
| شجرتا AST | Python AST + Template AST | `src/AST/**` (85 ملف) | AST tests | ✅ Complete | جذران: `Program`, `HtmlDocument` |
| نقل البيانات من Python إلى Jinja | AppModel + Cross analyzer + Generator | `model/*`, `CrossLanguageSemanticAnalyzer`, `FlaskAppGenerator` | X001/X003/X005 + generator | ✅ Complete | «Test Product From AST» يثبته |
| OOP | تسلسل أصناف العقد | `ASTNode` وفروعها | — | ✅ Complete | abstract/concrete |
| Inheritance | `Expression/Statement/Suite/TemplateNode` ترث `ASTNode` | `src/AST/**` | — | ✅ Complete | — |
| Polymorphism | `getChildren()`/`toString()` مُعاد تعريفها | `ASTPrinter`, `ASTGraphvizPrinter` | — | ✅ Complete | طابع واحد لكل الشجر |
| معلومات العقدة ورقم السطر | `lineNumber` (+`columnNumber`) | `ASTNode` | function line test | ✅ Complete | العمود جزئي |
| ≥ 5 أخطاء دلالية | 7 PY + 6 TPL + 5 X | `semantic/*` | 19 اختبار دلالي | ✅ Complete | يفوق المطلوب |
| توليد أجزاء تعمل معاً | تطبيق Flask CRUD | `FlaskAppGenerator` | pytest 8 | ✅ Complete | — |
| واجهات عرض/إضافة/تفاصيل/حذف | قوالب + routes | `generated-app/**` | pytest | ✅ Complete | — |
| تنقّل الواجهات | navbar + `url_for` | `base.html` المولّد | — | ✅ Complete | — |
| طباعة كل عقدة | `toString()` لكل عقدة | كل عقد AST | — | ✅ Complete | — |
| طباعة الشجرة | طابع شجري نصّي | `ASTPrinter` | countNodes/render | ✅ Complete | `--print-*-ast` |
| Symbol Table | جدول رموز بنطاقات | `FlaskSymbolTable`, `SymbolTableVisitor` | 4 اختبارات | ✅ Complete | تمييز التعريف/الاستخدام |
| التقرير ومخطط AST | وثائق + `ast.dot` | `docs/**`, `ASTGraphvizPrinter` | — | ✅ Complete | Graphviz |

## أدلة سريعة
- بناء: `121 Java files`, `Errors: 0`.
- اختبارات Java: `Passed: 30   Failed: 0`.
- اختبارات التطبيق: `8 passed`.
- توليد: `Generated 10 files`.
