"""The job's parameters, given by the entry point to the job.

@component  config (foundation, p0_common)
@spec       -
@project    my_pipeline
@author     A. Developer
@assisted   none
@date       2026-11-11
"""

from dataclasses import dataclass, fields


@dataclass(frozen=True)
class JobConfig:
    """The folders a run reads from and writes to."""

    input_path: str
    output_path: str

    def __post_init__(self) -> None:
        """Refuse an empty or blank path, naming the field."""
        for field in fields(self):
            if not getattr(self, field.name).strip():
                msg = f"{field.name} must not be empty"
                raise ValueError(msg)
