"""The app serves the order routes under /v1."""

import pytest
from fastapi.testclient import TestClient

from orders_api.p4_api.main import create_app


@pytest.mark.integration
@pytest.mark.decision(1, 2, 3, 4)
def test_routes_under_v1() -> None:
    paths = set(create_app().openapi()["paths"])  # the contract the app publishes
    assert {"/v1/orders", "/v1/orders/{order_id}"} <= paths
    assert (
        TestClient(create_app()).get("/orders/1").status_code == 404
    )  # nothing outside /v1


@pytest.mark.integration
@pytest.mark.invariant("main.INV1")
def test_a_domain_error_answers_only_its_detail() -> None:
    client = TestClient(create_app())
    created = client.post(
        "/v1/orders", json={"amount": "7.25"}, headers={"X-Customer": "kim"}
    ).json()
    for customer, order_id in (("lou", created["id"]), ("kim", 10**9)):
        answer = client.get(f"/v1/orders/{order_id}", headers={"X-Customer": customer})
        assert answer.status_code == 404
        assert answer.json() == {"detail": "Order not found"}
