<!-- archvow spec of orders_schema - the task the AI or the developer implements. Generated from spec.vow in this folder, do not edit. -->

# Task: implement `orders_schema` (schema) - US-001

Input orders: order_id string, customer_id string, amount decimal(18,2), order_date date

## Must never happen

- Safety questions still open (archvow show orders_schema) - do not invent hazards or guards; say in your answer that they are open:
  S1. Wrong values (nulls, negatives, out of range, wrong types, a column that changes type or disappears)
- Layer `p1_domain`: never read or write data (no spark.read, .write, open()).
- Layer `p1_domain`: never trigger Spark actions (collect, count, show, toPandas, cache...).
- Layer `p1_domain`: never create a SparkSession.

## Personal data

Columns and their class (personal data). Define exactly these; a new column is classified first (`archvow column set`), or the build fails (PRIV001):
- `amount`: none
- `customer_id`: none
- `order_date`: none
- `order_id`: none

## Create exactly

- File: `src/my_pipeline/p1_domain/orders_schema.py` - no other new file except its test.
- Public API (nothing else may be public; helpers start with `_`):
  - `ORDERS_SCHEMA: T.StructType`
- May not import other components (foundations in p0_common excepted).
- Used by: `orders_reader` - their tests must stay green.

## File header

Start the file with this docstring. Fill in the `<...>` values: the person responsible, the AI assistant you are (or `none`), today's date.

```python
"""<One-line summary of what this module does>.

@component  orders_schema (schema, p1_domain)
@spec       US-001
@project    my_pipeline
@author     <your name>
@assisted   <claude-code | none>
@date       <YYYY-MM-DD>
"""
```

## Tests to write

- File: `tests/p1_domain/test_orders_schema.py` - required, the build fails without it (ARCH017).
- Kind: **unit** - Pure and fast: small DataFrames built in memory, no files, tables or network. Runs on every commit.
- One test per decision and per rule below, named after it, e.g. `test_empty_input_returns_empty_dataframe`, and marked with it: `@pytest.mark.decision(3)`, `@pytest.mark.rule("R2")` (ARCH038).

## Decisions made (implement exactly, do not reinterpret)

1. Which columns are nullable, and which must never be null?
   **order_id and amount are never null; customer_id may be null**
2. Which columns hold personal data, and where may they flow?
   **None: the input holds no personal data**

## Assumptions

Anything this spec does not define is not yours to decide. Ask before coding. If you still had to assume something, list it in your answer and record it with `archvow assumption add orders_schema "<assumption>"`. Until a person accepts or rejects it, CI fails (ARCH034).

## Definition of done

- `archvow check` reports no errors for this component.
- Checks on this code: ARCH, STYLE, SPARK, PRIV, GDPR, TST - what each one checks: `.archvow/spec.md` (Checks).
- `ruff format .` run, then `ruff check .` and `pytest` pass.
- Every invariant above is tested.
- The tests of the components that use it still pass.
- You did not edit `.archvow/`, `AGENTS.md`, `ruff.toml`, CI files or other components' tests, and added no `# noqa` / `# type: ignore` / suppressions.
- Working code that breaks these rules is not done.
