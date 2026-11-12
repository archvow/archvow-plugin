"""Stores and finds orders (in memory to start).

@component  orders_repository (repository, p2_io)
@spec       US-001
@project    orders_api
@author     A. Developer
@assisted   none
@date       2026-10-09
"""

import itertools
import threading

from orders_api.p1_domain.order_model import Order

_ORDERS: dict[int, Order] = {}
_IDS = itertools.count(1)
_IDS_LOCK = threading.Lock()


class OrdersRepository:
    """The orders, kept in memory for the life of the process."""

    def next_id(self) -> int:
        """The id of the next order: never twice the same, even at the same time."""
        with _IDS_LOCK:
            return next(_IDS)

    def add(self, order: Order) -> None:
        """Save an order."""
        _ORDERS[order.id] = order

    def get(self, order_id: int) -> Order | None:
        """The order with this id, or None."""
        return _ORDERS.get(order_id)
