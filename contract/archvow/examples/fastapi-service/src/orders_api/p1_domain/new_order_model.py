"""What a customer sends to create an order.

@component  new_order_model (model, p1_domain)
@spec       US-001
@project    orders_api
@author     A. Developer
@assisted   none
@date       2026-10-09
"""

from decimal import Decimal

from pydantic import BaseModel, Field


class NewOrder(BaseModel):
    """The input of an order creation."""

    amount: Decimal = Field(gt=0, decimal_places=2)
