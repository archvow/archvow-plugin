"""Return one order of the current customer.

@component  get_order (service, p3_services)
@spec       US-001
@project    orders_api
@author     A. Developer
@assisted   none
@date       2026-10-09
"""

from orders_api.p1_domain.order_model import Order
from orders_api.p1_domain.order_not_found_error import OrderNotFoundError
from orders_api.p2_io.orders_repository import OrdersRepository


def get_order(customer: str, order_id: int) -> Order:
    """The order `order_id` if it belongs to `customer`."""
    order = OrdersRepository().get(order_id)
    if order is None or order.customer != customer:
        message = "Order not found"
        raise OrderNotFoundError(message)
    return order
