"""The orders service: order routes under /v1, domain errors mapped to HTTP statuses.

@component  main (app, p4_api)
@spec       US-001
@project    orders_api
@author     A. Developer
@assisted   none
@date       2026-10-09
"""

from fastapi import FastAPI, Request, status
from fastapi.responses import JSONResponse

from orders_api.p0_common.logging_setup import setup_logging
from orders_api.p0_common.settings import get_settings
from orders_api.p1_domain.order_not_found_error import OrderNotFoundError
from orders_api.p4_api.orders_router import router as orders_router


def create_app() -> FastAPI:
    """The FastAPI app (uvicorn --factory orders_api.p4_api.main:create_app)."""
    setup_logging()
    app = FastAPI(title=get_settings().app_name)
    app.include_router(orders_router, prefix="/v1")
    app.add_exception_handler(OrderNotFoundError, _not_found)
    return app


async def _not_found(request: Request, exc: Exception) -> JSONResponse:
    return JSONResponse(
        status_code=status.HTTP_404_NOT_FOUND, content={"detail": "Order not found"}
    )
