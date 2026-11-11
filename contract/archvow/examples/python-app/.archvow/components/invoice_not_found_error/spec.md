<!-- archvow spec of invoice_not_found_error - the task the AI or the developer implements. Generated from spec.vow in this folder, do not edit. -->

# Task: implement `invoice_not_found_error` (error) - US-001

No invoice with this number

## Must never happen

- Nothing: the safety analysis found nothing this component must guard against.
- Layer `p1_domain`: never read or write anything (no files, database, network or environment).
- Layer `p1_domain`: never log, print or read the clock (time and dates are passed in).

## Create exactly

- File: `src/invoicing/p1_domain/invoice_not_found_error.py` - no other new file except its test.
- Public API (nothing else may be public; helpers start with `_`):
  - `class InvoiceNotFoundError`
- May not import other components (foundations in p0_common excepted).
- Used by: `mark_paid`, `invoices_cli` - their tests must stay green.

## File header

Start the file with this docstring. Fill in the `<...>` values: the person responsible, the AI assistant you are (or `none`), today's date.

```python
"""<One-line summary of what this module does>.

@component  invoice_not_found_error (error, p1_domain)
@spec       US-001
@project    invoicing
@author     <your name>
@assisted   <claude-code | none>
@date       <YYYY-MM-DD>
"""
```

## Tests to write

- File: `tests/p1_domain/test_invoice_not_found_error.py` - required, the build fails without it (ARCH017).
- Kind: **unit** - Pure and fast: plain Python objects, no files, no database, no network. Runs on every commit.
- One test per decision and per rule below, named after it, e.g. `test_empty_input_returns_empty_dataframe`, and marked with it: `@pytest.mark.decision(3)`, `@pytest.mark.rule("R2")` (ARCH038).

## Decisions made (implement exactly, do not reinterpret)

1. Which exit code does the command line give for it - e.g. InvoiceNotFound -> 3?
   **Exit code 3**
2. What does its message say - and never say (internal ids, personal data, secrets) - e.g. "Invoice not found", without the customer?
   **A short fixed message, without personal data or secrets**

## Assumptions

Anything this spec does not define is not yours to decide. Ask before coding. If you still had to assume something, list it in your answer and record it with `archvow assumption add invoice_not_found_error "<assumption>"`. Until a person accepts or rejects it, CI fails (ARCH034).

## Definition of done

- `archvow check` reports no errors for this component.
- Checks on this code: ARCH, STYLE, PRIV, GDPR, TST - what each one checks: `.archvow/spec.md` (Checks).
- `ruff format .` run, then `ruff check .` and `pytest` pass.
- Every invariant above is tested.
- The tests of the components that use it still pass.
- You did not edit `.archvow/`, `AGENTS.md`, `ruff.toml`, CI files or other components' tests, and added no `# noqa` / `# type: ignore` / suppressions.
- Working code that breaks these rules is not done.
