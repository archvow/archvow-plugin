"""Tests of invoices_repository."""

import json
from decimal import Decimal
from pathlib import Path

import pytest

from invoicing.p1_domain.invoice_model import Invoice
from invoicing.p2_io.invoices_repository import InvoicesRepository

pytestmark = pytest.mark.integration
INVOICE = Invoice("F-1", "Ada", Decimal("12.50"), "sent")


@pytest.mark.decision(1)
def test_one_json_file_per_invoice(tmp_path: Path) -> None:
    InvoicesRepository(tmp_path).save(INVOICE)
    assert (
        json.loads((tmp_path / "F-1.json").read_text(encoding="utf-8"))["amount"]
        == "12.50"
    )


@pytest.mark.decision(2)
def test_a_missing_invoice_is_none(tmp_path: Path) -> None:
    assert InvoicesRepository(tmp_path).get("F-404") is None


@pytest.mark.decision(3)
@pytest.mark.invariant("invoices_repository.INV1")
def test_a_failed_write_leaves_the_old_file(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    repository = InvoicesRepository(tmp_path)
    repository.save(INVOICE)

    def broken(*_: object) -> None:
        raise OSError("disk full")

    monkeypatch.setattr("os.replace", broken)
    with pytest.raises(OSError):
        repository.save(Invoice("F-1", "Ada", Decimal("12.50"), "paid"))
    assert repository.get("F-1") == INVOICE
    assert [p.name for p in tmp_path.iterdir()] == ["F-1.json"]


@pytest.mark.decision(4)
def test_the_last_write_wins(tmp_path: Path) -> None:
    repository = InvoicesRepository(tmp_path)
    repository.save(INVOICE)
    repository.save(Invoice("F-1", "Ada", Decimal("12.50"), "paid"))
    assert repository.get("F-1") == Invoice("F-1", "Ada", Decimal("12.50"), "paid")


@pytest.mark.decision("P1")
@pytest.mark.decision("P2")
def test_it_keeps_the_files_in_the_data_folder(tmp_path: Path) -> None:
    InvoicesRepository(tmp_path / "invoices").save(INVOICE)
    assert [p.relative_to(tmp_path).as_posix() for p in tmp_path.rglob("*.json")] == [
        "invoices/F-1.json"
    ]
