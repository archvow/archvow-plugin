"""Tests of invoices_cli."""

from decimal import Decimal
from pathlib import Path

import pytest

from invoicing.p1_domain.invoice_model import Invoice
from invoicing.p2_io.invoices_repository import InvoicesRepository
from invoicing.p4_entrypoints.invoices_cli import main


@pytest.mark.decision(1)
def test_bad_arguments_exit_with_2(capsys: pytest.CaptureFixture[str]) -> None:
    with pytest.raises(SystemExit) as stopped:
        main(["refund", "F-1"])
    assert stopped.value.code == 2 and "usage" in capsys.readouterr().err


@pytest.mark.decision(2)
def test_exit_codes(tmp_path: Path) -> None:
    InvoicesRepository(tmp_path).save(Invoice("F-1", "Ada", Decimal("12.50"), "draft"))
    assert main(["--data-dir", str(tmp_path), "pay", "F-404"]) == 3
    assert main(["--data-dir", str(tmp_path), "pay", "F-1"]) == 1


@pytest.mark.decision(3)
def test_the_invoice_on_stdout_errors_on_stderr(
    tmp_path: Path, capsys: pytest.CaptureFixture[str]
) -> None:
    InvoicesRepository(tmp_path).save(Invoice("F-1", "Ada", Decimal("12.50"), "sent"))
    assert main(["--data-dir", str(tmp_path), "pay", "F-1"]) == 0
    assert capsys.readouterr().out == "F-1 Ada 12.50 paid\n"
    main(["--data-dir", str(tmp_path), "pay", "F-404"])
    assert capsys.readouterr().err == "Invoice not found: F-404\n"
