<!-- archvow spec of vat_writer - the task the AI or the developer implements. Generated from spec.vow in this folder, do not edit. -->

# Task: implement `vat_writer` (writer) - US-003

Write the orders with their VAT as parquet

## Must never happen

- **vat_writer.H1** (critical) Downstream reads a half-written output
  - prevented by **vat_writer.INV1**: the output folder is complete or absent
  - tested by: a runtime guard in this component that fails the job when violated, marked with the comment `# archvow: enforces[vat_writer.INV1]`
- Safety questions still open (archvow show vat_writer) - do not invent hazards or guards; say in your answer that they are open:
  S2. Run twice (a re-run or a retry)
  S3. Data exposed (personal or secret data written where it must not be)
- Layer `p2_io`: never transform business data beyond enforcing the schema.

## Create exactly

- File: `src/my_pipeline/p2_io/vat_writer.py` - no other new file except its test.
- Public API (nothing else may be public; helpers start with `_`):
  - `def write_vat(df: DataFrame, path: str) -> None`
- May not import other components (foundations in p0_common excepted).
- Used by: `my_pipeline_job` - their tests must stay green.

## File header

Start the file with this docstring. Fill in the `<...>` values: the person responsible, the AI assistant you are (or `none`), today's date.

```python
"""<One-line summary of what this module does>.

@component  vat_writer (writer, p2_io)
@spec       US-003
@project    my_pipeline
@author     <your name>
@assisted   <claude-code | none>
@date       <YYYY-MM-DD>
"""
```

## Tests to write

- File: `tests/p2_io/test_vat_writer.py` - required, the build fails without it (ARCH017).
- Kind: **integration** - Real I/O in a temporary folder (files, tables, a local Derby metastore), marked @pytest.mark.integration. Never a real metastore, bucket or database.
- One test per decision and per rule below, named after it, e.g. `test_empty_input_returns_empty_dataframe`, and marked with it: `@pytest.mark.decision(3)`, `@pytest.mark.rule("R2")` (ARCH038).
- The test of vat_writer.INV1, as described under *Must never happen*.

## Decisions made (implement exactly, do not reinterpret)

1. Output already exists: overwrite, append, or fail?
   **Overwrite**
2. A failure mid-write: how is a partial output prevented or detected?
   **Write to a temporary folder next to the output, then rename it**
3. Re-running the same input: must the output be identical (idempotent)?
   **Yes: the same input gives the same output**
4. Partitioning columns and file format?
   **Parquet, not partitioned**
5. Does it write a table? Which one, created by which script ddl/NNN_<table>.sql (tables exist before the job: deployment or the scheduler runs `./entrypoint.sh --ddl`; the job never creates them) - or files at a path, no table?
   **No table: parquet files at the output path**

## Assumptions

Anything this spec does not define is not yours to decide. Ask before coding. If you still had to assume something, list it in your answer and record it with `archvow assumption add vat_writer "<assumption>"`. Until a person accepts or rejects it, CI fails (ARCH034).

## Definition of done

- `archvow check` reports no errors for this component.
- Checks on this code: ARCH, STYLE, SPARK, GDPR, TST - what each one checks: `.archvow/spec.md` (Checks).
- `ruff format .` run, then `ruff check .` and `pytest` pass.
- Every invariant above is tested.
- The tests of the components that use it still pass.
- You did not edit `.archvow/`, `AGENTS.md`, `ruff.toml`, CI files or other components' tests, and added no `# noqa` / `# type: ignore` / suppressions.
- Working code that breaks these rules is not done.
