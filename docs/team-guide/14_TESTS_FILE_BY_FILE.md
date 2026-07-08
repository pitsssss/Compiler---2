# 14 – الاختبارات ملفاً ملفاً (Tests)

## أ) اختبارات Java

الملفات: `src/Tests/TestRunner.java` (harness) و`src/Tests/TestSupport.java`
(مساعدات: `parseFlask`, `parseTemplate`, `buildSymbolTable`, `buildModel`,
`analyzePython`, `analyzeTemplate`, `analyzeCross`, `hasCode`). كلاهما في الحزمة
`tests`. تُشغَّل بـ `java -cp "out;jar" tests.TestRunner` (عبر `test.ps1`).

**النتيجة الفعلية الملتقطة: `Passed: 30   Failed: 0`.** التشغيل يحدث مرة واحدة والإخراج
منسّق ومجمّع بأقسام (لا تكرار).

### Lexer / Parser (4)
| الاختبار | Input | Assertion | يثبت |
|----------|-------|-----------|------|
| valid Flask program parses | `x=1; def foo(): return x` | 0 أخطاء + للبرنامج أبناء | صحة اللكسر/البارسر |
| invalid Flask program reports syntax error | `def (:` | `countErrors()>0` | كشف الأخطاء النحوية |
| multi-line dict/list literal parses | list/dict متعدد الأسطر | 0 أخطاء | line-joining داخل الأقواس |
| valid template parses | HTML+`{{ title }}` | 0 أخطاء + جذر غير null | صحة لكسر/بارسر القوالب |

### AST (2)
| الاختبار | Input | Assertion |
|----------|-------|-----------|
| function def carries name/params/line | `def greet(a,b): return a` | name=greet, params=[a,b], line≥1 |
| dict literal produces DictLiteralExpr | `d={"a":1,"b":2}` | عقدة `DictLiteralExpr` بحجم 2 |

### Symbol Table (4)
- **repeated attribute access (app.route) not duplicate**: يبني جدولاً لبرنامج فيه
  routeان؛ يتحقّق أن هناك `attributeUsages` وأن الدالتين مُسجّلتان — أي أن `route`
  استخدام لا تعريف مكرّر.
- **true duplicate rejected**: `addSymbol("x")` مرتين → الأولى true والثانية false.
- **built-ins resolve but not declarations**: `isBuiltin("append"/"len")` true و
  `getTotalSymbolCount()==0`.
- **lookup finds imported module**: بعد `from flask import ...` و`app=Flask(...)`،
  `isDeclared("app")` true.

### Semantic — Python (10)
PY001 (+حالة نظيفة)، PY002، PY003، PY004، PY005، PY006، PY007، برنامج نظيف بلا أخطاء،
واختبار «جمع عدّة أخطاء في تمريرة واحدة» (`return z` + `break` → ≥2). كل واحد يستخدم
`assertCode(analyzePython(src), "PYxxx")`.

### Semantic — Template (4)
TPL001، TPL003، TPL004، وقالب نظيف (`{{ name | upper }}`) بلا أخطاء.

### Semantic — Cross-language (5)
X001، X002، X003، X005، وحالة سليمة (لا X001/X003/X005). تستخدم
`analyzeCross(flaskSrc, templatesMap)`.

### Code Generator (1)
`generator writes a complete, non-empty Flask project`: يبني `AppModel` من مصدر فيه
منتج «Keyboard»، يولّد إلى مجلد مؤقت، ويتحقّق أن: عدد الملفات ≥ 8، الملفات الأساسية غير
فارغة، `app.py` يحوي «products» و«Keyboard» (**إثبات تدفّق البيانات من AST**)، والحذف
عبر POST.

**الإجمالي: 4+2+4+10+4+5+1 = 30 اختبار → كلها PASS.**

## ب) اختبارات Flask (`generated-app/tests/test_app.py`)

تُشغَّل بـ pytest عبر Flask **test client** داخل venv معزول. **النتيجة الفعلية:
`8 passed`.**

| # | الاختبار | Route | Method | يتوقّع |
|---|----------|-------|--------|--------|
| 1 | list_products_ok | `/products` | GET | 200 |
| 2 | home_redirects | `/` | GET | 301/302 |
| 3 | add_page_ok | `/products/add` | GET | 200 |
| 4 | details_ok | `/products/<id>` | GET | 200 (لأول منتج) |
| 5 | details_missing_404 | `/products/999999` | GET | 404 |
| 6 | add_creates_and_redirects | `/products/add` | POST | 301/302 + العدد +1 |
| 7 | delete_via_get_not_allowed | `/products/<id>/delete` | GET | 405 |
| 8 | delete_removes_and_redirects | `/products/<id>/delete` | POST | 301/302 + العدد −1 |

## ج) الفرق بين أنواع الاختبارات وأين توجد
- **Unit Test**: يفحص وحدة صغيرة معزولة. مثال: اختبارات AST/Symbol Table في `TestRunner`.
- **Integration Test**: يفحص تكامل عدّة مراحل. مثال: اختبارات Cross-language والمولّد
  (parse → model → analyze/generate).
- **End-to-End Test**: المسار الكامل من الإدخال إلى تطبيق يعمل — تشغيل الـ pipeline ثم
  pytest على التطبيق الناتج (عبر `test.ps1`).
- **Flask Test Client Test**: `generated-app/tests/test_app.py` (طلبات HTTP وهمية بلا
  سيرفر حقيقي).
