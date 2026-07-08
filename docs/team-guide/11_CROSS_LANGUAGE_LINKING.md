# 11 – الربط بين اللغات (Cross-language Linking)

هذا **أهم جزء** في المشروع: إثبات أن شجرة Python وشجرة القوالب **مترابطتان**، لا
مُحلَّلتان بمعزل. المراجع: `src/model/*` و`src/semantic/CrossLanguageSemanticAnalyzer.java`.

## 1. الجسر: `AppModel` و`AppModelBuilder`

`AppModel` (لقطة دلالية للـ Flask) يحوي: `imports`, `globalVars`, `functions`,
`routes:List<RouteInfo>`, `renderCalls:List<RenderCall>`, `urlForRefs:List<UrlForRef>`,
`productRows:List<Map<String,Object>>`, `productFields:List<String>`, واسمَي
`appVariableName`/`productsVariableName`.

`AppModelBuilder.build(program)` يمرّ على الـ AST:
- `handleTopLevel`: يلتقط imports، الإسنادات العامة، الدوال، والـ decorators.
- `handleGlobalAssignment`: يكتشف `app = Flask(...)`، وإذا كان اسم المتغيّر يساوي
  `productsVariableName` («products») وقيمته `ListLiteralExpr` يستخرج المنتجات.
- `tryRoute`: يتعرّف على `@app.route("/path", methods=[...])` ويبني `RouteInfo`.
- `collectCalls`: يبحث في كل الشجرة عن `render_template(...)` (يبني `RenderCall`
  بأسماء متغيّرات السياق = مفاتيح keyword args) و`url_for(...)` (يبني `UrlForRef`).

**القيم الحرفية** (`RouteInfo`, `RenderCall`, `UrlForRef`) كائنات بسيطة تحمل البيانات
+ رقم السطر.

## 2. استخراج schema المنتجات (`extractProducts`)

لكل عنصر في قائمة `products`:
- إذا كان **`DictLiteralExpr`** (كما في الـ demo): المفاتيح تصبح أسماء الحقول.
- إذا كان **`ListLiteralExpr`** (list-of-lists): تُستخدم الحقول الموضعية القياسية
  `[id, name, price, in_stock, description, category]`.

النتيجة: `productFields` = مجموعة الحقول، و`productRows` = القيم المحوّلة إلى
String/Integer/Double/Boolean/null (`literalValue`).

## 3. المسار الكامل للبيانات (مثال products الحقيقي)

```mermaid
flowchart LR
    A["Python: products = [{'id':1,'name':'Laptop',...}]"] --> B[ListLiteralExpr / DictLiteralExpr]
    B --> C[AppModelBuilder.extractProducts]
    C --> D["schema: id,name,price,in_stock,description,category<br/>rows: 3 records"]
    D --> E["render_template('products.html', products=products)"]
    E --> F[RenderCall ctx=['products']]
    G["template: {{ product.name }}"] --> H[JinjaAttrExpr target=product attr=name]
    F --> I[CrossLanguageSemanticAnalyzer]
    H --> I
    D --> I
    I --> J[FlaskAppGenerator]
    J --> K["generated-app/app.py + templates"]
```

## 4. ماذا يتحقّق `CrossLanguageSemanticAnalyzer`؟

الدالة `analyze()` تستدعي ثلاثة فحوص:

### X001 — `render_template` لقالب غير موجود
`checkRenderTargets`: لكل `RenderCall`، إذا اسم القالب ليس ضمن `knownTemplateNames`
(مفاتيح خريطة القوالب المُحلَّلة) → X001. **اختبار PASS**.

### X002 — `url_for` لـ endpoint غير معرّف
`checkUrlForEndpoints`: لكل `UrlForRef`، إذا `!model.hasEndpoint(endpoint)` (أي لا توجد
دالة بهذا الاسم) → X002. **اختبار PASS**.

### X003 — القالب يستخدم متغيّر سياق لم يُمرَّر
`checkTemplateContexts`: يجمع من شجرة القالب المتغيّرات المستخدمة في الجذر
(`usedRootVars` عبر `JinjaExpr.getVariables()`)، ومتغيّرات الحلقات (`loopVars`)، والسمات
لكل متغيّر (`attrsByVar`). أي متغيّر مستخدَم وليس: مُمرَّراً (`provided` = أسماء سياق
`RenderCall`)، ولا محلياً لحلقة، ولا ضمن `IMPLICIT_NAMES` (مثل `url_for`, `loop`) → X003.
**اختبار PASS**.

### X004 — قالب التفاصيل يحتاج `product` والدالة لا تمرّره
إذا استُخدمت سمات على `product` ولم يُمرَّر `product` ولا كان متغيّر حلقة → X004.

### X005 — القالب يصل إلى مفتاح منتج ليس في الـ schema
لكل متغيّر يُعتبر «عنصر منتج» (`product`، أو متغيّر حلقة يكرّر `products` عبر
`iteratesProducts`)، تُفحَص سماته: أي سمة ليست ضمن `productFields` → X005. مثال: لو
استخدم القالب `product.color` وليس `color` في البيانات. **اختبار PASS**.

## 5. كيف يُثبت هذا الربط بين الشجرتين؟

- المتغيّرات المستخدمة في **Template AST** (`JinjaAttrExpr`, `JinjaIdentifierExpr`,
  `JinjaForStmt`) تُقارَن ببيانات مستخرجة من **Python AST** (عبر `AppModel`:
  `renderCalls`, `functions`, `productFields`).
- فمثلاً `{{ product.name }}` في القالب يُتحقَّق مقابل `render_template("...",
  product=...)` وschema المنتج في Python — وهذا هو **نقل البيانات من شجرة إلى شجرة**
  المطلوب في المشروع.

## 6. متى يُشغَّل؟
في `CompilerPipeline.run` (السطر 79) فقط عندما يوجد ملف flask و`model != null`
وهناك قوالب مُحلَّلة (`!templateAsts.isEmpty()`).
