<!-- archvow spec of config - the task the AI or the developer implements. Generated from spec.vow in this folder, do not edit. -->

# Task: implement `config` (foundation)

The program's settings, read from the environment and the command line, never from code.

## Must never happen

- Nothing: the safety analysis found nothing this component must guard against.
- Layer `p0_common`: never hold business rules.

## Create exactly

- File: `src/invoicing/p0_common/config.py` - no other new file except its test.
- Public API (nothing else may be public; helpers start with `_`):
  - `class Config`
  - `def load_config(environ: Mapping[str, str] | None = None) -> Config`
- May not import other components (foundations in p0_common excepted).

## File header

Start the file with this docstring. Fill in the `<...>` values: the person responsible, the AI assistant you are (or `none`), today's date.

```python
"""<One-line summary of what this module does>.

@component  config (foundation, p0_common)
@spec       -
@project    invoicing
@author     <your name>
@assisted   <claude-code | none>
@date       <YYYY-MM-DD>
"""
```

## Decisions made (implement exactly, do not reinterpret)

1. Settings, name: type = default separated by commas - e.g. data_dir: str (required), log_level: str = "INFO" - or none?
   **data_dir: str (required), log_level: str = "INFO"**
2. Which settings are secrets (never logged, never printed), and where do they come from - e.g. smtp_password, from an environment variable - or none?
   **None**

## Assumptions

Anything this spec does not define is not yours to decide. Ask before coding. If you still had to assume something, list it in your answer and record it with `archvow assumption add config "<assumption>"`. Until a person accepts or rejects it, CI fails (ARCH034).

## Definition of done

- `archvow check` reports no errors for this component.
- Checks on this code: ARCH, STYLE, PRIV, GDPR, TST - what each one checks: `.archvow/spec.md` (Checks).
- `ruff format .` run, then `ruff check .` and `pytest` pass.
- Every invariant above is tested.
- You did not edit `.archvow/`, `AGENTS.md`, `ruff.toml`, CI files or other components' tests, and added no `# noqa` / `# type: ignore` / suppressions.
- Working code that breaks these rules is not done.
