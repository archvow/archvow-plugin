"""Shared fixtures: one SparkSession, the logging put back, the business examples."""

import csv
import logging
from collections.abc import Callable, Iterator
from datetime import date
from decimal import Decimal
from pathlib import Path

import pytest
from pyspark.sql import DataFrame, SparkSession

from my_pipeline.p0_common.spark import get_spark_session
from my_pipeline.p1_domain.orders_schema import ORDERS_SCHEMA

EXAMPLES = Path(__file__).parent / "examples"


@pytest.fixture(scope="session")
def spark() -> Iterator[SparkSession]:
    """A local session, stopped at the end; few partitions keep small tests fast."""
    session = get_spark_session("tests", master="local[1]")
    session.conf.set("spark.sql.shuffle.partitions", "2")
    yield session
    session.stop()


@pytest.fixture(autouse=True)
def logging_put_back() -> Iterator[None]:
    """Put the root logger back as it was after each test.

    setup_logging (its own test, or the entry point's) binds it to the test's
    captured stderr: a later log (Spark's py4j, when the run ends) would write
    to a closed stream.
    """
    root = logging.getLogger()
    handlers, level = root.handlers[:], root.level
    yield
    root.handlers[:] = handlers
    root.setLevel(level)


Rows = list[dict[str, str]]


def _read(path: Path) -> Rows:
    with path.open(encoding="utf-8", newline="") as file:
        return list(csv.DictReader(file))


def _text(value: object) -> str:
    return "" if value is None else str(value)


Example = Callable[[str, str, Callable[[DataFrame], DataFrame]], tuple[Rows, Rows]]


@pytest.fixture()
def example(spark: SparkSession) -> Example:
    """Run a component on tests/examples/<component>/<example>/input/orders.csv.

    It writes what the component gave to actual/orders.csv (not committed) and
    returns its rows and those of expected/orders.csv, sorted by order_id.
    """

    def run(
        component: str, name: str, transform: Callable[[DataFrame], DataFrame]
    ) -> tuple[Rows, Rows]:
        folder = EXAMPLES / component / name
        rows = [
            (
                r["order_id"] or None,
                r["customer_id"] or None,
                Decimal(r["amount"]),
                date.fromisoformat(r["order_date"]),
            )
            for r in _read(folder / "input/orders.csv")
        ]
        result = transform(spark.createDataFrame(rows, ORDERS_SCHEMA))
        actual = [
            {k: _text(v) for k, v in row.asDict().items()}
            for row in result.orderBy("order_id").collect()
        ]
        (folder / "actual").mkdir(exist_ok=True)
        with (folder / "actual/orders.csv").open(
            "w", encoding="utf-8", newline=""
        ) as f:
            writer = csv.DictWriter(f, fieldnames=result.columns, lineterminator="\n")
            writer.writeheader()
            writer.writerows(actual)
        expected = sorted(
            _read(folder / "expected/orders.csv"), key=lambda r: r["order_id"]
        )
        return actual, expected

    return run
