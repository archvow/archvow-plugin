"""Tests of invoices_cli."""

import json
import shutil
from decimal import Decimal
from pathlib import Path

import pytest

from invoicing.p1_domain.invoice_model import Invoice
from invoicing.p2_io.invoices_repository import InvoicesRepository
from invoicing.p4_entrypoints.invoices_cli import main

EXAMPLE = Path(__file__).parents[1] / "examples/invoices_cli/US-001-pay-a-sent-invoice"


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
    err = capsys.readouterr().err.splitlines()
    assert "Invoice not found: F-404" in err  # with the run's log lines around it
    assert err[-1].endswith("INFO pay ended with exit code 3")


@pytest.mark.example("US-001-pay-a-sent-invoice")
def test_example_pay_a_sent_invoice(
    tmp_path: Path, capsys: pytest.CaptureFixture[str]
) -> None:
    data = tmp_path / "invoices"
    shutil.copytree(EXAMPLE / "input/invoices", data)
    assert main(["--data-dir", str(data), "pay", "F-7"]) == 0
    printed = capsys.readouterr().out
    actual = EXAMPLE / "actual"  # what it gave, for archvow show (not committed)
    (actual / "invoices").mkdir(parents=True, exist_ok=True)
    (actual / "stdout.txt").write_text(printed, encoding="utf-8", newline="")
    shutil.copy(data / "F-7.json", actual / "invoices/F-7.json")
    expected = EXAMPLE / "expected"
    assert printed == (expected / "stdout.txt").read_text(encoding="utf-8")
    saved = json.loads((data / "F-7.json").read_text(encoding="utf-8"))
    assert saved == json.loads(
        (expected / "invoices/F-7.json").read_text(encoding="utf-8")
    )
