"""The program's settings, read from the environment.

@component  config (foundation, p0_common)
@spec       -
@project    invoicing
@author     A. Developer
@assisted   claude-code
@date       2026-10-21
"""

from __future__ import annotations

import os
from collections.abc import Mapping
from dataclasses import dataclass
from pathlib import Path


@dataclass(frozen=True)
class Config:
    """The settings: where the invoices are, and how much to log."""

    data_dir: Path
    log_level: str = "INFO"


def load_config(environ: Mapping[str, str] | None = None) -> Config:
    """Read INVOICING_DATA_DIR (required) and INVOICING_LOG_LEVEL."""
    values = os.environ if environ is None else environ
    data_dir = values.get("INVOICING_DATA_DIR", "").strip()
    if not data_dir:
        raise ValueError("INVOICING_DATA_DIR is not set: the folder of the invoices")
    return Config(Path(data_dir), values.get("INVOICING_LOG_LEVEL", "INFO"))
