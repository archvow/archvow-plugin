"""Every session is in UTC."""

import pytest
from pyspark.sql import SparkSession


@pytest.mark.decision(1)
def test_every_session_is_in_utc(spark: SparkSession) -> None:
    assert spark.conf.get("spark.sql.session.timeZone") == "UTC"
