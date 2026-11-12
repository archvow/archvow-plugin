"""End to end: the job on a business example's input gives its expected output."""

import csv
from pathlib import Path

import pytest
from pyspark.sql import SparkSession

from my_pipeline.p0_common.config import JobConfig
from my_pipeline.p3_jobs.my_pipeline_job import run_my_pipeline

pytestmark = pytest.mark.integration
EXAMPLE = Path(__file__).parents[1] / "examples/compute_vat/US-002-vat-20"


def test_the_job_gives_the_vat_example(spark: SparkSession, tmp_path: Path) -> None:
    run_my_pipeline(spark, JobConfig(str(EXAMPLE / "input"), str(tmp_path / "out")))
    rows = spark.read.parquet(str(tmp_path / "out")).orderBy("order_id").collect()
    got = [
        {k: "" if v is None else str(v) for k, v in r.asDict().items()} for r in rows
    ]
    with (EXAMPLE / "expected/orders.csv").open(encoding="utf-8", newline="") as file:
        assert got == sorted(csv.DictReader(file), key=lambda r: r["order_id"])
