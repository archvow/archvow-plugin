"""Write the orders with their VAT as parquet, all or nothing.

@component  vat_writer (writer, p2_io)
@spec       US-003
@project    my_pipeline
@author     A. Developer
@assisted   none
@date       2026-11-11
"""

from pyspark.sql import DataFrame
from pyspark.sql import functions as F

from my_pipeline.p1_domain.vat_schema import VAT_SCHEMA


def write_vat(df: DataFrame, path: str) -> None:
    """Write VAT_SCHEMA's columns as parquet to <path>_tmp, then rename it to path.

    An existing output is replaced; a failure while writing leaves the previous
    output or none, never a half-written one.
    """
    spark = df.sparkSession
    hadoop = spark.sparkContext._jvm.org.apache.hadoop.fs
    target, temporary = hadoop.Path(path), hadoop.Path(f"{path}_tmp")
    fs = target.getFileSystem(spark.sparkContext._jsc.hadoopConfiguration())
    columns = [F.col(field.name).cast(field.dataType) for field in VAT_SCHEMA]
    df.select(*columns).write.mode("overwrite").parquet(f"{path}_tmp")
    # archvow: enforces[vat_writer.INV1] - complete in one rename, or absent
    fs.delete(target, True)
    if not fs.rename(temporary, target):
        msg = f"could not move {path}_tmp to {path}"
        raise OSError(msg)
