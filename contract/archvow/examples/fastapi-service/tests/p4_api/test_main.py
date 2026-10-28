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
