"""An order: every field required, amount >= 0, a known status."""

from decimal import Decimal

import pytest
from pydantic import ValidationError

from orders_api.p1_domain.order_model import Order


@pytest.mark.decision(1, 3)
def test_a_valid_order() -> None:
    order = Order(id=1, customer="alice", amount=Decimal("9.99"), status="pending")
    assert order.amount == Decimal("9.99")


@pytest.mark.parametrize(
    "change",
    [{"amount": Decimal("-1")}, {"status": "lost"}, {"amount": Decimal("1.001")}],
)
@pytest.mark.decision(2)
def test_invalid_values_are_refused(change: dict) -> None:
    with pytest.raises(ValidationError):
        Order(
            **{
                "id": 1,
                "customer": "alice",
                "amount": Decimal("1"),
                "status": "pending",
                **change,
            }
        )
