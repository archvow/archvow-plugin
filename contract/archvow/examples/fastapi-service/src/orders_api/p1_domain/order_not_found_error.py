"""The order does not exist, or belongs to another customer.

@component  order_not_found_error (error, p1_domain)
@spec       US-001
@project    orders_api
@author     A. Developer
@assisted   none
@date       2026-10-09
"""


class OrderNotFoundError(Exception):
    """No such order for this customer (the app answers 404)."""
