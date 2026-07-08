# 09 – التشخيص ومعالجة الأخطاء (Diagnostics)

المراجع: `src/diagnostics/` (`Diagnostic`, `DiagnosticKind`, `DiagnosticSeverity`,
`DiagnosticReporter`, `CompilerErrorListener`).

## 1. أنواع المشكلات
- **Lexical Error**: حرف/رمز غير صالح لا يستطيع الـ lexer تحويله إلى token
  (`DiagnosticKind.LEXICAL`, code `LEX001`).
- **Syntax Error**: تسلسل tokens لا يطابق القواعد (`SYNTAX`, `SYN001`).
- **Semantic Error**: نحوٌ صحيح لكن المعنى خاطئ (متغيّر غير معرّف…) (`SEMANTIC`،
  أكواد `PYxxx`/`TPLxxx`).
- **Cross-language Error**: خطأ في الربط بين Python والقوالب (`CROSS`، أكواد `Xxxx`).
- **Warning / Info**: `DiagnosticSeverity` يحوي `ERROR` و`WARNING` (والبنية تدعم
  مستويات أخرى)؛ التحذيرات **لا تمنع** التوليد.

## 2. محتويات `Diagnostic` (الحقول الفعلية)
`src/diagnostics/Diagnostic.java` — كائن غير قابل للتغيير يحوي:
- `kind : DiagnosticKind` — المرحلة.
- `severity : DiagnosticSeverity` — ERROR/WARNING.
- `code : String` — مثل `PY001`, `TPL003`, `X002`.
- `message : String` — الرسالة الواضحة.
- `file : String` — اسم الملف (قد يكون null).
- `line : int` (1-based، `-1` عند الجهل)، `column : int` (0-based، `-1` عند الجهل).
- `suggestion : String` — اقتراح إصلاح مختصر (قد يكون null).

`toString()` يُنتج سطراً مثل:
```text
[SEMANTIC/ERROR PY001] app.py:12:4 Undefined variable 'x' (hint: define 'x' before using it)
```

## 3. جمع أكثر من خطأ في تشغيل واحد
`DiagnosticReporter` يحتفظ بقائمة `diagnostics`. كل المحلّلات تستدعي `reporter.error(...)`
أو `reporter.warning(...)` **دون التوقّف**، فتُجمَع كل المشكلات ثم تُعرض معاً عبر
`printReport()` مجمّعةً حسب النوع (Lexical/Syntax/Semantic/Cross)، وتنتهي بسطر ملخّص
`Errors: N   Warnings: M`.

## 4. منع Stack Trace للمستخدم في الأخطاء المتوقعة
- **أخطاء اللكسر/البارسر**: `CompilerErrorListener` يحلّ محل مستمع ANTLR الافتراضي
  (الذي يطبع على الطرفية)، ويحوّل كل خطأ إلى `Diagnostic` منظّم بدل رمي/طباعة استثناء.
- **الأخطاء المتوقعة**: تُبلَّغ كـ Diagnostics. `CompilerPipeline` يلتقط `IOException`
  فقط ويعيد كود 3 برسالة واضحة، ويتجنّب بناء الـ AST بعد أخطاء نحوية (حارس السطر 123).

## 5. كيف يتصل `CompilerErrorListener` بـ ANTLR؟
في `CompilerPipeline` لكل lexer/parser:
```java
lexer.removeErrorListeners();
lexer.addErrorListener(new CompilerErrorListener(reporter, file));
```
عند خطأ يستدعي ANTLR الدالة `syntaxError(...)`، التي تُحدّد إن كان المصدر lexer أو
parser، وتختار `LEXICAL/LEX001` أو `SYNTAX/SYN001`، ثم `reporter.error(...)`.

## 6. متى يمنع Diagnostic التوليد؟
في `CompilerPipeline.run`: إذا `reporter.hasErrors()` (أي يوجد diagnostic بشدة ERROR)
يُرجَع الكود **2** ولا يُشغَّل المولّد. التحذيرات لا تمنع.

## 7. لماذا توجد أكواد مثل PY001 / TPL001 / X001؟
- **بادئة تدل على الفئة**: `PY` = Python/Flask، `TPL` = Template، `X` = Cross-language،
  `LEX`/`SYN` = lexical/syntax.
- تسهّل الإحالة والتوثيق والاختبار (الاختبارات تتحقق من وجود كود محدّد عبر
  `assertCode`).

## 8. مثال فعلي من `examples/invalid`
ملف `examples/invalid/app.py` يحوي: `return` في المستوى الأعلى، `break` خارج حلقة،
متغيّر غير معرّف، استدعاء دالة غير معرّفة، وroute مكرّر. تشغيله يُنتج تشخيصات مثل:
```text
[SEMANTIC/ERROR PY004] app.py:3:0 'return' used outside of a function (hint: move this return inside a function)
[SEMANTIC/ERROR PY005] app.py:6:... 'break' used outside of a loop
[SEMANTIC/ERROR PY006] app.py:... Call to undefined function 'ghost'
[SEMANTIC/ERROR PY007] app.py:... Duplicate route path '/'
...
Errors: N   Warnings: 0
```
وقالب `examples/invalid/templates/page.html` يُظهر `TPL001` (block مكرّر) و`TPL004`
(فلتر مجهول).

## 9. Exit Code عند وجود أخطاء
يُرجع `CompilerPipeline` الكود **2** (فشل تحليل)، و`Main` يستدعي `System.exit(2)`.
التفاصيل في [03_COMPILER_PIPELINE.md](03_COMPILER_PIPELINE.md).
