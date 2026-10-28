"""Tests of invoice_not_found_error."""

import pytest

from invoicing.p1_domain.invoice_not_found_error import InvoiceNotFoundError


@pytest.mark.decision(1)
def test_it_carries_the_number_for_exit_code_3() -> None:
    assert InvoiceNotFoundError("F-9").number == "F-9"


@pytest.mark.decision(2)
def test_its_message_names_only_the_number() -> None:
    assert str(InvoiceNotFoundError("F-9")) == "Invoice not found: F-9"
