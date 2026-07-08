# 18 – دليل المشكلات الشائعة (Troubleshooting)

لكل مشكلة: الرسالة، السبب، الحل، وأمر التحقق.

| المشكلة | الرسالة النموذجية | السبب | الحل | أمر التحقق |
|---------|-------------------|-------|------|------------|
| java غير معروف | `'java' is not recognized` | JDK غير مثبّت/غير على PATH | ثبّت JDK 17+ وأضفه لـ PATH | `java -version` |
| javac غير معروف | `'javac' is not recognized` | JRE فقط بلا JDK | ثبّت JDK كاملاً | `javac -version` |
| ANTLR jar مفقود | `ANTLR jar not found at lib\...` | الملف غير موجود في `lib/` | ضع `antlr-4.13.2-complete.jar` في `lib/` | `Test-Path lib\antlr-4.13.2-complete.jar` |
| package antlr does not exist | خطأ ترجمة | لم تُولَّد ملفات ANTLR | شغّل `generate-antlr.ps1` | `Test-Path src\antlr\FlaskParser.java` |
| ملفات Lexer/Parser غير مولّدة | `cannot find symbol FlaskParser` | نفس السبب | `.\scripts\generate-antlr.ps1` | نفس الأمر |
| PowerShell يمنع السكربتات | `running scripts is disabled` | ExecutionPolicy مقيّد | `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass` | `Get-ExecutionPolicy` |
| فشل Build | `Compilation failed` | خطأ Java/إعداد | اقرأ رسالة javac؛ تأكّد JDK 17+ | `.\scripts\build.ps1` |
| Classpath غير صحيح | `Could not find or load main class Main` | classpath ناقص | استخدم `run.ps1` أو `-cp "out;lib\...jar"` | افحص وجود `out\Main.class` |
| Python غير معروف | `'python' is not recognized` | Python غير مثبّت | ثبّت Python 3.10+ | `python --version` |
| pip غير موجود | `No module named pip` | pip ناقص | `python -m ensurepip` | `python -m pip --version` |
| pytest غير مثبّت | `No module named pytest` | لم تُثبَّت المتطلبات | `pip install -r requirements.txt` | داخل venv |
| Port 5000 مستخدم | `Address already in use` | عملية Flask سابقة | أوقفها (Ctrl+C) أو غيّر المنفذ `app.run(port=5001)` | `netstat -ano | findstr :5000` |
| generated-app غير موجود | `generated-app/ not found` | لم تُشغَّل `--generate` | شغّل run مع `--generate` | `Test-Path generated-app` |
| template مفقود | تشخيص `X001` | اسم قالب خاطئ/غير موجود | صحّح الاسم أو أنشئ القالب | راجع رسالة التشخيص |
| Graphviz dot غير مثبّت | `'dot' is not recognized` | Graphviz غير مثبّت | ثبّت Graphviz (اختياري) | `dot -V` |
| ast.dot لا يفتح | صورة فارغة | لم يُحوَّل بعد | `dot -Tpng ast.dot -o ast.png` | افتح `ast.png` |
| أخطاء Encoding | رموز غريبة | ملف ليس UTF-8 | المولّد يكتب UTF-8؛ احفظ مدخلاتك UTF-8 | — |
| علامة `?` في الطرفية | أحرف غير لاتينية تظهر `?` | ترميز الطرفية | `chcp 65001` (UTF-8) في PowerShell | `chcp` |
| مسار يحتوي مسافات | فشل javac على المسار | اقتباس ناقص | `build.ps1` يقتبس المسارات تلقائياً؛ استخدمه | — |
| Java تنجح وFlask تفشل | pytest fail | Python/Flask ناقص أو منفذ مشغول | ثبّت المتطلبات؛ راجع منفذ 5000 | `.\scripts\test-flask-app.ps1` |
| Exit Code غير صفري | خروج 2/3/64/66 | 2=أخطاء تحليل، 3=IO، 64=CLI، 66=ملف مفقود | راجع [03](03_COMPILER_PIPELINE.md) | `$LASTEXITCODE` |
