"""Read the orders from CSV files with a header, enforcing ORDERS_SCHEMA.

@component  orders_reader (reader, p2_io)
@spec       US-001
@project    my_pipeline
@author     A. Developer
@assisted   none
@date       2026-11-11
"""

from pyspark.errors import AnalysisException
from pyspark.sql import DataFrame, SparkSession

from my_pipeline.p1_domain.orders_schema import ORDERS_SCHEMA


def read_orders(spark: SparkSession, path: str) -> DataFrame:
    """Every CSV file under path; a missing or empty input, or a malformed row, fails.

    Raises FileNotFoundError when the path does not exist or holds no order: a
    missing input is an operations problem, never an empty run.
    """
    try:
        orders = spark.read.csv(
            path, header=True, schema=ORDERS_SCHEMA, mode="FAILFAST"
        )
    except AnalysisException as error:
        msg = f"no orders at {path}: the input was not delivered"
        raise FileNotFoundError(msg) from error
    if orders.isEmpty():
        msg = f"no orders at {path}: the input is empty"
        raise FileNotFoundError(msg)
    return orders
