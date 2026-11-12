"""The orders are read from every CSV file under the path, or the run fails."""

from datetime import date
from decimal import Decimal
from pathlib import Path

import pytest
from pyspark.sql import DataFrame, SparkSession

from my_pipeline.p1_domain.orders_schema import ORDERS_SCHEMA
from my_pipeline.p2_io.orders_reader import read_orders

pytestmark = pytest.mark.integration
HEADER = "order_id,customer_id,amount,order_date\n"


def _write(folder: Path, name: str, text: str) -> None:
    folder.mkdir(parents=True, exist_ok=True)
    (folder / name).write_text(text, encoding="utf-8")


@pytest.mark.decision(1)
def test_a_missing_or_empty_input_fails_with_a_clear_error(
    spark: SparkSession, tmp_path: Path
) -> None:
    with pytest.raises(FileNotFoundError, match="not delivered"):
        read_orders(spark, str(tmp_path / "none"))
    _write(tmp_path / "empty", "orders.csv", HEADER)
    with pytest.raises(FileNotFoundError, match="is empty"):
        read_orders(spark, str(tmp_path / "empty"))


@pytest.mark.decision(2)
def test_a_malformed_row_fails_the_job(spark: SparkSession, tmp_path: Path) -> None:
    _write(
        tmp_path / "in",
        "orders.csv",
        HEADER + "A1,C1,5.00,2026-10-01\nA2,C1,abc,2026-10-01\n",
    )
    with pytest.raises(Exception, match=r"(?i)malformed"):
        read_orders(spark, str(tmp_path / "in")).collect()


@pytest.mark.decision(3)
def test_reads_everything_under_the_path(spark: SparkSession, tmp_path: Path) -> None:
    _write(tmp_path / "in", "a.csv", HEADER + "A1,C1,5.00,2026-10-01\n")
    _write(tmp_path / "in", "b.csv", HEADER + "A2,,0.00,2026-10-02\n")
    rows = read_orders(spark, str(tmp_path / "in")).orderBy("order_id").collect()
    assert [tuple(r) for r in rows] == [
        ("A1", "C1", Decimal("5.00"), date(2026, 10, 1)),
        ("A2", None, Decimal("0.00"), date(2026, 10, 2)),
    ]


@pytest.mark.decision(4)
def test_spark_reads_it_with_the_schema(spark: SparkSession, tmp_path: Path) -> None:
    _write(tmp_path / "in", "a.csv", HEADER + "A1,C1,5.00,2026-10-01\n")
    orders = read_orders(spark, str(tmp_path / "in"))
    assert isinstance(orders, DataFrame)
    assert orders.columns == ORDERS_SCHEMA.fieldNames()
