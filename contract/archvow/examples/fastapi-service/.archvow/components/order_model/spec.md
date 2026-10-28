<!-- archvow spec of order_model - the task the AI or the developer implements. Generated from spec.vow in this folder, do not edit. -->

# Task: implement `order_model` (model) - US-001

An order: id int, customer str, amount Decimal >= 0, status pending | paid | cancelled

## Must never happen

- Safety questions still open (archvow show order_model) - do not invent hazards or guards; say in your answer that they are open:
  S1. Wrong input (invalid values, out of range, an illegal state change)
- Layer `p1_domain`: never read or write anything (no database, files or network).
- Layer `p1_domain`: never know about HTTP (FastAPI, status codes, requests).

## Personal data

Columns and their class (GDPR). Define exactly these; a new column is classified first (`archvow column set`), or the build fails (GDPR001):
- `amount`: none
- `customer`: personal
- `id`: none
- `status`: none

## Create exactly

- File: `src/orders_api/p1_domain/order_model.py` - no other new file except its test.
- Public API (nothing else may be public; helpers start with `_`):
  - `class Order`
- May not import other components (foundations in p0_common excepted).
- Used by: `orders_repository`, `create_order`, `get_order`, `orders_router` - their tests must stay green.

## File header

Start the file with this docstring. Fill in the `<...>` values: the person responsible, the AI assistant you are (or `none`), today's date.

```python
"""<One-line summary of what this module does>.

@component  order_model (model, p1_domain)
@spec       US-001
@project    orders_api
@author     <your name>
@assisted   <copilot | none>
@date       <YYYY-MM-DD>
"""
```

## Tests to write

- File: `tests/p1_domain/test_order_model.py` - required, the build fails without it (ARCH017).
- Kind: **unit** - Pure and fast: plain Python objects, no database, no network, no app. Runs on every commit.
- One test per decision and per rule below, named after it, e.g. `test_empty_input_returns_empty_dataframe`, and marked with it: `@pytest.mark.decision(3)`, `@pytest.mark.rule("R2")` (ARCH038).

## Decisions made (implement exactly, do not reinterpret)

1. Which fields are required, and which are optional (with which default)?
   **All fields required**
2. Which values are valid for each field (ranges, formats, lengths, allowed states)?
   **amount >= 0 with 2 decimals; status pending, paid or cancelled**
3. Which fields must never appear in a response or a log (passwords, internal ids, personal data)?
   **None: the customer id is returned only to its owner**

## Assumptions

Anything this spec does not define is not yours to decide. Ask before coding. If you still had to assume something, list it in your answer and record it with `archvow assumption add order_model "<assumption>"`. Until a person accepts or rejects it, CI fails (ARCH034).

## Definition of done

- `archvow check` reports no errors for this component.
- Checks on this code: ARCH, STYLE, FASTAPI, GDPR, TST - what each one checks: `.archvow/spec.md` (Checks).
- `ruff format .` run, then `ruff check .` and `pytest` pass.
- Every invariant above is tested.
- The tests of the components that use it still pass.
- You did not edit `.archvow/`, `AGENTS.md`, `ruff.toml`, CI files or other components' tests, and added no `# noqa` / `# type: ignore` / suppressions.
- Working code that breaks these rules is not done.
