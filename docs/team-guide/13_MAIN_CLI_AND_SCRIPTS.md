# 13 – واجهة الأوامر والسكربتات (Main CLI & Scripts)

## 1. `src/Main.java`
نقطة الدخول. لا منطق مترجم فيها. تملأ `CompilerPipeline.Config` من الوسائط ثم تستدعي
`new CompilerPipeline().run(cfg)`.

### الوسائط الفعلية المدعومة
| الوسيط | المعنى |
|--------|--------|
| `--flask <path>` | ملف Python/Flask |
| `--template <path>` | ملف أو مجلد قوالب |
| `--output <path>` | مجلد إخراج التطبيق (افتراضي `generated-app`) |
| `--print-python-ast` | طباعة Python AST |
| `--print-template-ast` | طباعة AST لكل قالب |
| `--print-symbol-table` | طباعة جدول الرموز |
| `--generate` | توليد التطبيق |
| `--analyze-only` | تحليل فقط دون توليد |
| `--help`, `-h` | المساعدة |

- **بدون وسائط**: يشغّل الـ demo (`examples/demo`) مع `--generate` وطباعة AST/الرموز.
- **تحقّقات**: وسيط غير معروف → طباعة المساعدة + خروج 64؛ ملف غير موجود → خروج 66.

## 2. `src/pipeline/CompilerPipeline.java`
منسّق كل المراحل (شرحه الكامل في [03_COMPILER_PIPELINE.md](03_COMPILER_PIPELINE.md)).

## 3. لماذا أُزيلت المسارات المطلقة؟
حتى يعمل المشروع على أي جهاز/مجلد. كل السكربتات تحسب الجذر نسبياً في `_common.ps1`:
`$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")`، وتعثر على الـ jar عبر
`Get-AntlrJar`. لا يوجد مسار مثل `C:\Users\...` مضمّن في الكود.

## 4. Classpath على Windows
```text
".\out;.\lib\antlr-4.13.2-complete.jar"
```
الفاصلة المنقوطة **`;`** هي فاصل مسارات classpath على **Windows** (على Linux/Mac تكون
`:`). المعنى: «ابحث عن الأصناف في `out/` **و** في jar الخاص بـ ANTLR».

## 5. السكربتات (`scripts/`)

### `_common.ps1`
- **لماذا؟** دوال مشتركة: `Get-RepoRoot`, `Get-AntlrJar`, `Write-Ok/Info/Fail`,
  `Invoke-Checked`. يضبط `$ErrorActionPreference = "Stop"`.
- **الفشل**: `Get-AntlrJar` يرمي رسالة واضحة إذا كان الـ jar مفقوداً.

### `generate-antlr.ps1`
- **الوظيفة**: توليد lexers ثم parsers (بـ `-visitor`) في `src/antlr` بالحزمة `antlr`.
- **مثال**: `.\scripts\generate-antlr.ps1`. **عند الفشل**: `Invoke-Checked` يخرج بكود
  ANTLR غير الصفري.

### `build.ps1`
- **الوظيفة**: ينظّف `out/`، يولّد ANTLR إن كان `src/antlr/FlaskParser.java` مفقوداً،
  يبني `sources.txt` (مسارات مقتبسة بـ forward slashes)، ثم `javac -encoding UTF-8`.
- **ينشئ/يحذف**: `out/`, `sources.txt`. **عند الفشل**: يطبع رسالة ويخرج بكود javac.

### `run.ps1`
- **الوظيفة**: يبني إن غاب `out/Main.class`، ثم `java -cp "out;jar" Main @args`.
- **مثال**: `.\scripts\run.ps1 --flask .\examples\demo\app.py --template .\examples\demo\templates --generate`.
- **Exit code**: يمرّر كود `Main`/`CompilerPipeline`.

### `test.ps1`
- **الوظيفة**: يبني، يشغّل `tests.TestRunner`، ثم — إن توفّر Python — `test-flask-app.ps1`.
- **Exit code**: 1 إذا فشل أي اختبار، وإلا 0.

### `clean.ps1`
- **الوظيفة**: يحذف `out/`, `sources.txt`, `generated-app/.venv`,
  `generated-app/.pytest_cache`, ومجلدات `__pycache__`. **لا يحذف** المصدر ولا
  `generated-app` (المصدر المولّد) ولا الوثائق.

### `test-flask-app.ps1`
- **الوظيفة**: ينشئ venv معزولاً في `generated-app/.venv`، يثبّت `requirements.txt`،
  ويشغّل `pytest -q`. **عند غياب Python/generated-app**: رسالة فشل وخروج 1.
