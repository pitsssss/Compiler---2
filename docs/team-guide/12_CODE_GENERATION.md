# 12 – توليد الكود (Code Generation)

المرجع: `src/generator/FlaskAppGenerator.java`.

## 1. المدخلات
المُنشئ `FlaskAppGenerator(AppModel model)` يأخذ **`AppModel`** فقط (لا الـ AST مباشرة).
منه يحسب:
- `fields = resolveFields(model)`: حقول المنتج المستخرجة من AST + ضمان وجود الحقول
  الأساسية (`id, name, price, in_stock, description, category`).
- `rows = resolveRows(model, fields)`: سجلّات المنتجات المستخرجة، وكل حقل ناقص يُملأ
  بقيمة افتراضية (`defaultFor`).

## 2. هل يأخذ AST أم AppModel؟
**AppModel** (وهو مبنيّ من الـ AST في `AppModelBuilder`). أي أن التوليد **مدفوع
بالبيانات المستخرجة من الشجرة**، لا من النص.

## 3. تحديد مجلد الإخراج ومنع الكتابة في src
- `generate(Path outputDir)` يكتب داخل `outputDir` (الافتراضي `generated-app`، من
  `Config.outputDir`). ينشئ `templates/`, `static/`, `tests/`.
- لا يكتب داخل `src/` إطلاقاً؛ المسار يأتي من إعداد المستخدم وافتراضه `generated-app`.

## 4. الملفات المولّدة (10)
| الملف | الدالة المنتجة | المحتوى |
|-------|-----------------|---------|
| `app.py` | `generateAppPy` | تطبيق Flask CRUD كامل + بيانات المنتجات |
| `requirements.txt` | `generateRequirements` | `Flask>=3.0`, `pytest>=8.0` |
| `README.md` | `generateReadme` | تعليمات التشغيل والاختبار والمسارات |
| `templates/base.html` | `generateBaseHtml` | القالب الأساس (nav + block content) |
| `templates/products.html` | `generateProductsHtml` | قائمة المنتجات (for/if) |
| `templates/product_details.html` | `generateDetailsHtml` | تفاصيل المنتج (جدول من الحقول) |
| `templates/add_product.html` | `generateAddHtml` | نموذج الإضافة |
| `templates/not_found.html` | `generateNotFoundHtml` | صفحة 404 |
| `static/style.css` | `generateCss` | التنسيق |
| `tests/test_app.py` | `generateTests` | اختبارات pytest عبر test client |

## 5. من أين تأتي المنتجات الثلاثة؟ (إثبات أنها ليست Hardcoded)

في `generateAppPy` (السطور 129–139) يمرّ المولّد على `rows` (المستخرجة من `AppModel`)
ويكتب كل سجلّ. **الدليل القاطع**: أُعيدت تسمية منتج في `examples/demo/app.py` إلى
`"Test Product From AST"`، وبعد التوليد ظهر **حرفياً** في `generated-app/app.py`
(السطر 12):

```python
{"id": 3, "name": "Test Product From AST", "price": 90.0, "in_stock": True, ...},
```

كذلك **جدول التفاصيل** يُبنى من `fields` (السطور 255–268): إضافة حقل جديد للـ schema
تضيف صفاً جديداً في `product_details.html` — سلوك مدفوع بالبيانات.

> صدق وأمانة: **بنية** المسارات والقوالب (home/list/details/add/delete) هي **سقالة
> ثابتة** يكتبها المولّد؛ أما **بيانات المنتجات وحقولها** فمستخرجة من الـ AST. المولّد
> يستخدم البيانات لا النص، لكنه لا يعيد إنتاج منطق Python المدخل حرفياً.

## 6. كيف يولّد كل جزء؟
- **routes**: نصوص Python في `generateAppPy` لـ `/`, `/products`,
  `/products/<int:product_id>`, `/products/add` (GET/POST), `/products/<id>/delete`
  (POST).
- **list view**: `products.html` يمرّ على المنتجات ويعرض بطاقة لكل منتج.
- **details view**: `product_details.html` يعرض جدولاً من الحقول ويستخدم `product.id`
  في روابط الحذف/العودة.
- **add form**: `add_product.html` نموذج POST مع عرض `errors`.
- **delete action**: زر داخل `<form method="post">` في صفحة التفاصيل.
- **404 page**: `not_found.html` يُعرض مع كود 404 من `product_details`.
- **navigation**: `base.html` يحوي شريط تنقّل بروابط `url_for`.
- **CSS**: `style.css` تنسيق كامل.

## 7. لماذا الحذف عبر POST لا GET؟
لأن الحذف **عملية تغيّر الحالة** (mutating). استخدام GET للحذف خطر (يمكن تنفيذه
بالخطأ عبر رابط/زحف). لذلك المسار `methods=["POST"]`، ومحاولة GET تُعيد **405 Method
Not Allowed** (يتحقّق منها اختبار `test_delete_via_get_not_allowed`).

## 8. التحقق من الحقول (validation)
في `add_product`: يتحقّق أن الاسم غير فارغ، ويحوّل السعر إلى `float` مع التقاط
`ValueError`، ويجمع الأخطاء في `errors` ويعرضها في القالب؛ لا يُضاف المنتج إلا إذا لم
تكن هناك أخطاء.

## 9. مشكلة المنتج غير الموجود
`product_details` يستدعي `find_product(product_id)`، وإذا كان `None` يُرجع
`render_template("not_found.html", ...), 404`.

## 10. الترميز (Encoding)
كل ملف يُكتب **UTF-8** عبر `write(...)` الذي يستخدم
`Files.writeString(path, content, StandardCharsets.UTF_8)`.

## 11. متى لا يعمل المولّد؟
- إذا وُجدت أخطاء تحليل (`hasErrors()`)، لا يُستدعى أصلاً (الـ pipeline يُرجع 2).
- إذا لم تُمرَّر `--generate` أو مُرِّرت `--analyze-only`.
- **بذرة احتياطية**: إذا لم توجد بيانات منتجات (`productRows` فارغة) يستخدم
  `defaultSeed()` (Laptop/Headphones) حتى يبقى التطبيق الناتج قابلاً للتشغيل.

## 12. القيود الحالية
- بنية المسارات والقوالب ثابتة (سقالة CRUD للمنتجات) ولا تُشتق من عدد/أسماء الدوال في
  المدخل.
- تخزين البيانات **In-memory** فقط (قائمة Python)، لا قاعدة بيانات؛ التغييرات تضيع عند
  إعادة تشغيل السيرفر.
- يعتمد على مفهوم «المنتجات»؛ نماذج بيانات أخرى غير مدعومة في التوليد.
