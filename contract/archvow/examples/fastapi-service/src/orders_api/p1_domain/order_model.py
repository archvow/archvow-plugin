"""An order.

@component  order_model (model, p1_domain)
@spec       US-001
@project    orders_api
@author     A. Developer
@assisted   none
@date       2026-10-09
"""

from decimal import Decimal
from typing import Literal

from pydantic import BaseModel, Field


class Order(BaseModel):
    """An order of one customer."""

    id: int
    customer: str
    amount: Decimal = Field(ge=0, decimal_places=2)
    status: Literal["pending", "paid", "cancelled"]
