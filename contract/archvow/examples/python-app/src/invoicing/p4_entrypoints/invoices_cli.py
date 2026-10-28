"""The command line: pay NUMBER.

@component  invoices_cli (cli, p4_entrypoints)
@spec       US-001
@project    invoicing
@author     A. Developer
@assisted   claude-code
@date       2026-10-21
"""

from __future__ import annotations

import argparse
import sys
from collections.abc import Sequence
from pathlib import Path

from invoicing.p0_common.config import load_config
from invoicing.p0_common.logging_setup import setup_logging
from invoicing.p1_domain.invoice_not_found_error import InvoiceNotFoundError
from invoicing.p3_services.mark_paid import mark_paid


def main(argv: Sequence[str] | None = None) -> int:
    """Run the command: 0 ok, 1 error, 2 bad arguments (argparse), 3 not found."""
    parser = argparse.ArgumentParser(prog="invoices", description="Mark invoices paid.")
    parser.add_argument(
        "--data-dir", help="the folder of the invoices (INVOICING_DATA_DIR)"
    )
    commands = parser.add_subparsers(dest="command", required=True)
    pay = commands.add_parser("pay", help="mark an invoice paid")
    pay.add_argument("number")
    args = parser.parse_args(argv)
    try:
        given = {"INVOICING_DATA_DIR": args.data_dir} if args.data_dir else None
        config = load_config(given)
        setup_logging(config.log_level)
        invoice = mark_paid(Path(config.data_dir), args.number)
    except InvoiceNotFoundError as error:
        print(error, file=sys.stderr)
        return 3
    except ValueError as error:
        print(error, file=sys.stderr)
        return 1
    print(f"{invoice.number} {invoice.customer} {invoice.amount} {invoice.status}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
