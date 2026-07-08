# 02 – بنية المشروع (Project Structure)

## شجرة المجلدات الفعلية (المستوى الأعلى)

```text
Compiler---2/
├─ src/                     ← كل مصدر Java (يدوي + ANTLR المولّد)
│  ├─ antlr/                ← ملفات .g4 + ملفات Java المولّدة من ANTLR
│  ├─ AST/                  ← عقد الشجرة التجريدية + الزوّار + الطابعات
│  │  ├─ expr/  literal/  stmt/  suite/   (Python AST)
│  │  ├─ template/          (Template AST: html/ css/ jinja/…)
│  │  └─ visitor/           (FlaskVisitor, ProgramVisitor)
│  ├─ SymbolTable/          ← جدول الرموز (+ ملفات قديمة)
│  ├─ diagnostics/          ← نظام التشخيص والأخطاء
│  ├─ model/                ← AppModel والجسر بين Python والقوالب
│  ├─ semantic/             ← المحلّلات الدلالية الثلاثة
│  ├─ generator/            ← مولّد تطبيق Flask
│  ├─ pipeline/             ← CompilerPipeline (منسّق المراحل)
│  ├─ Tests/                ← TestRunner + TestSupport (اختبارات Java)
│  └─ Main.java             ← نقطة الدخول (CLI)
├─ scripts/                 ← سكربتات PowerShell (build/run/test/clean/…)
├─ examples/                ← مدخلات جاهزة: demo (صحيح) + invalid (خاطئ)
├─ generated-app/           ← التطبيق الناتج (يُولَّد عند --generate)
├─ docs/                    ← الوثائق (بما فيها team-guide/ هذا)
├─ out/                     ← ناتج الترجمة .class (يُنشأ آلياً)
├─ lib/                     ← antlr-4.13.2-complete.jar
├─ ast.dot                  ← تمثيل Graphviz لـ Python AST
├─ README.md
├─ TEAM_AND_REPOSITORY.txt
└─ .vscode/settings.json
```

## أعداد فعلية

- **ملفات Java إجمالاً**: 121 (تُترجم جميعها في البناء).
- **ملفات ANTLR مولّدة**: 12 (تحت `src/antlr/`).
- **ملفات Java يدوية**: 109.
- **ملفات AST يدوية**: 85 (تحت `src/AST/`).
- **ملفات مولّدة في generated-app**: 10.

## شرح كل مجلد

### `src/antlr/`
- **لماذا موجود؟** يحوي ملفات القواعد `.g4` الأربعة (`FlaskLexer`, `FlaskParser`,
  `TemplateLexer`, `TemplateParser`) و**الملفات المولّدة** منها بواسطة ANTLR.
- **يدوي أم مولّد؟** ملفات `.g4` يدوية؛ ملفات `.java` هنا **مولّدة** ولا تُعدّل يدوياً.
- **يدخل في Build؟** نعم — الملفات المولّدة تُترجم مع البقية.
- التفاصيل في [04_ANTLR_LEXER_AND_PARSER.md](04_ANTLR_LEXER_AND_PARSER.md).

### `src/AST/`
- **لماذا؟** يمثّل الشجرة التجريدية لكلٍّ من Python والقوالب، إضافة إلى الزوّار
  (`visitor/`, `template/TemplateVisitor.java`) والطابعات (`ASTPrinter`,
  `ASTGraphvizPrinter`).
- **يدوي**، ويدخل في Build وRuntime. التفاصيل في
  [06_AST_NODES_FILE_BY_FILE.md](06_AST_NODES_FILE_BY_FILE.md).

### `src/SymbolTable/`
- **لماذا؟** بناء جدول الرموز للـ Flask (`FlaskSymbolTable`, `SymbolTableVisitor`).
- **ملفات قديمة/غير مستخدمة فعلياً**: `Scope.java`, `Symbol.java`,
  `TemplateSymbolTable.java`, `JinjaASTVisitor.java` (باقية من تصميم سابق؛ راجع
  [08_SYMBOL_TABLE_AND_SCOPES.md](08_SYMBOL_TABLE_AND_SCOPES.md) و
  [21_COMPLETE_FILE_INDEX.md](21_COMPLETE_FILE_INDEX.md)).

### `src/diagnostics/`
- **لماذا؟** جمع وعرض الأخطاء/التحذيرات بشكل منظّم، وربط أخطاء ANTLR بالنظام.
- **يدوي**. التفاصيل في [09_DIAGNOSTICS_AND_ERROR_HANDLING.md](09_DIAGNOSTICS_AND_ERROR_HANDLING.md).

### `src/model/`
- **لماذا؟** `AppModel` هو الجسر الذي يستخرج من Python AST (routes, render calls,
  url_for, بيانات المنتجات) ليستخدمه التحليل عبر اللغات والتوليد.
- **يدوي**. التفاصيل في [11_CROSS_LANGUAGE_LINKING.md](11_CROSS_LANGUAGE_LINKING.md).

### `src/semantic/`
- **لماذا؟** التحليل الدلالي: `PythonSemanticAnalyzer`, `TemplateSemanticAnalyzer`,
  `CrossLanguageSemanticAnalyzer`.
- **يدوي**. التفاصيل في [10_SEMANTIC_ANALYSIS.md](10_SEMANTIC_ANALYSIS.md).

### `src/generator/`
- **لماذا؟** `FlaskAppGenerator` يبني ملفات `generated-app/` من `AppModel`.
- **يدوي**. التفاصيل في [12_CODE_GENERATION.md](12_CODE_GENERATION.md).

### `src/pipeline/`
- **لماذا؟** `CompilerPipeline` ينسّق كل المراحل بالترتيب ويقرّر متى يسمح بالتوليد.
- **يدوي**. التفاصيل في [03_COMPILER_PIPELINE.md](03_COMPILER_PIPELINE.md).

### `src/Tests/`
- **لماذا؟** `TestRunner` (harness خفيف بلا مكتبات) + `TestSupport` (مساعدات).
- **يدوي**، حزمته `tests`. التفاصيل في [14_TESTS_FILE_BY_FILE.md](14_TESTS_FILE_BY_FILE.md).

### `scripts/`
- **لماذا؟** أتمتة البناء والتشغيل على Windows. **يدوي** (PowerShell)، خارج Build/Runtime
  لكنه يشغّلهما. التفاصيل في [13_MAIN_CLI_AND_SCRIPTS.md](13_MAIN_CLI_AND_SCRIPTS.md).

### `examples/`
- **لماذا؟** مدخلات جاهزة: `demo/` (تمر بلا أخطاء) و`invalid/` (تُظهر التشخيصات).
- **Test fixtures** بمعنى المدخلات — تُقرأ وقت التشغيل، لا تدخل Build.

### `generated-app/`
- **لماذا؟** الناتج النهائي. **مولّد بالكامل** (لا يُحرّر يدوياً؛ يُعاد توليده).
  يدخل Runtime وTests (pytest) فقط، لا يدخل في Build الخاص بالمترجم (Java).

### `docs/`, `out/`, `lib/`
- `docs/`: وثائق فقط. `out/`: ناتج ترجمة مؤقت. `lib/`: مكتبة ANTLR.

## جدول ملخّص

| المسار | النوع | الوظيفة | يُستخدم بواسطة | ملاحظات |
|--------|-------|---------|----------------|---------|
| `src/antlr/*.g4` | مصدر يدوي | قواعد Lexer/Parser | `generate-antlr.ps1` | تُعاد توليدها |
| `src/antlr/*.java` | مولّد ANTLR | Lexer/Parser/Visitor | Build + Runtime | لا يُعدّل يدوياً |
| `src/AST/**` | مصدر يدوي | عقد AST + زوّار + طابعات | كل المراحل | 85 ملف |
| `src/SymbolTable/` | مصدر يدوي | جدول الرموز | Symbol/Semantic | فيه ملفات قديمة |
| `src/diagnostics/` | مصدر يدوي | التشخيص | كل المراحل | — |
| `src/model/` | مصدر يدوي | AppModel (الجسر) | Semantic + Generator | — |
| `src/semantic/` | مصدر يدوي | التحليل الدلالي | Pipeline | 3 محلّلات |
| `src/generator/` | مصدر يدوي | توليد Flask | Pipeline | — |
| `src/pipeline/` | مصدر يدوي | تنسيق المراحل | Main | — |
| `src/Tests/` | مصدر يدوي | اختبارات Java | `test.ps1` | حزمة `tests` |
| `scripts/` | سكربت | أتمتة | المستخدم | Windows/PowerShell |
| `examples/` | مدخلات | fixtures | Runtime | demo + invalid |
| `generated-app/` | مولّد | التطبيق الناتج | pytest + المتصفح | لا يُحرّر يدوياً |
| `out/` | ناتج بناء | `.class` | Runtime | يُنشأ آلياً |
| `lib/` | مكتبة | ANTLR jar | Build + Runtime | — |
| `docs/` | وثائق | شرح | القارئ | — |

## الفرق بين أنواع الملفات

- **Source files (مصدر يدوي)**: كتبها الفريق (`src/**/*.java` عدا `src/antlr/*.java`،
  و`.g4`، والسكربتات).
- **Generated ANTLR files**: أنتجها ANTLR من `.g4` (`src/antlr/*.java`).
- **Compiled class files**: ناتج `javac` في `out/`.
- **Test fixtures**: مدخلات الاختبار (`examples/`، ومقاطع inline داخل `TestRunner`).
- **Generated Flask files**: أنتجها `FlaskAppGenerator` في `generated-app/`.
- **Documentation files**: `docs/` و`README.md`.
