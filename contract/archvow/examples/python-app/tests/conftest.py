"""Shared fixtures: the logging is put back as it was after each test."""

import logging
from collections.abc import Iterator

import pytest


@pytest.fixture(autouse=True)
def _logging_put_back() -> Iterator[None]:
    root = logging.getLogger()
    handlers, level = root.handlers[:], root.level
    yield
    root.handlers[:] = handlers
    root.setLevel(level)
