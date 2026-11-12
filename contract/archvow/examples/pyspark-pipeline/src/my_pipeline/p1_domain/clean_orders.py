"""Keep only the valid orders, one row per order_id.

@component  clean_orders (transformation, p1_domain)
@spec       US-002
@project    my_pipeline
@author     A. Developer
@assisted   none
@date       2026-11-11
"""

from pyspark.sql import DataFrame, Window
from pyspark.sql import functions as F

_COLUMNS = ["order_id", "customer_id", "amount", "order_date"]


def clean_orders(df: DataFrame) -> DataFrame:
    """The rows with an order_id and an amount >= 0, one per order_id, schema columns.

    Of two rows with the same order_id it keeps the same one on every run (the
    smallest amount, customer_id, order_date), so a re-run gives the same output.
    """
    valid = df.where(F.col("order_id").isNotNull() & (F.col("amount") >= 0))
    first = (
        Window.partitionBy("order_id")
        .orderBy(*(F.col(name).asc_nulls_last() for name in _COLUMNS[1:]))
        .rowsBetween(Window.unboundedPreceding, Window.currentRow)
    )
    return (
        valid.withColumn("_rank", F.row_number().over(first))
        .where(F.col("_rank") == 1)
        .select(*_COLUMNS)
    )
