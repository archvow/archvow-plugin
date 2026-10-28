<!-- archvow spec of mark_paid - the task the AI or the developer implements. Generated from spec.vow in this folder, do not edit. -->

# Task: implement `mark_paid` (service) - US-001

Find the invoice in the data folder, refuse a draft, mark it paid, save it, return it

## Tasks

This card is cut into 2 tasks: implement one at a time, in order; after each one, `archvow check` and `pytest` pass. A task is done when the code exists and each of its items has a marked test.

- **T1** the file and its public API, decisions 1-4, mark_paid.INV1
- **T2** decisions P1, P2

## Must never happen

- **mark_paid.H1** (high) Paying twice changes the invoice twice
  - prevented by **mark_paid.INV1**: marking an invoice paid twice gives the same invoice as once
  - tested by: a pytest test decorated with @pytest.mark.invariant("mark_paid.INV1")
- Layer `p3_services`: never parse command-line arguments.
- Layer `p3_services`: never print to the terminal.

## Personal data

Personal columns it handles: `customer` (personal).
- Never show, print or log their values, nor collected rows (GDPR004): log counts.
- Never partition or bucket files by them (GDPR006).

## Create exactly

- File: `src/invoicing/p3_services/mark_paid.py` - no other new file except its test.
- Public API (nothing else may be public; helpers start with `_`):
  - `def mark_paid(data_dir: Path, number: str) -> Invoice`
- May use only these components:
  - `invoicing.p1_domain.invoice_model` (model)
  - `invoicing.p1_domain.invoice_not_found_error` (error)
  - `invoicing.p2_io.invoices_repository` (repository)
- Used by: `invoices_cli` - their tests must stay green.

## File header

Start the file with this docstring. Fill in the `<...>` values: the person responsible, the AI assistant you are (or `none`), today's date.

```python
"""<One-line summary of what this module does>.

@component  mark_paid (service, p3_services)
@spec       US-001
@project    invoicing
@author     <your name>
@assisted   <claude-code | none>
@date       <YYYY-MM-DD>
"""
```

## Tests to write

- File: `tests/p3_services/test_mark_paid.py` - required, the build fails without it (ARCH017).
- Kind: **integration** - Real files in a temporary folder (pytest's tmp_path), a database in a temporary file (SQLite), a fake transport for other services, marked @pytest.mark.integration. Never a shared database, a real external service or a file outside the test's folder.
- One test per decision and per rule below, named after it, e.g. `test_empty_input_returns_empty_dataframe`, and marked with it: `@pytest.mark.decision(3)`, `@pytest.mark.rule("R2")` (ARCH038).
- The test of mark_paid.INV1, as described under *Must never happen*.

## Decisions made (implement exactly, do not reinterpret)

1. What does it do, step by step (read, check, change, save, call) - e.g. read the invoice, check it is a draft, send it, mark it sent?
   **Read the invoice; refuse a draft (ValueError); set status paid; save; return it**
2. Something it needs does not exist: which domain error - e.g. InvoiceNotFound?
   **InvoiceNotFoundError**
3. Run twice with the same input (a retry, a re-run) - e.g. "send invoice F-42" twice: same result and no duplicate effect, or a domain error?
   **The same result: an invoice already paid stays paid, saved again unchanged**
4. What must be all or nothing, and what happens if a step fails in the middle - e.g. the e-mail is sent but marking it sent fails?
   **Only one change, one save: nothing to undo**

## Decisions made for personal data and operations (implement exactly)

P1. Purpose: why does it process personal data (the fields of its spec card), and for whom - e.g. to send invoices to customers, for the finance team?
   **To record which customers paid their invoices, for the finance team**
P2. Legal basis (GDPR art. 6): consent, contract, legal obligation, vital interests, public task or legitimate interests - and where is it recorded - e.g. contract, in the record of processing?
   **Contract: the customer's purchase**

## Assumptions

Anything this spec does not define is not yours to decide. Ask before coding. If you still had to assume something, list it in your answer and record it with `archvow assumption add mark_paid "<assumption>"`. Until a person accepts or rejects it, CI fails (ARCH034).

## Definition of done

- `archvow check` reports no errors for this component.
- Checks on this code: ARCH, STYLE, GDPR, TST - what each one checks: `.archvow/spec.md` (Checks).
- `ruff format .` run, then `ruff check .` and `pytest` pass.
- Every invariant above is tested.
- The tests of the components that use it still pass.
- You did not edit `.archvow/`, `AGENTS.md`, `ruff.toml`, CI files or other components' tests, and added no `# noqa` / `# type: ignore` / suppressions.
- Working code that breaks these rules is not done.
