<!-- archvow spec of my_pipeline_job - the task the AI or the developer implements. Generated from spec.vow in this folder, do not edit. -->

# Task: implement `my_pipeline_job` (job) - US-003

Read the orders, clean them, compute their VAT, write the result

## Must never happen

- Safety questions still open (archvow show my_pipeline_job) - do not invent hazards or guards; say in your answer that they are open:
  S1. Input missing, empty or late
  S2. Run twice (a re-run or a retry)
  S3. Old data treated as today's (the job reads the wrong date)
- Layer `p3_jobs`: never read or write directly (use p2_io components).

## Create exactly

- File: `src/my_pipeline/p3_jobs/my_pipeline_job.py` - no other new file except its test.
- Public API (nothing else may be public; helpers start with `_`):
  - `def run_my_pipeline(spark: SparkSession, config: JobConfig) -> None`
- May use only these components:
  - `my_pipeline.p2_io.orders_reader` (reader)
  - `my_pipeline.p1_domain.clean_orders` (transformation)
  - `my_pipeline.p1_domain.compute_vat` (transformation)
  - `my_pipeline.p2_io.vat_writer` (writer)
- Used by: `my_pipeline_main` - their tests must stay green.
- Log one line when it starts and one when it ends (with its duration), and the rows read and written per step, taken from what Spark already computed (the write's metrics, a DataFrame already cached): a df.count() of its own runs the whole job again.

## File header

Start the file with this docstring. Fill in the `<...>` values: the person responsible, the AI assistant you are (or `none`), today's date.

```python
"""<One-line summary of what this module does>.

@component  my_pipeline_job (job, p3_jobs)
@spec       US-003
@project    my_pipeline
@author     <your name>
@assisted   <claude-code | none>
@date       <YYYY-MM-DD>
"""
```

## Tests to write

- File: `tests/p3_jobs/test_my_pipeline_job.py` - required, the build fails without it (ARCH017).
- Kind: **integration** - Real I/O in a temporary folder (files, tables, a local Derby metastore), marked @pytest.mark.integration. Never a real metastore, bucket or database.
- One test per decision and per rule below, named after it, e.g. `test_empty_input_returns_empty_dataframe`, and marked with it: `@pytest.mark.decision(3)`, `@pytest.mark.rule("R2")` (ARCH038).
- End to end with golden files: the business examples of the card (archvow example add: tests/examples/<component>/<example>/input/ and expected/, locked, with an optional scenario.feature saying it in Given/When/Then words), and one test per example in tests/integration/ that runs the job on input/ and compares the result with expected/ (sorted, same columns and types). Read them as they are, never change them: no example yet means a person records one first.

## Decisions made (implement exactly, do not reinterpret)

1. One input is missing: skip the run, fail, or run with partial data?
   **Fail**
2. Retries: how many, and is a retry safe?
   **No automatic retry; a re-run is safe (the writer overwrites)**
3. Which run date / partition does the job process, and who decides it?
   **No run date: the job processes the input it is given**

## Assumptions

Anything this spec does not define is not yours to decide. Ask before coding. If you still had to assume something, list it in your answer and record it with `archvow assumption add my_pipeline_job "<assumption>"`. Until a person accepts or rejects it, CI fails (ARCH034).

## Definition of done

- `archvow check` reports no errors for this component.
- Checks on this code: ARCH, STYLE, SPARK, GDPR, TST - what each one checks: `.archvow/spec.md` (Checks).
- `ruff format .` run, then `ruff check .` and `pytest` pass.
- Every invariant above is tested.
- The tests of the components that use it still pass.
- You did not edit `.archvow/`, `AGENTS.md`, `ruff.toml`, CI files or other components' tests, and added no `# noqa` / `# type: ignore` / suppressions.
- Working code that breaks these rules is not done.
