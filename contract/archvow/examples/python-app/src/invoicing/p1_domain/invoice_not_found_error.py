"""No invoice with this number.

@component  invoice_not_found_error (error, p1_domain)
@spec       US-001
@project    invoicing
@author     A. Developer
@assisted   claude-code
@date       2026-10-21
"""

from __future__ import annotations


class InvoiceNotFoundError(Exception):
    """No invoice has the number asked for; the command line exits with code 3."""

    def __init__(self, number: str) -> None:
        """Keep the number the user typed; the message names nothing else."""
        super().__init__(f"Invoice not found: {number}")
        self.number = number
