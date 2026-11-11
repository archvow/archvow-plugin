<!-- archvow spec of orders_repository - the task the AI or the developer implements. Generated from spec.vow in this folder, do not edit. -->

# Task: implement `orders_repository` (repository) - US-001

Stores and finds orders (in memory to start)

## Must never happen

- Safety questions still open (archvow show orders_repository) - do not invent hazards or guards; say in your answer that they are open:
  S2. Too much (unbounded results, duplicated records, a flood of requests)
  S5. Two requests change the same data at the same time (lost update, negative stock)
  S6. Data exposed (a user sees or changes data that is not theirs; secrets or personal data in a response or a log)
- Layer `p2_io`: never hold business rules beyond mapping data.
- Layer `p2_io`: never know about HTTP routes.

## Personal data

Personal columns it handles: `customer` (personal).
- Never show, print or log their values, nor collected rows (PRIV002): log counts.
- Never partition or bucket files by them (PRIV004).
- It writes: `customer`.

## Create exactly

- File: `src/orders_api/p2_io/orders_repository.py` - no other new file except its test.
- Public API (nothing else may be public; helpers start with `_`):
  - `class OrdersRepository`
- May use only these components:
  - `orders_api.p1_domain.order_model` (model)
- Used by: `create_order`, `get_order` - their tests must stay green.

## File header

Start the file with this docstring. Fill in the `<...>` values: the person responsible, the AI assistant you are (or `none`), today's date.

```python
"""<One-line summary of what this module does>.

@component  orders_repository (repository, p2_io)
@spec       US-001
@project    orders_api
@author     <your name>
@assisted   <copilot | none>
@date       <YYYY-MM-DD>
"""
```

## Tests to write

- File: `tests/p2_io/test_orders_repository.py` - required, the build fails without it (ARCH017).
- Kind: **integration** - Through FastAPI's TestClient or a real database in a temporary file (SQLite), marked @pytest.mark.integration. Never a shared database or a real external service: clients are tested against a fake transport (httpx.MockTransport).
- One test per decision and per rule below, named after it, e.g. `test_empty_input_returns_empty_dataframe`, and marked with it: `@pytest.mark.decision(3)`, `@pytest.mark.rule("R2")` (ARCH038).

## Decisions made (implement exactly, do not reinterpret)

1. Not found: return None, or raise which domain error?
   **Return None**
2. Who opens and commits the transaction (the service, never the repository)?
   **No transaction: memory; the service decides what is saved**
3. Two updates of the same record at the same time: last write wins, a version check, or a lock?
   **Last write wins: an order is only created, never updated yet**
4. Which queries can return many rows, and what is their maximum?
   **None: no list query yet**

## Decisions made for personal data and operations (implement exactly)

P1. Retention: how long is the personal data it writes kept, how is it deleted then, and how is one person's data erased on request (art. 17) - e.g. 5 years, then deleted; one person's rows deleted by id?
   **In memory: gone when the service stops; nothing kept, nothing to erase on request**
P2. Location: in which country or region is it stored? Outside the EU/EEA, which safeguard applies (adequacy decision, standard contractual clauses - art. 46) - e.g. France, cloud region eu-west-3?
   **The service's own memory, in the EU**

## Assumptions

Anything this spec does not define is not yours to decide. Ask before coding. If you still had to assume something, list it in your answer and record it with `archvow assumption add orders_repository "<assumption>"`. Until a person accepts or rejects it, CI fails (ARCH034).

## Definition of done

- `archvow check` reports no errors for this component.
- Checks on this code: ARCH, STYLE, FASTAPI, PRIV, GDPR, TST - what each one checks: `.archvow/spec.md` (Checks).
- `ruff format .` run, then `ruff check .` and `pytest` pass.
- Every invariant above is tested.
- The tests of the components that use it still pass.
- You did not edit `.archvow/`, `AGENTS.md`, `ruff.toml`, CI files or other components' tests, and added no `# noqa` / `# type: ignore` / suppressions.
- Working code that breaks these rules is not done.
