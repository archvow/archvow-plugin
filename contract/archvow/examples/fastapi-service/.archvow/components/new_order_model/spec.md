<!-- archvow spec of new_order_model - the task the AI or the developer implements. Generated from spec.vow in this folder, do not edit. -->

# Task: implement `new_order_model` (model) - US-001

What a customer sends to create an order: amount Decimal > 0

## Must never happen

- Safety questions still open (archvow show new_order_model) - do not invent hazards or guards; say in your answer that they are open:
  S1. Wrong input (invalid values, out of range, an illegal state change)
  S2. Data exposed (a user sees or changes data that is not theirs; secrets or personal data in a response or a log)
- Layer `p1_domain`: never read or write anything (no database, files or network).
- Layer `p1_domain`: never know about HTTP (FastAPI, status codes, requests).

## Personal data

Columns and their class (personal data). Define exactly these; a new column is classified first (`archvow column set`), or the build fails (PRIV001):
- `amount`: none

## Create exactly

- File: `src/orders_api/p1_domain/new_order_model.py` - no other new file except its test.
- Public API (nothing else may be public; helpers start with `_`):
  - `class NewOrder`
- May not import other components (foundations in p0_common excepted).
- Used by: `create_order`, `orders_router` - their tests must stay green.

## File header

Start the file with this docstring. Fill in the `<...>` values: the person responsible, the AI assistant you are (or `none`), today's date.

```python
"""<One-line summary of what this module does>.

@component  new_order_model (model, p1_domain)
@spec       US-001
@project    orders_api
@author     <your name>
@assisted   <copilot | none>
@date       <YYYY-MM-DD>
"""
```

## Tests to write

- File: `tests/p1_domain/test_new_order_model.py` - required, the build fails without it (ARCH017).
- Kind: **unit** - Pure and fast: plain Python objects, no database, no network, no app. Runs on every commit.
- One test per decision and per rule below, named after it, e.g. `test_empty_input_returns_empty_dataframe`, and marked with it: `@pytest.mark.decision(3)`, `@pytest.mark.rule("R2")` (ARCH038).

## Decisions made (implement exactly, do not reinterpret)

1. Which fields are required, and which are optional (with which default)?
   **amount required**
2. Which values are valid for each field (ranges, formats, lengths, allowed states)?
   **amount > 0 with at most 2 decimals**
3. Which fields must never appear in a response or a log (passwords, internal ids, personal data)?
   **None**

## Assumptions

Anything this spec does not define is not yours to decide. Ask before coding. If you still had to assume something, list it in your answer and record it with `archvow assumption add new_order_model "<assumption>"`. Until a person accepts or rejects it, CI fails (ARCH034).

## Definition of done

- `archvow check` reports no errors for this component.
- Checks on this code: ARCH, STYLE, FASTAPI, PRIV, GDPR, TST - what each one checks: `.archvow/spec.md` (Checks).
- `ruff format .` run, then `ruff check .` and `pytest` pass.
- Every invariant above is tested.
- The tests of the components that use it still pass.
- You did not edit `.archvow/`, `AGENTS.md`, `ruff.toml`, CI files or other components' tests, and added no `# noqa` / `# type: ignore` / suppressions.
- Working code that breaks these rules is not done.
