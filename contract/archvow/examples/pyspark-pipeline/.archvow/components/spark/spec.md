<!-- archvow spec of spark - the task the AI or the developer implements. Generated from spec.vow in this folder, do not edit. -->

# Task: implement `spark` (foundation)

Single factory for SparkSession objects.

## Must never happen

- Nothing: the safety analysis found nothing this component must guard against.
- Layer `p0_common`: never perform I/O.
- Layer `p0_common`: never hold business logic.

## Create exactly

- File: `src/my_pipeline/p0_common/spark.py` - no other new file except its test.
- Public API (nothing else may be public; helpers start with `_`):
  - `def get_spark_session(app_name: str, master: str | None = None) -> SparkSession`
- May not import other components (foundations in p0_common excepted).

## File header

Start the file with this docstring. Fill in the `<...>` values: the person responsible, the AI assistant you are (or `none`), today's date.

```python
"""<One-line summary of what this module does>.

@component  spark (foundation, p0_common)
@spec       -
@project    my_pipeline
@author     <your name>
@assisted   <claude-code | none>
@date       <YYYY-MM-DD>
"""
```

## Decisions made (implement exactly, do not reinterpret)

1. Which Spark settings must every session set (e.g. spark.sql.session.timeZone=UTC), or none?
   **spark.sql.session.timeZone=UTC, nothing else**

## Assumptions

Anything this spec does not define is not yours to decide. Ask before coding. If you still had to assume something, list it in your answer and record it with `archvow assumption add spark "<assumption>"`. Until a person accepts or rejects it, CI fails (ARCH034).

## Definition of done

- `archvow check` reports no errors for this component.
- Checks on this code: ARCH, STYLE, SPARK, GDPR, TST - what each one checks: `.archvow/spec.md` (Checks).
- `ruff format .` run, then `ruff check .` and `pytest` pass.
- Every invariant above is tested.
- You did not edit `.archvow/`, `AGENTS.md`, `ruff.toml`, CI files or other components' tests, and added no `# noqa` / `# type: ignore` / suppressions.
- Working code that breaks these rules is not done.
