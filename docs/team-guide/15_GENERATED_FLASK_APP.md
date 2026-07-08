# 15 – التطبيق الناتج (Generated Flask App)

المرجع: مجلد `generated-app/` (مولّد بالكامل من `FlaskAppGenerator`).

## 1. `app.py`
تطبيق Flask CRUD للمنتجات. أهم عناصره:
- `app = Flask(__name__)`.
- `products`: قائمة قواميس **مستخرجة من الـ AST** (تحوي مثلاً «Test Product From AST»).
- دوال مساعدة: `_next_id()` (أعلى id + 1)، `find_product(id)`.
- المسارات (routes) أدناه.

## 2. بيانات المنتجات
تخزين **In-memory** فقط (قائمة Python في الذاكرة). لا ملف ولا قاعدة بيانات. لذلك أي
إضافة/حذف تُفقَد عند إعادة تشغيل السيرفر.

## 3. جدول المسارات (Routes)
| Method | Route | Function | Input | Output | حالات الخطأ |
|--------|-------|----------|-------|--------|--------------|
| GET | `/` | `home` | — | redirect إلى `list_products` | — |
| GET | `/products` | `list_products` | — | `products.html` بكل المنتجات | — |
| GET | `/products/<int:product_id>` | `product_details` | id | `product_details.html` | 404 إن لم يوجد |
| GET | `/products/add` | `add_product` | — | نموذج الإضافة | — |
| POST | `/products/add` | `add_product` | form | إضافة + redirect | تُعرض `errors` عند فشل التحقق |
| POST | `/products/<int:product_id>/delete` | `delete_product` | id | حذف + redirect | GET → 405 |

## 4. القوالب
- `base.html`: هيكل + شريط تنقّل + `{% block content %}`.
- `products.html`: `{% for product in products %}` بطاقات + رابط تفاصيل.
- `product_details.html`: جدول من الحقول + زر حذف (POST) + عودة.
- `add_product.html`: نموذج POST + عرض `errors`.
- `not_found.html`: صفحة 404.

## 5. static CSS
`static/style.css`: تنسيق حديث (navbar، grid، cards، أزرار، جداول، نماذج).

## 6. النماذج (Forms) وإعادة التوجيه (Redirects) وحالات HTTP
- الإضافة والحذف يستخدمان **POST** ثم **redirect** (نمط Post/Redirect/Get لتفادي
  إعادة الإرسال). أكواد: 200 (عرض)، 302 (redirect)، 404 (غير موجود)، 405 (طريقة غير
  مسموحة على الحذف عبر GET).

## 7. رحلة المستخدم
1. يفتح `/` → يُحوَّل إلى `/products`.
2. يرى قائمة المنتجات (بطاقات).
3. ينقر «View Details» → `/products/<id>` (تفاصيل المنتج).
4. من الشريط ينقر «Add Product» → `/products/add`، يملأ النموذج ويحفظ (POST) →
   يعود للقائمة والمنتج مضاف.
5. من صفحة التفاصيل ينقر «Delete» (POST) → يعود للقائمة والمنتج محذوف.
6. يفتح id غير موجود (`/products/999999`) → صفحة 404.

## 8. test client
`tests/test_app.py` يستخدم `app.test_client()` لمحاكاة الطلبات دون سيرفر. التفاصيل في
[14_TESTS_FILE_BY_FILE.md](14_TESTS_FILE_BY_FILE.md).
