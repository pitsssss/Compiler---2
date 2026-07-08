# Generated Flask Product Store

This app was generated automatically by the Jinja2Compiler from the parsed
Python/Flask source. The product data and field schema were taken from the
analysed AST.

## Run

```powershell
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python app.py
```

Then open http://127.0.0.1:5000/

## Test

```powershell
pip install -r requirements.txt
pytest -q
```

## Routes

- `/` - redirects to the product list
- `/products` - list all products
- `/products/<id>` - product details (404 when missing)
- `/products/add` - add form (GET) and create (POST)
- `/products/<id>/delete` - delete (POST only)
