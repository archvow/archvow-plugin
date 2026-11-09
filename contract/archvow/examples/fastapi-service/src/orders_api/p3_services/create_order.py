"""Create a pending order for the current customer and save it.

@component  create_order (service, p3_services)
@spec       US-001
@project    orders_api
@author     A. Developer
@assisted   none
@date       2026-10-09
"""

import logging

from orders_api.p1_domain.new_order_model import NewOrder
from orders_api.p1_domain.order_model import Order
from orders_api.p2_io.orders_repository import OrdersRepository

logger = logging.getLogger(__name__)


def create_order(customer: str, new: NewOrder) -> Order:
    """Create and save a pending order of `customer`."""
    repository = OrdersRepository()
    order = Order(
        id=repository.next_id(), customer=customer, amount=new.amount, status="pending"
    )
    repository.add(order)
    logger.info("order %s created", order.id)
    return order
