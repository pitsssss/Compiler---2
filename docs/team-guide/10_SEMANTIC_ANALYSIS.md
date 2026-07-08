# 10 – التحليل الدلالي (Semantic Analysis)

**التحليل الدلالي** يفحص **المعنى** بعد أن يكون النحو صحيحاً: هل الأسماء معرّفة؟ هل
`return` داخل دالة؟ هل الـ route مكرّر؟ … يُبلَّغ كل شيء عبر `DiagnosticReporter`.

المراجع: `src/semantic/PythonSemanticAnalyzer.java`,
`TemplateSemanticAnalyzer.java` (والربط عبر اللغات في
[11_CROSS_LANGUAGE_LINKING.md](11_CROSS_LANGUAGE_LINKING.md)).

## أ) `PythonSemanticAnalyzer`

يقوم بمروره الخاص المُدرَك للنطاقات (`scopes: Deque<Set<String>>`) مع عدّادَي
`functionDepth` و`loopDepth`. يبدأ بجمع أسماء المستوى الأعلى (`collectGlobalNames`)
حتى تُحلّ الأسماء بغض النظر عن ترتيبها النصّي، ثم يفحص كل جملة.

### PY001 — متغيّر غير معرّف (undefined variable)
- **لماذا دلالي؟** النحو سليم؛ المشكلة أن الاسم غير موجود في أي نطاق.
- **خاطئ**: `return total` دون تعريف `total`. **صحيح**: `total = 0` قبل استخدامه.
- **AST/شرط**: في `checkExpr` عند `IdentifierExpr`، إن `!isDefined(name)` → PY001.
- **اختبار**: «PY001 undefined variable» → PASS.

### PY002 — تعريف دالة مكرّر
- **خاطئ**: دالتان باسم `home`. **صحيح**: أسماء فريدة.
- **شرط**: `detectDuplicateTopLevel` عبر `seenFns.add(name)`.
- **اختبار**: «PY002 duplicate function definition» → PASS.

### PY003 — بارامتر مكرّر
- **خاطئ**: `def f(x, x)`. **شرط**: في `checkFunction` عبر `seen.add(p)`.
- **اختبار**: «PY003 duplicate parameter name» → PASS.

### PY004 — `return` خارج دالة
- **شرط**: في `checkStmt` عند `ReturnStmt` إذا `functionDepth == 0`.
- **اختبار**: «PY004 return outside a function» → PASS.

### PY005 — `break`/`continue` خارج حلقة
- **شرط**: عند `BreakStmt`/`ContinueStmt` إذا `loopDepth == 0`. (لاحظ أن الحلقات لا
  تعبر حدود الدالة: `checkFunction` يحفظ `loopDepth` ويصفّره داخل الدالة.)
- **اختبار**: «PY005 break outside a loop» → PASS.

### PY006 — استدعاء دالة غير معرّفة
- **خاطئ**: `ghost()` دون تعريف/استيراد. **شرط**: في `checkCall` عند
  `IdentifierExpr` إذا `!isDefined`.
- **اختبار**: «PY006 call to undefined function» → PASS.

### PY007 — مسار route مكرّر
- **خاطئ**: `@app.route("/")` مرتين. **شرط**: `detectDuplicateTopLevel` عبر
  `seenPaths.add(routePath(d))`.
- **اختبار**: «PY007 duplicate route path» → PASS.

> ملاحظة: `BUILTINS` (سطر 37) قائمة أسماء معروفة (بايثون + سطح Flask) تُعتبر «معرّفة»
> دائماً، فلا تُبلَّغ كـ PY001/PY006.

## ب) `TemplateSemanticAnalyzer`

يفحص كل قالب على حدة (`analyze(root)`): أولاً موضع `extends`، ثم مرور عام `walk`
يجمع أسماء الكتل ومعرّفات HTML ويفحص الفلاتر والدوال.

### TPL001 — اسم `{% block %}` مكرّر
- **شرط**: في `walk` عند `JinjaBlockStmt`، إذا `blockNames.put(name,...) != null`.
- **اختبار**: «TPL001 duplicate block name» → PASS.

### TPL002 — `{% extends %}` في موضع غير صحيح
- **شرط**: `checkExtendsPosition` — إن ظهر أي محتوى قبل `extends` (يجب أن يكون أول عقدة).

### TPL003 — `{% include %}` لقالب غير موجود
- **شرط**: عند `JinjaIncludeStmt`، إذا الاسم ليس ضمن `knownTemplates`.
- **اختبار**: «TPL003 include of a missing template» → PASS.

### TPL004 — فلتر Jinja مجهول
- **خاطئ**: `{{ x | bogusfilter }}`. **شرط**: عند `JinjaFilterExpr` إذا
  `!KNOWN_FILTERS.contains(filterName)`.
- **اختبار**: «TPL004 unknown Jinja filter» → PASS.

### TPL005 — `id` مكرّر في HTML
- **شرط**: عند `HtmlAttribute` باسم `id`، إذا لم يُضَف للمجموعة (ويتجاهل القيم
  الديناميكية التي تحوي `{{`).

### TPL006 — دالة Jinja عامّة مجهولة
- **شرط**: عند `JinjaCallExpr` باستدعاء `JinjaIdentifierExpr` ليس ضمن `KNOWN_GLOBALS`
  (مثل `url_for`, `range`).

## ج) جدول تلخيصي

| الرمز | المحلّل | الخطأ | عقد AST المستخدمة | الاختبار | النتيجة |
|-------|---------|-------|--------------------|----------|---------|
| PY001 | Python | متغيّر غير معرّف | `IdentifierExpr` | نعم | PASS |
| PY002 | Python | دالة مكرّرة | `FunctionDefStmt`/`DecoratedStmt` | نعم | PASS |
| PY003 | Python | بارامتر مكرّر | `FunctionDefStmt` | نعم | PASS |
| PY004 | Python | return خارج دالة | `ReturnStmt` | نعم | PASS |
| PY005 | Python | break/continue خارج حلقة | `Break/ContinueStmt` | نعم | PASS |
| PY006 | Python | استدعاء دالة غير معرّفة | `CallExpr`/`IdentifierExpr` | نعم | PASS |
| PY007 | Python | route مكرّر | `Decorator`/`CallExpr` | نعم | PASS |
| TPL001 | Template | block مكرّر | `JinjaBlockStmt` | نعم | PASS |
| TPL002 | Template | extends في غير مكانه | `JinjaExtendsStmt` | (منطق مغطّى) | — |
| TPL003 | Template | include مفقود | `JinjaIncludeStmt` | نعم | PASS |
| TPL004 | Template | فلتر مجهول | `JinjaFilterExpr` | نعم | PASS |
| TPL005 | Template | id مكرّر | `HtmlAttribute` | (منطق مغطّى) | — |
| TPL006 | Template | دالة Jinja مجهولة | `JinjaCallExpr` | (منطق مغطّى) | — |
| X001–X005 | Cross | ربط Python↔Jinja | راجع [11](11_CROSS_LANGUAGE_LINKING.md) | نعم (X001/X002/X003/X005) | PASS |

> عدد الأخطاء الدلالية المنفّذة فعلياً **يفوق الحد المطلوب (5)** بوضوح: 7 في Python،
> 6 في القوالب، و5 عبر اللغات.
