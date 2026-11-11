<!-- archvow spec of create_order - the task the AI or the developer implements. Generated from spec.vow in this folder, do not edit. -->

# Task: implement `create_order` (service) - US-001

Create a pending order for the current customer and save it

## Must never happen

- **create_order.H1** (high) An order is saved with an amount of zero or less
  - prevented by **create_order.INV1**: every saved order has an amount greater than zero
  - tested by: a Hypothesis @given test decorated with @pytest.mark.invariant("create_order.INV1") (it searches for counterexamples, not one example)
- Safety questions still open (archvow show create_order) - do not invent hazards or guards; say in your answer that they are open:
  S2. Sent twice (a client retries or submits twice)
  S4. Two requests change the same data at the same time (lost update, negative stock)
  S5. Data exposed (a user sees or changes data that is not theirs; secrets or personal data in a response or a log)
- Layer `p3_services`: never know about HTTP (requests, status codes, HTTPException).

## Personal data

Personal columns it handles: `customer` (personal).
- Never show, print or log their values, nor collected rows (PRIV002): log counts.
- Never partition or bucket files by them (PRIV004).

## Create exactly

- File: `src/orders_api/p3_services/create_order.py` - no other new file except its test.
- Public API (nothing else may be public; helpers start with `_`):
  - `def create_order(...)`
- May use only these components:
  - `orders_api.p1_domain.order_model` (model)
  - `orders_api.p1_domain.new_order_model` (model)
  - `orders_api.p2_io.orders_repository` (repository)
- Used by: `orders_router` - their tests must stay green.

## File header

Start the file with this docstring. Fill in the `<...>` values: the person responsible, the AI assistant you are (or `none`), today's date.

```python
"""<One-line summary of what this module does>.

@component  create_order (service, p3_services)
@spec       US-001
@project    orders_api
@author     <your name>
@assisted   <copilot | none>
@date       <YYYY-MM-DD>
"""
```

## Tests to write

- File: `tests/p3_services/test_create_order.py` - required, the build fails without it (ARCH017).
- Kind: **integration** - Through FastAPI's TestClient or a real database in a temporary file (SQLite), marked @pytest.mark.integration. Never a shared database or a real external service: clients are tested against a fake transport (httpx.MockTransport).
- One test per decision and per rule below, named after it, e.g. `test_empty_input_returns_empty_dataframe`, and marked with it: `@pytest.mark.decision(3)`, `@pytest.mark.rule("R2")` (ARCH038).
- The test of create_order.INV1, as described under *Must never happen*.

## Decisions made (implement exactly, do not reinterpret)

1. What does it do, step by step (read, check, change, save, call)?
   **Build a pending order with the next id, save it, return it**
2. Something it needs does not exist: which domain error?
   **Not applicable: it needs nothing that may be missing**
3. Called twice with the same input (a client retry): same result and no duplicate effect (idempotent), or a duplicate error?
   **Two orders: a retry is a new order (customers cancel the extra one)**
4. Who may perform it, and is that checked here or in the router?
   **Any authenticated customer, for themselves; checked by the router**
5. What must be all or nothing (one transaction), and what happens if a call to another service fails in the middle?
   **One save, nothing else to undo**

## Assumptions

Anything this spec does not define is not yours to decide. Ask before coding. If you still had to assume something, list it in your answer and record it with `archvow assumption add create_order "<assumption>"`. Until a person accepts or rejects it, CI fails (ARCH034).

## Definition of done

- `archvow check` reports no errors for this component.
- Checks on this code: ARCH, STYLE, FASTAPI, PRIV, GDPR, TST - what each one checks: `.archvow/spec.md` (Checks).
- `ruff format .` run, then `ruff check .` and `pytest` pass.
- Every invariant above is tested.
- The tests of the components that use it still pass.
- You did not edit `.archvow/`, `AGENTS.md`, `ruff.toml`, CI files or other components' tests, and added no `# noqa` / `# type: ignore` / suppressions.
- Working code that breaks these rules is not done.
