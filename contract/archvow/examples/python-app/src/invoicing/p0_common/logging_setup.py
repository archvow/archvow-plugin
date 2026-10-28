"""Configures logging once, for the entry points.

@component  logging_setup (foundation, p0_common)
@spec       -
@project    invoicing
@author     A. Developer
@assisted   claude-code
@date       2026-10-21
"""

from __future__ import annotations

import logging


def setup_logging(level: str = "INFO") -> None:
    """Send logs to stderr, one line per event: time, level, message."""
    logging.basicConfig(
        level=level.upper(), format="%(asctime)s %(levelname)s %(message)s", force=True
    )
