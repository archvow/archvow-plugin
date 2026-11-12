"""Logging of the run, configured once by the entry point.

@component  logging_setup (foundation, p0_common)
@spec       -
@project    my_pipeline
@author     A. Developer
@assisted   none
@date       2026-11-11
"""

import logging
import sys


def setup_logging(level: str = "INFO") -> None:
    """Send the logs to stderr, one line per event: time, level, component, message."""
    logging.basicConfig(
        stream=sys.stderr,
        level=level.upper(),
        force=True,
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
