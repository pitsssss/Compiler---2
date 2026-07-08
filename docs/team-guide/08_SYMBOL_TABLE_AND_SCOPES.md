# 08 – جدول الرموز والنطاقات (Symbol Table & Scopes)

المراجع: `src/SymbolTable/FlaskSymbolTable.java`, `SymbolTableVisitor.java`.

## 1. ما هو Symbol؟
**Symbol** هو أي اسم ذو معنى في البرنامج: متغيّر، دالة، بارامتر، وحدة مستوردة… إلخ.
في المشروع يُمثَّل بالصنف الداخلي `FlaskSymbolTable.Symbol` بحقول: `name`, `type`
(`SymbolType`), `dataType`, `lineNumber`, `parameters`, `context`, `scopeName`.

## 2. ما هو Symbol Table؟
جدول يخزّن كل الرموز ويسمح بالبحث عنها (`lookup`) والتحقق من التعريفات المكرّرة. هنا
هو `FlaskSymbolTable` الذي يدير **مكدّس نطاقات** (`Deque<Scope> scopeStack`) وقوائم
مساعدة (`allSymbols`, `symbolsByType`, `builtins`, `attributeUsages`, `builtinUsages`).

## 3. لماذا يحتاج Compiler إلى Symbol Table؟
- للتحقق أن الأسماء مُعرّفة قبل الاستخدام.
- لكشف التعريفات المكرّرة.
- لتخزين معلومات (رقم السطر، النوع، البارامترات) تُستخدم في التحليل والعرض.

## 4. ما هو Scope؟
**النطاق** هو المنطقة التي يكون فيها الاسم مرئياً. يُمثَّل بـ `FlaskSymbolTable.Scope`
مع `ScopeType`: `GLOBAL, FUNCTION, LOOP, IF, BLOCK, CLASS, MODULE`.

## 5. الفرق بين النطاقات
- **Global**: المستوى الأعلى (imports، متغيّرات عامة مثل `products`، الدوال).
- **Function**: داخل `def` (البارامترات + المتغيّرات المحلية).
- **Class**: داخل `class`.
- **Loop/If (Block)**: داخل `for/while/if`.
- **Parent/Child**: كل نطاق يُضاف كابن للنطاق الحالي (`addChildScope`)، والبحث يصعد من
  الابن إلى الأب.

## 6. كيف يُبنى الجدول؟ (`SymbolTableVisitor`)
- `visitProgram` يمرّ على جُمل البرنامج.
- `visitFunction` (سطر 119): يضيف الدالة، ثم `enterScope(name, FUNCTION)`، يضيف
  البارامترات (`addParameter`)، يزور الجسم، ثم `exitScope()`.
- `visitAssignment`: إن كان الهدف `IdentifierExpr` أضاف متغيّراً عاماً أو محلياً حسب
  النطاق الحالي؛ وإن كان `AttributeExpr` سجّله كـ **استخدام سمة** (`addAttribute`).
- `visitFor`/`visitWhile`/`visitIf`: تدخل نطاقاً مناسباً (LOOP/IF) وتخرج منه.
- `visitDecorated`: يعالج `app.route` عبر `processDecoratorAttribute` (تسجيل سمة).

## 7. أنواع الرموز المخزّنة
- **Variables** (global/local/loop)، **Functions**، **Parameters**، **Imports/Modules**،
  **Built-ins** (للبحث فقط)، **Attributes** (كاستخدامات)، ولدى `SymbolType` أنواع
  إضافية (FILTER/BLOCK/INCLUDE/EXTENDS/CLASS) بقيت من تصميم القوالب.

## 8. العمليات: define / resolve / lookup / duplicate
- **define**: عبر `addSymbol` — يُنظّف الاسم (`cleanSymbolName`) ويضيفه للنطاق الحالي.
- **lookup/resolve**: `lookup(name)` يبحث في كل نطاقات المكدّس ثم في الـ builtins.
- **duplicate detection**: داخل `addSymbol`، إن كان الاسم موجوداً **في نفس النطاق**
  (`current.contains(cleanedName)`) يُعيد `false` (لا يُضاف مرتين). لا يطبع خطأً؛ الأخطاء
  المرئية للمستخدم مسؤولية `DiagnosticReporter` عبر المحلّل الدلالي.
- **shadowing**: اسم في نطاق داخلي يحجب اسماً بنفس القيمة في نطاق خارجي أثناء البحث.

## 9. المشكلة القديمة `route/args/get/append already declared` وحلّها

كانت السمات (`app.route`) والـ built-ins (`request.args.get`, `list.append`) تُسجَّل
كأنها **تعريفات** جديدة في كل مرة، فتظهر تحذيرات «already declared» كاذبة.

**الحل الحالي (مطبّق فعلياً):**
- **Built-ins لا تُضاف كتعريفات**: `addBuiltin` (سطر 182) يخزّنها في خريطة `builtins`
  **للبحث فقط**، لا في `allSymbols` أو النطاق.
- **الاستخدامات منفصلة عن التعريفات**:
  - `addAttribute` (سطر 316) يسجّل الوصول في قائمة `attributeUsages` (لا في نطاق).
  - `addBuiltinFunction` (سطر 341) يسجّل الاستدعاء في `builtinUsages` مرة واحدة لكل اسم.
- **التمييز الدقيق**:
  - **Declaration**: `products = []` → `addGlobalVariable` (تعريف حقيقي).
  - **Usage**: `products.append(...)` → `append` استخدام built-in، و`products` وصول
    قاعدة، وليست تعريفات.
  - **Attribute access**: `app.route`, `request.args`, `request.args.get` → تُسجَّل في
    `attributeUsages` فقط.
- **التعريفات المكرّرة الحقيقية ما زالت تُكتشف**: `addSymbol` يمنع التكرار في نفس
  النطاق، والمحلّل الدلالي يُصدر `PY002`/`PY003`.

مؤكَّد باختبارات (انظر [14_TESTS_FILE_BY_FILE.md](14_TESTS_FILE_BY_FILE.md)):
- «repeated attribute access (app.route) is not a duplicate declaration» → PASS.
- «true duplicate declaration in same scope is rejected» → PASS.
- «built-ins resolve via lookup but are not user declarations» → PASS.

## 10. مثال جدول رموز من `examples/demo/app.py`

عند `--print-symbol-table` نحصل تقريباً على:

```text
==================== FLASK SYMBOL TABLE ====================
Imports:
  - Flask [MODULE] [line 1]
  - render_template [MODULE] [line 1]
  - request [MODULE] [line 1]
  - redirect [MODULE] [line 1]
  - url_for [MODULE] [line 1]

Global Variables:
  - app [line 3]
  - products [line 5]

Functions:
  - home() [line 12]
  - list_products() [line 16]
  - product_details() [line 20]
  - add_product() [line 24]

Attribute Accesses (uses, not declarations):
  - app.route [line 11]  ...
```

> الأسماء والأسطر مأخوذة من `app.py` الفعلي؛ التفاصيل الدقيقة قد تختلف قليلاً حسب
> ترتيب الزيارة، لكن **لا تظهر أي تحذيرات «already declared»**.

## 11. لماذا Built-ins ليست User Declarations؟
لأنها ليست جزءاً مما كتبه المستخدم؛ هي معروفة مسبقاً للّغة/الإطار. تسجيلها كتعريفات
سيُلوّث الجدول ويُنتج تحذيرات كاذبة. لذا تُستخدم للبحث (resolve) فقط.

## 12. ملفات قديمة/غير مستخدمة في `src/SymbolTable/`
- `Scope.java`, `Symbol.java`: تعريفات قديمة منفصلة (النسخ المستخدمة فعلياً أصناف
  **داخلية** في `FlaskSymbolTable`).
- `TemplateSymbolTable.java`, `JinjaASTVisitor.java`: خاصة بجدول رموز للقوالب من تصميم
  سابق، **غير مستدعاة** في `CompilerPipeline`.
- **ملاحظة**: `SymbolTableVisitor.java` يستورد `java.lang.reflect.Field` دون استخدام
  فعلي (import زائد لا يؤثّر على البناء). سُجّل ضمن الملاحظات، ولم يُعدّل حفاظاً على
  «التوثيق لا التعديل».
