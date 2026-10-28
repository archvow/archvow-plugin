"""An invoice and its validation.

@component  invoice_model (model, p1_domain)
@spec       US-001
@project    invoicing
@author     A. Developer
@assisted   claude-code
@date       2026-10-21
"""

from __future__ import annotations

from dataclasses import dataclass
from decimal import Decimal

_STATUSES = ("draft", "sent", "paid")


@dataclass(frozen=True)
class Invoice:
    """An invoice; a change builds a new one (dataclasses.replace)."""

    number: str
    customer: str
    amount: Decimal
    status: str

    def __post_init__(self) -> None:
        """Refuse an invoice that breaks the rules of decision 2."""
        if not self.number.strip() or not self.customer.strip():
            raise ValueError("an invoice needs a number and a customer")
        if self.amount < 0:
            raise ValueError("the amount of an invoice is zero or more")
        if self.amount != self.amount.quantize(Decimal("0.01")):
            raise ValueError("the amount of an invoice has at most 2 decimals")
        if self.status not in _STATUSES:
            raise ValueError(f"an invoice's status is one of {', '.join(_STATUSES)}")
