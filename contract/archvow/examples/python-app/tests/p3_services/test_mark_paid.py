"""Tests of mark_paid."""

from decimal import Decimal
from pathlib import Path

import pytest

from invoicing.p1_domain.invoice_model import Invoice
from invoicing.p1_domain.invoice_not_found_error import InvoiceNotFoundError
from invoicing.p2_io.invoices_repository import InvoicesRepository
from invoicing.p3_services.mark_paid import mark_paid

pytestmark = pytest.mark.integration


def _saved(tmp_path: Path, status: str) -> None:
    InvoicesRepository(tmp_path).save(Invoice("F-1", "Ada", Decimal("12.50"), status))


@pytest.mark.decision(1)
def test_a_sent_invoice_becomes_paid_and_a_draft_is_refused(tmp_path: Path) -> None:
    _saved(tmp_path, "sent")
    assert mark_paid(tmp_path, "F-1").status == "paid"
    assert InvoicesRepository(tmp_path).get("F-1").status == "paid"
    _saved(tmp_path, "draft")
    with pytest.raises(ValueError, match="draft"):
        mark_paid(tmp_path, "F-1")


@pytest.mark.decision(2)
def test_an_unknown_invoice_is_not_found(tmp_path: Path) -> None:
    with pytest.raises(InvoiceNotFoundError):
        mark_paid(tmp_path, "F-404")


@pytest.mark.decision(3)
@pytest.mark.decision(4)
@pytest.mark.invariant("mark_paid.INV1")
def test_paying_twice_is_paying_once(tmp_path: Path) -> None:
    _saved(tmp_path, "sent")
    once = mark_paid(tmp_path, "F-1")
    assert mark_paid(tmp_path, "F-1") == once
    assert InvoicesRepository(tmp_path).get("F-1") == once


@pytest.mark.decision("P1")
@pytest.mark.decision("P2")
def test_it_records_the_payment_only(tmp_path: Path) -> None:
    _saved(tmp_path, "sent")
    paid = mark_paid(tmp_path, "F-1")
    assert (paid.number, paid.customer, paid.amount) == ("F-1", "Ada", Decimal("12.50"))
