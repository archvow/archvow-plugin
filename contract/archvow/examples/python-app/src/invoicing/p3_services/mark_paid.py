"""Marks an invoice paid.

@component  mark_paid (service, p3_services)
@spec       US-001
@project    invoicing
@author     A. Developer
@assisted   claude-code
@date       2026-10-21
"""

from __future__ import annotations

from dataclasses import replace
from pathlib import Path

from invoicing.p1_domain.invoice_model import Invoice
from invoicing.p1_domain.invoice_not_found_error import InvoiceNotFoundError
from invoicing.p2_io.invoices_repository import InvoicesRepository


def mark_paid(data_dir: Path, number: str) -> Invoice:
    """Find the invoice, refuse a draft, mark it paid, save it and return it."""
    invoices = InvoicesRepository(data_dir)
    invoice = invoices.get(number)
    if invoice is None:
        raise InvoiceNotFoundError(number)
    if invoice.status == "draft":
        raise ValueError(f"invoice {number} is a draft: send it before it is paid")
    paid = replace(invoice, status="paid")
    invoices.save(paid)
    return paid
