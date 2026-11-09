"""Configures logging once, at the app's startup; the other modules only log.

@component  logging_setup (foundation, p0_common)
@spec       -
@project    orders_api
@author     A. Developer
@assisted   none
@date       2026-11-08
"""

import logging


def setup_logging(level: str = "INFO") -> None:
    """Logs to stderr, one line per event: time, level, logger, message."""
    logging.basicConfig(
        level=level.upper(),
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
        force=True,
    )
