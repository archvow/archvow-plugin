"""The orders columns: which may be null, and no personal data."""

import pytest

from my_pipeline.p1_domain.orders_schema import ORDERS_SCHEMA

COLUMNS = ["order_id", "customer_id", "amount", "order_date"]
NULLABLE = ["customer_id", "order_date"]


@pytest.mark.decision(1)
def test_which_columns_may_be_null() -> None:
    nullable = {field.name: field.nullable for field in ORDERS_SCHEMA}
    assert [name for name, may in nullable.items() if may] == NULLABLE


@pytest.mark.decision(2)
def test_no_column_holds_personal_data() -> None:
    assert ORDERS_SCHEMA.fieldNames() == COLUMNS  # each one classified none in the spec
