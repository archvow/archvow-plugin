"""Stores and finds invoices, one JSON file per invoice.

@component  invoices_repository (repository, p2_io)
@spec       US-001
@project    invoicing
@author     A. Developer
@assisted   claude-code
@date       2026-10-21
"""

from __future__ import annotations

import json
import os
import tempfile
from decimal import Decimal
from pathlib import Path

from invoicing.p1_domain.invoice_model import Invoice


class InvoicesRepository:
    """The invoices of one data folder: NUMBER.json each, the amount as a string."""

    def __init__(self, data_dir: Path) -> None:
        """Work in `data_dir`."""
        self._dir = data_dir

    def get(self, number: str) -> Invoice | None:
        """The invoice with this number, or None when there is none."""
        path = self._dir / f"{number}.json"
        if not path.is_file():
            return None
        data = json.loads(path.read_text(encoding="utf-8"))
        return Invoice(
            data["number"], data["customer"], Decimal(data["amount"]), data["status"]
        )

    def save(self, invoice: Invoice) -> None:
        """Write the invoice completely or not at all: a temporary file, renamed."""
        self._dir.mkdir(parents=True, exist_ok=True)
        data = {
            "number": invoice.number,
            "customer": invoice.customer,
            "amount": str(invoice.amount),
            "status": invoice.status,
        }
        handle, temporary = tempfile.mkstemp(dir=self._dir, suffix=".tmp")
        try:
            with os.fdopen(handle, "w", encoding="utf-8") as out:
                json.dump(data, out)
            target = self._dir / f"{invoice.number}.json"
            os.replace(temporary, target)  # archvow: enforces[invoices_repository.INV1]
        except BaseException:
            Path(temporary).unlink(missing_ok=True)
            raise
