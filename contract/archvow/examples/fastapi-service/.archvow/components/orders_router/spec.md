<!-- archvow spec of orders_router - the task the AI or the developer implements. Generated from spec.vow in this folder, do not edit. -->

# Task: implement `orders_router` (router) - US-001

The order API

## Tasks

This card is cut into 2 tasks: implement one at a time, in order; after each one, `archvow check` and `pytest` pass. A task is done when the code exists and each of its items has a marked test.

- **T1** the file and its public API, decisions 1-5, example US-001-another-customers-order
- **T2** decisions P1, P2, orders_router.INV1

## Must never happen

- **orders_router.H1** (critical) A customer reads another customer's order
  - prevented by **orders_router.INV1**: a customer only ever reads their own orders
  - tested by: a Hypothesis @given test decorated with @pytest.mark.invariant("orders_router.INV1") (it searches for counterexamples, not one example)
- Safety questions still open (archvow show orders_router) - do not invent hazards or guards; say in your answer that they are open:
  S3. Wrong input (invalid values, out of range, an illegal state change)
  S4. Sent twice (a client retries or submits twice)
- Layer `p4_api`: never call the database or another service directly (use a service).
- Layer `p4_api`: never hold business rules.

## Personal data

Personal columns it handles: `customer` (personal).
- Never show, print or log their values, nor collected rows (GDPR004): log counts.
- Never partition or bucket files by them (GDPR006).

## Create exactly

- File: `src/orders_api/p4_api/orders_router.py` - no other new file except its test.
- Public API (nothing else may be public; helpers start with `_`):
  - `router: APIRouter`
  - `@router.post("/orders")  def post_order(...)`
  - `@router.get("/orders/{order_id}")  def read_order(...)`
- May use only these components:
  - `orders_api.p3_services.create_order` (service)
  - `orders_api.p3_services.get_order` (service)
  - `orders_api.p1_domain.order_model` (model)
  - `orders_api.p1_domain.new_order_model` (model)
- Used by: `main` - their tests must stay green.

## File header

Start the file with this docstring. Fill in the `<...>` values: the person responsible, the AI assistant you are (or `none`), today's date.

```python
"""<One-line summary of what this module does>.

@component  orders_router (router, p4_api)
@spec       US-001
@project    orders_api
@author     <your name>
@assisted   <copilot | none>
@date       <YYYY-MM-DD>
"""
```

## Tests to write

- File: `tests/p4_api/test_orders_router.py` - required, the build fails without it (ARCH017).
- Kind: **integration** - Through FastAPI's TestClient or a real database in a temporary file (SQLite), marked @pytest.mark.integration. Never a shared database or a real external service: clients are tested against a fake transport (httpx.MockTransport).
- One test per decision and per rule below, named after it, e.g. `test_empty_input_returns_empty_dataframe`, and marked with it: `@pytest.mark.decision(3)`, `@pytest.mark.rule("R2")` (ARCH038).
- The test of orders_router.INV1, as described under *Must never happen*.
- Test through FastAPI's TestClient with the app from the app component, one test per route and status code; for who may call it, a Hypothesis test that generates two users and checks one never sees the other's data.
- Contract with golden files: the business examples of the card (archvow example add: tests/examples/<component>/<example>/input/request.json and expected/response.json, locked, with an optional scenario.feature in Given/When/Then words), and one test that sends each request through the TestClient and compares the response with its expected one (generated ids and dates replaced first). Never change them: no example yet means a person records one first.

## Examples (reproduce exactly)

Given by the business team. For each one, a test reads `input/`, runs the component and compares what it gives with `expected/`. Never change these files: archvow compares them with the ones a person recorded (ARCH055); an example that looks wrong is a question for a person.

The test also writes what the component gave to `actual/`, with the same file names and format as `expected/` (not locked, not committed): people see where it differs. Open every file with `encoding="utf-8"` (and `newline=""` for CSV) and compare what you read (rows, JSON objects), not bytes. Write each example's test before the code it checks: it fails until the code exists, and is never changed to pass.

- **US-001-another-customers-order** (US-001) - rule orders_router.INV1: `tests/examples/orders_router/US-001-another-customers-order/` - its test is marked `@pytest.mark.example("US-001-another-customers-order") @pytest.mark.integration`

  ```gherkin
  Feature: read an order
    Rule: orders_router.INV1
      Scenario: another customer's order is not found
        Given ann has an order of 5.00
        When bob reads it
        Then the answer is 404 "Order not found"
  ```

  `input/request.json`

  ```json
  {
    "amount": "5.00",
    "owner": "ann",
    "reader": "bob"
  }
  ```

  `expected/response.json`

  ```json
  {
    "body": {
      "detail": "Order not found"
    },
    "status": 404
  }
  ```

## Decisions made (implement exactly, do not reinterpret)

1. Who may call each route: anyone, an authenticated user, the owner of the resource, which role?
   **Authenticated customers (get_current_user), each only on their own orders**
2. Status codes: success, not found, conflict, forbidden - and the error body format?
   **201 created, 200 read, 404 not found; errors as {"detail": "..."}**
3. Invalid input: FastAPI's 422, or 400 with your own error format?
   **FastAPI's 422**
4. Lists: pagination, default and maximum page size - or no list route?
   **No list route; POST repeated: two orders (no idempotency key yet)**
5. A POST or PUT repeated by a client retry: an idempotency key, or duplicates accepted?
   **No list route; POST repeated: two orders (no idempotency key yet)**

## Decisions made for personal data and operations (implement exactly)

P1. Purpose: why does it process personal data (the columns of its spec card), and for whom - e.g. to bill customers for their orders, for the finance team?
   **Take and show the orders of each customer, for that customer only**
P2. Legal basis (GDPR art. 6): consent, contract, legal obligation, vital interests, public task or legitimate interests - and where is it recorded - e.g. contract, in the record of processing?
   **Contract: the customer's orders (terms of sale)**

## Assumptions

Anything this spec does not define is not yours to decide. Ask before coding. If you still had to assume something, list it in your answer and record it with `archvow assumption add orders_router "<assumption>"`. Until a person accepts or rejects it, CI fails (ARCH034).

## Definition of done

- `archvow check` reports no errors for this component.
- Checks on this code: ARCH, STYLE, FASTAPI, GDPR, TST - what each one checks: `.archvow/spec.md` (Checks).
- `ruff format .` run, then `ruff check .` and `pytest` pass.
- Every invariant above is tested.
- The tests of the components that use it still pass.
- Every example above has its marked test, and its files are the ones recorded.
- You did not edit `.archvow/`, `AGENTS.md`, `ruff.toml`, CI files or other components' tests, and added no `# noqa` / `# type: ignore` / suppressions.
- Working code that breaks these rules is not done.
