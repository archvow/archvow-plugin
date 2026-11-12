"""The command line: its arguments and its exit codes (Spark is never started here)."""

import pytest

from my_pipeline.p0_common.config import JobConfig
from my_pipeline.p4_entrypoints import my_pipeline_main


class _Session:
    stopped = False

    def stop(self) -> None:
        self.stopped = True


@pytest.fixture()
def session(monkeypatch: pytest.MonkeyPatch) -> _Session:
    started = _Session()
    monkeypatch.setattr(my_pipeline_main, "get_spark_session", lambda *_: started)
    return started


@pytest.mark.decision(1)
@pytest.mark.parametrize(
    "argv", [["--input-path", "in"], ["--input-path", " ", "--output-path", "out"]]
)
def test_invalid_arguments_exit_2_with_a_usage_message(
    argv: list[str], session: _Session, capsys: pytest.CaptureFixture[str]
) -> None:
    assert my_pipeline_main.main(argv) == 2
    assert (
        "usage: my_pipeline --input-path PATH --output-path PATH"
        in capsys.readouterr().err
    )


@pytest.mark.decision(2)
def test_exit_codes_0_success_1_job_failure(
    session: _Session, monkeypatch: pytest.MonkeyPatch
) -> None:
    given: list[JobConfig] = []
    monkeypatch.setattr(
        my_pipeline_main, "run_my_pipeline", lambda _, c: given.append(c)
    )
    assert my_pipeline_main.main(["--input-path", "in", "--output-path", "out"]) == 0
    assert given == [JobConfig("in", "out")] and session.stopped

    def fail(*_: object) -> None:
        raise FileNotFoundError("no orders at in")

    monkeypatch.setattr(my_pipeline_main, "run_my_pipeline", fail)
    assert my_pipeline_main.main(["--input-path", "in", "--output-path", "out"]) == 1
