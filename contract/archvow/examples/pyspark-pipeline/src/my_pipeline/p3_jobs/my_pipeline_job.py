"""Read the orders, clean them, compute their VAT, write the result.

@component  my_pipeline_job (job, p3_jobs)
@spec       US-003
@project    my_pipeline
@author     A. Developer
@assisted   none
@date       2026-11-11
"""

import logging
import time

from pyspark.sql import SparkSession

from my_pipeline.p0_common.config import JobConfig
from my_pipeline.p1_domain.clean_orders import clean_orders
from my_pipeline.p1_domain.compute_vat import compute_vat
from my_pipeline.p2_io.orders_reader import read_orders
from my_pipeline.p2_io.vat_writer import write_vat

logger = logging.getLogger(__name__)


def run_my_pipeline(spark: SparkSession, config: JobConfig) -> None:
    """Run once on the input it is given; any failure propagates (no retry here)."""
    started = time.monotonic()
    logger.info("started: input %s, output %s", config.input_path, config.output_path)
    orders = read_orders(spark, config.input_path).cache()
    result = compute_vat(clean_orders(orders)).cache()
    read, kept = orders.count(), result.count()  # read once, kept in memory
    logger.info("orders read %d, kept %d", read, kept)
    write_vat(result, config.output_path)
    result.unpersist()
    orders.unpersist()
    logger.info("ended: %d rows written in %.1f s", kept, time.monotonic() - started)
