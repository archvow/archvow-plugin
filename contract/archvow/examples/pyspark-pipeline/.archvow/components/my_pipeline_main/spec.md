<!-- archvow spec of my_pipeline_main - the task the AI or the developer implements. Generated from spec.vow in this folder, do not edit. -->

# Task: implement `my_pipeline_main` (entrypoint) - US-003

Command line: --input PATH (the input folder), --output PATH (the output folder)

## Must never happen

- Safety questions still open (archvow show my_pipeline_main) - do not invent hazards or guards; say in your answer that they are open:
  S1. Wrong values (nulls, negatives, out of range, wrong types, a column that changes type or disappears)
- Layer `p4_entrypoints`: never contain pipeline logic.

## Create exactly

- File: `src/my_pipeline/p4_entrypoints/my_pipeline_main.py` - no other new file except its test.
- Public API (nothing else may be public; helpers start with `_`):
  - `def main(argv: Sequence[str] | None = None) -> int`
- May use only these components:
  - `my_pipeline.p3_jobs.my_pipeline_job` (job)
- End the file with `if __name__ == "__main__": raise SystemExit(main())`: entrypoint.sh runs it with spark-submit.
- Create the SparkSession with p0_common.spark.get_spark_session and stop it before returning.
- Accept one --name-with-dashes argument per required parameter of the config decision (archvow next prints them in its run line), in line with what the job reads and writes.

## File header

Start the file with this docstring. Fill in the `<...>` values: the person responsible, the AI assistant you are (or `none`), today's date.

```python
"""<One-line summary of what this module does>.

@component  my_pipeline_main (entrypoint, p4_entrypoints)
@spec       US-003
@project    my_pipeline
@author     <your name>
@assisted   <claude-code | none>
@date       <YYYY-MM-DD>
"""
```

## Tests to write

- File: `tests/p4_entrypoints/test_my_pipeline_main.py` - required, the build fails without it (ARCH017).
- Kind: **unit** - Pure and fast: small DataFrames built in memory, no files, tables or network. Runs on every commit.
- One test per decision and per rule below, named after it, e.g. `test_empty_input_returns_empty_dataframe`, and marked with it: `@pytest.mark.decision(3)`, `@pytest.mark.rule("R2")` (ARCH038).

## Decisions made (implement exactly, do not reinterpret)

1. Invalid arguments: which exit code and message?
   **Exit code 2 with a usage message**
2. Which exit codes signal which failures to the scheduler?
   **0 success, 1 job failure, 2 invalid arguments**

## Assumptions

Anything this spec does not define is not yours to decide. Ask before coding. If you still had to assume something, list it in your answer and record it with `archvow assumption add my_pipeline_main "<assumption>"`. Until a person accepts or rejects it, CI fails (ARCH034).

## Definition of done

- `archvow check` reports no errors for this component.
- Checks on this code: ARCH, STYLE, SPARK, GDPR, TST - what each one checks: `.archvow/spec.md` (Checks).
- `ruff format .` run, then `ruff check .` and `pytest` pass.
- Every invariant above is tested.
- You did not edit `.archvow/`, `AGENTS.md`, `ruff.toml`, CI files or other components' tests, and added no `# noqa` / `# type: ignore` / suppressions.
- Working code that breaks these rules is not done.
