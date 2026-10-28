<!-- archvow spec of settings - the task the AI or the developer implements. Generated from spec.vow in this folder, do not edit. -->

# Task: implement `settings` (foundation)

The service's settings, read from the environment (pydantic-settings), never from code.

## Must never happen

- Nothing: the safety analysis found nothing this component must guard against.
- Layer `p0_common`: never hold business rules.

## Create exactly

- File: `src/orders_api/p0_common/settings.py` - no other new file except its test.
- Public API (nothing else may be public; helpers start with `_`):
  - `class Settings`
  - `def get_settings() -> Settings`
- May not import other components (foundations in p0_common excepted).

## File header

Start the file with this docstring. Fill in the `<...>` values: the person responsible, the AI assistant you are (or `none`), today's date.

```python
"""<One-line summary of what this module does>.

@component  settings (foundation, p0_common)
@spec       -
@project    orders_api
@author     <your name>
@assisted   <copilot | none>
@date       <YYYY-MM-DD>
"""
```

## Decisions made (implement exactly, do not reinterpret)

1. Which settings, with which defaults (name: type = default), and which are required?
   **app_name: str = 'orders'; nothing else yet**
2. Which settings are secrets (never logged, never in a response), and where do they come from?
   **No secret yet**

## Assumptions

Anything this spec does not define is not yours to decide. Ask before coding. If you still had to assume something, list it in your answer and record it with `archvow assumption add settings "<assumption>"`. Until a person accepts or rejects it, CI fails (ARCH034).

## Definition of done

- `archvow check` reports no errors for this component.
- Checks on this code: ARCH, STYLE, FASTAPI, GDPR, TST - what each one checks: `.archvow/spec.md` (Checks).
- `ruff format .` run, then `ruff check .` and `pytest` pass.
- Every invariant above is tested.
- You did not edit `.archvow/`, `AGENTS.md`, `ruff.toml`, CI files or other components' tests, and added no `# noqa` / `# type: ignore` / suppressions.
- Working code that breaks these rules is not done.
