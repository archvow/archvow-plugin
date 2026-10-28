<!-- archvow spec of main - the task the AI or the developer implements. Generated from spec.vow in this folder, do not edit. -->

# Task: implement `main` (app) - US-001

The service: the order routes under /v1, domain errors mapped to HTTP statuses

## Must never happen

- Safety questions still open (archvow show main) - do not invent hazards or guards; say in your answer that they are open:
  S2. Data exposed (a user sees or changes data that is not theirs; secrets or personal data in a response or a log)
- Layer `p4_api`: never call the database or another service directly (use a service).
- Layer `p4_api`: never hold business rules.

## Personal data

Personal columns it handles: `customer` (personal).
- Never show, print or log their values, nor collected rows (GDPR004): log counts.
- Never partition or bucket files by them (GDPR006).

## Create exactly

- File: `src/orders_api/p4_api/main.py` - no other new file except its test.
- Public API (nothing else may be public; helpers start with `_`):
  - `def create_app() -> FastAPI`
- May use only these components:
  - `orders_api.p4_api.orders_router` (router)
  - `orders_api.p1_domain.order_not_found_error` (error)
- Only this component creates FastAPI() (FAPI003); startup and shutdown go in its lifespan, never on_event (FAPI004).
- Map domain errors to HTTP statuses here, with app.add_exception_handler: services and the domain never raise HTTPException (FAPI002).

## File header

Start the file with this docstring. Fill in the `<...>` values: the person responsible, the AI assistant you are (or `none`), today's date.

```python
"""<One-line summary of what this module does>.

@component  main (app, p4_api)
@spec       US-001
@project    orders_api
@author     <your name>
@assisted   <copilot | none>
@date       <YYYY-MM-DD>
"""
```

## Tests to write

- File: `tests/p4_api/test_main.py` - required, the build fails without it (ARCH017).
- Kind: **integration** - Through FastAPI's TestClient or a real database in a temporary file (SQLite), marked @pytest.mark.integration. Never a shared database or a real external service: clients are tested against a fake transport (httpx.MockTransport).
- One test per decision and per rule below, named after it, e.g. `test_empty_input_returns_empty_dataframe`, and marked with it: `@pytest.mark.decision(3)`, `@pytest.mark.rule("R2")` (ARCH038).

## Decisions made (implement exactly, do not reinterpret)

1. Which routers, under which prefix (e.g. /v1)?
   **orders_router under /v1**
2. Which domain errors map to which HTTP status (not found -> 404, conflict -> 409...)?
   **OrderNotFoundError -> 404, as decided by the error**
3. What happens at startup and shutdown (lifespan: connections, warm-up), and what if startup fails?
   **Nothing to start or stop yet**
4. CORS: which origins may call it from a browser - or none?
   **No CORS: not called from browsers**

## Assumptions

Anything this spec does not define is not yours to decide. Ask before coding. If you still had to assume something, list it in your answer and record it with `archvow assumption add main "<assumption>"`. Until a person accepts or rejects it, CI fails (ARCH034).

## Definition of done

- `archvow check` reports no errors for this component.
- Checks on this code: ARCH, STYLE, FASTAPI, GDPR, TST - what each one checks: `.archvow/spec.md` (Checks).
- `ruff format .` run, then `ruff check .` and `pytest` pass.
- Every invariant above is tested.
- You did not edit `.archvow/`, `AGENTS.md`, `ruff.toml`, CI files or other components' tests, and added no `# noqa` / `# type: ignore` / suppressions.
- Working code that breaks these rules is not done.
