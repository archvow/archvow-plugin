"""Add the VAT of each order: 20 % of its amount.

@component  compute_vat (transformation, p1_domain)
@spec       US-002
@project    my_pipeline
@author     A. Developer
@assisted   none
@date       2026-11-11
"""

from pyspark.sql import DataFrame
from pyspark.sql import functions as F
from pyspark.sql import types as T

_RATE = "0.20"  # as a decimal, never a float


def compute_vat(df: DataFrame) -> DataFrame:
    """Every row unchanged, plus vat = amount * 0.2, rounded half-up to 2 decimals."""
    rate = F.lit(_RATE).cast(T.DecimalType(3, 2))
    vat = F.round(F.col("amount") * rate, 2).cast(T.DecimalType(18, 2))
    return df.withColumn("vat", vat)
