"""The job's parameters: two required paths, never blank."""

import dataclasses

import pytest

from my_pipeline.p0_common.config import JobConfig


@pytest.mark.decision(1)
def test_two_required_paths_in_a_frozen_dataclass() -> None:
    config = JobConfig("in", "out")
    assert (config.input_path, config.output_path) == ("in", "out")
    with pytest.raises(dataclasses.FrozenInstanceError):
        config.input_path = "other"  # type: ignore[misc]


@pytest.mark.decision(2)
@pytest.mark.parametrize("paths", [("", "out"), ("in", "  ")])
def test_a_blank_path_raises_value_error_naming_it(paths: tuple[str, str]) -> None:
    field = "input_path" if not paths[0] else "output_path"
    with pytest.raises(ValueError, match=field):
        JobConfig(*paths)
