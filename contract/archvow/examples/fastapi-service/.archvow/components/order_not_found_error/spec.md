<!-- archvow spec of order_not_found_error - the task the AI or the developer implements. Generated from spec.vow in this folder, do not edit. -->

# Task: implement `order_not_found_error` (error) - US-001

The order does not exist, or belongs to another customer

## Must never happen

- Nothing: the safety analysis found nothing this component must guard against.
- Layer `p1_domain`: never read or write anything (no database, files or network).
- Layer `p1_domain`: never know about HTTP (FastAPI, status codes, requests).

## Create exactly

- File: `src/orders_api/p1_domain/order_not_found_error.py` - no other new file except its test.
- Public API (nothing else may be public; helpers start with `_`):
  - `class OrderNotFoundError`
- May not import other components (foundations in p0_common excepted).
- Used by: `get_order`, `main` - their tests must stay green.

## File header

Start the file with this docstring. Fill in the `<...>` values: the person responsible, the AI assistant you are (or `none`), today's date.

```python
"""<One-line summary of what this module does>.

@component  order_not_found_error (error, p1_domain)
@spec       US-001
@project    orders_api
@author     <your name>
@assisted   <copilot | none>
@date       <YYYY-MM-DD>
"""
```

## Tests to write

- File: `tests/p1_domain/test_order_not_found_error.py` - required, the build fails without it (ARCH017).
- Kind: **unit** - Pure and fast: plain Python objects, no database, no network, no app. Runs on every commit.
- One test per decision and per rule below, named after it, e.g. `test_empty_input_returns_empty_dataframe`, and marked with it: `@pytest.mark.decision(3)`, `@pytest.mark.rule("R2")` (ARCH038).

## Decisions made (implement exactly, do not reinterpret)

1. Which HTTP status does the app answer with it (404, 409, 422...)?
   **404 Not Found**
2. What does its message say - and never say (internal ids, personal data, whether the resource exists)?
   **"Order not found" - never the id's owner, never whether it exists**

## Assumptions

Anything this spec does not define is not yours to decide. Ask before coding. If you still had to assume something, list it in your answer and record it with `archvow assumption add order_not_found_error "<assumption>"`. Until a person accepts or rejects it, CI fails (ARCH034).

## Definition of done

- `archvow check` reports no errors for this component.
- Checks on this code: ARCH, STYLE, FASTAPI, GDPR, TST - what each one checks: `.archvow/spec.md` (Checks).
- `ruff format .` run, then `ruff check .` and `pytest` pass.
- Every invariant above is tested.
- The tests of the components that use it still pass.
- You did not edit `.archvow/`, `AGENTS.md`, `ruff.toml`, CI files or other components' tests, and added no `# noqa` / `# type: ignore` / suppressions.
- Working code that breaks these rules is not done.
