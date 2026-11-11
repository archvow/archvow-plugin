<!-- archvow spec of orders_reader - the task the AI or the developer implements. Generated from spec.vow in this folder, do not edit. -->

# Task: implement `orders_reader` (reader) - US-001

Read the orders from CSV files with a header at the given path and enforce ORDERS_SCHEMA

## Must never happen

- Safety questions still open (archvow show orders_reader) - do not invent hazards or guards; say in your answer that they are open:
  S1. Input missing, empty or late
  S2. Too many rows (duplicates, a join that multiplies rows, a file delivered twice)
  S3. Wrong values (nulls, negatives, out of range, wrong types, a column that changes type or disappears)
  S4. Old data treated as today's (the job reads the wrong date)
- Layer `p2_io`: never transform business data beyond enforcing the schema.

## Create exactly

- File: `src/my_pipeline/p2_io/orders_reader.py` - no other new file except its test.
- Public API (nothing else may be public; helpers start with `_`):
  - `def read_orders(spark: SparkSession, path: str) -> DataFrame`
- May use only these components:
  - `my_pipeline.p1_domain.orders_schema` (schema)
- Used by: `my_pipeline_job` - their tests must stay green.

## File header

Start the file with this docstring. Fill in the `<...>` values: the person responsible, the AI assistant you are (or `none`), today's date.

```python
"""<One-line summary of what this module does>.

@component  orders_reader (reader, p2_io)
@spec       US-001
@project    my_pipeline
@author     <your name>
@assisted   <claude-code | none>
@date       <YYYY-MM-DD>
"""
```

## Tests to write

- File: `tests/p2_io/test_orders_reader.py` - required, the build fails without it (ARCH017).
- Kind: **integration** - Real I/O in a temporary folder (files, tables, a local Derby metastore), marked @pytest.mark.integration. Never a real metastore, bucket or database.
- One test per decision and per rule below, named after it, e.g. `test_empty_input_returns_empty_dataframe`, and marked with it: `@pytest.mark.decision(3)`, `@pytest.mark.rule("R2")` (ARCH038).

## Decisions made (implement exactly, do not reinterpret)

1. The path does not exist or is empty: fail or return an empty DataFrame?
   **Fail with a clear error: a missing input is an operations problem**
2. Records not matching the schema: fail, drop, or quarantine?
   **Fail the job (FAILFAST): a malformed file must be fixed upstream**
3. Which partitions / dates are read, and what if the latest one is missing?
   **Everything under the given path, no partitions**

## Assumptions

Anything this spec does not define is not yours to decide. Ask before coding. If you still had to assume something, list it in your answer and record it with `archvow assumption add orders_reader "<assumption>"`. Until a person accepts or rejects it, CI fails (ARCH034).

## Definition of done

- `archvow check` reports no errors for this component.
- Checks on this code: ARCH, STYLE, SPARK, PRIV, GDPR, TST - what each one checks: `.archvow/spec.md` (Checks).
- `ruff format .` run, then `ruff check .` and `pytest` pass.
- Every invariant above is tested.
- The tests of the components that use it still pass.
- You did not edit `.archvow/`, `AGENTS.md`, `ruff.toml`, CI files or other components' tests, and added no `# noqa` / `# type: ignore` / suppressions.
- Working code that breaks these rules is not done.
