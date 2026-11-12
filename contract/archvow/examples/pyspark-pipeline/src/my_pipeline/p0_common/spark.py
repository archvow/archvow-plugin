"""The SparkSession factory, with the project's Spark settings.

@component  spark (foundation, p0_common)
@spec       -
@project    my_pipeline
@author     A. Developer
@assisted   none
@date       2026-11-11
"""

from pyspark.sql import SparkSession


def get_spark_session(app_name: str, master: str | None = None) -> SparkSession:
    """The session every job uses: spark.sql.session.timeZone=UTC, nothing else."""
    builder = SparkSession.builder.appName(app_name)
    if master:
        builder = builder.master(master)
    if (master or "").startswith("local"):  # a laptop on a VPN: bind locally
        builder = builder.config("spark.driver.bindAddress", "127.0.0.1")
        builder = builder.config("spark.driver.host", "127.0.0.1")
    return builder.config("spark.sql.session.timeZone", "UTC").getOrCreate()
