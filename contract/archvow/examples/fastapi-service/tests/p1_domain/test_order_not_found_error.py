"""The error says nothing but "Order not found"."""

import pytest

from orders_api.p1_domain.order_not_found_error import OrderNotFoundError


@pytest.mark.decision(1, 2)
def test_it_is_an_exception_with_a_plain_message() -> None:
    assert str(OrderNotFoundError("Order not found")) == "Order not found"
