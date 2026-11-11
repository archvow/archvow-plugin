<!-- archvow spec of get_order - the task the AI or the developer implements. Generated from spec.vow in this folder, do not edit. -->

# Task: implement `get_order` (service) - US-001

Return one order of the current customer

## Must never happen

- Safety questions still open (archvow show get_order) - do not invent hazards or guards; say in your answer that they are open:
  S1. A dependency down or slow, or the resource does not exist
  S5. Data exposed (a user sees or changes data that is not theirs; secrets or personal data in a response or a log)
- Layer `p3_services`: never know about HTTP (requests, status codes, HTTPException).

## Personal data

Personal columns it handles: `customer` (personal).
- Never show, print or log their values, nor collected rows (PRIV002): log counts.
- Never partition or bucket files by them (PRIV004).

## Create exactly

- File: `src/orders_api/p3_services/get_order.py` - no other new file except its test.
- Public API (nothing else may be public; helpers start with `_`):
  - `def get_order(...)`
- May use only these components:
  - `orders_api.p1_domain.order_model` (model)
  - `orders_api.p1_domain.order_not_found_error` (error)
  - `orders_api.p2_io.orders_repository` (repository)
- Used by: `orders_router` - their tests must stay green.

## File header

Start the file with this docstring. Fill in the `<...>` values: the person responsible, the AI assistant you are (or `none`), today's date.

```python
"""<One-line summary of what this module does>.

@component  get_order (service, p3_services)
@spec       US-001
@project    orders_api
@author     <your name>
@assisted   <copilot | none>
@date       <YYYY-MM-DD>
"""
```

## Tests to write

- File: `tests/p3_services/test_get_order.py` - required, the build fails without it (ARCH017).
- Kind: **integration** - Through FastAPI's TestClient or a real database in a temporary file (SQLite), marked @pytest.mark.integration. Never a shared database or a real external service: clients are tested against a fake transport (httpx.MockTransport).
- One test per decision and per rule below, named after it, e.g. `test_empty_input_returns_empty_dataframe`, and marked with it: `@pytest.mark.decision(3)`, `@pytest.mark.rule("R2")` (ARCH038).

## Decisions made (implement exactly, do not reinterpret)

1. What does it do, step by step (read, check, change, save, call)?
   **Find the order; check it belongs to the customer; return it**
2. Something it needs does not exist: which domain error?
   **OrderNotFoundError - also when it belongs to someone else, so ids reveal nothing**
3. Called twice with the same input (a client retry): same result and no duplicate effect (idempotent), or a duplicate error?
   **Same result: it only reads**
4. Who may perform it, and is that checked here or in the router?
   **The owner only; checked here**
5. What must be all or nothing (one transaction), and what happens if a call to another service fails in the middle?
   **Nothing to undo: it only reads**

## Assumptions

Anything this spec does not define is not yours to decide. Ask before coding. If you still had to assume something, list it in your answer and record it with `archvow assumption add get_order "<assumption>"`. Until a person accepts or rejects it, CI fails (ARCH034).

## Definition of done

- `archvow check` reports no errors for this component.
- Checks on this code: ARCH, STYLE, FASTAPI, PRIV, GDPR, TST - what each one checks: `.archvow/spec.md` (Checks).
- `ruff format .` run, then `ruff check .` and `pytest` pass.
- Every invariant above is tested.
- The tests of the components that use it still pass.
- You did not edit `.archvow/`, `AGENTS.md`, `ruff.toml`, CI files or other components' tests, and added no `# noqa` / `# type: ignore` / suppressions.
- Working code that breaks these rules is not done.
