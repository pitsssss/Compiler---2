# 01 – التجهيز والتشغيل الأول (Setup & First Run)

هذا الدليل يجهّز المشروع على **Windows + Cursor/VS Code** ويشغّله من الصفر.
النتائج المذكورة في نهاية الملف هي **نتائج فعلية** التقطناها من الطرفية.

## 1. المتطلبات

| المتطلب | الإصدار/الملاحظة | كيف تتحقق |
|---------|------------------|-----------|
| **JDK** | Java 17+ (تم التحقق على **JDK 21**؛ الكود يستخدم `switch` expressions وtext blocks) | `java -version` و`javac -version` |
| **PowerShell** | 5.1+ (الافتراضي على Windows) | `$PSVersionTable.PSVersion` |
| **Python** | 3.10+ (لاختبارات التطبيق الناتج فقط) | `python --version` |
| **ANTLR jar** | `antlr-4.13.2-complete.jar` داخل `lib/` | موجود في المستودع |
| **Cursor/VS Code Java** | إضافة "Extension Pack for Java" (اختياري للتحرير) | من سوق الإضافات |
| **Graphviz** | اختياري، لعرض `ast.dot` كصورة | `dot -V` |

## 2. البنية الأساسية المتعلقة بالبناء

- **`lib/antlr-4.13.2-complete.jar`**: مكتبة ANTLR (تُستخدم للتوليد ووقت التشغيل runtime). كل الأوامر تعثر عليها عبر الدالة `Get-AntlrJar` في `scripts/_common.ps1`.
- **`out/`**: مجلد ناتج الترجمة (`.class`). يُنشأ ويُنظّف تلقائياً، ولا يُرفع للمستودع.
- **`src/`**: كل مصدر Java (يدوي + ملفات ANTLR المولّدة تحت `src/antlr/`).
- **`generated-app/`**: التطبيق الناتج (يُنشأ عند `--generate`).

## 3. أوامر التنفيذ بالترتيب

من **جذر المشروع** في طرفية PowerShell:

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass

.\scripts\clean.ps1
.\scripts\generate-antlr.ps1
.\scripts\build.ps1

.\scripts\run.ps1 --flask .\examples\demo\app.py --template .\examples\demo\templates --generate

.\scripts\test.ps1
```

## 4. شرح كل أمر كلمة كلمة

- **`Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass`**
  يسمح بتشغيل سكربتات `.ps1` في **هذه الجلسة فقط** (Scope Process)، دون تغيير
  سياسة النظام. ضروري لأن Windows يمنع تشغيل السكربتات افتراضياً.

- **`.\scripts\clean.ps1`**
  يحذف `out/`, `sources.txt`, `generated-app/.venv`, `generated-app/.pytest_cache`
  ومجلدات `__pycache__`. **لا يحذف** المصدر ولا القواعد ولا الوثائق ولا مصدر
  `generated-app`. (انظر `scripts/clean.ps1`.)

- **`.\scripts\generate-antlr.ps1`**
  يشغّل ANTLR على ملفات `.g4`: أولاً الـ Lexers ثم الـ Parsers (بخيار `-visitor`)،
  والناتج في `src/antlr/` ضمن الحزمة `antlr`.

- **`.\scripts\build.ps1`**
  ينظّف `out/`، يولّد ANTLR إن كانت الملفات مفقودة، يبني ملف argfile اسمه
  `sources.txt` (مسارات بين علامتي اقتباس وبـ forward slashes)، ثم يشغّل
  `javac -encoding UTF-8 -cp <antlr.jar> -d out @sources.txt`.

- **`.\scripts\run.ps1 --flask ... --template ... --generate`**
  يبني عند الحاجة ثم يشغّل `java -cp "out;<jar>" Main` ويمرّر الوسائط إلى `Main`.
  - `--flask <path>`: ملف Python/Flask.
  - `--template <path>`: ملف أو مجلد قوالب.
  - `--generate`: فعّل توليد التطبيق في `generated-app/`.

- **`.\scripts\test.ps1`**
  يبني، يشغّل `java -cp "out;<jar>" tests.TestRunner` (اختبارات Java)، ثم — إذا
  توفّر Python — يشغّل `scripts/test-flask-app.ps1` الذي ينشئ venv معزولاً، يثبّت
  المتطلبات، ويشغّل `pytest -q` داخل `generated-app/`.

## 5. النتائج المتوقعة (نتائج فعلية مُلتقطة)

**generate-antlr + build:**

```text
[ OK ] Lexers generated
[ OK ] Parsers + visitors generated
[ OK ] ANTLR generation complete (src/antlr/*.java)
[INFO] Compiling 121 Java files...
[ OK ] Build succeeded -> out/
```

**run (demo مع --generate):**

```text
Parsing Flask source: examples\demo\app.py
Routes discovered: [home [GET] / (line 11), list_products [GET] /products (line 15), product_details [GET] /products/details (line 19), add_product [GET] /products/add (line 23)]
Products discovered: 3 with fields [id, name, price, in_stock, description, category]
Parsing template: examples\demo\templates\add_product.html
Parsing template: examples\demo\templates\base.html
Parsing template: examples\demo\templates\products.html
Parsing template: examples\demo\templates\product_details.html

==================== DIAGNOSTICS ====================
No diagnostics. Everything looks good.
-----------------------------------------------------
Errors: 0   Warnings: 0
=====================================================

Analysis completed successfully.

Generating Flask app into: generated-app
Generated 10 files:
  - app.py
  - requirements.txt
  - README.md
  - templates\base.html
  - templates\products.html
  - templates\product_details.html
  - templates\add_product.html
  - templates\not_found.html
  - static\style.css
  - tests\test_app.py
```

**test.ps1 (المُلخّص الفعلي):**

```text
Passed: 30   Failed: 0
...
8 passed in 0.17s
[ OK ] Generated Flask app tests passed.
[ OK ] All tests passed.
```

> الأرقام أعلاه فعلية: **121** ملف Java، **0** أخطاء، **0** تحذيرات، **10** ملفات
> مولّدة، **30** اختبار Java ناجح، **8** اختبارات pytest ناجحة.

## 6. تشغيل التطبيق الناتج (generated-app)

بعد أن يكون `test.ps1` قد أنشأ الـ venv، شغّل:

```powershell
cd generated-app
.\.venv\Scripts\python.exe app.py
```

ثم افتح المتصفح على:

```text
http://127.0.0.1:5000
```

إذا لم يكن الـ venv موجوداً، أنشئه يدوياً:

```powershell
cd generated-app
python -m venv .venv
.\.venv\Scripts\python.exe -m pip install -r requirements.txt
.\.venv\Scripts\python.exe app.py
```

## 7. إيقاف السيرفر

في نافذة الطرفية التي يعمل فيها Flask اضغط **Ctrl + C**. إذا بقي المنفذ 5000
مشغولاً بعملية معلّقة راجع [18_TROUBLESHOOTING.md](18_TROUBLESHOOTING.md).

## 8. مشاهدة الـ AST من ast.dot

الملف `ast.dot` في جذر المشروع يمثّل **Python AST** بصيغة Graphviz (يولّده
`ASTGraphvizPrinter`). لعرضه كصورة (يتطلب Graphviz):

```powershell
dot -Tpng ast.dot -o ast.png
dot -Tsvg ast.dot -o ast.svg
```

أو الصقه في محرر Graphviz أونلاين. التفاصيل في
[05_PARSE_TREE_AND_AST.md](05_PARSE_TREE_AND_AST.md).
