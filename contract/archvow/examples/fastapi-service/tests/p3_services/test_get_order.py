"""A customer gets their own order; any other id is "not found"."""

from decimal import Decimal

import pytest

from orders_api.p1_domain.new_order_model import NewOrder
from orders_api.p1_domain.order_not_found_error import OrderNotFoundError
from orders_api.p3_services.create_order import create_order
from orders_api.p3_services.get_order import get_order


@pytest.mark.integration
@pytest.mark.decision(1, 2, 3, 4, 5)
def test_own_order_and_others() -> None:
    order = create_order("erin", NewOrder(amount=Decimal("2")))
    assert get_order("erin", order.id).id == order.id
    for customer, order_id in (("frank", order.id), ("erin", 10**9)):
        with pytest.raises(OrderNotFoundError):
            get_order(customer, order_id)
