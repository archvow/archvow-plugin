"""A new order needs an amount greater than zero, with at most 2 decimals."""

from decimal import Decimal

import pytest
from pydantic import ValidationError

from orders_api.p1_domain.new_order_model import NewOrder


@pytest.mark.decision(1, 3)
def test_a_positive_amount() -> None:
    assert NewOrder(amount=Decimal("0.01")).amount == Decimal("0.01")


@pytest.mark.parametrize("amount", ["0", "-5", "1.234"])
@pytest.mark.decision(2)
def test_other_amounts_are_refused(amount: str) -> None:
    with pytest.raises(ValidationError):
        NewOrder(amount=Decimal(amount))
