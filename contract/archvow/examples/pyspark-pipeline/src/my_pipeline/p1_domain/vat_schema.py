"""The output orders with their VAT, as the writer enforces them.

@component  vat_schema (schema, p1_domain)
@spec       US-003
@project    my_pipeline
@author     A. Developer
@assisted   none
@date       2026-11-11
"""

from pyspark.sql import types as T

VAT_SCHEMA: T.StructType = T.StructType(
    [
        T.StructField("order_id", T.StringType(), nullable=False),
        T.StructField("customer_id", T.StringType(), nullable=True),
        T.StructField("amount", T.DecimalType(18, 2), nullable=False),
        T.StructField("order_date", T.DateType(), nullable=True),
        T.StructField("vat", T.DecimalType(18, 2), nullable=False),
    ]
)
