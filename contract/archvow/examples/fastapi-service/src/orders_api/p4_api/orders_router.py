"""The order API: create an order, read one of your own.

@component  orders_router (router, p4_api)
@spec       US-001
@project    orders_api
@author     A. Developer
@assisted   none
@date       2026-10-09
"""

from typing import Annotated

from fastapi import APIRouter, Depends, status

from orders_api.p0_common.get_current_user import get_current_user
from orders_api.p1_domain.new_order_model import NewOrder
from orders_api.p1_domain.order_model import Order
from orders_api.p3_services.create_order import create_order
from orders_api.p3_services.get_order import get_order

router: APIRouter = APIRouter(tags=["orders"])

_Customer = Annotated[str, Depends(get_current_user)]


@router.post("/orders", status_code=status.HTTP_201_CREATED)
def post_order(new: NewOrder, customer: _Customer) -> Order:
    """Create an order for the calling customer."""
    return create_order(customer, new)


@router.get("/orders/{order_id}")
def read_order(order_id: int, customer: _Customer) -> Order:
    """One order of the calling customer."""
    return get_order(customer, order_id)
