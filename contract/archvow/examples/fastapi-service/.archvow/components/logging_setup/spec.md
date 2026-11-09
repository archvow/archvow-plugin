<!-- archvow spec of logging_setup - the task the AI or the developer implements. Generated from spec.vow in this folder, do not edit. -->

# Task: implement `logging_setup` (foundation)

Configures logging once, at the app's startup - the only place that configures it (ARCH059 - no basicConfig, handler or root level anywhere else); every module takes its own logger with logging.getLogger(__name__). Level from the settings, one line per event with the request id, never a secret, a token or personal data; the tests put the logging back as it was after each test (a shared fixture of tests/conftest.py - later logs must not write to a test's closed stream).

## Must never happen

- Nothing: the safety analysis found nothing this component must guard against.
- Layer `p0_common`: never hold business rules.

## Create exactly

- File: `src/orders_api/p0_common/logging_setup.py` - no other new file except its test.
- Public API (nothing else may be public; helpers start with `_`):
  - `def setup_logging(level: str = "INFO") -> None`
- May not import other components (foundations in p0_common excepted).

## File header

Start the file with this docstring. Fill in the `<...>` values: the person responsible, the AI assistant you are (or `none`), today's date.

```python
"""<One-line summary of what this module does>.

@component  logging_setup (foundation, p0_common)
@spec       -
@project    orders_api
@author     <your name>
@assisted   <copilot | none>
@date       <YYYY-MM-DD>
"""
```

## Decisions made (implement exactly, do not reinterpret)

1. Where do the logs go and in which format - e.g. stderr, one line per event with time, level, request id and message?
   **stderr, one line per event: time, level, request id, message**

## Assumptions

Anything this spec does not define is not yours to decide. Ask before coding. If you still had to assume something, list it in your answer and record it with `archvow assumption add logging_setup "<assumption>"`. Until a person accepts or rejects it, CI fails (ARCH034).

## Definition of done

- `archvow check` reports no errors for this component.
- Checks on this code: ARCH, STYLE, FASTAPI, GDPR, TST - what each one checks: `.archvow/spec.md` (Checks).
- `ruff format .` run, then `ruff check .` and `pytest` pass.
- Every invariant above is tested.
- You did not edit `.archvow/`, `AGENTS.md`, `ruff.toml`, CI files or other components' tests, and added no `# noqa` / `# type: ignore` / suppressions.
- Working code that breaks these rules is not done.
