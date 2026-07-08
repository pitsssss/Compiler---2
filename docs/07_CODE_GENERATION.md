# 07 – Code Generation

## What the generator produces

`generator.FlaskAppGenerator` reads the `AppModel` and writes a complete,
runnable Flask project into `generated-app/` (configurable via `--output`):

```
generated-app/
  app.py               # routes, product data, CRUD logic
  requirements.txt     # Flask + pytest
  README.md            # how to run the generated app
  templates/
    base.html          # layout + nav bar (extended by the others)
    products.html      # product list (handles empty list)
    product_details.html
    add_product.html   # form + field validation
    not_found.html     # 404 page for missing products
  static/
    style.css          # readable, organised styling
  tests/
    test_app.py        # pytest using the Flask test client
```

The output is **10 files**, verified by running the compiler.

## How Python data flows into the templates

The generator does not hard-code an example. It uses data extracted from the
parsed Python AST via `AppModelBuilder`:

1. `AppModelBuilder` finds the products assignment (list of dicts or list of
   lists), extracts the **field schema** and each **row**, plus the routes and
   `render_template`/`url_for` calls.
2. `FlaskAppGenerator` writes those exact rows and fields into the generated
   `app.py` `products = [...]` list, and builds the templates around the same
   field names (`product.name`, `product.price`, …).

So the data genuinely travels from the Python tree → `AppModel` → generated
`app.py` and templates. Example (from the demo): the three demo products appear
verbatim in the generated `app.py`.

## How the pages work together

- `GET /` → redirects to the product list (`url_for("list_products")`).
- `GET /products` → `products.html`, iterating the product list; shows a friendly
  message when there are none.
- `GET /products/<int:id>` → `product_details.html`, or `not_found.html` with
  HTTP **404** when the id doesn't exist.
- `GET/POST /products/add` → `add_product.html`; on POST it validates the name
  and price, appends a new product, and **redirects** to the list.
- `POST /products/<int:id>/delete` → removes the product and redirects. Delete is
  **POST-only**, so `GET` on that URL returns **405**.

Navigation is provided by a shared nav bar in `base.html`; every page
`{% extends "base.html" %}`.

## Generation design (not one giant string)

Generation is split into focused methods — `generateAppPy`, `generateBaseHtml`,
`generateProductsHtml`, `generateDetailsHtml`, `generateAddHtml`,
`generateNotFoundHtml`, `generateCss`, `generateTests`, `generateRequirements`,
`generateReadme` — plus helpers to convert Java values to valid Python literals
(`pyValue`, `escapePy`). All files are written as **UTF-8**. Jinja autoescaping
is relied upon for user-facing values.
