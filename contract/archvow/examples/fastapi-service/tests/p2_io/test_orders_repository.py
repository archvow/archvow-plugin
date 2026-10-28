"""Orders are stored and found again; a missing one is None."""

from decimal import Decimal

import pytest

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
