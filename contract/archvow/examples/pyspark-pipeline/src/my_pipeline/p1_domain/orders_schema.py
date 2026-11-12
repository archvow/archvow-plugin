"""The input orders, as the reader enforces them.

@component  orders_schema (schema, p1_domain)
@spec       US-001
@project    my_pipeline
@author     A. Developer
@assisted   none
@date       2026-11-11
"""

from pyspark.sql import types as T

ORDERS_SCHEMA: T.StructType = T.StructType(
    [
        T.StructField("order_id", T.StringType(), nullable=False),
        T.StructField("customer_id", T.StringType(), nullable=True),
        T.StructField("amount", T.DecimalType(18, 2), nullable=False),
        T.StructField("order_date", T.DateType(), nullable=True),
    ]
)
