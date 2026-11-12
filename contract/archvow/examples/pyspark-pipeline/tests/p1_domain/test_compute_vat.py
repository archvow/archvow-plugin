"""Every order gets its VAT: 20 % of the amount, rounded half-up to 2 decimals."""

from datetime import date
from decimal import ROUND_HALF_UP, Decimal

import pytest
from hypothesis import given, settings
from hypothesis import strategies as st
from pyspark.sql import DataFrame, SparkSession
from pyspark.sql import functions as F
from pyspark.sql import types as T

from conftest import Example
from my_pipeline.p1_domain.compute_vat import compute_vat
from my_pipeline.p1_domain.orders_schema import ORDERS_SCHEMA

DAY = date(2026, 10, 1)


def _orders(spark: SparkSession, *amounts: str) -> DataFrame:
    data = [(f"A{n}", "C1", Decimal(a), DAY) for n, a in enumerate(amounts, 1)]
    return spark.createDataFrame(data, ORDERS_SCHEMA)


def _vat(df: DataFrame) -> list[Decimal]:
    return [row.vat for row in compute_vat(df).orderBy("order_id").collect()]


@pytest.mark.decision(1)
@pytest.mark.invariant("compute_vat.INV2")
def test_keeps_all_rows(spark: SparkSession) -> None:
    assert compute_vat(_orders(spark, "1.00", "0.00", "3.50")).count() == 3


@pytest.mark.decision(2)
def test_adds_vat_decimal_18_2_not_null(spark: SparkSession) -> None:
    result = compute_vat(_orders(spark, "12.50"))
    assert result.columns == [*ORDERS_SCHEMA.fieldNames(), "vat"]
    assert result.schema["vat"].dataType == T.DecimalType(18, 2)
    assert _vat(_orders(spark, "12.50")) == [Decimal("2.50")]


@pytest.mark.decision(3, 4)
def test_no_grouping_and_no_join_one_row_in_one_row_out(spark: SparkSession) -> None:
    assert compute_vat(_orders(spark, "1.00", "1.00")).count() == 2


@pytest.mark.decision(5)
@pytest.mark.rule("R2")
def test_empty_input_returns_empty_dataframe_with_the_output_schema(
    spark: SparkSession,
) -> None:
    result = compute_vat(spark.createDataFrame([], ORDERS_SCHEMA))
    assert result.isEmpty() and "vat" in result.columns


@pytest.mark.decision(6, 7, 8)
def test_relies_on_clean_orders_for_keys_and_amounts(spark: SparkSession) -> None:
    # nulls, duplicates and negative amounts are removed by clean_orders first:
    # compute_vat changes no row, it only adds the column
    df = _orders(spark, "2.00", "2.00")
    assert compute_vat(df).drop("vat").collect() == df.collect()


@pytest.mark.decision(9)
def test_extra_columns_are_kept_unchanged(spark: SparkSession) -> None:
    df = _orders(spark, "5.00").withColumn("comment", F.lit("kept"))
    assert compute_vat(df).first().comment == "kept"


@pytest.mark.rule("R1")
def test_vat_is_rounded_half_up_to_2_decimals(spark: SparkSession) -> None:
    # 0.03 * 0.2 = 0.006 -> 0.01; 0.02 * 0.2 = 0.004 -> 0.00
    assert _vat(_orders(spark, "0.03", "0.02")) == [Decimal("0.01"), Decimal("0.00")]


@pytest.mark.invariant("compute_vat.INV1")
@settings(max_examples=25, deadline=None)
@given(amounts=st.lists(st.decimals(min_value="0", max_value="1000000", places=2)))
def test_vat_is_amount_times_0_2_for_every_row(
    spark: SparkSession, amounts: list[Decimal]
) -> None:
    expected = [
        (a * Decimal("0.2")).quantize(Decimal("0.01"), ROUND_HALF_UP) for a in amounts
    ]
    df = _orders(spark, *(str(a) for a in amounts))
    rows = compute_vat(df).collect()
    assert sorted((r.order_id, r.vat) for r in rows) == sorted(
        (f"A{n}", vat) for n, vat in enumerate(expected, 1)
    )


@pytest.mark.example("US-002-vat-20")
def test_example_vat_20(example: Example) -> None:
    actual, expected = example("compute_vat", "US-002-vat-20", compute_vat)
    assert actual == expected
