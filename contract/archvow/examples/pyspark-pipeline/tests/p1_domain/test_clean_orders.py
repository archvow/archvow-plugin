"""Only the valid orders are kept, one per order_id, with the schema columns."""

from datetime import date
from decimal import Decimal

import pytest
from hypothesis import given, settings
from hypothesis import strategies as st
from pyspark.sql import DataFrame, SparkSession
from pyspark.sql import functions as F
from pyspark.sql import types as T

from conftest import Example
from my_pipeline.p1_domain.clean_orders import clean_orders
from my_pipeline.p1_domain.orders_schema import ORDERS_SCHEMA

DAY = date(2026, 10, 1)
# as the reader gives them: Spark reads every CSV column as nullable
READ = T.StructType([T.StructField(f.name, f.dataType) for f in ORDERS_SCHEMA])


def _orders(spark: SparkSession, *rows: tuple[str | None, str]) -> DataFrame:
    """Orders (order_id, amount) of customer C1 on DAY."""
    data = [(oid, "C1", Decimal(amount), DAY) for oid, amount in rows]
    return spark.createDataFrame(data, READ)


def _ids(df: DataFrame) -> list[str]:
    return sorted(row.order_id for row in df.collect())


@pytest.mark.decision(1)
def test_keeps_rows_with_an_order_id_and_amount_at_least_zero(
    spark: SparkSession,
) -> None:
    df = _orders(spark, ("A1", "5.00"), ("A2", "-0.01"), (None, "3.00"), ("A3", "0.00"))
    assert _ids(clean_orders(df)) == ["A1", "A3"]


@pytest.mark.decision(2)
def test_the_schema_columns_unchanged(spark: SparkSession) -> None:
    df = _orders(spark, ("A1", "12.34"))
    assert clean_orders(df).schema == df.schema
    assert clean_orders(df).collect() == df.collect()


@pytest.mark.decision(3, 4)
def test_no_grouping_and_no_join_one_row_in_one_row_out(spark: SparkSession) -> None:
    df = _orders(spark, ("A1", "1.00"), ("A2", "2.00"), ("A3", "3.00"))
    assert clean_orders(df).count() == 3


@pytest.mark.decision(5)
@pytest.mark.rule("R2")
def test_empty_input_returns_empty_dataframe_with_the_output_schema(
    spark: SparkSession,
) -> None:
    empty = spark.createDataFrame([], READ)
    assert clean_orders(empty).isEmpty()
    assert clean_orders(empty).schema == READ


@pytest.mark.decision(6)
def test_drops_rows_with_a_null_order_id(spark: SparkSession) -> None:
    assert _ids(clean_orders(_orders(spark, (None, "1.00"), ("A1", "1.00")))) == ["A1"]


@pytest.mark.decision(7)
def test_keeps_one_row_per_order_id_the_same_on_every_run(spark: SparkSession) -> None:
    df = _orders(spark, ("A1", "9.00"), ("A1", "4.00"), ("A2", "1.00"), ("A1", "4.00"))
    kept = clean_orders(df).orderBy("order_id").collect()
    assert [(r.order_id, r.amount) for r in kept] == [
        ("A1", Decimal("4.00")),
        ("A2", Decimal("1.00")),
    ]


@pytest.mark.decision(8)
def test_drops_rows_where_amount_is_negative(spark: SparkSession) -> None:
    df = _orders(spark, ("A1", "-3.00"), ("A2", "0.00"))
    assert _ids(clean_orders(df)) == ["A2"]


@pytest.mark.decision(9)
def test_extra_columns_are_dropped(spark: SparkSession) -> None:
    df = _orders(spark, ("A1", "1.00")).withColumn("comment", F.lit("upstream"))
    assert clean_orders(df).columns == ORDERS_SCHEMA.fieldNames()


_rows = st.lists(
    st.tuples(
        st.sampled_from(["A1", "A2", "A3", "A4", None]),
        st.decimals(min_value="-100", max_value="100", places=2),
    ),
    max_size=8,
)


@pytest.mark.invariant("clean_orders.INV1")
@settings(max_examples=25, deadline=None)
@given(rows=_rows)
def test_every_order_id_with_an_amount_at_least_zero_appears(
    spark: SparkSession, rows: list[tuple[str | None, Decimal]]
) -> None:
    df = _orders(spark, *((oid, str(amount)) for oid, amount in rows))
    valid = {oid for oid, amount in rows if oid is not None and amount >= 0}
    assert _ids(clean_orders(df)) == sorted(valid)


@pytest.mark.example("US-002-negative-amount")
def test_example_negative_amount(example: Example) -> None:
    actual, expected = example("clean_orders", "US-002-negative-amount", clean_orders)
    assert actual == expected


@pytest.mark.example("US-002-zero-amount-kept")
def test_example_zero_amount_kept(example: Example) -> None:
    actual, expected = example("clean_orders", "US-002-zero-amount-kept", clean_orders)
    assert actual == expected
