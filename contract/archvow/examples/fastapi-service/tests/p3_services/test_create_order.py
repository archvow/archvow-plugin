"""Creating an order saves a pending order of the customer."""

from decimal import Decimal

import pytest
from hypothesis import given, settings
from hypothesis import strategies as st

from orders_api.p1_domain.new_order_model import NewOrder
from orders_api.p3_services.create_order import create_order
from orders_api.p3_services.get_order import get_order


@pytest.mark.integration
@pytest.mark.decision(1, 2, 4, 5)
def test_a_pending_order_is_saved() -> None:
    order = create_order("carol", NewOrder(amount=Decimal("12.50")))
    assert order.status == "pending" and order.customer == "carol"
    assert get_order("carol", order.id) == order


@pytest.mark.integration
@pytest.mark.decision(3)
def test_a_retry_is_a_new_order() -> None:
    new = NewOrder(amount=Decimal("1"))
    assert create_order("dan", new).id != create_order("dan", new).id


_amounts = st.decimals(min_value="0.01", max_value="1000000", places=2)


@pytest.mark.integration
@pytest.mark.invariant("create_order.INV1")
@settings(max_examples=50, deadline=None)
@given(amount=_amounts)
def test_every_saved_order_has_an_amount_greater_than_zero(amount: Decimal) -> None:
    order = create_order("ivy", NewOrder(amount=amount))
    assert order.amount > 0 and get_order("ivy", order.id).amount > 0
