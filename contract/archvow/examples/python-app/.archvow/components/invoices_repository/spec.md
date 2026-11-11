<!-- archvow spec of invoices_repository - the task the AI or the developer implements. Generated from spec.vow in this folder, do not edit. -->

# Task: implement `invoices_repository` (repository) - US-001

Stores and finds invoices, one JSON file per invoice in the data folder

## Tasks

This card is cut into 2 tasks: implement one at a time, in order; after each one, `archvow check` and `pytest` pass. A task is done when the code exists and each of its items has a marked test.

- **T1** the file and its public API, decisions 1-4, invoices_repository.INV1
- **T2** decisions P1, P2

## Must never happen

- **invoices_repository.H1** (high) An invoice file is half written
  - prevented by **invoices_repository.INV1**: an invoice file is complete or absent
  - tested by: a pytest test decorated with @pytest.mark.invariant("invoices_repository.INV1")
- Layer `p2_io`: never hold business rules beyond mapping data.
- Layer `p2_io`: never parse command-line arguments.

## Personal data

Personal columns it handles: `customer` (personal).
- Never show, print or log their values, nor collected rows (PRIV002): log counts.
- Never partition or bucket files by them (PRIV004).
- It writes: `customer`.

## Create exactly

- File: `src/invoicing/p2_io/invoices_repository.py` - no other new file except its test.
- Public API (nothing else may be public; helpers start with `_`):
  - `class InvoicesRepository`
- May use only these components:
  - `invoicing.p1_domain.invoice_model` (model)
- Used by: `mark_paid` - their tests must stay green.

## File header

Start the file with this docstring. Fill in the `<...>` values: the person responsible, the AI assistant you are (or `none`), today's date.

```python
"""<One-line summary of what this module does>.

@component  invoices_repository (repository, p2_io)
@spec       US-001
@project    invoicing
@author     <your name>
@assisted   <claude-code | none>
@date       <YYYY-MM-DD>
"""
```

## Tests to write

- File: `tests/p2_io/test_invoices_repository.py` - required, the build fails without it (ARCH017).
- Kind: **integration** - Real files in a temporary folder (pytest's tmp_path), a database in a temporary file (SQLite), a fake transport for other services, marked @pytest.mark.integration. Never a shared database, a real external service or a file outside the test's folder.
- One test per decision and per rule below, named after it, e.g. `test_empty_input_returns_empty_dataframe`, and marked with it: `@pytest.mark.decision(3)`, `@pytest.mark.rule("R2")` (ARCH038).
- The test of invoices_repository.INV1, as described under *Must never happen*.

## Decisions made (implement exactly, do not reinterpret)

1. Where and in which format is the data kept - e.g. one JSON file per invoice in data/invoices/, or a SQLite table?
   **One file NUMBER.json in data_dir, the fields of the invoice, amount as a string**
2. Not found (e.g. get("F-42") when it does not exist): return None, or raise which domain error?
   **Return None; the service raises the domain error**
3. A crash while writing (e.g. the disk is full after half a file): how does a reader never see a half-written record - write to a temporary file then rename, or a transaction?
   **Write to a temporary file next to it, then rename it**
4. Two runs write the same record at the same moment: the last one wins, the second one is refused, or the second one waits (a lock)?
   **The last one wins: marking paid twice gives the same file**

## Decisions made for personal data and operations (implement exactly)

P1. Retention: how long is the personal data it writes kept, how is it deleted then, and how is one person's data erased on request (art. 17) - e.g. 5 years, then deleted; one person's rows deleted by id?
   **10 years (accounting law), then the file is deleted; one person's invoices are kept until then (legal obligation)**
P2. Location: in which country or region is it stored? Outside the EU/EEA, which safeguard applies (adequacy decision, standard contractual clauses - art. 46) - e.g. France, cloud region eu-west-3?
   **The company's file server, in France**

## Assumptions

Anything this spec does not define is not yours to decide. Ask before coding. If you still had to assume something, list it in your answer and record it with `archvow assumption add invoices_repository "<assumption>"`. Until a person accepts or rejects it, CI fails (ARCH034).

## Definition of done

- `archvow check` reports no errors for this component.
- Checks on this code: ARCH, STYLE, PRIV, GDPR, TST - what each one checks: `.archvow/spec.md` (Checks).
- `ruff format .` run, then `ruff check .` and `pytest` pass.
- Every invariant above is tested.
- The tests of the components that use it still pass.
- You did not edit `.archvow/`, `AGENTS.md`, `ruff.toml`, CI files or other components' tests, and added no `# noqa` / `# type: ignore` / suppressions.
- Working code that breaks these rules is not done.
