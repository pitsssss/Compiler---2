import pytest
from app import app, products

@pytest.fixture
def client():
    app.config["TESTING"] = True
    with app.test_client() as c:
        yield c

def test_list_products_ok(client):
    assert client.get("/products").status_code == 200

def test_home_redirects(client):
    assert client.get("/").status_code in (301, 302)

def test_add_page_ok(client):
    assert client.get("/products/add").status_code == 200

def test_details_ok(client):
    first = products[0]["id"]
    assert client.get(f"/products/{first}").status_code == 200

def test_details_missing_404(client):
    assert client.get("/products/999999").status_code == 404

def test_add_creates_and_redirects(client):
    before = len(products)
    resp = client.post("/products/add", data={"name": "Test", "price": "9.99", "in_stock": "on"})
    assert resp.status_code in (301, 302)
    assert len(products) == before + 1

def test_delete_via_get_not_allowed(client):
    first = products[0]["id"]
    assert client.get(f"/products/{first}/delete").status_code == 405

def test_delete_removes_and_redirects(client):
    new_id = None
    client.post("/products/add", data={"name": "ToDelete", "price": "1"})
    new_id = products[-1]["id"]
    before = len(products)
    resp = client.post(f"/products/{new_id}/delete")
    assert resp.status_code in (301, 302)
    assert len(products) == before - 1
