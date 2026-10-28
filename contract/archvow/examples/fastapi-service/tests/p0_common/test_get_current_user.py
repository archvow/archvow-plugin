"""The customer comes from the X-Customer header; without it the request is refused."""

import pytest
from fastapi import HTTPException

from orders_api.p0_common.get_current_user import get_current_user


@pytest.mark.decision(1)
def test_the_header_gives_the_customer() -> None:
    assert get_current_user(" alice ") == "alice"


@pytest.mark.parametrize("header", [None, "", "  "])
@pytest.mark.decision(2)
def test_a_missing_header_is_401(header: str | None) -> None:
    with pytest.raises(HTTPException) as refused:
        get_current_user(header)
    assert refused.value.status_code == 401
