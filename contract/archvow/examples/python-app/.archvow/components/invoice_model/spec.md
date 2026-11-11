<!-- archvow spec of invoice_model - the task the AI or the developer implements. Generated from spec.vow in this folder, do not edit. -->

# Task: implement `invoice_model` (model) - US-001

An invoice: number str, customer str, amount Decimal >= 0, status draft | sent | paid

## Must never happen

- **invoice_model.H1** (high) An invoice with a negative amount is accepted
  - prevented by **invoice_model.INV1**: every invoice has an amount of zero or more
  - tested by: a pytest test decorated with @pytest.mark.invariant("invoice_model.INV1")
- Layer `p1_domain`: never read or write anything (no files, database, network or environment).
- Layer `p1_domain`: never log, print or read the clock (time and dates are passed in).

## Personal data

Columns and their class (personal data). Define exactly these; a new column is classified first (`archvow column set`), or the build fails (PRIV001):
- `amount`: none
- `customer`: personal
- `number`: none
- `status`: none

## Create exactly

- File: `src/invoicing/p1_domain/invoice_model.py` - no other new file except its test.
- Public API (nothing else may be public; helpers start with `_`):
  - `class Invoice`
- May not import other components (foundations in p0_common excepted).
- Used by: `invoices_repository`, `mark_paid` - their tests must stay green.

## File header

Start the file with this docstring. Fill in the `<...>` values: the person responsible, the AI assistant you are (or `none`), today's date.

```python
"""<One-line summary of what this module does>.

@component  invoice_model (model, p1_domain)
@spec       US-001
@project    invoicing
@author     <your name>
@assisted   <claude-code | none>
@date       <YYYY-MM-DD>
"""
```

## Tests to write

- File: `tests/p1_domain/test_invoice_model.py` - required, the build fails without it (ARCH017).
- Kind: **unit** - Pure and fast: plain Python objects, no files, no database, no network. Runs on every commit.
- One test per decision and per rule below, named after it, e.g. `test_empty_input_returns_empty_dataframe`, and marked with it: `@pytest.mark.decision(3)`, `@pytest.mark.rule("R2")` (ARCH038).
- The test of invoice_model.INV1, as described under *Must never happen*.

## Decisions made (implement exactly, do not reinterpret)

1. Which fields are required, and which are optional (with which default) - e.g. number: str required, note: str = ""?
   **All fields required**
2. Which values are valid for each field (ranges, formats, lengths, allowed states) - e.g. amount >= 0 with 2 decimals, status draft, sent or paid?
   **amount >= 0 with at most 2 decimals; status draft, sent or paid; number and customer not empty**
3. Which fields must never appear in a log, an output or an error message (passwords, internal ids, personal data) - e.g. iban - or none?
   **None**
4. Is it changed after it is built, or frozen (a new one for every change) - e.g. frozen dataclass?
   **Frozen: a change builds a new one (dataclasses.replace)**

## Assumptions

Anything this spec does not define is not yours to decide. Ask before coding. If you still had to assume something, list it in your answer and record it with `archvow assumption add invoice_model "<assumption>"`. Until a person accepts or rejects it, CI fails (ARCH034).

## Definition of done

- `archvow check` reports no errors for this component.
- Checks on this code: ARCH, STYLE, PRIV, GDPR, TST - what each one checks: `.archvow/spec.md` (Checks).
- `ruff format .` run, then `ruff check .` and `pytest` pass.
- Every invariant above is tested.
- The tests of the components that use it still pass.
- You did not edit `.archvow/`, `AGENTS.md`, `ruff.toml`, CI files or other components' tests, and added no `# noqa` / `# type: ignore` / suppressions.
- Working code that breaks these rules is not done.
