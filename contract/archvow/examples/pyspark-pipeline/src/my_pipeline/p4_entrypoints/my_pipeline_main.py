"""Command line of the pipeline: --input-path PATH, --output-path PATH.

@component  my_pipeline_main (entrypoint, p4_entrypoints)
@spec       US-003
@project    my_pipeline
@author     A. Developer
@assisted   none
@date       2026-11-11
"""

import argparse
import logging
import sys
import uuid
from collections.abc import Sequence

from my_pipeline.p0_common.config import JobConfig
from my_pipeline.p0_common.logging_setup import setup_logging
from my_pipeline.p0_common.spark import get_spark_session
from my_pipeline.p3_jobs.my_pipeline_job import run_my_pipeline

logger = logging.getLogger(__name__)
_USAGE = "usage: my_pipeline --input-path PATH --output-path PATH"


def main(argv: Sequence[str] | None = None) -> int:
    """Return 0 on success, 1 when the job fails, 2 for invalid arguments."""
    setup_logging()
    parser = argparse.ArgumentParser(prog="my_pipeline", usage=_USAGE[7:])
    parser.add_argument("--input-path", required=True, help="the input folder")
    parser.add_argument("--output-path", required=True, help="the output folder")
    try:
        args = parser.parse_args(argv)
        config = JobConfig(args.input_path, args.output_path)
    except (SystemExit, ValueError) as error:
        print(f"{_USAGE} ({error})", file=sys.stderr)
        return 2
    run = uuid.uuid4().hex[:8]
    logger.info("run %s: no run date, the job processes the input it is given", run)
    spark = get_spark_session("my_pipeline")
    try:
        run_my_pipeline(spark, config)
    except Exception:
        logger.exception("run %s failed", run)
        return 1
    finally:
        spark.stop()
    logger.info("run %s succeeded", run)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
