# 04 – ANTLR: الـ Lexer والـ Parser

المراجع الفعلية: `src/antlr/FlaskLexer.g4`, `FlaskParser.g4`, `TemplateLexer.g4`,
`TemplateParser.g4`، وسكربت التوليد `scripts/generate-antlr.ps1`.

## 0. Lexer Grammar مقابل Parser Grammar

- **Lexer grammar** (`lexer grammar ...`): يحوّل تيار الأحرف إلى **Tokens** (قواعده
  أسماؤها بأحرف كبيرة مثل `IDENTIFIER`, `INT`). لا يفهم البنية، فقط «الكلمات».
- **Parser grammar** (`parser grammar ...`): يستهلك الـ Tokens ويبني **Parse Tree**
  وفق قواعد (قواعده أسماؤها بأحرف صغيرة مثل `prog`, `expr`). يرتبط بالـ lexer عبر
  `options { tokenVocab = FlaskLexer; }`.

## 1. `FlaskLexer.g4` — لكسر Python subset

- **الهدف**: تحويل كود Flask إلى Tokens مع **معالجة الإزاحة (indentation)** بأسلوب
  Python (INDENT/DEDENT).
- **أهم Tokens**: كلمات مفتاحية (`DEF`, `IF`, `FOR`, `RETURN`, `IMPORT`, `FROM`,
  `LAMBDA`, `TRUE`, `FALSE`, `NONE`, …)، `IDENTIFIER`، أرقام (`INT`, `FLOAT`,
  `SCIENTIFIC`)، نصوص (`SINGLE_STRING`, `DOUBLE_STRING`, `TRIPLE_STRING`)، عوامل
  (`PLUS`, `POW`, `EQ`, …)، ومحدّدات (`LPAREN`, `LBRACK`, `LBRACE`, `AT`, `ASSIGN`).
- **الإزاحة**: كتلة `@lexer::members` تدير مكدّس `indents` وقائمة `tokens`. قاعدة
  `NEWLINE` (السطور 84–113) تحسب المسافات وتُصدر `INDENT`/`DEDENT`. وعند EOF تُصدر
  كل الـ DEDENT المتبقية (السطور 27–50).
- **line joining داخل الأقواس**: يعدّاد `opened` يزيد مع `(`/`[`/`{` وينقص مع
  إغلاقها. في `NEWLINE` إن كان `opened > 0` فإنه `skip()` للسطر الجديد (السطر 93)،
  فتُقرأ القوائم/القواميس متعددة الأسطر بشكل صحيح.
- **comments/whitespace**: `COMMENT` (`# ...`) و`WS` كلاهما `-> skip`.
- **strings**: مفردة/مزدوجة مع `ESC` (هروب)، وثلاثية `'''...'''` / `"""..."""`.
- **numbers**: `INT` (0 أو يبدأ بغير صفر)، `FLOAT` (فيه نقطة)، `SCIENTIFIC` (فيه `e`).

**مثال إدخال صحيح للأرقام والقوائم متعددة الأسطر:**

```python
products = [
    {"id": 1, "name": "Laptop"},
]
```

**مثال إدخال خاطئ (خطأ نحوي لاحقاً):** `def (:` — سيُنتج رسالة syntax error.

## 2. `FlaskParser.g4` — نحو Python subset

- **القاعدة الجذر**: `prog : stmt* EOF ;` (السطر 15).
- **البنية**: `stmt` تتفرّع إلى `simple_stmt NEWLINE` أو `compound_stmt` أو سطر فارغ.
- **الإسناد**: `assignment : assign_target (ASSIGN assign_target)* ASSIGN expr` يدعم
  الإسناد المتسلسل. و`assign_target` يدعم `a.b` و`a[i]`.
- **الجمل المركّبة**: `if_stmt`, `while_stmt`, `for_stmt`, `func_def`, `class_def`,
  `decorated` (لدعم `@app.route`).
- **التعبيرات (سُلّم الأولويات)**: `lambda_expr → or_expr → and_expr → not_expr →
  comparison → arith_expr → term → power → unary → primary → atom`.
- **الاستدعاءات والسمات والفهرسة**: عبر `primary : atom trailer*` و`trailer`
  (`CallTrailer`, `AttrTrailer`, `IndexTrailer`).
- **keyword arguments**: `argument : IDENTIFIER ASSIGN expr # KeywordArg | expr #
  PositionalArg` (السطور 234–237) — هذا ما يلتقط `methods=[...]` و`products=products`.
- **الحرفيات**: `literal` يشمل أرقام/نصوص/بوليان/None/**dict**/**set**/**list**.
  لاحظ أن `dict_literal` مُدرَج **قبل** `set_literal` كي يميّز `{}` كقاموس فارغ ووجود
  `:` كقاموس (السطور 253–281).
- **decorators/routes**: `decorator : AT primary NEWLINE` و`decorated : decorator+
  (func_def | class_def)`.

## 3. `TemplateLexer.g4` — لكسر Jinja/HTML/CSS بأنماط (modes)

يعتمد على **modes** لأن نفس الرمز قد يعني أشياء مختلفة داخل وسم HTML أو تعبير Jinja
أو CSS:

- **DEFAULT_MODE**: نص HTML، تعليقات، وبدايات Jinja (`{{`, `{%`, `{#`)، و`<`
  (يدخل `TAG_MODE`).
- **TAG_MODE**: داخل `< ... >` — أسماء الوسوم (`HTML_TAG_NAME`, `HTML_VOID_TAG`)،
  السمات (`HTML_ATTR_NAME`, `HTML_STRING`)، و`>` (يعود أو يدخل CSS إذا كان `<style>`).
- **JINJA_EXPR_MODE**: بين `{{` و`}}` — `JINJA_ID`, `JINJA_OP`, `JINJA_EXPR_PIPE`
  (للفلاتر)، نصوص وأرقام.
- **JINJA_STMT_MODE**: بين `{%` و`%}` — كلمات `for/if/block/extends/include/…`.
- **CSS_MODE / CSS_BLOCK_MODE**: selectors وproperties وvalues وfunctions، مع دعم
  Jinja داخل CSS.

**أمثلة صحيحة**: `{{ product.name }}`, `{% for p in products %}`,
`{% extends "base.html" %}`, `<a href="{{ url_for('x') }}">`.

## 4. لماذا ترتيب قواعد الـ Lexer مهم؟

ANTLR يختار قاعدة الـ lexer بـ **أطول تطابق** (maximal munch)، وعند التعادل يختار
**القاعدة المُعرّفة أولاً**. لذلك تُوضع الكلمات المفتاحية (`TRUE`, `DEF`, …) **قبل**
`IDENTIFIER` وإلا لالتُقطت كمعرّفات. وفي `FlaskParser` وُضع `dict_literal` قبل
`set_literal` لحسم الالتباس بين `{}` و`{a}`.

## 5. أوامر التوليد ومعانيها (`generate-antlr.ps1`)

```powershell
java -jar <antlr.jar> -Dlanguage=Java -package antlr FlaskLexer.g4
java -jar <antlr.jar> -Dlanguage=Java -package antlr -visitor FlaskParser.g4
```

- `-Dlanguage=Java`: يولّد كود Java.
- `-package antlr`: يضع الملفات المولّدة في الحزمة `antlr` (لذا `import antlr.*`).
- `-visitor`: يولّد واجهات وأصناف الـ Visitor (نحتاجها لبناء الـ AST يدوياً).
- `-lib` (غير مستخدم هنا لأننا نولّد داخل نفس المجلد `src/antlr`، فيجد الـ parser ملف
  `.tokens` الخاص بالـ lexer). لذلك **يُولَّد الـ lexer أولاً** ثم الـ parser.

## 6. الملفات المولّدة ووظيفتها

| الملف | مصدره | الوظيفة |
|-------|-------|---------|
| `FlaskLexer.java` | `FlaskLexer.g4` | الـ lexer |
| `FlaskParser.java` | `FlaskParser.g4` | الـ parser + أصناف `*Context` |
| `FlaskParserVisitor.java` | `-visitor` | واجهة الزائر |
| `FlaskParserBaseVisitor.java` | `-visitor` | زائر أساسي (نرثه في `FlaskVisitor`) |
| `FlaskParserListener.java` / `...BaseListener.java` | افتراضي | نمط Listener (غير مستخدم عندنا) |
| نفس المجموعة لـ `Template*` | القوالب | مثيلها لجهة القوالب |

المجموع 12 ملفاً مولّداً.

## 7. لا تُعدّل الملفات المولّدة يدوياً

أي تعديل في السلوك يجب أن يتم في `.g4` ثم **إعادة التوليد** عبر
`scripts/generate-antlr.ps1`. الملفات المولّدة مذكورة في `.gitignore` وتُعاد كتابتها
عند كل توليد.
