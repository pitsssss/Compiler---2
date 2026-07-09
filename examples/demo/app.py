from flask import Flask, render_template, request, redirect, url_for

app = Flask("app")

products = [
    {"id": 1, "name": "Laptop", "price": 1200.0, "in_stock": True, "description": "Fast development laptop", "category": "Electronics"},
    {"id": 2, "name": "Headphones", "price": 250.0, "in_stock": False, "description": "Noise cancelling headphones", "category": "Audio"},
    {"id": 3, "name": "Test Product From AST", "price": 90.0, "in_stock": True, "description": "Mechanical keyboard", "category": "Accessories"},
]

@app.route("/")
def home():
    return redirect(url_for("list_products"))

@app.route("/products")
def list_products():
    return render_template("products.html", products=products)

@app.route("/products/details")
def product_details():
    return render_template("product_details.html", product=products[0])

@app.route("/products/add", methods=["GET", "POST"])
def add_product():
    errors = []
    if request.method == "POST":
        name = request.form.get("name")
        price_raw = request.form.get("price")
        category = request.form.get("category")
    return render_template("add_product.html", errors=errors)
