"""The service's settings, read from the environment.

@component  settings (foundation, p0_common)
@spec       -
@project    orders_api
@author     A. Developer
@assisted   none
@date       2026-10-09
"""

from functools import lru_cache

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """Settings of the orders service."""

    app_name: str = "orders"


@lru_cache
def get_settings() -> Settings:
    """The settings, read once from the environment."""
    return Settings()
