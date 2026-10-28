<!-- archvow spec of config - the task the AI or the developer implements. Generated from spec.vow in this folder, do not edit. -->

# Task: implement `config` (foundation)

Immutable job configuration passed from entry points to jobs.

## Must never happen

- Nothing: the safety analysis found nothing this component must guard against.
- Layer `p0_common`: never perform I/O.
- Layer `p0_common`: never hold business logic.

## Create exactly

- File: `src/my_pipeline/p0_common/config.py` - no other new file except its test.
- Public API (nothing else may be public; helpers start with `_`):
  - `class JobConfig`
- May not import other components (foundations in p0_common excepted).

## File header

Start the file with this docstring. Fill in the `<...>` values: the person responsible, the AI assistant you are (or `none`), today's date.

```python
"""<One-line summary of what this module does>.

@component  config (foundation, p0_common)
@spec       -
@project    my_pipeline
@author     <your name>
@assisted   <claude-code | none>
@date       <YYYY-MM-DD>
"""
```

## Decisions made (implement exactly, do not reinterpret)

1. Which fields does JobConfig hold (name: type, required or default value)?
   **input_path: str and output_path: str, both required; a frozen dataclass**
2. What happens when a value is missing or invalid (which exception, raised where)?
   **ValueError raised in __post_init__, naming the field, for an empty or blank path**

## Assumptions

Anything this spec does not define is not yours to decide. Ask before coding. If you still had to assume something, list it in your answer and record it with `archvow assumption add config "<assumption>"`. Until a person accepts or rejects it, CI fails (ARCH034).

## Definition of done

- `archvow check` reports no errors for this component.
- Checks on this code: ARCH, STYLE, SPARK, GDPR, TST - what each one checks: `.archvow/spec.md` (Checks).
- `ruff format .` run, then `ruff check .` and `pytest` pass.
- Every invariant above is tested.
- You did not edit `.archvow/`, `AGENTS.md`, `ruff.toml`, CI files or other components' tests, and added no `# noqa` / `# type: ignore` / suppressions.
- Working code that breaks these rules is not done.
