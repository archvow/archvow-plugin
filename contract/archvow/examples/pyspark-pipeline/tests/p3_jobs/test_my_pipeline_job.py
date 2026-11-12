"""The job reads, cleans, computes the VAT and writes; it fails on a missing input."""

from decimal import Decimal
from pathlib import Path

import pytest
from pyspark.sql import SparkSession

from my_pipeline.p0_common.config import JobConfig
from my_pipeline.p3_jobs.my_pipeline_job import run_my_pipeline

pytestmark = pytest.mark.integration
ORDERS = (
    "order_id,customer_id,amount,order_date\n"
    "A1,C1,12.50,2026-10-01\nA2,C2,-3.00,2026-10-01\n,C3,1.00,2026-10-01\n"
)


def _input(tmp_path: Path) -> str:
    (tmp_path / "in").mkdir()
    (tmp_path / "in/orders.csv").write_text(ORDERS, encoding="utf-8")
    return str(tmp_path / "in")


def _output(spark: SparkSession, path: Path) -> list[tuple[str, Decimal]]:
    return sorted((r.order_id, r.vat) for r in spark.read.parquet(str(path)).collect())


@pytest.mark.decision(1)
def test_a_missing_input_fails(spark: SparkSession, tmp_path: Path) -> None:
    with pytest.raises(FileNotFoundError):
        run_my_pipeline(spark, JobConfig(str(tmp_path / "none"), str(tmp_path / "out")))
    assert not (tmp_path / "out").exists()


@pytest.mark.decision(2)
def test_a_re_run_gives_the_same_output(spark: SparkSession, tmp_path: Path) -> None:
    config = JobConfig(_input(tmp_path), str(tmp_path / "out"))
    run_my_pipeline(spark, config)
    first = _output(spark, tmp_path / "out")
    run_my_pipeline(spark, config)
    assert _output(spark, tmp_path / "out") == first == [("A1", Decimal("2.50"))]


@pytest.mark.decision(3)
def test_processes_the_input_it_is_given(
    spark: SparkSession, tmp_path: Path, caplog: pytest.LogCaptureFixture
) -> None:
    caplog.set_level("INFO")
    run_my_pipeline(spark, JobConfig(_input(tmp_path), str(tmp_path / "out")))
    assert _output(spark, tmp_path / "out") == [("A1", Decimal("2.50"))]
    assert "orders read 3, kept 1" in caplog.text and "1 rows written" in caplog.text
