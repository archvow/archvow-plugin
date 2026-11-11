<!-- archvow spec of get_current_user - the task the AI or the developer implements. Generated from spec.vow in this folder, do not edit. -->

# Task: implement `get_current_user` (dependency) - US-001

The authenticated customer's id, from the X-Customer header

## Must never happen

- Safety questions still open (archvow show get_current_user) - do not invent hazards or guards; say in your answer that they are open:
  S2. Data exposed (a user sees or changes data that is not theirs; secrets or personal data in a response or a log)
- Layer `p0_common`: never hold business rules.

## Create exactly

- File: `src/orders_api/p0_common/get_current_user.py` - no other new file except its test.
- Public API (nothing else may be public; helpers start with `_`):
  - `def get_current_user(...)`
- May not import other components (foundations in p0_common excepted).

## File header

Start the file with this docstring. Fill in the `<...>` values: the person responsible, the AI assistant you are (or `none`), today's date.

```python
"""<One-line summary of what this module does>.

@component  get_current_user (dependency, p0_common)
@spec       US-001
@project    orders_api
@author     <your name>
@assisted   <copilot | none>
@date       <YYYY-MM-DD>
"""
```

## Tests to write

- File: `tests/p0_common/test_get_current_user.py` - required, the build fails without it (ARCH017).
- Kind: **unit** - Pure and fast: plain Python objects, no database, no network, no app. Runs on every commit.
- One test per decision and per rule below, named after it, e.g. `test_empty_input_returns_empty_dataframe`, and marked with it: `@pytest.mark.decision(3)`, `@pytest.mark.rule("R2")` (ARCH038).

## Decisions made (implement exactly, do not reinterpret)

1. What does it provide, and for how long (one request, the whole app)?
   **The customer id (str) from the X-Customer header, for one request**
2. What happens when it cannot (no token, the database is down): which error, which HTTP status in the end?
   **Header missing or empty: 401 Unauthorized**

## Assumptions

Anything this spec does not define is not yours to decide. Ask before coding. If you still had to assume something, list it in your answer and record it with `archvow assumption add get_current_user "<assumption>"`. Until a person accepts or rejects it, CI fails (ARCH034).

## Definition of done

- `archvow check` reports no errors for this component.
- Checks on this code: ARCH, STYLE, FASTAPI, PRIV, GDPR, TST - what each one checks: `.archvow/spec.md` (Checks).
- `ruff format .` run, then `ruff check .` and `pytest` pass.
- Every invariant above is tested.
- You did not edit `.archvow/`, `AGENTS.md`, `ruff.toml`, CI files or other components' tests, and added no `# noqa` / `# type: ignore` / suppressions.
- Working code that breaks these rules is not done.
