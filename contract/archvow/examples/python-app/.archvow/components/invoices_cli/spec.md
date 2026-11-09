<!-- archvow spec of invoices_cli - the task the AI or the developer implements. Generated from spec.vow in this folder, do not edit. -->

# Task: implement `invoices_cli` (cli) - US-001

Command line: pay NUMBER - marks the invoice paid and prints it

## Must never happen

- Nothing: the safety analysis found nothing this component must guard against.
- Layer `p4_entrypoints`: never call a repository or a client directly (use a service).
- Layer `p4_entrypoints`: never hold business rules.

## Personal data

Personal columns it handles: `customer` (personal).
- Never show, print or log their values, nor collected rows (GDPR004): log counts.
- Never partition or bucket files by them (GDPR006).

## Create exactly

- File: `src/invoicing/p4_entrypoints/invoices_cli.py` - no other new file except its test.
- Public API (nothing else may be public; helpers start with `_`):
  - `def main(argv: Sequence[str] | None = None) -> int`
- May use only these components:
  - `invoicing.p3_services.mark_paid` (service)
  - `invoicing.p1_domain.invoice_not_found_error` (error)
- End the file with `if __name__ == "__main__": raise SystemExit(main())`, so `python -m <module>` runs it.
- Parse the arguments with argparse; turn domain errors into a message on stderr and an exit code, never a traceback.
- Accept one --name-with-dashes argument per required setting of the config decision (archvow next prints them in its run line).
- Call setup_logging once, first; log the start and the end of the run with its exit code, never the values the user typed in when they may be personal.

## File header

Start the file with this docstring. Fill in the `<...>` values: the person responsible, the AI assistant you are (or `none`), today's date.

```python
"""<One-line summary of what this module does>.

@component  invoices_cli (cli, p4_entrypoints)
@spec       US-001
@project    invoicing
@author     <your name>
@assisted   <claude-code | none>
@date       <YYYY-MM-DD>
"""
```

## Tests to write

- File: `tests/p4_entrypoints/test_invoices_cli.py` - required, the build fails without it (ARCH017).
- Kind: **unit** - Pure and fast: plain Python objects, no files, no database, no network. Runs on every commit.
- One test per decision and per rule below, named after it, e.g. `test_empty_input_returns_empty_dataframe`, and marked with it: `@pytest.mark.decision(3)`, `@pytest.mark.rule("R2")` (ARCH038).

## Examples (reproduce exactly)

Given by the business team. For each one, a test reads `input/`, runs the component and compares what it gives with `expected/`. Never change these files: archvow compares them with the ones a person recorded (ARCH055); an example that looks wrong is a question for a person.

The test also writes what the component gave to `actual/`, with the same file names and format as `expected/` (not locked, not committed): people see where it differs. Open every file with `encoding="utf-8"` (and `newline=""` for CSV) and compare what you read (rows, JSON objects), not bytes. Write each example's test before the code it checks: it fails until the code exists, and is never changed to pass.

- **US-001-pay-a-sent-invoice** (US-001): `tests/examples/invoices_cli/US-001-pay-a-sent-invoice/` - its test is marked `@pytest.mark.example("US-001-pay-a-sent-invoice")`

  ```gherkin
  Feature: pay an invoice
    Scenario: a sent invoice is marked paid
      Given the sent invoice F-7 of Ada for 9.90
      When the finance team runs invoices pay F-7
      Then it prints "F-7 Ada 9.90 paid" and F-7 is saved paid
  ```

  `input/invoices/F-7.json`

  ```json
  {
    "amount": "9.90",
    "customer": "Ada",
    "number": "F-7",
    "status": "sent"
  }
  ```

  `expected/invoices/F-7.json`

  ```json
  {
    "amount": "9.90",
    "customer": "Ada",
    "number": "F-7",
    "status": "paid"
  }
  ```

  `expected/stdout.txt`

  ```text
  F-7 Ada 9.90 paid
  ```

## Decisions made (implement exactly, do not reinterpret)

1. Invalid arguments (e.g. an unknown option, a wrong date): which exit code and message?
   **Exit code 2 with the usage message (argparse)**
2. Which exit code for which problem, so a script or a scheduler knows what happened - e.g. 0 ok, 1 error, 2 bad arguments, 3 not found?
   **0 ok, 1 error, 2 bad arguments, 3 not found**
3. What does it print on success, and where do errors go - e.g. one line per invoice on stdout, errors on stderr?
   **The invoice on one line on stdout; errors on stderr**

## Assumptions

Anything this spec does not define is not yours to decide. Ask before coding. If you still had to assume something, list it in your answer and record it with `archvow assumption add invoices_cli "<assumption>"`. Until a person accepts or rejects it, CI fails (ARCH034).

## Definition of done

- `archvow check` reports no errors for this component.
- Checks on this code: ARCH, STYLE, GDPR, TST - what each one checks: `.archvow/spec.md` (Checks).
- `ruff format .` run, then `ruff check .` and `pytest` pass.
- Every invariant above is tested.
- Every example above has its marked test, and its files are the ones recorded.
- You did not edit `.archvow/`, `AGENTS.md`, `ruff.toml`, CI files or other components' tests, and added no `# noqa` / `# type: ignore` / suppressions.
- Working code that breaks these rules is not done.
