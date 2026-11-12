"""The logs: stderr, one line per event with time, level, component and message."""

import logging

import pytest

from my_pipeline.p0_common.logging_setup import setup_logging


@pytest.mark.decision(1)
def test_one_line_per_event_on_stderr(capsys: pytest.CaptureFixture[str]) -> None:
    setup_logging("debug")
    logging.getLogger("my_pipeline.demo").debug("hello")
    line = capsys.readouterr().err.strip()
    assert line.endswith(" DEBUG my_pipeline.demo hello") and line[:4].isdigit()
