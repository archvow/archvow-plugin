"""The authenticated customer, from the X-Customer header.

@component  get_current_user (dependency, p0_common)
@spec       US-001
@project    orders_api
@author     A. Developer
@assisted   none
@date       2026-10-09
"""

from typing import Annotated

from fastapi import Header, HTTPException, status


def get_current_user(x_customer: Annotated[str | None, Header()] = None) -> str:
    """The customer id of the request; 401 without one."""
    if not x_customer or not x_customer.strip():
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail="Unauthorized"
        )
    return x_customer.strip()
