"""The orders with their VAT are written as parquet, all or nothing."""

from datetime import date
from decimal import Decimal
from pathlib import Path

import pytest
from pyspark.sql import DataFrame, SparkSession
from pyspark.sql import functions as F

from my_pipeline.p1_domain.vat_schema import VAT_SCHEMA
from my_pipeline.p2_io.vat_writer import write_vat

pytestmark = pytest.mark.integration


def _rows(spark: SparkSession, *amounts: str) -> DataFrame:
    data = [
        (f"A{n}", "C1", Decimal(a), date(2026, 10, 1), Decimal(a) / 5)
        for n, a in enumerate(amounts, 1)
    ]
    return spark.createDataFrame(data, VAT_SCHEMA)


def _read(spark: SparkSession, path: Path) -> list[tuple]:
    return sorted(tuple(r) for r in spark.read.parquet(str(path)).collect())


@pytest.mark.decision(1)
def test_an_existing_output_is_overwritten(spark: SparkSession, tmp_path: Path) -> None:
    out = tmp_path / "out"
    write_vat(_rows(spark, "5.00", "1.00"), str(out))
    write_vat(_rows(spark, "2.00"), str(out))
    assert _read(spark, out) == sorted(tuple(r) for r in _rows(spark, "2.00").collect())


@pytest.mark.decision(2)
def test_a_failure_while_writing_leaves_no_half_written_output(
    spark: SparkSession, tmp_path: Path
) -> None:
    out = tmp_path / "out"
    failing = _rows(spark, "5.00").withColumn(
        "vat", F.when(F.raise_error("disk full").isNull(), F.col("vat"))
    )
    with pytest.raises(Exception, match="disk full"):
        write_vat(failing, str(out))
    assert not out.exists()  # absent, never half there


@pytest.mark.decision(3)
def test_the_same_input_gives_the_same_output(
    spark: SparkSession, tmp_path: Path
) -> None:
    out = tmp_path / "out"
    write_vat(_rows(spark, "5.00", "1.00"), str(out))
    first = _read(spark, out)
    write_vat(_rows(spark, "5.00", "1.00"), str(out))
    assert _read(spark, out) == first and len(first) == 2


@pytest.mark.decision(4, 5)
def test_parquet_files_not_partitioned_no_temporary_folder_left(
    spark: SparkSession, tmp_path: Path
) -> None:
    out = tmp_path / "out"
    write_vat(_rows(spark, "5.00").withColumn("extra", F.lit(1)), str(out))
    assert any(p.suffix == ".parquet" for p in out.iterdir())
    assert not [p for p in out.iterdir() if p.is_dir()]  # no date=... folders
    assert not (tmp_path / "out_tmp").exists()
    assert spark.read.parquet(str(out)).columns == VAT_SCHEMA.fieldNames()
