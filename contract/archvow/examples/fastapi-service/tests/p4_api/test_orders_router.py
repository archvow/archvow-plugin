"""The order routes: statuses, and a customer never reads another customer's order."""

import pytest
from fastapi.testclient import TestClient
from hypothesis import given, settings
from hypothesis import strategies as st

from orders_api.p4_api.main import create_app

client = TestClient(create_app())


@pytest.mark.integration
@pytest.mark.decision(2, 4, 5)
def test_create_then_read() -> None:
    created = client.post(
        "/v1/orders", json={"amount": "10.00"}, headers={"X-Customer": "gina"}
    )
    assert created.status_code == 201 and created.json()["status"] == "pending"
    read = client.get(
        f"/v1/orders/{created.json()['id']}", headers={"X-Customer": "gina"}
    )
    assert read.status_code == 200 and read.json() == created.json()


@pytest.mark.integration
@pytest.mark.decision(3)
def test_errors() -> None:
    assert client.post("/v1/orders", json={"amount": "10"}).status_code == 401
    assert (
        client.post(
            "/v1/orders", json={"amount": "0"}, headers={"X-Customer": "h"}
        ).status_code
        == 422
    )
    missing = client.get("/v1/orders/999999", headers={"X-Customer": "h"})
    assert missing.status_code == 404 and missing.json() == {
        "detail": "Order not found"
    }


_names = st.from_regex(r"[a-z]{1,8}", fullmatch=True)


@pytest.mark.integration
@pytest.mark.invariant("orders_router.INV1")
@settings(max_examples=25, deadline=None)
@given(owner=_names, other=_names)
@pytest.mark.decision(1, "P1", "P2")
def test_a_customer_only_reads_their_own_orders(owner: str, other: str) -> None:
    created = client.post(
        "/v1/orders", json={"amount": "1.00"}, headers={"X-Customer": owner}
    ).json()
    answer = client.get(f"/v1/orders/{created['id']}", headers={"X-Customer": other})
    assert answer.status_code == (200 if other == owner else 404)
