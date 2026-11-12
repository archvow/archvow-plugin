"""A customer gets their own order; any other id is "not found"."""

from decimal import Decimal

import pytest
from hypothesis import assume, given, settings
from hypothesis import strategies as st

from orders_api.p1_domain.new_order_model import NewOrder
from orders_api.p1_domain.order_not_found_error import OrderNotFoundError
from orders_api.p3_services.create_order import create_order
from orders_api.p3_services.get_order import get_order


@pytest.mark.integration
@pytest.mark.decision(1, 2, 3, 5)
def test_own_order_and_others() -> None:
    order = create_order("erin", NewOrder(amount=Decimal("2")))
    assert get_order("erin", order.id).id == order.id
    for customer, order_id in (("frank", order.id), ("erin", 10**9)):
        with pytest.raises(OrderNotFoundError):
            get_order(customer, order_id)


_customers = st.text(alphabet="abcdefghij", min_size=1, max_size=6)


@pytest.mark.integration
@pytest.mark.decision(4)
@settings(max_examples=50, deadline=None)
@given(owner=_customers, other=_customers)
def test_only_the_owner_gets_the_order(owner: str, other: str) -> None:
    assume(owner != other)
    order = create_order(owner, NewOrder(amount=Decimal("5")))
    assert get_order(owner, order.id) == order
    with pytest.raises(OrderNotFoundError):
        get_order(other, order.id)
