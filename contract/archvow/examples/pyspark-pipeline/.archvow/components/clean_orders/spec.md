<!-- archvow spec of clean_orders - the task the AI or the developer implements. Generated from spec.vow in this folder, do not edit. -->

# Task: implement `clean_orders` (transformation) - US-002

Keep only the valid rows: order_id not null and amount >= 0

## Tasks

This card is cut into 3 tasks: implement one at a time, in order; after each one, `archvow check` and `pytest` pass. A task is done when the code exists and each of its items has a marked test.

- **T1** the file and its public API, decisions 1-4
- **T2** decisions 5-9
- **T3** examples US-002-negative-amount, US-002-zero-amount-kept, clean_orders.INV1

## Must never happen

- **clean_orders.H1** (high) A valid input row is dropped
  - prevented by **clean_orders.INV1**: every input row with an order_id and amount >= 0 appears in the output
  - tested by: a Hypothesis @given test decorated with @pytest.mark.invariant("clean_orders.INV1") (it searches for counterexamples, not one example)
- Safety questions still open (archvow show clean_orders) - do not invent hazards or guards; say in your answer that they are open:
  S1. Input missing, empty or late
  S2. Too many rows (duplicates, a join that multiplies rows, a file delivered twice)
  S4. Wrong values (nulls, negatives, out of range, wrong types, a column that changes type or disappears)
  S5. Data exposed (personal or secret data written where it must not be)
- Layer `p1_domain`: never read or write data (no spark.read, .write, open()).
- Layer `p1_domain`: never trigger Spark actions (collect, count, show, toPandas, cache...).
- Layer `p1_domain`: never create a SparkSession.

## Create exactly

- File: `src/my_pipeline/p1_domain/clean_orders.py` - no other new file except its test.
- Public API (nothing else may be public; helpers start with `_`):
  - `def clean_orders(df: DataFrame) -> DataFrame`
- May not import other components (foundations in p0_common excepted).
- Used by: `my_pipeline_job` - their tests must stay green.

## File header

Start the file with this docstring. Fill in the `<...>` values: the person responsible, the AI assistant you are (or `none`), today's date.

```python
"""<One-line summary of what this module does>.

@component  clean_orders (transformation, p1_domain)
@spec       US-002
@project    my_pipeline
@author     <your name>
@assisted   <claude-code | none>
@date       <YYYY-MM-DD>
"""
```

## Tests to write

- File: `tests/p1_domain/test_clean_orders.py` - required, the build fails without it (ARCH017).
- Kind: **unit** - Pure and fast: small DataFrames built in memory, no files, tables or network. Runs on every commit.
- One test per decision and per rule below, named after it, e.g. `test_empty_input_returns_empty_dataframe`, and marked with it: `@pytest.mark.decision(3)`, `@pytest.mark.rule("R2")` (ARCH038).
- The test of clean_orders.INV1, as described under *Must never happen*.

## Examples (reproduce exactly)

Given by the business team. For each one, a test reads `input/`, runs the component and compares what it gives with `expected/`. Never change these files: archvow compares them with the ones a person recorded (ARCH055); an example that looks wrong is a question for a person.

The test also writes what the component gave to `actual/`, with the same file names and format as `expected/` (not locked, not committed): people see where it differs. Open every file with `encoding="utf-8"` (and `newline=""` for CSV) and compare what you read (rows, JSON objects), not bytes. Write each example's test before the code it checks: it fails until the code exists, and is never changed to pass.

- **US-002-negative-amount** (US-002): `tests/examples/clean_orders/US-002-negative-amount/` - its test is marked `@pytest.mark.example("US-002-negative-amount")`

  ```gherkin
  Feature: clean the orders
    Scenario: an order with a negative amount is dropped
      Given the orders A1 (5.00) and A2 (-3.00)
      When clean_orders runs
      Then only A1 is kept
  ```

  `input/orders.csv`

  | order_id | customer_id | amount | order_date |
  |---|---|---|---|
  | A1 | C1 | 5.00 | 2026-10-01 |
  | A2 | C2 | -3.00 | 2026-10-01 |

  `expected/orders.csv`

  | order_id | customer_id | amount | order_date |
  |---|---|---|---|
  | A1 | C1 | 5.00 | 2026-10-01 |
- **US-002-zero-amount-kept** (US-002) - rule clean_orders.INV1: `tests/examples/clean_orders/US-002-zero-amount-kept/` - its test is marked `@pytest.mark.example("US-002-zero-amount-kept")`

  ```gherkin
  Feature: clean the orders
    Rule: clean_orders.INV1
      Scenario: an order of 0.00 is kept
        Given the orders A1 (5.00) and A3 (0.00, a free sample)
        When clean_orders runs
        Then A1 and A3 are both kept
  ```

  `input/orders.csv`

  | order_id | customer_id | amount | order_date |
  |---|---|---|---|
  | A1 | C1 | 5.00 | 2026-10-01 |
  | A3 | C1 | 0.00 | 2026-10-01 |

  `expected/orders.csv`

  | order_id | customer_id | amount | order_date |
  |---|---|---|---|
  | A1 | C1 | 5.00 | 2026-10-01 |
  | A3 | C1 | 0.00 | 2026-10-01 |

## Decisions made (implement exactly, do not reinterpret)

1. Which rows does it keep (filter conditions), or all of them?
   **Rows where order_id is not null and amount >= 0**
2. Which output columns does it produce, and how is each one computed (type, nullable)?
   **The schema columns, unchanged**
3. Does it group rows? By which keys, with which aggregates - or no grouping?
   **No grouping**
4. Does it join another dataset? Which one, on which keys, which join type, what if a key has no match - or no join? (a join needs a second input: archvow component edit <component> --signature "(left: DataFrame, right: DataFrame) -> DataFrame")
   **No join**
5. Empty input: return an empty DataFrame with the output schema, or fail?
   **Return an empty DataFrame with the output schema**
6. Null values in key columns: drop, keep, or fail?
   **Drop rows with a null order_id**
7. Duplicate keys in the input: keep first, keep last, aggregate, or fail?
   **Keep one row per order_id**
8. Negative or out-of-range values: keep, clamp, drop, or fail?
   **Drop rows where amount is negative**
9. Extra input columns not in the schema: drop silently or fail?
   **Drop them: output only the schema columns**

## Assumptions

Anything this spec does not define is not yours to decide. Ask before coding. If you still had to assume something, list it in your answer and record it with `archvow assumption add clean_orders "<assumption>"`. Until a person accepts or rejects it, CI fails (ARCH034).

## Definition of done

- `archvow check` reports no errors for this component.
- Checks on this code: ARCH, STYLE, SPARK, PRIV, GDPR, TST - what each one checks: `.archvow/spec.md` (Checks).
- `ruff format .` run, then `ruff check .` and `pytest` pass.
- Every invariant above is tested.
- The tests of the components that use it still pass.
- Every example above has its marked test, and its files are the ones recorded.
- You did not edit `.archvow/`, `AGENTS.md`, `ruff.toml`, CI files or other components' tests, and added no `# noqa` / `# type: ignore` / suppressions.
- Working code that breaks these rules is not done.
