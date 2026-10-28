"""Tests of invoice_model."""

from dataclasses import FrozenInstanceError, replace
from decimal import Decimal

import pytest
from hypothesis import given
from hypothesis import strategies as st

from invoicing.p1_domain.invoice_model import Invoice


def _invoice(**changes: object) -> Invoice:
    values: dict[str, object] = {
        "number": "F-1",
        "customer": "Ada",
        "amount": Decimal("10.00"),
        "status": "sent",
    }
    values.update(changes)
    return Invoice(**values)


@pytest.mark.decision(1)
def test_all_fields_are_required() -> None:
    with pytest.raises(TypeError):
        Invoice("F-1", "Ada", Decimal(1))


@pytest.mark.decision(2)
def test_values_follow_the_rules() -> None:
    for bad in (
        {"amount": Decimal("1.001")},
        {"status": "lost"},
        {"number": " "},
        {"customer": ""},
    ):
        with pytest.raises(ValueError):
            _invoice(**bad)


@pytest.mark.decision(3)
def test_no_field_is_secret() -> None:
    assert "Ada" in repr(_invoice())


@pytest.mark.decision(4)
def test_an_invoice_is_frozen() -> None:
    invoice = _invoice()
    with pytest.raises(FrozenInstanceError):
        invoice.status = "paid"
    assert replace(invoice, status="paid").status == "paid"


@pytest.mark.invariant("invoice_model.INV1")
@given(st.decimals(max_value=Decimal("-0.01"), places=2, allow_nan=False))
def test_a_negative_amount_is_refused(amount: Decimal) -> None:
    with pytest.raises(ValueError):
        _invoice(amount=amount)
