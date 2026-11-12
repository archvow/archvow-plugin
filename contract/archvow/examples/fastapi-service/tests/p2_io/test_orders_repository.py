"""Orders are stored and found again; a missing one is None."""

from decimal import Decimal

import pytest
from hypothesis import given, settings
from hypothesis import strategies as st

from orders_api.p1_domain.order_model import Order
from orders_api.p2_io.orders_repository import OrdersRepository


@pytest.mark.integration
@pytest.mark.decision(1, 2, 3, 4, "P1", "P2")
def test_store_and_find() -> None:
    repository = OrdersRepository()
    order = Order(
        id=repository.next_id(), customer="bob", amount=Decimal("3"), status="pending"
    )
    repository.add(order)
    assert repository.get(order.id) == order
    assert repository.next_id() == order.id + 1
    assert repository.get(10**9) is None


_orders = st.builds(
    Order,
    id=st.integers(min_value=10**6, max_value=10**7),
    customer=st.text(min_size=1, max_size=12),
    amount=st.decimals(min_value="0", max_value="1000000", places=2),
    status=st.sampled_from(["pending", "paid", "cancelled"]),
)


@pytest.mark.integration
@pytest.mark.invariant("orders_repository.INV1")
@settings(max_examples=50, deadline=None)
@given(order=_orders)
def test_get_returns_the_order_saved_under_that_id(order: Order) -> None:
    repository = OrdersRepository()
    repository.add(order)
    assert repository.get(order.id) == order
    assert repository.get(order.id + 10**7) is None
